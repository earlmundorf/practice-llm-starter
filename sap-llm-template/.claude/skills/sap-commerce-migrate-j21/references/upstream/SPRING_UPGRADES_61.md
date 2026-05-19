Upgrading From Spring Framework 6.0
Baseline upgrades
Spring Framework 6.1 raises its minimum requirements with the following libraries:

SnakeYAML 2.0
Jackson 2.14
Kotlin Coroutines 1.7
Kotlin Serialization 1.5
Removed APIs
Several deprecated classes, constructors, and methods have been removed across the code base. See 29449 and 30604.

Parameter Name Retention
LocalVariableTableParameterNameDiscoverer has been removed in 6.1. Consequently, code within the Spring Framework and Spring portfolio frameworks no longer attempts to deduce parameter names by parsing bytecode. If you experience issues with dependency injection, property binding, SpEL expressions, or other use cases that depend on the names of parameters, you should compile your Java sources with the common Java 8+ -parameters flag for parameter name retention (instead of relying on the -debug compiler flag) in order to be compatible with StandardReflectionParameterNameDiscoverer. The Groovy compiler also supports a -parameters flag for the same purpose. With the Kotlin compiler, use the -java-parameters flag.

Note that if you are using Java Agents in your deployment, such agents can modify the bytecode and lose the "parameters" information at runtime. This has been fixed in JDK 19 and might get backported to oder JDK versions. In the meantime, we recommend running your application on a modern JDK if you are hitting this problem.

Maven users need to configure the maven-compiler-plugin for Java source code:

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
Gradle users need to configure the JavaCompile task for Java source code, either with the Kotlin DSL:

tasks.withType<JavaCompile>() {
    options.compilerArgs.add("-parameters")
}
Or the Groovy DSL:

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs.add("-parameters")
}
Similarly, Gradle users need to configure the GroovyCompile task for Groovy source code, either with the Kotlin DSL:

tasks.withType<GroovyCompile>() {
    groovyOptions.parameters = true
}
Or the Groovy DSL:

tasks.withType(GroovyCompile).configureEach {
    groovyOptions.parameters = true
}
Sometimes it is also necessary to manually configure your IDE.

In IntelliJ IDEA, open Settings and add -parameters to the following field.

Build, Execution, Deployment → Compiler → Java Compiler → Additional command line parameters
In Eclipse IDE, open Preferences and activate the following checkbox.

Java → Compiler → Store information about method parameters (usable via reflection)
In VSCode, edit or add the .settings/org.eclipse.jdt.core.prefs file with the following content:

org.eclipse.jdt.core.compiler.codegen.methodParameters=generate
Core Container
Aligned with the deprecation of java.net.URL constructors in JDK 20, URL resolution is now consistently performed via URI, including the handling of relative paths. This includes behavioral changes for uncommon cases such as when specifying a full URL as a relative path. See 29481 and 28522.

AutowireCapableBeanFactory.createBean(Class, int, boolean) is deprecated now, in favor of the convention-based createBean(Class). The latter is also consistently used internally in 6.1 – for example, in SpringBeanJobFactory for Quartz and SpringBeanContainer for Hibernate.

Array-to-collection conversion prefers a List result rather than a Set for a declared target type of Collection.

ThreadPoolTaskExecutor and ThreadPoolTaskScheduler enter a graceful shutdown phase when the application context starts to close. As a consequence, further task submissions are not accepted during stop or destroy callbacks in other components anymore. If the latter is necessary, switch the executor/scheduler's acceptTasksAfterContextClose flag to true, at the expense of a longer shutdown phase.

Message resolution through the ApplicationContext (accessing its internal MessageSource) is only allowed while the context is still active. After context close, getMessage attempts will throw an IllegalStateException now.

Spring's declarative caching infrastructure detects reactive method signatures, e.g. returning a Reactor Mono or Flux, and specifically processes such methods for asynchronous caching of their produced values rather than trying to cache the returned Reactive Streams Publisher instances themselves. This requires support in the target cache provider, e.g. with CaffeineCacheManager being set to setAsyncCacheMode(true). For existing applications which rely on synchronous caching of custom Mono.cache()/Flux.cache() results, we recommend revising this towards 6.1-style caching of produced values; if such a revision is not immediately possible/desirable, you may set the system property "spring.cache.reactivestreams.ignore=true" (or put a similar entry into a spring.properties file on the classpath).

When building a native image, the verbose logging about pre-computed fields has been removed by default, and can be restored by passing -Dspring.native.precompute.log=verbose as a native-image compiler build argument to display related detailed logs.

Data Access and Transactions
@TransactionalEventListener rejects invalid @Transactional usage on the same method: only allowed as REQUIRES_NEW (possibly in combination with @Async).

JPA bootstrapping now fails in case of an incomplete Hibernate Validator setup (e.g. without an EL provider), making such a scenario easier to debug.

Since JpaTransactionManager with HibernateJpaDialect translates commit/rollback exceptions to DataAccessException subclasses wherever possible, a Hibernate transaction exception formerly propagated as a generic JpaSystemException may show up as e.g. CannotAcquireLockException now. For a non-translatable fallback exception, JpaSystemException will be consistently thrown for commit/rollback now, instead of the former TransactionSystemException propagated from rollback.

JDBC setNull handling has been revised to bypass driver-level getParameterType resolution on PostgreSQL and MS SQL Server by default, as of 25679 in 6.1.2. This is a performance optimization to avoid further roundtrips to the DBMS just for parameter type resolution which is known to make a significant difference on PostgreSQL and MS SQL Server specifically. If you happen to see a side effect e.g. for a null byte array, consider revising your SQL statement or your application-specified type information (e.g. through providing a SqlParameterValue instead of a plain null value). Otherwise, you may explicitly set the spring.jdbc.getParameterType.ignore=false flag as a system property (or in a spring.properties file in the root of the classpath) to restore full getParameterType resolution.

Web Applications
Spring MVC and WebFlux now have built-in method validation support for controller method parameters with @Constraint annotations. To be in effect, you need to 1) opt out of AOP-based method validation by removing @Validated at the controller class level, 2) ensure mvcValidator or webFluxValidator beans are of type jakarta.validation.Validator (for example, LocalValidatorFactoryBean), and 3) have constraint annotations directly on method parameters. Where method validation is required (i.e. constraint annotations are present), model attribute and request body arguments with @Valid are also validated at the method level, and in that case no longer validated at the argument resolver level, thereby avoiding double validation. BindingResult arguments are still respected, but if not present or if method validation fails on other parameters, then a MethodValidationException raised. That's not handled yet in 6.1 M1, but will be in M2 with 30644. See 29825 for more details on the support in M1, and also the umbrella issue 30645 for all other related tasks and for providing feedback.

The format for MethodArgumentNotValidException and WebExchangeBindException message arguments has changed. Errors are now joined with ", and ", without single quotes and brackets. Field errors are resolved through the MessageSource with nothing further such as the field name added. This gives applications full control over the error format by customizing individual error codes. See 30198 and also planned documentation improvement 30653.

The default order of mappings has been refined to be more consistent by changing RouterFunctionMapping order from 3 to -1 in Spring MVC. That means RouterFunctionMapping is now always ordered before RequestMappingHandlerMapping in both Spring MVC and Spring WebFlux. See 30278 for more details.

The throwExceptionIfNoHandlerFound property of DispatcherHandler is now set to true by default and is deprecated. The resulting exception is handled by default as a 404 error so it should result in the same outcome. Likewise, ResourceHttpRequestHandler now raises NoResourceFoundException, which is also handled by default as a 404, and should have the same outcome for most applications. See 29491.

@RequestParam, @RequestHeader, and other controller method argument annotations now use the defaultValue if the input is a non-empty String without text.

ResponseBodyEmitter now completes the response if the exception is not an IOException, see issue 30687.

Preflight checks are now executed at the start of the HandlerInteceptor chain and not at the end.

The HTTP interface client no longer enforces a 5 second default timeout on methods with a blocking signature, instead relying on default timeout and configuration settings of the underlying HTTP client. See 30248.

The HTTP server Observability instrumentation in WebFlux was limited and was not properly observing errors. As a result, the WebFlux ServerHttpObservationFilter is now deprecated in favor of direct instrumentation on the WebHttpHandlerBuilder. See 30013.

ReactorResourceFactory class has been moved from the org.springframework.http.client.reactive package to the org.springframework.http.client one.

To reduce memory usage in RestClient and RestTemplate, most ClientHttpRequestFactory implementations no longer buffer request bodies before sending them to the server. As a result, for certain content types such as JSON, the contents size is no longer known, and a Content-Length header is no longer set. If you would like to buffer request bodies like before, simply wrap the ClientHttpRequestFactory you are using in a BufferingClientHttpRequestFactory.

Jackson ParameterNamesModule is now part of the well-known modules automatically registered by Jackson2ObjectMapperBuilder when present in the classpath. This can introduce changes of behavior in JSON serialization/deserialization as mentioned in the module documentation linked above. In such case, additional @JsonCreator or @JsonProperty("propertyName") annotations may be required. If you prefer avoid enabling such module, it is possible to use Jackson2ObjectMapperBuilder#modules in order to disable automatic module registration.

ReactorClientHttpConnector now implements SmartLifecycle to provide lifecycle management capabilities. As a consequence, it now requires spring-context dependency.

Messaging Applications
The RSocket interface client no longer enforces a 5 second default timeout on methods with a blocking signature, instead relying on default timeout and configuration settings of the RSocket client, and the underlying RSocket transport. See 30248.

In an effort to reduce the potential for security vulnerabilities in the Spring Expression Language (SpEL) to adversely affect Spring applications, the team has decided to disable support for evaluating SpEL expressions from untrusted sources by default. Within the core Spring Framework, this applies to the SpEL-based selector header support in WebSocket messaging, specifically in the DefaultSubscriptionRegistry. The selector header support will remain in place but will have to be explicitly enabled beginning with Spring Framework 6.1 (see 30550). For example, a custom implementation of WebSocketMessageBrokerConfigurer can override the configureMessageBroker() method and configure the selector header name as follows: registry.enableSimpleBroker().setSelectorHeaderName("selector");.

Testing
By default, if an error is encountered during build-time AOT processing, an exception will be thrown, and the overall process will fail immediately. If you would prefer that build-time AOT processing continue after errors are encountered, you can disable the failOnError mode which results in errors being logged at WARN level or with greater detail at DEBUG level. The failOnError mode can be disabled from the command line or a build script by setting a JVM system property named spring.test.aot.processing.failOnError to false. As an alternative, you can set the same property via the SpringProperties mechanism.

New and Noteworthy
Core Container
General compatibility with virtual threads and JDK 21 overall.
Configuration options for virtual threads: a dedicated VirtualThreadTaskExecutor and a virtual threads mode on SimpleAsyncTaskExecutor, plus an analogous SimpleAsyncTaskScheduler with a new-thread-per-task strategy and a virtual threads mode.
Lifecycle integration with Project CRaC for JVM checkpoint restore (see related documentation), including a -Dspring.context.checkpoint=onRefresh option.
Lifecycle integrated pause/resume capability and parallel graceful shutdown for ThreadPoolTaskExecutor and ThreadPoolTaskScheduler as well as SimpleAsyncTaskScheduler.
A -Dspring.context.exit=onRefresh option is available with AppCDS training runs as the main use-case; see 31595.
Reachability metadata contribution improvements, preparing for upcoming GraalVM changes: missing reachability metadata will be soon reported as runtime exceptions for better developer experience; see 31213.
New ModuleResource: Resource implementation for java.lang.Module resolution, performing getInputStream() access via Module.getResourceAsStream.
Custom @Component stereotype annotations may now use @AliasFor to configure an annotation attribute override for the component's name. Consequently, the name of the annotation attribute that is used to specify the bean name is no longer required to be value, and custom stereotype annotations can now declare an attribute with a different name (such as name) and annotate that attribute with @AliasFor(annotation = Component.class, attribute = "value").
Convention-based @Component stereotype names based on the value attribute are now deprecated in favor of explicit @AliasFor declarations. See previous bullet point.
Spring now finds all @ComponentScan and @PropertySource annotations; see 30941.
Async/reactive destroy methods – for example, on R2DBC ConnectionFactory; see 26691.
Async/reactive cacheable methods, including corresponding support in the Cache interface and in CaffeineCacheManager; see 17559 and 17920.
Reactive @Scheduled methods (including Kotlin coroutines); see 22924.
Selecting a specific target scheduler for each @Scheduled method; see 20818.
@Scheduled methods for one-time tasks (with just an initial delay); see 31211.
Observation instrumentation of @Scheduled methods; see 29883.
Spring Framework will not produce observations out-of-the-box for @Async or @EventListener annotated methods, but will help you with propagating context (e.g. MDC logging with the current trace id) for the execution of those methods. See the new ContextPropagatingTaskDecorator, the relevant reference documentation section, and issue 31130.
Validator factory methods for programmatic validator implementations; see 29890.
Validator.validateObject(Object) with returned Errors and Errors.failOnError method for flexible programmatic usage; see 19877.
MethodValidationInterceptor throws MethodValidationException subclass of ConstraintViolationException with violations adapted to MessageSource resolvable codes, and to Errors instances for @Valid arguments with cascaded violations; see 29825 and umbrella issue 30645.
Support for resource patterns in @PropertySource; see 21325.
Support for Iterable and MultiValueMap binding in BeanWrapper and DirectFieldAccessor; see 907 and 26297.
Revised Instant and Duration parsing (aligned with Spring Boot); see 22013.
Spring AOP now supports Kotlin Coroutines; see 22462.
ControlFlowPointcut has been revised to make its internals more open to extension by subclasses.
ControlFlowPointcut now provides built-in pattern matching support for method names, analogous to the pattern matching support in NameMatchMethodPointcut. Users can provide one or more method name patterns when constructing a ControlFlowPointcut. Alternatively, subclasses can override one of the new protected isMatch(...) methods – for example, to support regular expressions instead of simple pattern matching.
New getMergedRepeatableAnnotationAttributes() method in AnnotatedTypeMetadata that provides dedicated support for finding merged repeatable annotation attributes with full @AliasFor semantics.
Spring Expression Language (SpEL)
Numerous improvements to the SpEL Language Reference, including but not limited to:
Supported letters in variable names
Limitation regarding minimum values for numeric literals
Safe navigation support for selection and projection
Safe navigation semantics within compound expressions
Official documentation of the power operator, custom overloading operators, between operator, increment and decrement operators, as well as the repeat and character subtraction operators for strings
Numerous bug fixes.
Improved support for constructor and method invocations that use varargs.
The maximum length of a SpEL expression used in an ApplicationContext is now configurable via the spring.context.expression.maxLength Spring property.
Support for letters other than A-Z in property/field/variable names in SpEL expressions; see 30580.
Support for registering a MethodHandle as a SpEL function; see related documentation.
Data Access and Transactions
Common TransactionExecutionListener contract with beforeBegin/afterBegin, beforeCommit/afterCommit and beforeRollback/afterRollback callbacks triggered by the transaction manager (for thread-bound as well as reactive transactions); see 27479.
@TransactionalEventListener and TransactionalApplicationListener always run in the original thread, independent from an async multicaster setup; see 30244.
@TransactionalEventListener and TransactionalApplicationListener can participate in reactive transactions when the ApplicationEvent gets published with the transaction context as its event source; see 27515.
A failed CompletableFuture triggers a rollback for an async transactional method; see 30018.
DataAccessUtils provides various optionalResult methods with a java.util.Optional return type; see 27735.
The new JdbcClient provides a unified facade for query/update statements on top of JdbcTemplate and NamedParameterJdbcTemplate, with flexible parameter options as well as flexible result retrieval options; see 30931.
SimplePropertyRowMapper and SimplePropertySqlParameterSource strategies for use with JdbcTemplate/NamedParameterJdbcTemplate as well as JdbcClient, providing flexible constructor/property/field mapping for result objects and named parameter holders; see 26594.
SimpleJdbcInsert now provides support for quoted identifiers which can be enabled via the new usingQuotedIdentifiers() builder method.
SQLExceptionSubclassTranslator can be configured with an overriding customTranslator; see 24634.
The R2DBC DatabaseClient provides bindValues(Map) for a pre-composed map of parameter values and bindProperties(Object) for parameter objects based on bean properties or record components; see 27282.
The R2DBC DatabaseClient provides mapValue(Class) for plain database column values and mapProperties(Class) for result objects based on bean properties or record components; see 26021.
BeanPropertyRowMapper and DataClassRowMapper available for R2DBC as well; see 30530.
JpaTransactionManager with HibernateJpaDialect translates Hibernate commit/rollback exceptions to DataAccessException subclasses wherever possible, e.g. to CannotAcquireLockException, aligned with the exception hierarchy thrown from persistence exception translation for repository operations; see 31274 for the primary motivation.
Web Applications
Spring MVC and WebFlux now have built-in method validation support for controller method parameters with @Constraint annotations. That means you no longer need @Validated at the controller class level to enable method validation via an AOP proxy. Built-in method validation is layered on top of the existing argument validation for model attribute and request body arguments. The two are more tightly integrated and coordinated, e.g. avoiding cases with double validation. See the upgrade notes for migration details and umbrella issue 30645 for all related tasks and feedback.
Method validation is supported with method parameters that are collections, arrays, or maps of objects.
The HandlerMethodValidationException raised by the new built-in method validation exposes a Visitor API to process validation errors by controller method parameter type, e.g. @RequestParameter, @PathVariable, etc.
MethodValidationInterceptor supports validation of Mono and Flux method parameters; see issue 20781.
Spring MVC raises NoHandlerFoundException by default if there is no matching handler or ResponseStatusException(NOT_FOUND) if there is no matching static resource, and also handles these with the aim of consistent handling for 404 errors out of the box, including RFC 7807 responses; see 29491.
ErrorResponse allows customization of ProblemDetail type via MessageSource and use of custom ProblemDetail through its builder.
Spring MVC resets the Servlet response buffer prior to handling an error and rendering an error response.
DataBinder now supports constructor binding where argument values are looked up through a NameResolver (e.g. in the HTTP request parameters map), and those lookups can be customized through an @BindParam annotation. This also supports nested object structures through the invocation of constructors necessary to initialize constructor parameters. The feature is integrated in the data binding of Spring MVC and WebFlux and provides a safer option for data binding of expected parameters only; see Model Design for more details. Spring MVC and WebFlux now support data binding via constructors, including nested objects constructors
@ControllerAdvice and @RestControllerAdvice can now specify custom component names via their new name attributes.
WebFlux provides an option for blocking execution of controller methods with synchronous signatures on a different Executor such as the VirtualThreadTaskExecutor; see Blocking Execution in the reference documentation.
SseEmitter now formats data with newlines according to the SSE format.
New RestClient, a synchronous HTTP client that offers an API similar to WebClient, but sharing infrastructure with the RestTemplate; see 29552.
Jetty-based ClientHttpRequestFactory for use with RestTemplate and RestClient; see 30564.
JDK HttpClient-based ClientHttpRequestFactory for use with RestTemplate and RestClient; see 30478.
Reactor Netty-based ClientHttpRequestFactory for use with RestTemplate and RestClient; see 30835.
Improved buffering in various ClientHttpRequestFactory implementations; see 30557.
HTTP Interface client with built-in adapters for the new RestClient and RestTemplate in addition to the existing adapter for the reactive WebClient.
HTTP Interface client supports MultipartFile as an input method parameter.
HTTP Interface client supports UriBuilderFactory as an input method parameter to use instead of the one the underlying client is configured with – for example, if it's necessary to vary the baseUri dynamically.
The @HttpExchange annotation used on HTTP interface methods is now supported for server-side handling in Spring MVC and WebFlux as an alternative to @RequestMapping; see @HttpExchange for more details and guidance.
JVM checkpoint restore support added to Reactor Netty-based ClientHttpRequestFactory for use with RestTemplate and RestClient and to ClientHttpConnector for use with WebClient; see 31280, 31281, and 31180.
General Coroutines support revision in WebFlux, which includes CoroutineContext propagation in CoWebFilter, CoroutineContext propagation in coRouter DSL with filter, a new context function in coRouter DSL, support for @ModelAttribute with suspending function in WebFlux, and consistent usage of the Mono variant of awaitSingle().
Support for Kotlin parameter default and optional values in HTTP handler methods; see 21139 and 29820.
Messaging Applications
STOMP messaging supports a new preserveReceiveOrder config option for ordered processing of messages received from a given client. That's in addition to the existing preservePublishOrder flag for messages published to clients; see the Order of Messages section of the reference docs.
The @RSocketExchange annotation used on RSocket interface methods is now supported for responder-side handling as an alternative to @MessageMapping; see @RSocketExchange for more details and guidance.
Interface parameter annotations are detected for messaging handler methods as well (analogous to web handler methods).
The SpEL-based selector header support in WebSocket messaging is now disabled by default and must be explicitly enabled; see 30550 and the upgrade notes for migration details.
Observability support for JMS: Spring Framework now produces observations when publishing messages with JmsTemplate and when processing messages with MessageListener or @JmsListener; see the reference docs section and issue 30335.
Testing
ApplicationContext failure threshold support: avoids repeated attempts to load a failing ApplicationContext in the TestContext framework, based on a failure threshold which defaults to 1 but can be configured via a system property; see related documentation.
@⁠SpringJUnitConfig and @⁠SpringJUnitWebConfig now declare loader attributes that support custom ContextLoader configuration.
A ContextCustomizerFactory can now be registered for a particular test class via the new @ContextCustomizerFactories annotation.
Numerous enhancments for @TestPropertySource:
Support for resource patterns (i.e., wildcards) in locations.
Multiple inlined properties can be supplied via a single text block.
Property file encoding can be configured via the new encoding attribute.
A custom PropertySourceFactory can be configured via the new factory attribute in order to support custom property file formats such as JSON, YAML, etc.
Support for recording asynchronous events with @RecordApplicationEvents; see 30020.
Record events from threads other than the main test thread.
Assert events from a separate thread – for example with Awaitility.
When used with JUnit Jupiter, @​BeforeTransaction and @​AfterTransaction methods can now make use of parameter injection to have Spring components (such as an @Autowired DataSource) injected directly into the method.
JdbcTestUtils has new overloaded methods that accept a JdbcClient instead of JdbcOperations.
MockMvc now supports initialization of filters with init parameters and mapping to specific dispatch types.
MockMvcWebTestClient now supports the RequestPostProcessor hook which can, for example, allow varying user identity across tests; see 31298.
MockRestServiceServer supports the new RestClient in addition to the RestTemplate.
Support for null in MockHttpServletResponse.setCharacterEncoding(); see 30341.
Errors encountered during build-time AOT processing now cause the build to fail immediately. This behavior can be disabled by setting the spring.test.aot.processing.failOnError property to false. See the upgrade notes for migration details.
New @⁠DisabledInAotMode annotation that can be used to disable AOT build-time processing of a test's ApplicationContext and to disable an entire test class or a single test method at run time when the test suite is run with AOT optimizations enabled.
@Resource may now be used for dependency injection in test classes when running in AOT mode.
