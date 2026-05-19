# Getting Started — Local Development

This guide walks you through setting up a local SAP Commerce development environment from scratch. Follow every step in order.

## 1. System Requirements

### Java 17 or 21

SAP Commerce 22.11 requires Java 17 at minimum. Java 21 may work depending on your platform patch level.

**Check your version:**
```bash
java -version
```

If you need to install or switch versions, [SAP JDK](https://tools.hana.ondemand.com/#cloud) (SapMachine) is recommended. Alternatives: Eclipse Temurin, Amazon Corretto, or any OpenJDK 17+ distribution.

**macOS (Homebrew):**
```bash
brew install --cask temurin@17
```

The `setup-local.sh` script validates your Java version on each run. Override the minimum with:
```bash
REQUIRED_JAVA=21 ./local/scripts/setup-local.sh
```

### MySQL 8.0 (recommended) or HSQLDB (zero-config fallback)

The project is configured for MySQL by default. If you don't want to set up MySQL, you can skip this section — comment out the `db.*` lines in `local.properties` and SAP Commerce will fall back to embedded HSQLDB (fine for development, not suitable for performance testing or production-like data volumes).

**Docker (easiest):**
```bash
docker run -d \
  --name hybris-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=hybris_llm \
  -e MYSQL_USER=hybris \
  -e MYSQL_PASSWORD=hybris \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

**Native MySQL:** Install MySQL 8.0 via your package manager, then create the database:
```sql
CREATE DATABASE hybris_llm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'hybris'@'%' IDENTIFIED BY 'hybris';
GRANT ALL PRIVILEGES ON hybris_llm.* TO 'hybris'@'%';
FLUSH PRIVILEGES;
```

The database name is configured in `core-customize/hybris/config/local.properties` under `db.url`. Change it there if needed.

### Ports

The following ports must be available:

| Port | Service | Configurable in |
|------|---------|-----------------|
| 9001 | HTTP (HAC, Backoffice) | `local.properties` → `tomcat.http.port` |
| 9002 | HTTPS (OCC API) | `local.properties` → `tomcat.ssl.port` |
| 8983 | Solr (embedded) | Platform default |

### Disk and Memory

- **Disk:** ~10 GB for the platform download, ~5 GB for build artifacts and data
- **RAM:** The JVM is configured for 2–6 GB (`-Xms2g -Xmx6g` in `local.properties`). Your machine should have at least 16 GB total.

## 2. Obtain the SAP Commerce Platform

SAP Commerce is not open source. You need access to the [SAP Software Download Center](https://launchpad.support.sap.com/#/softwarecenter) or an internal artifact repository.

**What to download:**
- SAP Commerce 2211 (the ZIP named something like `CXCOMM2211*.ZIP`)

**Extract it** to a location outside this project — for example:
```bash
mkdir -p ~/sap-commerce
unzip CXCOMM221100*.ZIP -d ~/sap-commerce
```

After extraction, you should have a directory structure like:
```
~/sap-commerce/hybris/
├── bin/
│   ├── platform/       <-- this must exist
│   └── modules/        <-- this must exist
├── ...
```

The path to this `hybris/` directory is what you'll set as `HYBRIS_HOME`.

### SAP Licence File (optional)

SAP Commerce runs in demo mode without a licence file. Demo mode has no time limit but restricts some features. If you have a licence:

```bash
mkdir -p core-customize/hybris/config/licence
cp /path/to/hybrislicence.jar core-customize/hybris/config/licence/
```

The `setup-local.sh` script will remind you if no licence is found.

## 3. Clone and Link

```bash
git clone <this-repo-url>
cd <project-directory>
```

Set `HYBRIS_HOME` and run the setup script:

```bash
export HYBRIS_HOME=~/sap-commerce/hybris
./local/scripts/setup-local.sh
```

**What `setup-local.sh` does:**
1. Validates `HYBRIS_HOME` contains `bin/platform/` and `bin/modules/`
2. Checks Java version (configurable via `REQUIRED_JAVA` env var, default 17)
3. Creates symlinks: `core-customize/hybris/bin/platform` -> your platform, and `bin/modules` -> your modules
4. Creates reverse symlinks: your platform's `bin/custom/` -> this project's extensions
5. Checks for the SAP licence file (non-blocking warning if missing)
6. Creates the MySQL database if the `mysql` client is available (credentials via `MYSQL_ROOT_USER`/`MYSQL_ROOT_PASS`, default `root`/`root`)

**Expected output:**
```
Setting up local development environment...
  HYBRIS_HOME: /Users/you/sap-commerce/hybris
  Java:        openjdk version "17.0.2" 2022-01-18
  Target:      /path/to/project/core-customize/hybris/bin

  platform/  -> /Users/you/sap-commerce/hybris/bin/platform
  modules/   -> /Users/you/sap-commerce/hybris/bin/modules

  custom/<extension> -> /path/to/project/core-customize/hybris/bin/custom/<extension>

Done. Next steps:
  ./local/scripts/initialize-server.sh    # Build, initialize, and start (destroys data)
  ./local/scripts/index-solr.sh           # Index the product catalog
```

## 4. Initialize and Start

The `initialize-server.sh` script does everything: builds all extensions, initializes the database with all ImpEx data, and starts the server.

```bash
./local/scripts/initialize-server.sh --force
```

This takes 5-10 minutes. It will:
1. Stop any running server
2. Run `ant clean all` (compile all extensions)
3. Run `ant initialize` (create database schema, load all `essentialdata-*.impex` and `projectdata-*.impex` files)
4. Start the server
5. Print `STARTED` when Tomcat is ready, or `ERROR: <reason>` if something failed

**Do not use `--force` in subsequent runs** unless you want to wipe all data. Without `--force`, the script prompts for confirmation.

## 5. Index Solr

After initialization, the product catalog exists in the database but Solr hasn't indexed it yet. Product search will return empty results until you run:

```bash
./local/scripts/index-solr.sh
```

Expected output:
```
=== INDEXING SOLR ===
[commit mode]
OK: Solr Config for Backoffice
OK: Solr Config for Backoffice Visibility Product
OK: thinkshopIndex
=== DONE ===
```

## 6. Verify Everything Works

### Admin Console (HAC)

Open http://localhost:9001/hac in your browser.
- **Credentials:** admin / nimda
- Navigate to Console -> FlexibleSearch and run: `SELECT {code} FROM {Product}`
- You should see product codes (Staged + Online catalog versions)

### OCC API

**Product detail:**
```bash
curl -sk https://localhost:9002/occ/v2/electronics/products/LAPTOP_PRO_15
```

**Product search:**
```bash
curl -sk 'https://localhost:9002/occ/v2/electronics/products/search?query=laptop'
```

**Get an OAuth token:**
```bash
curl -sk -X POST https://localhost:9002/authorizationserver/oauth/token \
  -d 'client_id=trusted_client&client_secret=secret&grant_type=password&username=john.doe@thinkshop.com&password=1234'
```

**Authenticated request (order history):**
```bash
TOKEN=$(curl -sk -X POST https://localhost:9002/authorizationserver/oauth/token \
  -d 'client_id=trusted_client&client_secret=secret&grant_type=password&username=john.doe@thinkshop.com&password=1234' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -sk -H "Authorization: Bearer $TOKEN" \
  'https://localhost:9002/occ/v2/electronics/users/john.doe@thinkshop.com/orders'
```

### Backoffice

Open http://localhost:9001/backoffice in your browser.
- **Credentials:** admin / nimda
- Navigate to Catalog -> Products to browse the product catalog

## 7. Environment Variables Reference

All environment variables are optional — defaults work for standard local development.

| Variable | Default | Purpose |
|----------|---------|---------|
| `HYBRIS_HOME` | *(required for setup)* | Path to SAP Commerce installation (`hybris/` directory) |
| `REQUIRED_JAVA` | `17` | Minimum Java version enforced by `setup-local.sh` |
| `HYBRIS_CONFIG_DIR` | *(auto-detected)* | Override config directory (needed when running `ant` directly outside scripts) |
| `HYBRIS_DATA_DIR` | `${HYBRIS_CONFIG_DIR}/../data` | Isolate data per project when sharing a platform |
| `MYSQL_ROOT_USER` | `root` | MySQL admin user for `setup-local.sh` database creation |
| `MYSQL_ROOT_PASS` | `root` | MySQL admin password for `setup-local.sh` database creation |
| `HAC_URL` | `https://localhost:9002` | HAC console URL for `hac-*.sh` scripts |
| `HAC_USER` | `admin` | HAC console username |
| `HAC_PASS` | `nimda` | HAC console password |
## 8. Day-to-Day Development

After the initial setup, you won't run `initialize-server.sh` again unless you want a clean slate. Here's what to use for each scenario:

| What Changed | Script to Run |
|-------------|---------------|
| Java source code | `./local/scripts/build-server.sh` |
| `*-items.xml` or `*-beans.xml` | `./local/scripts/update-server.sh` |
| Quick restart (no code changes) | `./local/scripts/restart-server.sh` |
| Full data reset | `./local/scripts/initialize-server.sh` then `./local/scripts/index-solr.sh` |
| ImpEx data (manual import) | `./local/scripts/hac-impex.sh <file>` |
| FlexibleSearch query | `./local/scripts/hac-flexquery.sh "<query>"` |
| Groovy script | `./local/scripts/hac-groovy.sh "<script>" [--commit]` |

### Running Tests

```bash
export HYBRIS_CONFIG_DIR=$(pwd)/core-customize/hybris/config
cd core-customize/hybris/bin/platform && . ./setantenv.sh

# Unit tests (replace extension name as needed)
ant unittests -Dtestclasses.extensions=llmtemplate

# Integration tests
ant integrationtests -Dtestclasses.extensions=llmtemplate
```

**Important:** `HYBRIS_CONFIG_DIR` must be set when running `ant` directly (outside the local scripts) so the test runner can find your extensions.

## 9. Sample Data Summary

| Data | Details |
|------|---------|
| **Base site** | `electronics` |
| **OAuth clients** | `trusted_client` / `secret`, `mobile_android` / `secret` |
| **Customers** | `john.doe@thinkshop.com`, `jane.smith@thinkshop.com`, `bob.wilson@thinkshop.com` (password: `1234` for all) |
| **Products** | 10 electronics products (laptops, phones, headphones, etc.) in Staged + Online catalogs |
| **Orders** | 3 sample orders (THINK-0001, THINK-0002, THINK-0003) |
| **Delivery modes** | thinkshop-standard ($5.99), thinkshop-express ($14.99) |
| **Promotions** | Promotion rules and coupons (configure via Backoffice or ImpEx after initialize) |

## Troubleshooting

### `ant build` fails with "Extension not found"

Make sure `HYBRIS_CONFIG_DIR` is set:
```bash
export HYBRIS_CONFIG_DIR=$(pwd)/core-customize/hybris/config
```
The local scripts set this automatically. It's only needed when running `ant` directly.

### Server starts but OCC returns "Base site doesn't exist"

The base site is `electronics`:
```bash
# Correct
curl https://localhost:9002/occ/v2/electronics/products/...
```

### Product search returns 0 results

Solr hasn't been indexed. Run:
```bash
./local/scripts/index-solr.sh
```

### OAuth returns "invalid_client"

The OAuth client is `trusted_client` with secret `secret`:
```bash
curl -X POST https://localhost:9002/authorizationserver/oauth/token \
  -d 'client_id=trusted_client&client_secret=secret&grant_type=password&username=john.doe@thinkshop.com&password=1234'
```

### `setup-local.sh` can't create the MySQL database

The script uses `MYSQL_ROOT_USER`/`MYSQL_ROOT_PASS` env vars (default: `root`/`root`). If your MySQL has different credentials:
```bash
MYSQL_ROOT_USER=admin MYSQL_ROOT_PASS=mypass ./local/scripts/setup-local.sh
```

Or create the database manually and skip this step — the script continues either way.

### macOS: Backoffice fails with SafeZipEntry errors

This is caused by the `/var` -> `/private/var` symlink on macOS. The `local.properties` already includes the fix:
```properties
tomcat.generaloptions=-Xms2g -Xmx6g -XX:+UseG1GC -Djava.io.tmpdir=/private/tmp
```
If you've overridden `tomcat.generaloptions`, make sure `-Djava.io.tmpdir=/private/tmp` is included.

### Server hangs or runs out of memory

Check the JVM settings in `local.properties`:
```properties
tomcat.generaloptions=-Xms2g -Xmx6g -XX:+UseG1GC
```
Increase `-Xmx` if your machine has more RAM available. The platform comfortably runs with 8 GB.
