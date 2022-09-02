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
	"bytes"
	"encoding/json"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/pkg/asyncapi"
	"reflect"

	"github.com/asyncapi/parser-go/pkg/parser"
	"github.com/mitchellh/mapstructure"
	"github.com/pkg/errors"
)

// Decode implements the Decoder interface. Decodes AsyncAPI V2.x.x documents.
func Decode(b []byte, dst interface{}) error {
	r, err := parser.NewReader(string(b)) // parser should provide another method for parsing []byte
	if err != nil {
		return errors.Wrap(err, "error reading AsyncAPI doc")
	}

	p, err := parser.New()
	if err != nil {
		return err
	}

	w := bytes.NewBuffer(nil)
	if err := p(r, w); err != nil {
		return errors.Wrap(err, "error parsing AsyncAPI doc")
	}

	raw := make(map[string]interface{})
	if err := json.Unmarshal(w.Bytes(), &raw); err != nil {
		return err
	}

	dec, err := mapstructure.NewDecoder(&mapstructure.DecoderConfig{
		DecodeHook: mapstructure.ComposeDecodeHookFunc(setModelIdentifierHook, setDefaultsHook),
		Squash:     true,
		Result:     dst,
	})
	if err != nil {
		return err
	}

	return dec.Decode(raw)
}

// setModelIdentifierHook is a hook for the mapstructure decoder.
// It checks if the destination type is a map of Identifiable elements and sets the proper identifier (name, id, etc) to it.
// Example: Useful for storing the name of the server in the Server struct (AsyncAPI doc does not have such field because it assumes the name is the key of the map).
func setModelIdentifierHook(from reflect.Type, to reflect.Type, data interface{}) (interface{}, error) {
	if from.Kind() != reflect.Map || to.Kind() != reflect.Map {
		return data, nil
	}

	identifiableInterface := reflect.TypeOf((*asyncapi.Identifiable)(nil)).Elem()
	if to.Key() != reflect.TypeOf("string") || !to.Elem().Implements(identifiableInterface) {
		return data, nil
	}

	fieldName := reflect.New(to.Elem()).Interface().(asyncapi.Identifiable).IDField()
	for k, v := range data.(map[string]interface{}) {
		// setting the value directly in the raw map. The struct needs to keep the mapstructure field tag so it unmarshals the field.
		v.(map[string]interface{})[fieldName] = k
	}

	return data, nil
}

// MapStructureDefaultsProvider tells to mapstructure setDefaultsHook the defaults value for that type.
type MapStructureDefaultsProvider interface {
	MapStructureDefaults() map[string]interface{}
}

// setDefaultsHook is a hook for the mapstructure decoder.
// It checks if the destination type implements MapStructureDefaultsProvider.
// If so, it gets the defaults values from it and sets them if empty.
func setDefaultsHook(_ reflect.Type, to reflect.Type, data interface{}) (interface{}, error) {
	if !to.Implements(reflect.TypeOf((*MapStructureDefaultsProvider)(nil)).Elem()) {
		return data, nil
	}

	var toType reflect.Type
	switch to.Kind() { //nolint:exhaustive
	case reflect.Array, reflect.Chan, reflect.Map, reflect.Ptr, reflect.Slice:
		toType = to.Elem()
	default:
		toType = to
	}

	defaults := reflect.New(toType).Interface().(MapStructureDefaultsProvider).MapStructureDefaults()
	for k, v := range defaults {
		if _, ok := data.(map[string]interface{})[k]; !ok {
			data.(map[string]interface{})[k] = v
		}
	}

	return data, nil
}
