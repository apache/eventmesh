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

package eventmesh_runtime

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
	"strconv"
	"time"
)

// RuntimeReconciler reconciles a Runtime object
type RuntimeReconciler struct {
	Client client.Client
	Scheme *runtime.Scheme
	Logger logr.Logger
}

// SetupWithManager creates a new Runtime Controller and adds it to the Manager. The Manager will set fields on the Controller
// and Start it when the Manager is Started.
func SetupWithManager(mgr manager.Manager) error {
	return add(mgr, newReconciler(mgr))
}

// newReconciler returns a new reconcile.Reconciler
func newReconciler(mgr manager.Manager) reconcile.Reconciler {
	return &RuntimeReconciler{
		Client: mgr.GetClient(),
		Scheme: mgr.GetScheme(),
		Logger: mgr.GetLogger().WithName("runtime"),
	}
}

// add adds a new Controller to mgr with r as the reconcile.Reconciler
func add(mgr manager.Manager, r reconcile.Reconciler) error {
	// Create a new controller
	c, err := controller.New("runtime-controller", mgr, controller.Options{Reconciler: r})
	if err != nil {
		return err
	}

	// Watch for changes to primary resource runtime
	err = c.Watch(&source.Kind{Type: &eventmeshoperatorv1.Runtime{}}, &handler.EnqueueRequestForObject{})
	if err != nil {
		return err
	}

	// Watch for changes to secondary resource Pods and requeue the owner runtime
	err = c.Watch(&source.Kind{Type: &corev1.Pod{}}, &handler.EnqueueRequestForOwner{
		IsController: true,
		OwnerType:    &eventmeshoperatorv1.Runtime{},
	})
	if err != nil {
		return err
	}

	return nil
}

//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=runtimes,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=runtimes/status,verbs=get;update;patch
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=runtimes/finalizers,verbs=update
//+kubebuilder:rbac:groups="",resources=pods,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups="apps",resources=statefulsets,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups="apps",resources=deployments,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups="",resources=services,verbs=get;list;watch;create;update;patch;delete

// Reconcile is part of the main kubernetes reconciliation loop which aims to
// move the current state of the cluster closer to the desired state.
func (r *RuntimeReconciler) Reconcile(ctx context.Context, req reconcile.Request) (reconcile.Result, error) {
	r.Logger.Info("eventMeshRuntime start reconciling",
		"Namespace", req.Namespace, "Name", req.Name)

	eventMeshRuntime := &eventmeshoperatorv1.Runtime{}
	err := r.Client.Get(context.TODO(), req.NamespacedName, eventMeshRuntime)
	if err != nil {
		if errors.IsNotFound(err) {
			r.Logger.Info("eventMeshRuntime resource not found. Ignoring since object must be deleted.")
			return reconcile.Result{}, nil
		}
		r.Logger.Error(err, "Failed to get eventMeshRuntime")
		return reconcile.Result{}, err
	}
	r.Logger.Info("get eventMeshRuntime object", "name", eventMeshRuntime.Name)

	var groupNum int
	if eventMeshRuntime.Status.Size == 0 {
		groupNum = eventMeshRuntime.Spec.Size
	} else {
		groupNum = eventMeshRuntime.Status.Size
	}

	replicaPerGroup := eventMeshRuntime.Spec.ReplicaPerGroup
	r.Logger.Info("GroupNum=" + strconv.Itoa(groupNum) + ", replicaPerGroup=" + strconv.Itoa(replicaPerGroup))

	for groupIndex := 0; groupIndex < groupNum; groupIndex++ {
		r.Logger.Info("Check eventMeshRuntime cluster " + strconv.Itoa(groupIndex+1) + "/" + strconv.Itoa(groupNum))
		
		// 1. Reconcile Service
		service := r.getEventMeshRuntimeService(eventMeshRuntime, groupIndex)
		foundService := &corev1.Service{}
		err = r.Client.Get(context.TODO(), types.NamespacedName{Name: service.Name, Namespace: service.Namespace}, foundService)
		if err != nil && errors.IsNotFound(err) {
			r.Logger.Info("Creating a new eventMeshRuntime Service.", "Service.Namespace", service.Namespace, "Service.Name", service.Name)
			err = r.Client.Create(context.TODO(), service)
			if err != nil {
				r.Logger.Error(err, "Failed to create new Service", "Service.Namespace", service.Namespace, "Service.Name", service.Name)
				return reconcile.Result{}, err
			}
		} else if err != nil {
			r.Logger.Error(err, "Failed to get eventMeshRuntime Service.")
			return reconcile.Result{}, err
		}

		// 2. Reconcile StatefulSet
		runtimeSts := r.getEventMeshRuntimeStatefulSet(eventMeshRuntime, groupIndex)
		foundSts := &appsv1.StatefulSet{}
		err = r.Client.Get(context.TODO(), types.NamespacedName{
			Name:      runtimeSts.Name,
			Namespace: runtimeSts.Namespace,
		}, foundSts)
		
		if err != nil && errors.IsNotFound(err) {
			r.Logger.Info("Creating a new eventMeshRuntime StatefulSet.",
				"StatefulSet.Namespace", runtimeSts.Namespace,
				"StatefulSet.Name", runtimeSts.Name)
			err = r.Client.Create(context.TODO(), runtimeSts)
			if err != nil {
				r.Logger.Error(err, "Failed to create new StatefulSet",
					"StatefulSet.Namespace", runtimeSts.Namespace,
					"StatefulSet.Name", runtimeSts.Name)
				return reconcile.Result{}, err
			}
		} else if err != nil {
			r.Logger.Error(err, "Failed to get eventMeshRuntime StatefulSet.")
			return reconcile.Result{}, err
		} else {
			// Update if needed
			if eventMeshRuntime.Spec.AllowRestart {
				// Simple update logic: overwrite spec
				r.Logger.Info("Updating eventMeshRuntime StatefulSet", "Name", foundSts.Name)
				runtimeSts.ResourceVersion = foundSts.ResourceVersion
				err = r.Client.Update(context.TODO(), runtimeSts)
				if err != nil {
					r.Logger.Error(err, "Failed to update eventMeshRuntime StatefulSet", "Name", foundSts.Name)
					return reconcile.Result{}, err
				}
			}
		}
	}

	podList := &corev1.PodList{}
	labelSelector := labels.SelectorFromSet(getLabels(eventMeshRuntime.Name))
	listOps := &client.ListOptions{
		Namespace:     eventMeshRuntime.Namespace,
		LabelSelector: labelSelector,
	}
	err = r.Client.List(context.TODO(), podList, listOps)
	if err != nil {
		r.Logger.Error(err, "Failed to list pods.",
			"eventMeshRuntime.Namespace", eventMeshRuntime.Namespace, "eventMeshRuntime.Name", eventMeshRuntime.Name)
		return reconcile.Result{}, err
	}

	podNames := getRuntimePodNames(podList.Items)
	
	// Update Status
	var needsUpdate bool
	if eventMeshRuntime.Spec.Size != eventMeshRuntime.Status.Size {
		eventMeshRuntime.Status.Size = eventMeshRuntime.Spec.Size
		needsUpdate = true
	}
	if !reflect.DeepEqual(podNames, eventMeshRuntime.Status.Nodes) {
		eventMeshRuntime.Status.Nodes = podNames
		needsUpdate = true
	}

	if needsUpdate {
		r.Logger.Info("Updating eventMeshRuntime status")
		err = r.Client.Status().Update(context.TODO(), eventMeshRuntime)
		if err != nil {
			r.Logger.Error(err, "Failed to update eventMeshRuntime status.")
			return reconcile.Result{}, err
		}
	}

	// Update global state
	runningEventMeshRuntimeNum := getRunningRuntimeNum(podList.Items)
	// We check if total running pods match expected total replicas
	totalExpectedReplicas := groupNum * replicaPerGroup
	if runningEventMeshRuntimeNum == totalExpectedReplicas {
		// share.IsEventMeshRuntimeInitialized = true (Removed as per refactor)
	}

	r.Logger.Info("Successful reconciliation!")
	return reconcile.Result{RequeueAfter: time.Duration(share.RequeueAfterSecond) * time.Second}, nil
}

func getRunningRuntimeNum(pods []corev1.Pod) int {
	var num = 0
	for _, pod := range pods {
		if reflect.DeepEqual(pod.Status.Phase, corev1.PodRunning) {
			num++
		}
	}
	return num
}

func getRuntimePodNames(pods []corev1.Pod) []string {
	var podNames []string
	for _, pod := range pods {
		podNames = append(podNames, pod.Name)
	}
	return podNames
}

func (r *RuntimeReconciler) getEventMeshRuntimeStatefulSet(runtime *eventmeshoperatorv1.Runtime, groupIndex int) *appsv1.StatefulSet {
	// Naming: <runtimeName>-<groupIndex>
	statefulSetName := fmt.Sprintf("%s-%d", runtime.Name, groupIndex)
	serviceName := fmt.Sprintf("%s-%d-headless", runtime.Name, groupIndex)
	
	replicas := int32(runtime.Spec.ReplicaPerGroup)
	label := getLabels(runtime.Name)
	
	deployment := &appsv1.StatefulSet{
		ObjectMeta: metav1.ObjectMeta{
			Name:      statefulSetName,
			Namespace: runtime.Namespace,
			Labels:    label,
		},
		Spec: appsv1.StatefulSetSpec{
			ServiceName: serviceName,
			Replicas:    &replicas,
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
					DNSPolicy:         runtime.Spec.RuntimePodTemplate.Template.Spec.DNSPolicy,
					Affinity:          runtime.Spec.RuntimePodTemplate.Template.Spec.Affinity,
					Tolerations:       runtime.Spec.RuntimePodTemplate.Template.Spec.Tolerations,
					NodeSelector:      runtime.Spec.RuntimePodTemplate.Template.Spec.NodeSelector,
					PriorityClassName: runtime.Spec.RuntimePodTemplate.Template.Spec.PriorityClassName,
					HostNetwork:       runtime.Spec.RuntimePodTemplate.Template.Spec.HostNetwork,
					Containers:        runtime.Spec.RuntimePodTemplate.Template.Spec.Containers, // Use all containers
					Volumes:           runtime.Spec.RuntimePodTemplate.Template.Spec.Volumes,
					SecurityContext:   getRuntimePodSecurityContext(runtime),
				},
			},
		},
	}
	// Manually set security context for the first container if not set, for backward compatibility or strict override
	if len(deployment.Spec.Template.Spec.Containers) > 0 {
		if deployment.Spec.Template.Spec.Containers[0].SecurityContext == nil {
			deployment.Spec.Template.Spec.Containers[0].SecurityContext = getContainerSecurityContext(runtime)
		}
	}

	_ = controllerutil.SetControllerReference(runtime, deployment, r.Scheme)
	return deployment
}

func (r *RuntimeReconciler) getEventMeshRuntimeService(runtime *eventmeshoperatorv1.Runtime, groupIndex int) *corev1.Service {
	serviceName := fmt.Sprintf("%s-%d-headless", runtime.Name, groupIndex)
	label := getLabels(runtime.Name)

	var ports []corev1.ServicePort
	// Extract ports from the first container
	if len(runtime.Spec.RuntimePodTemplate.Template.Spec.Containers) > 0 {
		for _, port := range runtime.Spec.RuntimePodTemplate.Template.Spec.Containers[0].Ports {
			ports = append(ports, corev1.ServicePort{
				Name:       port.Name,
				Port:       port.ContainerPort,
				TargetPort: intstr.FromInt(int(port.ContainerPort)),
			})
		}
	}
	// Fallback if no ports defined, though ideally CR should have them
	if len(ports) == 0 {
		ports = append(ports, corev1.ServicePort{
			Name:       "grpc",
			Port:       10000,
			TargetPort: intstr.FromInt(10000),
		})
	}

	svc := &corev1.Service{
		ObjectMeta: metav1.ObjectMeta{
			Name:      serviceName,
			Namespace: runtime.Namespace,
			Labels:    label,
		},
		Spec: corev1.ServiceSpec{
			ClusterIP: "None", // Headless Service
			Selector:  label,
			Ports:     ports,
		},
	}
	_ = controllerutil.SetControllerReference(runtime, svc, r.Scheme)
	return svc
}

func getRuntimePodSecurityContext(runtime *eventmeshoperatorv1.Runtime) *corev1.PodSecurityContext {
	var securityContext = corev1.PodSecurityContext{}
	if runtime.Spec.PodSecurityContext != nil {
		securityContext = *runtime.Spec.PodSecurityContext
	}
	return &securityContext
}

func getContainerSecurityContext(runtime *eventmeshoperatorv1.Runtime) *corev1.SecurityContext {
	var securityContext = corev1.SecurityContext{}
	if runtime.Spec.ContainerSecurityContext != nil {
		securityContext = *runtime.Spec.ContainerSecurityContext
	}
	return &securityContext
}

func getLabels(name string) map[string]string {
	return map[string]string{
		"app":      "eventmesh-runtime",
		"instance": name,
	}
}
