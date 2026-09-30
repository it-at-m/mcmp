package proxmox

import (
	"fmt"
	"math"
	"strconv"
	"strings"
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

// ParseCommaSeparatedMap parses a string consisting of key=value pairs,
// separated by commas, and one optional key-less value, into a proper
// Go map.
//
// Examples:
//
//	ParseCommaSeparatedMap("lvm:vm-100-disk-0.qcow2,size=10G")
//	  // -> "lvm:vm-100-disk-0.qcow2", { "size": "10G" }
//
//	ParseCommaSeparatedMap("virtio=00:00:00:00:00:00")
//	  // -> "", { "virtio": "00:00:00:00:00:00" }
//
//	ParseCommaSeparatedMap("1")
//	  // -> "1", { }
func ParseCommaSeparatedMap(s string) (value string, values map[string]string) {
	elements := strings.Split(s, ",")
	values = make(map[string]string, len(elements)-1)
	for _, element := range elements {
		k, v, ok := strings.Cut(element, "=")
		if !ok {
			value = k
			continue
		}
		values[k] = v
	}
	return
}

// Get is a type parametrized map accessor.  If the given map contains
// a value of the given type at the given index, the value will be
// returned.  If the index is missing or the value has a different
// type, a zero value will be returned instead.
//
// Example:
//
//	Get[string](map[string]any{"hello": "world"}, "hello") // -> "world", true
//	Get[string](map[string]any{}, "hello")                 // -> "", false
//	Get[string](map[string]any{"hello": 1}, "hello")       // -> "", false
func Get[T any](values map[string]any, key string) (T, bool) {
	v, ok := values[key]
	if !ok {
		return *new(T), false
	}
	vp, ok := v.(T)
	if !ok {
		return *new(T), false
	}
	return vp, true
}

// GetUnwrap is like Get, but doesn't return the OK boolean.
func GetUnwrap[T any](values map[string]any, key string) T {
	v, _ := Get[T](values, key)
	return v
}
