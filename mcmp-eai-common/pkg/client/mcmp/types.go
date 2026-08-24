package mcmp

// OracleServer represents the data structure returned by the Oracle DB controller's server list endpoint.
type OracleServer struct {
	FQDN string `json:"fqdn"`
	PDB  string `json:"pdb"`
}
