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

package eventmesh_runtime

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

	// TODO(user): Modify this to be the types you create that are owned by the primary resource
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

//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=runtime,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=runtime/status,verbs=get;update;patch
//+kubebuilder:rbac:groups=eventmesh-operator.eventmesh,resources=runtime/finalizers,verbs=update
//+kubebuilder:rbac:groups="",resources=pods,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups="",resources=pods/exec,verbs=get;list;watch;create;update;patch;delete
//+kubebuilder:rbac:groups="apps",resources=statefulsets,verbs=get;list;watch;create;update;patch;delete

// Reconcile is part of the main kubernetes reconciliation loop which aims to
// move the current state of the cluster closer to the desired state.
// TODO(user): Modify the Reconcile function to compare the state specified by
// the EventMeshOperator object against the actual cluster state, and then
// perform operations to make the cluster state reflect the state specified by
// the user.
//
// For more details, check Reconcile and its Result here:
// - https://pkg.go.dev/sigs.k8s.io/controller-runtime@v0.14.1/pkg/reconcile
func (r *RuntimeReconciler) Reconcile(ctx context.Context, req reconcile.Request) (reconcile.Result, error) {
	r.Logger.Info("eventMeshRuntime start reconciling",
		"Namespace", req.Namespace, "Namespace", req.Name)

	eventMeshRuntime := &eventmeshoperatorv1.Runtime{}
	err := r.Client.Get(context.TODO(), req.NamespacedName, eventMeshRuntime)
	if err != nil {
		// If it's a not found exception, it means the cr has been deleted.
		if errors.IsNotFound(err) {
			r.Logger.Info("eventMeshRuntime resource not found. Ignoring since object must be deleted.")
			return reconcile.Result{}, err
		}
		r.Logger.Error(err, "Failed to get eventMeshRuntime")
		return reconcile.Result{}, err
	}
	r.Logger.Info("get eventMeshRuntime object", "name", eventMeshRuntime.Name)

	if eventMeshRuntime.Status.Size == 0 {
		GroupNum = eventMeshRuntime.Spec.Size
	} else {
		GroupNum = eventMeshRuntime.Status.Size
	}

	replicaPerGroup := eventMeshRuntime.Spec.ReplicaPerGroup
	r.Logger.Info("GroupNum=" + strconv.Itoa(GroupNum) + ", replicaPerGroup=" + strconv.Itoa(replicaPerGroup))

	for groupIndex := 0; groupIndex < GroupNum; groupIndex++ {
		r.Logger.Info("Check eventMeshRuntime cluster " + strconv.Itoa(groupIndex+1) + "/" + strconv.Itoa(GroupNum))
		runtimeDep := r.getEventMeshRuntimeStatefulSet(eventMeshRuntime, groupIndex, 0)
		//r.Logger.Info(fmt.Sprintf("%s", runtimeDep))
		// Check if the statefulSet already exists, if not create a new one
		found := &appsv1.StatefulSet{}
		err = r.Client.Get(context.TODO(), types.NamespacedName{
			Name:      runtimeDep.Name,
			Namespace: runtimeDep.Namespace,
		}, found)
		if err != nil && errors.IsNotFound(err) {
			r.Logger.Info("Creating a new eventMeshRuntime StatefulSet.",
				"StatefulSet.Namespace", runtimeDep.Namespace,
				"StatefulSet.Name", runtimeDep.Name)
			err = r.Client.Create(context.TODO(), runtimeDep)
			if err != nil {
				r.Logger.Error(err, "Failed to create new StatefulSet",
					"StatefulSet.Namespace", runtimeDep.Namespace,
					"StatefulSet.Name", runtimeDep.Name)
			}
			time.Sleep(time.Duration(3) * time.Second)
		} else if err != nil {
			r.Logger.Error(err, "Failed to get eventMeshRuntime StatefulSet.")
		}
	}
	if eventMeshRuntime.Spec.AllowRestart {
		for groupIndex := 0; groupIndex < eventMeshRuntime.Spec.Size; groupIndex++ {
			runtimeName := eventMeshRuntime.Name + "-" + strconv.Itoa(groupIndex)
			r.Logger.Info("update eventMeshRuntime", "runtimeName", runtimeName)
			// update
			deployment := r.getEventMeshRuntimeStatefulSet(eventMeshRuntime, groupIndex, 0)
			found := &appsv1.StatefulSet{}
			err = r.Client.Get(context.TODO(), types.NamespacedName{
				Name:      deployment.Name,
				Namespace: deployment.Namespace,
			}, found)
			if err != nil {
				r.Logger.Error(err, "Failed to get eventMeshRuntime StatefulSet.")
			} else {
				err = r.Client.Update(context.TODO(), found)
				if err != nil {
					r.Logger.Error(err, "Failed to update eventMeshRuntime "+runtimeName,
						"StatefulSet.Namespace", found.Namespace, "StatefulSet.Name", found.Name)
				} else {
					r.Logger.Info("Successfully update "+runtimeName,
						"StatefulSet.Namespace", found.Namespace, "StatefulSet.Name", found.Name)
				}
				time.Sleep(time.Duration(1) * time.Second)
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
	r.Logger.Info(fmt.Sprintf("Stutas.Nodes = %s", eventMeshRuntime.Status.Nodes))
	r.Logger.Info(fmt.Sprintf("podNames = %s", podNames))
	// Ensure every pod is in running phase
	for _, pod := range podList.Items {
		if !reflect.DeepEqual(pod.Status.Phase, corev1.PodRunning) {
			r.Logger.Info("pod " + pod.Name + " phase is " + string(pod.Status.Phase) + ", wait for a moment...")
		}
	}

	if podNames != nil {
		eventMeshRuntime.Status.Nodes = podNames
		r.Logger.Info(fmt.Sprintf("eventMeshRuntime.Stutas.Nodes = %s", eventMeshRuntime.Status.Nodes))
		// Update status.Size if needed
		if eventMeshRuntime.Spec.Size != eventMeshRuntime.Status.Size {
			r.Logger.Info("eventMeshRuntime.Status.Size = " + strconv.Itoa(eventMeshRuntime.Status.Size))
			r.Logger.Info("eventMeshRuntime.Spec.Size = " + strconv.Itoa(eventMeshRuntime.Spec.Size))
			eventMeshRuntime.Status.Size = eventMeshRuntime.Spec.Size
			err = r.Client.Status().Update(context.TODO(), eventMeshRuntime)
			if err != nil {
				r.Logger.Error(err, "Failed to update eventMeshRuntime Size status.")
			}
		}

		// Update status.Nodes if needed
		if !reflect.DeepEqual(podNames, eventMeshRuntime.Status.Nodes) {
			err = r.Client.Status().Update(context.TODO(), eventMeshRuntime)
			if err != nil {
				r.Logger.Error(err, "Failed to update eventMeshRuntime Nodes status.")
			}
		}
	} else {
		r.Logger.Error(err, "Not found eventmesh runtime pods")
	}

	r.Logger.Info("Successful reconciliation!")
	return reconcile.Result{}, nil
}

func getRuntimePodNames(pods []corev1.Pod) []string {
	var podNames []string
	for _, pod := range pods {
		podNames = append(podNames, pod.Name)
	}
	return podNames
}

var GroupNum = 0

func (r *RuntimeReconciler) getEventMeshRuntimeStatefulSet(runtime *eventmeshoperatorv1.Runtime, groupIndex int, replicaIndex int) *appsv1.StatefulSet {
	var statefulSetName string
	var a int32 = 1
	var c = &a
	if replicaIndex == 0 {
		statefulSetName = runtime.Name + "-" + strconv.Itoa(groupIndex) + "-a"
	} else {
		statefulSetName = runtime.Name + "-" + strconv.Itoa(groupIndex) + "-r-" + strconv.Itoa(replicaIndex)
	}
	label := getLabels(runtime.Name)
	deployment := &appsv1.StatefulSet{
		ObjectMeta: metav1.ObjectMeta{
			Name:      statefulSetName,
			Namespace: runtime.Namespace,
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
					Affinity:          runtime.Spec.RuntimePodTemplate.Template.Spec.Affinity,
					Tolerations:       runtime.Spec.RuntimePodTemplate.Template.Spec.Tolerations,
					NodeSelector:      runtime.Spec.RuntimePodTemplate.Template.Spec.NodeSelector,
					PriorityClassName: runtime.Spec.RuntimePodTemplate.Template.Spec.PriorityClassName,
					HostNetwork:       runtime.Spec.RuntimePodTemplate.Template.Spec.HostNetwork,
					Containers: []corev1.Container{{
						Image:           runtime.Spec.RuntimePodTemplate.Template.Spec.Containers[0].Image,
						Name:            runtime.Spec.RuntimePodTemplate.Template.Spec.Containers[0].Name,
						SecurityContext: getContainerSecurityContext(runtime),
						ImagePullPolicy: runtime.Spec.RuntimePodTemplate.Template.Spec.Containers[0].ImagePullPolicy,
						Ports:           runtime.Spec.RuntimePodTemplate.Template.Spec.Containers[0].Ports,
						VolumeMounts:    runtime.Spec.RuntimePodTemplate.Template.Spec.Containers[0].VolumeMounts,
					}},
					Volumes:         runtime.Spec.RuntimePodTemplate.Template.Spec.Volumes,
					SecurityContext: getRuntimePodSecurityContext(runtime),
				},
			},
		},
	}
	_ = controllerutil.SetControllerReference(runtime, deployment, r.Scheme)
	return deployment
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
	return map[string]string{"app": "eventmesh-runtime", "runtime": name}
}
