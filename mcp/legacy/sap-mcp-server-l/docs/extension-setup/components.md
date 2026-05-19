# OCC Extension Setup — Components

The files that configure how an OCC extension loads and initializes. File names use the extension name as a prefix (shown here as `<name>`).

## Extension Declaration

**File:** `extensioninfo.xml`

Tells the platform this extension exists, what it's called, and what it depends on.

- `name="<name>"` — Extension identifier
- `<requires-extension name="commercewebservices"/>` — Must load after commercewebservices (required for OCC extensions)
- `<coremodule packageroot="com.company.ext"/>` — Root package for generated code
- No `<webmodule>` element — Web module is configured via `project.properties` instead

## Extension Properties

**File:** `project.properties`

Configures how the platform loads this extension at runtime:

- `<name>.application-context=<name>-spring.xml` — Tells the platform which Spring XML to load into the global application context
- `ext.<name>.extension.webmodule.webroot=/occ/v2` — Registers the web module and maps it to the `/occ/v2` URL path
- `<name>.documentation.static.generate=true` — Enables static Swagger documentation generation

This is how the extension gets its Spring context loaded and its web endpoints mounted — without these properties, the platform wouldn't know about either.

## Extension Registration

**File:** `hybris/config/localextensions.xml`

Registers the extension with the platform:
```xml
<extension dir="${HYBRIS_BIN_DIR}/custom/<name>"/>
```
The platform also scans `bin/modules/` and `bin/platform/ext/` for OOTB extensions.

## Runtime Configuration

**File:** `hybris/config/local.properties`

Overrides for this deployment: database URL, server ports, CORS origins, logging levels. Extension-specific defaults come from `project.properties` in the extension root.

## Core Spring Context

**File:** `resources/<name>-spring.xml`

Loaded at platform startup (because `project.properties` declares `<name>.application-context`). Defines service and facade beans.

- `<context:annotation-config/>` — Enables `@Required` enforcement on setter injection
- Use the alias pattern for all beans:
  ```xml
  <alias name="defaultMyService" alias="myService"/>
  <bean id="defaultMyService" class="com.company.ext.services.impl.DefaultMyService">
      <property name="modelService" ref="modelService"/>
  </bean>
  ```

## Web Spring Context

**File:** `resources/occ/v2/<name>/web/spring/<name>-web-spring.xml`

Loaded by the web module. Contains:
- `<context:component-scan base-package="com.company.ext.controllers"/>` — Discovers `@Controller` classes

This is a child context of the core context. Controllers can `@Resource` any core bean.

## Type System

**File:** `resources/<name>-items.xml`

Defines custom item types, attributes, relations, and enums. When types are added here, `./gradlew ybuild` regenerates Model classes in `gensrc/`.

## DTO Definitions

**File:** `resources/<name>-beans.xml`

Defines data objects using Hybris bean definitions (not Spring beans). Two conventions:
- `*Data` classes — Used at the facade layer boundary
- `*WsDTO` classes — Used at the REST API boundary

`./gradlew ybuild` generates the Java classes.

## Generated Code

**Directory:** `gensrc/`

Auto-generated on every build:
- `Generated<Name>Constants.java` — Extension constants
- `<Name>Manager.java` — Jalo manager (legacy, required but not used)

Never edit files in `gensrc/`.
