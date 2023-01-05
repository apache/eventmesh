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

package jqer

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/third_party/lexer"
	"reflect"
	"sort"
	"strings"
	"time"

	"github.com/itchyny/gojq"
)

const (
	defaultWrapBeginCharacter                 = "$"
	defaultWrapLeftCharacter                  = "{"
	defaultWrapRightCharacter                 = "}"
	jqStartToken              lexer.TokenType = iota
	stringToken
	errorToken
	noToken
)

var (
	ErrCodeJQBadQuery  = "eventmesh.workflow.jq.badCommand"
	ErrCodeJQNotObject = "eventmesh.workflow.jq.notObject"
)

// JQ json query encapsulation
type JQ interface {
	Object(input interface{}, command interface{}) (map[string]interface{}, error)
	One(input interface{}, command interface{}) (interface{}, error)
}

type jqError struct {
	Code    string `json:"code"`
	Message string `json:"msg"`
}

// Error error method implementation
func (e *jqError) Error() string {
	return fmt.Sprintf("code: %s, message: %s", e.Code, e.Message)
}

// NewError error constructor
func NewError(code string, msg string, a ...interface{}) error {
	return &jqError{
		Code:    code,
		Message: fmt.Sprintf(msg, a...),
	}
}

type jqer struct {
	options *Options
}

// NewJQ constructor jq instance
func NewJQ(options ...Option) JQ {
	jq := &jqer{options: loadOptions(options...)}
	if len(jq.options.WrapLeftSeparator) == 0 {
		jq.options.WrapBegin = defaultWrapBeginCharacter
	}
	if len(jq.options.WrapLeftSeparator) == 0 {
		jq.options.WrapLeftSeparator = defaultWrapLeftCharacter
	}
	if len(jq.options.WrapRightSeparator) == 0 {
		jq.options.WrapRightSeparator = defaultWrapRightCharacter
	}
	return jq
}

// One single jq
func (j *jqer) One(input interface{}, command interface{}) (interface{}, error) {
	output, err := j.jq(input, command)
	if err != nil {
		return nil, err
	}

	if len(output) != 1 {
		return nil, NewError(ErrCodeJQNotObject, "command produced multiple outputs")
	}

	return output[0], nil
}

// Object object jq
func (j *jqer) Object(input interface{}, command interface{}) (map[string]interface{}, error) {
	x, err := j.One(input, command)
	if err != nil {
		return nil, err
	}

	m, ok := x.(map[string]interface{})
	if !ok {
		return nil, NewError(ErrCodeJQNotObject, "the `jq` or `js` command produced a non-object output")
	}

	return m, nil
}

func (j *jqer) jq(input interface{}, command interface{}) ([]interface{}, error) {
	out, err := j.evaluate(input, command)
	if err != nil {
		return nil, NewError(ErrCodeJQBadQuery, "failed to evaluate jq: %v", err)
	}
	return out, nil
}

// Evaluate evaluates the data against the query provided and returns the result
func (j *jqer) evaluate(data, query interface{}) ([]interface{}, error) {
	if query == nil {
		var out []interface{}
		out = append(out, data)
		return out, nil
	}

	return j.recursiveEvaluate(data, query)
}

func (j *jqer) recursiveEvaluate(data, query interface{}) ([]interface{}, error) {

	var out []interface{}

	if query == nil {
		out = append(out, nil)
		return out, nil
	}

	switch query.(type) {
	case bool:
	case int:
	case float64:
	case string:
		return j.recurseIntoString(data, query.(string))
	case map[string]interface{}:
		return j.recurseIntoMap(data, query.(map[string]interface{}))
	case []interface{}:
		return j.recurseIntoArray(data, query.([]interface{}))
	default:
		return nil, fmt.Errorf("unexpected type: %s", reflect.TypeOf(query).String())
	}

	out = append(out, query)

	return out, nil

}

func (j *jqer) jqState(l *lexer.L) lexer.StateFunc {
	var jqStartSeparatorLen = len(j.options.WrapBegin) + len(j.options.WrapLeftSeparator)
	src := make([]string, jqStartSeparatorLen)
	var jdxJ int

	mover := func(rewind int, forward bool) {
		for a := 0; a < rewind; a++ {
			if forward {
				l.Next()
			} else {
				l.Rewind()
			}
		}
	}

	for i := 0; i < jqStartSeparatorLen; i++ {
		r := l.Next()
		if r == lexer.EOFRune {
			if len(l.Current()) > 0 {
				l.Emit(stringToken)
			}
			return nil
		}
		src[i] = string(r)

		if src[i] == "$" && i > 0 {
			jdxJ = i
		}
	}

	isJX := strings.Join(src, "")

	token := noToken
	if isJX == j.options.WrapBegin+j.options.WrapLeftSeparator {
		token = jqStartToken
	}
	if token != noToken {
		// this cuts out the '${{' bit
		mover(jqStartSeparatorLen, false)

		// emit string token if there is content in it
		if len(l.Current()) > 0 {
			l.Emit(stringToken)
		}
		mover(jqStartSeparatorLen, true)

		// counting the '{}'
		var open int
		l.Ignore()
		for {
			n := l.Next()
			if n == lexer.EOFRune {
				l.Emit(errorToken)
				return nil
			}
			switch n {
			case rune(j.options.WrapLeftSeparator[0]):
				open++
			case rune(j.options.WrapRightSeparator[0]):
				open--
			}
			if open < 0 {
				l.Rewind()
				break
			}
		}
		l.Emit(token)

		// remove closing '}'
		mover(len(j.options.WrapRightSeparator), true)
		l.Ignore()

		return j.jqState
	}

	if jdxJ > 0 {
		mover(jqStartSeparatorLen-jdxJ, false)
	}

	return j.jqState
}

func (j *jqer) recurseIntoString(data interface{}, s string) ([]interface{}, error) {
	out := make([]interface{}, 0)
	s = strings.TrimSpace(s)
	l := lexer.New(s, j.jqState)
	l.Start()

	for {
		tok, done := l.NextToken()
		if done {
			break
		}

		switch tok.Type {
		case errorToken:
			return nil, fmt.Errorf("jq script missing bracket")
		case jqStartToken:
			x, err := j.doJq(data, tok.Value)
			if err != nil {
				return nil, fmt.Errorf("error executing jq query %s: %v", tok.Value, err)
			}

			if len(x) == 0 || len(x) > 0 && x[0] == nil {
				return nil, fmt.Errorf("error in jq query %s: no results", tok.Value)
			}

			if len(x) == 1 {
				out = append(out, x[0])
			} else {
				return nil, fmt.Errorf("jq query produced multiple outputs")
			}
		default:
			out = append(out, tok.Value)
		}
	}

	if len(out) == 1 {
		return out, nil
	}

	x := make([]string, len(out))
	for i := range out {
		part := out[i]
		if _, ok := part.(string); ok {
			x = append(x, fmt.Sprintf("%v", part))
		} else {
			data, err := json.Marshal(part)
			if err != nil {
				return nil, err
			}
			x = append(x, string(data))
		}
	}

	s = strings.Join(x, "")
	out = make([]interface{}, 1)
	out[0] = s
	return out, nil

}

func (j *jqer) recurseIntoMap(data interface{}, m map[string]interface{}) ([]interface{}, error) {
	var out []interface{}
	var results = make(map[string]interface{})
	var keys []string
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for i := range keys {
		k := keys[i]
		x, err := j.recursiveEvaluate(data, m[k])
		if err != nil {
			return nil, fmt.Errorf("error in '%s': %v", k, err)
		}
		if len(x) == 0 {
			return nil, fmt.Errorf("error in element '%s': no results", k)
		}
		if len(x) > 1 {
			return nil, fmt.Errorf("error in element '%s': more than one result", k)
		}
		results[k] = x[0]
	}
	out = append(out, results)
	return out, nil
}

func (j *jqer) recurseIntoArray(data interface{}, q []interface{}) ([]interface{}, error) {
	var out []interface{}
	var array = make([]interface{}, 0)
	for i := range q {
		x, err := j.recursiveEvaluate(data, q[i])
		if err != nil {
			return nil, fmt.Errorf("error in element %d: %v", i, err)
		}
		if len(x) == 0 {
			return nil, fmt.Errorf("error in element %d: no results", i)
		}
		if len(x) > 1 {
			return nil, fmt.Errorf("error in element %d: more than one result", i)
		}
		array = append(array, x[0])
	}
	out = append(out, array)
	return out, nil
}

func (j *jqer) doJq(input interface{}, command string) ([]interface{}, error) {
	data, err := json.Marshal(input)
	if err != nil {
		return nil, err
	}

	var x interface{}

	err = json.Unmarshal(data, &x)
	if err != nil {
		return nil, err
	}

	query, err := gojq.Parse(command)
	if err != nil {
		return nil, err
	}

	var output []interface{}

	ctx, cancel := context.WithTimeout(context.Background(), time.Second*10)
	defer cancel()
	iter := query.RunWithContext(ctx, x)

	for i := 0; ; i++ {
		v, ok := iter.Next()
		if !ok {
			break
		}
		if err, ok := v.(error); ok {
			return nil, err
		}
		output = append(output, v)
	}

	return output, nil
}
