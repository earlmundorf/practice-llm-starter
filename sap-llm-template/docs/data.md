# Local Database & Data Directory

## Where the Data Lives

All runtime data is stored outside the hybris repo root in a sibling `data/` directory:

```
core/
├── hybris/          ← repo root (git-tracked)
│   └── bin/platform/  ← where ${platformhome} points
└── data/            ← runtime data (NOT in git)
    ├── hsqldb/          # Database files
    ├── media/           # Uploaded media (images, documents)
    ├── acceleratorservices/  # Accelerator runtime data
    ├── build.number     # Build metadata
    ├── hybristomcat.*   # Tomcat PID/lock files
    └── shutdown.token   # Server shutdown token
```

## How the Location Is Determined

The path resolution follows a chain:

### 1. `env.properties` sets the default

`bin/platform/env.properties` defines `HYBRIS_DATA_DIR` relative to `${platformhome}` (which is `bin/platform/`):

```properties
HYBRIS_DATA_DIR=${platformhome}/../../data
```

Since `platformhome` = `hybris/bin/platform`, this resolves to `core/data/` — two levels up from platform, one level up from the hybris repo root.

### 2. Environment variable can override

The Ant build system (`bin/platform/resources/ant/util.xml`) checks for an environment variable first:

```xml
<condition property="HYBRIS_DATA_DIR" value="${env.HYBRIS_DATA_DIR}">
    <isset property="env.HYBRIS_DATA_DIR" />
</condition>
```

If `HYBRIS_DATA_DIR` is set as a shell environment variable, it takes precedence over `env.properties`.

### 3. Tomcat wrapper passes it as a JVM property

When the server starts, the wrapper config passes the resolved path to the JVM:

```
-DHYBRIS_DATA_DIR="/Users/emundorf/development/mundo/training/sap_commerce/core/data"
```

### Resolution order

1. Shell environment variable `HYBRIS_DATA_DIR` (if set)
2. `active-role-env.properties` (if exists, for role-based deployments)
3. `env.properties` default: `${platformhome}/../../data`

## The HSQLDB Database

### How Hybris Knows It's HSQLDB

The database driver and connection URL are configured in `config/local.properties`:

```properties
db.url=jdbc:hsqldb:file:${HYBRIS_DATA_DIR}/hsqldb/hybris;hsqldb.lock_file=false;shutdown=true
db.driver=org.hsqldb.jdbcDriver
db.username=sa
db.password=
```

The platform also has a fallback default in `bin/platform/project.properties`:

```properties
db.url=jdbc:hsqldb:file:${HYBRIS_DATA_DIR}/hsqldb/mydb;shutdown=true;hsqldb.tx=MVCC;hsqldb.log_size=256
db.driver=org.hsqldb.jdbcDriver
db.username=sa
db.password=
```

The `local.properties` values override the platform defaults. The `db.driver` class (`org.hsqldb.jdbcDriver`) tells the platform to use HSQLDB, and the `jdbc:hsqldb:file:` URL prefix tells HSQLDB to use file-based persistence.

To switch to a different database (e.g., MySQL, PostgreSQL), you would change `db.driver`, `db.url`, `db.username`, and `db.password` in `local.properties`.

### Database Files

The HSQLDB files live at `core/data/hsqldb/`:

| File | Purpose | Size (approx) |
|------|---------|---------------|
| `hybris.script` | DDL statements + cached row data (SQL text format) | ~1 MB |
| `hybris.data` | Table data (binary format) | ~470 MB |
| `hybris.log` | Transaction log since last checkpoint | ~8 MB |
| `hybris.properties` | HSQLDB runtime configuration | tiny |
| `hybris.backup` | Backup of `.script` before last checkpoint | ~650 KB |
| `hybris.tmp/` | Temporary working files | varies |

### How It Works

HSQLDB is a pure-Java embedded database. In file mode:

- **On startup**, HSQLDB reads `hybris.script` (DDL + small data) and `hybris.data` (bulk data) into memory
- **During operation**, changes are appended to `hybris.log`
- **On checkpoint/shutdown**, the log is merged back into `.script` and `.data`, and `.backup` is updated
- The `shutdown=true` JDBC parameter ensures a clean shutdown when the last connection closes

### When It Gets Created

The database files are created by `ant initialize`, which:
1. Drops and recreates the schema (DDL from the type system)
2. Loads essential data from `resources/impex/essentialdata_*.impex` files
3. Loads project data from `resources/impex/projectdata_*.impex` files

The generated SQL for initialization is cached in `hybris/temp/hybris/`:
- `init_master_schema.sql` — CREATE TABLE statements
- `init_master_data.sql` — INSERT statements for essential/project data
- `init_master_drop_schema.sql` — DROP TABLE statements

### Resetting the Database

```bash
# Full reset — drops all tables, recreates schema, reloads all data
ant clean all initialize

# Schema update only — adds new columns/tables, preserves existing data
ant updatesystem
```

## Other Data Directory Contents

### `media/`

Uploaded media files (product images, documents, etc.), organized by tenant:
- `sys_master/` — main tenant media
- `sys_junit/` — JUnit test tenant media

Configured via `advanced.properties`:
```properties
media.replication.dirs=${HYBRIS_DATA_DIR}/media
media.read.dir=${HYBRIS_DATA_DIR}/media
```

### Tomcat Runtime Files

- `hybristomcat.java.pid` — Process ID of the running server
- `hybristomcat.java.id` — Server instance ID
- `hybristomcat.lock` — Lock file preventing multiple instances
- `shutdown.token` — Token for graceful shutdown
