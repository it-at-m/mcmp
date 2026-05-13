package config

// General settings common for all applications
type General struct {
	Passphrase string
	Debug      bool
}

// Database settings common for all applications
type Database struct {
	DSN               string
	Username          string
	EncryptedPassword string
}
