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
	v1 "k8s.io/api/apps/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// EDIT THIS FILE!  THIS IS SCAFFOLDING FOR YOU TO OWN!
// NOTE: json tags are required.  Any new fields you add must have json tags for the fields to be serialized.

// ConnectorsSpec defines the desired state of ConnectorsSpec
type ConnectorsSpec struct {
	// INSERT ADDITIONAL SPEC FIELDS - desired state of cluster
	// Important: Run "make" to regenerate code after modifying this file
	// Foo is an example field of Connectors. Edit connectors_types.go to remove/update

	// Specify the container in detail if needed
	ConnectorDeployment v1.Deployment `json:"connectorDeployment"`
}

// ConnectorsStatus defines the observed state of ConnectorsStatus
type ConnectorsStatus struct {
	// INSERT ADDITIONAL STATUS FIELD - define observed state of cluster
	// Important: Run "make" to regenerate code after modifying this file

}

//+kubebuilder:object:root=true
//+kubebuilder:subresource:status

// Connectors is the Schema for the Connectors API
type Connectors struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	Spec   ConnectorsSpec   `json:"spec,omitempty"`
	Status ConnectorsStatus `json:"status,omitempty"`
}

//+kubebuilder:object:root=true

// ConnectorsList contains a list of Connectors
type ConnectorsList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []Connectors `json:"items"`
}

func init() {
	SchemeBuilder.Register(&Connectors{}, &ConnectorsList{})
}
