// Package lock provides a simple file-based mechanism to ensure that only one
// instance of an application is running at a time (single-instance lock).
package lock

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"
)

// maxRetries defines the maximum number of attempts to acquire the lock.
// This handles race conditions, e.g., when a stale lock file is removed
// by another process between our check and creation attempt.
const maxRetries = 3

// ErrAlreadyRunning is returned when another instance of the application
// is currently holding the lock.
var ErrAlreadyRunning = errors.New("another instance is already running")

// Acquire attempts to create a lock file to ensure that only one instance of the
// application is running at a time.
//
// Mechanism:
//  1. Determines the lock file path (platform dependent).
//  2. Ensures the directory for the lock file exists.
//  3. Tries to open the file with O_CREATE | O_EXCL (atomic creation).
//  4. If successful, writes the current PID.
//  5. If the file exists, it checks for a stale lock (dead process).
//     - If the holding process is dead, removes the lock file and retries.
//     - If the holding process is alive, returns ErrAlreadyRunning.
//
// Returns:
//   - release: A cleanup function that removes the lock file. Call it via defer.
//   - error: ErrAlreadyRunning if another instance is running, or other errors.
func Acquire(appname string) (release func(), err error) {
	lockFilePath := getLockFilePath(appname)

	// Ensure the directory exists. /var/run/user/<UID> might not exist on all systems.
	if err := os.MkdirAll(filepath.Dir(lockFilePath), 0o755); err != nil {
		return nil, fmt.Errorf("failed to create lock directory: %w", err)
	}

	for i := 0; i < maxRetries; i++ {
		// Try to open/create the file atomically.
		// O_EXCL ensures that it fails if the file already exists.
		f, err := os.OpenFile(lockFilePath, os.O_RDWR|os.O_CREATE|os.O_EXCL, 0o600)

		if err == nil {
			// Successfully created -> write PID and close.
			pid := os.Getpid()
			if _, writeErr := fmt.Fprintf(f, "%d", pid); writeErr != nil {
				_ = f.Close()
				_ = os.Remove(lockFilePath) // Clean up broken file.
				return nil, fmt.Errorf("failed to write PID to lock file: %w", writeErr)
			}
			if closeErr := f.Close(); closeErr != nil {
				// The PID was written, but the file is in an undefined state.
				// Attempt cleanup, but the lock was technically acquired.
				_ = os.Remove(lockFilePath)
				return nil, fmt.Errorf("failed to close lock file: %w", closeErr)
			}
			release := func() {
				if err := os.Remove(lockFilePath); err != nil && !os.IsNotExist(err) {
					_, _ = fmt.Fprintf(os.Stderr, "warning: failed to remove lock file %q: %v\n", lockFilePath, err)
				}
			}
			return release, nil
		}

		// If the error is NOT "file already exists", it's a real error (e.g., permissions).
		if !os.IsExist(err) {
			return nil, fmt.Errorf("error accessing lock file %q: %w", lockFilePath, err)
		}

		// --- File already exists: Check for stale lock (crashed process) ---

		// Read the PID from the existing file
		content, readErr := os.ReadFile(lockFilePath)
		if readErr != nil {
			// If the file was deleted by another process, loop and retry.
			if os.IsNotExist(readErr) {
				continue
			}
			return nil, fmt.Errorf("cannot read existing lock file: %w", readErr)
		}

		// Parse PID
		oldPID, parseErr := strconv.Atoi(strings.TrimSpace(string(content)))
		if parseErr != nil {
			// File content is invalid -> Probably corrupt. Remove and retry.
			if removeErr := os.Remove(lockFilePath); removeErr != nil && !os.IsNotExist(removeErr) {
				return nil, fmt.Errorf("lock file corrupt (%q) and cannot be removed: %w", string(content), removeErr)
			}
			continue
		}

		// Check if the process holding the lock is still alive.
		if isProcessRunning(oldPID) {
			return nil, fmt.Errorf("%w (PID: %d)", ErrAlreadyRunning, oldPID)
		}

		// The old process is dead -> Delete stale lock file and retry.
		if removeErr := os.Remove(lockFilePath); removeErr != nil && !os.IsNotExist(removeErr) {
			return nil, fmt.Errorf("could not delete stale lock file: %w", removeErr)
		}
		// Loop continues to retry acquisition.
	}

	return nil, fmt.Errorf("maximum retries (%d) exceeded for lock acquisition", maxRetries)
}

// isProcessRunning checks if a process with the given PID is still running.
// It uses os.FindProcess and sends signal 0 to test process existence.
//
// Note: On Unix systems, os.FindProcess always succeeds, so Signal(0) is
// the reliable way to check. On Windows, os.FindProcess can fail if the
// process doesn't exist.
func isProcessRunning(pid int) bool {
	process, err := os.FindProcess(pid)
	if err != nil {
		// On Unix, FindProcess always succeeds. On Windows, this means process not found.
		return false
	}

	// Signal 0 checks if the process exists without sending an actual signal.
	err = process.Signal(syscall.Signal(0))
	if err == nil {
		return true // Process is running and we can signal it.
	}

	// Check for specific errors to determine process state.
	// ESRCH: No such process.
	if errors.Is(err, syscall.ESRCH) {
		return false
	}
	// os.ErrProcessDone: Process has finished.
	if errors.Is(err, os.ErrProcessDone) {
		return false
	}
	// EPERM: Process exists but we don't have permission to signal it.
	if errors.Is(err, syscall.EPERM) {
		return true
	}

	// Conservative fallback: assume the process is running if we can't determine its state.
	return true
}

// getLockFilePath returns the full path to the lock file based on the current user's UID.
// It typically follows the pattern /var/run/user/<uid>/<appname>.pid on Linux,
// or falls back to the system temp directory on other platforms.
// Using the UID ensures that the lock file path is unique per user, avoiding
// permission conflicts on shared systems.
func getLockFilePath(appname string) string {
	var baseDir string

	if runtime.GOOS == "linux" {
		uid := os.Getuid()
		baseDir = fmt.Sprintf("/var/run/user/%d", uid)
	} else {
		baseDir = os.TempDir()
	}
	return filepath.Join(baseDir, appname+".pid")
}
