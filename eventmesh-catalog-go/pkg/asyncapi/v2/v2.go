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

package v2

import (
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/pkg/asyncapi"
)

// Constants for Operation Types.
const (
	OperationTypePublish   asyncapi.OperationType = "publish"
	OperationTypeSubscribe asyncapi.OperationType = "subscribe"
)

type Info struct {
	TitleField   string `mapstructure:"title" json:"title"`
	VersionField string `mapstructure:"version" json:"version"`
}

func (info *Info) Title() string {
	return info.TitleField
}

func (info *Info) Version() string {
	return info.VersionField
}

type Document struct {
	Extendable
	InfoField     Info               `mapstructure:"info" json:"info"`
	ServersField  map[string]Server  `mapstructure:"servers" json:"servers"`
	ChannelsField map[string]Channel `mapstructure:"channels" json:"channels"`
}

func (d Document) Info() asyncapi.Info {
	return &d.InfoField
}

func (d Document) ApplicationPublishableChannels() []asyncapi.Channel {
	return d.filterChannels(func(operation asyncapi.Operation) bool {
		return operation.IsApplicationPublishing()
	})
}

func (d Document) ApplicationPublishableMessages() []asyncapi.Message {
	return d.filterMessages(func(operation asyncapi.Operation) bool {
		return operation.IsApplicationPublishing()
	})
}

func (d Document) ApplicationPublishOperations() []asyncapi.Operation {
	return d.filterOperations(func(operation asyncapi.Operation) bool {
		return operation.IsApplicationPublishing()
	})
}

func (d Document) ApplicationSubscribableChannels() []asyncapi.Channel {
	return d.filterChannels(func(operation asyncapi.Operation) bool {
		return operation.IsApplicationSubscribing()
	})
}

func (d Document) ApplicationSubscribableMessages() []asyncapi.Message {
	return d.filterMessages(func(operation asyncapi.Operation) bool {
		return operation.IsApplicationSubscribing()
	})
}

func (d Document) ApplicationSubscribeOperations() []asyncapi.Operation {
	return d.filterOperations(func(operation asyncapi.Operation) bool {
		return operation.IsApplicationSubscribing()
	})
}

func (d Document) Channels() []asyncapi.Channel {
	var channels []asyncapi.Channel
	for _, c := range d.ChannelsField {
		channels = append(channels, c)
	}

	return channels
}

func (d Document) HasChannels() bool {
	return len(d.ChannelsField) > 0
}

func (d Document) ClientPublishableChannels() []asyncapi.Channel {
	return d.filterChannels(func(operation asyncapi.Operation) bool {
		return operation.IsClientPublishing()
	})
}

func (d Document) ClientPublishableMessages() []asyncapi.Message {
	return d.filterMessages(func(operation asyncapi.Operation) bool {
		return operation.IsClientPublishing()
	})
}

func (d Document) ClientPublishOperations() []asyncapi.Operation {
	return d.filterOperations(func(operation asyncapi.Operation) bool {
		return operation.IsClientPublishing()
	})
}

func (d Document) ClientSubscribableChannels() []asyncapi.Channel {
	return d.filterChannels(func(operation asyncapi.Operation) bool {
		return operation.IsClientSubscribing()
	})
}

func (d Document) ClientSubscribableMessages() []asyncapi.Message {
	return d.filterMessages(func(operation asyncapi.Operation) bool {
		return operation.IsClientSubscribing()
	})
}

func (d Document) ClientSubscribeOperations() []asyncapi.Operation {
	return d.filterOperations(func(operation asyncapi.Operation) bool {
		return operation.IsClientSubscribing()
	})
}

func (d Document) Messages() []asyncapi.Message {
	var messages []asyncapi.Message
	for _, c := range d.Channels() {
		messages = append(messages, c.Messages()...)
	}

	return messages
}

func (d Document) Server(name string) (asyncapi.Server, bool) {
	s, ok := d.ServersField[name]
	return s, ok
}

func (d Document) Servers() []asyncapi.Server {
	var servers []asyncapi.Server
	for _, s := range d.ServersField {
		servers = append(servers, s)
	}

	return servers
}

func (d Document) HasServers() bool {
	return len(d.ServersField) > 0
}

func (d Document) filterChannels(filter func(operation asyncapi.Operation) bool) []asyncapi.Channel {
	var channels []asyncapi.Channel
	for _, c := range d.Channels() {
		for _, o := range c.Operations() {
			if filter(o) {
				channels = append(channels, c)
			}
		}
	}

	return channels
}

func (d Document) filterMessages(filter func(operation asyncapi.Operation) bool) []asyncapi.Message {
	var messages []asyncapi.Message
	for _, c := range d.Channels() {
		for _, o := range c.Operations() {
			if filter(o) {
				messages = append(messages, o.Messages()...)
			}
		}
	}

	return messages
}

func (d Document) filterOperations(filter func(operation asyncapi.Operation) bool) []asyncapi.Operation {
	var operations []asyncapi.Operation
	for _, c := range d.Channels() {
		for _, o := range c.Operations() {
			if filter(o) {
				operations = append(operations, o)
			}
		}
	}

	return operations
}

// NewChannel creates a new Channel. Useful for testing.
func NewChannel(path string) *Channel {
	return &Channel{
		PathField: path,
	}
}

type Channel struct {
	Extendable
	Describable     `mapstructure:",squash"`
	PathField       string                      `mapstructure:"path" json:"path"`
	ParametersField map[string]ChannelParameter `mapstructure:"parameters" json:"parameters"`
	Subscribe       *SubscribeOperation         `mapstructure:"subscribe" json:"subscribe"`
	Publish         *PublishOperation           `mapstructure:"publish" json:"publish"`
}

func (c Channel) IDField() string {
	return "path"
}

func (c Channel) ID() string {
	return c.PathField
}

func (c Channel) Path() string {
	return c.PathField
}

func (c Channel) Parameters() []asyncapi.ChannelParameter {
	var parameters []asyncapi.ChannelParameter
	for _, p := range c.ParametersField {
		parameters = append(parameters, p)
	}

	return parameters
}

func (c Channel) HasParameters() bool {
	return len(c.ParametersField) > 0
}

func (c Channel) Operations() []asyncapi.Operation {
	var operations []asyncapi.Operation
	if c.Publish != nil {
		operations = append(operations, c.Publish)
	}
	if c.Subscribe != nil {
		operations = append(operations, c.Subscribe)
	}

	return operations
}

func (c Channel) Messages() []asyncapi.Message {
	var messages []asyncapi.Message
	for _, o := range c.Operations() {
		messages = append(messages, o.Messages()...)
	}

	return messages
}

type ChannelParameter struct {
	Extendable
	Describable
	NameField   string  `mapstructure:"name"`
	SchemaField *Schema `mapstructure:"schema"`
}

func (c ChannelParameter) IDField() string {
	return "name"
}

func (c ChannelParameter) ID() string {
	return c.NameField
}

func (c ChannelParameter) Name() string {
	return c.NameField
}

func (c ChannelParameter) Schema() asyncapi.Schema {
	return c.SchemaField
}

type SubscribeOperation struct {
	Operation
}

// NewSubscribeOperation creates a new SubscribeOperation. Useful for testing.
func NewSubscribeOperation(msgs ...*Message) *SubscribeOperation {
	return &SubscribeOperation{Operation: *NewOperation(OperationTypeSubscribe, msgs...)}
}

func (o SubscribeOperation) MapStructureDefaults() map[string]interface{} {
	return map[string]interface{}{
		"operationType": OperationTypeSubscribe,
	}
}

// NewPublishOperation creates a new PublishOperation. Useful for testing.
func NewPublishOperation(msgs ...*Message) *PublishOperation {
	return &PublishOperation{Operation: *NewOperation(OperationTypePublish, msgs...)}
}

type PublishOperation struct {
	Operation
}

func (o PublishOperation) MapStructureDefaults() map[string]interface{} {
	return map[string]interface{}{
		"operationType": OperationTypePublish,
	}
}

// NewOperation creates a new Operation. Useful for testing.
func NewOperation(operationType asyncapi.OperationType, msgs ...*Message) *Operation {
	op := &Operation{
		OperationType: operationType,
	}

	if len(msgs) == 0 {
		return op
	}

	op.MessageField = *NewMessages(msgs)

	return op
}

type Operation struct {
	Extendable
	Describable      `mapstructure:",squash"`
	OperationIDField string                 `mapstructure:"operationId" json:"operationId"`
	MessageField     Messages               `mapstructure:"message" json:"message"`
	OperationType    asyncapi.OperationType `mapstructure:"operationType" json:"operationType"` // set by hook
	SummaryField     string                 `mapstructure:"summary" json:"summary"`
}

func (o Operation) ID() string {
	return o.OperationIDField
}

func (o Operation) IsApplicationPublishing() bool {
	return o.Type() == OperationTypeSubscribe
}

func (o Operation) IsApplicationSubscribing() bool {
	return o.Type() == OperationTypePublish
}

func (o Operation) IsClientPublishing() bool {
	return o.Type() == OperationTypePublish
}

func (o Operation) IsClientSubscribing() bool {
	return o.Type() == OperationTypeSubscribe
}

func (o Operation) Messages() []asyncapi.Message {
	msgs := o.MessageField.Messages()

	convertedMsgs := make([]asyncapi.Message, len(msgs)) // Go lack of covariance :/
	for i, m := range msgs {
		convertedMsgs[i] = m
	}
	return convertedMsgs
}

func (o Operation) Type() asyncapi.OperationType {
	return o.OperationType
}

func (o Operation) Summary() string {
	return o.SummaryField
}

func (o Operation) HasSummary() bool {
	return o.SummaryField != ""
}

// Messages is a variadic type for Message object, which can be either one message or oneOf.
// See https://www.asyncapi.com/docs/specifications/v2.0.0#operationObject.
type Messages struct {
	Message    `mapstructure:",squash"`
	OneOfField []*Message `mapstructure:"oneOf" json:"oneOf"`
}

func (m *Messages) Messages() []*Message {
	if len(m.OneOfField) > 0 {
		return m.OneOfField
	}

	return []*Message{&m.Message}
}

// NewMessages creates Messages.
func NewMessages(msgs []*Message) *Messages {
	if len(msgs) == 1 {
		return &Messages{Message: *msgs[0]}
	}

	return &Messages{OneOfField: msgs}
}

type Message struct {
	Extendable
	Describable      `mapstructure:",squash"`
	NameField        string  `mapstructure:"name" json:"name"`
	TitleField       string  `mapstructure:"title" json:"title"`
	SummaryField     string  `mapstructure:"summary" json:"summary"`
	ContentTypeField string  `mapstructure:"contentType" json:"contentType"`
	PayloadField     *Schema `mapstructure:"payload" json:"payload"`
}

func (m Message) UID() string {
	return m.Name()
}

func (m Message) Name() string {
	return m.NameField
}

func (m Message) Title() string {
	return m.TitleField
}

func (m Message) HasTitle() bool {
	return m.TitleField != ""
}

func (m Message) Summary() string {
	return m.SummaryField
}

func (m Message) HasSummary() bool {
	return m.SummaryField != ""
}

func (m Message) ContentType() string {
	return m.ContentTypeField
}

func (m Message) Payload() asyncapi.Schema {
	return m.PayloadField
}

type Schemas map[string]*Schema

func (s Schemas) ToInterface(dst map[string]asyncapi.Schema) map[string]asyncapi.Schema {
	if len(dst) > 0 {
		return dst
	}

	if dst == nil {
		dst = make(map[string]asyncapi.Schema)
	}

	for k, v := range s {
		dst[k] = v
	}

	return dst
}

type FalsifiableSchema struct {
	val interface{}
}

// NewFalsifiableSchema creates a new FalsifiableSchema.
func NewFalsifiableSchema(val interface{}) *FalsifiableSchema {
	if val == nil {
		return nil
	}
	return &FalsifiableSchema{val: val}
}

func (f FalsifiableSchema) IsFalse() bool {
	_, ok := f.val.(bool)
	return ok
}

func (f FalsifiableSchema) IsSchema() bool {
	_, ok := f.val.(*Schema)
	return ok
}

func (f FalsifiableSchema) Schema() asyncapi.Schema {
	if f.IsSchema() {
		return f.val.(*Schema)
	}

	return nil
}

type Schema struct {
	Extendable
	AdditionalItemsField      interface{}       `mapstructure:"additionalItems" json:"additionalItems,omitempty"`
	AdditionalPropertiesField interface{}       `mapstructure:"additionalProperties" json:"additionalProperties,omitempty"` // Schema || false
	AllOfField                []asyncapi.Schema `mapstructure:"allOf" json:"allOf,omitempty"`
	AnyOfField                []asyncapi.Schema `mapstructure:"anyOf" json:"anyOf,omitempty"`
	ConstField                interface{}       `mapstructure:"const" json:"const,omitempty"`
	ContainsField             *Schema           `mapstructure:"contains" json:"contains,omitempty"`
	ContentEncodingField      string            `mapstructure:"contentEncoding" json:"contentEncoding,omitempty"`
	ContentMediaTypeField     string            `mapstructure:"contentMediaType" json:"contentMediaType,omitempty"`
	DefaultField              interface{}       `mapstructure:"default" json:"default,omitempty"`
	DefinitionsField          Schemas           `mapstructure:"definitions" json:"definitions,omitempty"`
	DependenciesField         Schemas           `mapstructure:"dependencies" json:"dependencies,omitempty"`
	DeprecatedField           bool              `mapstructure:"deprecated" json:"deprecated,omitempty"`
	DescriptionField          string            `mapstructure:"description" json:"description,omitempty"`
	DiscriminatorField        string            `mapstructure:"discriminator" json:"discriminator,omitempty"`
	ElseField                 *Schema           `mapstructure:"else" json:"else,omitempty"`
	EnumField                 []interface{}     `mapstructure:"enum" json:"enum,omitempty"`
	ExamplesField             []interface{}     `mapstructure:"examples" json:"examples,omitempty"`
	ExclusiveMaximumField     *float64          `mapstructure:"exclusiveMaximum" json:"exclusiveMaximum,omitempty"`
	ExclusiveMinimumField     *float64          `mapstructure:"exclusiveMinimum" json:"exclusiveMinimum,omitempty"`
	FormatField               string            `mapstructure:"format" json:"format,omitempty"`
	IDField                   string            `mapstructure:"$id" json:"$id,omitempty"`
	IfField                   *Schema           `mapstructure:"if" json:"if,omitempty"`
	ItemsField                []asyncapi.Schema `mapstructure:"items" json:"items,omitempty"`
	MaximumField              *float64          `mapstructure:"maximum" json:"maximum,omitempty"`
	MaxItemsField             *float64          `mapstructure:"maxItems" json:"maxItems,omitempty"`
	MaxLengthField            *float64          `mapstructure:"maxLength" json:"maxLength,omitempty"`
	MaxPropertiesField        *float64          `mapstructure:"maxProperties" json:"maxProperties,omitempty"`
	MinimumField              *float64          `mapstructure:"minimum" json:"minimum,omitempty"`
	MinItemsField             *float64          `mapstructure:"minItems" json:"minItems,omitempty"`
	MinLengthField            *float64          `mapstructure:"minLength" json:"minLength,omitempty"`
	MinPropertiesField        *float64          `mapstructure:"minProperties" json:"minProperties,omitempty"`
	MultipleOfField           *float64          `mapstructure:"multipleOf" json:"multipleOf,omitempty"`
	NotField                  *Schema           `mapstructure:"not" json:"not,omitempty"`
	OneOfField                []asyncapi.Schema `mapstructure:"oneOf" json:"oneOf,omitempty"`
	PatternField              string            `mapstructure:"pattern" json:"pattern,omitempty"`
	PatternPropertiesField    Schemas           `mapstructure:"patternProperties" json:"patternProperties,omitempty"`
	PropertiesField           Schemas           `mapstructure:"properties" json:"properties,omitempty"`
	PropertyNamesField        *Schema           `mapstructure:"propertyNames" json:"propertyNames,omitempty"`
	ReadOnlyField             bool              `mapstructure:"readOnly" json:"readOnly,omitempty"`
	RequiredField             []string          `mapstructure:"required" json:"required,omitempty"`
	ThenField                 *Schema           `mapstructure:"then" json:"then,omitempty"`
	TitleField                string            `mapstructure:"title" json:"title,omitempty"`
	TypeField                 interface{}       `mapstructure:"type" json:"type,omitempty"` // string | []string
	UniqueItemsField          bool              `mapstructure:"uniqueItems" json:"uniqueItems,omitempty"`
	WriteOnlyField            bool              `mapstructure:"writeOnly" json:"writeOnly,omitempty"`

	// cached converted map[string]asyncapi.Schema from map[string]*Schema
	propertiesFieldMap        map[string]asyncapi.Schema
	patternPropertiesFieldMap map[string]asyncapi.Schema
	definitionsFieldMap       map[string]asyncapi.Schema
	dependenciesFieldMap      map[string]asyncapi.Schema
}

func (s *Schema) AdditionalItems() asyncapi.FalsifiableSchema {
	return NewFalsifiableSchema(s.AdditionalItemsField)
}

func (s *Schema) AdditionalProperties() asyncapi.FalsifiableSchema {
	return NewFalsifiableSchema(s.AdditionalPropertiesField)
}

func (s *Schema) AllOf() []asyncapi.Schema {
	return s.AllOfField
}

func (s *Schema) AnyOf() []asyncapi.Schema {
	return s.AnyOfField
}

func (s *Schema) CircularProps() []string {
	if props, ok := s.Extension("x-parser-circular-props").([]string); ok {
		return props
	}

	return nil
}

func (s *Schema) Const() interface{} {
	return s.ConstField
}

func (s *Schema) Contains() asyncapi.Schema {
	return s.ContainsField
}

func (s *Schema) ContentEncoding() string {
	return s.ContentEncodingField
}

func (s *Schema) ContentMediaType() string {
	return s.ContentMediaTypeField
}

func (s *Schema) Default() interface{} {
	return s.DefaultField
}

func (s *Schema) Definitions() map[string]asyncapi.Schema {
	s.definitionsFieldMap = s.DefinitionsField.ToInterface(s.definitionsFieldMap)
	return s.definitionsFieldMap
}

func (s *Schema) Dependencies() map[string]asyncapi.Schema {
	s.dependenciesFieldMap = s.DependenciesField.ToInterface(s.dependenciesFieldMap)
	return s.dependenciesFieldMap
}

func (s *Schema) Deprecated() bool {
	return s.DeprecatedField
}

func (s *Schema) Description() string {
	return s.DescriptionField
}

func (s *Schema) Discriminator() string {
	return s.DiscriminatorField
}

func (s *Schema) Else() asyncapi.Schema {
	return s.ElseField
}

func (s *Schema) Enum() []interface{} {
	return s.EnumField
}

func (s *Schema) Examples() []interface{} {
	return s.ExamplesField
}

func (s *Schema) ExclusiveMaximum() *float64 {
	return s.ExclusiveMaximumField
}

func (s *Schema) ExclusiveMinimum() *float64 {
	return s.ExclusiveMinimumField
}

func (s *Schema) Format() string {
	return s.FormatField
}

func (s *Schema) HasCircularProps() bool {
	return len(s.CircularProps()) > 0
}

func (s *Schema) ID() string {
	return s.IDField
}

func (s *Schema) If() asyncapi.Schema {
	return s.IfField
}

func (s *Schema) IsCircular() bool {
	if isCircular, ok := s.Extension("x-parser-circular").(bool); ok {
		return isCircular
	}

	return false
}

func (s *Schema) Items() []asyncapi.Schema {
	return s.ItemsField
}

func (s *Schema) Maximum() *float64 {
	return s.MaximumField
}

func (s *Schema) MaxItems() *float64 {
	return s.MaxItemsField
}

func (s *Schema) MaxLength() *float64 {
	return s.MaxLengthField
}

func (s *Schema) MaxProperties() *float64 {
	return s.MaxPropertiesField
}

func (s *Schema) Minimum() *float64 {
	return s.MinimumField
}

func (s *Schema) MinItems() *float64 {
	return s.MinItemsField
}

func (s *Schema) MinLength() *float64 {
	return s.MinLengthField
}

func (s *Schema) MinProperties() *float64 {
	return s.MinPropertiesField
}

func (s *Schema) MultipleOf() *float64 {
	return s.MultipleOfField
}

func (s *Schema) Not() asyncapi.Schema {
	return s.NotField
}

func (s *Schema) OneOf() []asyncapi.Schema {
	return s.OneOfField
}

func (s *Schema) Pattern() string {
	return s.PatternField
}

func (s *Schema) PatternProperties() map[string]asyncapi.Schema {
	s.patternPropertiesFieldMap = s.PatternPropertiesField.ToInterface(s.patternPropertiesFieldMap)
	return s.patternPropertiesFieldMap
}

func (s *Schema) Properties() map[string]asyncapi.Schema {
	s.propertiesFieldMap = s.PropertiesField.ToInterface(s.propertiesFieldMap)
	return s.propertiesFieldMap
}

func (s *Schema) Property(name string) asyncapi.Schema {
	return s.PropertiesField[name]
}

func (s *Schema) PropertyNames() asyncapi.Schema {
	return s.PropertyNamesField
}

func (s *Schema) ReadOnly() bool {
	return s.ReadOnlyField
}

func (s *Schema) Required() []string {
	return s.RequiredField
}

func (s *Schema) Then() asyncapi.Schema {
	return s.ThenField
}

func (s *Schema) Title() string {
	return s.TitleField
}

func (s *Schema) Type() []string {
	if stringVal, isString := s.TypeField.(string); isString {
		return []string{stringVal}
	}

	if sliceVal, isSlice := s.TypeField.([]string); isSlice {
		return sliceVal
	}

	return nil
}

func (s *Schema) UID() string {
	if id := s.ID(); id != "" {
		return id
	}

	// This is not yet supported by parser-go
	parserGeneratedID, ok := s.Extension("x-parser-id").(string)
	if ok {
		return parserGeneratedID
	}

	return ""
}

func (s *Schema) UniqueItems() bool {
	return s.UniqueItemsField
}

func (s *Schema) WriteOnly() bool {
	return s.WriteOnlyField
}

type Server struct {
	Extendable
	Describable    `mapstructure:",squash"`
	NameField      string                    `mapstructure:"name" json:"name"`
	ProtocolField  string                    `mapstructure:"protocol" json:"protocol"`
	URLField       string                    `mapstructure:"url" json:"url"`
	VariablesField map[string]ServerVariable `mapstructure:"variables" json:"variables"`
}

func (s Server) Variables() []asyncapi.ServerVariable {
	var vars []asyncapi.ServerVariable
	for _, v := range s.VariablesField {
		vars = append(vars, v)
	}

	return vars
}

func (s Server) IDField() string {
	return "name"
}

func (s Server) ID() string {
	return s.NameField
}

func (s Server) Name() string {
	return s.NameField
}

func (s Server) HasName() bool {
	return s.NameField != ""
}

func (s Server) URL() string {
	return s.URLField
}

func (s Server) HasURL() bool {
	return s.URLField != ""
}

func (s Server) Protocol() string {
	return s.ProtocolField
}

func (s Server) HasProtocol() bool {
	return s.ProtocolField != ""
}

type ServerVariable struct {
	Extendable
	NameField string   `mapstructure:"name" json:"name"`
	Default   string   `mapstructure:"default" json:"default"`
	Enum      []string `mapstructure:"enum" json:"enum"`
}

func (s ServerVariable) IDField() string {
	return "name"
}

func (s ServerVariable) ID() string {
	return s.NameField
}

func (s ServerVariable) Name() string {
	return s.NameField
}

func (s ServerVariable) HasName() bool {
	return s.NameField != ""
}

func (s ServerVariable) DefaultValue() string {
	return s.Default
}

func (s ServerVariable) AllowedValues() []string {
	return s.Enum
}

type Describable struct {
	DescriptionField string `mapstructure:"description" json:"description"`
}

func (d Describable) Description() string {
	return d.DescriptionField
}

func (d Describable) HasDescription() bool {
	return d.DescriptionField != ""
}

type Extendable struct {
	Raw map[string]interface{} `mapstructure:",remain" json:"-"`
}

func (e Extendable) HasExtension(name string) bool {
	_, ok := e.Raw[name]
	return ok
}

func (e Extendable) Extension(name string) interface{} {
	return e.Raw[name]
}
