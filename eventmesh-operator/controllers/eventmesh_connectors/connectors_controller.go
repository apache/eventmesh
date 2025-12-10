/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eventmesh_connectors

import (
	"context"
	"fmt"
	eventmeshoperatorv1 "github.com/apache/eventmesh/eventmesh-operator/api/v1"
	"github.com/apache/eventmesh/eventmesh-operator/share"
	"github.com/go-logr/logr"
	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/apimachinery/pkg/util/intstr"
	"reflect"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
	"sigs.k8s.io/controller-runtime/pkg/handler"
	"sigs.k8s.io/controller-runtime/pkg/manager"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	"sigs.k8s.io/controller-runtime/pkg/source"
	_ "strings"
	"time"
)

// ConnectorsReconciler reconciles a Connectors object
type ConnectorsReconciler struct {
	Client client.Client
	Scheme *runtime.Scheme
	Logger logr.Logger
}

// SetupWithManager creates a new Connectors Controller and adds it to the Manager. The Manager will set fields on the Controller
// and Start it when the Manager is Started.
func SetupWithManager(mgr manager.Manager) error {
	return add(mgr, newReconciler(mgr))
}

// newReconciler returns a new reconcile.Reconciler
func newReconciler(mgr manager.Manager) reconcile.Reconciler {
	return &ConnectorsReconciler{
		Client: mgr.GetClient(),
		Scheme: mgr.GetScheme(),
		Logger: mgr.GetLogger().WithName("connector"),
	}
}

// add adds a new Controller to mgr with r as the reconcile.Reconciler
func add(mgr manager.Manager, r reconcile.Reconciler) error {
	// Create a new controller
	c, err := controller.New("connectors-controller", mgr, controller.Options{Reconciler: r})
	if err != nil {
		return err
	}

	// Watch for changes to primary resource connector
	err = c.Watch(&source.Kind{Type: &eventmeshoperatorv1.Connectors{}}, &handler.EnqueueRequestForObject{})
	if err != nil {
		return err
	}

	// TODO(user): Modify this to be the types you create that are owned by the primary resource
	// Watch for changes to secondary resource Pods and requeue the owner runtime
	err = c.Watch(&source.Kind{Type: &corev1.Pod{}}, &handler.EnqueueRequestForOwner{
		IsController: true,
		OwnerType:    &eventmeshoperatorv1.Connectors{},
	})
	if err != nil {
		return err
	}

	return nil
}

//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=connectors,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=connectors/status,verbs=get;update;patch
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=connectors/finalizers,verbs=update
//+kubebuilder:rbac:groups="",resources=pods,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups="",resources=pods/exec,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups="apps",resources=statefulsets,verbs=get;list;watch;create;update;patch;delete

// Reconcile is part of the main kubernetes reconciliation loop which aims to
// move the current state of the cluster closer to the desired state.
func (r ConnectorsReconciler) Reconcile(ctx context.Context, req reconcile.Request) (reconcile.Result, error) {
	r.Logger.Info("connectors start reconciling",
		"Namespace", req.Namespace, "Name", req.Name)

	connector := &eventmeshoperatorv1.Connectors{}
	err := r.Client.Get(context.TODO(), req.NamespacedName, connector)
	if err != nil {
		if errors.IsNotFound(err) {
			r.Logger.Info("connector resource not found. Ignoring since object must be deleted.")
			return reconcile.Result{}, nil
		}
		r.Logger.Error(err, "Failed to get connector")
		return reconcile.Result{}, err
	}

	// Dependency Check: Check if Runtime is ready
	runtimeList := &eventmeshoperatorv1.RuntimeList{}
	listOps := &client.ListOptions{Namespace: connector.Namespace}
	err = r.Client.List(context.TODO(), runtimeList, listOps)
	if err != nil {
		r.Logger.Error(err, "Failed to list Runtimes for dependency check")
		return reconcile.Result{}, err
	}

	runtimeReady := false
	for _, runtime := range runtimeList.Items {
		// Simple check: if at least one runtime has size > 0
		if runtime.Status.Size > 0 {
			runtimeReady = true
			break
		}
	}

	if !runtimeReady {
		r.Logger.Info("Connector waiting for EventMesh Runtime to be ready...")
		return reconcile.Result{Requeue: true, RequeueAfter: time.Duration(share.RequeueAfterSecond) * time.Second}, nil
	}

	// 1. Reconcile Service
	connectorService := r.getConnectorService(connector)
	foundService := &corev1.Service{}
	err = r.Client.Get(context.TODO(), types.NamespacedName{
		Name:      connectorService.Name,
		Namespace: connectorService.Namespace,
	}, foundService)
	if err != nil && errors.IsNotFound(err) {
		r.Logger.Info("Creating a new Connector Service.", "Namespace", connectorService.Namespace, "Name", connectorService.Name)
		err = r.Client.Create(context.TODO(), connectorService)
		if err != nil {
			r.Logger.Error(err, "Failed to create new Connector Service")
			return reconcile.Result{}, err
		}
	} else if err != nil {
		r.Logger.Error(err, "Failed to get Connector Service")
		return reconcile.Result{}, err
	}

	// 2. Reconcile StatefulSet
	connectorStatefulSet := r.getConnectorStatefulSet(connector)
	found := &appsv1.StatefulSet{}
	err = r.Client.Get(context.TODO(), types.NamespacedName{
		Name:      connectorStatefulSet.Name,
		Namespace: connectorStatefulSet.Namespace}, found)
	if err != nil && errors.IsNotFound(err) {
		r.Logger.Info("Creating a new Connector StatefulSet.",
			"StatefulSet.Namespace", connectorStatefulSet.Namespace,
			"StatefulSet.Name", connectorStatefulSet.Name)
		err = r.Client.Create(context.TODO(), connectorStatefulSet)
		if err != nil {
			r.Logger.Error(err, "Failed to create new Connector StatefulSet",
				"StatefulSet.Namespace", connectorStatefulSet.Namespace,
				"StatefulSet.Name", connectorStatefulSet.Name)
			return reconcile.Result{}, err
		}
	} else if err != nil {
		r.Logger.Error(err, "Failed to list Connector StatefulSet.")
		return reconcile.Result{}, err
	}

	podList := &corev1.PodList{}
	labelSelector := labels.SelectorFromSet(getLabels(connector.Name))
	podListOps := &client.ListOptions{
		Namespace:     connector.Namespace,
		LabelSelector: labelSelector,
	}
	err = r.Client.List(context.TODO(), podList, podListOps)
	if err != nil {
		r.Logger.Error(err, "Failed to list pods.", "Connector.Namespace", connector.Namespace,
			"Connector.Name", connector.Name)
		return reconcile.Result{}, err
	}
	podNames := getConnectorPodNames(podList.Items)
	
	// Update Status
	var needsUpdate bool
	if connector.Spec.Size != connector.Status.Size {
		connector.Status.Size = connector.Spec.Size
		needsUpdate = true
	}
	if !reflect.DeepEqual(podNames, connector.Status.Nodes) {
		connector.Status.Nodes = podNames
		needsUpdate = true
	}

	if needsUpdate {
		r.Logger.Info("Updating connector status")
		err = r.Client.Status().Update(context.TODO(), connector)
		if err != nil {
			r.Logger.Error(err, "Failed to update Connector status.")
			return reconcile.Result{}, err
		}
	}

	r.Logger.Info("Successful reconciliation!")
	return reconcile.Result{RequeueAfter: time.Duration(share.RequeueAfterSecond) * time.Second}, nil
}

func (r ConnectorsReconciler) getConnectorStatefulSet(connector *eventmeshoperatorv1.Connectors) *appsv1.StatefulSet {
	replica := int32(connector.Spec.Size)
	serviceName := fmt.Sprintf("%s-service", connector.Name)
	label := getLabels(connector.Name)

	connectorDep := &appsv1.StatefulSet{
		ObjectMeta: metav1.ObjectMeta{
			Name:      connector.Name,
			Namespace: connector.Namespace,
			Labels:    label,
		},
		Spec: appsv1.StatefulSetSpec{
			ServiceName: serviceName,
			Replicas:    &replica,
			Selector: &metav1.LabelSelector{
				MatchLabels: label,
			},
			UpdateStrategy: appsv1.StatefulSetUpdateStrategy{
				Type: appsv1.RollingUpdateStatefulSetStrategyType,
			},
			Template: corev1.PodTemplateSpec{
				ObjectMeta: metav1.ObjectMeta{
					Labels: label,
				},
				Spec: corev1.PodSpec{
					HostNetwork:        connector.Spec.HostNetwork,
					DNSPolicy:          connector.Spec.DNSPolicy,
					ServiceAccountName: connector.Spec.ServiceAccountName,
					Affinity:           connector.Spec.Affinity,
					Tolerations:        connector.Spec.Tolerations,
					NodeSelector:       connector.Spec.NodeSelector,
					PriorityClassName:  connector.Spec.PriorityClassName,
					ImagePullSecrets:   connector.Spec.ImagePullSecrets,
					Containers:         connector.Spec.ConnectorContainers, // Use all containers
					Volumes:            connector.Spec.Volumes,
					SecurityContext:    getConnectorPodSecurityContext(connector),
				},
			},
		},
	}
	
	// Manually set security context for first container if needed
	if len(connectorDep.Spec.Template.Spec.Containers) > 0 {
		if connectorDep.Spec.Template.Spec.Containers[0].SecurityContext == nil {
			connectorDep.Spec.Template.Spec.Containers[0].SecurityContext = getConnectorContainerSecurityContext(connector)
		}
	}

	_ = controllerutil.SetControllerReference(connector, connectorDep, r.Scheme)

	return connectorDep
}

func (r ConnectorsReconciler) getConnectorService(connector *eventmeshoperatorv1.Connectors) *corev1.Service {
	serviceName := fmt.Sprintf("%s-service", connector.Name)
	label := getLabels(connector.Name)

	var ports []corev1.ServicePort
	if len(connector.Spec.ConnectorContainers) > 0 {
		for _, port := range connector.Spec.ConnectorContainers[0].Ports {
			ports = append(ports, corev1.ServicePort{
				Name:       port.Name,
				Port:       port.ContainerPort,
				TargetPort: intstr.FromInt(int(port.ContainerPort)),
			})
		}
	}
	// Fallback port if none
	if len(ports) == 0 {
		ports = append(ports, corev1.ServicePort{
			Name:       "http",
			Port:       8080,
			TargetPort: intstr.FromInt(8080),
		})
	}

	svc := &corev1.Service{
		ObjectMeta: metav1.ObjectMeta{
			Name:      serviceName,
			Namespace: connector.Namespace,
			Labels:    label,
		},
		Spec: corev1.ServiceSpec{
			ClusterIP: "None", // Headless
			Selector:  label,
			Ports:     ports,
		},
	}
	_ = controllerutil.SetControllerReference(connector, svc, r.Scheme)
	return svc
}

func getConnectorContainerSecurityContext(connector *eventmeshoperatorv1.Connectors) *corev1.SecurityContext {
	var securityContext = corev1.SecurityContext{}
	if connector.Spec.ContainerSecurityContext != nil {
		securityContext = *connector.Spec.ContainerSecurityContext
	}
	return &securityContext
}

func getLabels(name string) map[string]string {
	return map[string]string{
		"app":      "eventmesh-connector",
		"instance": name,
	}
}

func getConnectorPodSecurityContext(connector *eventmeshoperatorv1.Connectors) *corev1.PodSecurityContext {
	var securityContext = corev1.PodSecurityContext{}
	if connector.Spec.PodSecurityContext != nil {
		securityContext = *connector.Spec.PodSecurityContext
	}
	return &securityContext
}

func getConnectorPodNames(pods []corev1.Pod) []string {
	var podNames []string
	for _, pod := range pods {
		podNames = append(podNames, pod.Name)
	}
	return podNames
}
