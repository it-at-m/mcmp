package datasource

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"sync"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const defaultFilePermission = 0o644

// JSONSender defines the interface for sending JSON data to MCMP
// Both the common and extended clients should implement this interface
type JSONSender interface {
	SendJSON(ctx context.Context, endpoint string, jsonData []byte) error
}

// DataFetcher defines a function that retrieves data of type T.
// This typically wraps the Processor.AggregateData method.
type DataFetcher[T any] func(ctx context.Context) (T, error)

// JsonFileSource is a generic implementation of the app.DataSource interface.
// It fetches data, marshals it to JSON, and writes it to a file.
type JsonFileSource[T any] struct {
	Hostname       string
	Enabled        bool
	ExportFilename string         // Full filename or pattern
	Fetcher        DataFetcher[T] // The function to call to get data
	Logger         logging.Logger
	McmpClient     JSONSender   // For backward compatibility
	ApiEndpoint    string       // For backward compatibility
	McmpClients    []JSONSender // For multiple clients
	ApiEndpoints   []string     // For multiple endpoints
}

func (s *JsonFileSource[T]) Name() string {
	return s.Hostname
}

func (s *JsonFileSource[T]) IsEnabled() bool {
	return s.Enabled
}

func (s *JsonFileSource[T]) FetchData(ctx context.Context) (T, error) {
	if s.Fetcher == nil {
		var zero T
		return zero, fmt.Errorf("fetcher function is not initialized")
	}
	return s.Fetcher(ctx)
}

func (s *JsonFileSource[T]) ProcessData(ctx context.Context, data T) error {
	// 1. Marshal to JSON
	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		s.Logger.Error("failed to marshal data to JSON", "hostname", s.Hostname, "error", err)
		return fmt.Errorf("failed to marshal data: %w", err)
	}

	// 2. Write to File
	if err := os.WriteFile(s.ExportFilename, jsonData, defaultFilePermission); err != nil {
		s.Logger.Error("failed to write data to file", "filename", s.ExportFilename, "error", err)
		return fmt.Errorf("failed to write data to file %s: %w", s.ExportFilename, err)
	}

	// 3. Send data to MCMP APIs
	if len(s.McmpClients) > 0 {
		var wg sync.WaitGroup
		errCh := make(chan error, len(s.McmpClients))
		for i, client := range s.McmpClients {
			endpoint := ""
			if i < len(s.ApiEndpoints) {
				endpoint = s.ApiEndpoints[i]
			}
			if client == nil || endpoint == "" {
				continue
			}
			wg.Add(1)
			go func(c JSONSender, ep string, idx int) {
				defer wg.Done()
				if err := c.SendJSON(ctx, ep, jsonData); err != nil {
					s.Logger.Error("failed to send data to MCMP API", "hostname", s.Hostname, "endpoint", ep, "error", err)
					errCh <- fmt.Errorf("failed to send data to MCMP[%d] %s: %w", idx, ep, err)
					return
				}
				s.Logger.Info("Data sent to MCMP API", "endpoint", ep, "hostname", s.Hostname)
			}(client, endpoint, i)
		}
		wg.Wait()
		close(errCh)
		var allErrs error
		for err := range errCh {
			allErrs = errors.Join(allErrs, err)
		}
		if allErrs != nil {
			return allErrs
		}
	} else if s.McmpClient != nil && s.ApiEndpoint != "" {
		if err := s.McmpClient.SendJSON(ctx, s.ApiEndpoint, jsonData); err != nil {
			s.Logger.Error("failed to send data to MCMP API", "hostname", s.Hostname, "error", err)
			return fmt.Errorf("failed to send data to MCMP: %w", err)
		}
		s.Logger.Info("Data sent to MCMP API", "endpoint", s.ApiEndpoint, "hostname", s.Hostname)
	}

	s.Logger.Info("Data exported to file", "filename", s.ExportFilename, "hostname", s.Hostname)
	return nil
}
