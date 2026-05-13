package oauth2client

import (
	"fmt"
	"time"
)

// Config contains settings for an OAuth2-enabled API client.
// This struct is designed to be embedded in application configuration files (TOML)
// and provides all necessary OAuth2 and API endpoint settings.
// Resource Owner Password Credentials (ROPC) Flow : OAuthUsername + OAuthPassword + OAuthGrantType = "password"
// Client Credentials Flow : OAuthClientId + OAuthClientSecret + OAuthGrantType = "" or "client_credentials"
type Config struct {
	OAuthUrl                     string // OAuth2 authentication server base URL
	OAuthRealm                   string // OAuth2 realm name where the client is configured
	OAuthClientId                string // OAuth2 client identifier
	OAuthClientSecret            string // OAuth2 client secret for authentication
	OAuthUsername                string // Username for password grant flow
	OAuthPassword                string // Password for password grant flow
	OAuthGrantType               string // Grant type: "client_credentials" or "password"
	OAuthScope                   string // OAuth2 scope (optional, for password grant or client credentials)
	OAuthTokenUrl                string
	ApiEndpoint                  string // Optional API endpoint URL for requests (can be empty if not needed)
	RequestTimeoutSeconds        int    // HTTP request timeout in seconds (default: 30)
	ConnectTimeoutSeconds        int    // Connection establishment timeout in seconds (default: 60)
	ReadTimeoutSeconds           int    // Response header read timeout in seconds (default: 60)
	MaxRetries                   int    // Maximum number of retries (default: 3)
	RetryDelaySeconds            int    // Delay between retries in seconds (default: 2)
	MaxIdleConns                 int    // Maximum idle connections (default: 100)
	IdleConnTimeoutSeconds       int    // Idle connection timeout in seconds (default: 90)
	TLSHandshakeTimeoutSeconds   int    // TLS handshake timeout in seconds (default: 10)
	ExpectContinueTimeoutSeconds int    // Expect continue timeout in seconds (default: 1)
	MaxIdleConnsPerHost          int    // Maximum idle connections per host (default: 10)
	MaxConnsPerHost              int    // Maximum connections per host (default: 0, unlimited)
}

// Validate checks if all required configuration fields are set.
// Returns an error if any required field is missing or invalid.
func (c *Config) Validate() error {
	if c.OAuthGrantType == "" {
		c.OAuthGrantType = "client_credentials"
	}
	if c.OAuthGrantType != "client_credentials" && c.OAuthGrantType != "password" {
		return fmt.Errorf("OAuth grant type must be 'client_credentials' or 'password'")
	}
	if c.OAuthUrl == "" {
		return fmt.Errorf("OAuth2 URL is required")
	}
//	if c.OAuthTokenUrl == "" {
//		return fmt.Errorf("OAuth2 token URL is required")
//	}
	if c.OAuthGrantType == "client_credentials" {
		if c.OAuthRealm == "" {
			return fmt.Errorf("OAuth2 realm is required for client credentials")
		}
		if c.OAuthClientId == "" {
			return fmt.Errorf("OAuth2 client ID is required")
		}
		if c.OAuthClientSecret == "" {
			return fmt.Errorf("OAuth2 client secret is required")
		}
	} else if c.OAuthGrantType == "password" {
		if c.OAuthUsername == "" {
			return fmt.Errorf("OAuth2 username is required for password grant")
		}
		if c.OAuthPassword == "" {
			return fmt.Errorf("OAuth2 password is required for password grant")
		}
	}
	if c.RequestTimeoutSeconds < 0 {
		return fmt.Errorf("request timeout seconds cannot be negative")
	}
	if c.ConnectTimeoutSeconds < 0 {
		return fmt.Errorf("connect timeout seconds cannot be negative")
	}
	if c.ReadTimeoutSeconds < 0 {
		return fmt.Errorf("read timeout seconds cannot be negative")
	}
	if c.MaxRetries < 0 {
		return fmt.Errorf("max retries cannot be negative")
	}
	if c.RetryDelaySeconds < 0 {
		return fmt.Errorf("retry delay seconds cannot be negative")
	}
	if c.MaxIdleConns < 0 {
		return fmt.Errorf("max idle connections cannot be negative")
	}
	if c.IdleConnTimeoutSeconds < 0 {
		return fmt.Errorf("idle connection timeout seconds cannot be negative")
	}
	if c.TLSHandshakeTimeoutSeconds < 0 {
		return fmt.Errorf("TLS handshake timeout seconds cannot be negative")
	}
	if c.ExpectContinueTimeoutSeconds < 0 {
		return fmt.Errorf("expect continue timeout seconds cannot be negative")
	}
	if c.MaxIdleConnsPerHost < 0 {
		return fmt.Errorf("max idle connections per host cannot be negative")
	}
	if c.MaxConnsPerHost < 0 {
		return fmt.Errorf("max connections per host cannot be negative")
	}

	return nil
}

// ToClientConfig converts the Config to a ClientConfig for creating a new OAuth2 client.
// This method maps the TOML-friendly field names to the internal client configuration.
// It applies defaults for optional fields if not set in the TOML.
func (c *Config) ToClientConfig() ClientConfig {
	clientConfig := ClientConfig{
		AuthServerURL:   c.OAuthUrl,
		Realm:           c.OAuthRealm,
		ClientID:        c.OAuthClientId,
		ClientSecret:    c.OAuthClientSecret,
		Username:        c.OAuthUsername,
		Password:        c.OAuthPassword,
		GrantType:       c.OAuthGrantType,
		TokenURL:        c.OAuthTokenUrl,
		ApiEndpoint:     c.ApiEndpoint,
		EnableTLSVerify: false, // Default: verify TLS
	}

	// Set Scopes if OAuthScope is provided
	if c.OAuthScope != "" {
		clientConfig.Scopes = []string{c.OAuthScope}
	}

	// Map and convert timeout fields with defaults
	if c.RequestTimeoutSeconds > 0 {
		clientConfig.RequestTimeout = time.Duration(c.RequestTimeoutSeconds) * time.Second
	} else {
		clientConfig.RequestTimeout = 30 * time.Second // Default
	}

	if c.ConnectTimeoutSeconds > 0 {
		clientConfig.ConnectTimeout = time.Duration(c.ConnectTimeoutSeconds) * time.Second
	} else {
		clientConfig.ConnectTimeout = 60 * time.Second // Default
	}

	if c.ReadTimeoutSeconds > 0 {
		clientConfig.ReadTimeout = time.Duration(c.ReadTimeoutSeconds) * time.Second
	} else {
		clientConfig.ReadTimeout = 60 * time.Second // Default
	}

	// Map retry fields with defaults
	if c.MaxRetries > 0 {
		clientConfig.MaxRetries = c.MaxRetries
	} else {
		clientConfig.MaxRetries = 3 // Default
	}

	if c.RetryDelaySeconds > 0 {
		clientConfig.RetryDelay = time.Duration(c.RetryDelaySeconds) * time.Second
	} else {
		clientConfig.RetryDelay = 2 * time.Second // Default
	}

	if c.MaxIdleConns > 0 {
		clientConfig.MaxIdleConns = c.MaxIdleConns
	} else {
		clientConfig.MaxIdleConns = 100 // Default
	}

	if c.IdleConnTimeoutSeconds > 0 {
		clientConfig.IdleConnTimeout = time.Duration(c.IdleConnTimeoutSeconds) * time.Second
	} else {
		clientConfig.IdleConnTimeout = 90 * time.Second // Default
	}

	if c.TLSHandshakeTimeoutSeconds > 0 {
		clientConfig.TLSHandshakeTimeout = time.Duration(c.TLSHandshakeTimeoutSeconds) * time.Second
	} else {
		clientConfig.TLSHandshakeTimeout = 10 * time.Second // Default
	}

	if c.ExpectContinueTimeoutSeconds > 0 {
		clientConfig.ExpectContinueTimeout = time.Duration(c.ExpectContinueTimeoutSeconds) * time.Second
	} else {
		clientConfig.ExpectContinueTimeout = 1 * time.Second // Default
	}

	if c.MaxIdleConnsPerHost > 0 {
		clientConfig.MaxIdleConnsPerHost = c.MaxIdleConnsPerHost
	} else {
		clientConfig.MaxIdleConnsPerHost = 10 // Default
	}

	if c.MaxConnsPerHost >= 0 { // Allow 0 to disable limit
		clientConfig.MaxConnsPerHost = c.MaxConnsPerHost
	} else {
		clientConfig.MaxConnsPerHost = 0 // Default: unlimited
	}

	return clientConfig
}
