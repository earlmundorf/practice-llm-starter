Upgrading From Spring Framework 6.1
Baseline Upgrades
Spring Framework 6.2 raises its minimum requirements with the following libraries:

For GraalVM native image support only, Hibernate 6.5
FreeMarker 2.3.33
HtmlUnit 4.2
Codecs and converters now officially support Protobuf 4.x, raising our baseline to Protobuf 3.29.
We also recommend an upgrade to Jackson 2.18/2.19 while preserving runtime compatibility with Jackson 2.15+ for the time being.

Removed APIs
Several deprecated classes, constructors, and methods have been removed across the code base. See 30608, 31492, and 33123.

Core Container
6.2 comes with a slightly revised autowiring algorithm where among a set of candidate beans that match by type, parameter name matches and @Qualifier("...") matches (against the target bean name) overrule @jakarta.annotation.Priority ranking whereas they were previously checked the other way around. That said, since we do not recommend mixing and matching those qualification mechanisms and generally do not recommend @Priority for identifying single candidates (rather just for ranking multiple candidates in an injected collection), we do not expect common regressions here. Note that @Primary beans always come first (and as a side note, 6.2 introduces the notion of @Fallback beans as well).

The core container will now consistently reject invalid @Configuration class setups, For example:

a @Bean method that declares a void return type
a @Bean method that is also annotated with @Autowired
6.2 also comes with deeper generic type matching. If an injection point that previously matched does not match anymore, double-check your generic signatures at that injection point (e.g. your constructor argument) and for the bean type on the bean definition (e.g. the return type of your @Bean method). Spring is effectively less lenient in accepting fallback matches now, insisting on the resolvable part of the type signature to match even if the remaining part is leniently accepted with unresolvable type variables or wildcards.

6.2 revises the core container's internal locking arrangement for a mix of strict and lenient locking. Background bean initialization (as mentioned below) always operates with lenient locking, next to the main bootstrap thread which applies strict locking. For any other threads hitting the application context at startup, the container tries to infer appropriate locking with a preference for lenient; as of 6.2.6, this can be overridden with spring.locking.strict=true (as a JVM system property or in a spring.properties file in the root of the classpath) to restore previous behavior.

Classpath scanning comes with internal jar caching now which is beneficial for repeated component scan attempts. However, this can cause regressions if such caching is unexpected: As of 6.2.6, you may revert to the previous behavior through setUseCaches(false) on PathMatchingResourcePatternResolver.

Component scanning happens early in the BeanFactory initialization and, as such, is not suitable to be guarded by a condition that is evaluated late. We now fail hard if you use @ComponentScan with a REGISTER_BEAN condition (such as Spring Boot's @ConditionalOnBean).

We've also made it clearer that bean definition overriding is discouraged in production code, and the container now logs each override at INFO level. While not recommended, you can silence those logs by setting the allowBeanDefinitionOverriding flag to true on the bean factory or application context before it is refreshed.

Property Placeholder Resolution
The parser for property placeholders has been completely rewritten to be as lenient as possible. As a result, certain constructs that previously worked by accident rather than by design are no longer possible.

A typical placeholder can be described as ${key:default} where default is evaluated if key is not present. Placeholder keys that have the : character in them need to be escaped if a literal resolution is expected.

For instance, if a property source can resolve sub://host to example.com, the placeholder should be written as ${sub\://host} (assuming \ being configured as the escape character). Note that the backslash must be doubled in configuration properties:

my.host=${sub\\://host}
If you can control the keys, we strongly encourage you to migrate to a format where : isn't used as part of the key.

As of 6.2, the placeholder can also be escaped, e.g. This is a \\${test}! evaluates to This is ${test}! in case you need to use the placeholder syntax literally. If you used the escaped character right before the placeholder, you will need to modify your configuration structure to move \\ in the value itself.

Spring Expression Language (SpEL)
PropertyAccessor implementations that specify target types for which they should apply now properly take precedence over generic, fallback property accessors such as the ReflectivePropertyAccessor. Consequently, the order in which accessors are evaluated may change when upgrading to Spring Framework 6.2. If you notice unexpected behavior for property access in SpEL expressions, you may need to revise the canRead() and canWrite() implementations of the property accessors used in your application or register accessors in a different order.

HTTP Clients
The behavior of the RestClient API has been changed to better align with other clients and to fix observability issues. Previously, writing the following would "fire and forget" an HTTP request and would leave observation scopes hanging:

ResponseSpec spec = restClient.get().uri("/spring").retrieve();
As of 6.2, this is now a no-op and applications must invoke a terminal operation on the ResponseSpec to have any side effect. For the previous case, developers will need to update their code to:

ResponseEntity<Void> response = restClient.get().uri("/spring").retrieve().toBodilessEntity();
Web Applications
Static resource locations configured as a String now have a trailing slash appended if not present. This is necessary because otherwise the path segment after the last slash is dropped when appending the request path with Resource#createRelative. This does not apply to static resource locations configured as a Resource.

org.webjars:webjars-locator-core support implemented in WebJarsResourceResolver is deprecated due to efficiency issues as of Spring Framework 6.2 and is superseded by org.webjars:webjars-locator-lite support implemented in LiteWebJarsResourceResolver.

WebFlux endpoints returning empty response bodies (like Mono.empty()) will not write "Content-Type" headers anymore as this was an invalid behavior (see #32622).

Messaging Applications
The JMS DefaultMessageListenerContainer comes with revised idleReceivesPerTaskLimit semantics when using its default executor: Core threads always stay alive now, with only surplus consumers (between concurrentConsumers and maxConcurrentConsumers) timing out after the specified number of idle receives. Only in combination with a maxMessagesPerTask does idleReceivesPerTaskLimit have an effect on core consumers as well, as inferred for an external thread pool for dynamic rescheduling of all consumer tasks.

STOMP over WebSocket configuration now orders components it declares at 0 in order to be ahead of Boot's WebServerStartStopLifecycle default phase. See #27519

Testing
Support for HtmlUnit has moved to a new major release that requires some changes when upgrading, see Migrating from HtmlUnit 2.x.x to HtmlUnit 3.x.x for additional details. If you are using HtmlUnit with Selenium, please note that the coordinates of the driver have changed, and the version now matches the Selenium version: org.seleniumhq.selenium:htmlunit3-driver:X.Y.Z, where X.Y.Z is your Selenium version.

New and Noteworthy
Support for escaping property placeholders
Property placeholders are a way to replace a property from the environment in an arbitrary String. Assuming that customer.name is set to "John Smith" in the Environment, "Customer ${customer.name}" would resolve to "Customer John Smith". There are corner cases where you’d like to retain the original value rather than having it resolved. Spring Framework 6.2 allows you to escape a placeholder using a configurable escape character (backslash by default). Taking our previous example, "Customer \${customer.name}" resolves now to "Customer ${customer.name}".

Note that the backslash must be doubled in configuration properties.

The escaping applies to the separator as well, see the "Property Placeholder Resolution" section above.

Support for fallback beans
A fallback bean is used if no bean of that type has been provided. This is essentially a companion of @Primary without the trade-off of having to specify it. Consider that a component requires MyService to be defined. You can provide a default implementation for the service, but you’d like that if a user specifies one, it can be injected by type transparently. So far, the user had to configure their specific bean with @Primary to make sure it is used, since two beans of that type are defined now.

As of Spring Framework 6.2.0 you can craft your configuration with @Fallback:

@Configuration
class MyConfiguration {

	@Bean
	MyComponent myComponent(MyService service) {
    	//...
	}

	@Bean
	@Fallback
	MyService defaultMyService() {
    	//...
	}

}
If no other MyService bean is defined, defaultMyService is used. Otherwise, the container will pick transparently the one that’s been defined externally.

See the dedicated section in the reference documentation.

Background bean initialization
Individual beans can be initialized in the background using the newly introduced bootstrap attribute.

@Configuration
class MyConfiguration {

    @Bean(bootstrap = BACKGROUND)
    MyExpensiveComponent myComponent() {
   	 ...
    }

}
Check the reference guide for more details about this new feature.

Enhanced indexing support in SpEL
The Spring Expression Language (SpEL) now offers first-class support for indexing into custom structures via new IndexAccessor and CompilableIndexAccessor SPIs plus a built-in ReflectiveIndexAccessor implementation of those SPIs (see reference documentation), and SpEL now provides safe navigation support for indexing into arrays, collections, strings, maps, objects, and custom structures (see reference documentation). Furthermore, the documentation for SpEL's property navigation and indexing support has been fully revised along with official documentation for indexing into strings and objects in SpEL expressions.

Bean overriding in tests, @TestBean, @MockitoBean, and @MockitoSpyBean
See our new reference documentation section on these features and the dedicated blog post published during the milestones phase.

AssertJ support for MockMvc
We love AssertJ! While Spring Boot has already jumped on that train a while ago and provides several testing facilities using it, the framework team has been more conservative. At the same time, we recognize that our Hamcrest support may not fit everyone’s needs: the use of static imports make the API less discoverable and writing custom assertions is harder. Spring Framework now provides an exhaustive support for testing your web application with MockMvc and AssertJ.

Building an MvcTester instance is more straightforward, with dedicated factory methods on the class itself. If you have a WebApplicationContext handy, this is as easy as MvcTester.from(webApplicationContext). If you want to test only a controller in a unit test, you can do so as follows:

MvcTester mvc = MvcTester.of(List.of(new HelloController()), builder ->
builder.defaultRequest(get("/hello").accept(MediaType.APPLICATION_JSON)).build());
Once you have an instance you can perform your usual requests and wrap that in AssertJ’s standard assertThat:

assertThat(mvc.perform(get("/vehicle/{id}", "12").accept(MediaType.TEXT_PLAIN)))
        .hasStatusOk()
        .body().isEqualTo("Honda Civic");
This covers the same features as the existing Hamcrest matchers, and extends it with advanced JSON support, for instance:

assertThat(mvc.perform(get("/message")))
        .body().json()
        .isLenientlyEqualTo(new ClassPathResource("samples/message.json"));
See the reference documentation for more on that.

Dynamic properties can now be registered from within a test's ApplicationContext
You can now register dynamic properties in tests by contributing DynamicPropertyRegistrar beans to the context.

See related documentation.

Constructor Data Binding to Lists, Maps, and Arrays
While setter binding is driven by request values, and may need to be restricted through allow and disallow lists, constructor binding is driven by the constructor arguments that already declare exactly and only what is expected. In 6.2 constructor binding has been enhanced to support List, Map, and array arguments giving it parity with setter binding.

See #32426

Data Binding from Headers
In addition to Servlet request parameters, multiparts, and path variables, data binding now also supports binding request header values to @ModelAttribute controller method parameters.

See #32676.

Fragment Rendering
Spring MVC and WebFlux support rendering multiple views in one request, or to create a stream of rendered views. This helps to support HTML-over-the-wire libraries such as htmx.org and @hotwired/turbo.

See HTML Fragments in the reference documentation.

Content negotiation for @ExceptionHandler methods
@ExceptionHandler methods now support content negotiation during error handling. It allows selecting a content type for error responses depending on what the client requested.

Here's a code snippet showing this feature in action:

@ExceptionHandler(produces = "application/json")
public ResponseEntity<ErrorMessage> handleJson(IllegalArgumentException exc) {
    return ResponseEntity.badRequest().body(new ErrorMessage(exc.getMessage(), 42));
}

@ExceptionHandler(produces = "text/html")
public String handle(IllegalArgumentException exc, Model model) {
    model.addAttribute("error", new ErrorMessage(exc.getMessage(), 42));
    return "errorView";
}
Here, automated clients will get a JSON response, while browsers will display an HTML error page with custom messages.

URL Parsing
Two new URL parser implementations replace the regular expression based parsing that led to vulnerability reports such as CVE-2024-22262 and others after it. The parser implementation used by default follows closely RFC 3986 syntax, in effect expecting URL's to be well formed. The other parser follows the URL Living URL standard, and provides lenient handling of a wide range of user typed URL's, which allows parsing URL's in the same way that browsers do, and is important in scenarios where a server application parses a user provided URL that is then returned to and may be used by a browser (e.g. redirect or as a link in HTML). See URI Parsing in the reference documentation.

// This is using the "strict" RFC parser
UriComponents uriComponents = UriComponentsBuilder.fromUriString(uri, ParserType.RFC).build();
// Using the "living URL" standard is more flexible and behaves similarly to browsers
UriComponents uriComponents = UriComponentsBuilder.fromUriString(uri, ParserType.WHAT_WG).build();
UrlHandlerFilter for trailing slash match
Spring Framework recently deprecated the trailing slash match option in PathMatchConfigurer, the Spring community requested a way to gracefully handle this transition period in large applications. The new UrlHandlerFilter Servlet and reactive filters will help redirecting or rewriting incoming URLs with a trailing slash "/blog/blog-title/" to "/blog/blog-title".

Check out the reference documentation for this.

Easier reflection hint registration for Native apps
We have significantly improved the developer experience of registering reflection hints. @RegisterReflection has been introduced to easily register hints against arbitrary data types, and @ReflectionScan lets you opt-in for scanning of any reflection hints on arbitrary classes, not only Spring beans. See the reference documentation section for this.

Other
Apart from the changes listed above, there have also been a lot of minor tweaks, improvements, and bug fixes including:

This release includes a revision of the autowiring algorithm – for example, for consistent generic type matching and for faster resolution of name-based matches. See #28122 and #17677.
TaskDecorator support for scheduled tasks; TaskDecorator is used for scheduled tasks as well, harmonizing task execution and scheduling in the process. See #23755.
The Task and ScheduledTask types now expose metadata about their execution: last execution time and outcome, next scheduled execution time, etc.
@DurationFormat and @Scheduled now support "simple" duration formats such as "30s" or "2h30m".
Improved Spring Expression Language (SpEL) compilation support for constructor and method invocations that use varargs as well as for expressions that index into arrays and lists with an Integer. In addition, methods in SpEL expressions are now invoked via a public interface or public superclass whenever possible.
Interception mechanism for RFC 7807 error detail responses #31970
ResponseBodyEmitter now allows the registration of multiple state listeners, which is useful if your application maintains an ad hoc "keep alive" mechanism for your streaming sessions.
ServerResponse now provides more ways to send data streams for WebMvc functional endpoints. Although Server Sent Events were already supported, this enables support for other streaming protocols.
The new CHIPS feature deployed by browser vendors requires changes in applications using third-party cookies. Reactive web servers (except Undertow) now support Partitioned cookies.
You can now test WebMvc.fn endpoints with MockMvcWebTestClient, just as you could already for your annotated controllers. See #30477.
