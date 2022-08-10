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
	"fmt"
	"os"
	"strconv"
	"time"

	"github.com/apache/incubator-eventmesh/eventmesh-go/log/rollwriter"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

var defaultConfig = []OutputConfig{
	{
		Writer:    "console",
		Level:     "debug",
		Formatter: "console",
	},
}

// Some ZapCore constants.
const (
	ConsoleZapCore = "console"
	FileZapCore    = "file"
)

// Levels is the map from string to zapcore.Level.
var Levels = map[string]zapcore.Level{
	"":      zapcore.DebugLevel,
	"debug": zapcore.DebugLevel,
	"info":  zapcore.InfoLevel,
	"warn":  zapcore.WarnLevel,
	"error": zapcore.ErrorLevel,
	"fatal": zapcore.FatalLevel,
}

var levelToZapLevel = map[Level]zapcore.Level{
	LevelTrace: zapcore.DebugLevel,
	LevelDebug: zapcore.DebugLevel,
	LevelInfo:  zapcore.InfoLevel,
	LevelWarn:  zapcore.WarnLevel,
	LevelError: zapcore.ErrorLevel,
	LevelFatal: zapcore.FatalLevel,
}

var zapLevelToLevel = map[zapcore.Level]Level{
	zapcore.DebugLevel: LevelDebug,
	zapcore.InfoLevel:  LevelInfo,
	zapcore.WarnLevel:  LevelWarn,
	zapcore.ErrorLevel: LevelError,
	zapcore.FatalLevel: LevelFatal,
}

// NewZapLog creates a workflow default Logger from zap whose caller skip is set to 2.
func NewZapLog(c Config) Logger {
	return NewZapLogWithCallerSkip(c, 2)
}

// NewZapLogWithCallerSkip creates a workflow default Logger from zap.
func NewZapLogWithCallerSkip(c Config, callerSkip int) Logger {
	var (
		cores  []zapcore.Core
		levels []zap.AtomicLevel
	)
	for _, o := range c {
		writer := GetWriter(o.Writer)
		if writer == nil {
			panic("log: writer core: " + o.Writer + " no registered")
		}
		decoder := &Decoder{OutputConfig: &o}
		if err := writer.Setup(o.Writer, decoder); err != nil {
			panic("log: writer core: " + o.Writer + " setup fail: " + err.Error())
		}
		cores = append(cores, decoder.Core)
		levels = append(levels, decoder.ZapLevel)
	}
	return &zapLog{
		levels: levels,
		logger: zap.New(
			zapcore.NewTee(cores...),
			zap.AddCallerSkip(callerSkip),
			zap.AddCaller(),
		),
	}
}

func newEncoder(c *OutputConfig) zapcore.Encoder {
	encoderCfg := zapcore.EncoderConfig{
		TimeKey:        GetLogEncoderKey("T", c.FormatConfig.TimeKey),
		LevelKey:       GetLogEncoderKey("L", c.FormatConfig.LevelKey),
		NameKey:        GetLogEncoderKey("N", c.FormatConfig.NameKey),
		CallerKey:      GetLogEncoderKey("C", c.FormatConfig.CallerKey),
		FunctionKey:    GetLogEncoderKey(zapcore.OmitKey, c.FormatConfig.FunctionKey),
		MessageKey:     GetLogEncoderKey("M", c.FormatConfig.MessageKey),
		StacktraceKey:  GetLogEncoderKey("S", c.FormatConfig.StacktraceKey),
		LineEnding:     zapcore.DefaultLineEnding,
		EncodeLevel:    zapcore.CapitalLevelEncoder,
		EncodeTime:     NewTimeEncoder(c.FormatConfig.TimeFmt),
		EncodeDuration: zapcore.StringDurationEncoder,
		EncodeCaller:   zapcore.ShortCallerEncoder,
	}
	switch c.Formatter {
	case "console":
		return zapcore.NewConsoleEncoder(encoderCfg)
	case "json":
		return zapcore.NewJSONEncoder(encoderCfg)
	default:
		return zapcore.NewConsoleEncoder(encoderCfg)
	}
}

// GetLogEncoderKey gets user defined log output name, uses defKey if empty.
func GetLogEncoderKey(defKey, key string) string {
	if key == "" {
		return defKey
	}
	return key
}

func newConsoleCore(c *OutputConfig) (zapcore.Core, zap.AtomicLevel) {
	lvl := zap.NewAtomicLevelAt(Levels[c.Level])
	return zapcore.NewCore(
		newEncoder(c),
		zapcore.Lock(os.Stdout),
		lvl), lvl
}

func newFileCore(c *OutputConfig) (zapcore.Core, zap.AtomicLevel, error) {
	opts := []rollwriter.Option{
		rollwriter.WithMaxAge(c.WriteConfig.MaxAge),
		rollwriter.WithMaxBackups(c.WriteConfig.MaxBackups),
		rollwriter.WithCompress(c.WriteConfig.Compress),
		rollwriter.WithMaxSize(c.WriteConfig.MaxSize),
	}
	// roll by time.
	if c.WriteConfig.RollType != RollBySize {
		opts = append(opts, rollwriter.WithRotationTime(c.WriteConfig.TimeUnit.Format()))
	}
	writer, err := rollwriter.NewRollWriter(c.WriteConfig.Filename, opts...)
	if err != nil {
		return nil, zap.AtomicLevel{}, err
	}

	// write mod.
	var ws zapcore.WriteSyncer
	if c.WriteConfig.WriteMode == WriteSync {
		ws = zapcore.AddSync(writer)
	} else {
		dropLog := c.WriteConfig.WriteMode == WriteFast
		ws = rollwriter.NewAsyncRollWriter(writer,
			rollwriter.WithDropLog(dropLog),
		)
	}

	// log level.
	lvl := zap.NewAtomicLevelAt(Levels[c.Level])
	return zapcore.NewCore(
		newEncoder(c),
		ws, lvl,
	), lvl, nil
}

// NewTimeEncoder creates a time format encoder.
func NewTimeEncoder(format string) zapcore.TimeEncoder {
	switch format {
	case "":
		return func(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
			enc.AppendByteString(DefaultTimeFormat(t))
		}
	case "seconds":
		return zapcore.EpochTimeEncoder
	case "milliseconds":
		return zapcore.EpochMillisTimeEncoder
	case "nanoseconds":
		return zapcore.EpochNanosTimeEncoder
	default:
		return func(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
			enc.AppendString(CustomTimeFormat(t, format))
		}
	}
}

// CustomTimeFormat customize time format.
func CustomTimeFormat(t time.Time, format string) string {
	return t.Format(format)
}

// DefaultTimeFormat returns the default time format.
func DefaultTimeFormat(t time.Time) []byte {
	t = t.Local()
	year, month, day := t.Date()
	hour, minute, second := t.Clock()
	micros := t.Nanosecond() / 1000

	buf := make([]byte, 23)
	buf[0] = byte((year/1000)%10) + '0'
	buf[1] = byte((year/100)%10) + '0'
	buf[2] = byte((year/10)%10) + '0'
	buf[3] = byte(year%10) + '0'
	buf[4] = '-'
	buf[5] = byte((month)/10) + '0'
	buf[6] = byte((month)%10) + '0'
	buf[7] = '-'
	buf[8] = byte((day)/10) + '0'
	buf[9] = byte((day)%10) + '0'
	buf[10] = ' '
	buf[11] = byte((hour)/10) + '0'
	buf[12] = byte((hour)%10) + '0'
	buf[13] = ':'
	buf[14] = byte((minute)/10) + '0'
	buf[15] = byte((minute)%10) + '0'
	buf[16] = ':'
	buf[17] = byte((second)/10) + '0'
	buf[18] = byte((second)%10) + '0'
	buf[19] = '.'
	buf[20] = byte((micros/100000)%10) + '0'
	buf[21] = byte((micros/10000)%10) + '0'
	buf[22] = byte((micros/1000)%10) + '0'
	return buf
}

// ZapLogWrapper delegates zapLogger which was introduced in this
// By ZapLogWrapper proxy, we can add a layer to the debug series function calls, so that the caller
// information can be set correctly.
type ZapLogWrapper struct {
	l *zapLog
}

// GetLogger returns interval zapLog.
func (z *ZapLogWrapper) GetLogger() Logger {
	return z.l
}

// Trace logs to TRACE log. Arguments are handled in the manner of fmt.Print.
func (z *ZapLogWrapper) Trace(args ...interface{}) {
	z.l.Trace(args...)
}

// Tracef logs to TRACE log. Arguments are handled in the manner of fmt.Printf.
func (z *ZapLogWrapper) Tracef(format string, args ...interface{}) {
	z.l.Tracef(format, args...)
}

// Debug logs to DEBUG log. Arguments are handled in the manner of fmt.Print.
func (z *ZapLogWrapper) Debug(args ...interface{}) {
	z.l.Debug(args...)
}

// Debugf logs to DEBUG log. Arguments are handled in the manner of fmt.Printf.
func (z *ZapLogWrapper) Debugf(format string, args ...interface{}) {
	z.l.Debugf(format, args...)
}

// Info logs to INFO log. Arguments are handled in the manner of fmt.Print.
func (z *ZapLogWrapper) Info(args ...interface{}) {
	z.l.Info(args...)
}

// Infof logs to INFO log. Arguments are handled in the manner of fmt.Printf.
func (z *ZapLogWrapper) Infof(format string, args ...interface{}) {
	z.l.Infof(format, args...)
}

// Warn logs to WARNING log. Arguments are handled in the manner of fmt.Print.
func (z *ZapLogWrapper) Warn(args ...interface{}) {
	z.l.Warn(args...)
}

// Warnf logs to WARNING log. Arguments are handled in the manner of fmt.Printf.
func (z *ZapLogWrapper) Warnf(format string, args ...interface{}) {
	z.l.Warnf(format, args...)
}

// Error logs to ERROR log. Arguments are handled in the manner of fmt.Print.
func (z *ZapLogWrapper) Error(args ...interface{}) {
	z.l.Error(args...)
}

// Errorf logs to ERROR log. Arguments are handled in the manner of fmt.Printf.
func (z *ZapLogWrapper) Errorf(format string, args ...interface{}) {
	z.l.Errorf(format, args...)
}

// Fatal logs to FATAL log. Arguments are handled in the manner of fmt.Print.
func (z *ZapLogWrapper) Fatal(args ...interface{}) {
	z.l.Fatal(args...)
}

// Fatalf logs to FATAL log. Arguments are handled in the manner of fmt.Printf.
func (z *ZapLogWrapper) Fatalf(format string, args ...interface{}) {
	z.l.Fatalf(format, args...)
}

// Sync calls the zap logger's Sync method, and flushes any buffered log entries.
// Applications should take care to call Sync before exiting.
func (z *ZapLogWrapper) Sync() error {
	return z.l.Sync()
}

// SetLevel set output log level.
func (z *ZapLogWrapper) SetLevel(output string, level Level) {
	z.l.SetLevel(output, level)
}

// GetLevel gets output log level.
func (z *ZapLogWrapper) GetLevel(output string) Level {
	return z.l.GetLevel(output)
}

// WithFields set some user defined data to logs, such as uid, imei, etc.
// Use this function at the beginning of each request. The returned new Logger should be used to
// print logs.
// Fields must be paired.
// Deprecated: use With instead.
func (z *ZapLogWrapper) WithFields(fields ...string) Logger {
	return z.l.WithFields(fields...)
}

// With add user defined fields to Logger. Fields support multiple values.
func (z *ZapLogWrapper) With(fields ...Field) Logger {
	return z.l.With(fields...)
}

// zapLog is a Logger implementation based on zaplogger.
type zapLog struct {
	levels []zap.AtomicLevel
	logger *zap.Logger
}

// WithFields set some user defined data to logs, such as uid, imei, etc.
// Use this function at the beginning of each request. The returned new Logger should be used to
// print logs.
// Fields must be paired.
// Deprecated: use With instead.
func (l *zapLog) WithFields(fields ...string) Logger {
	zapFields := make([]zap.Field, len(fields)/2)
	for i := range zapFields {
		zapFields[i] = zap.String(fields[2*i], fields[2*i+1])
	}

	// By ZapLogWrapper proxy, we can add a layer to the debug series function calls, so that the
	// caller information can be set correctly.
	return &ZapLogWrapper{
		l: &zapLog{
			levels: l.levels,
			logger: l.logger.With(zapFields...)}}
}

// With add user defined fields to Logger. Fields support multiple values.
func (l *zapLog) With(fields ...Field) Logger {
	zapFields := make([]zap.Field, len(fields))
	for i := range fields {
		zapFields[i] = zap.Any(fields[i].Key, fields[i].Value)
	}

	// By ZapLogWrapper proxy, we can add a layer to the debug series function calls, so that the
	// caller information can be set correctly.
	return &ZapLogWrapper{
		l: &zapLog{
			levels: l.levels,
			logger: l.logger.With(zapFields...)}}
}

func getLogMsg(args ...interface{}) string {
	msg := fmt.Sprint(args...)
	return msg
}

func getLogMsgf(format string, args ...interface{}) string {
	msg := fmt.Sprintf(format, args...)
	return msg
}

// Trace logs to TRACE log. Arguments are handled in the manner of fmt.Print.
func (l *zapLog) Trace(args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.DebugLevel) {
		l.logger.Debug(getLogMsg(args...))
	}
}

// Tracef logs to TRACE log. Arguments are handled in the manner of fmt.Printf.
func (l *zapLog) Tracef(format string, args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.DebugLevel) {
		l.logger.Debug(getLogMsgf(format, args...))
	}
}

// Debug logs to DEBUG log. Arguments are handled in the manner of fmt.Print.
func (l *zapLog) Debug(args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.DebugLevel) {
		l.logger.Debug(getLogMsg(args...))
	}
}

// Debugf logs to DEBUG log. Arguments are handled in the manner of fmt.Printf.
func (l *zapLog) Debugf(format string, args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.DebugLevel) {
		l.logger.Debug(getLogMsgf(format, args...))
	}
}

// Info logs to INFO log. Arguments are handled in the manner of fmt.Print.
func (l *zapLog) Info(args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.InfoLevel) {
		l.logger.Info(getLogMsg(args...))
	}
}

// Infof logs to INFO log. Arguments are handled in the manner of fmt.Printf.
func (l *zapLog) Infof(format string, args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.InfoLevel) {
		l.logger.Info(getLogMsgf(format, args...))
	}
}

// Warn logs to WARNING log. Arguments are handled in the manner of fmt.Print.
func (l *zapLog) Warn(args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.WarnLevel) {
		l.logger.Warn(getLogMsg(args...))
	}
}

// Warnf logs to WARNING log. Arguments are handled in the manner of fmt.Printf.
func (l *zapLog) Warnf(format string, args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.WarnLevel) {
		l.logger.Warn(getLogMsgf(format, args...))
	}
}

// Error logs to ERROR log. Arguments are handled in the manner of fmt.Print.
func (l *zapLog) Error(args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.ErrorLevel) {
		l.logger.Error(getLogMsg(args...))
	}
}

// Errorf logs to ERROR log. Arguments are handled in the manner of fmt.Printf.
func (l *zapLog) Errorf(format string, args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.ErrorLevel) {
		l.logger.Error(getLogMsgf(format, args...))
	}
}

// Fatal logs to FATAL log. Arguments are handled in the manner of fmt.Print.
func (l *zapLog) Fatal(args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.FatalLevel) {
		l.logger.Fatal(getLogMsg(args...))
	}
}

// Fatalf logs to FATAL log. Arguments are handled in the manner of fmt.Printf.
func (l *zapLog) Fatalf(format string, args ...interface{}) {
	if l.logger.Core().Enabled(zapcore.FatalLevel) {
		l.logger.Fatal(getLogMsgf(format, args...))
	}
}

// Sync calls the zap logger's Sync method, and flushes any buffered log entries.
// Applications should take care to call Sync before exiting.
func (l *zapLog) Sync() error {
	return l.logger.Sync()
}

// SetLevel sets output log level.
func (l *zapLog) SetLevel(output string, level Level) {
	i, e := strconv.Atoi(output)
	if e != nil {
		return
	}
	if i < 0 || i >= len(l.levels) {
		return
	}
	l.levels[i].SetLevel(levelToZapLevel[level])
}

// GetLevel gets output log level.
func (l *zapLog) GetLevel(output string) Level {
	i, e := strconv.Atoi(output)
	if e != nil {
		return LevelDebug
	}
	if i < 0 || i >= len(l.levels) {
		return LevelDebug
	}
	return zapLevelToLevel[l.levels[i].Level()]
}
