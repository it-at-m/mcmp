package mcmp

import (
	"net/http"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

type (
	// HTTPClientInterface defines the interface for HTTP client operations
	// This interface enables dependency injection and testing with mock implementations
	HTTPClientInterface interface {
		Do(req *http.Request) (*http.Response, error)
	}

	// Client represents an MCMP API client with HTTP communication capabilities and OAuth2 support
	// It encapsulates the HTTP client configuration, OAuth2 authentication, and debug logging functionality
	Client struct {
		*logging.DebugLogger                     // Embedded debug logger for request/response monitoring
		httpClient           HTTPClientInterface // HTTP client implementation for API calls (OAuth2-enabled)
		debug                bool                // Debug flag to enable verbose logging
		config               ClientConfig        // Store configuration for reference
	}
)
