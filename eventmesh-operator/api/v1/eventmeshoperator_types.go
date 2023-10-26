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

package v1

import (
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// EDIT THIS FILE!  THIS IS SCAFFOLDING FOR YOU TO OWN!
// NOTE: json tags are required.  Any new fields you add must have json tags for the fields to be serialized.

// EventMeshOperatorSpec defines the desired state of EventMeshOperator
type EventMeshOperatorSpec struct {
	// INSERT ADDITIONAL SPEC FIELDS - desired state of cluster
	// Important: Run "make" to regenerate code after modifying this file

	// Foo is an example field of EventMeshOperator. Edit eventmeshoperator_types.go to remove/update
	Size int `json:"size"`
	// Image
	Image string `json:"image"`
	// Define the mirror pulling policy
	ImagePullPolicy corev1.PullPolicy `json:"imagePullPolicy"`
	// AllowRestart defines whether allow pod restart
	AllowRestart bool `json:"allowRestart"`

	ReplicaPerGroup int `json:"replicaPerGroup"`

	// Pod Security Context
	PodSecurityContext *corev1.PodSecurityContext `json:"securityContext,omitempty"`
	// Container Security Context
	ContainerSecurityContext *corev1.SecurityContext `json:"containerSecurityContext,omitempty"`
	// Affinity the pod's scheduling constraints
	Affinity *corev1.Affinity `json:"affinity,omitempty"`
	// Tolerations the pod's tolerations.
	Tolerations []corev1.Toleration `json:"tolerations,omitempty"`
	// NodeSelector is a selector which must be true for the pod to fit on a node
	NodeSelector map[string]string `json:"nodeSelector,omitempty"`
	// PriorityClassName indicates the pod's priority
	PriorityClassName string `json:"priorityClassName,omitempty"`
}

// EventMeshOperatorStatus defines the observed state of EventMeshOperator
type EventMeshOperatorStatus struct {
	// INSERT ADDITIONAL STATUS FIELD - define observed state of cluster
	// Important: Run "make" to regenerate code after modifying this file
	Size int `json:"size"`

	Nodes []string `json:"nodes"`
}

//+kubebuilder:object:root=true
//+kubebuilder:subresource:status

// EventMeshOperator is the Schema for the eventmeshoperators API
type EventMeshOperator struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	Spec   EventMeshOperatorSpec   `json:"spec,omitempty"`
	Status EventMeshOperatorStatus `json:"status,omitempty"`
}

//+kubebuilder:object:root=true

// EventMeshOperatorList contains a list of EventMeshOperator
type EventMeshOperatorList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []EventMeshOperator `json:"items"`
}

func init() {
	SchemeBuilder.Register(&EventMeshOperator{}, &EventMeshOperatorList{})
}
