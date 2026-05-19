# OCC Extension Setup — Context

## What This Covers

How a custom OCC extension is configured, discovered by the platform, and initialized at startup. This is the foundational wiring that makes everything else work.

## Key Concepts

- **Extension loading** — The platform reads `localextensions.xml` to know which extensions exist, then loads them in dependency order based on `<requires-extension>` declarations in each extension's `extensioninfo.xml`.
- **Two Spring contexts** — Each extension has a core context (services, facades) and a web context (controllers). The web context is a child of the core context, so controllers can see core beans but not the other way around.
- **Web module via properties** — OCC extensions configure their web root in `project.properties` (`ext.<name>.extension.webmodule.webroot=/occ/v2`) and place their web Spring config under `resources/occ/v2/<name>/`. No `<webmodule>` element is needed in `extensioninfo.xml`.
- **Spring context loading** — The platform discovers the core Spring context via `project.properties` (`<name>.application-context=<name>-spring.xml`). This is how the platform knows which Spring XML to load at startup.
- **Code generation** — `./gradlew ybuild` generates Java classes from `*-beans.xml` (DTOs) and `*-items.xml` (Models). Generated code goes to `gensrc/` and should never be hand-edited.

## Key Conventions

- **MySQL for development** — Configured in `dev-config/local.properties`. See `docs/data.md` for setup.
- **Component scan for controllers** — Controllers are discovered by annotation (`@Controller`) rather than explicit XML bean definitions. New controllers just need to be in the extension's controller package.
- **Alias pattern for beans** — Always define beans with `<alias name="defaultMyService" alias="myService"/>` so they can be overridden by other extensions.
