package log

import (
	"time"

	"gopkg.in/yaml.v3"
)

// output name, default support console and file.
const (
	OutputConsole = "console"
	OutputFile    = "file"
)

// Config is the log config. Each log may have multiple outputs.
type Config []OutputConfig

// OutputConfig is the output config, includes console, file and remote.
type OutputConfig struct {
	// Writer is the output of log, such as console or file.
	Writer      string
	WriteConfig WriteConfig `yaml:"writer_config"`

	// Formatter is the format of log, such as console or json.
	Formatter    string
	FormatConfig FormatConfig `yaml:"formatter_config"`

	// RemoteConfig is the remote config. It's defined by business and should be registered by
	// third-party modules.
	RemoteConfig yaml.Node `yaml:"remote_config"`

	// Level controls the log level, like debug, info or error.
	Level string

	// CallerSkip controls the nesting depth of log function.
	CallerSkip int `yaml:"caller_skip"`
}

// WriteConfig is the local file config.
type WriteConfig struct {
	// LogPath is the log path like /usr/local/workflow/log/.
	LogPath string `yaml:"log_path"`
	// Filename is the file name like workflow.log.
	Filename string `yaml:"filename"`
	// WriteMode is the log write mod. 1: sync, 2: async, 3: fast(maybe dropped).
	WriteMode int `yaml:"write_mode"`
	// RollType is the log rolling type. Split files by size/time, default by size.
	RollType string `yaml:"roll_type"`
	// MaxAge is the max expire times(day).
	MaxAge int `yaml:"max_age"`
	// MaxBackups is the max backup files.
	MaxBackups int `yaml:"max_backups"`
	// Compress defines whether log should be compressed.
	Compress bool `yaml:"compress"`
	// MaxSize is the max size of log file(MB).
	MaxSize int `yaml:"max_size"`

	// TimeUnit splits files by time unit, like year/month/hour/minute, default day.
	// It takes effect only when split by time.
	TimeUnit TimeUnit `yaml:"time_unit"`
}

// FormatConfig is the log format config.
type FormatConfig struct {
	// TimeFmt is the time format of log output, default as "2006-01-02 15:04:05.000" on empty.
	TimeFmt string `yaml:"time_fmt"`

	// TimeKey is the time key of log output, default as "T".
	TimeKey string `yaml:"time_key"`
	// LevelKey is the level key of log output, default as "L".
	LevelKey string `yaml:"level_key"`
	// NameKey is the name key of log output, default as "N".
	NameKey string `yaml:"name_key"`
	// CallerKey is the caller key of log output, default as "C".
	CallerKey string `yaml:"caller_key"`
	// FunctionKey is the function key of log output, default as "", which means not to print
	// function name.
	FunctionKey string `yaml:"function_key"`
	// MessageKey is the message key of log output, default as "M".
	MessageKey string `yaml:"message_key"`
	// StackTraceKey is the stack trace key of log output, default as "S".
	StacktraceKey string `yaml:"stacktrace_key"`
}

// WriteMode is the log write mode, one of 1, 2, 3.
type WriteMode int

const (
	// WriteSync writes synchronously.
	WriteSync = 1
	// WriteAsync writes asynchronously.
	WriteAsync = 2
	// WriteFast writes fast(may drop logs asynchronously).
	WriteFast = 3
)

// By which log rolls.
const (
	// RollBySize rolls logs by file size.
	RollBySize = "size"
	// RollByTime rolls logs by time.
	RollByTime = "time"
)

// Some common used time formats.
const (
	// TimeFormatMinute is accurate to the minute.
	TimeFormatMinute = "%Y%m%d%H%M"
	// TimeFormatHour is accurate to the hour.
	TimeFormatHour = "%Y%m%d%H"
	// TimeFormatDay is accurate to the day.
	TimeFormatDay = "%Y%m%d"
	// TimeFormatMonth is accurate to the month.
	TimeFormatMonth = "%Y%m"
	// TimeFormatYear is accurate to the year.
	TimeFormatYear = "%Y"
)

// TimeUnit is the time unit by which files are split, one of minute/hour/day/month/year.
type TimeUnit string

const (
	// Minute splits by the minute.
	Minute = "minute"
	// Hour splits by the hour.
	Hour = "hour"
	// Day splits by the day.
	Day = "day"
	// Month splits by the month.
	Month = "month"
	// Year splits by the year.
	Year = "year"
)

// Format returns a string preceding with `.`. Use TimeFormatDay as default.
func (t TimeUnit) Format() string {
	var timeFmt string
	switch t {
	case Minute:
		timeFmt = TimeFormatMinute
	case Hour:
		timeFmt = TimeFormatHour
	case Day:
		timeFmt = TimeFormatDay
	case Month:
		timeFmt = TimeFormatMonth
	case Year:
		timeFmt = TimeFormatYear
	default:
		timeFmt = TimeFormatDay
	}
	return "." + timeFmt
}

// RotationGap returns the time.Duration for time unit. Use one day as the default.
func (t TimeUnit) RotationGap() time.Duration {
	switch t {
	case Minute:
		return time.Minute
	case Hour:
		return time.Hour
	case Day:
		return time.Hour * 24
	case Month:
		return time.Hour * 24 * 30
	case Year:
		return time.Hour * 24 * 365
	default:
		return time.Hour * 24
	}
}
