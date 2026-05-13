package snow

import (
	"encoding/json"
	"fmt"
)

// ChangeCloseCode represents the possible closure codes for changes
type ChangeCloseCode int

const (
	// Successful - Change was completed successfully
	Successful ChangeCloseCode = iota
	// SuccessfulIssues - Change was completed, but with issues
	SuccessfulIssues
	// Unsuccessful - Change was not successful
	Unsuccessful
)

// String returns the string representation of the ChangeCloseCode
func (c ChangeCloseCode) String() string {
	switch c {
	case Successful:
		return "successful"
	case SuccessfulIssues:
		return "successful_issues"
	case Unsuccessful:
		return "unsuccessful"
	default:
		return fmt.Sprintf("ChangeCloseCode(%d)", int(c))
	}
}

// MarshalJSON serializes the ChangeCloseCode as its string representation in JSON format.
func (c ChangeCloseCode) MarshalJSON() ([]byte, error) {
	return json.Marshal(c.String())
}

// UnmarshalJSON deserializes a ChangeCloseCode from its string representation in JSON format.
func (c *ChangeCloseCode) UnmarshalJSON(data []byte) error {
	var s string
	if err := json.Unmarshal(data, &s); err != nil {
		return err
	}

	switch s {
	case "successful":
		*c = Successful
	case "successful_issues":
		*c = SuccessfulIssues
	case "unsuccessful":
		*c = Unsuccessful
	default:
		return fmt.Errorf("invalid ChangeCloseCode: %s", s)
	}

	return nil
}

// IsValid checks if the ChangeCloseCode is valid
func (c ChangeCloseCode) IsValid() bool {
	return c >= Successful && c <= Unsuccessful
}

// AllCloseCodes returns all available ChangeCloseCode values
func AllCloseCodes() []ChangeCloseCode {
	return []ChangeCloseCode{Successful, SuccessfulIssues, Unsuccessful}
}
