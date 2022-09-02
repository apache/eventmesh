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

// Options config option
type Options struct {
	WrapBegin          string
	WrapLeftSeparator  string
	WrapRightSeparator string
}

// Option represents the optional function.
type Option func(opts *Options)

// WithWrapBegin config wrap begin option
func WithWrapBegin(wrapBegin string) Option {
	return func(opts *Options) {
		opts.WrapBegin = wrapBegin
	}
}

// WithWrapLeftSeparator config wrap left separator option
func WithWrapLeftSeparator(wrapBegin string) Option {
	return func(opts *Options) {
		opts.WrapBegin = wrapBegin
	}
}

// WithWrapRightSeparator config wrap right separator option
func WithWrapRightSeparator(wrapBegin string) Option {
	return func(opts *Options) {
		opts.WrapBegin = wrapBegin
	}
}

func loadOptions(options ...Option) *Options {
	var opts = new(Options)
	for _, option := range options {
		option(opts)
	}
	return opts
}
