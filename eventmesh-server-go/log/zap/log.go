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

package zap

import (
	"fmt"
	"time"

	"github.com/apache/eventmesh-go/log"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"gopkg.in/natefinch/lumberjack.v2"
)

// New create log with zap according to the configuration
func New(cf *log.LogConfig) (log.Logger, error) {
	var (
		encoderCfg zapcore.EncoderConfig
		encoder    zapcore.Encoder
		level      zapcore.Level
		syncer     zapcore.WriteSyncer
		options    []zap.Option
		core       zapcore.Core
	)

	encoderCfg = zap.NewProductionEncoderConfig()
	encoderCfg.EncodeLevel = zapcore.CapitalLevelEncoder
	encoderCfg.EncodeTime = func(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
		enc.AppendString(t.Format(cf.TimeEncoder))
	}

	if cf.Encoder == log.Console {
		encoder = zapcore.NewConsoleEncoder(encoderCfg)
	} else {
		encoder = zapcore.NewJSONEncoder(encoderCfg)
	}

	level = toZapLevel(cf.Level)

	if cf.Rotation != nil {
		syncer = zapcore.AddSync(&lumberjack.Logger{
			Filename:   cf.Filename,
			MaxSize:    cf.MaxSize,
			MaxAge:     cf.MaxAge,
			MaxBackups: cf.MaxBackups,
			LocalTime:  true,
			Compress:   cf.Compress,
		})
	} else {
		sink, _, err := zap.Open([]string{cf.Filename}...)
		if err != nil {
			return nil, err
		}
		syncer = sink
	}
	core = zapcore.NewCore(encoder, syncer, level)
	options = append(options, zap.AddCaller(), zap.AddCallerSkip(1), zap.AddStacktrace(zap.ErrorLevel))
	return &zapLogger{
		Logger:      zap.New(core, options...),
		atomicLevel: zap.NewAtomicLevelAt(level),
	}, nil
}

// toZapLevel transfer the log.Level to zapcore.Level
func toZapLevel(l log.Level) zapcore.Level {
	switch l {
	case log.DebugLevel:
		return zapcore.DebugLevel
	case log.InfoLevel:
		return zapcore.InfoLevel
	case log.WarnLevel:
		return zapcore.WarnLevel
	case log.ErrorLevel:
		return zapcore.ErrorLevel
	case log.DPanicLevel:
		return zapcore.DPanicLevel
	case log.PanicLevel:
		return zapcore.PanicLevel
	case log.FatalLevel:
		return zapcore.FatalLevel
	}
	return zapcore.DebugLevel
}

type zapLogger struct {
	*zap.Logger
	atomicLevel zap.AtomicLevel
}

// Debugf logs a message at DebugLevel. The message includes any fields passed
// at the log site, as well as any fields accumulated on the logger.
func (z *zapLogger) Debugf(template string, fields ...interface{}) {
	if !z.atomicLevel.Level().Enabled(zapcore.DebugLevel) {
		return
	}
	msg := fmt.Sprintf(template, fields...)
	if ce := z.Check(zapcore.DebugLevel, msg); ce != nil {
		ce.Write()
	}
}

// Infof logs a message at InfoLevel. The message includes any fields passed
// at the log site, as well as any fields accumulated on the logger.
func (z *zapLogger) Infof(template string, fields ...interface{}) {
	if !z.atomicLevel.Level().Enabled(zapcore.InfoLevel) {
		return
	}
	msg := fmt.Sprintf(template, fields...)
	if ce := z.Check(zapcore.InfoLevel, msg); ce != nil {
		ce.Write()
	}
}

// Warnf logs a message at WarnLevel. The message includes any fields passed
// at the log site, as well as any fields accumulated on the logger.
func (z *zapLogger) Warnf(template string, fields ...interface{}) {
	if !z.atomicLevel.Level().Enabled(zapcore.WarnLevel) {
		return
	}
	msg := fmt.Sprintf(template, fields...)
	if ce := z.Check(zapcore.WarnLevel, msg); ce != nil {
		ce.Write()
	}
}

// Errorf logs a message at ErrorLevel. The message includes any fields passed
// at the log site, as well as any fields accumulated on the logger.
func (z *zapLogger) Errorf(template string, fields ...interface{}) {
	if !z.atomicLevel.Level().Enabled(zapcore.ErrorLevel) {
		return
	}
	msg := fmt.Sprintf(template, fields...)
	if ce := z.Check(zapcore.ErrorLevel, msg); ce != nil {
		ce.Write()
	}
}

// DPanicf logs a message at DPanicLevel. The message includes any fields
// passed at the log site, as well as any fields accumulated on the logger.
//
// If the logger is in development mode, it then panics (DPanic means
// "development panic"). This is useful for catching errors that are
// recoverable, but shouldn't ever happen.
func (z *zapLogger) DPanicf(template string, fields ...interface{}) {
	if !z.atomicLevel.Level().Enabled(zapcore.PanicLevel) {
		return
	}
	msg := fmt.Sprintf(template, fields...)
	if ce := z.Check(zapcore.PanicLevel, msg); ce != nil {
		ce.Write()
	}
}

// Panicf logs a message at PanicLevel. The message includes any fields passed
// at the log site, as well as any fields accumulated on the logger.
//
// The logger then panics, even if logging at PanicLevel is disabled.
func (z *zapLogger) Panicf(template string, fields ...interface{}) {
	if !z.atomicLevel.Level().Enabled(zapcore.PanicLevel) {
		return
	}
	msg := fmt.Sprintf(template, fields...)
	if ce := z.Check(zapcore.PanicLevel, msg); ce != nil {
		ce.Write()
	}
}

// Fatalf logs a message at FatalLevel. The message includes any fields passed
// at the log site, as well as any fields accumulated on the logger.
//
// The logger then calls os.Exit(1), even if logging at FatalLevel is
// disabled.
func (z *zapLogger) Fatalf(template string, fields ...interface{}) {
	if !z.atomicLevel.Level().Enabled(zapcore.FatalLevel) {
		return
	}
	msg := fmt.Sprintf(template, fields...)
	if ce := z.Check(zapcore.FatalLevel, msg); ce != nil {
		ce.Write()
	}
}
