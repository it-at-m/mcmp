package logging

import (
	"fmt"
	"io"
	"log"
	"log/slog"
	"os"
	"strings"

	"gopkg.in/natefinch/lumberjack.v2"
)

// SetupGlobalLogger initializes a StructuredLogger with defaults and redirects standard library logging.
// This allows for a one-line setup in applications.
func SetupGlobalLogger(config LogConfig) (*StructuredLogger, error) {
	// 1. Apply defaults to ensure valid configuration
	config.SetDefaults()

	// 2. Create the structured logger
	logger, err := NewStructuredLogger(config)
	if err != nil {
		return nil, err
	}

	// 3. Redirect standard library "log" output to this logger's writer
	// This ensures dependencies using log.Println also go to our structured output
	log.SetOutput(logger.GetWriter())
	log.SetPrefix("[STDLIB] ")
	// Remove flags like Ldate | Ltime because our handler already adds timestamps
	log.SetFlags(0)

	return logger, nil
}

// StructuredLogger provides structured logging capabilities with support for different
// log levels, file rotation, and configurable output destinations.
// It wraps Go's structured logging (slog) with additional functionality for
// production use cases like file rotation and retention policies.
type StructuredLogger struct {
	logger *slog.Logger
	writer io.Writer // Store the writer for external use
}

// NewStructuredLogger creates a new StructuredLogger instance with the specified configuration.
// It sets up output destinations, log levels, and file rotation based on the provided config.
//
// Parameters:
//   - config: LogConfig containing all logging configuration options
//
// Returns:
//   - *StructuredLogger: A configured logger ready for use
//   - error: An error if setup fails (invalid config, file permissions, etc.)
//
// Example:
//
//	config := LogConfig{
//		Level:      "INFO",
//		Output:     "file",
//		Filename:   "/var/log/app.log",
//		MaxSize:    100,
//		MaxBackups: 3,
//		MaxAge:     28,
//		Compress:   true,
//	}
//	logger, err := NewStructuredLogger(config)
func NewStructuredLogger(config LogConfig) (*StructuredLogger, error) {
	// Parse log level
	var level slog.Level
	switch strings.ToUpper(config.Level) {
	case "DEBUG":
		level = slog.LevelDebug
	case "INFO":
		level = slog.LevelInfo
	case "WARN":
		level = slog.LevelWarn
	case "ERROR":
		level = slog.LevelError
	default:
		level = slog.LevelInfo
	}

	// Configure output destination
	var writer io.Writer
	var lumberjackLogger *lumberjack.Logger

	switch strings.ToLower(config.Output) {
	case "console":
		writer = os.Stderr
	case "file":
		if config.Filename == "" {
			config.Filename = "application.log"
		}
		lumberjackLogger = &lumberjack.Logger{
			Filename:   config.Filename,
			MaxSize:    config.MaxSize,
			MaxBackups: config.MaxBackups,
			MaxAge:     config.MaxAge,
			Compress:   config.Compress,
		}
		writer = lumberjackLogger
	case "both":
		if config.Filename == "" {
			config.Filename = "application.log"
		}
		lumberjackLogger = &lumberjack.Logger{
			Filename:   config.Filename,
			MaxSize:    config.MaxSize,
			MaxBackups: config.MaxBackups,
			MaxAge:     config.MaxAge,
			Compress:   config.Compress,
		}
		writer = io.MultiWriter(os.Stderr, lumberjackLogger)
	default:
		writer = os.Stderr
	}

	// Create structured logger with configurable format
	opts := &slog.HandlerOptions{
		Level: level,
	}

	var handler slog.Handler
	switch strings.ToLower(config.Format) {
	case "json":
		handler = slog.NewJSONHandler(writer, opts)
	case "text":
		handler = slog.NewTextHandler(writer, opts)
	case "plain":
		handler = NewPlainTextHandler(writer, level)
	default:
		// Default to text format for better readability
		handler = slog.NewTextHandler(writer, opts)
	}

	logger := slog.New(handler)

	return &StructuredLogger{
		logger: logger,
		writer: writer,
	}, nil
}

// Info logs an informational message with optional key-value attributes.
func (sl *StructuredLogger) Info(msg string, args ...any) {
	sl.logger.Info(msg, args...)
}

// Debug logs a debug message with optional key-value attributes.
// Only outputs if debug level is enabled.
func (sl *StructuredLogger) Debug(msg string, args ...any) {
	sl.logger.Debug(msg, args...)
}

// Warn logs a warning message with optional key-value attributes.
func (sl *StructuredLogger) Warn(msg string, args ...any) {
	sl.logger.Warn(msg, args...)
}

// Error logs an error message with optional key-value attributes.
func (sl *StructuredLogger) Error(msg string, args ...any) {
	sl.logger.Error(msg, args...)
}

// Printf provides compatibility with the Logger interface.
// Maps to Info level for compatibility.
func (sl *StructuredLogger) Printf(format string, v ...interface{}) {
	// If it's a simple string without placeholders, use it as is.
	// Otherwise, format it first.
	if len(v) == 0 {
		sl.logger.Info(format)
	} else {
		sl.logger.Info(fmt.Sprintf(format, v...))
	}
}

// DebugPrintf provides conditional debug logging with Printf-style formatting.
func (sl *StructuredLogger) DebugPrintf(format string, args ...interface{}) {
	if len(args) == 0 {
		sl.logger.Debug(format)
	} else {
		sl.logger.Debug(fmt.Sprintf(format, args...))
	}
}

// GetWriter returns the underlying io.Writer used by this logger.
// This can be useful for integrating with other logging systems like GORM.
func (sl *StructuredLogger) GetWriter() io.Writer {
	return sl.writer
}
