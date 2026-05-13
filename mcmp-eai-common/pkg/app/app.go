package app

import (
	"context"
	"errors"
	"sync"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/lock"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// DataSource defines the interface for a data source (vCenter, NetApp, etc.)
type DataSource[T any] interface {
	// Name returns the unique name of the data source (for logging/locking)
	Name() string
	// IsEnabled checks if the data source is enabled
	IsEnabled() bool
	// FetchData retrieves data from the external source
	FetchData(ctx context.Context) (T, error)
	// ProcessData processes and stores the data
	ProcessData(ctx context.Context, data T) error
}

// EAIConfig contains common configuration for all EAIs
type EAIConfig struct {
	AppName     string
	MaxWorkers  int
	LockEnabled bool
}

// RunEAI executes the common bot logic
func RunEAI[T any](ctx context.Context, cfg EAIConfig, sources []DataSource[T], logger logging.Logger) error {
	startTime := time.Now()
	logger.Info("Program started", "time", startTime.Format(time.RFC3339))

	// Lock handling (optional)
	if cfg.LockEnabled {
		release, err := lock.Acquire(cfg.AppName)
		if err != nil {
			logger.Error("Error creating lock file", "error", err)
			return err
		}
		defer release()
	}

	var waitGroup sync.WaitGroup
	errorChannel := make(chan error, len(sources))

	for _, dataSource := range sources {
		if !dataSource.IsEnabled() {
			continue
		}

		waitGroup.Add(1)
		go func(src DataSource[T]) {
			defer waitGroup.Done()

			sourceStartTime := time.Now()
			logger.Info("Starting processing", "source", src.Name())

			data, err := src.FetchData(ctx)
			if err != nil {
				logger.Error("FetchData failed", "source", src.Name(), "error", err)
				errorChannel <- err
				return
			}

			if err := src.ProcessData(ctx, data); err != nil {
				logger.Error("ProcessData failed", "source", src.Name(), "error", err)
				errorChannel <- err
				return
			}

			logger.Info("Processing completed",
				"source", src.Name(),
				"duration", time.Since(sourceStartTime).String())
		}(dataSource)
	}

	waitGroup.Wait()
	close(errorChannel)

	// Collect errors
	var allErrors error
	for err := range errorChannel {
		allErrors = errors.Join(allErrors, err)
	}

	logger.Info("Program ended",
		"duration", time.Since(startTime).String())

	return allErrors
}
