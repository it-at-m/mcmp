package processor

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"golang.org/x/sync/errgroup"
)

var (
	ErrNilProvider      = errors.New("server provider must not be nil")
	ErrNoDialect        = errors.New("dialect is required")
	ErrAllServersFailed = errors.New("all servers failed processing")
)

type (
	Server struct {
		FQDN string
		PDB  string // Oracle
	}

	ServerProvider interface {
		GetServers(ctx context.Context) ([]Server, error)
	}

	ServerProviderFunc func(ctx context.Context) ([]Server, error)

	Dialect interface {
		DriverName() string
		DSN(server Server) string
		Queries() []NamedQuery
	}

	NamedQuery struct {
		Name string
		SQL  string
	}

	Config struct {
		WorkerCount int
	}

	Processor struct {
		serverProvider ServerProvider
		dialect        Dialect
		logger         logging.Logger
		config         Config
	}

	serverResult struct {
		metrics DatabaseMetrics
		err     error
	}

	Export struct {
		app.EaiMetadata `json:"metadata"`
		Databases       []DatabaseMetrics `json:"databases"`
	}

	DatabaseMetrics struct {
		FQDN      string        `json:"fqdn"`
		PDB       string        `json:"pdb,omitempty"`
		Type      string        `json:"type,omitempty"`
		StartTime time.Time     `json:"startTime"`
		EndTime   time.Time     `json:"endTime"`
		Duration  time.Duration `json:"duration"`
		Data      []QueryResult `json:"data"`
	}

	QueryResult struct {
		QueryName string           `json:"queryName"`
		Duration  time.Duration    `json:"duration"`
		Rows      []map[string]any `json:"rows"`
	}
)

func (f ServerProviderFunc) GetServers(ctx context.Context) ([]Server, error) { return f(ctx) }

func (e *Export) SetEaiMetadata(meta app.EaiMetadata) { e.EaiMetadata = meta }
func (e *Export) GetEaiMetadata() app.EaiMetadata     { return e.EaiMetadata }

func NewProcessor(provider ServerProvider, dialect Dialect, logger logging.Logger, config Config) (*Processor, error) {
	if provider == nil {
		return nil, ErrNilProvider
	}
	if dialect == nil {
		return nil, ErrNoDialect
	}
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}
	if config.WorkerCount <= 0 {
		config.WorkerCount = 5
	}
	return &Processor{serverProvider: provider, dialect: dialect, logger: logger, config: config}, nil
}

func (p *Processor) FetchDatabaseMetrics(ctx context.Context) (*Export, error) {
	servers, err := p.serverProvider.GetServers(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get servers: %w", err)
	}

	jobs := make(chan Server, len(servers))
	results := make(chan serverResult, len(servers))
	var wg sync.WaitGroup

	for i := 0; i < min(p.config.WorkerCount, len(servers)); i++ {
		wg.Add(1)
		go p.worker(ctx, jobs, results, &wg)
	}
	for _, s := range servers {
		jobs <- s
	}
	close(jobs)

	go func() { wg.Wait(); close(results) }()

	export := &Export{Databases: make([]DatabaseMetrics, 0)}
	processed, failed := 0, 0
	for res := range results {
		if res.err != nil {
			cleanErr := strings.ReplaceAll(res.err.Error(), "\n", " ")
			cleanErr = strings.ReplaceAll(cleanErr, "\r", " ")
			cleanErr = strings.Join(strings.Fields(cleanErr), " ")
			p.logger.Error("Error processing database", "error", cleanErr)
			failed++
			continue
		}
		if res.metrics.FQDN != "" {
			export.Databases = append(export.Databases, res.metrics)
		}
		processed++
	}

	if failed > 0 && processed == 0 {
		return nil, fmt.Errorf("%w: %d databases", ErrAllServersFailed, failed)
	}
	if failed > 0 {
		export.Status = "ERROR"
	}

	p.printStatistics(export.Databases)

	return export, nil
}

func (p *Processor) worker(ctx context.Context, jobs <-chan Server, results chan<- serverResult, wg *sync.WaitGroup) {
	defer wg.Done()
	for server := range jobs {
		if ctx.Err() != nil {
			results <- serverResult{err: ctx.Err()}
			continue
		}
		m, err := p.processDatabase(ctx, server)
		results <- serverResult{metrics: m, err: err}
	}
}

func (p *Processor) processDatabase(ctx context.Context, server Server) (DatabaseMetrics, error) {
	db, err := sql.Open(p.dialect.DriverName(), p.dialect.DSN(server))
	if err != nil {
		return DatabaseMetrics{}, fmt.Errorf("failed to open connection to %s: %w", server.FQDN, err)
	}
	defer db.Close()

	startTime := time.Now()
	metrics := DatabaseMetrics{
		FQDN:      server.FQDN,
		PDB:       server.PDB,
		StartTime: startTime,
		Data:      make([]QueryResult, len(p.dialect.Queries())),
	}

	var g errgroup.Group
	g.SetLimit(5)

	queries := p.dialect.Queries()
	for i := range queries {
		q := queries[i]

		g.Go(func() error {
			rows, err := queryRows(ctx, db, q.SQL)
			if err != nil {
				cleanErr := strings.ReplaceAll(err.Error(), "\n", " ")
				cleanErr = strings.ReplaceAll(cleanErr, "\r", " ")
				return fmt.Errorf("query %s failed for %s: %s", q.Name, server.FQDN, cleanErr)
			}
			metrics.Data[i] = QueryResult{QueryName: q.Name, Rows: rows}
			return nil
		})
	}

	if err := g.Wait(); err != nil {
		return DatabaseMetrics{}, err
	}
	metrics.EndTime = time.Now()
	metrics.Duration = metrics.EndTime.Sub(startTime)
	return metrics, nil
}

func queryRows(ctx context.Context, db *sql.DB, query string) ([]map[string]any, error) {
	rows, err := db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	cols, _ := rows.Columns()
	var result []map[string]any

	for rows.Next() {
		values := make([]any, len(cols))
		ptrs := make([]any, len(cols))
		for i := range values {
			ptrs[i] = &values[i]
		}
		if err := rows.Scan(ptrs...); err != nil {
			continue
		}

		m := make(map[string]any, len(cols))
		for i, name := range cols {
			key := strings.ToLower(name)
			val := values[i]
			if val == nil {
				m[key] = nil
				continue
			}
			var s string
			switch v := val.(type) {
			case []byte:
				s = string(v)
			default:
				s = fmt.Sprintf("%v", v)
			}
			if n, err := strconv.ParseInt(s, 10, 64); err == nil {
				m[key] = n
			} else if f, err := strconv.ParseFloat(s, 64); err == nil {
				m[key] = f
			} else {
				m[key] = s
			}
		}
		result = append(result, m)
	}
	return result, rows.Err()
}

func (p *Processor) printStatistics(databases []DatabaseMetrics) {
	if len(databases) == 0 {
		p.logger.Info("=== DATABASE STATISTICS ===")
		p.logger.Info("No databases processed")
		return
	}

	var minDuration time.Duration
	var maxDuration time.Duration
	var fastestServer string
	var slowestServers []struct {
		fqdn     string
		duration time.Duration
	}

	minDuration = databases[0].Duration
	maxDuration = databases[0].Duration
	fastestServer = databases[0].FQDN
	if databases[0].PDB != "" {
		fastestServer = databases[0].FQDN + " (PDB: " + databases[0].PDB + ")"
	}

	allServers := make([]struct {
		fqdn     string
		duration time.Duration
	}, 0, len(databases))

	for _, db := range databases {
		serverName := db.FQDN
		if db.PDB != "" {
			serverName = db.FQDN + " (PDB: " + db.PDB + ")"
		}

		allServers = append(allServers, struct {
			fqdn     string
			duration time.Duration
		}{serverName, db.Duration})

		if db.Duration < minDuration {
			minDuration = db.Duration
			fastestServer = serverName
		}
		if db.Duration > maxDuration {
			maxDuration = db.Duration
		}
	}

	sort.Slice(allServers, func(i, j int) bool {
		return allServers[i].duration > allServers[j].duration
	})

	slowestCount := min(len(allServers), 10)
	slowestServers = allServers[:slowestCount]

	p.logger.Info("=== DATABASE QUERY STATISTICS ===")
	p.logger.Info("Total databases processed", "count", len(databases))
	p.logger.Info("Fastest query time", "duration", minDuration)
	p.logger.Info("Slowest query time", "duration", maxDuration)
	p.logger.Info("Fastest server", "server", fastestServer, "duration", minDuration)

	p.logger.Info("Top 10 slowest servers:")
	for i, server := range slowestServers {
		p.logger.Info("", "rank", i+1, "server", server.fqdn, "duration", server.duration)
	}
	p.logger.Info("=== END STATISTICS ===")
}
