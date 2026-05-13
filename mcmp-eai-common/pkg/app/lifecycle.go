package app

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"syscall"
)

// RunFunc defines the signature for the main application entry point
type RunFunc func(ctx context.Context) error

// Bootstrap handles the standard application lifecycle:
// 1. Creates a cancellable context.
// 2. Sets up signal handling (SIGINT, SIGTERM).
// 3. Executes the provided run function.
// 4. Handles the exit code based on the error returned.
func Bootstrap(run RunFunc) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Signal handler for graceful shutdown
	signalChan := make(chan os.Signal, 1)
	signal.Notify(signalChan, os.Interrupt, syscall.SIGTERM)

	// Listen for signals in background
	go func() {
		sig := <-signalChan
		// We use stderr here because the logger might not be initialized yet
		// or might be local to the run function.
		_, _ = fmt.Fprintf(os.Stderr, "\nReceived signal: %v. Shutting down...\n", sig)
		cancel()
	}()

	if err := run(ctx); err != nil {
		// If the error is just "context canceled", it's a clean shutdown
		if errors.Is(err, context.Canceled) {
			return
		}

		// Otherwise it's a real error
		_, _ = fmt.Fprintf(os.Stderr, "Application error: %v\n", err)
		os.Exit(1)
	}
}
