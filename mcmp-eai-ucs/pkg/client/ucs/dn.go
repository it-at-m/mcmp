package ucs

import (
	"strings"
)

func GetServerDN(dn string) (string, bool) {
	if serverDN, contains := GetRackUnitDN(dn); contains {
		return serverDN, contains
	}
	if serverDN, contains := GetBladeDN(dn); contains {
		return serverDN, contains
	}
	return "", false
}

func GetRackUnitDN(dn string) (string, bool) {
	return getDN(dn, "rack-unit-")
}

func GetBladeDN(dn string) (string, bool) {
	return getDN(dn, "blade-")
}

func GetChassisDN(dn string) (string, bool) {
	return getDN(dn, "chassis-")
}

func GetFiDN(dn string) (string, bool) {
	return getDN(dn, "switch-")
}

func GetFexDN(dn string) (string, bool) {
	return getDN(dn, "fex-")
}

func GetHealthDN(dn string) (string, bool) {
	return getDN(dn, "health")
}

func getDN(dn, dnType string) (string, bool) {
	dnParts := strings.Split(dn, "/")
	if len(dnParts) > 0 && strings.Contains(dn, dnType) {
		var resultDN strings.Builder
		var hasPrefix bool
		for i, dnPart := range dnParts {
			if i > 0 {
				resultDN.WriteString("/")
			}
			resultDN.WriteString(dnPart)
			if strings.HasPrefix(dnPart, dnType) {
				hasPrefix = true
				break
			}
		}
		if hasPrefix {
			return resultDN.String(), true
		}
	}
	return "", false
}
