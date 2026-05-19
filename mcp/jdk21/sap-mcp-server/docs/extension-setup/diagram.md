# OCC Extension Setup — Architecture Diagram

How an OCC extension is wired together, from configuration files through code generation to runtime Spring contexts.

## Component Overview

The diagram shows three layers of the extension setup:

- **Platform Startup** — Configuration files that register and configure the extension (`localextensions.xml`, `extensioninfo.xml`, `local.properties`, `project.properties`)
- **Code Generation** — The `./gradlew ybuild` process that generates Java classes from `items.xml` and `beans.xml` definitions
- **Runtime Contexts** — The core Spring context (services, facades) and web Spring context (controllers) that are loaded at startup

```mermaid
%%{ init: { 'theme': 'neutral' } }%%
graph TD
    subgraph Platform Startup
        LEX["localextensions.xml<br/><i>hybris/config/</i>"]
        EXT["extensioninfo.xml<br/><i>extension root</i>"]
        LP["local.properties<br/><i>hybris/config/</i>"]
        PP["project.properties<br/><i>extension root</i>"]
    end

    subgraph Code Generation ["./gradlew ybuild"]
        ITEMS["name-items.xml<br/><i>Type system definitions</i>"]
        BEANS["name-beans.xml<br/><i>DTO definitions</i>"]
        GEN["gensrc/<br/><i>Generated Models &amp; Constants</i>"]
    end

    subgraph Core Context ["name-spring.xml"]
        SVC["myService<br/><i>DefaultMyService</i>"]
        FAC["myFacade<br/><i>DefaultMyFacade</i>"]
    end

    subgraph Web Context ["name-web-spring.xml"]
        SCAN["component-scan<br/><i>com.company.ext.controllers</i>"]
        CTRL["MyController<br/><i>@Controller</i>"]
    end

    subgraph Platform Services
        PS["Platform beans<br/><i>modelService, userService, etc.</i>"]
    end

    LEX -->|"points to"| EXT
    LP -->|"configures"| EXT
    EXT -->|"declares dependencies"| PP
    PP -->|"application-context"| SVC
    PP -->|"application-context"| FAC
    PP -->|"webmodule.webroot=/occ/v2"| SCAN
    ITEMS -->|"./gradlew yclean yall"| GEN
    BEANS -->|"./gradlew ybuild"| GEN
    SVC -->|"injects"| PS
    FAC -->|"injects"| SVC
    SCAN -->|"discovers"| CTRL
    CTRL -->|"@Resource"| FAC

    style LEX fill:#fff3e0
    style EXT fill:#fff3e0
    style LP fill:#fff3e0
    style PP fill:#fff3e0
    style SVC fill:#e8f5e9
    style FAC fill:#e8f5e9
    style CTRL fill:#e1f5fe
    style GEN fill:#fce4ec
```

## Key Takeaways

- **`project.properties` is the glue** — It tells the platform which Spring XML to load (`application-context`) and where to mount the web module (`webmodule.webroot`). Without it, neither the core beans nor the controllers would be discovered.
- The **web context is a child** of the core context — controllers can inject services/facades, but not the reverse.
- **Component scan** discovers controllers by annotation, so adding a new controller only requires placing it in the extension's controller package.
- **Code generation** from `items.xml` requires `./gradlew yclean yall` (full rebuild), while `beans.xml` changes only need `./gradlew ybuild`.
