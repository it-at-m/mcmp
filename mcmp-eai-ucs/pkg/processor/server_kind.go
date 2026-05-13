package processor

type ServerKind string

const (
	ServerKindUnknown  ServerKind = "UNKNOWN"
	ServerKindHardware ServerKind = "HARDWARE"
	ServerKindVirtual  ServerKind = "VIRTUAL"
)
