package repo

import (
	"context"
	"errors"
	"net/http"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

var ErrRepoUrlRequired = errors.New("repo url is required")

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
	}

	Client struct {
		client  HTTPClient
		logger  logging.Logger
		config  Config
		baseURL string
	}

	RepositoryInfo struct {
		Name string `json:"name"`
		URL  string `json:"url"`
	}
)

func (c *Config) Validate() error {
	if !c.Enabled {
		return nil // Disabled configurations don't need validation
	}
	if c.RepoUrl == "" {
		return ErrRepoUrlRequired
	}
	return nil
}
