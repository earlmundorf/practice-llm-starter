# SAP Commerce Architecture Reference

## Table of Contents
1. [Platform Overview](#platform-overview)
2. [Extension System](#extension-system)
3. [Layered Architecture](#layered-architecture)
4. [Request Flow](#request-flow)
5. [Build System](#build-system)
6. [Configuration Hierarchy](#configuration-hierarchy)
7. [Multi-Tenancy and Sites](#multi-tenancy-and-sites)

---

## Platform Overview

SAP Commerce Cloud (formerly Hybris) is a Java-based, Spring-powered e-commerce platform. The runtime consists of:

- **Platform core** — Type system, persistence, caching, clustering, session management
- **Extensions** — Modular units that add functionality (like plugins)
- **Embedded Tomcat** — Servlet container (default ports: 9001 HTTP, 9002 HTTPS)
- **Database** — HSQLDB for dev, MySQL/Oracle/HANA/PostgreSQL for production

The platform directory structure:

```
hybris/
├── bin/
│   ├── platform/          # Core platform + built-in extensions
│   │   └── ext/           # Platform extensions (core, impex, catalog, etc.)
│   ├── modules/           # SAP-provided module bundles
│   └── custom/            # YOUR custom extensions go here
├── config/
│   ├── local.properties          # Runtime config overrides
│   └── localextensions.xml       # Which extensions are active
├── data/                  # Database files, media storage
├── log/                   # Runtime logs
└── temp/                  # Compiled artifacts, Tomcat
```

## Extension System

An extension is the fundamental unit of modularity. Each extension:
- Has its own type definitions (items.xml), Spring context, and Java code
- Declares dependencies on other extensions via `extensioninfo.xml`
- Can extend or override beans from other extensions via Spring aliasing

### extensioninfo.xml

```xml
<extensioninfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:noNamespaceSchemaLocation="extensioninfo.xsd">
  <extension abstractclassprefix="Generated"
             classprefix="Myextension"
             name="myextension"
             managername="MyextensionManager"
             managersuperclass="de.hybris.platform.jalo.extension.Extension">
    <requires-extension name="core"/>
    <requires-extension name="commerceservices"/>
    <coremodule generated="true" packageroot="com.company.myextension"/>
    <webmodule jspcompile="false"
               webroot="/myextension"/>
  </extension>
</extensioninfo>
```

Key attributes:
- `requires-extension` — Dependency declaration (build + runtime order)
- `coremodule` — Java package root for generated code
- `webmodule` — Exposes a web endpoint (Spring MVC context)

### localextensions.xml

Controls which extensions are loaded at runtime:

```xml
<hybrisconfig>
  <extensions>
    <!-- Scan directories for extensions -->
    <path dir="${HYBRIS_BIN_DIR}" />

    <!-- Explicitly include extensions -->
    <extension name="commercewebservices" />
    <extension dir="${HYBRIS_BIN_DIR}/custom/myextension" />
  </extensions>
</hybrisconfig>
```

### Creating a New Extension

```bash
cd ${HYBRIS_HOME}/bin/platform
. ./setantenv.sh
ant extgen
```

Choose a template:
- `yempty` — Bare-bones extension
- `ybackoffice` — Backoffice UI extension
- `yocc` — OCC REST API extension
- `yaddon` — AddOn (UI overlay for accelerator storefronts)

## Layered Architecture

### DAO Layer (Data Access)
- Contains all FlexibleSearch queries
- Methods named descriptively: `findByCode()`, `findActiveProductsForCategory()`
- Returns `Model` objects (generated from items.xml)
- Never contains business logic

### Service Layer
- Contains business logic and transaction management
- Operates on `Model` objects
- Throws business exceptions (`ModelNotFoundException`, custom exceptions)
- Injected with DAOs and other services
- Always define an interface + `Default*` implementation

### Facade Layer
- Converts `Model` → `Data` (DTO) using Converters and Populators
- Orchestrates multiple service calls
- Never exposes Model objects to the presentation layer
- The "public API" consumed by controllers

### Converter/Populator Pattern
- **Converter** — Delegates to one or more Populators to build a DTO
- **Populator** — Fills specific fields on a DTO from a Model
- This is composable: add your custom Populator to an existing Converter's list

```xml
<!-- Spring config: add your populator to the product converter -->
<bean parent="modifyPopulatorList">
  <property name="list" ref="productConverter"/>
  <property name="add" ref="myCustomProductPopulator"/>
</bean>
```

### Controller Layer
- Spring MVC `@Controller` classes
- Calls facades only (never services/DAOs directly)
- Handles HTTP concerns (request/response mapping, validation, error handling)
- For OCC: returns `*WsDTO` objects serialized as JSON/XML

## Request Flow

Example: User views product `/occ/v2/mysite/products/12345`

```
HTTP Request
  → DispatcherServlet
    → ProductController.getProduct("12345")
      → ProductFacade.getProductForCodeAndOptions("12345", options)
        → ProductService.getProductForCode("12345")
          → ProductDao.findProductsByCode("12345")
            → FlexibleSearch: SELECT {pk} FROM {Product} WHERE {code}=?code
          ← ProductModel
        ← ProductModel (+ business validation)
      ← ProductData (via ProductConverter + Populators)
    ← ProductWsDTO (mapped from ProductData)
  → JSON Response
```

## Build System

### Ant (per-extension)

```bash
# Set environment
cd ${HYBRIS_HOME}/bin/platform
. ./setantenv.sh

# Core build commands
ant clean all              # Full rebuild (required after items.xml changes)
ant build                  # Incremental compile
ant initialize             # Reset DB + run all ImpEx (DESTRUCTIVE)
ant updatesystem           # Apply model changes without data loss
ant server                 # Start embedded Tomcat

# Testing
ant unittests
ant integrationtests
ant alltests

# Code generation
ant extgen                 # Generate new extension from template

# Utilities
ant modulegen              # Generate a module
ant clearlock              # Clear platform lock file
```

### Gradle Installer (top-level orchestration)

```bash
./install.sh -r cx setup          # Configure for B2C recipe
./install.sh -r cx buildSystem    # Compile all
./install.sh -r cx initialize     # Init DB
./install.sh -r cx start          # Start server
```

Recipes (`cx`, `cx_china`, etc.) define which extensions to include and how to configure them.

## Configuration Hierarchy

Properties are resolved in this order (later overrides earlier):

1. Extension `project.properties` (defaults)
2. `local.properties` in config/ (runtime overrides)
3. Environment variables
4. HAC (Hybris Administration Console) runtime properties

### Key local.properties settings

```properties
# Database
db.url=jdbc:mysql://localhost:3306/commerce
db.driver=com.mysql.cj.jdbc.Driver
db.username=commerce
db.password=secret

# Server
tomcat.http.port=9001
tomcat.ssl.port=9002

# Logging
log4j2.logger.myextension.name=com.company.myextension
log4j2.logger.myextension.level=DEBUG

# OCC / CORS
corsfilter.commercewebservices.allowedOrigins=http://localhost:4200
corsfilter.commercewebservices.allowedMethods=GET,POST,PUT,DELETE,OPTIONS

# Solr
solrserver.instances.default.autostart=true

# Media
media.read.dir=/opt/hybris/data/media
```

## Multi-Tenancy and Sites

SAP Commerce supports multiple "base stores" and "CMS sites" from a single deployment:

- **BaseSite** — Top-level site config (URL patterns, theme, language, currency)
- **BaseStore** — Commerce config (catalogs, warehouses, delivery modes)
- **ContentCatalog** — CMS pages, components, slots per site
- **ProductCatalog** — Products, categories, with Staged/Online versioning

A single platform instance can serve multiple brands/regions, each with its own catalog, pricing, and content.
