Baseline upgrades
Spring Framework 6.0 raises the minimum requirements to Java 17+ and Jakarta EE 9+.

Removed APIs
RPC-style remoting that has been officially and/or effectively deprecated for several years has been removed. This impacts Hessian, HTTP Invoker, JMS Invoker, and JAX-WS support, see 27422.

Dedicated EJB access has also been removed as part of this effort. If you need to lookup an EJB, use JNDI directly via JndiObjectFactoryBean or <jee:jndi-lookup>.

Joda-Time support in org.springframework.format.datetime has been removed. Please migrate to the java.time types.

The org.springframework.cache.ehcache package has been removed as it was providing support for Ehcache 2.x - with this version, net.sf.ehcache is using Java EE APIs and is about to be End Of Life'd. Ehcache 3 is the direct replacement. You should revisit your dependency management to use org.ehcache:ehcache (with the jakarta classifier) instead and look into the official migration guide or reach out to the ehcache community for assistance. We did not replace org.springframework.cache.ehcache with an updated version, as using Ehcache through the JCache API or its new native API is preferred.

Core Container
The JSR-330 based @Inject annotation is to be found in jakarta.inject now. The corresponding JSR-250 based annotations @PostConstruct and @PreDestroy are to be found in jakarta.annotation. For the time being, Spring keeps detecting their javax equivalents as well, covering common use in pre-compiled binaries.

The core container performs basic bean property determination without java.beans.Introspector by default. For full backwards compatibility with 5.3.x in case of sophisticated JavaBeans usage, specify the following content in a META-INF/spring.factories file which enables 5.3-style full java.beans.Introspector usage: org.springframework.beans.BeanInfoFactory=org.springframework.beans.ExtendedBeanInfoFactory

When staying on 5.3.x for the time being, you may enforce forward compatibility with 6.0-style property determination (and better introspection performance!) through a custom META-INF/spring.factories file: org.springframework.beans.BeanInfoFactory=org.springframework.beans.SimpleBeanInfoFactory

LocalVariableTableParameterNameDiscoverer is deprecated now and logs a warning for each successful resolution attempt (it only kicks in when StandardReflectionParameterNameDiscoverer has not found names). Compile your Java sources with the common Java 8+ -parameters flag for parameter name retention (instead of relying on the -debug compiler flag) in order to avoid that warning, or report it to the maintainers of the affected code. With the Kotlin compiler, we recommend the -java-parameters flag for completeness.

LocalValidatorFactoryBean relies on standard parameter name resolution in Bean Validation 3.0 now, just configuring additional Kotlin reflection if Kotlin is present. If you refer to parameter names in your Bean Validation setup, make sure to compile your Java sources with the Java 8+ -parameters flag.

ListenableFuture has been deprecated in favor of CompletableFuture. See 27780.

Methods annotated with @Async must return either Future or void. This has long been documented but is now also actively checked and enforced, with an exception thrown for any other return type. See 27734.

SimpleEvaluationContext disables array allocations now, aligned with regular constructor resolution.

Data Access and Transactions
Due to the Jakarta EE migration, make sure to upgrade to Hibernate ORM 5.6.x with the hibernate-core-jakarta artifact, alongside switching your javax.persistence imports to jakarta.persistence (Jakarta EE 9). Alternatively, consider migrating to Hibernate ORM 6.1 right away (exclusively based on jakarta.persistence, compatible with EE 9 as well as EE 10) which is the Hibernate version that Spring Boot 3.0 comes with.

The corresponding Hibernate Validator generation is 7.0.x, based on jakarta.validation (Jakarta EE 9). You may also choose to upgrade to Hibernate Validator 8.0 right away (aligned with Jakarta EE 10).

For EclipseLink as the persistence provider of choice, the reference version is 3.0.x (Jakarta EE 9), with EclipseLink 4.0 as the most recent supported version (Jakarta EE 10).

Spring's default JDBC exception translator is the JDBC 4 based SQLExceptionSubclassTranslator now, detecting JDBC driver subclasses as well as common SQL state indications (without database product name resolution at runtime). As of 6.0.3, this includes a common SQL state check for DuplicateKeyException, addressing a long-standing difference between SQL state mappings and legacy default error code mappings.

CannotSerializeTransactionException and DeadlockLoserDataAccessException are deprecated as of 6.0.3 due to their inconsistent JDBC semantics, in favor of the PessimisticLockingFailureException base class and consistent semantics of its common CannotAcquireLockException subclass (aligned with JPA/Hibernate) in all default exception translation scenarios.

For full backwards compatibility with database-specific error codes, consider re-enabling the legacy SQLErrorCodeSQLExceptionTranslator. This translator kicks in for user-provided sql-error-codes.xml files. It can simply pick up Spring's legacy default error code mappings as well when triggered by an empty user-provided file in the root of the classpath.

Web Applications
Due to the Jakarta EE migration, make sure to upgrade to Tomcat 10, Jetty 11, or Undertow 2.2.19 with the undertow-servlet-jakarta artifact, alongside switching your javax.servlet imports to jakarta.servlet (Jakarta EE 9). For the latest server generations, consider Tomcat 10.1 and Undertow 2.3 (Jakarta EE 10).

Several outdated Servlet-based integrations have been dropped: e.g. Apache Commons FileUpload (org.springframework.web.multipart.commons.CommonsMultipartResolver), and Apache Tiles as well as FreeMarker JSP support in the corresponding org.springframework.web.servlet.view subpackages. We recommend org.springframework.web.multipart.support.StandardServletMultipartResolver for multipart file uploads and regular FreeMarker template views if needed, and a general focus on REST-oriented web architectures.

As of Spring Framework 6.0, the trailing slash matching configuration option has been deprecated and its default value set to false. This means that previously, the following controller would match both "GET /some/greeting" and "GET /some/greeting/":

@RestController
public class MyController {

  @GetMapping("/some/greeting")
  public String greeting() {
    return "Hello";
  } 

}
As of this Spring Framework change, "GET /some/greeting/" doesn't match anymore by default and will result in an HTTP 404 error. Developers should instead configure explicit redirects/rewrites through a proxy, a Servlet/web filter, or even declare the additional route explicitly on the controller handler (like @GetMapping("/some/greeting", "/some/greeting/") for more targeted cases. Until your application fully adapts to this change, you can change the default with the following global Spring MVC configuration:

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
      configurer.setUseTrailingSlashMatch(true);
    }
  
}
Spring MVC and Spring WebFlux no longer detect controllers based solely on a type-level @RequestMapping annotation. That means interface-based AOP proxying for web controllers may no longer work. Please, enable class-based proxying for such controllers; otherwise the interface must also be annotated with @Controller. See 22154.

HttpMethod is now a class and no longer an enum. Though the public API has been maintained, some migration might be necessary (i.e. change from EnumSet<HttpMethod> to Set<HttpMethod>, use if  else instead of switch). For the rationale behind this decision, see 27697.

The Kotlin extension function to WebTestClient.ResponseSpec::expectBody now returns the Java BodySpec type and no longer uses the workaround type KotlinBodySpec. Spring 6.0 uses Kotlin 1.6, which fixed the bug that needed this workaround (KT-5464). This means that consumeWith is no longer available.

RestTemplate, or rather the HttpComponentsClientHttpRequestFactory, now requires Apache HttpClient 5.

The Spring-provided Servlet mocks (MockHttpServletRequest, MockHttpSession) require Servlet 6.0 now, due to a breaking change between the Servlet 5.0 and 6.0 API jars. They can be used for testing Servlet 5.0 based code but need to run against the Servlet 6.0 API (or newer) on the test classpath. Note that your production code may still compile against Servlet 5.0 and get integration-tested with Servlet 5.0 based containers; just mock-based tests need to run against the Servlet 6.0 API jar.

SourceHttpMessageConverter is not configured by default anymore in Spring MVC and RestTemplate. As a consequence, Spring web applications using javax.xml.transform.Source now need to configure SourceHttpMessageConverter explicitly. Note that the order of converter registration is important, and SourceHttpMessageConverter should typically be registered before "catch-all" converters like MappingJackson2HttpMessageConverter for example.

New and Noteworthy
JDK 17+ and Jakarta EE 9+ Baseline
Entire framework codebase based on Java 17 source code level now.
Migration from javax to jakarta namespace for Servlet, JPA, etc.
Runtime compatibility with Jakarta EE 9 as well as Jakarta EE 10 APIs.
Compatible with latest web servers: Tomcat 10.1, Jetty 11, Undertow 2.3.
Early compatibility with virtual threads (in preview as of JDK 19).
General Core Revision
Upgrade to ASM 9.4 and Kotlin 1.7.
Complete CGLIB fork with support for capturing CGLIB-generated classes.
Comprehensive foundation for Ahead-Of-Time transformations.
First-class support for GraalVM native images (see related Spring Boot 3 blog post).
Core Container
Basic bean property determination without java.beans.Introspector by default.
AOT processing support in GenericApplicationContext (refreshForAotProcessing).
Bean definition transformation based on pre-resolved constructors and factory methods.
Support for early proxy class determination for AOP proxies and configuration classes.
PathMatchingResourcePatternResolver uses NIO and module path APIs for scanning, enabling support for classpath scanning within a GraalVM native image and within the Java module path, respectively.
DefaultFormattingConversionService supports ISO-based default java.time type parsing.
Data Access and Transactions
Support for predetermining JPA managed types (for inclusion in AOT processing).
JPA support for Hibernate ORM 6.1 (retaining compatibility with Hibernate ORM 5.6).
Upgrade to R2DBC 1.0 (including R2DBC transaction definitions).
Aligned data access exception translation between JDBC, R2DBC, JPA and Hibernate.
Removal of JCA CCI support.
Spring Messaging
RSocket interface client based on @RSocketExchange service interfaces.
Early support for Reactor Netty 2 based on Netty 5 alpha.
Support for Jakarta WebSocket 2.1 and its standard WebSocket protocol upgrade mechanism.
General Web Revision
HTTP interface client based on @HttpExchange service interfaces.
Support for RFC 7807 problem details.
Unified HTTP status code handling.
Support for Jackson 2.14.
Alignment with Servlet 6.0 (while retaining runtime compatibility with Servlet 5.0).
Spring MVC
PathPatternParser used by default (with the ability to opt into PathMatcher).
Removal of outdated Tiles and FreeMarker JSP support.
Spring WebFlux
New PartEvent API to stream multipart form uploads (both on client and server).
New ResponseEntityExceptionHandler to customize WebFlux exceptions and render RFC 7807 error responses.
Flux return values for non-streaming media types (no longer collected to List before written).
Early support for Reactor Netty 2 based on Netty 5 alpha.
JDK HttpClient integrated with WebClient.
Observability
Direct Observability instrumentation with Micrometer Observation in several parts of the Spring Framework. The spring-web module now requires io.micrometer:micrometer-observation:1.10+ as a compile dependency.

RestTemplate and WebClient are instrumented to produce HTTP client request observations.
Spring MVC can be instrumented for HTTP server observations using the new org.springframework.web.filter.ServerHttpObservationFilter.
Spring WebFlux can be instrumented for HTTP server observations using the new org.springframework.web.filter.reactive.ServerHttpObservationFilter.
Integration with Micrometer Context Propagation for Flux and Mono return values from controller methods.
Testing
Support for testing AOT-processed application contexts on the JVM or within a GraalVM native image.
Integration with HtmlUnit 2.64+ request parameter handling.
Servlet mocks (MockHttpServletRequest, MockHttpSession) are based on Servlet API 6.0 now.
New MockHttpServletRequestBuilder.setRemoteAddress() method.
The four abstract base test classes for JUnit 4 and TestNG no longer declare listeners via @TestExecutionListeners and instead now rely on registration of default listeners.
