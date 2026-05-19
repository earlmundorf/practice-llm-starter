#!/bin/bash
# setup-local.sh — Set up local development by symlinking the SAP Commerce platform.
#
# Requires HYBRIS_HOME environment variable pointing to an existing SAP Commerce
# installation directory (the one containing bin/platform/ and bin/modules/).
#
# Environment variables:
#   HYBRIS_HOME       (required) Path to SAP Commerce installation
#   REQUIRED_JAVA     Minimum Java version (default: 17)
#   MYSQL_ROOT_USER   MySQL admin user (default: root)
#   MYSQL_ROOT_PASS   MySQL admin password (default: root)
#
# Example:
#   export HYBRIS_HOME=/path/to/hybris
#   ./local/scripts/setup-local.sh
#
# Or inline:
#   HYBRIS_HOME=/path/to/hybris ./local/scripts/setup-local.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
HYBRIS_BIN="$PROJECT_ROOT/core-customize/hybris/bin"

err() { echo "ERROR: $*" >&2; }
warn() { echo "WARNING: $*" >&2; }

# --- Validate HYBRIS_HOME ---
if [ -z "$HYBRIS_HOME" ]; then
    err "HYBRIS_HOME environment variable is not set."
    echo "" >&2
    echo "Set it to your SAP Commerce installation directory:" >&2
    echo "  export HYBRIS_HOME=/path/to/hybris" >&2
    echo "" >&2
    echo "The directory should contain bin/platform/ and bin/modules/." >&2
    exit 1
fi

if [ ! -d "$HYBRIS_HOME/bin/platform" ]; then
    err "$HYBRIS_HOME/bin/platform does not exist."
    echo "HYBRIS_HOME should point to the hybris/ directory containing bin/platform/." >&2
    exit 1
fi

if [ ! -d "$HYBRIS_HOME/bin/modules" ]; then
    err "$HYBRIS_HOME/bin/modules does not exist."
    echo "HYBRIS_HOME should point to the hybris/ directory containing bin/modules/." >&2
    exit 1
fi

# --- Check prerequisites ---
REQUIRED_JAVA="${REQUIRED_JAVA:-17}"
if ! command -v java &>/dev/null; then
    err "java not found. SAP Commerce requires Java $REQUIRED_JAVA+ (SAP JDK recommended)."
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | head -1 | sed -n 's/.*"\([0-9]*\)\..*/\1/p')
if [ -z "$JAVA_VER" ]; then
    warn "Could not determine Java version. Ensure Java $REQUIRED_JAVA+ is installed."
elif [ "$JAVA_VER" -lt "$REQUIRED_JAVA" ]; then
    err "Java $REQUIRED_JAVA+ required, found Java $JAVA_VER."
    exit 1
fi

# --- Create symlinks ---
echo "Setting up local development environment..."
echo "  HYBRIS_HOME: $HYBRIS_HOME"
echo "  Java:        $(java -version 2>&1 | head -1)"
echo "  Target:      $HYBRIS_BIN"
echo ""

# Platform
if [ -L "$HYBRIS_BIN/platform" ]; then
    echo "  platform/  — symlink already exists, replacing"
    rm "$HYBRIS_BIN/platform"
elif [ -d "$HYBRIS_BIN/platform" ]; then
    err "$HYBRIS_BIN/platform exists as a real directory. Remove it first."
    exit 1
fi
ln -s "$HYBRIS_HOME/bin/platform" "$HYBRIS_BIN/platform"
echo "  platform/  → $HYBRIS_HOME/bin/platform"

# Modules
if [ -L "$HYBRIS_BIN/modules" ]; then
    echo "  modules/   — symlink already exists, replacing"
    rm "$HYBRIS_BIN/modules"
elif [ -d "$HYBRIS_BIN/modules" ]; then
    err "$HYBRIS_BIN/modules exists as a real directory. Remove it first."
    exit 1
fi
ln -s "$HYBRIS_HOME/bin/modules" "$HYBRIS_BIN/modules"
echo "  modules/   → $HYBRIS_HOME/bin/modules"

# Config — symlink project config into the platform so it's found at runtime.
CONFIG_SRC="$PROJECT_ROOT/core-customize/hybris/config"
CONFIG_TARGET="$HYBRIS_HOME/config"
if [ -L "$CONFIG_TARGET" ]; then
    echo "  config/    — symlink already exists, replacing"
    rm "$CONFIG_TARGET"
elif [ -d "$CONFIG_TARGET" ]; then
    warn "$CONFIG_TARGET is a real directory — skipping (remove or rename it manually to link)"
fi
if [ ! -e "$CONFIG_TARGET" ]; then
    ln -s "$CONFIG_SRC" "$CONFIG_TARGET"
    echo "  config/    → $CONFIG_SRC"
fi

# Custom extensions — symlink each extension from bin/custom/ into the platform's
# bin/custom/ so ${HYBRIS_BIN_DIR}/custom/ resolves correctly during ant builds.
if [ ! -w "$HYBRIS_HOME/bin" ]; then
    warn "$HYBRIS_HOME/bin is not writable — skipping reverse symlinks for custom extensions."
    echo "  You may need to create these manually or run with appropriate permissions." >&2
else
    mkdir -p "$HYBRIS_HOME/bin/custom"
    echo ""
    for ext_dir in "$HYBRIS_BIN/custom"/*/; do
        ext_name=$(basename "$ext_dir")
        target="$HYBRIS_HOME/bin/custom/$ext_name"
        if [ -L "$target" ]; then
            echo "  custom/$ext_name — symlink already exists, replacing"
            rm "$target"
        elif [ -d "$target" ]; then
            warn "$target is a real directory — skipping (remove it manually to link)"
            continue
        fi
        ln -s "$(cd "$ext_dir" && pwd)" "$target"
        echo "  custom/$ext_name → $(cd "$ext_dir" && pwd)"
    done
fi

# --- Copy demo licence from platform templates ---
LICENCE_DIR="$PROJECT_ROOT/core-customize/hybris/config/licence"
PLATFORM_LICENCE="$HYBRIS_HOME/bin/platform/resources/configtemplates/develop/licence/hybrislicence.jar"
if [ ! -f "$LICENCE_DIR/hybrislicence.jar" ]; then
    if [ -f "$PLATFORM_LICENCE" ]; then
        mkdir -p "$LICENCE_DIR"
        cp "$PLATFORM_LICENCE" "$LICENCE_DIR/"
        echo "  hybrislicence.jar → copied from platform develop template"
    else
        echo ""
        echo "  NOTE: No SAP licence file found and none in platform templates."
        echo "        The platform will run in demo mode. Place your licence file at config/licence/hybrislicence.jar"
    fi
else
    echo "  hybrislicence.jar — already exists"
fi
# --- Create MySQL database if it doesn't exist ---
MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"
MYSQL_ROOT_PASS="${MYSQL_ROOT_PASS:-root}"
MYSQL_CONTAINER="hybris-mysql"

DB_NAME=$(grep '^db.url=' "$PROJECT_ROOT/core-customize/hybris/config/local.properties" | sed 's|.*/\([a-zA-Z0-9_]*\).*|\1|')

# Helper: run a mysql command via docker exec (preferred when container is running) or local client
run_mysql() {
    if command -v docker &>/dev/null && docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
        docker exec "$MYSQL_CONTAINER" mysql -u "$MYSQL_ROOT_USER" -p"$MYSQL_ROOT_PASS" -e "$1" 2>/dev/null
    elif command -v mysql &>/dev/null; then
        MYSQL_PWD="$MYSQL_ROOT_PASS" mysql -h 127.0.0.1 -u "$MYSQL_ROOT_USER" -e "$1" 2>/dev/null
    else
        return 1
    fi
}

if [ -n "$DB_NAME" ]; then
    echo ""

    # Start MySQL Docker container if no local mysql client and Docker is available
    if ! command -v mysql &>/dev/null && command -v docker &>/dev/null; then
        if docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
            echo "  Docker container '$MYSQL_CONTAINER' already running."
        elif docker ps -a --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
            echo "  Starting existing Docker container '$MYSQL_CONTAINER'..."
            docker start "$MYSQL_CONTAINER"
            echo "  Waiting for MySQL to be ready..."
            sleep 5
        else
            echo "  Starting MySQL Docker container '$MYSQL_CONTAINER'..."
            docker run -d --name "$MYSQL_CONTAINER" -p 3306:3306 -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASS" mysql:8
            echo "  Waiting for MySQL to be ready..."
            sleep 10
        fi
    fi

    echo "Creating MySQL database '$DB_NAME' (if it doesn't exist)..."
    if run_mysql "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"; then
        echo "  database/$DB_NAME — created"
    else
        warn "Could not create database '$DB_NAME' (user: $MYSQL_ROOT_USER). Set MYSQL_ROOT_USER/MYSQL_ROOT_PASS env vars or create it manually:"
        echo "    CREATE DATABASE $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >&2
    fi
    if run_mysql "CREATE USER IF NOT EXISTS 'hybris'@'%' IDENTIFIED BY 'hybris';" \
        && run_mysql "GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO 'hybris'@'%'; FLUSH PRIVILEGES;"; then
        echo "  user/hybris — ready"
    else
        warn "Could not create user or grant privileges. Create manually:"
        echo "    CREATE USER IF NOT EXISTS 'hybris'@'%' IDENTIFIED BY 'hybris';" >&2
        echo "    GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO 'hybris'@'%';" >&2
    fi
fi

# --- Copy MySQL JDBC driver ---
DBDRIVER_DIR="$HYBRIS_HOME/bin/platform/lib/dbdriver"
MYSQL_JAR="$PROJECT_ROOT/local/lib/mysql-connector-j-8.2.0.jar"
if [ -f "$MYSQL_JAR" ]; then
    mkdir -p "$DBDRIVER_DIR"
    cp "$MYSQL_JAR" "$DBDRIVER_DIR/"
    echo "  mysql-connector-j-8.2.0.jar → $DBDRIVER_DIR/"
else
    warn "MySQL JDBC driver not found at $MYSQL_JAR"
fi

# --- Build the platform ---
echo ""
echo "Running ant all..."
cd "$HYBRIS_HOME/bin/platform" && . ./setantenv.sh && ant all -Dinput.template=develop
if [ $? -ne 0 ]; then
    err "ant all failed."
    exit 1
fi

echo ""
echo "Done. Next steps:"
echo "  ./local/scripts/initialize-server.sh    # Initialize and start (destroys data)"
echo "  ./local/scripts/index-solr.sh           # Index the product catalog"
