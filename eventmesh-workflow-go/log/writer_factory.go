package log

import (
	"errors"
	"path/filepath"

	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/plugin"
)

var (
	// DefaultConsoleWriterFactory is the default console output implementation.
	DefaultConsoleWriterFactory = &ConsoleWriterFactory{}
	// DefaultFileWriterFactory is the default file output implementation.
	DefaultFileWriterFactory = &FileWriterFactory{}

	writers = make(map[string]plugin.Factory)
)

// RegisterWriter registers log output writer. Writer may have multiple implementations.
func RegisterWriter(name string, writer plugin.Factory) {
	writers[name] = writer
}

// GetWriter gets log output writer, returns nil if not exist.
func GetWriter(name string) plugin.Factory {
	return writers[name]
}

// ConsoleWriterFactory is the console writer instance.
type ConsoleWriterFactory struct {
}

// Setup starts, loads and registers console output writer.
func (f *ConsoleWriterFactory) Setup(name string, dec plugin.Decoder) error {
	if dec == nil {
		return errors.New("console writer decoder empty")
	}
	decoder, ok := dec.(*Decoder)
	if !ok {
		return errors.New("console writer log decoder type invalid")
	}
	cfg := &OutputConfig{}
	if err := decoder.Decode(&cfg); err != nil {
		return err
	}
	decoder.Core, decoder.ZapLevel = newConsoleCore(cfg)
	return nil
}

// Type returns log file type.
func (f *FileWriterFactory) Type() string {
	return pluginType
}

// FileWriterFactory is the file writer instance Factory.
type FileWriterFactory struct {
}

// Type returns log file type.
func (f *ConsoleWriterFactory) Type() string {
	return pluginType
}

// Setup starts, loads and register file output writer.
func (f *FileWriterFactory) Setup(name string, dec plugin.Decoder) error {
	if dec == nil {
		return errors.New("file writer decoder empty")
	}
	decoder, ok := dec.(*Decoder)
	if !ok {
		return errors.New("file writer log decoder type invalid")
	}
	if err := f.setupConfig(decoder); err != nil {
		return err
	}
	return nil
}

func (f *FileWriterFactory) setupConfig(decoder *Decoder) error {
	cfg := &OutputConfig{}
	if err := decoder.Decode(&cfg); err != nil {
		return err
	}
	if cfg.WriteConfig.LogPath != "" {
		cfg.WriteConfig.Filename = filepath.Join(cfg.WriteConfig.LogPath, cfg.WriteConfig.Filename)
	}
	if cfg.WriteConfig.RollType == "" {
		cfg.WriteConfig.RollType = RollBySize
	}
	if cfg.WriteConfig.WriteMode == 0 {
		// Use WriteFast as default mod.
		// It has better performance, discards logs on full and avoid blocking service.
		cfg.WriteConfig.WriteMode = WriteFast
	}
	core, level, err := newFileCore(cfg)
	if err != nil {
		return err
	}
	decoder.Core, decoder.ZapLevel = core, level
	return nil
}
