# Database & Data Directory

## Database Configuration

This project uses **MySQL 8.0** configured in `dev-config/local.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/hybris_mcp?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8
db.driver=com.mysql.cj.jdbc.Driver
db.username=hybris
db.password=hybris
```

The MySQL JDBC driver is declared as a Gradle dependency in `build.gradle`:
```groovy
dbDriver 'com.mysql:mysql-connector-j:8.2.0'
```

It is downloaded automatically during `bootstrapPlatform` into `hybris/bin/platform/lib/dbdriver/`.

### Setting Up MySQL

**Docker (easiest):**
```bash
docker run -d \
  --name hybris-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=hybris_mcp \
  -e MYSQL_USER=hybris \
  -e MYSQL_PASSWORD=hybris \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

**Native MySQL:**
```sql
CREATE DATABASE hybris_mcp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'hybris'@'%' IDENTIFIED BY 'hybris';
GRANT ALL PRIVILEGES ON hybris_mcp.* TO 'hybris'@'%';
FLUSH PRIVILEGES;
```

## Where Runtime Data Lives

Runtime data is stored in `core-customize/hybris/data/` (not in git):

```
hybris/data/
├── acceleratorservices/  # Accelerator runtime data
├── media/               # Uploaded media (product images, documents)
│   └── sys_master/      # Main tenant media
├── build.number         # Build metadata
├── hybristomcat.java.pid # Process ID of the running server
├── hybristomcat.java.id  # Server instance ID
└── shutdown.token       # Server shutdown token
```

The path is controlled by the platform's `HYBRIS_DATA_DIR` property, which defaults to `${HYBRIS_HOME}/data`.

## Database Operations

| Operation | Command |
|-----------|---------|
| Full reset (drop + recreate + load all data) | `./gradlew yinitialize` |
| Schema update (add new columns/tables, preserve data) | `./gradlew yupdatesystem` |
| Clean build artifacts | `./gradlew cleanAll` |

### Resetting Everything

```bash
./gradlew cleanAll          # Wipe hybris/config, hybris/data, hybris/bin/platform+modules
./gradlew bootstrapPlatform # Unpack ZIPs, install drivers, generate config
./gradlew yclean yall       # Build
./gradlew yinitialize       # Initialize database + load sample data
```

## Sample Data Layout

All demo data lives in `hybris/bin/custom/sampledatamcp/resources/impex/`, named
with numeric prefixes that control alphabetical load order (see ADR 0006):
`essentialdata-NN-*.impex` (infrastructure, Solr configs — re-imported on every
initialize/update) and `projectdata-NN-*.impex` (products, media, categories,
customers, orders, knowledge — **initialize only**; import new files onto an
existing DB via `./gradlew impex -Pfile=...`). The per-file inventory is in
`sampledatamcp/docs/sampledatamcp/sample-data/components.md`.

## Media Files

Product images and documents are stored in `hybris/data/media/sys_master/`. They are loaded during initialization from `projectdata-30-product-media.impex` in the `sampledatamcp` extension.

Configured in platform defaults:
```properties
media.replication.dirs=${HYBRIS_DATA_DIR}/media
media.read.dir=${HYBRIS_DATA_DIR}/media
```
