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
	"strconv"
	"time"
)

// EventMeshOperatorReconciler reconciles a EventMeshOperator object
type EventMeshOperatorReconciler struct {
	client.Client
	Scheme *runtime.Scheme
	Logger logr.Logger
}

//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=eventmeshoperators,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=eventmeshoperators/status,verbs=get;update;patch
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=eventmeshoperators/finalizers,verbs=update

// Reconcile is part of the main kubernetes reconciliation loop which aims to
// move the current state of the cluster closer to the desired state.
// TODO(user): Modify the Reconcile function to compare the state specified by
// the EventMeshOperator object against the actual cluster state, and then
// perform operations to make the cluster state reflect the state specified by
// the user.
//
// For more details, check Reconcile and its Result here:
// - https://pkg.go.dev/sigs.k8s.io/controller-runtime@v0.14.1/pkg/reconcile
func (r *EventMeshOperatorReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	r.Logger.Info("eventmesh start reconciling",
		"Namespace", req.Namespace, "Namespace", req.Name)

	eventmesh := &eventmeshoperatorv1.EventMeshOperator{}
	err := r.Client.Get(context.TODO(), req.NamespacedName, eventmesh)
	if err != nil {
		// If it's a not found exception, it means the cr has been deleted.
		if errors.IsNotFound(err) {
			r.Logger.Info("eventmesh resource not found. Ignoring since object must be deleted.")
			return ctrl.Result{}, err
		}
		r.Logger.Error(err, "Failed to get eventmesh")
		return ctrl.Result{}, err
	}
	r.Logger.Info("get eventmesh object", "name", eventmesh.Name)

	if eventmesh.Status.Size == 0 {
		GroupNum = eventmesh.Spec.Size
	} else {
		GroupNum = eventmesh.Status.Size
	}

	replicaPerGroup := eventmesh.Spec.ReplicaPerGroup
	r.Logger.Info("GroupNum=" + strconv.Itoa(GroupNum) + ", replicaPerGroup=" + strconv.Itoa(replicaPerGroup))

	for groupIndex := 0; groupIndex < GroupNum; groupIndex++ {
		r.Logger.Info("Check eventmesh cluster " + strconv.Itoa(groupIndex+1) + "/" + strconv.Itoa(GroupNum))
		deployment := r.getEventMeshStatefulSet(eventmesh, groupIndex, 0)
		// Check if the statefulSet already exists, if not create a new one
		found := &appsv1.StatefulSet{}
		err = r.Client.Get(context.TODO(), types.NamespacedName{
			Name:      deployment.Name,
			Namespace: deployment.Namespace,
		}, found)
		if err != nil && errors.IsNotFound(err) {
			r.Logger.Info("Creating a new eventmesh StatefulSet.",
				"StatefulSet.Namespace", deployment.Namespace,
				"StatefulSet.Name", deployment.Name)
			err = r.Client.Create(context.TODO(), deployment)
			if err != nil {
				r.Logger.Error(err, "Failed to create new StatefulSet",
					"StatefulSet.Namespace", deployment.Namespace,
					"StatefulSet.Name", deployment.Name)
			}
		} else if err != nil {
			r.Logger.Error(err, "Failed to get eventmesh StatefulSet.")
		}
	}

	if eventmesh.Spec.AllowRestart {
		for groupIndex := 0; groupIndex < eventmesh.Spec.Size; groupIndex++ {
			eventmeshName := eventmesh.Name + "-" + strconv.Itoa(groupIndex)
			r.Logger.Info("update eventmesh", eventmeshName)
			// update
			deployment := r.getEventMeshStatefulSet(eventmesh, groupIndex, 0)
			found := &appsv1.StatefulSet{}
			err = r.Client.Get(context.TODO(), types.NamespacedName{
				Name:      deployment.Name,
				Namespace: deployment.Namespace,
			}, found)
			if err != nil {
				r.Logger.Error(err, "Failed to get eventmesh StatefulSet.")
			} else {
				err = r.Client.Update(context.TODO(), found)
				if err != nil {
					r.Logger.Error(err, "Failed to update eventmesh"+eventmeshName,
						"StatefulSet.Namespace", found.Namespace, "StatefulSet.Name", found.Name)
				} else {
					r.Logger.Info("Successfully update"+eventmeshName,
						"StatefulSet.Namespace", found.Namespace, "StatefulSet.Name", found.Name)
				}
				time.Sleep(time.Duration(1) * time.Second)
			}
		}
	}

	podList := &corev1.PodList{}
	labelSelector := labels.SelectorFromSet(getLabels(eventmesh.Name))
	listOps := &client.ListOptions{
		Namespace:     eventmesh.Namespace,
		LabelSelector: labelSelector,
	}
	err = r.Client.List(context.TODO(), podList, listOps)
	if err != nil {
		r.Logger.Error(err, "Failed to list pods.",
			"eventmesh.Namespace", eventmesh.Namespace, "eventmesh.Name", eventmesh.Name)
		return ctrl.Result{}, err
	}

	podNames := getPodNames(podList.Items)
	r.Logger.Info("broker.Status.Nodes length = " + strconv.Itoa(len(eventmesh.Status.Nodes)))
	r.Logger.Info("podNames length = " + strconv.Itoa(len(podNames)))
	// Ensure every pod is in running phase
	for _, pod := range podList.Items {
		if !reflect.DeepEqual(pod.Status.Phase, corev1.PodRunning) {
			r.Logger.Info("pod " + pod.Name + " phase is " + string(pod.Status.Phase) + ", wait for a moment...")
		}
	}

	// Update status.Size if needed
	if eventmesh.Spec.Size != eventmesh.Status.Size {
		r.Logger.Info("eventmesh.Status.Size = " + strconv.Itoa(eventmesh.Status.Size))
		r.Logger.Info("eventmesh.Spec.Size = " + strconv.Itoa(eventmesh.Spec.Size))
		eventmesh.Status.Size = eventmesh.Spec.Size
		err = r.Client.Status().Update(context.TODO(), eventmesh)
		if err != nil {
			r.Logger.Error(err, "Failed to update eventmesh Size status.")
		}
	}

	// Update status.Nodes if needed
	if !reflect.DeepEqual(podNames, eventmesh.Status.Nodes) {
		eventmesh.Status.Nodes = podNames
		err = r.Client.Status().Update(context.TODO(), eventmesh)
		if err != nil {
			r.Logger.Error(err, "Failed to update eventmesh Nodes status.")
		}
	}
	return ctrl.Result{}, nil
}

func getPodNames(pods []corev1.Pod) []string {
	var podNames []string
	for _, pod := range pods {
		podNames = append(podNames, pod.Name)
	}
	return podNames
}

var GroupNum = 0

// SetupWithManager sets up the controller with the Manager.
func (r *EventMeshOperatorReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&eventmeshoperatorv1.EventMeshOperator{}).
		Complete(r)
}

func (r *EventMeshOperatorReconciler) getEventMeshStatefulSet(eventmesh *eventmeshoperatorv1.EventMeshOperator, groupIndex int, replicaIndex int) *appsv1.StatefulSet {
	var statefulSetName string
	var a int32 = 1
	var c = &a
	if replicaIndex == 0 {
		statefulSetName = eventmesh.Name + "-" + strconv.Itoa(groupIndex) + "-A"
	} else {
		statefulSetName = eventmesh.Name + "-" + strconv.Itoa(groupIndex) + "-R-" + strconv.Itoa(replicaIndex)
	}
	label := getLabels(eventmesh.Name)
	deployment := &appsv1.StatefulSet{
		ObjectMeta: metav1.ObjectMeta{
			Name:      statefulSetName,
			Namespace: eventmesh.Namespace,
		},
		Spec: appsv1.StatefulSetSpec{
			Replicas: c,
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
					DNSPolicy:         corev1.DNSClusterFirstWithHostNet,
					Affinity:          eventmesh.Spec.Affinity,
					Tolerations:       eventmesh.Spec.Tolerations,
					NodeSelector:      eventmesh.Spec.NodeSelector,
					PriorityClassName: eventmesh.Spec.PriorityClassName,
					Containers: []corev1.Container{{
						Image:           eventmesh.Spec.Image,
						Name:            eventmesh.Name,
						SecurityContext: getContainerSecurityContext(eventmesh),
						ImagePullPolicy: eventmesh.Spec.ImagePullPolicy,
					}},
					SecurityContext: getPodSecurityContext(eventmesh),
				},
			},
		},
	}
	_ = controllerutil.SetControllerReference(eventmesh, deployment, r.Scheme)
	return deployment
}

func getPodSecurityContext(eventmesh *eventmeshoperatorv1.EventMeshOperator) *corev1.PodSecurityContext {
	var securityContext = corev1.PodSecurityContext{}
	if eventmesh.Spec.PodSecurityContext != nil {
		securityContext = *eventmesh.Spec.PodSecurityContext
	}
	return &securityContext
}

func getContainerSecurityContext(eventmesh *eventmeshoperatorv1.EventMeshOperator) *corev1.SecurityContext {
	var securityContext = corev1.SecurityContext{}
	if eventmesh.Spec.ContainerSecurityContext != nil {
		securityContext = *eventmesh.Spec.ContainerSecurityContext
	}
	return &securityContext
}

func getLabels(name string) map[string]string {
	return map[string]string{"app": "eventmesh", "runtime_cr": name}
}
