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

// Level is a logging priority, Higher levels are more important.
type Level int8

const (
	// DebugLevel logs are typically voluminous, and are usually disabled in
	// production.
	DebugLevel Level = iota - 1
	// InfoLevel is the default logging priority.
	InfoLevel
	// WarnLevel logs are more important than Info, but don't need individual
	// human review.
	WarnLevel
	// ErrorLevel logs are high-priority. If an application is running smoothly,
	// it shouldn't generate any error-level logs.
	ErrorLevel
	// DPanicLevel logs are particularly important errors. In development the
	// logger panics after writing the message.
	DPanicLevel
	// PanicLevel logs a message, then panics.
	PanicLevel
	// FatalLevel logs a message, then calls os.Exit(1).
	FatalLevel

	_minLevel = DebugLevel
	_maxLevel = FatalLevel
)

// Encoder log format encoder
type Encoder string

const (
	// JSON write log in json format
	JSON Encoder = "json"
	// Console write log in single line format
	Console Encoder = "console"
)

type LogConfig struct {
	// Filename indicates where the log writted
	// if the name is stdout and stderr, it will interpreted as os.Stdout and os.Stderr
	// default to stdout
	Filename string
	// Encoder indicates the format about log, support json and console
	// for json, log will write in json format with timestamp and level information and so on
	// for console, log will write in single line
	// only worked on log type is zap or logrus
	// default to Console
	Encoder Encoder
	// Level the log level, higer levels are more important, refers to Level for details
	// default to info
	Level Level
	// TimeEncoder the time format
	// default to 2026-01-02 15:04:05.000
	TimeEncoder string
	// Rotation rotation configuration
	// currentlly works on zap log
	*Rotation
}

// Rotation configuration for log rotation
// only works on write log to file
type Rotation struct {
	// MaxSize the maximum size in megabytes of the log file on it gets rotated
	// default to 100M
	MaxSize int
	// MaxAge the maximum number of days to retain the old log files based on
	// the log file create timestamp
	MaxAge int
	// MaxBackups the maximum number of old log files to retain, the oldest log
	// file will deleted if reached
	MaxBackups int
	// Compress weather to conpmress the file if the log file is rotated
	Compress bool
}

// Logger log api with multiple level
type Logger interface {
	// Debugf logs a message at DebugLevel. The message includes any fields passed
	// at the log site, as well as any fields accumulated on the logger.
	Debugf(template string, fields ...interface{})
	// Infof logs a message at InfoLevel. The message includes any fields passed
	// at the log site, as well as any fields accumulated on the logger.
	Infof(template string, fields ...interface{})
	// Warnf logs a message at WarnLevel. The message includes any fields passed
	// at the log site, as well as any fields accumulated on the logger.
	Warnf(template string, fields ...interface{})
	// Errorf logs a message at ErrorLevel. The message includes any fields passed
	// at the log site, as well as any fields accumulated on the logger.
	Errorf(template string, fields ...interface{})
	// DPanicf logs a message at DPanicLevel. The message includes any fields
	// passed at the log site, as well as any fields accumulated on the logger.
	//
	// If the logger is in development mode, it then panics (DPanic means
	// "development panic"). This is useful for catching errors that are
	// recoverable, but shouldn't ever happen.
	DPanicf(template string, fields ...interface{})
	// Panicf logs a message at PanicLevel. The message includes any fields passed
	// at the log site, as well as any fields accumulated on the logger.
	//
	// The logger then panics, even if logging at PanicLevel is disabled.
	Panicf(template string, fields ...interface{})
	// Fatalf logs a message at FatalLevel. The message includes any fields passed
	// at the log site, as well as any fields accumulated on the logger.
	//
	// The logger then calls os.Exit(1), even if logging at FatalLevel is
	// disabled.
	Fatalf(template string, fields ...interface{})
}
