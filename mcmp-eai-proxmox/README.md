# MCMP EAI for Proxmox Datacenter Manager

This EAI imports servers from Proxmox Datacenter Manager.


## Usage

1. Ensure the configuration file is valid and the service can access all configured MCMP and PDM instances.
2. Launching the EAI will import all servers from the configured PDM instances into all configured MCMP instances.

A configuration example is provided in `mcmp-eai-proxmox.toml.example`.

A proxy can be configured using the `HTTP_PROXY`/`HTTPS_PROXY` variables.


## Permissions

An API Token with Auditor permission is required.