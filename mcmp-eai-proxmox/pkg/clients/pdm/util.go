package pdm

import (
	"fmt"
	"math"
	"strconv"
)

// UInt32 is an unsigned 32-bit integer, but with laxer JSON
// unmarshalling implementation than a standard uint32.
//
// This is useful because the Proxmox Datacenter Manager API will,
// for some fields, return an integer with a decimal part (e.g. 1.0).
// While this is valid JSON (since all numbers are implicitly floating
// point regardless of the chosen decimal expansion), Go's
// encoding/json rejects such literals as valid values for integer
// variables or fields.
//
// This type accepts any JSON number literal with a zero decimal
// component. It still rejects numbers with a non-zero decimal
// component as they cannot be represented as integers.
//
// Example:
//
//	var parsed struct {
//	  VMID   uint32 `json:"vmid"`
//	  MaxCPU UInt32 `json:"maxcpu"`
//	}
//
//	data := []byte("{ vmid: 1, maxcpu: 8.0 }")
//
//	if err := json.Unmarshal(data, &parsed); err != nil {
//	  panic(err)
//	}
//
//	// parsed = { VMID: 1, MaxCPU: 8 }
type UInt32 uint32

func (v *UInt32) UnmarshalJSON(b []byte) error {
	f, err := strconv.ParseFloat(string(b), 64)
	if err != nil || math.Mod(f, 1) != 0 {
		return fmt.Errorf("cannot parse %s as uint32", string(b))
	}
	*v = UInt32(f)
	return nil
}
