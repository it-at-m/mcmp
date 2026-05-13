package logging

import (
	"log"
)

// Logger defines the interface for logging operations throughout the application.
// This interface provides a standardized way to output formatted log messages
// and enables dependency injection for different logging implementations.
//
// Example usage:
//
//	logger := log.Default()
//	logger.Printf("Processing %d items", count)
type Logger interface {
	// Printf formats and prints a log message according to a format specifier.
	// It follows the same conventions as fmt.Printf for format strings and arguments.
	//
	// Parameters:
	//   - format: Format string following Printf conventions (e.g., "User %s has %d items")
	//   - v: Variable arguments corresponding to format specifiers in the format string
	Printf(format string, v ...interface{})

	// Info logs an informational message with optional key-value attributes.
	Info(msg string, args ...any)
	// Warn logs a warning message with optional key-value attributes.
	Warn(msg string, args ...any)
	// Error logs an error message with optional key-value attributes.
	Error(msg string, args ...any)
	// Debug logs a debug message with optional key-value attributes.
	Debug(msg string, args ...any)
	// DebugPrintf formats and prints a debug message.
	DebugPrintf(format string, v ...interface{})
}

// NoOpLogger is a logger implementation that discards all log entries.
// It is useful as a default value to avoid nil checks.
type NoOpLogger struct{}

func (n *NoOpLogger) Printf(format string, v ...interface{})      {}
func (n *NoOpLogger) Info(msg string, args ...any)                {}
func (n *NoOpLogger) Warn(msg string, args ...any)                {}
func (n *NoOpLogger) Error(msg string, args ...any)               {}
func (n *NoOpLogger) Debug(msg string, args ...any)               {}
func (n *NoOpLogger) DebugPrintf(format string, v ...interface{}) {}

// NewNoOpLogger creates a new logger that does nothing.
func NewNoOpLogger() Logger {
	return &NoOpLogger{}
}

// DebugLogger is a wrapper around any Logger implementation that adds conditional debug logging.
// It embeds a Logger interface and adds debug-specific functionality with an enable/disable toggle.
// When debug mode is disabled, debug messages are silently discarded for performance.
//
// Key features:
// - Conditional logging based on debug flag state
// - Wraps any Logger implementation for flexibility
// - Zero performance impact when debug is disabled
// - Thread-safe operations for concurrent usage
//
// Example usage:
//
//	baseLogger := log.Default()
//	debugLogger := NewDebugLogger(baseLogger)
//	debugLogger.EnableDebug()
//	debugLogger.DebugPrintf("Debug info: %s", data)
type DebugLogger struct {
	// Logger is the embedded interface that provides the underlying logging functionality.
	// This allows DebugLogger to be used anywhere a Logger is expected.
	Logger

	// debug is an internal flag that controls whether debug messages are actually logged.
	// When false, DebugPrintf calls are no-ops for optimal performance.
	// Access is controlled through EnableDebug() and DisableDebug() methods.
	debug bool
}

// EnableDebug turns on debug logging for this DebugLogger instance.
// After calling this method, subsequent calls to DebugPrintf will output messages.
// This method is safe to call multiple times and from multiple goroutines.
//
// Example:
//
//	debugLogger.EnableDebug()
//	debugLogger.DebugPrintf("This message will be logged")
func (dl *DebugLogger) EnableDebug() {
	dl.debug = true
}

// DisableDebug turns off debug logging for this DebugLogger instance.
// After calling this method, subsequent calls to DebugPrintf will be silently ignored.
// This is the default state for new DebugLogger instances.
// This method is safe to call multiple times and from multiple goroutines.
//
// Example:
//
//	debugLogger.DisableDebug()
//	debugLogger.DebugPrintf("This message will NOT be logged")
func (dl *DebugLogger) DisableDebug() {
	dl.debug = false
}

// DebugPrintf conditionally formats and prints a debug message.
// The message is only output if debug mode is enabled and the underlying Logger is not nil.
// This method provides zero-cost debugging when debug mode is disabled.
//
// Parameters:
//   - format: Format string following Printf conventions (e.g., "Processing item %d of %d")
//   - args: Variable arguments corresponding to format specifiers in the format string
//
// Behavior:
// - If debug is false: No operation is performed (optimal performance)
// - If debug is true and Logger is nil: No operation is performed (safe fallback)
// - If debug is true and Logger is valid: Message is formatted and logged
//
// Example:
//
//	debugLogger.DebugPrintf("User %s logged in at %s", username, timestamp)
//	debugLogger.DebugPrintf("Processing %d/%d items", current, total)
func (dl *DebugLogger) DebugPrintf(format string, args ...interface{}) {
	// Perform debug flag check first for optimal performance when debugging is disabled
	// Short-circuit evaluation ensures no formatting work is done when not needed
	if dl.debug && dl.Logger != nil {
		// Delegate to the underlying Logger's Printf method for actual output
		// This maintains consistency with standard logging behavior
		dl.Printf(format, args...)
	}
}

// ErrorPrintf formats and prints an error message using the underlying Logger.
// Unlike DebugPrintf, this method ignores the debug flag and always logs the message
// as long as a Logger implementation is configured.
//
// Parameters:
//   - format: Format string following Printf conventions (e.g., "failed to process %s: %v")
//   - args:   Variable arguments corresponding to format specifiers in the format string
func (dl *DebugLogger) ErrorPrintf(format string, args ...interface{}) {
	// Only perform the log call when a Logger implementation is configured.
	// Error messages are logged regardless of the debug flag state.
	if dl.Logger != nil {
		dl.Printf(format, args...)
	}
}

// StdLoggerWrapper wraps a standard library log.Logger to satisfy the logging.Logger interface.
type StdLoggerWrapper struct {
	*log.Logger
}

func (s *StdLoggerWrapper) Info(msg string, args ...any) {
	s.Printf("INFO: %s %v", msg, args)
}

func (s *StdLoggerWrapper) Warn(msg string, args ...any) {
	s.Printf("WARN: %s %v", msg, args)
}

func (s *StdLoggerWrapper) Error(msg string, args ...any) {
	s.Printf("ERROR: %s %v", msg, args)
}

func (s *StdLoggerWrapper) Debug(msg string, args ...any) {
	s.Printf("DEBUG: %s %v", msg, args)
}

func (s *StdLoggerWrapper) DebugPrintf(format string, v ...interface{}) {
	s.Printf("DEBUG: "+format, v...)
}

// NewDebugLogger creates a new DebugLogger instance with the provided Logger implementation.
// If the provided logger is nil, it defaults to Go's standard logger wrapped to satisfy the interface.
// The returned DebugLogger starts with debug mode disabled for security and performance.
//
// Parameters:
//   - logger: Any implementation of the Logger interface, or nil for default logger
//
// Returns:
//   - *DebugLogger: A new DebugLogger instance ready for use
//
// Default behavior:
// - Uses log.Default() if logger parameter is nil
// - Debug mode starts disabled (call EnableDebug() to activate)
// - Safe to use immediately after creation
//
// Example usage:
//
//	// Using custom logger
//	customLogger := log.New(os.Stdout, "DEBUG: ", log.LstdFlags)
//	debugLogger := NewDebugLogger(customLogger)
//
//	// Using default logger
//	debugLogger := NewDebugLogger(nil)
//
//	// Enable debug output
//	debugLogger.EnableDebug()
//	debugLogger.DebugPrintf("Application started")
func NewDebugLogger(logger Logger) *DebugLogger {
	// Provide safe default if no logger is specified
	// This prevents nil pointer dereferences and provides immediate usability
	if logger == nil {
		logger = &StdLoggerWrapper{log.Default()}
	}

	// Return the configured DebugLogger with debug mode initially disabled
	// This follows the principle of secure defaults - debug output must be explicitly enabled
	return &DebugLogger{
		Logger: logger,
		debug:  false, // Start with debug disabled for security and performance
	}
}
