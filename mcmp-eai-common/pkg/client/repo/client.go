package repo

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/httpclient"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

func NewClient(config Config, logger logging.Logger) (*Client, error) {
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	baseURL := config.RepoUrl
	// Ensure the baseURL ends with a slash for proper path concatenation.
	if !strings.HasSuffix(baseURL, "/") {
		baseURL += "/"
	}

	httpClientConfig := httpclient.Config{
		Username:        config.Username,
		Password:        config.Password,
		EnableTLSVerify: config.VerifyTLS,
	}

	client, err := httpclient.NewClient(httpClientConfig, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize http client: %w", err)
	}

	return &Client{
		config:  config,
		client:  client,
		baseURL: baseURL,
		logger:  logger,
	}, nil
}

// ListRepositories fetches the directory listing page and extracts repository names.
func (c *Client) ListRepositories(ctx context.Context) ([]RepositoryInfo, error) {
	body, statusCode, err := c.client.Get(ctx, c.baseURL)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch repo page: %w", err)
	}

	if statusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status code from repo: %d", statusCode)
	}

	return c.parseListing(body), nil
}

// GetRepoStatus fetches the status file (Config.StatusPath) below the repo url. A missing file (404) means that
// no repository is LOCKED or SELF_ONLY. Any other error is returned, so that callers don't unlock repositories by accident.
func (c *Client) GetRepoStatus(ctx context.Context) (*RepoStatus, error) {
	url := c.baseURL + c.config.statusPath()
	body, statusCode, err := c.client.Get(ctx, url)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch repo status: %w", err)
	}

	if statusCode == http.StatusNotFound {
		c.logger.Warn("Repo status file not found, treating all repositories as OPEN", "url", url)
		return &RepoStatus{}, nil
	}

	if statusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status code from repo status: %d", statusCode)
	}

	var status RepoStatus
	if err := json.Unmarshal(body, &status); err != nil {
		return nil, fmt.Errorf("failed to parse repo status: %w", err)
	}

	return &status, nil
}

// parseListing extracts directory entries from the Apache-style HTML body.
func (c *Client) parseListing(htmlBody []byte) []RepositoryInfo {
	var repos []RepositoryInfo

	// Locate the relevant content within <pre> tags used by Apache Autoindex.
	startPre := bytes.Index(htmlBody, []byte("<pre>"))
	endPre := bytes.LastIndex(htmlBody, []byte("</pre>"))

	if startPre == -1 || endPre == -1 || endPre <= startPre {
		return repos
	}

	preContent := string(htmlBody[startPre:endPre])
	// Remove the <pre> tag itself from the content to process
	preContent = strings.TrimPrefix(preContent, "<pre>")
	lines := strings.Split(preContent, "\n")

	for _, line := range lines {
		// Look for lines containing directory links (pattern: <a href="NAME/">NAME/</a>).
		if !strings.Contains(line, "href=\"") || !strings.Contains(line, "/\"") {
			continue
		}

		// Extract all href attributes from the line to handle cases where multiple links
		// (e.g., Header + First Repo) are on the same line.
		hrefParts := strings.Split(line, "href=\"")
		for i := 1; i < len(hrefParts); i++ {
			// Find the closing quote of the href attribute
			endQuoteIdx := strings.Index(hrefParts[i], "\"")
			if endQuoteIdx == -1 {
				continue
			}
			linkPart := hrefParts[i][:endQuoteIdx]

			// Filter criteria:
			// 1. Must be a directory (ends with /).
			// 2. Must not be a query string used for sorting (starts with ?).
			// 3. Must not be an absolute path or the parent directory link (..).
			// 4. Must not be a hidden directory (e.g. .repostatus/), these are no repositories.
			if strings.HasSuffix(linkPart, "/") && !strings.HasPrefix(linkPart, "?") && !strings.HasPrefix(linkPart, "/") && !strings.HasPrefix(linkPart, ".") {
				name := strings.TrimSuffix(linkPart, "/")
				repos = append(repos, RepositoryInfo{
					Name: name,
					URL:  c.baseURL + linkPart,
				})
			}
		}
	}

	return repos
}
