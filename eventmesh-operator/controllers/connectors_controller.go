/*
Copyright 2023.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package controllers

import (
	"context"
	"fmt"
	eventmeshoperatorv1 "github.com/apache/eventmesh/eventmesh-operator/api/v1"
	"github.com/go-logr/logr"
	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"reflect"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	"strconv"
	_ "strings"
)

// ConnectorsReconciler reconciles a Connectors object
type ConnectorsReconciler struct {
	client.Client
	Scheme *runtime.Scheme
	Logger logr.Logger
}

//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=connectors,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=connectors/status,verbs=get;update;patch
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=connectors/finalizers,verbs=update

// Reconcile is part of the main kubernetes reconciliation loop which aims to
// move the current state of the cluster closer to the desired state.
// TODO(user): Modify the Reconcile function to compare the state specified by
// the EventMeshOperator object against the actual cluster state, and then
// perform operations to make the cluster state reflect the state specified by
// the user.
//
// For more details, check Reconcile and its Result here:
// - https://pkg.go.dev/sigs.k8s.io/controller-runtime@v0.14.1/pkg/reconcile
func (r ConnectorsReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	r.Logger.Info("connectors start reconciling",
		"Namespace", req.Namespace, "Namespace", req.Name)

	connector := &eventmeshoperatorv1.Connectors{}
	err := r.Client.Get(context.TODO(), req.NamespacedName, connector)
	if err != nil {
		// If it's a not found exception, it means the cr has been deleted.
		if errors.IsNotFound(err) {
			r.Logger.Info("connector resource not found. Ignoring since object must be deleted.")
			return ctrl.Result{}, err
		}
		r.Logger.Error(err, "Failed to get connector")
		return ctrl.Result{}, err
	}

	connectorStatefulSet := r.getConnectorStatefulSet(connector)
	// Check if the statefulSet already exists, if not create a new one
	found := &appsv1.StatefulSet{}
	err = r.Client.Get(context.TODO(),
		types.NamespacedName{
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
		}
	} else if err != nil {
		r.Logger.Error(err, "Failed to list Connector StatefulSet.")
	}

	podList := &corev1.PodList{}
	labelSelector := labels.SelectorFromSet(labelsForController(connector.Name))
	listOps := &client.ListOptions{
		Namespace:     connector.Namespace,
		LabelSelector: labelSelector,
	}
	err = r.Client.List(context.TODO(), podList, listOps)
	if err != nil {
		r.Logger.Error(err, "Failed to list pods.", "Connector.Namespace", connector.Namespace,
			"Connector.Name", connector.Name)
		return reconcile.Result{}, err
	}
	podNames := getConnectorPodNames(podList.Items)
	r.Logger.Info("Connector.Status.Nodes length = " + strconv.Itoa(len(connector.Status.Nodes)))
	r.Logger.Info("podNames length = " + strconv.Itoa(len(podNames)))
	// Ensure every pod is in running phase
	for _, pod := range podList.Items {
		if !reflect.DeepEqual(pod.Status.Phase, corev1.PodRunning) {
			r.Logger.Info("pod " + pod.Name + " phase is " + string(pod.Status.Phase) + ", wait for a moment...")
		}
	}

	// Update status.Size if needed
	if connector.Spec.Size != connector.Status.Size {
		r.Logger.Info("Connector.Status.Size = " + strconv.Itoa(connector.Status.Size))
		r.Logger.Info("Connector.Spec.Size = " + strconv.Itoa(connector.Spec.Size))
		connector.Status.Size = connector.Spec.Size
		err = r.Client.Status().Update(context.TODO(), connector)
		if err != nil {
			r.Logger.Error(err, "Failed to update Connector Size status.")
		}
	}

	// Update status.Nodes if needed
	if !reflect.DeepEqual(podNames, connector.Status.Nodes) {
		connector.Status.Nodes = podNames
		err = r.Client.Status().Update(context.TODO(), connector)
		if err != nil {
			r.Logger.Error(err, "Failed to update Connector Nodes status.")
		}
	}

	return ctrl.Result{}, nil
}

// SetupWithManager sets up the controller with the Manager.
func (r ConnectorsReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&eventmeshoperatorv1.Connectors{}).
		Complete(r)
}

func (r ConnectorsReconciler) getConnectorStatefulSet(connector *eventmeshoperatorv1.Connectors) *appsv1.StatefulSet {
	ls := labelsForController(connector.Name)

	var replica = int32(connector.Spec.Size)
	connectorDep := &appsv1.StatefulSet{
		ObjectMeta: metav1.ObjectMeta{
			Name:      connector.Name,
			Namespace: connector.Namespace,
		},
		Spec: appsv1.StatefulSetSpec{
			ServiceName: fmt.Sprintf("%s-service", connector.Name),
			Replicas:    &replica,
			Selector: &metav1.LabelSelector{
				MatchLabels: ls,
			},
			UpdateStrategy: appsv1.StatefulSetUpdateStrategy{
				Type: appsv1.RollingUpdateStatefulSetStrategyType,
			},
			Template: corev1.PodTemplateSpec{
				ObjectMeta: metav1.ObjectMeta{
					Labels: ls,
				},
				Spec: corev1.PodSpec{

					ServiceAccountName: connector.Spec.ServiceAccountName,
					Affinity:           connector.Spec.Affinity,
					Tolerations:        connector.Spec.Tolerations,
					NodeSelector:       connector.Spec.NodeSelector,
					PriorityClassName:  connector.Spec.PriorityClassName,
					ImagePullSecrets:   connector.Spec.ImagePullSecrets,
					Containers: []corev1.Container{{
						Image:           connector.Spec.ConnectorImage,
						Name:            "eventmesh-connector",
						SecurityContext: getConnectorContainerSecurityContext(connector),
						ImagePullPolicy: connector.Spec.ImagePullPolicy,
					}},
					SecurityContext: getConnectorPodSecurityContext(connector),
				},
			},
		},
	}
	_ = controllerutil.SetControllerReference(connector, connectorDep, r.Scheme)

	return connectorDep
}

func getConnectorContainerSecurityContext(connector *eventmeshoperatorv1.Connectors) *corev1.SecurityContext {
	var securityContext = corev1.SecurityContext{}
	if connector.Spec.ContainerSecurityContext != nil {
		securityContext = *connector.Spec.ContainerSecurityContext
	}
	return &securityContext
}

func labelsForController(name string) map[string]string {
	return map[string]string{"app": "connector", "connectors_rocketmq": name}
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
