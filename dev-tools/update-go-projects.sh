#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# update-go-projects.sh
#
# Aligns every go.mod, go.work and .golangci.yml file in this repository with
# the latest stable Go release and verifies that all modules still vet, build
# and test.
#
# The go directive rewriting is delegated to `go mod edit` / `go work edit`.
# Hand-rolled parsing/writing of go directives is error prone (comment
# placement, CRLF line endings, file permissions); the Go toolchain already
# handles all of that and keeps the files canonical.
#
# Usage: ./dev-tools/update-go-projects.sh [OPTIONS]
#
# Requirements: bash 4.2+, go 1.21+ (go work edit -toolchain), curl (optional),
#               git (optional), gofumpt (optional)
#
# Logs are written to .update-logs/ - add that directory to .gitignore, or
# point UPDATE_GO_LOG_DIR somewhere outside the repository.
# ---------------------------------------------------------------------------

set -euo pipefail

# --- Paths -----------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${UPDATE_GO_LOG_DIR:-${PROJECT_ROOT}/.update-logs}"
VERSION_URL="https://go.dev/VERSION?m=text"
GO_WORK_FILE="./go.work"

# --- Runtime state ---------------------------------------------------------
ASSUME_YES=false
UPDATE_DEPS=true
DRY_RUN=false
RUN_FORMATTER=true

declare -a TEMP_FILES=()
declare -a MODULE_DIRS=()
declare -a LINTER_FILES=()
declare -a UPDATED_FILES=()
declare -a FAILED_UPDATES=()
declare -a FAILED_DEPS=()
declare -a FAILED_VET=()
declare -a FAILED_BUILDS=()
declare -a FAILED_TESTS=()

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/update-go-projects.XXXXXX")"

# Never leave temporary files behind when the script is interrupted.
# The arrays are only expanded when non-empty: on Bash 4.2/4.3 "${arr[@]}"
# on an empty array is an error under `set -u`.
cleanup() {
    local f
    if [ "${#TEMP_FILES[@]}" -gt 0 ]; then
        for f in "${TEMP_FILES[@]}"; do
            if [ -n "$f" ]; then
                rm -f -- "$f"
            fi
        done
    fi
    if [ -n "${TEMP_DIR:-}" ] && [ -d "$TEMP_DIR" ]; then
        rm -rf -- "$TEMP_DIR"
    fi
    return 0
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

usage() {
    cat <<'EOF'
Usage: update-go-projects.sh [OPTIONS]

Aligns every go.mod, go.work and .golangci.yml in this repository with the
latest stable Go release and verifies that all modules still vet, build and
test. Exit code is 0 only if every update and every check succeeded.

Options:
  -y, --yes      Accept all confirmation prompts (non-interactive mode).
      --no-deps  Do not bump dependencies (skips `go get -u=patch`).
      --no-format
                 Do not run gofumpt / go fmt at all.
  -n, --dry-run  Show what would change without writing any file.
  -h, --help     Show this help and exit.

Environment:
  UPDATE_GO_LOG_DIR  Directory for per-module logs (default: .update-logs).
  GOTOOLCHAIN        Respected; if it forbids a newer toolchain the script
                     aborts before touching any file.
EOF
}

# ---------------------------------------------------------------------------
# Version helpers
# ---------------------------------------------------------------------------

# Normalise an arbitrary version string (go1.23rc1, 1.22, v1.22.5+meta)
# to MAJOR.MINOR.PATCH. Uses a regex instead of stripping non-digits:
# stripping would turn "go1.23rc1" into "1.231" (minor 231!) and could
# overflow the patch field with a 4th segment.
# Note: pre-releases collapse onto their final release (1.23rc1 -> 1.23.0);
# do not use this helper for pre-release ordering.
normalize_version() {
    local s="${1:-}" major=0 minor=0 patch=0
    if [[ "$s" =~ ([0-9]+)\.([0-9]+)(\.([0-9]+))? ]]; then
        major="${BASH_REMATCH[1]}"
        minor="${BASH_REMATCH[2]}"
        patch="${BASH_REMATCH[4]:-0}"
    fi
    printf '%s.%s.%s\n' "$major" "$minor" "$patch"
}

# Return 0 if $1 >= $2, 1 otherwise (both normalised to MAJOR.MINOR.PATCH).
# Pure bash on purpose: `sort -V` is GNU-only and unavailable on macOS.
version_ge() {
    local -a av=()
    local -a bv=()
    local i
    IFS='.' read -r -a av <<< "$(normalize_version "$1")"
    IFS='.' read -r -a bv <<< "$(normalize_version "$2")"
    for i in 0 1 2; do
        # ${var:-0} guards against a malformed value reaching `10#`
        # ("invalid number" would abort the script under `set -e`).
        if (( 10#${av[i]:-0} > 10#${bv[i]:-0} )); then return 0; fi
        if (( 10#${av[i]:-0} < 10#${bv[i]:-0} )); then return 1; fi
    done
    return 0
}

# MAJOR.MINOR only - golangci-lint's `run.go` does not accept a patch level
major_minor() {
    normalize_version "$1" | cut -d. -f1,2
}

# https://go.dev/doc/toolchain: GOTOOLCHAIN is either "auto"/"local" or
# "<name>" / "<name>+<mode>" with <mode> in {auto, path, local}.
toolchain_blocks_upgrade() {
    if [ "$GO_TOOLCHAIN_POLICY" = "local" ]; then
        return 0
    fi
    case "$GO_TOOLCHAIN_BASE" in
        auto)
            return 1
            ;;
        local)
            return 0
            ;;
        *)
            # An explicitly pinned toolchain cannot be upgraded implicitly.
            if version_ge "$GO_TOOLCHAIN_BASE" "$LATEST_GO_VERSION"; then
                return 1
            fi
            return 0
            ;;
    esac
}

# ---------------------------------------------------------------------------
# go.mod / go.work helpers - all parsing is delegated to the Go toolchain
# ---------------------------------------------------------------------------

# Read a top-level field ("Go", "Toolchain") from a go.mod / go.work file.
# $1 = mod|work, $2 = file, $3 = JSON field name
# Returns 0 with empty output if the field is absent, 2 if the file could not
# be parsed at all.
read_mod_field() {
    local kind="$1" file="$2" field="$3" json
    local -a cmd=()

    [ -f "$file" ] || return 0

    case "$kind" in
        mod)  cmd=(go mod edit -json) ;;
        work) cmd=(go work edit -json) ;;
        *)
            echo "internal error: unknown module kind '$kind'" >&2
            return 2
            ;;
    esac

    if ! json="$("${cmd[@]}" "$file" 2>/dev/null)"; then
        return 2
    fi

    case "$field" in
        Go|Toolchain) ;;
        *) echo "internal error: unknown JSON field '$field'" >&2; return 2 ;;
    esac
    # No `head` in the pipeline: `pipefail` would turn a SIGPIPE into a
    # failure and discard an otherwise valid result.
    printf '%s\n' "$json" \
        | sed -n "s/^[[:space:]]*\"${field}\": \"\\([^\"]*\\)\".*/\\1/p" \
        | sed -n '1p'
}

# Write `go` and, if requested, `toolchain` in a single invocation.
# Two separate calls would rewrite the file twice and could leave a half
# applied state if the second call fails.
# $1 = mod|work, $2 = file, $3 = go version, $4 = toolchain value
#      (empty string = do not touch the toolchain directive)
write_mod_versions() {
    local kind="$1" file="$2" goversion="$3" toolchain="${4:-}" err
    local -a cmd=()
    local -a args=("-go=${goversion}")

    if [ -n "$toolchain" ]; then
        # Never pass -toolchain="" - that deletes the directive.
        args+=("-toolchain=${toolchain}")
    fi

    case "$kind" in
        mod)  cmd=(go mod edit) ;;
        work) cmd=(go work edit) ;;
        *)    return 2 ;;
    esac

    if ! err="$("${cmd[@]}" "${args[@]}" "$file" 2>&1)"; then
        echo "❌ ${file}: '${cmd[*]} ${args[*]}' failed" >&2
        if [ -n "$err" ]; then
            printf '   %s\n' "$err" >&2
        fi
        return 1
    fi
    return 0
}

# Sync a single go.mod / go.work file to the target version.
# $1 = file, $2 = mod|work
sync_modfile() {
    local file="$1" kind="$2"
    local current_go="" current_tc="" target_tc=""

    if [ ! -f "$file" ]; then
        return 0
    fi

    if ! current_go="$(read_mod_field "$kind" "$file" Go)"; then
        echo "❌ ${file}: not parseable - skipped" >&2
        FAILED_UPDATES+=("$file")
        return 1
    fi
    current_tc="$(read_mod_field "$kind" "$file" Toolchain)" || current_tc=""

    # Only maintain an existing toolchain directive. Adding one where it was
    # absent before would produce a diff without any content reason.
    if [ -n "$current_tc" ]; then
        target_tc="go${LATEST_GO_VERSION}"
        # Never downgrade the toolchain either: a pinned pre-release
        # (e.g. go1.24rc1) can be newer than the latest stable release.
        if ! version_ge "$target_tc" "$current_tc"; then
            target_tc="$current_tc"
        fi
    fi

    # Never downgrade: a local pre-release can be newer than the latest stable
    # release returned by go.dev.
    if [ -n "$current_go" ] && ! version_ge "$LATEST_GO_VERSION" "$current_go"; then
        echo "   -  ${file}: keeps go ${current_go} (newer than ${LATEST_GO_VERSION})"
        return 0
    fi

    # Idempotency: `go mod edit` always rewrites the file, so skip the write
    # when the target values are already in place.
    if [ "$current_go" = "$LATEST_GO_VERSION" ] \
        && { [ -z "$target_tc" ] || [ "$current_tc" = "$target_tc" ]; }; then
        echo "   ✓ ${file}: already at go ${LATEST_GO_VERSION}"
        return 0
    fi

    if [ -n "$target_tc" ]; then
        echo "   → ${file}: go ${current_go:-unknown} -> ${LATEST_GO_VERSION}, toolchain ${current_tc} -> ${target_tc}"
    else
        echo "   → ${file}: go ${current_go:-unknown} -> ${LATEST_GO_VERSION}"
    fi

    if [ "$DRY_RUN" = true ]; then
        UPDATED_FILES+=("$file")
        return 0
    fi

    if ! write_mod_versions "$kind" "$file" "$LATEST_GO_VERSION" "$target_tc"; then
        FAILED_UPDATES+=("$file")
        return 1
    fi

    # Verify by re-parsing instead of trusting the written value.
    if [ "$(read_mod_field "$kind" "$file" Go)" != "$LATEST_GO_VERSION" ]; then
        echo "❌ ${file}: go directive did not take the target value" >&2
        FAILED_UPDATES+=("$file")
        return 1
    fi

    UPDATED_FILES+=("$file")
    return 0
}

# ---------------------------------------------------------------------------
# .golangci.yml helpers - no Go command covers this file, so it stays manual
# ---------------------------------------------------------------------------

# Read `run.go` from a .golangci.yml. Only the top-level `run:` block is
# inspected, so stray numbers elsewhere in the file are never picked up.
read_linter_version() {
    local file="$1" raw=""
    [ -f "$file" ] || return 0
    raw="$(awk '
        in_run && /^[[:space:]]+go:/ {
            sub(/^[[:space:]]+go:[[:space:]]*/, "")
            sub(/[[:space:]]+#.*$/, "")
            sub(/[[:space:]]+$/, "")
            print
            exit
        }
    ' "$file")"
    raw="${raw//\"/}"
    raw="${raw//\'/}"
    printf '%s' "$raw"
}

# Rewrite `run.go` in a .golangci.yml. Anchored to the top-level `run:` block
# and to the `go:` key, so e.g. `concurrency: 4` is left untouched.
write_linter_version() {
    local file="$1" target="$2" tmp="" current="" rc=0

    if [ ! -f "$file" ]; then
        return 0
    fi

    current="$(read_linter_version "$file")"
    if [ "$current" = "$target" ]; then
        echo "   ✓ ${file}: already at go ${target}"
        return 0
    fi

    echo "   → ${file}: run.go ${current:-unknown} -> ${target}"
    if [ "$DRY_RUN" = true ]; then
        UPDATED_FILES+=("$file")
        return 0
    fi

    if ! tmp="$(mktemp "${TEMP_DIR}/golangci.XXXXXX")"; then
        echo "❌ ${file}: could not create a temporary file" >&2
        FAILED_UPDATES+=("$file")
        return 1
    fi
    TEMP_FILES+=("$tmp")

    awk -v target="$target" '
        /^[^[:space:]#]/ { in_run = ($0 ~ /^run:/); print; next }
        in_run && /^[[:space:]]+go:/ && !done {
            match($0, /^[[:space:]]+/)
            printf "%sgo: \"%s\"\n", substr($0, 1, RLENGTH), target
            done = 1
            next
        }
        { print }
    ' "$file" > "$tmp"

    # Verify that the run.go block actually holds the target value. A plain
    # `grep -F` would also match the same string in another top-level block
    # (e.g. linters-settings), producing a false positive. The awk below keeps
    # the match scoped to the `run:` block.
    # Exit codes: 0 = ok, 1 = no run.go entry, 2 = entry found but not updated.
    awk -v target="$target" '
        /^[^[:space:]#]/ { in_run = ($0 ~ /^run:/); next }
        in_run && /^[[:space:]]+go:/ { found = 1; ok = (index($0, "\"" target "\"") > 0); exit }
        END { exit(ok ? 0 : (found ? 2 : 1)) }
    ' "$tmp" || rc=$?

    if [ "$rc" -ne 0 ]; then
        case "$rc" in
            2) echo "❌ ${file}: 'run.go' could not be set to ${target}" >&2 ;;
            *) echo "❌ ${file}: no 'run.go' entry found" >&2 ;;
        esac
        rm -f -- "$tmp"
        FAILED_UPDATES+=("$file")
        return 1
    fi

    # Use `cp` instead of `mv`: the mktemp file is 0600 and `mv` would carry
    # that mode over, making the original file (e.g. 0644) unreadable.
    # `cp` must be checked explicitly: this function is called from a `||`
    # list, which disables `set -e` inside the whole function body.
    if ! cp -- "$tmp" "$file"; then
        echo "❌ ${file}: could not write the updated configuration" >&2
        rm -f -- "$tmp"
        FAILED_UPDATES+=("$file")
        return 1
    fi
    rm -f -- "$tmp"

    # Re-verify the destination file, not only the temporary copy.
    if [ "$(read_linter_version "$file")" != "$target" ]; then
        echo "❌ ${file}: run.go did not take the target value" >&2
        FAILED_UPDATES+=("$file")
        return 1
    fi

    UPDATED_FILES+=("$file")
    return 0
}

# ---------------------------------------------------------------------------
# Module helpers
# ---------------------------------------------------------------------------

# Format a module without ever touching vendored third-party code.
# $1 = module directory
run_formatter() {
    local dir="$1"
    local log="${LOG_DIR}/$(log_slug "$dir").fmt.log"
    local -a files=()
    local f

    if [ "$DRY_RUN" = true ] || [ "$RUN_FORMATTER" = false ]; then
        return 0
    fi

    if command -v gofumpt > /dev/null 2>&1; then
        # `-prune` matches at every depth, so nested vendor/ and .git/
        # directories are excluded as well (a leading "./vendor/*" pattern
        # would only cover the repository root).
        while IFS= read -r -d '' f; do
            files+=("$f")
        done < <(find "$dir" \( -name .git -o -name vendor \) -prune -o \
            -type f -name '*.go' -print0)

        if [ "${#files[@]}" -eq 0 ]; then
            return 0
        fi
        # Use xargs instead of one giant call: avoids "Argument list too long"
        # with many files or very long paths.
        if ! printf '%s\0' "${files[@]}" | xargs -0 gofumpt -w > "$log" 2>&1; then
            echo "   ⚠️  gofumpt failed for ${dir} (see ${log})" >&2
            return 1
        fi
    else
        if ! ( cd "$dir" && go fmt ./... ) > "$log" 2>&1; then
            echo "   ⚠️  go fmt failed for ${dir} (see ${log})" >&2
            return 1
        fi
    fi
    return 0
}

# Bump dependencies of a single module. $1 = module directory
update_dependencies() {
    local dir="$1"
    local log="${LOG_DIR}/$(log_slug "$dir").deps.log"

    if ! ( cd "$dir" && go get -u=patch ./... && go mod tidy ) > "$log" 2>&1; then
        echo "   ❌ dependency update failed: ${dir} (see ${log})" >&2
        FAILED_DEPS+=("$dir")
        return 1
    fi
    echo "   ✓ dependencies updated: ${dir}"
    return 0
}

# Keep go.sum consistent after a `go` directive bump. A new `go` version can
# change module graph pruning, which requires `go mod tidy` even when the
# dependency bump itself was skipped (--no-deps).
tidy_module() {
    local dir="$1"
    local log="${LOG_DIR}/$(log_slug "$dir").tidy.log"

    if ! ( cd "$dir" && go mod tidy ) > "$log" 2>&1; then
        echo "   ❌ go mod tidy failed: ${dir} (see ${log})" >&2
        FAILED_DEPS+=("$dir")
        return 1
    fi
    return 0
}

# Run vet, build and test for a single module. $1 = module directory
run_checks() {
    local dir="$1" slug="" log=""

    slug="$(log_slug "$dir")"

    log="${LOG_DIR}/${slug}.vet.log"
    if ! ( cd "$dir" && go vet ./... ) > "$log" 2>&1; then
        echo "   ❌ go vet failed: ${dir} (see ${log})" >&2
        FAILED_VET+=("$dir")
    else
        echo "   ✓ go vet: ${dir}"
    fi

    log="${LOG_DIR}/${slug}.build.log"
    if ! ( cd "$dir" && go build ./... ) > "$log" 2>&1; then
        echo "   ❌ go build failed: ${dir} (see ${log})" >&2
        FAILED_BUILDS+=("$dir")
    else
        echo "   ✓ go build: ${dir}"
    fi

    log="${LOG_DIR}/${slug}.test.log"
    # -count=1 disables the test cache: after a toolchain or dependency bump
    # a cached "ok" says nothing about the current state.
    if ! ( cd "$dir" && go test -count=1 ./... ) > "$log" 2>&1; then
        echo "   ❌ go test failed: ${dir} (see ${log})" >&2
        FAILED_TESTS+=("$dir")
    else
        echo "   ✓ go test: ${dir}"
    fi
    return 0
}

# Discover all modules, skipping nested vendor directories, .git and fixture
# modules under testdata (those are intentionally broken and must not be
# vetted, built or tested). Equivalent prune expression as in
# collect_linter_files(), so nested .git dirs are excluded as well.
collect_modules() {
    local file="" dir=""
    while IFS= read -r -d '' file; do
        dir="$(dirname -- "$file")"
        MODULE_DIRS+=("$dir")
    done < <(find . \( -name .git -o -name vendor -o -name testdata \) -prune -o \
        -type f -name 'go.mod' -print0)
}

# Discover golangci-lint configuration files.
collect_linter_files() {
    local file=""
    while IFS= read -r -d '' file; do
        LINTER_FILES+=("$file")
    done < <(find . \( -name .git -o -name vendor \) -prune -o -type f \
        \( -name '.golangci.yml' -o -name '.golangci.yaml' \) -print0)
}

# Turn a project path into a safe log file name
log_slug() {
    local slug=""
    slug="$(printf '%s' "$1" | sed -e 's|^\./||' -e 's|/$||' -e 's|[/\\]|__|g')"
    case "$slug" in
        ''|'.') slug="root" ;;
    esac
    printf '%s' "$slug"
}

# Ask a yes/no question. Returns 0 for "yes".
ask() {
    local prompt="$1" answer=""

    if [ "$ASSUME_YES" = true ]; then
        echo "${prompt}yes (--yes)"
        return 0
    fi
    # Probe the terminal by actually opening it: `[ -r /dev/tty ]` only
    # checks the permission bits and succeeds in CI even when open() fails.
    # The test runs in a subshell so a redirection failure cannot exit the
    # script itself.
    if ! ( : < /dev/tty ) 2>/dev/null; then
        echo "❌ No TTY available for: $prompt" >&2
        echo "   Re-run with --yes to accept all prompts non-interactively." >&2
        return 1
    fi
    read -r -p "$prompt" answer < /dev/tty || answer=""
    [[ "$answer" =~ ^[Yy]$ ]]
}

# Refuse to mix unrelated local changes with the automated update.
check_working_tree() {
    # `git check-ignore` understands every .gitignore syntax, including
    # negations and patterns without a trailing slash.
    if command -v git > /dev/null 2>&1 \
        && git -C "$PROJECT_ROOT" rev-parse --is-inside-work-tree > /dev/null 2>&1 \
        && ! git -C "$PROJECT_ROOT" check-ignore -q -- "$LOG_DIR"; then
        echo "⚠️  ${LOG_DIR} is not git-ignored - add it to .gitignore or point UPDATE_GO_LOG_DIR outside the repository." >&2
    fi
    if ! git -C "$PROJECT_ROOT" rev-parse --is-inside-work-tree > /dev/null 2>&1; then
        return 0
    fi
    if [ -n "$(git -C "$PROJECT_ROOT" status --porcelain --untracked-files=no 2>/dev/null)" ]; then
        echo "⚠️  The working tree has uncommitted changes." >&2
        if ! ask "Continue anyway? [y/N] "; then
            echo "Aborted - commit or stash your changes first." >&2
            exit 130
        fi
    fi
    return 0
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
for arg in "$@"; do
    case "$arg" in
        -y|--yes)      ASSUME_YES=true ;;
        --no-deps)     UPDATE_DEPS=false ;;
        --no-format)   RUN_FORMATTER=false ;;
        -n|--dry-run)  DRY_RUN=true ;;
        -h|--help)     usage; exit 0 ;;
        *)
            echo "Unknown argument: $arg" >&2
            usage >&2
            exit 2
            ;;
    esac
done

cd "$PROJECT_ROOT"
mkdir -p -- "$LOG_DIR"

if [ -f "${PROJECT_ROOT}/.gitignore" ] \
    && ! grep -qxF '.update-logs/' "${PROJECT_ROOT}/.gitignore"; then
    echo "⚠️  ${LOG_DIR} is not git-ignored - consider adding '.update-logs/' to .gitignore." >&2
fi

# ---------------------------------------------------------------------------
# Preflight checks
# ---------------------------------------------------------------------------
if ! command -v go > /dev/null 2>&1; then
    echo "❌ Go toolchain not found in PATH - aborting." >&2
    exit 1
fi

LOCAL_GO_VERSION="$(go version | sed -n 's/^go version go\([0-9][0-9.]*\).*/\1/p')"
if ! [[ "$LOCAL_GO_VERSION" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
    # Without validation, version_ge "" would silently evaluate to 0.0.0
    # and the script would only warn instead of aborting.
    echo "❌ Could not determine the local Go version ('$(go version)')." >&2
    exit 1
fi

# Deliberately a distinct name: `GOTOOLCHAIN` is a Go environment variable
# and would affect every subsequent `go` invocation if exported.
GO_TOOLCHAIN_MODE="$(go env GOTOOLCHAIN 2>/dev/null || echo "auto")"
GO_TOOLCHAIN_BASE="$GO_TOOLCHAIN_MODE"
GO_TOOLCHAIN_POLICY="auto"
case "$GO_TOOLCHAIN_MODE" in
    *+*)
        GO_TOOLCHAIN_BASE="${GO_TOOLCHAIN_MODE%%+*}"
        GO_TOOLCHAIN_POLICY="${GO_TOOLCHAIN_MODE##*+}"
        ;;
esac

# ---------------------------------------------------------------------------
# Fetch the latest Go release
# ---------------------------------------------------------------------------
fetch_latest_go_version() {
    local version=""
    if command -v curl > /dev/null 2>&1; then
        # No `head` in the pipeline: `pipefail` would treat curl's SIGPIPE
        # (exit 141) as a failure, and `|| version=""` would then discard
        # an already fetched version so the fallback kicks in needlessly.
        version="$(curl -fsSL --retry 3 --max-time 15 --connect-timeout 5 \
            --proto '=https' "$VERSION_URL" 2>/dev/null \
            | sed -n '1s/^go//p')" || version=""
    fi
    if [ -z "$version" ]; then
        echo "⚠️  Could not query go.dev - falling back to the local toolchain." >&2
        version="$(go env GOVERSION 2>/dev/null | sed -n 's/^go//p')" || version=""
    fi
    printf '%s' "$version"
}

echo "Querying the latest Go release from go.dev ..."
LATEST_GO_VERSION="$(fetch_latest_go_version)"

if ! [[ "$LATEST_GO_VERSION" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
    echo "❌ Could not determine a valid Go version (got '${LATEST_GO_VERSION}')." >&2
    exit 1
fi
# Normalise to MAJOR.MINOR.PATCH: the toolchain directive requires a full
# version, i.e. `go 1.23` must yield `toolchain go1.23.0`.
LATEST_GO_VERSION="$(normalize_version "$LATEST_GO_VERSION")"

echo "Latest available Go version: ${LATEST_GO_VERSION}"
echo "Local Go version           : ${LOCAL_GO_VERSION}"

if ! version_ge "$LOCAL_GO_VERSION" "$LATEST_GO_VERSION"; then
    if toolchain_blocks_upgrade; then
        echo "❌ Local Go (${LOCAL_GO_VERSION}) is older than ${LATEST_GO_VERSION}." >&2
        echo "   GOTOOLCHAIN=${GO_TOOLCHAIN_MODE} does not allow a newer toolchain to be used." >&2
        if [ "$DRY_RUN" = true ]; then
            echo "   --dry-run given: reporting the planned changes anyway." >&2
        else
            echo "   Bumping the go directives would break every subsequent build. Aborting." >&2
            exit 1
        fi
    else
        echo "⚠️  Local Go (${LOCAL_GO_VERSION}) is older than ${LATEST_GO_VERSION}."
        echo "   GOTOOLCHAIN=${GO_TOOLCHAIN_MODE} will download the required toolchain."
    fi
fi

LINTER_TARGET="$(major_minor "$LATEST_GO_VERSION")"

if [ "$DRY_RUN" = true ]; then
    echo "------------------------------------------------------------"
    echo "DRY RUN - no file will be modified"
fi
echo "------------------------------------------------------------"

check_working_tree

if [ "$DRY_RUN" = false ] && [ "$ASSUME_YES" = false ]; then
    if ! ask "Update all go.mod/go.work/.golangci.yml files to go ${LATEST_GO_VERSION}? [y/N] "; then
        echo "Aborted."
        exit 0
    fi
fi

# ---------------------------------------------------------------------------
# Phase 1 - go.mod files (must run for every module before any build)
# ---------------------------------------------------------------------------
echo "Phase 1: go.mod directives"
echo "------------------------------------------------------------"
collect_modules
if [ "${#MODULE_DIRS[@]}" -eq 0 ]; then
    echo "   no go.mod files found"
else
    for dir in "${MODULE_DIRS[@]}"; do
        sync_modfile "${dir}/go.mod" mod || true
    done
fi

# ---------------------------------------------------------------------------
# Phase 2 - go.work (must be raised before any build in the workspace)
# ---------------------------------------------------------------------------
echo "Phase 2: go.work directives"
echo "------------------------------------------------------------"
if [ -f "$GO_WORK_FILE" ]; then
    sync_modfile "$GO_WORK_FILE" work || true
else
    echo "   no go.work found"
fi

# ---------------------------------------------------------------------------
# Phase 3 - .golangci.yml (run.go uses MAJOR.MINOR only)
# ---------------------------------------------------------------------------
echo "Phase 3: golangci-lint configuration"
echo "------------------------------------------------------------"
collect_linter_files
if [ "${#LINTER_FILES[@]}" -eq 0 ]; then
    echo "   no .golangci.yml files found"
else
    for file in "${LINTER_FILES[@]}"; do
        write_linter_version "$file" "$LINTER_TARGET" || true
    done
fi

# ---------------------------------------------------------------------------
# Phase 4 - dependencies
# ---------------------------------------------------------------------------
echo "Phase 4: dependencies"
echo "------------------------------------------------------------"
if [ "$DRY_RUN" = true ]; then
    echo "   skipped (--dry-run)"
elif [ "${#MODULE_DIRS[@]}" -eq 0 ]; then
    echo "   no modules found"
else
    for dir in "${MODULE_DIRS[@]}"; do
        if [ "$UPDATE_DEPS" = true ]; then
            update_dependencies "$dir" || true
        else
            echo "   dependency bump skipped (--no-deps): ${dir}"
            tidy_module "$dir" || true
        fi
    done
fi

# ---------------------------------------------------------------------------
# Phase 5 - formatting and verification
# ---------------------------------------------------------------------------
echo "Phase 5: format, vet, build and test"
echo "------------------------------------------------------------"
if [ "$DRY_RUN" = true ]; then
    echo "   skipped (--dry-run)"
elif [ "${#MODULE_DIRS[@]}" -eq 0 ]; then
    echo "   no modules found"
else
    for dir in "${MODULE_DIRS[@]}"; do
        run_formatter "$dir" || true
        run_checks "$dir"
    done
fi

# ---------------------------------------------------------------------------
# Summary - a non-empty failure list must produce a non-zero exit code,
# otherwise the script is useless in CI.
# ---------------------------------------------------------------------------
failures=0

echo "============================================================"
echo "Summary"
echo "============================================================"
echo "Target Go version:   ${LATEST_GO_VERSION}"
echo "golangci-lint go:    ${LINTER_TARGET}"
if [ "$DRY_RUN" = true ]; then
    echo "Mode:                dry run"
fi
echo "Updates planned/applied: ${#UPDATED_FILES[@]}"

if [ "${#FAILED_UPDATES[@]}" -gt 0 ]; then
    echo "❌ Version updates failed (${#FAILED_UPDATES[@]}):"
    for entry in "${FAILED_UPDATES[@]}"; do
        echo "   - ${entry}"
    done
    failures=$((failures + ${#FAILED_UPDATES[@]}))
fi
if [ "${#FAILED_DEPS[@]}" -gt 0 ]; then
    echo "❌ Dependency updates failed (${#FAILED_DEPS[@]}):"
    for entry in "${FAILED_DEPS[@]}"; do
        echo "   - ${entry}"
    done
    failures=$((failures + ${#FAILED_DEPS[@]}))
fi
if [ "${#FAILED_VET[@]}" -gt 0 ]; then
    echo "❌ go vet failed (${#FAILED_VET[@]}):"
    for entry in "${FAILED_VET[@]}"; do
        echo "   - ${entry}"
    done
    failures=$((failures + ${#FAILED_VET[@]}))
fi
if [ "${#FAILED_BUILDS[@]}" -gt 0 ]; then
    echo "❌ go build failed (${#FAILED_BUILDS[@]}):"
    for entry in "${FAILED_BUILDS[@]}"; do
        echo "   - ${entry}"
    done
    failures=$((failures + ${#FAILED_BUILDS[@]}))
fi
if [ "${#FAILED_TESTS[@]}" -gt 0 ]; then
    echo "❌ go test failed (${#FAILED_TESTS[@]}):"
    for entry in "${FAILED_TESTS[@]}"; do
        echo "   - ${entry}"
    done
    failures=$((failures + ${#FAILED_TESTS[@]}))
fi

echo "Logs: ${LOG_DIR}"

if [ "$failures" -gt 0 ]; then
    echo "Result: FAILED (${failures} problem(s))"
    exit 1
fi

if [ "$DRY_RUN" = true ]; then
    echo "Result: OK - dry run, nothing was written"
else
    echo "Result: OK"
fi
exit 0