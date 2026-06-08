package pdm

import "net/http"

// authorizedTransport is an http.RoundTripper that sets the Authorization header.
type authorizedTransport struct {
	tp   http.RoundTripper
	auth string
}

// RoundTrip executes a single HTTP transaction (see http.RoundTripper).
func (t *authorizedTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	req.Header.Add("Authorization", t.auth)
	return t.tp.RoundTrip(req)
}
