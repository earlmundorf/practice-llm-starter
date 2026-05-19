# Additional changes from SAP's framework update changelog

## Prerequisites (before starting a migration)

**Required:**
- SapMachine JDK 21 installed (e.g., `sdk install java 21.0.11-sapmchn`).
- SAP Commerce Suite ZIP matching the target `commerceSuiteVersion` — drop in `core-customize/dependencies/hybris-commerce-suite-<version>.zip`.

**Recommended (warn-don't-fail):**
- SAP Integration Extension Pack ZIP matching the target release, as a separate download from the SAP Software Download Center. Drop in `core-customize/dependencies/hybris-commerce-integrations-<version>.zip`.
- **If you cannot source a matching version, leave it OFF rather than use an older/mismatched pack.** `bootstrapPlatform` and early migration phases work fine without integrations; only Phase F OCC smoke tests require them. A mismatched pack risks silent runtime failures that surface late and are hard to diagnose.

---

Items in SAP's `SAPCommerceUpgradeJDK21Changes.docx` that don't have a dedicated page mirror in `sap-docs/`. Each SAP sub-topic listed here links to one of the TOC landings (e.g. `03-spring-6.md`, `05-tomcat.md`), but the text below is what the .docx actually says about each one. Use this when the plan needs more detail than `00-overview.md` carries and the `sap-docs/` landing page doesn't include the sub-topic verbatim.

> To drill into any item, add its loio to `LOIO_FILE` in `scripts/fetch_sap_docs.sh` and re-run. SAP's help pages link to each sub-topic by its own loio (visible as `<a href="{LOIO}.html">` in the mirrored TOC pages).

## Spring 6 — detailed sub-topics

Every item here is a distinct section in the Spring 6 page (`03-spring-6.md`).

- **Adapting to PathPatternParser.** Spring 6 default URL matcher is `PathPatternParser`, replacing `AntPathMatcher`. Review custom MVC config and any code that reads a handler mapping's pattern parser.
- **Adapting to Strict URL Matching.** Trailing-slash matching default is now `false` (deprecated option). Clients calling `/path/` when handler is `/path` will 404 silently. Audit OCC clients.
- **Adjusting to jakarta.servlet.Filter API.** `javax.servlet.Filter` → `jakarta.servlet.Filter`. OpenRewrite handles most cases; verify custom filter impls.
- **Using StandardServletMultipartResolver for Multipart Data.** `CommonsMultipartResolver` was removed in Spring 6.1. Swap to `StandardServletMultipartResolver`. Impact: any multipart POST endpoint.
- **Updating spring-security-saml2-service-provider to 6.4.x.** Bump the lib version from 5.8.11 to 6.4.1.
- **Updating to SiteMesh 3.2.x.** SiteMesh 3.0-alpha is not fully compatible with Jakarta EE 10. Bump to 3.2.1.
- **Adding Missing MVC Configuration in Web Application Context.** Spring 6.x introduces the `mvcHandlerMappingIntrospector` bean; missing it throws at startup. Ensure Spring MVC and Security share the same web application context.
- **Adapting to Removal of the @Required Annotation.** `@Required` is gone. OpenRewrite removes invocations automatically when applied.
- **Adapting codebase after removal of SocketUtils Class.** `org.springframework.util.SocketUtils` is removed. Replace with equivalent test-port discovery.
- **Resolving BeanPostProcessorChecker Warnings.** New warning-message logic in Spring 6's `BeanPostProcessorChecker`. Usually cosmetic but can indicate post-processor ordering issues.
- **Configure Extension Load Order to Support Fixed Spring 6 Alias Overriding Behavior.** Spring 6 fixes inconsistencies where bean names and aliases conflicted. Extensions whose bean overrides relied on the old order need their load order adjusted.
- **Adapting to @TestExecutionListener Annotation Changes.** Spring 6.0 changed how test listeners are registered in certain base classes. Some tests need updates to preserve expected behavior.
- **Replacing HttpPutFormContentFilter with FormContentFilter.** Only for extensions generated from `ycommercewebservices`. OpenRewrite handles the rename.
- **Loading OCC Swagger Properties.** Extensions generated from `ycommercewebservices` template need a loader adjustment for Swagger props after the Spring 6 update.
- **@ModelAttribute Annotation Changes.** Spring 6 changed how `@ModelAttribute` binds request elements to model objects. Review controllers that use it — behavior differences may be silent.
- **Consider Removing Separate DispatcherServlet Context.** When configuring a Spring web app, reassess whether separate servlet contexts still make sense under Spring 6. Often the right move is to collapse them.
- **Integration 6.4.x Update.** Spring Integration bumped from 5.5.20 to ≥ 6.4.1.
- **Parameter Name Retention for Dependency Injection.** Spring now relies on Java/Groovy compilers retaining parameter names in bytecode (no more bytecode parsing). Ensure `-parameters` is enabled in the compiler config for extensions.
- **Resolving Redirection Issues of request-matcher.** Spring Security 6.x default policy changed from `AntPathRequestMatcher` to `MvcPathRequestMatcher`. Side-effect: 302 redirects on static resources. Review security intercept-URL rules.
- **Fixing "No visible WebSecurityExpressionHandler" Error.** `java.io.IOException: No visible WebSecurityExpressionHandler instance could be found` at startup — caused by missing security-expression-handler config for JSP `<authorize>` tags. Usually resolved by explicitly wiring the handler bean.
- **Updating Spring Security Intercept-URL Configuration for Empty Expression Strings.** `expressionString cannot be empty` at server start — caused by `<intercept-url>` rules with empty `access` attributes. Fix the rule or remove it.
- **Optimizing SequenceSizeReleaseStrategy Configuration for Large Groups.** Switch `SequenceSizeReleaseStrategy` to `SimpleSequenceSizeReleaseStrategy` to silence perf warnings on large groups.
- **Resolving DispatcherServlet Exceptions During Server Startup.** Make Spring MVC config compatible with Spring 6 by explicitly defining `AntPathMatcher` and setting `patternParser` to `null` on all handler-mapping beans (backport strategy when full PathPatternParser migration isn't ready).

## Spring 6 — subtleties not in the SAP digest

These are Spring 6 behavior changes the SAP framework-update changelog doesn't surface but that experienced upgraders verify. Each can ship a working build that misbehaves in production. Treat these as **diagnostic candidates** if Phase B/C/D pass cleanly but runtime behavior is subtly wrong. Cross-reference against `references/upstream/SPRING_UPGRADES_60.md` / `61.md` / `62.md` for upstream Spring's detailed framing.

### `@Transactional` propagation defaults

Spring 6 tightened transaction-related defaults in subtle ways. Behaviors that worked under Spring 5 with permissive defaults may now require explicit configuration:

- **Rollback rules.** Spring 5 had several legacy rollback-on-* defaults; Spring 6 normalized them. Custom services that relied on a specific exception class triggering or NOT triggering rollback should be re-verified.
- **`Propagation.REQUIRES_NEW` semantics.** New transactions in Spring 6 are stricter about not seeing the outer transaction's uncommitted state. Code that read its own pending writes via a `REQUIRES_NEW` inner call may now read stale data.
- **`@Transactional` on private methods.** Was always silently ignored; in Spring 6 some build-time validators warn or error. If you see new validation warnings here, fix them — they were latent bugs.

**Verification:** grep `@Transactional` in custom extensions; for each, confirm the propagation behavior matches expectations. Add explicit `propagation = ` and `rollbackFor = ` rather than relying on defaults.

### AOP proxy class generation

Spring 6 changed CGLIB defaults around method visibility:

- **`final` methods on `@Aspect`-targeted classes.** In Spring 5 these were silently bypassed by CGLIB proxies (advice didn't fire); in Spring 6 the behavior is strict — final methods can cause proxy-creation failures or louder warnings. Either remove `final`, or refactor advice to target an interface.
- **CGLIB by default on `@Configuration`.** Spring 6 still uses CGLIB to enforce singleton bean semantics in `@Configuration` classes. If you have `proxyBeanMethods = false` for performance, verify your `@Bean` methods don't have intra-config dependencies.

**Verification:** boot the upgraded server with `-Dorg.springframework.aop.proxy.LOG_FAILED_PROXIES=true` (informal flag — actual setup varies by Spring version); audit any class loading errors or `Could not generate CGLIB subclass` warnings.

### `HandlerMethodArgumentResolver` order

Custom argument resolvers in OCC controllers may resolve in different priority order under Spring 6. Symptom: a parameter that used to bind from one source (e.g., header) silently binds from another (e.g., query string), giving wrong data with no error.

**Verification:** for any custom `HandlerMethodArgumentResolver` impl in OCC code, write a test that exercises the parameter binding with a mix of sources (header + query + body) and verifies the expected source wins.

### `@ModelAttribute` binding nullability

Related to but more subtle than the SAP-digested change. Spring 6 is stricter about which fields it will and won't bind from request data. Fields that previously got nulled out from missing request params may now retain their default value, or vice versa. Subtle but bites OCC POST handlers.

**Verification:** for OCC controllers using `@ModelAttribute`, check whether the bound objects have the same field-population shape post-migration as pre-. Diff against the OCC contract baseline (Step 0.0c).

### `WebMvcConfigurer` interface methods

Several `WebMvcConfigurer` default-methods changed signature subtly between Spring 5 and 6 (notably around content negotiation and async handling). Custom configurers that override these methods may compile fine but no longer be called.

**Verification:** grep for `extends WebMvcConfigurerAdapter` (gone in Spring 6 — must use the interface directly) and `implements WebMvcConfigurer` in custom extensions; verify that override hooks still fire post-migration via debug logging.

### Reactive / WebFlux interop

If any custom extension uses Spring WebFlux alongside Spring MVC (uncommon in Commerce but seen in OAuth integration extensions), the interop story changed. Spring 6 cleaned up some shared-bean conflicts that Spring 5 papered over.

**Verification:** boot logs for any `BeanCurrentlyInCreationException` or `Conflicting bean of name` referring to webflux beans. If present, untangle by giving each context its own bean names.

### Native compilation / Spring AOT

**Do NOT enable** Spring AOT or native compilation for SAP Commerce. Much of the platform uses runtime reflection in ways AOT can't analyze. The platform isn't certified or supported for native; turning it on will produce builds that compile but fail in cryptic runtime ways. If a developer asks "should we enable AOT?", the answer is no for Commerce.

### Parameter name retention

The SAP digest mentions `-parameters` flag retention. The subtlety: it's not just about Spring DI; affects:

- Bean Validation error messages (3.0 dropped name-from-bytecode).
- Jackson deserialization of records or constructor-bound DTOs.
- Some reflection-based logging frameworks.

If you see `arg0`, `arg1` in error messages or logs after migration, the `-parameters` flag isn't propagating. Verify in `core-customize/hybris/bin/platform/build.xml` and any custom extension build scripts.

### Spring Security 6 default authorization-rule format

`access` attribute syntax in `<intercept-url>` rules changed default expressions. The SAP digest mentions empty-expression errors but not the format change itself. Examples:

- Spring 5: `access="hasRole('ROLE_USER')"` — works
- Spring 6: same syntax works, but some defaults around `permitAll` and `anonymous` were tightened.

**Verification:** boot the server in dev mode; hit pages that should be anonymous-accessible (login page, password reset); confirm they don't redirect to login. Hit pages that require auth; confirm they do.

## Tomcat 10

From `05-tomcat.md` TOC — each is a distinct sub-topic:

- **Replacing setAttribute() Method for Tomcat Connector.** Tomcat 9.0.95 → 10.1.x changed connector attribute API.
- **Configuring Tomcat SSL Connector.** SSL connector attributes have moved in 10.x — update `server.xml` if you configure SSL locally.
- **Adjusting to new filter API after Tomcat update.** Jakarta namespace adoption across filter impls + library bumps.
- **Adapting security headers configuration in Tomcat 10.1.X.** `X-XSS-Protection` is deprecated (removed starting Tomcat 11.0.x). Update any platform config referencing it.

## Build tooling (Ant + Gradle)

From `06-build-ant-gradle.md`:

- Gradle used by `ant gradle` task bumped from 7.3.3 to 8.5.
- Ant must be ≥ 1.10.14 (up from 1.10.5) to work with Java 21.
- Custom Ant task definitions need review — they are expected to break across the version jump.
- `gradle.project.name` default changed to `sap-commerce` (unified naming). Projects relying on defaults get a rename; pin the property explicitly to retain a specific name.
- `create-data` unsuccessful-phase default changed to `true`, making update or initialization processes **fail** after data-import errors that used to be tolerated. Review ImpEx logs.

## Apache libraries

Items from `07. Apache Libraries` section of .docx. None have a dedicated mirror here — consult the general update guide + Spring 6 page where cross-references exist.

- **Apache Commons Configuration 1.x → 2.x.** API changes are non-trivial; check for `PropertiesConfiguration`, `XMLConfiguration`, builder APIs.
- **Apache Commons Lang 2 → 3.** Lang 2 is end-of-life. Package renames: `org.apache.commons.lang` → `org.apache.commons.lang3`. OpenRewrite may handle most call-site rewrites; class names occasionally changed.
- **Apache HttpComponents 5.** Only relevant to extensions generated from `ycommercewebservicestest`. HttpComponents 4 → 5 changes request-building idioms substantially.
- **Apache Commons Pool 1.6 removal prep.** Pool 1.6 is slated for removal; prepare custom impls.
- **Apache Commons Math 3.6 removed.** Adjust any code that used `org.apache.commons.math3`. Replace or inline small utilities.
- **Apache Commons Collections + BeanUtils upgrades.** `commons-collections 3.2.2` + `beanutils 1.9.4` moved to newer versions. **OpenRewrite handles most of this**; still review diffs.

## Caching (Ehcache)

- **Ehcache 2.x → 3.x.** Major API change. Config syntax differs (XML namespace change; `CacheManager` API rewritten).
- **EhCacheRegion replaced with DefaultCacheRegion.** `de.hybris.platform.regioncache.EhCacheRegion` (Ehcache 2) is removed. Swap to `de.hybris.platform.regioncache.DefaultCacheRegion`. Any custom code casting or referencing `EhCacheRegion` needs updating.

## "Other Topics" (from the .docx)

- **Repackaging of DdlUtils 1.0.** Deprecated DdlUtils library is gone; its core logic was integrated directly into the platform. Any extension that imported `org.apache.ddlutils.*` needs to switch to the platform-internal equivalents.
- **Updating Eclipse .classpath Files and JDK Settings.** Sync `.classpath` files with library versions; update JDK version in Eclipse project settings.
- **Fixing checks for method visibility in Byte-Buddy 1.14.19.** Byte-Buddy update can surface visibility check errors in bytecode-manipulating code (proxies, aspects).
- **Removing Deprecated Thread Class Method Usage.** Refactor out deprecated/unsafe `java.lang.Thread` methods (e.g., `stop()`, `suspend()`, `destroy()`, `countStackFrames()`). Java 21 enforces these stricter.
- **Upgrade of Groovy to 4.0.x.** Groovy 3.0.13 → 4.0.26. Check any `*.groovy` / Gradle scripts for syntax incompatibilities.

## javax → jakarta specifics

From the .docx `Migrating from javax to jakarta` section:

- **yacceleratorstorefront template migration steps.** Extensions generated from that template have a specific set of steps to upgrade from javax to jakarta — consult the anchored sub-section in `sap-docs/01-general-update-guide.md` when working on storefront code.
- **`SEVERE: Exception starting filter [WebResourceOptimizer] java.lang.NoClassDefFoundError: javax/servlet/Filter`.** Known Tomcat-startup error after the jakarta migration; caused by a missed filter class still referencing `javax`. Fix: complete the namespace migration for the offending filter and any JAR dependency that still ships javax classes.
- **Upgrading jakarta.servlet.jsp.jstl from 1.2.6 to 3.0.1.** Required under JDK 21 / Jakarta EE 10.

## Olingo

- **Olingo 2.0.13 → 5.0.0-sap-02.** The public Olingo 2.0.13 is replaced with SAP's internal fork (5.0.0-sap-02). Any custom OData producer/consumer code built on the old Olingo needs review. The SAP fork carries platform-specific fixes.

## LocalizedHybrisConstraintViolation

- **Adjust Your Configuration to Extend LocalizedHybrisConstraintViolation.** Refactor classes that extend this type; update constructors. Ensures custom validation violations are compatible with the new Jakarta validation API.

## Orphaned types cleanup

- **Cleaning Up Orphaned Types.** After upgrade, clean up orphaned types (types still in the database but no longer declared in items.xml, typically left over from deprecated extensions). Run the platform's orphaned-type cleanup tooling after a successful initialize/update.

## Reference links from .docx

Additional authoritative links the .docx points to (not all have been ingested into `sap-docs/`):

- **SAP Note 3618495** — `https://me.sap.com/notes/3618495/E`. Customer-login required. Pivotal general note for this update release.
- **Release Notes for 2211-jdk21 Updates** — loio `236dcbe0ff5d4bd0bdf177b7f151cc66` → mirrored as `sap-docs/13-release-notes-2211-jdk21.md` (after running `fetch_sap_docs.sh`).
- **Update Release 2211.46** — loio `712a8128d12d49cb8752765c38e50d41` → mirrored as `sap-docs/14-update-release-2211.46.md`.
