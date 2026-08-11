#!/usr/bin/env bash

# Exit on error, unset variables and failed pipeline commands
set -euo pipefail

# Helper to compare versions (returns 0 if v1 >= v2)
version_ge() {
    [ "$(printf '%s\n%s' "$1" "$2" | sort -V | head -n1)" = "$2" ]
}

echo "🔍 Fetching latest Go version from go.dev..."
LATEST_GO_VERSION=$(curl -s https://go.dev/VERSION?m=text | head -n 1 | sed 's/go//')
[ -z "$LATEST_GO_VERSION" ] && LATEST_GO_VERSION=$(go version | sed -n 's/.*go\([0-9.]*\).*/\1/p')

echo "Latest available Go version: $LATEST_GO_VERSION"
echo "------------------------------------------------------------"

# Get project root (one level up from script directory)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 1. Check go.work
WORK_VERSION=""
if [ -f "go.work" ]; then
    WORK_VERSION=$(grep -E "^go [0-9.]+" go.work | awk '{print $2}' || echo "")
    [ -n "$WORK_VERSION" ] && echo "Found go.work version: $WORK_VERSION"
fi

# 2. Scan projects and collect status
NEEDS_UPDATE=false
DISCREPANCY_FOUND=false
declare -a PROJECTS

echo "Checking project modules..."
printf "%-40s %-10s %-10s %-10s\n" "Project Directory" "go.mod" "Linter" "Status"
printf "%-40s %-10s %-10s %-10s\n" "----------------" "------" "------" "------"

while read -r dir; do
    MOD_FILE="$dir/go.mod"
    MOD_VERSION=$(grep -E "^go [0-9.]+" "$MOD_FILE" | awk '{print $2}' || echo "")

    LINTER_FILE="$dir/.golangci.yml"
    LINTER_VERSION=""
    if [ -f "$LINTER_FILE" ]; then
        LINTER_VERSION=$(sed -n 's/.*go: "\(.*\)".*/\1/p' "$LINTER_FILE")
        [ -z "$LINTER_VERSION" ] && LINTER_VERSION=$(sed -n 's/.*go: \(.*\)$/\1/p' "$LINTER_FILE")
    fi

    STATUS="OK"
    # Logic to determine if this specific module is outdated
    if [ -n "$MOD_VERSION" ] && ! version_ge "$MOD_VERSION" "$LATEST_GO_VERSION"; then
        STATUS="Outdated"
        NEEDS_UPDATE=true
    fi
    if [ -n "$WORK_VERSION" ] && [ "$MOD_VERSION" != "$WORK_VERSION" ]; then
        STATUS="Outdated"
        DISCREPANCY_FOUND=true
    fi

    printf "%-40s %-10s %-10s %-10s\n" "$dir" "$MOD_VERSION" "${LINTER_VERSION:-N/A}" "$STATUS"
    PROJECTS+=("$dir|$MOD_VERSION|$LINTER_VERSION")
done < <(find . -name "go.mod" -not -path "*/vendor/*" -exec dirname {} \;)

echo "------------------------------------------------------------"

# 3. Decision Logic
UPDATE_ALL=false
if [ "$NEEDS_UPDATE" = true ] || [ "$DISCREPANCY_FOUND" = true ]; then
    echo "⚠️  Outdated versions or discrepancies detected in the projects marked above."
    read -p "Align all go.mod, go.work and .golangci.yml files to Go $LATEST_GO_VERSION? (y/N): " confirm
    if [[ "$confirm" =~ ^[Yy]$ ]]; then
        UPDATE_ALL=true
        if [ -f "go.work" ]; then
            sed -i.bak "s/^go [0-9.]*/go $LATEST_GO_VERSION/" go.work && rm go.work.bak
        fi
    fi
else
    echo "✅ All version strings are up to date."
fi

# 4. Processing with Reporting
FAILED_BUILDS=()
FAILED_TESTS=()

for entry in "${PROJECTS[@]}"; do
    IFS="|" read -r dir mod_ver lint_ver <<< "$entry"
    echo "------------------------------------------------------------"
    echo "Processing: $dir"

    (
        cd "$dir" || exit 1

        if [ "$UPDATE_ALL" = true ]; then
            echo "-> Upgrading Go version to $LATEST_GO_VERSION..."
            go mod edit -go="$LATEST_GO_VERSION"
            if [ -f ".golangci.yml" ]; then
                # Update ONLY the go version inside the run block
                # We use a pattern that looks for the 'run:' section and the 'go:' key
                LINTER_GO=$(echo "$LATEST_GO_VERSION" | cut -d. -f1,2)
                sed -i.bak -E "/run:/,/go:/ s/(go: \")?[0-9.]+(\")?/\1$LINTER_GO\2/" .golangci.yml && rm .golangci.yml.bak
            fi
        fi

        echo "-> Formatting & Updating..."
        # Use gofumpt if available (as seen in your golangci.yml)
        if command -v gofumpt &> /dev/null; then gofumpt -w . ; else go fmt ./... ; fi

        go mod tidy
        go get -u ./...
        go mod tidy

        echo "-> Building..."
        if ! go build ./... > /dev/null 2>&1; then
            echo "   ❌ Build failed"
            exit 2
        fi

        echo "-> Testing..."
        if ! go test ./... > /dev/null 2>&1; then
            echo "   ❌ Tests failed"
            exit 3
        fi
        echo "   ✅ OK"
    ) || {
        RET=$?
        if [ $RET -eq 2 ]; then FAILED_BUILDS+=("$dir"); fi
        if [ $RET -eq 3 ]; then FAILED_TESTS+=("$dir"); fi
    }
done

# 5. Final Summary
echo "============================================================"
echo "SUMMARY"
echo "============================================================"
if [ ${#FAILED_BUILDS[@]} -eq 0 ] && [ ${#FAILED_TESTS[@]} -eq 0 ]; then
    echo "✅ All projects updated and verified successfully!"
else
    [ ${#FAILED_BUILDS[@]} -gt 0 ] && echo "❌ Build failures in: ${FAILED_BUILDS[*]}"
    [ ${#FAILED_TESTS[@]} -gt 0 ] && echo "❌ Test failures in: ${FAILED_TESTS[*]}"
    exit 1
fi