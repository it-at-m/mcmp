package loadbalancer

// Config holds the JSON response from the BIG-IP configuration API.
type Config struct {
	VirtualServers map[string]VirtualServerConfig `json:"Virtual Server"`
	Pools          map[string]PoolConfig          `json:"Pool"`
}

// VirtualServerConfig represents a single virtual server definition.
type VirtualServerConfig struct {
	Addresses   []string           `json:"addresses"`
	Listen      string             `json:"listen"`
	Forward     string             `json:"forward"`
	Pool        map[string]PoolRef `json:"pool"`
	Port        int                `json:"port"`
	Waf         WafConfig          `json:"waf"`
	Persistence string             `json:"persistence"`
	IRules      map[string]string  `json:"irules"`
	Redirect80  bool               `json:"redirect80"`
}

// PoolRef represents a pool reference within a virtual server's pool map.
type PoolRef struct {
	Default bool     `json:"default"`
	Hosts   []string `json:"hosts,omitempty"`
	Paths   []string `json:"paths,omitempty"`
}

// WafConfig holds Web Application Firewall settings for a virtual server.
type WafConfig struct {
	Enabled bool   `json:"enabled"`
	Status  string `json:"status"`
}

// PoolConfig represents a pool configuration entry.
type PoolConfig struct {
	PoolMembers      []PoolMember `json:"pool_member"`
	LbMethod         string       `json:"lb_method"`
	MonitorCondition interface{}  `json:"monitor_condition"`
	Monitors         []Monitor    `json:"monitors"`
}

// PoolMember is a single backend member within a pool.
type PoolMember struct {
	IP               string      `json:"ip"`
	Port             int         `json:"port"`
	MonitorCondition interface{} `json:"monitor_condition"`
	Monitors         []Monitor   `json:"monitors"`
}

// Monitor describes a health-check monitor attached to a pool or pool member.
type Monitor struct {
	Type     string      `json:"type"`
	Interval int         `json:"interval,omitempty"`
	Port     interface{} `json:"port,omitempty"`
	Method   string      `json:"method,omitempty"`
	Path     string      `json:"path,omitempty"`
	Host     string      `json:"host,omitempty"`
	Version  string      `json:"version,omitempty"`
	Expect   *string     `json:"expect,omitempty"`
}
