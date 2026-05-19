---
source_topic: SPRING
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/4f86494bd4574ae4b5d29f7dc9b8e5b6.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: 4f86494bd4574ae4b5d29f7dc9b8e5b6
sap_version: v2211
fetched: 2026-04-30
title: "Spring Framework 6.2.x"
---

> Mirror of [Spring Framework 6.2.x](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/4f86494bd4574ae4b5d29f7dc9b8e5b6.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loio4f86494bd4574ae4b5d29f7dc9b8e5b6" class="page concept - topic-topic concept-concept">

# Spring Framework 6.2.x

<div class="body conbody">

Review all manual steps related to Spring 6 update and adapt your <span class="ph pname" translate="no">SAP Commerce Cloud</span> implementation accordingly.

</div>

<div class="related-links">

1.  **<a href="7ed18f701d32426291d0a72324fff6b9.html#loio7ed18f701d32426291d0a72324fff6b9" class="link">Adapting to PathPatternParser New Default URL Matcher</a>**\
    <div class="linkdesc">

    Adapt your Spring application to use <span class="keyword apiname">PathPatternParser</span>, the new default URL matcher introduced in Spring 6.0, which replaces <span class="keyword apiname">AntPathMatcher</span>.

    </div>
2.  **<a href="8b6e4dc334b34946a158cdaba06e1a0d.html" class="link">Adapting to Strict URL Matching</a>**\
    <div class="linkdesc">

    As of Spring Framework 6.0, the default behavior for matching trailing slash in URL paths has changed. The trailing slash matching configuration option has been deprecated and its default value has been set to false.

    </div>
3.  **<a href="6da5d917fee04b49b4ba7572c045f884.html" class="link">Adjusting to jakarta.servlet.Filter API</a>**\
    <div class="linkdesc">

    With the <span class="ph pname" translate="no">SAP Commerce Cloud</span> Framework Update, <span class="ph filepath">javax.servlet.Filter</span> was updated to <span class="ph filepath">jakarta.servlet.Filter</span>.

    </div>
4.  **<a href="7fe2cdf0bf1a4dd28271a086fca7e18d.html" class="link">Using StandardServletMultipartResolver for Multipart Data</a>**\
    <div class="linkdesc">

    Prepare for <span class="keyword apiname">StandardServletMultipartResolver</span> usage in your <span class="ph pname" translate="no">SAP Commerce Cloud</span> implementation after the removal of <span class="keyword apiname">CommonsMultipartResolver</span> that was used in Spring versions prior to 6.1.

    </div>
5.  **<a href="102c41e92b6842d395e3ca7aed0c9618.html" class="link">Updating spring-security-saml2-service-provider to 6.4.x</a>**\
    <div class="linkdesc">

    For compatibility reasons with Spring 6, the version of the `spring-security-saml2-service-provider` library version has been updated from 5.8.11 to 6.4.1.

    </div>
6.  **<a href="86dd6f248abf495ab5b3dd8cfb7de05a.html" class="link">Updating to SiteMesh 3.2.x</a>**\
    <div class="linkdesc">

    SiteMesh 3.0-alpha is not fully compatible with Jakarta EE 10 and was upgraded to version 3.2.1 with the Spring 6 update.

    </div>
7.  **<a href="3b46648018bf4bf4b8e50ec46dda36fe.html" class="link">Adding Missing MVC Configuration in Web Application Context</a>**\
    <div class="linkdesc">

    Ensure Spring MVC and Security share the same web application context to avoid missing <span class="keyword apiname">mvcHandlerMappingIntrospector</span> bean issues introduced in Spring 6.x.

    </div>
8.  **<a href="c7ee7ccdec864ff7af45ce189fe3313a.html" class="link">Adapting to Removal of the @Required Annotation</a>**\
    <div class="linkdesc">

    With Spring Framework 6 update, the `@Required` annotation is no longer used. If you decided to utilize the OpenRewrite recipe, all the invocations of `@Required` annotation were automatically removed from the code.

    </div>
9.  **<a href="414b4f1cd93b463e81984fa61f22568f.html" class="link">Adapting codebase after removal of SocketUtils Class with Spring 6.X releases</a>**\
    <div class="linkdesc">

    Spring removed the deprecated <span class="ph filepath">org.springframework.util.SocketUtils</span> class with 6.X releases.

    </div>
10. **<a href="44f6d669a733484aa557ee4ccad55af8.html" class="link">Resolving BeanPostProcessorChecker Warnings</a>**\
    <div class="linkdesc">

    Adapt to new warning message logic introduced in BeanPostProcessorChecker with Spring Framework 6.

    </div>
11. **<a href="cc32013437724c0689ac83eb3d955ccc.html" class="link">Configure Extension Load Order to Support Fixed Spring 6 Alias Overriding Behavior</a>**\
    <div class="linkdesc">

    Spring Framework 6 resolves inconsistencies in bean overriding, specifically involving conflicts between bean names and aliases.

    </div>
12. **<a href="bccb85f337cb44359f137bc2bece4d0e.html" class="link">Adapting to @TestExecutionListener Annotation Changes</a>**\
    <div class="linkdesc">

    Spring 6.0 changed how test listeners are registered in certain base classes. Some tests may need updates to keep the expected behavior when updating from earlier versions.

    </div>
13. **<a href="9c70e5d39934431b951e669684f246d3.html" class="link">Replacing HttpPutFormContentFilter with FormContentFilter</a>**\
    <div class="linkdesc">

    If your extensions are generated from <span class="keyword apiname">ycommercewebservices</span> template, perform the following steps to replace <span class="keyword apiname">HttpPutFormContentFilter</span> with <span class="keyword apiname">FormContentFilter</span>.

    </div>
14. **<a href="a960d820c27e4d1fbeafb6c20e568c09.html" class="link">Loading OCC Swagger Properties</a>**\
    <div class="linkdesc">

    If your extensions are generated from the <span class="keyword apiname">ycommercewebservices</span> template, perform the following steps to load OCC Swagger properties.

    </div>
15. **<a href="06fd4ce1e10848948ef84694b764bca1.html" class="link">@ModelAttribute Annotation Changes</a>**\
    <div class="linkdesc">

    Spring provides a `@ModelAttribute` annotation that allows binding elements for the HTTP request to the model object.

    </div>
16. **<a href="8687bc8fca2e407697079d5e587c5858.html" class="link">Consider Removing Separate DispatcherServlet Context</a>**\
    <div class="linkdesc">

    When configuring a Spring web application, it's worth evaluating how servlet contexts are defined.

    </div>
17. **<a href="0a4cbae7db3a4fd18c2fe7097db9663c.html" class="link">Integration 6.4.x Update</a>**\
    <div class="linkdesc">

    Spring integration version was updated from 5.5.20 to \>= 6.4.1.

    </div>
18. **<a href="bd309a9f5199432891d0cb433b03bf3c.html" class="link">Parameter Name Retention for Dependency Injection</a>**\
    <div class="linkdesc">

    Spring framework now relies on Java and Groovy compilers to retain parameter names in bytecode for dependency injection, removing the need for bytecode parsing.

    </div>
19. **<a href="f53b87feec67434999c6d7a6b846e731.html" class="link">Resolving Redirection Issues of request-matcher</a>**\
    <div class="linkdesc">

    In Spring Security 6.x, the default policy of the `request-matcher` has been changed from `AntPathRequestMatcher` to `MvcPathRequestMatcher`, causing 302 redirections of static resources.

    </div>
20. **<a href="5fe2a1a6280d4c8c803e38509a50c629.html" class="link">Fixing "No visible WebSecurityExpressionHandler" Error</a>**\
    <div class="linkdesc">

    This document provides steps to resolve the <span class="keyword apiname">java.io.IOException: No visible WebSecurityExpressionHandler instance could be found</span> error when starting the server, which occurs due to missing security expression handler configuration for JSP 'authorize' tags.

    </div>
21. **<a href="f5f321a8260e43618fd151913baab658.html" class="link">Updating Spring Security Intercept-URL Configuration for Empty Expression Strings</a>**\
    <div class="linkdesc">

    This document provides guidance on resolving the "expressionString cannot be empty" error during server startup by properly configuring intercept-url rules in Spring Security 6.

    </div>
22. **<a href="4602e7cfda1d412f9254d09bbe305924.html" class="link">Optimizing SequenceSizeReleaseStrategy Configuration for Large Groups</a>**\
    <div class="linkdesc">

    This document provides steps to resolve performance warnings related to <span class="keyword apiname">SequenceSizeReleaseStrategy</span> by switching to the more efficient <span class="keyword apiname">SimpleSequenceSizeReleaseStrategy</span> implementation.

    </div>
23. **<a href="753b7169ecd64e3992ba974dfbe15ec2.html" class="link">Resolving DispatcherServlet Exceptions During Server Startup</a>**\
    <div class="linkdesc">

    Update the Spring MVC configuration to ensure compatibility with Spring 6 by explicitly defining the use of `AntPathMatcher` and setting the `patternParser` property to `null` for all handler mapping beans.

    </div>
24. **<a href="0cb4926b2a9944679b27a5db630bb163.html" class="link">Adding xxxMapping Annotation</a>**\
    <div class="linkdesc">

    Add annotation of <span class="ph sap-technical-name sap-technical-name">xxxMapping</span> to <span class="keyword apiname">chineseaddressaddon</span>, <span class="keyword apiname">chinesepaymentaddon</span>, <span class="keyword apiname">chineselogisticaddon</span>, and <span class="keyword apiname">chineseprofileaddon</span> to fix certain issues in cx_china recipe.

    </div>

</div>

</div>

</div>

<div class="clear">

</div>

</div>

</div>

</div>
