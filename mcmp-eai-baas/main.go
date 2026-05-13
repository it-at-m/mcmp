package main

import (
	"flag"
	"fmt"
	"log"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-baas/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-baas/pkg/clients/baas"
	"github.com/it-at-m/mcmp/mcmp-eai-baas/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-baas/pkg/db"
	cfg "github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"gorm.io/gorm"
)

var debug = false

const (
	appname            = "mcmp-eai-baas"
	VM      BackupType = "vm"
	Agent   BackupType = "agent"
	DA      BackupType = "da"
	DB      BackupType = "db"
	DH      BackupType = "dh"
	DM      BackupType = "dm"
	DP      BackupType = "dp"
	DS      BackupType = "ds"
	DY      BackupType = "dy"
	NFS     BackupType = "nfs"
	CIFS    BackupType = "cifs"
)

type (
	// Baas represents the configuration for a backup as a service system
	Baas struct {
		Enabled bool   // Whether this BaaS instance is enabled
		Fqdn    string // Fully qualified domain name of the BaaS system
		Cloud   string // Cloud identifier for this BaaS instance
	}

	// Config represents the complete application configuration
	Config struct {
		GENERAL  config.General  // General application configuration
		DATABASE config.Database // Database connection configuration
		BAAS     []Baas          // List of configured BaaS systems
	}

	// BackupType represents the type of backup (vm, agent, db, etc.)
	BackupType string

	// Cloud represents a cloud environment in the database
	Cloud struct {
		ID        uint   `gorm:"column:id;primaryKey;autoIncrement:true"`
		Fqdn      string `gorm:"column:fqdn;size:100;not null;not null;uniqueIndex"`
		CloudType string `gorm:"column:cloud_type"`
	}

	// Server represents a server/VM in the database
	Server struct {
		ID      uint   `gorm:"column:id;primaryKey;autoIncrement:true"`
		CloudID uint   `gorm:"column:cloud_id;not null;uniqueIndex:cloud_uuid_idx;constraint:OnDelete:CASCADE"`
		Cloud   Cloud  `gorm:"constraint:OnDelete:CASCADE"`
		UUID    string `gorm:"column:uuid;size:50;not null;uniqueIndex:cloud_uuid_idx"`
		Name    string `gorm:"column:name;not null;size:200"`
	}

	// Backup represents a backup entry in the database
	Backup struct {
		ID             uint       `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version        uint32     `gorm:"column:version;default:0"`
		CreatedAt      time.Time  `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt      time.Time  `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		ServerID       uint       `gorm:"column:server_id;not null;constraint:OnDelete:CASCADE"`
		Server         Server     `gorm:"constraint:OnDelete:CASCADE"`
		BackupType     BackupType `gorm:"column:backup_type;type:cmp.backup_type;not null"`
		BackupServer   string     `gorm:"column:backup_server;size:100;not null"`
		ClientServer   string     `gorm:"column:client_server;size:100;not null"`
		SaveSetName    string     `gorm:"column:save_set_name;size:100;not null"`
		SaveTimeString string     `gorm:"column:save_time_string;size:50;not null"`
		SaveTime       time.Time  `gorm:"column:save_time;not null"`
		SsretentString string     `gorm:"column:ssretent_string;size:30;not null"`
		Ssretent       time.Time  `gorm:"column:ssretent;not null"`
		Ssid           string     `gorm:"column:ssid;not null;size:50"`
		CloneId        string     `gorm:"column:clone_id;not null;size:50"`
		Pool           string     `gorm:"column:pool;size:100;not null"`
		Totalsize      int64      `gorm:"column:totalsize;type:bigint"`
		Runtime        string     `gorm:"column:runtime;not null;size:10"`
	}
)

// TableName returns the custom table name for Cloud model
func (*Cloud) TableName() string {
	return "cloud"
}

// TableName returns the custom table name for Server model
func (*Server) TableName() string {
	return "server"
}

// TableName returns the custom table name for Backup model
func (*Backup) TableName() string {
	return "backup"
}

// CompareAndUpdate compares the stored backup with a new backup and updates it if there are differences.
// Returns true if the backup was updated, false otherwise.
// This method performs a field-by-field comparison and updates the stored backup with new values
// if any differences are found. The version is incremented and the updated timestamp is set.
func (storedBackup *Backup) CompareAndUpdate(newBackup Backup, timeNow time.Time) bool {
	if storedBackup.ServerID != newBackup.ServerID ||
		storedBackup.BackupType != newBackup.BackupType ||
		storedBackup.BackupServer != newBackup.BackupServer ||
		storedBackup.ClientServer != newBackup.ClientServer ||
		storedBackup.SaveSetName != newBackup.SaveSetName ||
		storedBackup.SaveTimeString != newBackup.SaveTimeString ||
		storedBackup.SaveTime.UTC() != newBackup.SaveTime.UTC() ||
		storedBackup.SsretentString != newBackup.SsretentString ||
		storedBackup.Ssretent.UTC() != newBackup.Ssretent.UTC() ||
		storedBackup.Ssid != newBackup.Ssid ||
		storedBackup.CloneId != newBackup.CloneId ||
		storedBackup.Pool != newBackup.Pool ||
		storedBackup.Totalsize != newBackup.Totalsize ||
		storedBackup.Runtime != newBackup.Runtime {
		storedBackup.UpdatedAt = timeNow
		storedBackup.Version = storedBackup.Version + 1
		storedBackup.ServerID = newBackup.ServerID
		storedBackup.BackupType = newBackup.BackupType
		storedBackup.BackupServer = newBackup.BackupServer
		storedBackup.ClientServer = newBackup.ClientServer
		storedBackup.SaveSetName = newBackup.SaveSetName
		storedBackup.SaveTimeString = newBackup.SaveTimeString
		storedBackup.SaveTime = newBackup.SaveTime
		storedBackup.SsretentString = newBackup.SsretentString
		storedBackup.Ssretent = newBackup.Ssretent
		storedBackup.Ssid = newBackup.Ssid
		storedBackup.CloneId = newBackup.CloneId
		storedBackup.Pool = newBackup.Pool
		storedBackup.Totalsize = newBackup.Totalsize
		storedBackup.Runtime = newBackup.Runtime
		return true
	}
	return false
}

// main is the entry point of the application. It handles command line arguments and
// delegates to the appropriate function based on the provided arguments.
// Supported commands:
// - no arguments: runs the bot
// - "init": initializes the database
// - "crypt-password": encrypts a password
func main() {
	flag.Usage = func() {
		_, err := fmt.Fprintf(flag.CommandLine.Output(), "usage: %s [init|crypt-password]\n", os.Args[0])
		if err != nil {
			log.Fatal(err)
		}
		flag.PrintDefaults()
	}
	flag.Parse()
	switch len(os.Args) {
	case 1:
		bot()
	case 2:
		switch os.Args[1] {
		case "init":
			initDB()
		case "crypt-password":
			err := app.CryptPassword()
			if err != nil {
				log.Fatal(err)
			}
		default:
			_, err := fmt.Fprintf(os.Stderr, "error: unknown command - %s\n", os.Args[1])
			if err != nil {
				log.Fatal(err)
			}
			flag.Usage()
			os.Exit(1)
		}
	default:
		_, err := fmt.Fprintln(os.Stderr, "error: wrong number of arguments")
		if err != nil {
			log.Fatal(err)
		}
		flag.Usage()
		os.Exit(1)
	}
}

// worker processes backup data for a single server.
// It fetches current backup information from the BaaS system, compares it with
// stored data in the database, and updates the database accordingly.
// This function handles the complete backup synchronization process for one server.
func worker(db *gorm.DB, server Server, baasClient *baas.Client) bool {
	debugPrintf("VM %s\n", server.Name)
	timeNow := time.Now()

	// Retrieve existing backups from database as a map for efficient lookup
	storedBackupMap, err := getBackupsAsMap(db, server.ID)
	if err != nil {
		log.Printf("Error reading backups for VM %s: %s\n", server.Name, err.Error())
		return false
	}

	// URL-encode the server name for the API call
	servername := url.QueryEscape(server.Name)

	// Fetch current backup information from BaaS system
	backup, err := baasClient.FetchBackups(servername)
	if err != nil {
		log.Printf("Error fetching backups for VM %s: %s\n", server.Name, err.Error())
		return false
	}

	// Check if backup data is valid
	if backup == nil || backup.Status != 0 {
		debugPrintf("No backups found for VM %s\n", server.Name)
		return false
	}

	foundAny := len(backup.Backups.Catalog.VM) > 0 ||
		len(backup.Backups.Catalog.Agent) > 0 ||
		len(backup.Backups.Catalog.DA) > 0 ||
		len(backup.Backups.Catalog.DB) > 0 ||
		len(backup.Backups.Catalog.DH) > 0 ||
		len(backup.Backups.Catalog.DM) > 0 ||
		len(backup.Backups.Catalog.DP) > 0 ||
		len(backup.Backups.Catalog.DS) > 0 ||
		len(backup.Backups.Catalog.DY) > 0 ||
		len(backup.Backups.Catalog.NFS) > 0 ||
		len(backup.Backups.Catalog.CIFS) > 0

	if !foundAny {
		return false
	}

	// Process each backup type catalog
	processCatalog(backup.Backups.Catalog.VM, VM, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.Agent, Agent, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.DA, DA, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.DB, DB, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.DH, DH, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.DM, DM, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.DP, DP, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.DS, DS, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.DY, DY, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.NFS, NFS, timeNow, storedBackupMap, db, server)
	processCatalog(backup.Backups.Catalog.CIFS, CIFS, timeNow, storedBackupMap, db, server)

	// Delete backups that are no longer present in the BaaS system
	for _, storedBackup := range storedBackupMap {
		debugPrintf("Server %s - Backup %s (%s) will be deleted from DB!\n", server.Name, storedBackup.SaveSetName, storedBackup.Ssid)
		if err := db.Delete(&storedBackup).Error; err != nil {
			log.Printf("VM %s - Error deleting backup %s (%v)\n", server.Name, storedBackup.Ssid, err)
		}
	}
	return true
}

// bot orchestrates backup verification and processing across multiple systems, including VMs and UCS hardware.
func bot() {
	// Configure concurrency - limit to 1 worker to avoid overwhelming the BaaS system
	maxWorker := 1
	semaphore := make(chan struct{}, maxWorker)
	var wg sync.WaitGroup

	// Read application configuration
	c, err := cfg.LoadConfig[Config](appname)
	if err != nil {
		log.Fatal(err)
	}
	debug = c.GENERAL.Debug

	// Open database connection
	postgres := db.OpenPostgres(c.GENERAL, c.DATABASE)

	// vCenter
	var activeClients []*baas.Client
	for _, baasConfig := range c.BAAS {
		if baasConfig.Enabled {
			debugPrintf("BaaS System: %s / Cloud: %s\n", baasConfig.Fqdn, baasConfig.Cloud)

			// Create BaaS client
			baasClient := baas.New(baasConfig.Fqdn)
			if c.GENERAL.Debug {
				baasClient.EnableDebug()
			}
			activeClients = append(activeClients, baasClient)

			debugPrintf("Phase 1: Processing Cloud %s on BaaS %s\n", baasConfig.Cloud, baasConfig.Fqdn)
			vms := getVirtualMachinesByCloud(postgres, baasConfig.Cloud)
			if len(vms) < 1 {
				continue
			}

			// Process each VM concurrently
			for _, vm := range vms {
				wg.Add(1)
				go func(v Server) {
					defer wg.Done()
					semaphore <- struct{}{}        // Acquire semaphore
					defer func() { <-semaphore }() // Release semaphore safely via defer

					worker(postgres, v, baasClient) // Process VM
				}(vm)
			}
			wg.Wait() // Wait for all workers to complete
		}
	}

	// UCS Hardware
	ucsServers := getServersByUcs(postgres)
	debugPrintf("Phase 2: Processing %d UCS Hardware servers\n", len(ucsServers))
	if len(ucsServers) == 0 {
		return
	}
	for _, server := range ucsServers {
		if len(server.Name) >= 4 && strings.ToLower(server.Name[:4]) == "esxi" {
			continue
		}

		wg.Add(1)
		go func(s Server) {
			defer wg.Done()
			semaphore <- struct{}{}
			defer func() { <-semaphore }()

			for _, client := range activeClients {
				debugPrintf("Checking UCS Server %s on %s...\n", s.Name, client.GetHostname())
				if worker(postgres, s, client) {
					debugPrintf("Backup found for %s on %s. Stopping search for this server.\n", s.Name, client.GetHostname())
					break
				}
			}
		}(server)
	}
	wg.Wait() // Wait for all workers to complete
}

// initDB initializes the database by creating all necessary tables.
// This function performs auto-migration for all defined models.
func initDB() {
	c, err := cfg.LoadConfig[Config](appname)
	if err != nil {
		log.Fatal(err)
	}
	debug = c.GENERAL.Debug
	postgres := db.OpenPostgres(c.GENERAL, c.DATABASE)

	// List of all models that need to be migrated
	models := []any{
		&Cloud{},
		&Server{},
		&Backup{},
	}

	// Auto-migrate each model
	for _, model := range models {
		err := app.AutoMigrateTable(postgres, model)
		if err != nil {
			log.Fatal(err)
		}
	}
}

// debugPrintf prints debug messages when debug mode is enabled.
// This function provides conditional logging based on the debug flag.
func debugPrintf(format string, a ...interface{}) {
	if debug {
		log.Printf(format, a...)
	}
}

// getVirtualMachinesByCloud retrieves all virtual machines associated with a specific cloud.
// Returns a slice of Server objects or nil if no VMs are found.
func getVirtualMachinesByCloud(db *gorm.DB, cloudFqdn string) []Server {
	debugPrintf("Reading all VMs from cloud '%s'.\n", cloudFqdn)

	// First, find the cloud by FQDN
	var cloud Cloud
	result := db.Where("fqdn = ?", cloudFqdn).First(&cloud)
	if result.RowsAffected == 0 {
		log.Printf("Cloud '%s' was not found in the database!\n", cloudFqdn)
		return nil
	}

	// Then, find all VMs associated with this cloud
	var vmList []Server
	result = db.Where("cloud_id = ?", cloud.ID).Find(&vmList)
	if result.RowsAffected == 0 {
		debugPrintf("No VMs are assigned to cloud '%s'!\n", cloudFqdn)
		return nil
	}
	return vmList
}

// getServersByUcs retrieves all servers linked to UCS_MANAGER and UCS_CIMC cloud types from the database.
func getServersByUcs(db *gorm.DB) []Server {
	debugPrintf("Reading all servers from UCS_MANAGER and UCS_CIMC clouds.\n")

	var serverList []Server
	result := db.Joins("JOIN cloud ON cloud.id = server.cloud_id").
		Where("cloud.cloud_type IN ?", []string{"UCS_MANAGER", "UCS_CIMC"}).
		Find(&serverList)

	if result.Error != nil {
		log.Printf("Error reading UCS servers: %v\n", result.Error)
		return nil
	}

	if result.RowsAffected == 0 {
		debugPrintf("No servers found for UCS clouds.\n")
		return nil
	}

	return serverList
}

// deleteBackups removes all backup entries for a specific server from the database.
// This function is used when backup data cannot be retrieved or processed.
func deleteBackups(db *gorm.DB, serverId uint) error {
	debugPrintf("Deleting all backups for VM-ID %d.\n", serverId)
	result := db.Where("server_id = ?", serverId).Delete(&Backup{})
	if result.Error != nil {
		return result.Error
	}
	debugPrintf("Number of deleted backups: %d\n", result.RowsAffected)
	return nil
}

// getBackupsAsMap retrieves all backups for a server and returns them as a map.
// The map key is generated using getBackupId() for efficient lookup during synchronization.
func getBackupsAsMap(db *gorm.DB, serverID uint) (map[string]Backup, error) {
	var backups []Backup
	result := db.Where("server_id = ?", serverID).Find(&backups)
	if result.Error != nil {
		return nil, result.Error
	}

	// Convert slice to map for efficient lookup
	backupMap := make(map[string]Backup)
	for _, backup := range backups {
		backupMap[getBackupId(backup.BackupType, backup.Ssid, backup.CloneId, backup.SaveSetName, backup.SaveTimeString)] = backup
	}
	return backupMap, nil
}

// getBackupId generates a unique identifier for a backup entry.
// The ID format varies based on backup type - database backups include additional fields.
func getBackupId(backupType BackupType, ssid string, cloneId string, saveSetName string, saveTime string) string {
	if backupType != "db" {
		return fmt.Sprintf("%s-%s-%s", backupType, ssid, cloneId)
	}
	return fmt.Sprintf("%s-%s-%s-%s-%s", backupType, ssid, cloneId, saveSetName, saveTime)
}

// processCatalog processes a catalog of backup items from the BaaS system.
// It compares each item with existing database entries and performs batch operations
// to insert new backups and update existing ones. This function optimizes database
// operations by using transactions and batch processing.
func processCatalog(items []baas.BackupItem, backupType BackupType, timeNow time.Time, storedBackupMap map[string]Backup, db *gorm.DB, vm Server) {
	var newBackups []Backup     // Stores new backups to be inserted
	var updatedBackups []Backup // Stores backups to be updated

	// Process each backup item from the BaaS system
	for _, item := range items {
		// Create a new backup object from the BaaS item
		newBackup := Backup{
			Version:        0,
			CreatedAt:      timeNow,
			UpdatedAt:      timeNow,
			ServerID:       vm.ID,
			BackupType:     backupType,
			BackupServer:   item.BackupServer,
			ClientServer:   item.ClientServer,
			SaveSetName:    item.SaveSetName,
			SaveTimeString: item.SaveTime.StringValue,
			SaveTime:       item.SaveTime.TimeValue,
			SsretentString: item.Ssretent.StringValue,
			Ssretent:       item.Ssretent.TimeValue,
			Ssid:           item.SSID,
			CloneId:        item.CloneID,
			Pool:           item.Pool,
			Totalsize:      item.Totalsize,
			Runtime:        item.Runtime,
		}

		// Generate unique backup ID for comparison
		backupID := getBackupId(backupType, item.SSID, item.CloneID, item.SaveSetName, item.SaveTime.StringValue)

		// Check if backup already exists in database
		if storedBackup, exists := storedBackupMap[backupID]; exists {
			// Backup exists - remove from map (remaining entries will be deleted)
			delete(storedBackupMap, backupID)

			// Check if update is needed
			if storedBackup.CompareAndUpdate(newBackup, timeNow) {
				updatedBackups = append(updatedBackups, storedBackup)
				debugPrintf("VM %s - Backup %s (%s) will be updated.\n", vm.Name, item.SaveSetName, item.SSID)
			} else {
				debugPrintf("VM %s - Backup %s (%s) does not need to be updated!\n", vm.Name, item.SaveSetName, item.SSID)
			}
		} else {
			// New backup - add to insert list
			newBackups = append(newBackups, newBackup)
			debugPrintf("VM %s - Backup %s (%s) is not in DB and will be created!\n", vm.Name, item.SaveSetName, item.SSID)
		}
	}

	// Perform batch operations within a transaction
	tx := db.Begin() // Start transaction
	if tx.Error != nil {
		log.Printf("Error starting transaction: %v\n", tx.Error)
		return
	}

	// Insert new backups in batches
	if len(newBackups) > 0 {
		if err := tx.CreateInBatches(newBackups, 100).Error; err != nil {
			log.Printf("Error inserting new backups: %v\n", err)
			tx.Rollback()
			return
		}
	}

	// Update existing backups
	for _, backup := range updatedBackups {
		if err := tx.Save(&backup).Error; err != nil {
			log.Printf("Error updating backup %d: %v\n", backup.ID, err)
			tx.Rollback()
			return
		}
	}

	// Commit transaction
	if err := tx.Commit().Error; err != nil {
		log.Printf("Error committing transaction: %v\n", err)
	}
}
