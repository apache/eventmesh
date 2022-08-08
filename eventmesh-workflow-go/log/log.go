// Package log provides a log for the framework and applications.
package log

import (
	"fmt"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

// SetLevel sets log level for different output which may be "0", "1" or "2".
func SetLevel(output string, level Level) {
	GetDefaultLogger().SetLevel(output, level)
}

// GetLevel gets log level for different output.
func GetLevel(output string) Level {
	return GetDefaultLogger().GetLevel(output)
}

// WithFields sets some user defined data to logs, such as, uid, imei. Fields must be paired.
// Deprecated: use With instead.
func WithFields(fields ...string) Logger {
	return GetDefaultLogger().WithFields(fields...)
}

// With adds user defined fields to Logger. Field support multiple values.
func With(fields ...Field) Logger {
	return GetDefaultLogger().With(fields...)
}

// RedirectStdLog redirects std log to workflow logger as log level INFO.
// After redirection, log flag is zero, the prefix is empty.
// The returned function may be used to recover log flag and prefix, and redirect output to
// os.Stderr.
func RedirectStdLog(logger Logger) (func(), error) {
	return RedirectStdLogAt(logger, zap.InfoLevel)
}

// RedirectStdLogAt redirects std log to workflow logger with a specific level.
// After redirection, log flag is zero, the prefix is empty.
// The returned function may be used to recover log flag and prefix, and redirect output to
// os.Stderr.
func RedirectStdLogAt(logger Logger, level zapcore.Level) (func(), error) {
	if l, ok := logger.(*zapLog); ok {
		return zap.RedirectStdLogAt(l.logger, level)
	}
	if l, ok := logger.(*ZapLogWrapper); ok {
		return zap.RedirectStdLogAt(l.l.logger, level)
	}
	return nil, fmt.Errorf("log: only supports redirecting std logs to workflow zap logger")
}

// Debug logs to DEBUG log. Arguments are handled in the manner of fmt.Print.
func Debug(args ...interface{}) {
	GetDefaultLogger().Debug(args...)
}

// Debugf logs to DEBUG log. Arguments are handled in the manner of fmt.Printf.
func Debugf(format string, args ...interface{}) {
	GetDefaultLogger().Debugf(format, args...)
}

// Info logs to INFO log. Arguments are handled in the manner of fmt.Print.
func Info(args ...interface{}) {
	GetDefaultLogger().Info(args...)
}

// Infof logs to INFO log. Arguments are handled in the manner of fmt.Printf.
func Infof(format string, args ...interface{}) {
	GetDefaultLogger().Infof(format, args...)
}

// Warn logs to WARNING log. Arguments are handled in the manner of fmt.Print.
func Warn(args ...interface{}) {
	GetDefaultLogger().Warn(args...)
}

// Warnf logs to WARNING log. Arguments are handled in the manner of fmt.Printf.
func Warnf(format string, args ...interface{}) {
	GetDefaultLogger().Warnf(format, args...)
}

// Error logs to ERROR log. Arguments are handled in the manner of fmt.Print.
func Error(args ...interface{}) {
	GetDefaultLogger().Error(args...)
}

// Errorf logs to ERROR log. Arguments are handled in the manner of fmt.Printf.
func Errorf(format string, args ...interface{}) {
	GetDefaultLogger().Errorf(format, args...)
}

// Fatal logs to ERROR log. Arguments are handled in the manner of fmt.Print.
// All Fatal logs will exit by calling os.Exit(1).
// Implementations may also call os.Exit() with a non-zero exit code.
func Fatal(args ...interface{}) {
	GetDefaultLogger().Fatal(args...)
}

// Fatalf logs to ERROR log. Arguments are handled in the manner of fmt.Printf.
func Fatalf(format string, args ...interface{}) {
	GetDefaultLogger().Fatalf(format, args...)
}
