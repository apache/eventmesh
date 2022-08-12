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
	"errors"
	"fmt"
	"sync"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

func init() {
	RegisterWriter(OutputConsole, DefaultConsoleWriterFactory)
	RegisterWriter(OutputFile, DefaultFileWriterFactory)
	Register(defaultLoggerName, NewZapLog(defaultConfig))
	plugin.Register(defaultLoggerName, DefaultLogFactory)
}

const (
	pluginType        = "log"
	defaultLoggerName = "default"
)

var (
	// DefaultLogger the default Logger. The initial output is console. When frame start, it is
	// over write by configuration.
	DefaultLogger Logger
	// DefaultLogFactory is the default log loader. Users may replace it with their own
	// implementation.
	DefaultLogFactory = &Factory{}

	mu      sync.RWMutex
	loggers = make(map[string]Logger)
)

// Register registers Logger. It supports multiple Logger implementation.
func Register(name string, logger Logger) {
	mu.Lock()
	defer mu.Unlock()
	if logger == nil {
		panic("log: Register logger is nil")
	}
	if _, dup := loggers[name]; dup && name != defaultLoggerName {
		panic("log: Register called twice for logger name " + name)
	}
	loggers[name] = logger
	if name == defaultLoggerName {
		DefaultLogger = logger
	}
}

// GetDefaultLogger gets the default Logger.
// To configure it, set key in configuration file to default.
// The console output is the default value.
func GetDefaultLogger() Logger {
	mu.RLock()
	l := DefaultLogger
	mu.RUnlock()
	return l
}

// SetLogger sets the default Logger.
func SetLogger(logger Logger) {
	mu.Lock()
	DefaultLogger = logger
	mu.Unlock()
}

// Get returns the Logger implementation by log name.
// log.Debug use DefaultLogger to print logs. You may also use log.Get("name").Debug.
func Get(name string) Logger {
	mu.RLock()
	l := loggers[name]
	mu.RUnlock()
	return l
}

// Decoder decodes the log.
type Decoder struct {
	OutputConfig *OutputConfig
	Core         zapcore.Core
	ZapLevel     zap.AtomicLevel
}

// Decode decodes writer configuration, copy one.
func (d *Decoder) Decode(cfg interface{}) error {
	output, ok := cfg.(**OutputConfig)
	if !ok {
		return fmt.Errorf("decoder config type:%T invalid, not **OutputConfig", cfg)
	}
	*output = d.OutputConfig
	return nil
}

// Factory is the log plugin factory.
// When server start, the configuration is feed to Factory to generate a log instance.
type Factory struct {
}

// Type returns the log plugin type.
func (f *Factory) Type() string {
	return pluginType
}

// Setup starts, load and register logs.
func (f *Factory) Setup(name string, dec plugin.Decoder) error {
	if dec == nil {
		return errors.New("log config decoder empty")
	}
	cfg, callerSkip, err := f.setupConfig(dec)
	if err != nil {
		return err
	}
	logger := NewZapLogWithCallerSkip(cfg, callerSkip)
	if logger == nil {
		return errors.New("new zap logger fail")
	}
	Register(name, logger)
	return nil
}

func (f *Factory) setupConfig(configDec plugin.Decoder) (Config, int, error) {
	cfg := Config{}
	if err := configDec.Decode(&cfg); err != nil {
		return nil, 0, err
	}
	if len(cfg) == 0 {
		return nil, 0, errors.New("log config output empty")
	}

	// If caller skip is not configured, use 2 as default.
	callerSkip := 2
	for i := 0; i < len(cfg); i++ {
		if cfg[i].CallerSkip != 0 {
			callerSkip = cfg[i].CallerSkip
		}
	}
	return cfg, callerSkip, nil
}
