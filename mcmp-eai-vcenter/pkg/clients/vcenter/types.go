package vcenter

import (
	"context"
	"net/url"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/vmware/govmomi/session/cache"
	"github.com/vmware/govmomi/vim25"
)

type (
	MetricName string // MetricName definiert einen benutzerdefinierten Typ zur Verwendung von Counter als Enum

	Client struct {
		*logging.DebugLogger // embedding
		client               *vim25.Client
		context              context.Context
		url                  *url.URL
		session              *cache.Session
		fqdn                 string
	}

	VCenterHostStatus struct {
		SN                string
		Hostname          string
		PowerState        string
		InMaintenanceMode bool
		ConnectionState   string
		OverallStatus     string
		OS                string
		BootTime          *time.Time
	}
	VmPerfData struct {
		Timestamp      time.Time
		VMName         string
		UUID           string
		NumCores       int32
		MemoryMB       int32
		MaxCpuUsage    int32
		MaxMemoryUsage int32
		MemoryOverhead int64
		PowerState     string
		Metrics        map[MetricName]IntervalMetrics
	}

	IntervalMetrics struct {
		Min    float64 // Min represents the minimum value in the IntervalMetrics dataset.
		Q1     float64 // LowerQuartile represents the 25th percentile value of a dataset in IntervalMetrics.
		Median float64 // Median represents the 50th percentile value of a dataset in IntervalMetrics.
		Q3     float64 // UpperQuartile represents the 75th percentile value of a dataset in IntervalMetrics.
		Max    float64 // Max represents the maximum value in the IntervalMetrics dataset.
		Avg    float64 // Avg represents the average value in the IntervalMetrics dataset.
	}

	TagInfo struct {
		Name         string
		CategoryName string
		Description  string
	}
)
