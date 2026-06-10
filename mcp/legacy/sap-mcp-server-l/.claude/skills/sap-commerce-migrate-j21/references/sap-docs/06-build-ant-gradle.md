---
source_topic: BUILD
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/53d796af83224e98bbd6d5dbffa1de89.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: 53d796af83224e98bbd6d5dbffa1de89
sap_version: v2211
fetched: 2026-04-30
title: "Build Tooling"
---

> Mirror of [Build Tooling](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/53d796af83224e98bbd6d5dbffa1de89.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

## How to invoke ant in an SAP Commerce project

SAP Commerce uses its **platform-bundled ant** (shipped under `core-customize/hybris/bin/platform/apache-ant/`), activated via a source-in-shell script:

```bash
cd core-customize/hybris/bin/platform
source ./setantenv.sh       # or: . ./setantenv.sh
ant -version                # Apache Ant(TM) version 1.10.15 (bundled with 2211-jdk21.x)
ant clean all               # etc.
```

`setantenv.sh` does three things that raw system `ant` on PATH does NOT:
1. Sets `ANT_HOME` to the platform-bundled ant directory.
2. Sets `ANT_OPTS` with the JDK 21 module-export flags (`--add-opens`, `--add-exports`) that Spring 6 and Jakarta EE 10 reflection requires.
3. Wires the hybris classpath into the ant task lookup so platform-specific tasks (`ysetplatformproperties`, `ybuild`, `ycheckjalotypes`, etc.) resolve.

Raw `ant` on PATH will execute but silently skip all three — which usually manifests later as "task not found" errors or module-access crashes. **Always activate via `source setantenv.sh` first.**

Gradle invocation does NOT need sourcing. The `sap.commerce.build` Gradle plugin (`./gradlew ybuild`, `./gradlew yall`, etc.) sources the script implicitly when it shells out to ant. Only invoke `source setantenv.sh` manually when running ant directly — e.g., for OpenRewrite's Step 3 prereq or a HAC script that predates the Gradle wrapper.

Verification pattern for `scripts/detect_state.sh` and Phase A.1:

```bash
cd core-customize/hybris/bin/platform && . ./setantenv.sh && ant -version
# Expect: Apache Ant(TM) version 1.10.15 compiled on August 25 2024
```

Absence of `setantenv.sh` means `bootstrapPlatform` hasn't run yet — Phase 0.7 extracts it from the suite ZIP.

---

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loio53d796af83224e98bbd6d5dbffa1de89" class="page concept - topic-topic concept-concept">

# Build Tooling

<div class="body conbody">

Review all manual steps related to Ant build tooling and adapt your <span class="ph pname" translate="no">SAP Commerce Cloud</span> implementation accordingly.

</div>

<div class="related-links">

1.  **<a href="2ef93eca4b7b4d4983e25df0a57b15b6.html" class="link">Upgrading Gradle version used in ant gradle task</a>**\
    <div class="linkdesc">

    The version of Gradle used has been upgraded from 7.3.3 to 8.5.

    </div>
2.  **<a href="de6e8dbd78bd4218adf95174f18486ce.html" class="link">Update of Ant Version to &gt;= 1.10.14</a>**\
    <div class="linkdesc">

    Update to Java 21 version requires Ant update from 1.10.5 to 1.10.14 or higher.

    </div>
3.  **<a href="b4d0ea441d7a4112a239d4db26233e78.html" class="link">Update of Ant Task Definitions</a>**\
    <div class="linkdesc">

    If your codebase contains any custom ant task definitions, they should be adjusted after the framework update.

    </div>
4.  **<a href="0940373348974491b37a52ac2343285e.html" class="link">Changed Default Value of Gradle Project Name Property</a>**\
    <div class="linkdesc">

    To adopt a more general and unified naming convention, the default value of the `gradle.project.name` property has been changed to `sap-commerce`.

    </div>
5.  **<a href="cfe79424eccc478d96b03d7194392e39.html" class="link">Changed Default Value of Property Controlling Behavior of SAP Commerce Cloud During Unsuccessful Create Data Phrase</a>**\
    <div class="linkdesc">

    The default value of the property that controls behavior of <span class="ph pname" translate="no">SAP Commerce Cloud</span> during an unsuccessful create data phase has been changed to true, making update or initialization processes fail after encountering data import errors.

    </div>

</div>

</div>

</div>

<div class="clear">

</div>

</div>

</div>

</div>
