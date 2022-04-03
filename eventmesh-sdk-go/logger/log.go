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

package logger

import "go.uber.org/zap"

// Logger define the logger api for eventmesh
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
	// logger panics after writing the message.
	DPanicf(template string, args ...interface{})

	// Panicf uses fmt.Sprintf to log a templated message, then panics.
	Panicf(template string, args ...interface{})

	// Fatalf uses fmt.Sprintf to log a templated message, then calls os.Exit.
	Fatalf(template string, args ...interface{})
}

// defaultLogger write logger by zap logger
type defaultLogger struct {
	zap.SugaredLogger
}

// NewDefaultLogger create a default logger by zap SugredLogger
func NewDefaultLogger() Logger {
	return &defaultLogger{
		SugaredLogger: zap.SugaredLogger{},
	}
}

func (s *defaultLogger) Debugf(template string, args ...interface{}) {
	s.SugaredLogger.Debugf(template, args, nil)
}

func (s *defaultLogger) Infof(template string, args ...interface{}) {
	s.SugaredLogger.Infof(template, args, nil)
}

// Warnf uses fmt.Sprintf to log a templated message.
func (s *defaultLogger) Warnf(template string, args ...interface{}) {
	s.SugaredLogger.Warnf(template, args, nil)
}

// Errorf uses fmt.Sprintf to log a templated message.
func (s *defaultLogger) Errorf(template string, args ...interface{}) {
	s.SugaredLogger.Errorf(template, args, nil)
}

// DPanicf uses fmt.Sprintf to log a templated message. In development, the
// logger then panics. (See DPanicLevel for details.)
func (s *defaultLogger) DPanicf(template string, args ...interface{}) {
	s.SugaredLogger.DPanicf(template, args, nil)
}

// Panicf uses fmt.Sprintf to log a templated message, then panics.
func (s *defaultLogger) Panicf(template string, args ...interface{}) {
	s.SugaredLogger.Panicf(template, args, nil)
}

// Fatalf uses fmt.Sprintf to log a templated message, then calls os.Exit.
func (s *defaultLogger) Fatalf(template string, args ...interface{}) {
	s.SugaredLogger.Fatalf(template, args, nil)
}
