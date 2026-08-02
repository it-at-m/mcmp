package repo

import (
	"testing"
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
