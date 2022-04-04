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

package log

import (
	"go.uber.org/zap"
)

var (
	// _defaultLogger global log instance, default to zap.log
	_defaultLogger Logger = &DefaultLogger{
		SugaredLogger: zap.SugaredLogger{},
	}
)

// SetLogger set the log
func SetLogger(l Logger) {
	_defaultLogger = l
}

func Debugf(template string, args ...interface{}) {
	_defaultLogger.Debugf(template, args, nil)
}

func Infof(template string, args ...interface{}) {
	_defaultLogger.Infof(template, args, nil)
}

// Warnf uses fmt.Sprintf to log a templated message.
func Warnf(template string, args ...interface{}) {
	_defaultLogger.Warnf(template, args, nil)
}

// Errorf uses fmt.Sprintf to log a templated message.
func Errorf(template string, args ...interface{}) {
	_defaultLogger.Errorf(template, args, nil)
}

// DPanicf uses fmt.Sprintf to log a templated message. In development, the
// log then panics. (See DPanicLevel for details.)
func DPanicf(template string, args ...interface{}) {
	_defaultLogger.DPanicf(template, args, nil)
}

// Panicf uses fmt.Sprintf to log a templated message, then panics.
func Panicf(template string, args ...interface{}) {
	_defaultLogger.Panicf(template, args, nil)
}

// Fatalf uses fmt.Sprintf to log a templated message, then calls os.Exit.
func Fatalf(template string, args ...interface{}) {
	_defaultLogger.Fatalf(template, args, nil)
}

// Logger define the log api for eventmesh
type Logger interface {
	// Debugf uses fmt.Sprintf to log a templated message.
	// DebugLevel logs are typically voluminous, and are usually disabled in
	// production.
	Debugf(template string, args ...interface{})

	// Infof uses fmt.Sprintf to log a templated message.
	// InfoLevel is the default logging priority.
	Infof(template string, args ...interface{})

	// Warnf uses fmt.Sprintf to log a templated message.
	// WarnLevel logs are more important than Info, but don't need individual
	// human review.
	Warnf(template string, args ...interface{})

	// Errorf uses fmt.Sprintf to log a templated message.
	// ErrorLevel logs are high-priority. If an application is running smoothly,
	// it shouldn't generate any error-level logs.
	Errorf(template string, args ...interface{})

	// DPanicf uses fmt.Sprintf to log a templated message. In development, the
	// DPanicLevel logs are particularly important errors. In development the
	// log panics after writing the message.
	DPanicf(template string, args ...interface{})

	// Panicf uses fmt.Sprintf to log a templated message, then panics.
	Panicf(template string, args ...interface{})

	// Fatalf uses fmt.Sprintf to log a templated message, then calls os.Exit.
	Fatalf(template string, args ...interface{})
}

// DefaultLogger write log by zap log
type DefaultLogger struct {
	zap.SugaredLogger
}

func (s *DefaultLogger) Debugf(template string, args ...interface{}) {
	s.SugaredLogger.Debugf(template, args, nil)
}

func (s *DefaultLogger) Infof(template string, args ...interface{}) {
	s.SugaredLogger.Infof(template, args, nil)
}

func (s *DefaultLogger) Warnf(template string, args ...interface{}) {
	s.SugaredLogger.Warnf(template, args, nil)
}

func (s *DefaultLogger) Errorf(template string, args ...interface{}) {
	s.SugaredLogger.Errorf(template, args, nil)
}

func (s *DefaultLogger) DPanicf(template string, args ...interface{}) {
	s.SugaredLogger.DPanicf(template, args, nil)
}

func (s *DefaultLogger) Panicf(template string, args ...interface{}) {
	s.SugaredLogger.Panicf(template, args, nil)
}

func (s *DefaultLogger) Fatalf(template string, args ...interface{}) {
	s.SugaredLogger.Fatalf(template, args, nil)
}
