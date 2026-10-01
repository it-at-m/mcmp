package processor

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	_ "github.com/sijms/go-ora/v2"
)

const (
	sqlSizeDB = `
select sum(bytes) db_size_b from (select bytes from dba_data_files union all select bytes from dba_temp_files)
`

	sqlSizeSGA = `
SELECT sum(value) sga_b FROM gv$sga
`

	sqlSizePGA = `
SELECT sum(value) pga_b FROM gv$pgastat WHERE name='maximum PGA allocated'
`

	sqlOptionsDB = `
SELECT comp_name FROM dba_registry WHERE comp_id in ('ORDIM','SDO','CONTEXT','JAVAVM','RAC') AND status!='OPTION OFF' ORDER BY 1
`

	sqlConnectionsMax = `
SELECT sum(value) max_conn FROM gv$parameter WHERE name='processes'
`
	sqlConnectionsActive = `
SELECT inst_id, -- hidden
       sid, -- hidden
       serial#, -- hidden
	   username,
	   machine, -- db client
	   status,
	   last_call_et -- in status since (sec)
FROM gv$session 
WHERE type!='BACKGROUND' AND username!='C##MCMP'
ORDER by 3,4,5,6
`

	sqlInstances = `
SELECT sys_context('USERENV', 'CON_NAME')                              AS pdb_name,
       CASE WHEN i.database_type='RAC' THEN 'RAC' ELSE i.host_name END AS host_name,
       nls.value                                                       AS characterset,
       to_char(i.startup_time, 'YYYY-MM-DD HH24:MI:SS')                AS startup_time,
       i.database_type -- hidden
FROM   v$instance i, v$nls_parameters nls
WHERE  nls.parameter='NLS_CHARACTERSET'
`

	sqlUsers = `
SELECT u.username                                          AS user_name,
       CASE
           WHEN u.profile LIKE '%LHM_APP%'  THEN 'Application'
           WHEN u.profile LIKE '%LHM_USER%' THEN 'End User'
           ELSE 'General'
           END                                                 AS profile,
       u.account_status                                    AS account_status,
       TO_CHAR(u.last_login, 'YYYY-MM-DD HH24:MI:SS')      AS last_login,
       LISTAGG(s.tablespace_name, ', ' ON OVERFLOW TRUNCATE)
               WITHIN GROUP (ORDER BY s.tablespace_name)       AS tablespaces
FROM   dba_users u
           LEFT JOIN (SELECT owner, tablespace_name
                      FROM   dba_segments
                      GROUP  BY owner, tablespace_name) s
                     ON u.username = s.owner
WHERE  u.common = 'NO'
  AND  u.profile LIKE '%LHM_APP%'
GROUP  BY u.username,
          u.profile,
          u.account_status,
          u.last_login
ORDER  BY u.username
`

	sqlTablespaces = `
SELECT r.tablespace_name,
       DECODE(r.contents, 'PERMANENT', 'Persistent Data', 'Temporary Data') AS tablespace_type,
       SUM(r.bytes)                                                         AS data_used_in_b,
       SUM(r.maxbytes)                                                      AS data_max_in_b
FROM   (SELECT t.tablespace_name,
               t.contents,
               SUM(ds.bytes) AS bytes,
               SUM(DECODE(dd.maxbytes, 0, dd.bytes, dd.maxbytes)) AS maxbytes
        FROM   dba_tablespaces t
                   LEFT JOIN dba_data_files dd
                             ON t.tablespace_name = dd.tablespace_name
                   LEFT JOIN dba_segments ds
                             ON t.tablespace_name = ds.tablespace_name
        GROUP  BY t.tablespace_name, t.contents
        UNION
        /* TEMP has no active bytes */
        SELECT t.tablespace_name,
               t.contents,
               0 AS bytes,
               SUM(DECODE(dt.maxbytes, 0, dt.bytes, dt.maxbytes)) AS maxbytes
        FROM   dba_tablespaces t
                   LEFT JOIN dba_temp_files dt
                             ON t.tablespace_name = dt.tablespace_name
        GROUP  BY t.tablespace_name, t.contents) r
GROUP  BY r.tablespace_name,
          r.contents
ORDER  BY r.tablespace_name
`

	sqlBackups = `
select start_time,
       case when backup_type like '%D%' or backup_type like '%I%' then 'FULL' else 'ARCH' end backup_type,
	   tag,
	   keep_until,
	   db_size_mb,
	   bu_size_mb,
	   duration_min
from (
  SELECT min(sd.start_time) start_time,
  	   LISTAGG(sd.backup_type, '' ON OVERFLOW TRUNCATE)
                 WITHIN GROUP (ORDER BY sd.backup_type) backup_type,
         p.tag,
         min(sd.keep_until) keep_until,
         round(sum(sd.original_input_bytes)/1024/1024) DB_SIZE_MB,
         round(sum(sd.output_bytes)/1024/1024) BU_SIZE_MB,
         ceil(sum(sd.elapsed_seconds)/60) duration_min
  FROM V$BACKUP_SET_DETAILS sd
  join v$backup_piece p
    on sd.set_stamp = p.set_stamp and sd.set_count=p.set_count
  where sd.keep_until is not null and p.tag like 'RP_%' 
  group by p.tag)
where start_time>sysdate-4
ORDER BY start_time desc
`
)

var (
	ErrNilClient        = errors.New("MCMP client must not be nil")
	ErrAllServersFailed = errors.New("all servers failed processing")
)

type (
	OracleServerProvider interface {
		GetAllOracleServers(ctx context.Context) ([]mcmp.OracleServer, error)
	}

	// Config contains all configurable parameters for the oracle processor.
	Config struct {
		WorkerCount    int
		OracleUser     string
		OraclePassword string
		OraclePort     int
	}

	NamedQuery struct {
		Name string
		SQL  string
	}

	// Processor is the main processor struct that coordinates oracle database analysis.
	Processor struct {
		serverProvider OracleServerProvider
		logger         logging.Logger
		config         Config
		queries        []NamedQuery
	}

	// serverResult is an internal type used for collecting worker results.
	serverResult struct {
		metrics OracleDatabaseMetrics
		err     error
	}

	OracleExport struct {
		app.EaiMetadata `json:"metadata"`
		Databases       []OracleDatabaseMetrics `json:"databases"`
	}

	OracleDatabaseMetrics struct {
		FQDN      string        `json:"fqdn"`
		PDB       string        `json:"pdb"`
		Timestamp time.Time     `json:"timestamp"`
		Data      []QueryResult `json:"data"`
	}

	QueryResult struct {
		QueryName string           `json:"queryName"`
		Rows      []map[string]any `json:"rows"`
	}
)

func (e *OracleExport) SetEaiMetadata(meta app.EaiMetadata) {
	e.EaiMetadata = meta
}

func (e *OracleExport) GetEaiMetadata() app.EaiMetadata {
	return e.EaiMetadata
}

// NewProcessor creates a new Processor instance with the given configuration.
func NewProcessor(serverProvider OracleServerProvider, logger logging.Logger, config Config) (*Processor, error) {
	if serverProvider == nil {
		return nil, ErrNilClient
	}

	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	// Set defaults
	if config.WorkerCount <= 0 {
		config.WorkerCount = 5
	}
	if config.OraclePort <= 0 {
		config.OraclePort = 1521
	}

	return &Processor{
		serverProvider: serverProvider,
		logger:         logger,
		config:         config,
		queries: []NamedQuery{
			{Name: "size_db", SQL: sqlSizeDB},
			{Name: "size_sga", SQL: sqlSizeSGA},
			{Name: "size_pga", SQL: sqlSizePGA},
			{Name: "options_db", SQL: sqlOptionsDB},
			{Name: "connections_max", SQL: sqlConnectionsMax},
			{Name: "connections_active", SQL: sqlConnectionsActive},
			{Name: "instances", SQL: sqlInstances},
			{Name: "users", SQL: sqlUsers},
			{Name: "tablespaces", SQL: sqlTablespaces},
			{Name: "backups", SQL: sqlBackups},
		},
	}, nil
}

// FetchDatabaseMetrics retrieves metrics from all Oracle servers.
func (p *Processor) FetchDatabaseMetrics(ctx context.Context) (*OracleExport, error) {
	servers, err := p.serverProvider.GetAllOracleServers(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get oracle servers: %w", err)
	}

	filteredServers := make([]mcmp.OracleServer, 0, len(servers))
	for _, s := range servers {
		if strings.HasPrefix(s.FQDN, "bdbtestmcmpdbc004") {
			filteredServers = append(filteredServers, s)
		} else {
			p.logger.DebugPrintf("Skipping server %s (currently not enabled for JDBC connections)", s.FQDN)
		}
	}
	servers = filteredServers

	p.logger.DebugPrintf("Starting database analysis for %d servers with %d workers", len(servers), p.config.WorkerCount)

	jobs := make(chan mcmp.OracleServer, len(servers))
	results := make(chan serverResult, len(servers))
	var wg sync.WaitGroup

	workerCount := min(p.config.WorkerCount, len(servers))
	for i := 0; i < workerCount; i++ {
		wg.Add(1)
		go p.worker(ctx, jobs, results, &wg)
	}

	for _, s := range servers {
		jobs <- s
	}
	close(jobs)

	go func() {
		wg.Wait()
		close(results)
	}()

	export := &OracleExport{
		Databases: make([]OracleDatabaseMetrics, 0),
	}

	processedCount := 0
	errorCount := 0

	for res := range results {
		if res.err != nil {
			p.logger.Error("Error processing database", "error", res.err)
			errorCount++
			continue
		}
		if res.metrics.FQDN != "" {
			export.Databases = append(export.Databases, res.metrics)
		}
		processedCount++
	}

	p.logger.DebugPrintf("Database analysis completed: %d successful, %d errors out of %d total servers", processedCount, errorCount, len(servers))

	if errorCount > 0 && processedCount == 0 {
		return nil, fmt.Errorf("%w: %d databases", ErrAllServersFailed, errorCount)
	}

	if errorCount > 0 {
		export.Status = "ERROR"
	}

	return export, nil
}

func (p *Processor) worker(ctx context.Context, jobs <-chan mcmp.OracleServer, results chan<- serverResult, wg *sync.WaitGroup) {
	defer wg.Done()

	for server := range jobs {
		if ctx.Err() != nil {
			results <- serverResult{err: ctx.Err()}
			continue
		}
		metrics, err := p.processDatabase(ctx, server)
		results <- serverResult{metrics: metrics, err: err}
	}
}

func (p *Processor) processDatabase(ctx context.Context, server mcmp.OracleServer) (OracleDatabaseMetrics, error) {
	// DSN Format for go-ora: oracle://user:pass@host:port/service
	dsn := fmt.Sprintf("oracle://%s:%s@%s:%d/%s", url.PathEscape(p.config.OracleUser), url.PathEscape(p.config.OraclePassword), server.FQDN, p.config.OraclePort, server.PDB)
	db, err := sql.Open("oracle", dsn)
	if err != nil {
		return OracleDatabaseMetrics{}, fmt.Errorf("failed to open connection to %s: %w", server.FQDN, err)
	}
	defer db.Close()

	metrics := OracleDatabaseMetrics{
		FQDN:      server.FQDN,
		PDB:       server.PDB,
		Timestamp: time.Now(),
		Data:      []QueryResult{},
	}

	for _, q := range p.queries {
		rows, err := db.QueryContext(ctx, q.SQL)
		if err != nil {
			p.logger.Error("Query failed", "server", server.FQDN, "query", q.Name, "error", err)
			return OracleDatabaseMetrics{}, fmt.Errorf("query %s failed for %s: %w", q.Name, server.FQDN, err)
		}

		cols, _ := rows.Columns()
		var queryRows []map[string]any

		for rows.Next() {
			columns := make([]any, len(cols))
			columnPointers := make([]any, len(cols))
			for j := range columns {
				columnPointers[j] = &columns[j]
			}

			if err := rows.Scan(columnPointers...); err != nil {
				continue
			}

			m := make(map[string]any)
			for j, colName := range cols {
				val := columns[j]

				if val == nil {
					m[strings.ToLower(colName)] = nil
					continue
				}

				// Konvertierung in String zur Prüfung auf numerische Werte
				var strVal string
				switch v := val.(type) {
				case []byte:
					strVal = string(v)
				default:
					strVal = fmt.Sprintf("%v", v)
				}

				// Versuch, den Wert als Int zu interpretieren
				if i, err := strconv.ParseInt(strVal, 10, 64); err == nil {
					m[strings.ToLower(colName)] = i
				} else if f, err := strconv.ParseFloat(strVal, 64); err == nil {
					// Wenn kein Int, dann vielleicht Float (z.B. bei Dezimalstellen)
					m[strings.ToLower(colName)] = f
				} else {
					// Ansonsten als String belassen
					m[strings.ToLower(colName)] = strVal
				}
			}
			queryRows = append(queryRows, m)
		}
		rows.Close()

		metrics.Data = append(metrics.Data, QueryResult{
			QueryName: q.Name,
			Rows:      queryRows,
		})
	}

	return metrics, nil
}
