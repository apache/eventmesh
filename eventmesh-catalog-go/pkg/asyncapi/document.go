// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package asyncapi

// Document is an object representing an AsyncAPI document.
// NOTE: this interface is not completed yet.
type Document interface {
	Extendable
	Channels() []Channel
	HasChannels() bool
	ApplicationPublishableChannels() []Channel
	ApplicationPublishableMessages() []Message
	ApplicationPublishOperations() []Operation
	ApplicationSubscribableChannels() []Channel
	ApplicationSubscribableMessages() []Message
	ApplicationSubscribeOperations() []Operation
	ClientPublishableChannels() []Channel
	ClientPublishableMessages() []Message
	ClientPublishOperations() []Operation
	ClientSubscribableChannels() []Channel
	ClientSubscribableMessages() []Message
	ClientSubscribeOperations() []Operation
	Messages() []Message
	Server(name string) (Server, bool)
	Servers() []Server
	HasServers() bool
	Info() Info
}

type Info interface {
	Title() string
	Version() string
}

// Channel is an addressable component, made available by the server, for the organization of messages.
// Producer applications send messages to channels and consumer applications consume messages from channels.
type Channel interface {
	Extendable
	Identifiable
	Describable
	Path() string // Path is the identifier
	Parameters() []ChannelParameter
	HasParameters() bool
	Operations() []Operation
	Messages() []Message
}

// ChannelParameter describes a parameter included in a channel name.
type ChannelParameter interface {
	Extendable
	Identifiable
	Describable
	Name() string
	Schema() Schema
}

// OperationType is the type of an operation.
type OperationType string

// Operation describes a publish or a subscribe operation.
// This provides a place to document how and why messages are sent and received.
type Operation interface {
	Extendable
	Describable
	ID() string
	IsApplicationPublishing() bool
	IsApplicationSubscribing() bool
	IsClientPublishing() bool
	IsClientSubscribing() bool
	Messages() []Message
	Summary() string
	HasSummary() bool
	Type() OperationType
}

// Message describes a message received on a given channel and operation.
type Message interface {
	Extendable
	Describable
	UID() string
	Name() string
	Title() string
	HasTitle() bool
	Summary() string
	HasSummary() bool
	ContentType() string
	Payload() Schema
}

// FalsifiableSchema is a variadic type used for some Schema fields.
// For example, additionalProperties value can be either `false` or a Schema.
type FalsifiableSchema interface {
	IsFalse() bool
	IsSchema() bool
	Schema() Schema
}

// Schema is an object that allows the definition of input and output data types.
// These types can be objects, but also primitives and arrays.
// This object is a superset of the JSON Schema Specification Draft 07.
type Schema interface {
	Extendable
	ID() string
	AdditionalItems() FalsifiableSchema
	AdditionalProperties() FalsifiableSchema
	AllOf() []Schema
	AnyOf() []Schema
	CircularProps() []string
	Const() interface{}
	Contains() Schema
	ContentEncoding() string
	ContentMediaType() string
	Default() interface{}
	Definitions() map[string]Schema
	Dependencies() map[string]Schema
	Deprecated() bool
	Description() string
	Discriminator() string
	Else() Schema
	Enum() []interface{}
	Examples() []interface{}
	ExclusiveMaximum() *float64
	ExclusiveMinimum() *float64
	Format() string
	HasCircularProps() bool
	If() Schema
	IsCircular() bool
	Items() []Schema
	Maximum() *float64
	MaxItems() *float64
	MaxLength() *float64
	MaxProperties() *float64
	Minimum() *float64
	MinItems() *float64
	MinLength() *float64
	MinProperties() *float64
	MultipleOf() *float64
	Not() Schema
	OneOf() []Schema
	Pattern() string
	PatternProperties() map[string]Schema
	Properties() map[string]Schema
	Property(name string) Schema
	PropertyNames() Schema
	ReadOnly() bool
	Required() []string
	Then() Schema
	Title() string
	Type() []string
	UID() string
	UniqueItems() bool
	WriteOnly() bool
}

// Server is an object representing a message broker, a server or any other kind of computer program capable of
// sending and/or receiving data.
type Server interface {
	Extendable
	Identifiable
	Describable
	Name() string
	HasName() bool
	URL() string
	HasURL() bool
	Protocol() string
	HasProtocol() bool
	Variables() []ServerVariable
}

// ServerVariable is an object representing a Server Variable for server URL template substitution.
type ServerVariable interface {
	Extendable
	Identifiable
	Name() string
	HasName() bool
	DefaultValue() string
	AllowedValues() []string // Parser API spec says any[], but AsyncAPI mentions is []string
}

// Extendable means the object can have extensions.
// The extensions properties are implemented as patterned fields that are always prefixed by "x-".
// See https://www.asyncapi.com/docs/specifications/v2.0.0#specificationExtensions.
type Extendable interface {
	HasExtension(name string) bool
	Extension(name string) interface{}
}

// Describable means the object can have a description.
type Describable interface {
	Description() string
	HasDescription() bool
}

// Identifiable identifies objects. Some objects can have fields that identify themselves as unique resources.
// For example: `id` and `name` fields.
type Identifiable interface {
	IDField() string
	ID() string
}
