# Getting Started — Local Development

This guide walks you through setting up a local SAP Commerce development environment from scratch using the Gradle build system.

## 1. System Requirements

### Java 17

SAP Commerce 22.11 requires Java 17. The project uses SDKMAN to manage the JDK — the path is configured in `core-customize/gradle.properties`.

**Check your version:**
```bash
java -version
```

**Install via SDKMAN:**
```bash
sdk install java 17.0.12-oracle
```

If you use a different Java 17 distribution, update the path in `core-customize/gradle.properties`:
```properties
org.gradle.java.home=/path/to/java/17
```

### MySQL 8.0

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

The database name is configured in `dev-config/local.properties` under `db.url`.

### Ports

| Port | Service |
|------|---------|
| 9001 | HTTP (HAC, Backoffice) |
| 9002 | HTTPS (OCC API) |
| 8983 | Solr (embedded) |

### Disk and Memory

- **Disk:** ~10 GB for the platform ZIP, ~5 GB for build artifacts and data
- **RAM:** JVM configured for 2–6 GB. Machine should have at least 16 GB total.

## 2. Obtain the SAP Commerce Platform

Download from the [SAP Software Download Center](https://launchpad.support.sap.com/#/softwarecenter):

- **SAP Commerce Suite** (e.g., `CXCOMCL221100U_38-70007431.ZIP`)
- **Integration Extension Pack** (e.g., `CXCOMIEP221100U_33-70007891.ZIP`)

Place both in `core-customize/dependencies/` and rename to Maven convention:

```bash
cd core-customize/dependencies/
cp CXCOMCL221100U_38-70007431.ZIP hybris-commerce-suite-2211.38.zip
cp CXCOMIEP221100U_33-70007891.ZIP hybris-commerce-integrations-2211.33.zip
```

The version numbers must match:
- **Suite version** → `commerceSuiteVersion` in `manifest.json`
- **Integration pack version** → `intExtPackVersion` in `build.gradle`

> **Version pin (June 2026):** `manifest.json` is deliberately pinned to **2211.38**
> to match the suite ZIP used for local development, so local and CCv2 cloud builds
> compile against the same patch. When upgrading, bump `manifest.json` and the local
> ZIP together. Note SAP's Java 21 framework update deadline (no new Java-17 builds
> after 2026-08-31) — the next version bump should be to a `2211-jdk21` line.

## 3. Bootstrap, Build, and Initialize

All commands run from the `core-customize/` directory.

```bash
cd core-customize

# Unpack platform ZIPs, install MySQL driver, generate config
./gradlew bootstrapPlatform

# Build all extensions
./gradlew yclean yall

# Initialize database (creates schema, loads sample data)
./gradlew yinitialize
```

**What `bootstrapPlatform` does:**
1. Unpacks `hybris-commerce-suite` and `hybris-commerce-integrations` ZIPs into `hybris/bin/`
2. Downloads MySQL JDBC driver into `hybris/bin/platform/lib/dbdriver/`
3. Runs `createDefaultConfig` (generates `hybris/config/` with Solr, Tomcat defaults)
4. Overlays `dev-config/` onto `hybris/config/` (your properties, extensions, etc.)

## 4. Start and Set Up

```bash
# Start the server
./gradlew startServer

# Wait ~30 seconds for full startup, then index Solr
./scripts/index-solr.sh

# Create promotions and publish to Drools
./scripts/setup-promotions.sh
./gradlew groovy -Pfile=scripts/publish-promotions.groovy -Pcommit=true
```

## 5. Verify Everything Works

### OCC API

**Product search:**
```bash
curl -sk 'https://localhost:9002/occ/v2/electronics/products/search?query=laptop'
```

**Get an OAuth token:**
```bash
curl -sk -X POST https://localhost:9002/authorizationserver/oauth/token \
  -d 'client_id=trusted_client&client_secret=secret&grant_type=password&username=john.doe@thinkshop.com&password=1234'
```

### Admin Console (HAC)

https://localhost:9002/hac — Credentials: admin / nimda

### Backoffice

https://localhost:9002/backoffice — Credentials: admin / nimda

## 6. Day-to-Day Development

| What Changed | Command |
|-------------|---------|
| Java source code | `./gradlew yclean yall` then `./gradlew stopServer startServer` |
| `*-items.xml` or `*-beans.xml` | `./gradlew yclean yall` then `./gradlew yupdatesystem` |
| Quick restart | `./gradlew stopServer startServer` |
| Full data reset | `./gradlew cleanAll bootstrapPlatform yclean yall yinitialize` |
| ImpEx import | `./gradlew impex -Pfile=path/to/data.impex` |
| FlexibleSearch query | `./gradlew flexquery -Pfile="SELECT {pk} FROM {Product}"` |
| Groovy script | `./gradlew groovy -Pfile=path/to/script.groovy -Pcommit=true` |
| View logs | `./gradlew serverLog` or `./gradlew solrLog` or `./gradlew allLogs` |

## 7. Configuration

### dev-config/

Project-specific configuration files checked into git. These are overlaid onto `hybris/config/` during `bootstrapPlatform` or `setupConfig`:

| File | Purpose |
|------|---------|
| `local.properties` | Database, ports, JVM, CORS, logging |
| `local-dev.properties` | Development persona overrides |
| `local-stg.properties` | Staging persona overrides |
| `local-prod.properties` | Production persona overrides |
| `localextensions.xml` | Extension list |

### What NOT to check in

- `core-customize/hybris/` — generated by bootstrap
- `core-customize/dependencies/` — SAP-licensed ZIP files
- `core-customize/build/` — Gradle build output

### Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `HAC_URL` | `https://localhost:9002` | HAC console URL for scripts |
| `HAC_USER` | `admin` | HAC console username |
| `HAC_PASS` | `nimda` | HAC console password |
| `OPENAI_API_KEY` | *(none)* | OpenAI API key for MCP agent service |

## 8. Clean Rebuild

To start completely fresh:

```bash
./gradlew cleanAll          # Wipe everything except hybris/bin/custom
./gradlew bootstrapPlatform # Unpack ZIPs + config
./gradlew yclean yall       # Build
./gradlew yinitialize       # Init DB
./gradlew startServer       # Start
./scripts/index-solr.sh     # Index
./scripts/setup-promotions.sh
./gradlew groovy -Pfile=scripts/publish-promotions.groovy -Pcommit=true
```

## Troubleshooting

### Product search returns 0 results

Solr hasn't been indexed. Run `./scripts/index-solr.sh` with the server running.

### "Base site doesn't exist"

The base site is `electronics`:
```bash
curl -sk https://localhost:9002/occ/v2/electronics/products/search?query=laptop
```

### MySQL driver not found

Run `./gradlew bootstrapPlatform` — it downloads the MySQL driver automatically via the `dbDriver` dependency in `build.gradle`.

### Wrong Java version

Update `core-customize/gradle.properties` to point to your Java 17 installation.

### macOS: Backoffice fails with SafeZipEntry errors

The `local.properties` includes the fix: `-Djava.io.tmpdir=/private/tmp` in `tomcat.generaloptions`.

### Server hangs or runs out of memory

Check JVM settings in `dev-config/local.properties`:
```properties
tomcat.generaloptions=-Xms2g -Xmx6g -XX:+UseG1GC -Djava.io.tmpdir=/private/tmp
```
