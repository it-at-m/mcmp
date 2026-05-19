// SPDX-FileCopyrightText: 2023 Landeshauptstadt München | it@M
//
// SPDX-License-Identifier: MIT

package baas

import (
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"
)

const (
	url        = "https://%s/cmp/?view=backup&server="
	timeLayout = "2006-01-02 15:04:05"
)

var berlinLocation *time.Location

type (
	HttpClient interface {
		Get(url string) (resp *http.Response, err error)
	}

	Client struct {
		httpClient HttpClient
		url        string
		hostname   string
		debug      bool
	}

	JsonBackupResponse struct {
		Status  int    `json:"status"`
		Message string `json:"message"`
		Backups struct {
			Count   int `json:"count"`
			Catalog struct {
				VM    BackupItemList `json:"vm"`
				Agent BackupItemList `json:"agent"`
				DA    BackupItemList `json:"da"`
				DB    BackupItemList `json:"db"`
				DH    BackupItemList `json:"dh"`
				DM    BackupItemList `json:"dm"`
				DP    BackupItemList `json:"dp"`
				DS    BackupItemList `json:"ds"`
				DY    BackupItemList `json:"dy"`
				NFS   BackupItemList `json:"nfs"`
				CIFS  BackupItemList `json:"cifs"`
			} `json:"catalog"`
		} `json:"backups"`
	}

	SaveTime struct {
		StringValue string
		TimeValue   time.Time
	}

	StringOrIntAsString string

	BackupItem struct {
		BackupServer string   `json:"backupserver"`
		ClientServer string   `json:"clientserver"`
		SaveSetName  string   `json:"savesetname"`
		SaveTime     SaveTime `json:"savetime"`
		Ssretent     SaveTime `json:"ssretent"`
		SSID         string   `json:"ssid"`
		CloneID      string   `json:"cloneid"`
		Pool         string   `json:"pool"`
		Totalsize    int64    `json:"totalsize"`
		Runtime      string   `json:"runtime"`
	}

	BackupItemList []BackupItem
)

func init() {
	loc, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		panic(fmt.Sprintf("failed to load time location Europe/Berlin: %v", err))
	}
	berlinLocation = loc
}

func New(hostname string) *Client {
	c := new(Client)
	c.hostname = hostname
	c.url = fmt.Sprintf(url, hostname)
	c.httpClient = &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyFromEnvironment,
			TLSClientConfig: &tls.Config{
				InsecureSkipVerify: false,
				MinVersion:         tls.VersionTLS13,
			},
		},
	}
	return c
}

func (c *Client) EnableDebug() {
	c.debug = true
}

func (c *Client) DisableDebug() {
	c.debug = false
}

func (c *Client) debugPrintf(format string, a ...interface{}) {
	if c.debug {
		log.Printf(format, a...)
	}
}

func (c *Client) GetHostname() string {
	return c.hostname
}

func (c *Client) FetchBackups(servername string) (*JsonBackupResponse, error) {
	if servername == "" {
		return nil, fmt.Errorf("servername is empty")
	}
	requestUrl := c.url + strings.TrimSpace(servername)
	c.debugPrintf("url: %s", requestUrl)

	resp, err := c.httpClient.Get(requestUrl)
	if err != nil {
		return nil, fmt.Errorf("request error %s", err.Error())
	}
	defer resp.Body.Close()
	c.debugPrintf("StatusCode: %d", resp.StatusCode)
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP Status Code: %d, server: %s", resp.StatusCode, servername)
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read error %s", err.Error())
	}
	c.debugPrintf("Body: %s", string(body))

	// JSON-Parsing
	var jsonBackupResponse JsonBackupResponse
	err = json.Unmarshal(body, &jsonBackupResponse)
	if err != nil {
		return nil, fmt.Errorf("Json unmarshal error: %#v, server: %s\n", err, servername)
	}
	return &jsonBackupResponse, nil
}

func (st *SaveTime) UnmarshalJSON(data []byte) error {
	var rawString string
	if err := json.Unmarshal(data, &rawString); err != nil {
		return err
	}
	st.StringValue = rawString

	parsedTime, err := time.ParseInLocation(timeLayout, rawString, berlinLocation)
	if err != nil {
		return err
	}

	st.TimeValue = parsedTime
	return nil
}

func (b *BackupItem) UnmarshalJSON(data []byte) error {
	// Temporäre Aliasstruktur, die flexible Typen ermöglicht
	type Alias BackupItem
	aux := &struct {
		SSID      any `json:"ssid"`      // Platzhalter für int oder string
		CloneID   any `json:"cloneid"`   // Platzhalter für int oder string
		Totalsize any `json:"totalsize"` // Platzhalter für int oder string/number
		*Alias
	}{
		Alias: (*Alias)(b),
	}

	if err := json.Unmarshal(data, aux); err != nil {
		return err
	}

	// SSID (int oder string) zu string konvertieren
	b.SSID, _ = convertToString(aux.SSID)

	// CloneID (int oder string) zu string konvertieren
	b.CloneID, _ = convertToString(aux.CloneID)

	// Totalsize zu int64 konvertieren
	b.Totalsize, _ = convertToInt64(aux.Totalsize)

	return nil
}

func (l *BackupItemList) UnmarshalJSON(data []byte) error {
	var rawItems []json.RawMessage
	if err := json.Unmarshal(data, &rawItems); err != nil {
		return err
	}

	items := make([]BackupItem, 0, len(rawItems))
	for i, raw := range rawItems {
		var bi BackupItem
		if err := json.Unmarshal(raw, &bi); err != nil {
			log.Printf("BackupItemList: faulty data record ignored (index=%d): %v; payload=%s", i, err, string(raw))
			continue
		}
		items = append(items, bi)
	}

	*l = items
	return nil
}

func convertToString(value any) (string, error) {
	switch v := value.(type) {
	case string:
		return v, nil
	case int:
		return fmt.Sprintf("%d", v), nil
	case float64: // JSON-Zahlen werden in Go als float64 verarbeitet
		return fmt.Sprintf("%.0f", v), nil
	default:
		return "", fmt.Errorf("unsupported type: %T", value)
	}
}

func convertToInt64(value any) (int64, error) {
	switch v := value.(type) {
	case int:
		return int64(v), nil
	case int64:
		return v, nil
	case float64:
		return int64(v), nil
	case string:
		if v == "" {
			return 0, nil
		}
		return strconv.ParseInt(v, 10, 64)
	default:
		return 0, fmt.Errorf("unsupported type for int64 conversion: %T", value)
	}
}
