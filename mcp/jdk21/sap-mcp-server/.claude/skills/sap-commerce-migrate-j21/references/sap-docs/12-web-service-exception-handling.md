---
source_topic: EXCEPTION_ERROR_HANDLING_WEB_SERVICES
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/52632c5ae11f430baf05fff9e0d483fe.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: 52632c5ae11f430baf05fff9e0d483fe
sap_version: v2211
fetched: 2026-04-30
title: "Updating Exception and Error Handling for Webservices"
---

> Mirror of [Updating Exception and Error Handling for Webservices](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/52632c5ae11f430baf05fff9e0d483fe.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loio52632c5ae11f430baf05fff9e0d483fe" class="page concept - topic-topic concept-concept">

# Updating Exception and Error Handling for Webservices

<div class="body conbody">

Implement new exception and error handling beans and handlers for your webservices extensions.

<div id="loio52632c5ae11f430baf05fff9e0d483fe__section_tgl_lh5_rfc" class="section">

<div class="section section" type="Introduction of New Error Handling Beans for Webservices">

## Introduction of New Error Handling Beans for Webservices

To be consistent with the error-handling process of SAP Commerce Webservices, the `oAuth2AccessDeniedHandler` and `oAuth2AuthenticationEntryPoint` beans were defined in the <span class="ph filepath">webservicescommons/resources/webservicescommons/commons-security-spring.xml</span> file:

``` pre
<bean id="oAuth2AccessDeniedHandler" class="de.hybris.platform.webservicescommons.oauth2.OAuth2AccessDeniedHandler">
    <constructor-arg name="restHandlerExceptionResolver" ref="restHandlerExceptionResolver"/>
</bean>
 
<bean id="oAuth2AuthenticationEntryPoint"
      class="de.hybris.platform.webservicescommons.oauth2.OAuth2AuthenticationEntryPoint">
    <constructor-arg name="restHandlerExceptionResolver" ref="restHandlerExceptionResolver"/>
</bean>
```

The `oAuth2AuthenticationEntryPoint` bean extends the `EnrichingAccessDeniedHandler` and the `oAuth2AuthenticationEntryPoint` extends the `EnrichingAuthenticationEntryPoint` defined in platform's <span class="keyword apiname">resourceserver</span> extension. The beans were introduced because the `enrichingAuthenticationEntryPoint` and the `enrichingAccessDeniedHandler` beans defined in the <span class="keyword apiname">resourceserver</span> extension use the error-handling process provided in the Spring framework instead of the process used in the SAP Commerce Webservices.

</div>

</div>

<div id="loio52632c5ae11f430baf05fff9e0d483fe__section_q1b_nh5_rfc" class="section">

<div class="section section" type="Making the Error Handling Beans Availabe in the Web Application Context">

## Making the Error Handling Beans Availabe in the Web Application Context

To make the `oAuth2AccessDeniedHandler` and the `oAuth2AuthenticationEntryPoint` beans available in the web application context, you must configure a `restHandlerExceptionResolver` bean that implements the `org.springframework.web.servlet.HandlerExceptionResolver` interface. A `restHandlerExceptionResolver` bean should already be defined in the <span class="ph filepath">error-config-spring.xml</span> file of your webservices extensions.

The following code shows how to configure a `restHandlerExceptionResolver` bean that implements the `org.springframework.web.servlet.HandlerExceptionResolver` interface in the <span class="ph filepath">error-config-spring.xml</span> file. In the example, the bean is configured in the <span class="keyword apiname">ywebservices</span> extension:

``` pre
<bean id="restHandlerExceptionResolver" class="de.hybris.platform.webservicescommons.resolver.RestExceptionResolver"
    parent="wsBaseRestExceptionResolver">
    <property name="webserviceErrorFactory" ref="webserviceErrorFactory" />
    <property name="messageConverters" ref="jaxbMessageConverters" />
    <property name="extensionName" value="ywebservices" />
</bean>
```

</div>

</div>

<div id="loio52632c5ae11f430baf05fff9e0d483fe__section_nz2_nh5_rfc" class="section">

<div class="section section" type="Adopting the Error-Handling Process Used in the SAP Commerce Webservices">

## Adopting the Error-Handling Process Used in the SAP Commerce Webservices

To adopt the error-handling process used in the SAP Commerce Webservices, configure the `entry-point-ref` for the `oauth2-resource-server` and the `ref` for the `access-denied-handler` in the Spring Security configuration file of your web extension, for example, <span class="ph filepath">myWebservice-security-spring.xml</span>. The following code shows an example:

``` pre
<security:access-denied-handler ref="oAuth2AccessDeniedHandler" />
 
<security:oauth2-resource-server entry-point-ref="oAuth2AuthenticationEntryPoint">
```

</div>

</div>

<div id="loio52632c5ae11f430baf05fff9e0d483fe__section_uqt_nh5_rfc" class="section">

<div class="section section" type="Configuring Exception Handling">

## Configuring Exception Handling

You can configure exception mappings, such as exception messages and logging, to be used by the `oAuth2AccessDeniedHandler` and the `oAuth2AuthenticationEntryPoint` security handlers. You configure the exception mapping in the `project.properties` file of your webservices extension. The following code excerpt shows the configuration for the `AuthorizationDeniedException`, `AuthenticationCredentialsNotFoundException`, `AuthenticationCredentialsNotFoundException`, and the `InvalidBearerTokenException` exceptions:

``` pre
webservicescommons.resthandlerexceptionresolver.<yourextensionname>.AuthorizationDeniedException.logstack=false
webservicescommons.resthandlerexceptionresolver.<yourextensionname>.AuthorizationDeniedException.status=401
 
webservicescommons.resthandlerexceptionresolver.<yourextensionname>.AuthenticationCredentialsNotFoundException.logstack=false
webservicescommons.resthandlerexceptionresolver.<yourextensionname>.AuthenticationCredentialsNotFoundException.status=401
 
 
webservicescommons.resthandlerexceptionresolver.<yourextensionname>.InsufficientAuthenticationException.logstack=false
webservicescommons.resthandlerexceptionresolver.<yourextensionname>.InsufficientAuthenticationException.status=401
 
webservicescommons.resthandlerexceptionresolver.<yourextensionname>.InvalidBearerTokenException.logstack=false
webservicescommons.resthandlerexceptionresolver.<yourextensionname>.InvalidBearerTokenException.status=401
```

<div id="n0t" class="title">

**Note**

</div>

You can configure exception mapping, but you can't fully configure the response statuses to be used by the `oAuth2AccessDeniedHandler` and `oAuth2AuthenticationEntryPoint` security handlers. These handlers preserve the response status set by the `EnrichingAccessDeniedHandler` and `EnrichingAuthenticationEntryPoint` base classes respectively, if they are set. If they are not set, the response status set uses the response status set through the mapping. The response status codes are configured as in the previous version of the SAP Commerce, with the exception of the `oAuth2AccessDeniedHandler` and the `oAuth2AuthenticationEntryPoint` security handlers.

</div>

</div>

</div>

</div>

</div>

<div class="clear">

</div>

</div>

</div>

</div>
