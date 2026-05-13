package logging

type LogConfig struct {
	Level      string // DEBUG, INFO, WARN, ERROR
	Output     string // console, file, both
	Format     string // json, text, plain
	Filename   string // log file path
	MaxSize    int    // maximum size in megabytes before rotation
	MaxBackups int    // maximum number of old files to retain
	MaxAge     int    // maximum days to retain old files
	Compress   bool   // compress rotated files with gzip
}

// SetDefaults sets default values for configuration fields that are empty or zero.
func (c *LogConfig) SetDefaults() {
	if c.Level == "" {
		c.Level = "INFO"
	}
	if c.Output == "" {
		c.Output = "console"
	}
	if c.Format == "" {
		c.Format = "plain"
	}
	if c.MaxSize == 0 {
		c.MaxSize = 100
	}
	if c.MaxBackups == 0 {
		c.MaxBackups = 3
	}
	if c.MaxAge == 0 {
		c.MaxAge = 28
	}
	// Filename defaults are handled in NewStructuredLogger depending on Output type,
	// but can be set here if needed.
}
