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

package rollwriter

// Options is the RollWriter call options.
type Options struct {
	// MaxSize is max size by byte of the log file.
	MaxSize int64

	// MaxBackups is the max number of log files.
	MaxBackups int

	// MaxAge is the max expire time by day of log files.
	MaxAge int

	// whether the log file should be compressed.
	Compress bool

	// TimeFormat is the time format to split log file by time.
	TimeFormat string
}

// Option modifies the Options.
type Option func(*Options)

// WithMaxSize returns an Option which sets the max size(MB) of log files.
func WithMaxSize(n int) Option {
	return func(o *Options) {
		o.MaxSize = int64(n) * 1024 * 1024
	}
}

// WithMaxAge returns an Option which sets the max expire time(Day) of log files.
func WithMaxAge(n int) Option {
	return func(o *Options) {
		o.MaxAge = n
	}
}

// WithMaxBackups returns an Option which sets the max number of backup log files.
func WithMaxBackups(n int) Option {
	return func(o *Options) {
		o.MaxBackups = n
	}
}

// WithCompress returns an Option which sets whether log files should be compressed.
func WithCompress(b bool) Option {
	return func(o *Options) {
		o.Compress = b
	}
}

// WithRotationTime returns an Option which sets the time format(%Y%m%d) to roll logs.
func WithRotationTime(s string) Option {
	return func(o *Options) {
		o.TimeFormat = s
	}
}
