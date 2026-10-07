package repo

import (
	"context"
	"errors"
	"net/http"
	"strings"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

var ErrRepoUrlRequired = errors.New("repo url is required")

// Lock states of a repository. They are read from the status file (Config.StatusPath) below the repo url.
const (
	StatusLocked   = "LOCKED"    // repository may not be changed or attached at all
	StatusSelfOnly = "SELF_ONLY" // repository may only be attached by the owners of the repository
	StatusOpen     = "OPEN"      // no restrictions

	// DefaultStatusPath is used when Config.StatusPath is empty. The status file contains the keys LOCKED and
	// SELF_ONLY, each with a list of repository names. Repositories not listed are OPEN.
	DefaultStatusPath = ".repostatus/locked.json"
)

type (
	HTTPClient interface {
		Get(ctx context.Context, url string) ([]byte, int, error)
		Do(req *http.Request) (*http.Response, error)
	}

	Config struct {
		RepoUrl   string `mapstructure:"RepoUrl"`
		Username  string `mapstructure:"Username"`
		Password  string `mapstructure:"Password"`
		Enabled   bool   `mapstructure:"Enabled"`
		VerifyTLS bool   `mapstructure:"VerifyTLS"`
		// StatusPath is the path of the lock status file relative to RepoUrl (default: DefaultStatusPath).
		StatusPath string `mapstructure:"StatusPath"`
	}

	Client struct {
		client  HTTPClient
		logger  logging.Logger
		config  Config
		baseURL string
	}

	RepositoryInfo struct {
		Name   string `json:"name"`
		URL    string `json:"url"`
		Status string `json:"status,omitempty"`
	}

	// RepoStatus is the content of the status file.
	RepoStatus struct {
		Locked   []string `json:"LOCKED"`
		SelfOnly []string `json:"SELF_ONLY"`
	}
)

// StatusFor returns the lock state of the repository. LOCKED takes precedence over SELF_ONLY.
func (s *RepoStatus) StatusFor(name string) string {
	if s == nil {
		return StatusOpen
	}
	for _, locked := range s.Locked {
		if strings.TrimSpace(locked) == name {
			return StatusLocked
		}
	}
	for _, selfOnly := range s.SelfOnly {
		if strings.TrimSpace(selfOnly) == name {
			return StatusSelfOnly
		}
	}
	return StatusOpen
}

// statusPath returns the configured status file path relative to the repo url, or DefaultStatusPath.
func (c *Config) statusPath() string {
	path := strings.TrimLeft(strings.TrimSpace(c.StatusPath), "/")
	if path == "" {
		return DefaultStatusPath
	}
	return path
}

func (c *Config) Validate() error {
	if !c.Enabled {
		return nil // Disabled configurations don't need validation
	}
	if c.RepoUrl == "" {
		return ErrRepoUrlRequired
	}
	return nil
}
