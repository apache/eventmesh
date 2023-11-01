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
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"reflect"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
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

	connectorDeployment := r.newConnectorDeploymentForCR(connector)

	found := &appsv1.Deployment{}
	err = r.Client.Get(context.TODO(), types.NamespacedName{
		Name:      connectorDeployment.Name,
		Namespace: connectorDeployment.Namespace,
	}, found)

	if err != nil && errors.IsNotFound(err) {
		r.Logger.Info("Creating RocketMQ Console Deployment",
			"Namespace", connectorDeployment.Namespace, "Name", connectorDeployment.Name)
		err = r.Client.Create(context.TODO(), connectorDeployment)
		if err != nil {
			r.Logger.Error(err, "Failed to create new connectorDeployment",
				"Namespace", connectorDeployment.Namespace,
				"Name", connectorDeployment.Name)
			return ctrl.Result{}, err
		}
		return ctrl.Result{}, nil
	} else if err != nil {
		return ctrl.Result{}, err
	}

	if !reflect.DeepEqual(connector.Spec.ConnectorDeployment.Spec.Replicas, found.Spec.Replicas) {
		found.Spec.Replicas = connector.Spec.ConnectorDeployment.Spec.Replicas
		err = r.Client.Update(context.TODO(), found)
		if err != nil {
			r.Logger.Error(err, "Failed to update connector CR ",
				"Namespace", found.Namespace, "Name", found.Name)
		} else {
			r.Logger.Info("Successfully connector CR ",
				"Namespace", found.Namespace, "Name", found.Name)
		}
	}
	r.Logger.Info("Connector Deployment already exists",
		"Namespace", found.Namespace, "Name", found.Name)
	return ctrl.Result{}, nil
}

func (r ConnectorsReconciler) newConnectorDeploymentForCR(connector *eventmeshoperatorv1.Connectors) *appsv1.Deployment {
	deployment := &appsv1.Deployment{
		ObjectMeta: metav1.ObjectMeta{
			Name:      connector.Name,
			Namespace: connector.Namespace,
		},
		Spec: appsv1.DeploymentSpec{
			Replicas: connector.Spec.ConnectorDeployment.Spec.Replicas,
			Selector: &metav1.LabelSelector{
				MatchLabels: connector.Spec.ConnectorDeployment.Spec.Selector.MatchLabels,
			},
			Template: corev1.PodTemplateSpec{
				ObjectMeta: metav1.ObjectMeta{
					Labels: connector.Spec.ConnectorDeployment.Spec.Template.ObjectMeta.Labels,
				},
				Spec: corev1.PodSpec{
					ServiceAccountName: connector.Spec.ConnectorDeployment.Spec.Template.Spec.ServiceAccountName,
					Affinity:           connector.Spec.ConnectorDeployment.Spec.Template.Spec.Affinity,
					ImagePullSecrets:   connector.Spec.ConnectorDeployment.Spec.Template.Spec.ImagePullSecrets,
					Containers: []corev1.Container{{
						Resources:       connector.Spec.ConnectorDeployment.Spec.Template.Spec.Containers[0].Resources,
						Image:           connector.Spec.ConnectorDeployment.Spec.Template.Spec.Containers[0].Image,
						Args:            connector.Spec.ConnectorDeployment.Spec.Template.Spec.Containers[0].Args,
						Name:            connector.Spec.ConnectorDeployment.Spec.Template.Spec.Containers[0].Name,
						ImagePullPolicy: connector.Spec.ConnectorDeployment.Spec.Template.Spec.Containers[0].ImagePullPolicy,
						Ports:           connector.Spec.ConnectorDeployment.Spec.Template.Spec.Containers[0].Ports,
					}},
				},
			},
		},
	}
	_ = controllerutil.SetControllerReference(connector, deployment, r.Scheme)
	return deployment
}

// SetupWithManager sets up the controller with the Manager.
func (r ConnectorsReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&eventmeshoperatorv1.Connectors{}).
		Complete(r)
}
