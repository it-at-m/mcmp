package repo

import (
	"context"
	"errors"
	"net/http"
	"testing"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

func TestParseListing(t *testing.T) {
	htmlResponse := `
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
<html>
 <head>
  <title>Index of /</title>
 </head>
 <body>
<a href="/"><img src="/repo.png" alt="Repo" style="margin-left: 70px;"></a>

<pre><img src="/icons/blank.gif" alt="Icon "> <a href="?C=N;O=D">Name</a>                                   <a href="?C=M;O=A">Last modified</a>      <a href="?C=S;O=A">Size</a>  <a href="?C=D;O=A">Description</a><hr><img src="/icons/folder.gif" alt="[DIR]"> <a href="Repo1/">Repo/</a>           2025-10-15 17:38    -   
<img src="/icons/folder.gif" alt="[DIR]"> <a href="repo-prod/">repo-prod/</a>           2025-10-15 17:38    -   
<img src="/icons/folder.gif" alt="[DIR]"> <a href="repo-test/">repo-test/</a>                  2024-04-26 13:51    -   
<hr></pre>
<p>...</a></p>
</body></html>

`

	client := &Client{baseURL: "https://repo.example.com/"}
	repos := client.parseListing([]byte(htmlResponse))

	expectedRepos := []string{"Repo1", "repo-prod", "repo-test"}

	if len(repos) != len(expectedRepos) {
		t.Errorf("expected %d repositories, got %d", len(expectedRepos), len(repos))
	}

	for i, expectedName := range expectedRepos {
		if i >= len(repos) {
			break
		}
		if repos[i].Name != expectedName {
			t.Errorf("expected repo name %s, got %s", expectedName, repos[i].Name)
		}
		expectedURL := client.baseURL + expectedName + "/"
		if repos[i].URL != expectedURL {
			t.Errorf("expected repo URL %s, got %s", expectedURL, repos[i].URL)
		}
	}
}

func TestParseListing_FirstEntryInSameLine(t *testing.T) {
	// Test case where multiple links are on the same line (common with <hr> or header)
	htmlResponse := `<pre><a href="?C=N;O=D">Name</a><hr><a href="repo1/">repo1/</a>
<a href="repo2/">repo2/</a></pre>`

	client := &Client{baseURL: "https://repo.example.com/"}
	repos := client.parseListing([]byte(htmlResponse))

	if len(repos) != 2 {
		t.Fatalf("expected 2 repositories, got %d", len(repos))
	}

	if repos[0].Name != "repo1" {
		t.Errorf("expected first repo to be repo1, got %s", repos[0].Name)
	}
}

func TestParseListing_SkipsHiddenDirectories(t *testing.T) {
	htmlResponse := `<pre><a href="../">Parent</a>
<a href=".repostatus/">.repostatus/</a>
<a href="repo1/">repo1/</a></pre>`

	client := &Client{baseURL: "https://repo.example.com/"}
	repos := client.parseListing([]byte(htmlResponse))

	if len(repos) != 1 || repos[0].Name != "repo1" {
		t.Fatalf("expected only repo1, got %+v", repos)
	}
}

func TestRepoStatus_StatusFor(t *testing.T) {
	status := &RepoStatus{
		Locked:   []string{"locked-repo", "both-repo"},
		SelfOnly: []string{" self-repo ", "both-repo"},
	}

	cases := map[string]string{
		"locked-repo": StatusLocked,
		"self-repo":   StatusSelfOnly,
		"both-repo":   StatusLocked, // LOCKED takes precedence
		"other-repo":  StatusOpen,
	}
	for name, expected := range cases {
		if got := status.StatusFor(name); got != expected {
			t.Errorf("StatusFor(%q) = %s, expected %s", name, got, expected)
		}
	}

	var nilStatus *RepoStatus
	if got := nilStatus.StatusFor("any"); got != StatusOpen {
		t.Errorf("nil status should be OPEN, got %s", got)
	}
}

type fakeHTTPClient struct {
	body       []byte
	statusCode int
	err        error
	requested  string
}

func (f *fakeHTTPClient) Get(_ context.Context, url string) ([]byte, int, error) {
	f.requested = url
	return f.body, f.statusCode, f.err
}

func (f *fakeHTTPClient) Do(_ *http.Request) (*http.Response, error) {
	return nil, errors.New("not implemented")
}

func newTestClient(fake *fakeHTTPClient) *Client {
	return &Client{client: fake, baseURL: "https://repo.example.com/", logger: logging.NewNoOpLogger()}
}

func TestGetRepoStatus(t *testing.T) {
	fake := &fakeHTTPClient{
		statusCode: http.StatusOK,
		body:       []byte(`{"LOCKED":["repo-a"],"SELF_ONLY":["repo-b"]}`),
	}

	status, err := newTestClient(fake).GetRepoStatus(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if fake.requested != "https://repo.example.com/.repostatus/locked.json" {
		t.Errorf("unexpected url %s", fake.requested)
	}
	if status.StatusFor("repo-a") != StatusLocked || status.StatusFor("repo-b") != StatusSelfOnly {
		t.Errorf("unexpected status %+v", status)
	}
}

func TestGetRepoStatus_CustomStatusPath(t *testing.T) {
	fake := &fakeHTTPClient{statusCode: http.StatusOK, body: []byte(`{}`)}
	client := newTestClient(fake)
	client.config.StatusPath = "/status/repos.json"

	if _, err := client.GetRepoStatus(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if fake.requested != "https://repo.example.com/status/repos.json" {
		t.Errorf("unexpected url %s", fake.requested)
	}
}

func TestGetRepoStatus_NotFound_AllOpen(t *testing.T) {
	status, err := newTestClient(&fakeHTTPClient{statusCode: http.StatusNotFound}).GetRepoStatus(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if status.StatusFor("repo-a") != StatusOpen {
		t.Errorf("expected OPEN when the status file is missing")
	}
}

func TestGetRepoStatus_Errors(t *testing.T) {
	cases := map[string]*fakeHTTPClient{
		"server error":   {statusCode: http.StatusInternalServerError},
		"request failed": {err: errors.New("connection refused")},
		"invalid json":   {statusCode: http.StatusOK, body: []byte("not json")},
	}
	for name, fake := range cases {
		if _, err := newTestClient(fake).GetRepoStatus(context.Background()); err == nil {
			t.Errorf("%s: expected an error", name)
		}
	}
}
