package logging

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"strings"
)

// PlainTextHandler is a custom slog.Handler that outputs log messages in plain text format
// without key=value pairs, just space-separated values in a defined order.
type PlainTextHandler struct {
	writer io.Writer
	level  slog.Level
}

// NewPlainTextHandler creates a new PlainTextHandler with the specified writer and level.
func NewPlainTextHandler(writer io.Writer, level slog.Level) *PlainTextHandler {
	return &PlainTextHandler{
		writer: writer,
		level:  level,
	}
}

// Enabled reports whether the handler handles records at the given level.
func (h *PlainTextHandler) Enabled(ctx context.Context, level slog.Level) bool {
	return level >= h.level
}

// Handle handles the Record by formatting it as plain text without keys.
// Format: TIMESTAMP LEVEL MESSAGE [ATTRIBUTES...]
func (h *PlainTextHandler) Handle(ctx context.Context, r slog.Record) error {
	// Start with timestamp
	timestamp := r.Time.Format("2006/01/02 15:04:05")

	// Add level
	level := r.Level.String()

	// Start building the output
	output := fmt.Sprintf("%s [%s] %s", timestamp, level, r.Message)

	// Add attributes as key=value pairs
	var attrs []string
	r.Attrs(func(a slog.Attr) bool {
		// Format as key=value
		attrs = append(attrs, fmt.Sprintf("%s=%v", a.Key, a.Value))
		return true
	})

	// Append attributes if any exist
	if len(attrs) > 0 {
		output += " " + strings.Join(attrs, " ")
	}

	// Write to output with newline
	_, err := fmt.Fprintln(h.writer, output)
	return err
}

// WithAttrs returns a new PlainTextHandler with the given attributes.
// For simplicity, this implementation ignores persistent attributes.
func (h *PlainTextHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	return h
}

// WithGroup returns a new PlainTextHandler for the given group.
// For simplicity, this implementation ignores groups.
func (h *PlainTextHandler) WithGroup(name string) slog.Handler {
	return h
}
