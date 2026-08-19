---
source_topic: RESOURCE_SERVER
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/d92f552a2fbc47c59950e3f134fb67c2.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: d92f552a2fbc47c59950e3f134fb67c2
sap_version: v2211
fetched: 2026-04-30
title: "Moving to the New Resource Server"
---

> Mirror of [Moving to the New Resource Server](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/d92f552a2fbc47c59950e3f134fb67c2.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loiod92f552a2fbc47c59950e3f134fb67c2" class="page task - topic-topic task-task">

# Moving to the New Resource Server

<div class="body taskbody">

Adjust your resource server configuration to the new OAuth implementation.

<div class="section context">

<span id="steps" class="anchor"></span>

<div class="tasklabel">

## Context

</div>

To fully move to the new OAuth implementation, adjust your resource server configuration to the new version of the Spring Security library. Unless stated otherwise, all changes should be introduced in your Spring configuration file.

<div id="n0t" class="title">

**Note**

</div>

There are no changes to the configuration of the Swagger UI.

When migrating your resource server to the new implementation, check your extensions' dependency tree as some steps might have already been covered by an extension configuration that you have already included in your configuration, for example, in the <span class="keyword apiname yext">webservicescommons</span> and <span class="keyword apiname yext">apiframework</span> extensions.

</div>

<div class="section section procedure">

<span id="steps" class="anchor"></span>

<div class="ol steps tasklabel">

## Procedure

</div>

Import resourceserver-commons-spring.xml

1.  <span class="ph cmd">Remove the import of the old resource server definition and replace it with the import with a common Spring configuration.</span>
    <div class="itemgroup stepxmp">

    Previous Configuration

    ``` pre
    <beans:import resource="classpath*:oauth2-resource-spring.xml"/>
    ```

    Updated Configuration:

    </div>

    <div class="itemgroup stepxmp">

    ``` pre
    <import resource="classpath*:resourceserver-commons-spring.xml"/>
    ```

    <div id="n1t" class="title">

    **Note**

    </div>

    There's no need to specify a dependency to the resource server extension as it's one of the core extensions that are part of each build.

    </div>

Remove the resource-server Tag

2.  <span class="ph cmd">Remove the `resource-server` tag.</span>
    <div class="itemgroup stepxmp">

    ``` pre
    <resource-server id="resourceServerFilter"
            token-services-ref="oauthTokenServices"
            entry-point-ref="oauthAuthenticationEntryPoint"/>
    ```

    </div>
3.  <span class="ph cmd">Remove the use of the `resource-server` filter bean from the security filter chain in the HTTP security configuration.</span>
    <div class="itemgroup stepxmp">

    ``` pre
    <custom-filter ref="resourceServerFilter" before="PRE_AUTH_FILTER"/>
    ```

    </div>

Remove oauthAuthenticationEntryPoint Bean

4.  <span class="ph cmd">Remove the `oauthAuthenticationEntryPoint` bean for the deprecated <span class="keyword apiname">OAuth2AuthenticationEntryPoint</span> class.</span>
    <div class="itemgroup stepxmp">

    ``` pre
    <alias name="defaultOauthAuthenticationEntryPoint" alias="oauthAuthenticationEntryPoint"/>
    <bean id="defaultOauthAuthenticationEntryPoint"
        class="org.springframework.security.oauth2.provider.error.OAuth2AuthenticationEntryPoint"
        p:realmName="hybris" p:typeName="Bearer" p:exceptionRenderer-ref="oauth2ExceptionRender"/>
    ```

    </div>
5.  <span class="ph cmd">Remove the `entry-point-ref` attribute with <span class="keyword apiname">OAuth2AuthenticationEntryPoint</span>.</span>
    <div class="itemgroup stepxmp">

    ``` pre
    <http pattern="/**" entry-point-ref="oauthAuthenticationEntryPoint">
       <intercept-url pattern="/**" access=.../>
       ...
    </http>
    ```

    </div>

Configure Authentication Using oauth2-resource-server Tag

6.  <span class="ph cmd">Use `<oauth2-resource-server>` instead of `<resource-server>` to configure authentication.</span>
    <div class="itemgroup stepxmp">

    ``` pre
    <http pattern="/**">
        <intercept-url pattern="/**" access=.../>
        <access-denied-handler ref="enrichingAccessDeniedHandler" />
        <oauth2-resource-server entry-point-ref="enrichingAuthenticationEntryPoint">
            <jwt decoder-ref="jwtDecoder" jwt-authentication-converter-ref="jwtAuthenticationRoleConverter" />
        </oauth2-resource-server>
    </http>
    ```

    </div>

    1.  <span class="ph cmd">Decide if you want to configure the `entry-point-ref` attribute inside the `oauth2-resource-server` tag. If you don't specify it, the default <span class="keyword apiname">BearerTokenAuthenticationEntryPoint</span> is used. You can add a reference to the `enrichingAuthenticationEntryPoint` bean that is defined in <span class="ph filepath">bin/platform/ext/resourceserver/resources/resourceserver-commons-spring.xml</span>.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        <oauth2-resource-server entry-point-ref="enrichingAuthenticationEntryPoint">
        ...
        </oauth2-resource-server>
        ```

        </div>

        <div class="itemgroup info">

        The `enrichingAuthenticationEntryPoint` bean ensures that error descriptions are added to the response body in authentication exceptions (HTTP 401 status code - Unauthorized access).

        </div>

        <div class="itemgroup stepxmp">

        ``` pre
        {
            "error_code": "invalid_token",
            "error_description": "An error occurred while attempting to decode the Jwt: Jwt expired at 2024-03-05T12:46:08Z"
        }
        ```

        ``` pre
        {
            "error_code": "invalid_token",
            "error_description": "An error occurred while attempting to decode the Jwt: Malformed token"
        }
        ```

        </div>

        <div class="itemgroup info">

        If you don't use it, the response body is always empty. For more information on <span class="keyword apiname">BearerTokenAuthenticationEntryPoint</span> being added to the default configuration, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2Fservlet%2Fappendix%2Fnamespace%2Fhttp.html%23nsa-oauth2-resource-server" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/servlet/appendix/namespace/http.html#nsa-oauth2-resource-server" title="https://docs.spring.io/spring-security/reference/servlet/appendix/namespace/http.html#nsa-oauth2-resource-server">&lt;oauth2-resource-server&gt;<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        </div>
    2.  <span class="ph cmd">Remove the `oauthAccessDeniedHandler` bean and the `oauth2ExceptionRender` exception renderer for the deprecated <span class="keyword apiname">OAuth2AccessDeniedHandler</span> and <span class="keyword apiname">DefaultOAuth2ExceptionRenderer</span> classes.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        <bean id="oauthAccessDeniedHandler" class="org.springframework.security.oauth2.provider.error.OAuth2AccessDeniedHandler"
            p:exceptionRenderer-ref="oauth2ExceptionRender" />
        ```

        ``` pre
        <bean id="oauth2ExceptionRender"
              class="org.springframework.security.oauth2.provider.error.DefaultOAuth2ExceptionRenderer">
            <property name="messageConverters">
                <list>
                    <bean class="org.springframework.http.converter.json.MappingJackson2HttpMessageConverter" />
                    <bean class="org.springframework.security.oauth2.http.converter.jaxb.JaxbOAuth2ExceptionMessageConverter" />
                    <bean class="org.springframework.http.converter.StringHttpMessageConverter"
                          p:writeAcceptCharset="false" />
                    <bean class="org.springframework.http.converter.ByteArrayHttpMessageConverter" />
                </list>
            </property>
        </bean>
        ```

        </div>
    3.  <span class="ph cmd">Decide if you want to configure `access-denied-handler` as a replacement for `oauthAccessDeniedHandler`. If you don't specify it, the default <span class="keyword apiname">BearerTokenAccessDeniedHandler</span> is used.</span>
        <div class="itemgroup stepxmp">

        You can add a reference to the `enrichingAccessDeniedHandler` bean that is defined in <span class="ph filepath">bin/platform/ext/resourceserver/resources/resourceserver-commons-spring.xml</span>.

        </div>

        <div class="itemgroup stepxmp">

        ``` pre
        <http pattern="/**">
           <intercept-url pattern="/**" access=.../>
           <access-denied-handler ref="enrichingAccessDeniedHandler" />  
        </http>
        ```

        </div>

        <div class="itemgroup info">

        The `enrichingAccessDeniedHandler` bean ensures that error descriptions are added to the response body in access exceptions (HTTP 403 status code - access to the requested resource is forbidden).

        </div>

        <div class="itemgroup stepxmp">

        ``` pre
        {
            "error_code": "insufficient_scope",
            "error_description": "The request requires higher privileges than provided by the access token."
        }
        ```

        If you don't use it, the response body will always be empty.

        </div>

        <div class="itemgroup info">

        As an alternative, you can define your own custom logic by creating a class that implements the <span class="keyword apiname">AccessDeniedHandler</span> interface and overrides the <span class="keyword apiname">handle()</span> method. For more information on <span class="keyword apiname">BearerTokenAccessDeniedHandler</span> being added to the default configuration, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2Fservlet%2Fappendix%2Fnamespace%2Fhttp.html%23nsa-oauth2-resource-server" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/servlet/appendix/namespace/http.html#nsa-oauth2-resource-server" title="https://docs.spring.io/spring-security/reference/servlet/appendix/namespace/http.html#nsa-oauth2-resource-server">&lt;oauth2-resource-server&gt;<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        </div>
    4.  <span class="ph cmd">Configure the JWT authentication through the `<jwt>` tag. Use the `decoder-ref` attribute with a reference to the `jwtDecoder` bean to use JwtDecoder that’s responsible for decoding and validating JWTs. Use the `jwt-authentication-converter-ref` attribute with a reference to the `jwtAuthenticationRoleConverter` bean to use <span class="keyword apiname">JwtAuthenticationConverter</span> that’s responsible for converting JWTs (JSON Web Tokens) into a <span class="keyword apiname">Collection</span> of granted authorities.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        <oauth2-resource-server>
           <jwt decoder-ref="jwtDecoder" jwt-authentication-converter-ref="jwtAuthenticationRoleConverter" />
        </oauth2-resource-server>
        ```

        </div>

Replace AccessDecisionManager and AccessDecisionVoter with AuthorizationManager

7.  <span class="ph cmd">Replace the deprecated <span class="keyword apiname">AccessDecisionManager</span> and <span class="keyword apiname">AccessDecisionVoter</span> with <span class="keyword apiname">AuthorizationManager</span> that supersede these two classes. </span>
    1.  <span class="ph cmd">Remove the <span class="keyword apiname">accessDecisionManager</span> bean along with a list if voters.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        <bean id="accessDecisionManager" class="org.springframework.security.access.vote.UnanimousBased"
                  xmlns="http://www.springframework.org/schema/beans">
            <constructor-arg>
                <list>
                    <bean class="org.springframework.security.oauth2.provider.vote.ScopeVoter"/>
                    <bean class="org.springframework.security.access.vote.RoleVoter"/>
                    <bean class="org.springframework.security.access.vote.AuthenticatedVoter"/>
                    <bean class="org.springframework.security.web.access.expression.WebExpressionVoter" />
                </list>
            </constructor-arg>
        </bean>
        ```

        </div>

        <div class="itemgroup info">

        If you use custom voters, you can replace their functionality by either:
        - as a probably most simple approach, creating a custom <span class="keyword apiname">@Bean</span>, which is used in the `access` expression or the <span class="keyword apiname">@PreAuthorize</span> annotation. Create your custom class, for example, a <span class="keyword apiname">SystemInitializedSecurityChecker</span> class.

          ``` pre
          public class SystemInitializedSecurityChecker
          {
             public boolean isNotInitialized()
             { //code }
          }
          ```

          Create a bean.

          ``` pre
          <bean id="systemInitializedChecker" class="de.hybris.platform.spring.security.expression.SystemInitializedSecurityChecker"/>
          ```

          This can be used in:

          - your XML configuration in the `access` element.

            ``` pre
            <intercept-url pattern="/**" access="@systemInitializedChecker.isInitialized()"/>
            ```

          - in your Java configuration in the <span class="keyword apiname">@PreAuthorize</span> annotation.

            ``` pre
            @PreAuthorize("@systemInitializedChecker.isInitialized()")
            public void someMethod()  
            {    // some code  }
            ```

          You need to add an explicit reference to <span class="keyword apiname">org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler</span>.

          ``` pre
          <bean id="expressionHandler" class="org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler"/>    
          <method-security pre-post-enabled="true" proxy-target-class="true" secured-enabled="true">        
            <expression-handler ref="expressionHandler" />    
          </method-security>
          ```

          If you don't reference <span class="keyword apiname">DefaultMethodSecurityExpressionHandler</span> in your configuration, you encounter the following issue:

          ``` pre
          org.springframework.expression.spel.SpelEvaluationException: EL1057E: No bean resolver registered in the context to resolve access to bean 'systemInitializedChecker'
          ```

          For more information on this solution, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fmigration%2Fservlet%2Fauthorization.html%23_use_a_custom_bean_instead_of_subclassing_defaultmethodsecurityexpressionhandler" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/migration/servlet/authorization.html#_use_a_custom_bean_instead_of_subclassing_defaultmethodsecurityexpressionhandler" title="https://docs.spring.io/spring-security/reference/5.8/migration/servlet/authorization.html#_use_a_custom_bean_instead_of_subclassing_defaultmethodsecurityexpressionhandler">Use a Custom @Bean instead of subclassing DefaultMethodSecurityExpressionHandler<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a> and <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fauthorization%2Fexpression-based.html%23el-access-web-beans" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html#el-access-web-beans" title="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html#el-access-web-beans">Referring to Beans in Web Security Expressions<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        - adding a new custom expression by sub-classing expression handlers. To use a new expression in your Java configuration, for example, the <span class="keyword apiname">@PreAuthorize</span> annotation, sub-class <span class="keyword apiname">DefaultMethodSecurityExpressionHandler</span> and <span class="keyword apiname">SecurityExpressionRoot</span>. This approach requires adding a custom expression handler to your XML configuration, for example:

          ``` pre
          <bean id="myMethodSecurityExpressionHandler" class="some.package.CustomMethodSecurityExpressionHandler"> </bean>
          <method-security pre-post-enabled="true" proxy-target-class="true" secured-enabled="true">
             <expression-handler ref="myMethodSecurityExpressionHandler"/>
          </method-security>
          ```

          To use new expressions in your XML configuration in the `access` tag, sub-class <span class="keyword apiname">DefaultHttpSecurityExpressionHandler</span> and <span class="keyword apiname">WebSecurityExpressionRoot</span>. This requires adding a custom expression handler to your XML configuration, for example:

          ``` pre
          <bean id="myHttpSecurityExpressionHandler" class="some.package.CustomHttpSecurityExpressionHandler"> </bean>  
          <http pattern="/**" use-authorization-manager="true" use-expressions="true" ...>
          <expression-handler ref="myHttpSecurityExpressionHandler"/>    
          <intercept-url pattern="/**" access="customExpression()"/>
          ...
          </http>
          ```

          For more information, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2Fservlet%2Fauthorization%2Fmethod-security.html%23_use_a_custom_bean_instead_of_subclassing_defaultmethodsecurityexpressionhandler" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html#_use_a_custom_bean_instead_of_subclassing_defaultmethodsecurityexpressionhandler" title="https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html#_use_a_custom_bean_instead_of_subclassing_defaultmethodsecurityexpressionhandler">Use a Custom @Bean instead of subclassing DefaultMethodSecurityExpressionHandler<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a> and <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fauthorization%2Fmethod-security.html%23_customizing_authorization" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/method-security.html#_customizing_authorization" title="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/method-security.html#_customizing_authorization">Customizing Authorization<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        - adding a custom <span class="keyword apiname">AuthorizationManager</span>. This approach could be problematic as, in a such scenario, the default <span class="keyword apiname">AuthorizationManager</span>, which handles the `access` attribute from the `intercept-url` element, isn't created by Spring. For more information, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fmigration%2Fservlet%2Fauthorization.html%23_replace_any_custom_filter_security_accessdecisionmanagers" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/migration/servlet/authorization.html#_replace_any_custom_filter_security_accessdecisionmanagers" title="https://docs.spring.io/spring-security/reference/5.8/migration/servlet/authorization.html#_replace_any_custom_filter_security_accessdecisionmanagers">Replace any custom filter-security AccessDecisionManagers<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

          For example, in your XML configuration files, you can construct your custom <span class="keyword apiname">AuthorizationManager</span> as follows:

          - Create a <span class="keyword apiname">MyAuthorizationManager</span> class with your custom logic that implements the <span class="keyword apiname">AuthorizationManager</span> interface.

          - Define a bean of the <span class="keyword apiname">org.springframework.security.authorization.AuthorizationManagers</span> class and pass the <span class="keyword apiname">MyAuthorizationManager</span> class in the constructor.

          - Define the `factory-method` element:

            - Using the `anyOf` value, which creates an <span class="keyword apiname">AuthorizationManager</span> that grants access if at least one <span class="keyword apiname">AuthorizationManager</span> is granted or abstained. If managers are empty, then a denied decision is returned.

            - Using the `allOf` value, which creates an <span class="keyword apiname">AuthorizationManager</span> that grants access if all <span class="keyword apiname">AuthorizationManagers</span> are granted or abstained.

            For more information on the `anyOf` and `allOf` values, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Fsite%2Fdocs%2F5.8.13%2Fapi%2Forg%2Fspringframework%2Fsecurity%2Fauthorization%2FAuthorizationManagers.html" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/site/docs/5.8.13/api/org/springframework/security/authorization/AuthorizationManagers.html" title="https://docs.spring.io/spring-security/site/docs/5.8.13/api/org/springframework/security/authorization/AuthorizationManagers.html">Class AuthorizationManagers<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

            ``` pre
            <bean id="myRequestAuthorization" class="org.springframework.security.authorization.AuthorizationManagers" factory-method="anyOf">
               <constructor-arg>
                  <list>
                      <bean class="some.package.MyAuthorizationManager"/>
                  </list>
               </constructor-arg>
            </bean>
            ```

          - Inside the `<http>` tag, add a reference to the <span class="keyword apiname">myRequestAuthorization</span> class through the `authorization-manager-ref` element.

            ``` pre
            <http pattern="/**" use-authorization-manager="true" authorization-manager-ref="myRequestAuthorization">
               ...
            </http>
            ```

            In the `myRequestAuthorization` bean definition, define just your custom <span class="keyword apiname">AuthorizationManagers</span> in the constructor list since there's no need to pass the Spring <span class="keyword apiname">AuthorizationManagers</span>. This means that if you use the <span class="keyword apiname">@PreAuthorize()</span> annotation, the corresponding <span class="keyword apiname">PreAuthorizeAuthorizationManager</span> is called out automatically.

          However, expressions defined inside the `access` element aren't validated in the custom <span class="keyword apiname">AuthorizationManager</span> approach.

          ``` pre
          <intercept-url pattern="/**" access="isFullyAuthenticated()"/>
          ```

          It's caused by the fact that when you explicitly set `authorization-manager-ref` to your custom <span class="keyword apiname">AuthorizationManager</span>, Spring doesn't create the default <span class="keyword apiname">org.springframework.security.web.access.intercept.RequestMatcherDelegatingAuthorizationManager</span> manager. <span class="keyword apiname">RequestMatcherDelegatingAuthorizationManager</span> matches requests with the most appropriate delegate <span class="keyword apiname">AuthorizationManager</span> by calling <span class="keyword apiname">WebExpressionAuthorizationManager.check()</span> and, as a result, evaluating expressions from the `access` element. For more information, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fauthorization%2Farchitecture.html%23authz-delegate-authorization-manager" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/architecture.html#authz-delegate-authorization-manager" title="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/architecture.html#authz-delegate-authorization-manager">Delegate-based AuthorizationManager Implementations<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        </div>
    2.  <span class="ph cmd">Remove the `access-decision-manager-ref` attribute and replace it with `use-authorization-manager="true"`.</span>
        <div class="itemgroup info">

        Previous Configuration:

        </div>

        <div class="itemgroup stepxmp">

        ``` pre
        <http pattern="/**" access-decision-manager-ref="accessDecisionManager">
            <intercept-url pattern="/**" access=.../>
            ...
        </http>
        ```

        </div>

        <div class="itemgroup info">

        Updated Configuration:

        </div>

        <div class="itemgroup stepxmp">

        ``` pre
        <http pattern="/**" use-authorization-manager="true">
            <intercept-url pattern="/**" access=.../>
            ...
        </http>
        ```

        </div>

    <div class="itemgroup info">

    For more information on the <span class="keyword apiname">AuthorizationManager</span> replacing <span class="keyword apiname">AccessDecisionManager</span> and <span class="keyword apiname">AccessDecisionVoter</span>, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fauthorization%2Farchitecture.html" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/architecture.html" title="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/architecture.html">Architecture<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

    </div>

Implement Spring EL Expressions

8.  <span class="ph cmd">To secure individual URLs, use Spring EL expressions. </span>
    1.  <span class="ph cmd">Add `use-expressions="true"` to the `<http>` tag to enable Spring EL expressions. Spring Security will now expect the `access` attributes of the `<intercept-url>` elements to contain Spring EL expressions. These expressions should evaluate to a Boolean, defining whether access should be allowed or not.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        <http pattern="/**" use-authorization-manager="true" use-expressions="true">
            <intercept-url pattern="/**" access="isFullyAuthenticated() and hasAuthority('SCOPE_READ') and hasRole('ROLE_BUSINESSADMINGROUP')"/>
            ...
        </http>
        ```

        </div>

        <div class="itemgroup info">

        If you didn't use Spring EL expressions so far, your access attributes looked like the following:
        ``` pre
        access="IS_AUTHENTICATED_FULLY, ROLE_BUSINESSADMINGROUP, SCOPE_READ"
        ```

        Access rules were previously evaluated by voters, which are deprecated and recommended not to be used. For example, `IS_AUTHENTICATED_FULLY` was evaluated by <span class="keyword apiname">AuthenticatedVoter</span>, which should now be replaced with the `access="isFullyAuthenticated()` expression. Similarly, `SCOPE_READ` should be replaced with `hasAuthority('SCOPE_READ')` and `ROLE_BUSINESSADMINGROUP` with `hasRole('ROLE_BUSINESSADMINGROUP')`.

        </div>

        <div class="itemgroup info">

        For a list of common built-in expressions, such as <span class="keyword apiname">isFullyAuthenticated()</span>, <span class="keyword apiname">hasAuthority(String authority)</span>, <span class="keyword apiname">hasRole(String role)</span>, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fauthorization%2Fexpression-based.html%23el-common-built-in" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html#el-common-built-in" title="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html#el-common-built-in">Common Built-In Expressions<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        </div>

        <div class="itemgroup info">

        For more information on Spring EL expressions, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fauthorization%2Fexpression-based.html" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html" title="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html">Expression-Based Access Control<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        </div>
    2.  <span class="ph cmd">Check if you use the <span class="keyword apiname">@oauthSecurityChecker.hasScope()</span> method in the `intercept-url` tag in the `access` attribute for scope checking. The <span class="keyword apiname">de.hybris.platform.oauth2.util.OAuth2SecurityChecker</span> class is provided with the removed <span class="keyword apiname yext">oauth2</span> extension. Replace it with a usage of the <span class="keyword apiname">hasAuthority()</span> method that is provided with Spring Security.</span>
        <div class="itemgroup stepxmp">

        Previous Configuration:

        ``` pre
        <intercept-url pattern="/**" method="GET" access="@oauthSecurityChecker.hasScope(authentication,'basic')"/>
        ```

        Updated Configuration:

        </div>

        <div class="itemgroup stepxmp">

        ``` pre
        <intercept-url pattern="/**" method="GET" access="hasAuthority('SCOPE_basic')"/>
        ```

        </div>

        <div class="itemgroup info">

        For more information on the Spring Security built-in methods, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fauthorization%2Fexpression-based.html%23el-common-built-in" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html#el-common-built-in" title="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html#el-common-built-in">Common Built-In Expressions<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        </div>

Replace enable global method security with enable security

9.  <span class="ph cmd">Replace <span class="q">“enable global method security”</span> with <span class="q">“method security”</span>. You can use annotation-based configuration using the `EnableMethodSecurity` annotation on any `@Configuration` instance. <span class="keyword apiname">@EnableGlobalMethodSecurity</span> and `<global-method-security>` are deprecated in favor of <span class="keyword apiname">@EnableMethodSecurity</span> and `<method-security>`, respectively. The new annotation and XML elements activate Spring’s pre-post annotations by default and use <span class="keyword apiname">AuthorizationManager</span> internally. </span>
    1.  <span class="ph cmd">In your XML configuration files, replace `<global-method-security>` with the `<method-security>` tag.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        <global-method-security pre-post-annotations="enabled" proxy-target-class="true" secured-annotations="enabled"/>
        ```

        <div id="n2t" class="title">

        **Note**

        </div>

        The `<method security>` attribute names have been changed in the new implementation. For example, the `secured-annotations` attribute is now `secured-enabled`. For more information on the `<method security>` attributes, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fappendix%2Fnamespace%2Fmethod-security.html%23nsa-method-security-attributes" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/appendix/namespace/method-security.html#nsa-method-security-attributes" title="https://docs.spring.io/spring-security/reference/5.8/servlet/appendix/namespace/method-security.html#nsa-method-security-attributes">&lt;method-security&gt; attributes<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        The <span class="keyword apiname">@Secured</span> and <span class="keyword apiname">@PreAuthorize</span> annotations should work as before.

        </div>
    2.  <span class="ph cmd">If you use the `@EnableGlobalMethodSecurity` annotation in your Java configuration, replace it with <span class="keyword apiname">@EnableMethodSecurity</span>.</span>
    3.  <span class="ph cmd">Remove the usage of the <span class="keyword apiname">oauthExpressionHandler</span> Spring bean and the `expression-handler` tag.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        <method-security pre-post-enabled="true" proxy-target-class="true" secured-enabled="true">
           <expression-handler ref="oauthExpressionHandler"/>
        </method-security>
        <expression-handler id="oauthExpressionHandler"/>
        <web-expression-handler id="oauthWebExpressionHandler"/>
        ```

        </div>

        <div class="itemgroup info">

        The <span class="keyword apiname">org.springframework.security.oauth2.provider.expression.OAuth2MethodSecurityExpressionHandler</span> comes with the removed Spring Security OAuth library. As described in <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Foauth%2Fapidocs%2Forg%2Fspringframework%2Fsecurity%2Foauth2%2Fprovider%2Fexpression%2FOAuth2MethodSecurityExpressionHandler.html" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/oauth/apidocs/org/springframework/security/oauth2/provider/expression/OAuth2MethodSecurityExpressionHandler.html" title="https://docs.spring.io/spring-security/oauth/apidocs/org/springframework/security/oauth2/provider/expression/OAuth2MethodSecurityExpressionHandler.html">Class OAuth2MethodSecurityExpressionHandler<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>, <span class="keyword apiname">OAuth2MethodSecurityExpressionHandler</span> is a security expression handler that can handle default method security expressions plus the set provided by <span class="keyword apiname">OAuth2SecurityExpressionMethods</span> using the variable <span class="keyword apiname">oauth2</span> to access the methods. For example, the expression <span class="keyword apiname">\#oauth2.clientHasRole('ROLE_ADMIN')</span> would invoke <span class="keyword apiname">OAuth2SecurityExpressionMethods.clientHasRole(java.lang.String)</span>.
        This means that methods provided by <span class="keyword apiname">OAuth2MethodSecurityExpressionHandler</span> could be replaced by one of the Spring Security built-in methods listed in <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fauthorization%2Fexpression-based.html%23el-common-built-in" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html#el-common-built-in" title="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/expression-based.html#el-common-built-in">Common Built-In Expressions<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

        </div>
    4.  <span class="ph cmd">You might have used the <span class="keyword apiname">@PreAuthorize</span> annotation with a custom bean in your controller.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        @PreAuthorize("@authz.isAdmin(#root)")
        ```

        </div>

        <div class="itemgroup info">

        To be able to reference your custom bean, you need to define a <span class="keyword apiname">SecurityExpressionHandler</span> instance through the `expression-handler` tag inside the `method-security` tag with a reference to <span class="keyword apiname">DefaultMethodSecurityExpressionHandler</span>.
        ``` pre
        <method-security pre-post-enabled="true" proxy-target-class="true" secured-enabled="true">
           <expression-handler ref="expressionHandler" />
        </method-security>
         
        <bean id="expressionHandler" class="org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler"/>
        ```

        </div>

    <div class="itemgroup info">

    For more information, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fservlet%2Fauthorization%2Fmethod-security.html%23jc-enable-method-security" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/method-security.html#jc-enable-method-security" title="https://docs.spring.io/spring-security/reference/5.8/servlet/authorization/method-security.html#jc-enable-method-security">EnableMethodSecurity<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a> and <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Freference%2F5.8%2Fmigration%2Fservlet%2Fauthorization.html%23servlet-replace-globalmethodsecurity-with-methodsecurity" class="extlink" target="_blank" rel="noopener" alt="https://docs.spring.io/spring-security/reference/5.8/migration/servlet/authorization.html#servlet-replace-globalmethodsecurity-with-methodsecurity" title="https://docs.spring.io/spring-security/reference/5.8/migration/servlet/authorization.html#servlet-replace-globalmethodsecurity-with-methodsecurity">Replace global method security with method security<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

    </div>

Reference Authentication Manager

10. <span class="ph cmd">Remove the following `<authentication-manager>` tag that has been replaced with `<authentication-manager>` from the imported <span class="ph filepath">resourceserver-commons-spring.xml</span> file.</span>
    <div class="itemgroup stepxmp">

    ``` pre
    <authentication-manager id="authenticationManager" xmlns="http://www.springframework.org/schema/security">
       <authentication-provider ref="wsAuthenticationProvider"/>
    </authentication-manager>
    ```

    </div>

Replace hybrisUserFilter

11. <span class="ph cmd">Remove the <span class="keyword apiname">hybrisUserFilter</span> bean since <span class="keyword apiname">hybrisUserFilter</span> uses the deprecated <span class="keyword apiname">OAuth2Authentication</span> class. Replace it with <span class="keyword apiname">com.sap.cx.commerce.platform.oauth2.resourceserver.filter.JwtAuthenticationUserFilter</span> that is already defined in <span class="ph filepath">resourceserver/resources/resourceserver-spring.xml</span>.</span>
    <div class="itemgroup stepxmp">

    Previous Configuration:

    ``` pre
    <bean id="hybrisUserFilter" class="de.hybris.platform.webservicescommons.oauth2.HybrisOauth2UserFilter" />
     
    <http pattern="/**">
        <intercept-url pattern="/**" access=.../>
        <custom-filter ref="hybrisUserFilter" after="LAST" />
    </http>
    ```

    Updated Configuration:

    </div>

    <div class="itemgroup stepxmp">

    ``` pre
    <http pattern="/**">
        <intercept-url pattern="/**" access=.../>
        <custom-filter ref="jwtAuthenticationUserFilter" after="LAST" />
    </http>
    ```

    </div>

    1.  <span class="ph cmd">If you use other custom <span class="keyword apiname">UserFilter</span> or <span class="keyword apiname">UserMatchingFilter</span>, make sure that they work properly as it might be necessary to adjust their implementation to use <span class="keyword apiname">org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken</span> and avoid casting errors. </span>
        <div class="itemgroup stepxmp">

        ``` pre
        INFO  [hybrisHTTP38] [AbstractRestHandlerExceptionResolver] Translating exception [java.lang.ClassCastException]: class org.springframework.security.oauth2.jwt.Jwt cannot be cast to class java.lang.String (org.springframework.security.oauth2.jwt.Jwt is in unnamed module of loader de.hybris.bootstrap.loader.PlatformInPlaceClassLoader @ac5ae4e; java.lang.String is in module java.base of loader 'bootstrap')
         
        ERROR [hybrisHTTP38] [AbstractRestHandlerExceptionResolver] java.lang.ClassCastException: class org.springframework.security.oauth2.jwt.Jwt cannot be cast to class java.lang.String (org.springframework.security.oauth2.jwt.Jwt is in unnamed module of loader de.hybris.bootstrap.loader.PlatformInPlaceClassLoader @ac5ae4e; java.lang.String is in module java.base of loader 'bootstrap')
        ```

        </div>

        <div class="itemgroup info">

        As in <a href="/docs/link-disclaimer?site=https%3A%2F%2Fgithub.tools.sap%2Fcx-commerce%2Fcxmc-platform%2Fblob%2Fdevelop%2Fbin%2Fplatform%2Fext%2Fresourceserver%2Fsrc%2Fcom%2Fsap%2Fcx%2Fcommerce%2Fplatform%2Foauth2%2Fresourceserver%2Ffilter%2FJwtAuthenticationUserFilter.java" class="extlink" target="_blank" rel="noopener" alt="https://github.tools.sap/cx-commerce/cxmc-platform/blob/develop/bin/platform/ext/resourceserver/src/com/sap/cx/commerce/platform/oauth2/resourceserver/filter/JwtAuthenticationUserFilter.java" title="https://github.tools.sap/cx-commerce/cxmc-platform/blob/develop/bin/platform/ext/resourceserver/src/com/sap/cx/commerce/platform/oauth2/resourceserver/filter/JwtAuthenticationUserFilter.java">JwtAuthenticationUserFilter.java<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>, the configuration needs to be adjusted since <span class="keyword apiname">Authentication.getPrincipal()</span> used to return a String object, and now it returns an <span class="keyword apiname">org.springframework.security.oauth2.jwt.Jwt</span> object.

        </div>

Session Fixation Protection

12. <span class="ph cmd">You might have the following configuration of session fixation protection.</span>
    <div class="itemgroup stepxmp">

    ``` pre
    <bean id="fixation" class="de.hybris.platform.servicelayer.security.spring.HybrisSessionFixationProtectionStrategy"/>
    <http pattern="/**">
        <session-management session-authentication-strategy-ref="fixation" />  
        <intercept-url pattern="/**" access=.../>
        ...
    </http>
    ```

    </div>

    <div class="itemgroup info">

    <span class="keyword apiname">HybrisSessionFixationProtectionStrategy</span> extends Spring Security's <span class="keyword apiname">SessionFixationProtectionStrategy</span> that is used to protect against session fixation attacks. <span class="keyword apiname">SessionFixationProtectionStrategy</span> is added to the default configuration by Spring Security, but it always gets replaced with <span class="keyword apiname">HybrisSessionFixationProtectionStrategy</span> by <span class="keyword apiname">RemoveDefaultSessionFixationStrategyBeanPostProcessor</span> for each web application. If there's no instance of <span class="keyword apiname">HybrisSessionFixationProtectionStrategy</span>, <span class="keyword apiname">RemoveDefaultSessionFixationStrategyBeanPostProcessor</span> creates one. This means that you don't need to specify the `HybrisSessionFixationProtectionStrategy` bean explicitly in your configuration or inject it as an instance of <span class="keyword apiname">SessionAuthenticationStrategy</span> that is used by <span class="keyword apiname">SessionManagementFilter</span> in the `session-authentication-strategy-ref` tag. You can remove the `HybrisSessionFixationProtectionStrategy` bean and the `session-management` tag if you've used it in your configuration.

    </div>

Replace Deprecated Message Converters

13. <span class="ph cmd">Check if you use any deprecated message converters in the `annotation-driven` configuration, such as `JaxbOAuth2AccessTokenMessageConverter` or `JaxbOAuth2ExceptionMessageConverter`. Consider replacing them with <span class="keyword apiname">OAuth2AccessTokenResponseHttpMessageConverter</span> and <span class="keyword apiname">OAuth2ErrorHttpMessageConverter</span>.</span>
    <div class="itemgroup stepxmp">

    Previous Configuration:

    ``` pre
    <mvc:annotation-driven>
        <mvc:message-converters register-defaults="false">
    ...
            <bean class="org.springframework.security.oauth2.http.converter.jaxb.JaxbOAuth2AccessTokenMessageConverter"/>
            <bean class="org.springframework.security.oauth2.http.converter.jaxb.JaxbOAuth2ExceptionMessageConverter"/>
        </mvc:message-converters>
    </mvc:annotation-driven>
    ```

    Updated Configuration:

    ``` pre
    org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
    org.springframework.security.oauth2.core.http.converter.
    ```

    </div>

Remove oauth2 Extension Dependency

14. <span class="ph cmd">In the <span class="ph filepath">extensioninfo.xml</span> file of your extension, remove the <span class="keyword apiname yext">oauth2</span> dependency.</span>
    <div class="itemgroup stepxmp">

    ``` pre
    <requires-extension name="oauth2"/>
    ```

    </div>

Test Update Steps

15. <span class="ph cmd">Update tests that use OAuth with an embedded server. Instead of manually retrieving tokens, the `SignedJWTTokenFactory` is now used in the Spring test context. The <span class="keyword apiname">SignedJWTTokenFactory</span> class generates RSA keys and creates customizable tokens. To ensure that the resource server can decode these tokens, WireMock is used to stub endpoints that the JWTDecoder requests for JWKS. To streamline the setup, use the <span class="keyword apiname">@ResourceServerTest</span> annotation alongside <span class="keyword apiname">@NeedsEmbeddedServer</span>. Remember that <span class="keyword apiname">@ResourceServerTest</span> is recognized by <span class="keyword apiname">ResourceServerRunListener</span> for resource server tests. See an example of a test with <span class="keyword apiname">@ResourceServerTest</span>. </span>
    <div class="itemgroup stepxmp">

    ``` pre
    package de.hybris.platform.commercewebservicestests.test.resourceserver;
     
    import static org.assertj.core.api.Assertions.assertThat;
    import static org.junit.Assert.assertEquals;
     
    import de.hybris.bootstrap.annotations.IntegrationTest;
    import de.hybris.platform.core.model.security.PrincipalGroupModel;
    import de.hybris.platform.core.model.user.CustomerModel;
    import de.hybris.platform.core.model.user.UserGroupModel;
    import de.hybris.platform.servicelayer.ServicelayerBaseTest;
    import de.hybris.platform.servicelayer.model.ModelService;
     
    import java.time.Duration;
    import java.time.Instant;
    import java.util.Set;
     
    import javax.annotation.Resource;
    import javax.ws.rs.core.MediaType;
    import javax.ws.rs.core.Response;
     
    import org.junit.Test;
     
    import com.nimbusds.jwt.SignedJWT;
    import com.sap.cx.commerce.api.framework.testsupport.client.WsSecuredRequestBuilder;
    import com.sap.cx.commerce.api.framework.testsupport.server.NeedsEmbeddedServer;
    import com.sap.cx.commerce.platform.oauth2.resourceserver.constants.ResourceserverConstants;
    import com.sap.cx.commerce.platform.oauth2.resourceserver.testframework.tokenfactory.SignedJWTTokenFactory;
    import com.sap.cx.commerce.platform.oauth2.resourceserver.testframework.runlistener.ResourceServerTest;
     
    @ResourceServerTest
    @NeedsEmbeddedServer(webExtensions =
            { ResourceserverConstants.EXTENSIONNAME })
    @IntegrationTest
    public class SignedJWTTokenFactoryTest extends ServicelayerBaseTest
    {
        private final static String OAUTH_WHO_AM_I_PATH = "oauth/whoami";
        protected static final Set<String> AUTHORITIES = Set.of("ROLE_USER", "ROLE_CLIENT", "ROLE_CUSTOM");
        private final static String USER_UID = "testuser";
     
        @Resource
        private SignedJWTTokenFactory signedJWTTokenFactory;
        @Resource
        private ModelService modelService;
     
        @Test
        public void shouldAllowAccessToWhoAmIEndpointWithValidJWTSignature()
        {
            final Set<PrincipalGroupModel> groups = setupPrincipalGroups();
            final CustomerModel customer = setupCustomer(groups);
            final SignedJWT jwt = signedJWTTokenFactory.builder()
                                  .roles(AUTHORITIES)
                                  .subject(USER_UID)
                                  .build();
     
            final WsSecuredRequestBuilder wsSecuredRequestBuilder = new WsSecuredRequestBuilder().extensionName(
                                                               ResourceserverConstants.EXTENSIONNAME)
                                                       .path(OAUTH_WHO_AM_I_PATH)
                                                       .jwtAuthorization(jwt.serialize());
     
            final Response response = wsSecuredRequestBuilder.build().accept(MediaType.APPLICATION_JSON_TYPE).get();
     
            assertEquals(" HTTP status at response: " + response.getStatus(), Response.Status.OK.getStatusCode(),
                    response.getStatus());
            assertThat(response.readEntity(String.class)).contains(customer.getUid());
        }
     
        @Test
        public void shouldDenyAccessToWhoAmIEndpointWithInvalidJWTSignature()
        {
            final SignedJWTTokenFactory tokenFactoryWithDifferentRsaKey = new SignedJWTTokenFactory();
            final Set<PrincipalGroupModel> groups = setupPrincipalGroups();
     
            setupCustomer(groups);
            final SignedJWT jwt = tokenFactoryWithDifferentRsaKey.builder()
                                       .roles(AUTHORITIES)
                                       .subject(USER_UID)
                                       .build();
     
            final WsSecuredRequestBuilder wsSecuredRequestBuilder = new WsSecuredRequestBuilder().extensionName(
                                                               ResourceserverConstants.EXTENSIONNAME)
                                                       .path(OAUTH_WHO_AM_I_PATH)
                                                       .jwtAuthorization(jwt.serialize());
     
            final Response response = wsSecuredRequestBuilder.build().accept(MediaType.APPLICATION_JSON_TYPE).get();
     
            assertEquals(" HTTP status at response: " + response.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode(),
                    response.getStatus());
        }
     
        @Test
        public void shouldAllowAccessToWhoAmIEndpointWithDifferentRoles()
        {
            final Set<PrincipalGroupModel> groups = setupPrincipalGroups();
            final CustomerModel customer = setupCustomer(groups);
            final Set<String> roles = Set.of("ROLE_ADMIN", "ROLE_USER");
            final SignedJWT jwt = signedJWTTokenFactory.builder()
                                  .roles(roles)
                                  .subject(USER_UID)
                                  .build();
     
            final WsSecuredRequestBuilder wsSecuredRequestBuilder = new WsSecuredRequestBuilder().extensionName(
                                                               ResourceserverConstants.EXTENSIONNAME)
                                                       .path(OAUTH_WHO_AM_I_PATH)
                                                       .jwtAuthorization(jwt.serialize());
     
            final Response response = wsSecuredRequestBuilder.build().accept(MediaType.APPLICATION_JSON_TYPE).get();
     
            assertEquals(" HTTP status at response: " + response.getStatus(), Response.Status.OK.getStatusCode(),
                    response.getStatus());
            assertThat(response.readEntity(String.class)).contains(customer.getUid());
        }
     
        @Test
        public void shouldDenyAccessToWhoAmIEndpointWithExpiredJWT()
        {
            final Set<PrincipalGroupModel> groups = setupPrincipalGroups();
            setupCustomer(groups);
            final SignedJWT jwt = signedJWTTokenFactory.builder()
                                  .roles(AUTHORITIES)
                                  .subject(USER_UID)
                                  .issuedAt(Instant.now().minus(Duration.ofDays(1)))
                                  .duration(Duration.ofHours(1))
                                  .build();
     
            final WsSecuredRequestBuilder wsSecuredRequestBuilder = new WsSecuredRequestBuilder().extensionName(
                                                               ResourceserverConstants.EXTENSIONNAME)
                                                       .path(OAUTH_WHO_AM_I_PATH)
                                                       .jwtAuthorization(jwt.serialize());
     
            final Response response = wsSecuredRequestBuilder.build().accept(MediaType.APPLICATION_JSON_TYPE).get();
     
            assertEquals(" HTTP status at response: " + response.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode(),
                    response.getStatus());
        }
     
        @Test
        public void shouldAllowAccessToWhoAmIEndpointWithCustomClaims()
        {
            final Set<PrincipalGroupModel> groups = setupPrincipalGroups();
            final CustomerModel customer = setupCustomer(groups);
            final SignedJWT jwt = signedJWTTokenFactory.builder()
                                  .roles(AUTHORITIES)
                                  .subject(USER_UID)
                                  .jwtClaims(claims -> claims.claim("customClaim", "customValue"))
                                  .build();
     
            final WsSecuredRequestBuilder wsSecuredRequestBuilder = new WsSecuredRequestBuilder().extensionName(
                                                               ResourceserverConstants.EXTENSIONNAME)
                                                       .path(OAUTH_WHO_AM_I_PATH)
                                                       .jwtAuthorization(jwt.serialize());
     
            final Response response = wsSecuredRequestBuilder.build().accept(MediaType.APPLICATION_JSON_TYPE).get();
     
            assertEquals(" HTTP status at response: " + response.getStatus(), Response.Status.OK.getStatusCode(),
                    response.getStatus());
            assertThat(response.readEntity(String.class)).contains(customer.getUid());
        }
     
        private Set<PrincipalGroupModel> setupPrincipalGroups()
        {
            final PrincipalGroupModel testGroup1 = modelService.create(UserGroupModel.class);
            testGroup1.setUid("TEST_GROUP1");
     
            final PrincipalGroupModel testGroup2 = modelService.create(UserGroupModel.class);
            testGroup2.setUid("TEST_GROUP2");
     
            modelService.saveAll(testGroup1, testGroup2);
            return Set.of(testGroup1, testGroup2);
        }
     
        private CustomerModel setupCustomer(final Set<PrincipalGroupModel> principalGroups)
        {
            final CustomerModel user = modelService.create(CustomerModel.class);
            user.setUid(USER_UID);
            user.setName(USER_UID);
            user.setGroups(principalGroups);
            modelService.save(user);
            return user;
        }
    }
    ```

    </div>

    <div class="itemgroup info">

    Since <span class="keyword apiname">@ResourceServerTest</span> starts the WireMock server and replaces the JWKS URI configuration, you can't use both <span class="keyword apiname">@ResourceServerTest</span> and the real authorization server in one class. For example, you can't use <span class="keyword apiname">@ResourceServerTest</span> with <span class="keyword apiname">@NeedsEmbeddedServer(extensions = "authorizationserver")</span>.
    When creating a token using <span class="keyword apiname">SignedJWTTokenFactory</span>, it's crucial to set an appropriate expiration time for tokens. Use the duration method to specify the token's lifespan, for example, <span class="keyword apiname">duration(Duration.ofMinutes(4)</span>. Avoid setting excessively long expiration times. Tokens with extended lifespans undergo additional verification, which fails for tokens generated by <span class="keyword apiname">SignedJWTTokenFactory</span>. This verification process activates for tokens exceeding the threshold defined by the `resourceserver.revoked.access.tokens.store.longttl.threshold=300` property. Ensure that the token's expiration time remains below this threshold to prevent verification failures.

    </div>

Configuration of Read and Connection Timeout for JwtDecoder

16. <span class="ph cmd">You might want to customize the process of decoding access tokens to include additional validations or conversions to modify the way JWT access tokens are validated and converted. To implement custom validations or conversions:</span>
    1.  <span class="ph cmd">Extend the <span class="keyword apiname">DefaultJwtConfigProvider</span> class.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        com.sap.cx.commerce.platform.oauth2.resourceserver.config.impl.DefaultJwtConfigProvider
        ```

        </div>
    2.  <span class="ph cmd">Customize token validators by overriding the <span class="keyword apiname">getOAuth2TokenValidators()</span> method. </span>
        <div class="itemgroup info">

        The following validators are provided in the default configuration:

        - <span class="keyword apiname">JwtTimestampValidator</span> - an implementation of <span class="keyword apiname">OAuth2TokenValidator</span> for verifying expiration time for JWTs.

        - <span class="keyword apiname">JWTRevocationTokenValidator</span> - a validator for JWT access tokens which checks the tokens against a disallow list that is stored in the <span class="keyword apiname">RevokedAccessTokensStore</span>.

        </div>

        <div class="itemgroup stepxmp">

        The validators are run during the decode process. They ensure that JWT access tokens meet specific requirements and are valid for further processing. Because of that, make sure that these validators are also returned in the overridden method.

        ``` pre
        @Override
        Collection<OAuth2TokenValidator<Jwt>> getOAuth2TokenValidators(){
           Collection<OAuth2TokenValidator<Jwt>> extendedValidatorList = new LinkedList<>(super.getOAuth2TokenValidators());
           extendedValidatorList.add(new YourOAuth2TokenValiator());
           return extendedValidatorList;
        }
        ```

        </div>
    3.  <span class="ph cmd">Customize authority converters by overriding <span class="keyword apiname">getGrantedAuthorityConverters()</span>. Overriding this method allows you to add your custom granted authority converters that translate JWTs into a collection of <span class="keyword apiname">GrantedAuthority</span>. Make sure that the converters provided in the default configuration in <span class="keyword apiname">DefaultJwtConfigProvider</span> are returned in the overridden method. </span>
        <div class="itemgroup stepxmp">

        ``` pre

        @Override
        Collection<Converter<Jwt, Collection<GrantedAuthority>>> getGrantedAuthorityConverters(){
           final Collection<Converter<Jwt, Collection<GrantedAuthority>>>  extendedConveterList = new LinkedList<>(super.getGrantedAuthorityConverters());
           extendedConveterList.add(new YourOAuth2GrantedAuthorityConverter());
           return extendedConveterList;
        }
        ```

        </div>
    4.  <span class="ph cmd">Register your custom implementation bean.</span>
        <div class="itemgroup stepxmp">

        ``` pre
        <bean id="jwtConfigProvider" class="package.YourJwtConfigProviderCustomization"/>
        ```

        </div>

</div>

</div>

<div id="task_ekl_nbw_tfc" class="topic task nested1" aria-labelledby="ariaid-title2">

## Troubleshooting

<div class="body taskbody">

<div class="section section procedure">

<span id="steps" class="anchor"></span>

<div class="ul steps-unordered tasklabel">

### Procedure

</div>

- <span class="ph cmd">To resolve the issue with users not being able to log in as agent in ASM mode on the B2B storefront with the punchoutaddon enabled, follow these steps:</span>
  <div class="itemgroup info">

  In the <span class="ph filepath">b2bacceleratoraddon/resources/b2bacceleratoraddon/web/spring/b2bacceleratoraddon-spring-security-config.xm</span> and the <span class="ph filepath">b2bpunchoutaddon/resources/b2bpunchoutaddon/web/spring/b2bpunchoutaddon-spring-security-config.xml</span> files, remove the definition of the <span class="ph uicontrol">csrfTokenRequestAttributeHandler</span>:
  ``` pre
  <bean id="csrfTokenRequestAttributeHandler" class="org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler" />
  ```

  This issue arises because the spring security configurations in the <span class="ph uicontrol">b2bpunchoutaddon</span> and the <span class="ph uicontrol">b2bacceleratoraddon</span> override the <span class="ph uicontrol">csrfTokenRequestAttributeHandler</span> configuration in the B2B storefront web. This results in an empty CSRF token.

  </div>
- <span class="ph cmd">To ensure individual URLs use Spring EL expressions, use the <span class="ph uicontrol">AuthorizationManager</span> with `use-expressions="true"`. Update the configuration in the <span class="ph filepath">b2bpunchoutaddon/resources/b2bpunchoutaddon/web/spring/b2bpunchoutaddon-spring-security-config.xml</span> file. Change the following from:</span>
  <div class="itemgroup info">

  ``` pre
  <security:http pattern="${b2bpunchoutaddon.mapping.cxml.pattern}"  disable-url-rewriting="true" use-expressions="false">
  ...
  <security:intercept-url pattern="/**" requires-channel="https" />
  ```

  to
  ``` pre
  <security:http pattern="${b2bpunchoutaddon.mapping.cxml.pattern}"  disable-url-rewriting="true" use-expressions="true">
  ...
  <security:intercept-url pattern="/**" access="permitAll()" requires-channel="https" />
  ```

  </div>
- <span class="ph cmd">To resolve the login requirement issue, update the Spring Security configuration. Add the parameter <span class="keyword apiname">security-context-explicit-save="false"</span> to the <span class="ph filepath">spring-security-config.xml</span> file. In the <span class="ph filepath">b2bpunchoutaddon/resources/b2bpunchoutaddon/web/spring/b2bpunchoutaddon-spring-security-config.xml</span> file, modify the configuration by including `security-context-explicit-save="false"` as shown:</span>
  <div class="itemgroup info">

  ``` pre
  <security:http pattern="${b2bpunchoutaddon.mapping.cxml.pattern}" disable-url-rewriting="true" use-expressions="true" security-context-explicit-save="false">
  ```

  </div>

  <div class="itemgroup info">

  This issue arises because the behavior of some classes has changed in Spring Security 6. Currently, the <span class="ph uicontrol">SecurityContextHolderFilter</span> only reads the <span class="ph uicontrol">SecurityContext</span> from the <span class="ph uicontrol">SecurityContextRepository</span> and populates it in the <span class="ph uicontrol">SecurityContextHolder</span>.

  </div>

</div>

</div>

</div>

<div id="task_jgj_yrq_nfc" class="topic task nested1" aria-labelledby="ariaid-title3">

## Changes for Extensions Generated from ycommercewebservicestest Template

<div class="body taskbody">

<div id="task_jgj_yrq_nfc__context_lcy_bsq_nfc" class="section context">

<span id="steps" class="anchor"></span>

<div class="tasklabel">

### Context

</div>

If your extensions are generated from the <span class="keyword apiname">ycommercewebservicestest</span> template, perform the following steps to adopt them for the new resource server.

</div>

<div class="section section procedure">

<span id="steps" class="anchor"></span>

<div class="ol steps tasklabel">

### Procedure

</div>

1.  <span class="ph cmd">Create a new class `ResourceServerTestManager`.</span>
    <div class="itemgroup info">

    ``` pre
    /*
     * Copyright (c) 2025 SAP SE or an SAP affiliate company. All rights reserved.
     */
    package de.hybris.platform.ycommercewebservicestest.setup;
     
    import com.sap.cx.commerce.platform.oauth2.resourceserver.testframework.runlistener.ResourceServerWireMockManager;
    import com.sap.cx.commerce.platform.oauth2.resourceserver.testframework.tokenfactory.SignedJWTTokenFactory;
     
     
    public class ResourceServerTestManager
    {
        private SignedJWTTokenFactory signedJWTTokenFactory;
     
        private ResourceServerWireMockManager wireMockManager;
     
        protected SignedJWTTokenFactory getSignedJWTTokenFactory()
        {
            return signedJWTTokenFactory;
        }
     
        public void setSignedJWTTokenFactory(final SignedJWTTokenFactory signedJWTTokenFactory)
        {
            this.signedJWTTokenFactory = signedJWTTokenFactory;
        }
     
        public ResourceServerTestManager(final SignedJWTTokenFactory signedJWTTokenFactory)
        {
            this.signedJWTTokenFactory = signedJWTTokenFactory;
        }
     
        public void start()
        {
            if (this.wireMockManager == null)
            {
                final var rsaKey = signedJWTTokenFactory.getRsaKey();
                this.wireMockManager = new ResourceServerWireMockManager(rsaKey);
            }
            wireMockManager.start();
        }
     
        public void stop()
        {
            if (this.wireMockManager != null)
            {
                wireMockManager.stop();
            }
        }
    }
    ```

    </div>

2.  <span class="ph cmd">Add a bean with id <span class="keyword apiname">resourceServerTestManager</span> in `ycommercewebservicestest-test-spring.xml`. </span>
    <div class="itemgroup info">

    ``` pre
    <bean id="resourceServerTestManager" class="de.hybris.platform.ycommercewebservicestest.setup.ResourceServerTestManager">
        <constructor-arg name="signedJWTTokenFactory" ref="signedJWTTokenFactory"/>
    </bean>
    ```

    </div>

3.  <div id="n3t" class="title">

    **Note**

    </div>

    In the Spock test setup for a class annotated with <span class="keyword apiname">org.junit.platform.suite.api.Suite</span>, the `ResourceServerTestManager#start` method should be called before all other embedded servers start. In the teardown process, the `ResourceServerTestManager#stop` method should be called after all other hooks have completed.

    <span class="ph cmd">Update the following code for all suite classes: <span class="ph filepath">AbstractSpockTest.groovy</span>, <span class="ph filepath">AllSpockTests.groovy</span>, <span class="ph filepath">AllSpockTestsJUnit5MigratedSuite.groovy</span>, <span class="ph filepath">AllAccSpockTests.groovy</span>, and <span class="ph filepath">AllAccSpockTestsJUnit5MigratedSuite.groovy</span>.</span>

    <div class="itemgroup info">

    Previous Configuration:

    ``` pre
    @BeforeClass
    public static void startServerIfNeeded() {
        if (!TestSetupUtils.isServerStarted()) {
            SERVER_NEEDS_SHUTDOWN.set(true);
            TestSetupUtils.startServer();
        }
    }
     
    @AfterClass
    public static void stopServerIfNeeded() {
        if (SERVER_NEEDS_SHUTDOWN.get()) {
            TestSetupUtils.stopServer();
            SERVER_NEEDS_SHUTDOWN.set(false);
        }
    }
    ```

    Updated Configuration:

    ``` pre
    @BeforeClass
    public static void startServerIfNeeded() {
        if (!TestSetupUtils.isServerStarted()) {
            startResourceServerMock()
            SERVER_NEEDS_SHUTDOWN.set(true);
            TestSetupUtils.startServer();
        }
    }
     
    @AfterClass
    public static void stopServerIfNeeded() {
        if (SERVER_NEEDS_SHUTDOWN.get()) {
            TestSetupUtils.stopServer();
            SERVER_NEEDS_SHUTDOWN.set(false);
            stopResourceServerMock()
        }
    }
     
    private static void startResourceServerMock()
    {
        ResourceServerTestManager resourceServerTestManager = Registry.getApplicationContext().getBean("resourceServerTestManager", ResourceServerTestManager.class);
        resourceServerTestManager.start();
    }
     
    private static void stopResourceServerMock()
    {
        ResourceServerTestManager resourceServerTestManager = Registry.getApplicationContext().getBean("resourceServerTestManager", ResourceServerTestManager.class);
        resourceServerTestManager.stop();
    }
    ```

    </div>

4.  <span class="ph cmd">Add two new members to retrieve a JWT in new resource server.</span>
    <div class="itemgroup info">

    ``` pre
    protected static final Set<String> AUTHORITIES = Set.of("ROLE_USER", "ROLE_CUSTOM", "SCOPE_basic", "ROLE_CLIENT");
     
    private SignedJWTTokenFactory signedJWTTokenFactory = Registry.getApplicationContext()
            .getBean("signedJWTTokenFactory", SignedJWTTokenFactory.class);
    ```

    </div>

5.  <span class="ph cmd">Updated the following methods:</span>
    <div class="itemgroup info">

    Previous Configuration:

    ``` pre
    protected getOAuth2TokenUsingClientCredentials(RESTClient client, clientId, clientSecret) {
        HttpResponseDecorator response = client.post(
                uri: getOAuth2TokenUri(),
                path: getOAuth2TokenPath(),
                body: [
                        'grant_type'   : 'client_credentials',
                        'client_id'    : clientId,
                        'client_secret': clientSecret
                ],
                contentType: JSON,
                requestContentType: URLENC)
     
        with(response) {
            if (isNotEmpty(data) && isNotEmpty(data.error)) println(data)
            assert status == SC_OK
            assert data.token_type == 'bearer'
            assert data.access_token
            assert data.expires_in
        }
     
        return response.data
    }
     
    protected getOAuth2TokenUsingPassword(RESTClient client, clientId, clientSecret, username, password, boolean doAssert = true) {
        HttpResponseDecorator response = client.post(
                uri: getOAuth2TokenUri(),
                path: getOAuth2TokenPath(),
                body: [
                        'grant_type'   : 'password',
                        'client_id'    : clientId,
                        'client_secret': clientSecret,
                        'username'     : username,
                        'password'     : password
                ],
                contentType: JSON,
                requestContentType: URLENC)
     
        if (doAssert) {
            with(response) {
                if (isNotEmpty(data) && isNotEmpty(data.error)) println(data)
                assert status == SC_OK
                assert data.token_type == 'bearer'
                assert data.access_token
                assert data.expires_in
                assert data.refresh_token
            }
        }
     
        return response.data
    }
     
    protected refreshOAuth2Token(RESTClient client, refreshToken, clientId, clientSecret, redirectUri) {
        def bodyParams = [
                'grant_type'   : 'refresh_token',
                'refresh_token': refreshToken
        ]
     
        if (clientId) {
            bodyParams['client_id'] = clientId
        }
     
        if (clientSecret) {
            bodyParams['client_secret'] = clientSecret
        }
     
        if (redirectUri) {
            bodyParams['redirect_uri'] = URLEncoder.encode(redirectUri, 'UTF-8')
        }
     
        HttpResponseDecorator response = client.post(
                uri: getOAuth2TokenUri(),
                path: getOAuth2TokenPath(),
                body: bodyParams,
                contentType: JSON,
                requestContentType: URLENC)
     
        with(response) {
            if (isNotEmpty(data) && isNotEmpty(data.errors)) println(data)
            assert status == SC_OK
            assert data.token_type == 'bearer'
            assert data.access_token
            assert data.expires_in
            assert data.refresh_token
        }
     
        return response.data
    }
    ```

    Updated Configuration:

    ``` pre
    protected void addAuthorization(RESTClient client, token) {
        client.addHeader("Authorization", " Bearer " + (token as String))
    }
     
    protected SignedJWT getSignedJWT(final String username, final String clientId)
    {
        final var authorities = new HashSet<>(AUTHORITIES);
        if (clientId.equals("trusted_client"))
        {
            authorities.add("ROLE_TRUSTED_CLIENT")
        }
        final var builder = getSignedJWTTokenFactory().builder()
                .roles(authorities)
                .duration(Duration.ofSeconds(180));
        if (username != null)
        {
            builder.subject(username)
        }
        return builder.build();
    }
     
    protected SignedJWTTokenFactory getSignedJWTTokenFactory()
    {
        if (signedJWTTokenFactory == null)
        {
            signedJWTTokenFactory = Registry.getApplicationContext()
                    .getBean("signedJWTTokenFactory", SignedJWTTokenFactory.class);
        }
        return signedJWTTokenFactory
    }
     
    protected WsSecuredRequestBuilder createWsSecuredRequestBuilder(final String username, final String clientId)
    {
        final var jwt = getSignedJWT(username, clientId);
        return new WsSecuredRequestBuilder()//
                .extensionName(PermissionswebservicesConstants.EXTENSIONNAME)//
                .path(VERSION)
                .jwtAuthorization(jwt.serialize());
    }
     
    protected getOAuth2TokenUsingClientCredentials(RESTClient client, clientId, clientSecret) {
        return getSignedJWT(null, clientId).serialize()
    }
     
    protected getOAuth2TokenUsingPassword(RESTClient client, clientId, clientSecret, username, password, boolean doAssert = true) {
        return getSignedJWT(username, clientId).serialize()
    }
    ```

    </div>

6.  <span class="ph cmd">Update the following method for adopting new JWT string:</span>
    <div class="itemgroup info">

    Previous Configuration:

    ``` pre
    protected void addAuthorization(RESTClient client, token) {
        client.addHeader("Authorization", " Bearer " + (token.access_token as String))
    }
    ```

    Updated Configuration:

    ``` pre
    protected void addAuthorization(RESTClient client, token) {
        client.addHeader("Authorization", " Bearer " + (token as String))
    }
    ```

    </div>

7.  <span class="ph cmd">Remove the class `de.hybris.platform.ycommercewebservices.conv.Oauth2AccessTokenConverter` and its bean configuration from <span class="ph filepath">xstream-converters-spring.xml</span>.</span>
    <div class="itemgroup info">

    ``` pre
    <bean class="de.hybris.platform.commercefacades.xstream.conv.TypeConverterMapping">
        <property name="converter">
            <bean class="de.hybris.platform.ycommercewebservices.conv.Oauth2AccessTokenConverter" />
        </property>
    </bean>
    ```

    </div>

8.  <span class="ph cmd">Replace <span class="keyword apiname">OAuth2Constants</span> with <span class="keyword apiname">ResourceserverConstants</span> in `EXTENSIONS_TO_START` for testing purpose.</span>
    <div class="itemgroup info">

    Previous Configuration:

    ``` pre

    private static final String[] EXTENSIONS_TO_START = new String[]
    { YcommercewebservicesConstants.EXTENSIONNAME, OAuth2Constants.EXTENSIONNAME };
    ```

    Updated Configuration:

    ``` pre

    private static final String[] EXTENSIONS_TO_START = new String[]
    { YcommercewebservicesConstants.EXTENSIONNAME, ResourceserverConstants.EXTENSIONNAME };
    ```

    </div>

9.  <span class="ph cmd">Update the error message for unsupported content types.</span>
    <div class="itemgroup info">

    Previous Configuration:

    ``` pre
    'HttpMediaTypeNotSupportedError' | "Content type 'application/xml' not supported"
    ```

    Updated Configuration:

    ``` pre
    'HttpMediaTypeNotSupportedError' | "Content-Type 'application/xml' is not supported"
    ```

    </div>

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
