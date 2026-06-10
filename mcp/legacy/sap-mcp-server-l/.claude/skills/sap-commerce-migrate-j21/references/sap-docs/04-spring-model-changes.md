---
source_topic: SPRING_MODEL_CHANGES
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/202acfa790dc41149af8d5a33374a795.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: 202acfa790dc41149af8d5a33374a795
sap_version: v2211
fetched: 2026-04-30
title: "Model and Spring Bean Changes"
---

> Mirror of [Model and Spring Bean Changes](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/202acfa790dc41149af8d5a33374a795.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loio202acfa790dc41149af8d5a33374a795" class="page reference - topic-topic reference-reference">

# Model and Spring Bean Changes

<div class="body refbody">

Analyze changes in models and Spring beans to fully migrate to the new OAuth implementation.

<div id="loio202acfa790dc41149af8d5a33374a795__section_mvp_xpw_x2c" class="section">

In the new OAuth implementation, authorization-related data is stored differently than previously. All data is now stored in the <span class="keyword apiname">SAPOAuth2Authorization</span> model and the old models have been removed.

</div>

<div id="loio202acfa790dc41149af8d5a33374a795__section_nvp_xpw_x2c" class="section">

<div class="section section" type="Removed Models">

## Removed Models

The following models have been removed:

<div class="table-wrapper tablenoborder">

<table class="table" data-summary="" data-frame="border" data-border="1" data-rules="all">
<caption><span class="tablecap"><span class="tablecap">Removed Models</span></span></caption>
<colgroup>
<col style="width: 33%" />
<col style="width: 33%" />
<col style="width: 33%" />
</colgroup>
<thead class="thead" style="text-align:left;">
<tr class="row">
<th id="d140643e47" class="entry" style="vertical-align: top"><p>Removed Model</p></th>
<th id="d140643e50" class="entry" style="vertical-align: top"><p>Description</p></th>
<th id="d140643e53" class="entry" style="vertical-align: top"><p>Related Database Table</p></th>
</tr>
</thead>
<tbody class="tbody">
<tr class="row">
<td rowspan="2" class="entry" style="vertical-align: top" headers="d140643e47 "><p><span class="keyword apiname">OAuthAccessTokenModel</span></p></td>
<td rowspan="2" class="entry" style="vertical-align: top" headers="d140643e50 "><p>Object for storing access tokens.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e53 "><p><span class="keyword apiname">OAUTHACCESSTOKEN</span></p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e53 "><p><span class="keyword apiname">OAUTHACCESSTOKE6228SN</span></p></td>
</tr>
<tr class="row">
<td rowspan="2" class="entry" style="vertical-align: top" headers="d140643e47 "><p><span class="keyword apiname">OAuthRefreshTokenModel</span></p></td>
<td rowspan="2" class="entry" style="vertical-align: top" headers="d140643e50 "><p>Object for storing refresh tokens.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e53 "><p><span class="keyword apiname">OAUTHREFRESHTOKEN</span></p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e53 "><p><span class="keyword apiname">OAUTHREFRESHTOK6229SN</span></p></td>
</tr>
<tr class="row">
<td rowspan="2" class="entry" style="vertical-align: top" headers="d140643e47 "><p><span class="keyword apiname">OAuthAuthorizationCodeModel</span></p></td>
<td rowspan="2" class="entry" style="vertical-align: top" headers="d140643e50 "><p>Object for storing authorization codes.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e53 "><p><span class="keyword apiname">OAUTHAUTHORIZATIONCODE</span></p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e53 "><p><span class="keyword apiname">OAUTHAUTHORIZAT6231SN</span></p></td>
</tr>
</tbody>
</table>

</div>

<div id="n0t" class="title">

**Note**

</div>

In newer versions of Spring Security, several classes that have been removed and replaced with appropriate equivalents. Review your compilation logs to ensure that all new classes have been adjusted to fit your custom implementations.

For example, the <span class="keyword apiname">OAuth2Authentication</span> class has been replaced with specific authentication token types. For OAuth2 resource server authentication, replace all instances of <span class="keyword apiname">OAuth2Authentication</span> with <span class="keyword apiname">JwtAuthenticationToken</span>, which extends <span class="keyword apiname">AbstractOAuth2TokenAuthenticationToken</span>. Modify your token access logic to use <span class="keyword apiname">getToken().getTokenValue()</span> instead of the previous token retrieval methods.

</div>

</div>

<div id="loio202acfa790dc41149af8d5a33374a795__section_w4j_zpw_x2c" class="section">

<div class="section section" type="Modified Models">

## Modified Models

The model that is used for storing OAuth client data remains the same but has been adapted to the new OAuth server implementation:

<div class="table-wrapper tablenoborder">

<table class="table" data-summary="" data-frame="border" data-border="1" data-rules="all">
<caption><span class="tablecap"><span class="tablecap">Modified Models</span></span></caption>
<colgroup>
<col style="width: 33%" />
<col style="width: 33%" />
<col style="width: 33%" />
</colgroup>
<thead class="thead" style="text-align:left;">
<tr class="row">
<th id="d140643e173" class="entry" style="vertical-align: top"><p>Model</p></th>
<th id="d140643e176" class="entry" style="vertical-align: top"><p>New Attributes</p></th>
<th id="d140643e179" class="entry" style="vertical-align: top"><p>Deprecated Attributes</p></th>
</tr>
</thead>
<tbody class="tbody">
<tr class="row">
<td rowspan="8" class="entry" style="vertical-align: top" headers="d140643e173 "><p><span class="keyword apiname">OAuthClientDetailsModel</span></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e176 "><p><samp class="ph codeph">authenticationMethods</samp> - contains a set of authentication methods that clients can use to authenticate themselves.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e179 "><p><samp class="ph codeph">auto-approve</samp> - defines a set of auto-approved scopes. This attribute is not used anymore as it's no longer possible to define specific scopes that are approved automatically. In the new implementation, all scopes are approved automatically or a consent page is displayed. This behavior can be configured through <samp class="ph codeph">requireAuthorizationConsent</samp>.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e176 "><p><samp class="ph codeph">authorizationCodeValiditySeconds</samp> - configures the validity of authorization codes (in seconds).</p></td>
<td rowspan="7" class="entry" style="vertical-align: top" headers="d140643e179 "><p><samp class="ph codeph">resourceIds</samp></p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e176 "><p><samp class="ph codeph">requireProofKey</samp> - determines whether the PKCE flow is required for all authorization code requests.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e176 "><p><samp class="ph codeph">public</samp> - determines whether a client is public or confidential.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e176 "><p><samp class="ph codeph">requireAuthorizationConsent</samp> - defines if a consent screen should be displayed to a user during the authorization request flow.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e176 "><p><samp class="ph codeph">loginPageUri</samp> - defines an URI of an external login page.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e176 "><p><samp class="ph codeph">authorizations</samp> - defines a collection of <span class="keyword apiname">SAPOAuth2Authorization</span> objects related to a client.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e176 "><p><samp class="ph codeph">oidcTokenValiditySeconds</samp> - defines the validity of OIDC tokens (in seconds).</p></td>
</tr>
</tbody>
</table>

</div>

</div>

</div>

<div id="loio202acfa790dc41149af8d5a33374a795__section_ktg_1qw_x2c" class="section">

<div class="section section" type="Deprecated Models">

## Deprecated Models

The following models have been deprecated:

<div class="table-wrapper tablenoborder">

<table class="table" data-summary="" data-frame="border" data-border="1" data-rules="all">
<caption><span class="tablecap"><span class="tablecap">Deprecated Models</span></span></caption>
<colgroup>
<col style="width: 50%" />
<col style="width: 50%" />
</colgroup>
<thead class="thead" style="text-align:left;">
<tr class="row">
<th id="d140643e297" class="entry" style="vertical-align: top"><p>Model</p></th>
<th id="d140643e300" class="entry" style="vertical-align: top"><p>Description</p></th>
</tr>
</thead>
<tbody class="tbody">
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e297 "><p><span class="keyword apiname">OpenIDClientDetailsModel</span></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e300 "><p>OAuth client that is used for OpenID connect. It contains additional parameters required to support OpenID Connect. The parameters are not used in the new OAuth implementation.</p>
<p>Features such as external scopes and client-specific keystore definitions are not supported anymore.</p>
<p>OpenID clients should be redefined as standard <span class="keyword apiname">OAuthClientDetailsModel</span>.</p>
<p>For more information on configuring OpenID Connect, see <a href="d079f886cab647e5a0555e2cae8e4416.html" class="xref" title="Adjust your OAuth configuration to the new implementation that has been introduced as a result of moving the OAuth functionality from the Spring Security OAuth library, which has reached its EOL, to Spring Security.">Moving to the New OAuth Implementation</a>.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e297 "><p><span class="keyword apiname">OpenIDExternalScopesModel</span></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e300 "><p>Model that is used to define external scopes that can be added to a token in OpenID Connect.</p>
<p>External scopes are not supported in the new implementation.</p>
<p>For more information on configuring OpenID Connect, see <a href="d079f886cab647e5a0555e2cae8e4416.html" class="xref" title="Adjust your OAuth configuration to the new implementation that has been introduced as a result of moving the OAuth functionality from the Spring Security OAuth library, which has reached its EOL, to Spring Security.">Moving to the New OAuth Implementation</a>.</p></td>
</tr>
</tbody>
</table>

</div>

</div>

</div>

<div id="loio202acfa790dc41149af8d5a33374a795__section_kmq_btw_x2c" class="section">

<div class="section section" type="Removed Spring Beans">

## Removed Spring Beans

The following Spring beans have been removed:

<div class="table-wrapper tablenoborder">

<table class="table" data-summary="" data-frame="border" data-border="1" data-rules="all">
<caption><span class="tablecap"><span class="tablecap">Removed Spring Beans</span></span></caption>
<colgroup>
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
</colgroup>
<thead class="thead" style="text-align:left;">
<tr class="row">
<th id="d140643e388" class="entry" style="vertical-align: top"><p>Spring Bean ID</p></th>
<th id="d140643e391" class="entry" style="vertical-align: top"><p>Bean Class</p></th>
<th id="d140643e394" class="entry" style="vertical-align: top"><p>Spring Bean Alias</p></th>
<th id="d140643e397" class="entry" style="vertical-align: top"><p>Description</p></th>
<th id="d140643e400" class="entry" style="vertical-align: top"><p>New Equivalent</p></th>
</tr>
</thead>
<tbody class="tbody">
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultOAuthTokenDao</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.token.dao.impl.DefaultOAuthTokenDao</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">oauthTokenDao</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Default implementation of <span class="keyword apiname">de.hybris.platform.webservicescommons.oauth2.token.dao.OAuthTokenDao</span> for storing <span class="keyword apiname">OAuthAccessTokenModel</span> and <span class="keyword apiname">OAuthRefreshTokenModel</span>.</p>
<p>The <span class="keyword apiname">OAuthAccessTokenModel</span> and <span class="keyword apiname">OAuthRefreshTokenModel</span> have been removed, so the DAO is no longer needed.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>In the new implementation, access and refresh tokens are part of <span class="keyword apiname">SAPOAuth2AuthorizationModel</span>. <samp class="ph codeph">oAuth2AuthorizationDao</samp> (<samp class="ph codeph">com.sap.cx.commerce.platform.oauth2.authorizationserver.authorization.impl.DefaultOAuth2AuthorizationDao</samp>) is a related DAO bean for this model.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultOAuthTokenService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.token.impl.DefaultOAuthTokenService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">oauthTokenService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Default implementation of <span class="keyword apiname">de.hybris.platform.webservicescommons.oauth2.token.OAuthTokenService</span> for managing <span class="keyword apiname">OAuthAccessTokenModel</span> and <span class="keyword apiname">OAuthRefreshTokenModel</span>.</p>
<p>The <span class="keyword apiname">OAuthAccessTokenModel</span> and <span class="keyword apiname">OAuthRefreshTokenModel</span> have been removed, so the DAO is no longer needed.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>In the new implementation, access and refresh tokens are part of <span class="keyword apiname">SAPOAuth2AuthorizationModel</span>. <span class="keyword apiname">authorizationService</span> (<samp class="ph codeph">com.sap.cx.commerce.platform.oauth2.authorizationserver.authorization.impl.DefaultOAuth2AuthorizationService</samp>) is a related service bean for this model.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultOauthTokenStore</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.token.provider.HybrisOAuthTokenStore</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">oauthTokenStore</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Default implementation of the Spring <span class="keyword apiname">org.springframework.security.oauth2.provider.token.TokenStore</span> persistence interface for OAuth tokens.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p><samp class="ph codeph">org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService</samp> is an interface with a similar role in new authorization server. <span class="keyword apiname">authorizationService</span> (<samp class="ph codeph">com.sap.cx.commerce.platform.oauth2.authorizationserver.authorization.impl.DefaultOAuth2AuthorizationService</samp>) is a related service bean for this model.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultOauthTokenServices</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.token.provider.HybrisOAuthTokenServices</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Implementation for the Spring <span class="keyword apiname">org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices</span>, <span class="keyword apiname">org.springframework.security.oauth2.provider.token.ResourceServerTokenServices</span>, <span class="keyword apiname">org.springframework.security.oauth2.provider.token.ConsumerTokenServices</span> interfaces that are used for token management.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Currently, most functionality for token management is not accessible at the global Spring context level. Instead, beans for the authorization server are declared within the web context. However, you can customize certain aspects of token management by utilizing the <span class="keyword apiname">OAuth2TokenCustomizer</span>.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultCleanupAccessToken</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.CleanupAccessToken</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">cleanupAccessToken</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Job for cleanup of incompatible version of access tokens from database during system update. Only used for <span class="ph pname" translate="no">SAP Commerce Cloud</span> 6.1 and lower.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Not applicable</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">cleanupOAuthAccessTokenPerformable</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.jobs.maintenance.impl.CleanUpOAuthAccessTokenStrategy</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Strategy used for removing old access tokens from database based on the <span class="keyword apiname">cleanup.cronjob.oauth.access.token.expiry.time.seconds</span> property.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>In the new implementation, access tokens are part of <span class="keyword apiname">SAPOAuth2AuthorizationModel</span>. <span class="keyword apiname">cleanupOAuth2AuthorizationPerformable</span> (<samp class="ph codeph">com.sap.cx.commerce.platform.oauth2.authorizationserver.jobs.CleanUpOAuth2AuthorizationTokenStrategy</samp>) is a related bean that is used for a cleanup of old objects.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">cleanupOAuthRefreshTokenPerformable</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.jobs.maintenance.impl.CleanUpOAuthRefreshTokenStrategy</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Strategy used for removing old refresh tokens from the database based on the <samp class="ph codeph">cleanup.cronjob.oauth.refresh.token.expiry.time.seconds</samp> property.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>In the new implementation, refresh tokens are part of <span class="keyword apiname">SAPOAuth2AuthorizationModel</span>. <span class="keyword apiname">cleanupOAuth2AuthorizationPerformable</span> (<samp class="ph codeph">com.sap.cx.commerce.platform.oauth2.authorizationserver.jobs.CleanUpOAuth2AuthorizationTokenStrategy</samp>) is a related bean that is used for a cleanup of old objects.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">cleanupOAuthAuthorizationCodePerformable</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.jobs.maintenance.impl.CleanUpOAuthAuthorizationCodeStrategy</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Strategy used for removing old authorization codes from the database based on the <samp class="ph codeph">cleanup.cronjob.oauth.code.expiry.time.minutes</samp> property.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>In the new implementation, authorization codes are part of <span class="keyword apiname">SAPOAuth2AuthorizationModel</span>. <span class="keyword apiname">cleanupOAuth2AuthorizationPerformable</span> (<samp class="ph codeph">com.sap.cx.commerce.platform.oauth2.authorizationserver.jobs.CleanUpOAuth2AuthorizationTokenStrategy</samp>) is a related bean that is used for a cleanup of old objects.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultAuthorizationCode</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.AuthorizationCodeService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">oauthAuthorizationCode</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Implementation of the Spring <span class="keyword apiname">org.springframework.security.oauth2.provider.code.AuthorizationCodeServices</span> interface that is used for issuing and storing authorization codes.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>In the new implementation, authorization codes are part of <span class="keyword apiname">SAPOAuth2AuthorizationModel</span>. <span class="keyword apiname">authorizationService</span>(<samp class="ph codeph">com.sap.cx.commerce.platform.oauth2.authorizationserver.authorization.impl.DefaultOAuth2AuthorizationService</samp>) is a related service bean for this model.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultOauthClientDetails</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.client.impl.DefaultClientDetailsService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">oauthClientDetails</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Implementation of the Spring <span class="keyword apiname">org.springframework.security.oauth2.provider.ClientDetailsService</span> interface that provides OAuth2 client details.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p><span class="keyword apiname">org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository</span> is an interface with similar functionality. Its implementation is defined as <span class="keyword apiname">defaultClientDetailsRepository</span> (<samp class="ph codeph">com.sap.cx.commerce.platform.oauth2.authorizationserver.client.DefaultClientDetailsRepository</samp>).</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "> </td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultOAuthRevokeTokenService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.token.impl.DefaultOAuthRevokeTokenService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">oauthRevokeTokenService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Default implementation of <samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.token.OAuthRevokeTokenService</samp>that handles access and refresh token revocation.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p><span class="keyword apiname">oAuth2RevocationTokenService</span> (<span class="keyword apiname">com.sap.cx.commerce.platform.oauth2.authorizationserver.revocation.impl.DefaultOAuth2RevocationTokenService</span>) is a Spring bean with similar functionality.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">onUserPasswordChangeTokenRevoker</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.OnUserPasswordChangeTokenRevoker</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Class that is responsible for revoking OAuth tokens when users change their password.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Bean handling revocation for authorization objects is defined as <span class="keyword apiname">onUserPasswordChangeTokenRevocation</span> (<span class="keyword apiname">com.sap.cx.commerce.platform.oauth2.authorizationserver.revocation.OnUserPasswordChangeTokenRevocation</span>).</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "> </td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">openIDClientDetails</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.client.impl.OpenIDClientDetailsService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><span class="keyword apiname">oauthClientDetails</span></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Implementation of the Spring <samp class="ph codeph">org.springframework.security.oauth2.provider.ClientDetailsService</samp> interface that provides details on an OAuth2 client and adds support for OpenID Connect.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Not applicable since no specific implementation for OpenId Connect is implemented and standard Spring implementation is used.</p>
<p>The default implementation is defined as <span class="keyword apiname">defaultClientDetailsRepository</span> (<span class="keyword apiname">com.sap.cx.commerce.platform.oauth2.authorizationserver.client.DefaultClientDetailsRepository</span>)</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">openIDTokenServices</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.token.provider.HybrisOpenIDTokenServices</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><span class="keyword apiname">oauthTokenServices</span></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Extended and deprecated implementation for the Spring <span class="keyword apiname">org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices</span>, <span class="keyword apiname">org.springframework.security.oauth2.provider.token.ResourceServerTokenServices</span>, <span class="keyword apiname">org.springframework.security.oauth2.provider.token.ConsumerTokenServices</span> interfaces responsible for access token management and OpenID Connect support.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Not applicable since no specific implementation for OpenId Connect is implemented and the standard Spring implementation is used.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">oidcService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.services.impl.DefaultOIDCService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>OpenID Connect service functionality that provides support for OpenID Connect, for example, OpenID Connect feature configuration.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Not applicable</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultHybrisOpenIDTokenServices</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.services.impl.DefaultHybrisOpenIDTokenServices</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">oauthTokenServices</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Extended implementation for the Spring <span class="keyword apiname">org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices</span>, <span class="keyword apiname">org.springframework.security.oauth2.provider.token.ResourceServerTokenServices</span>, <span class="keyword apiname">org.springframework.security.oauth2.provider.token.ConsumerTokenServices</span> interfaces that is used for access token management and OpenID Connect support.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Not applicable since no specific implementation for OpenId Connect is implemented and the standard Spring implementation is used.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultExternalScopesStrategy</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.scope.impl.DefaultExternalScopesStrategy</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">externalScopesStrategy</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Strategy supporting the external scopes feature for OpenID Connect.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Not applicable since the external scopes feature is not supported in the new implementation and the standard Spring implementation is used.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultExternalScopesDao</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.scope.impl.DefaultExternalScopesDao</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">externalScopesDao</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Default implementation of the <span class="keyword apiname">de.hybris.platform.webservicescommons.oauth2.scope.ExternalScopesDaofor</span> external scopes.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Not applicable since the external scopes feature is not supported in new implementation and the standard Spring implementation is used.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">rsaKeyStoreHelper</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.jwt.util.RsaKeyStoreHelper</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">keyStoreHelper</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Helper class that is used by OpenID Connect.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Not applicable</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "> </td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultCustomTokenGranter</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.provider.custom.DefaultCustomTokenGranter</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">customTokenGranter</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Default implementation of the Spring <span class="keyword apiname">org.springframework.security.oauth2.provider.TokenGranter</span> interface that accepts the <span class="q">“custom”</span> grant type.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "> </td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "> </td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">oauthSecurityChecker</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.oauth2.util.OAuth2SecurityChecker</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Bean that is used to configure access to resource server endpoints, for example:</p>
<pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_ywy_lmd_y2c" class="pre codeblock prettyprint"><code>&lt;intercept-url pattern=&quot;/**&quot; method=&quot;GET&quot; access=&quot;@oauthSecurityChecker.hasScope(authentication,&#39;basic&#39;)&quot;/&gt;</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>You can replace it with the Spring Security build-in <span class="keyword apiname">hasAuthority()</span> method, for example:</p>
<pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_pn1_rmd_y2c" class="pre codeblock prettyprint"><code>&lt;intercept-url pattern=&quot;/**&quot; method=&quot;GET&quot; access=&quot;@oauthSecurityChecker.hasScope(authentication,&#39;basic&#39;)&quot;/&gt;</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "> </td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">tokenStoreUserApprovalHandler</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">org.springframework.security.oauth2.provider.approval.TokenStoreUserApprovalHandler</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">userApprovalHandler</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Implementation of the Spring <span class="keyword apiname">org.springframework.security.oauth2.provider.approval.UserApprovalHandler</span>interface that determines whether a given authorization request has already been approved by a user.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p><span class="keyword apiname">org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService.Spring</span> is an interface with similar functionality.</p>
<p>Default implementation is defined as <span class="keyword apiname">noOpOAuth2AuthorizationConsentService</span> with the<samp class="ph codeph">oAuth2AuthorizationConsentService</samp> alias (<samp class="ph codeph">com.sap.cx.commerce.platform.oauth2.authorizationserver.client.NoOpOAuth2AuthorizationConsentService</samp>). This implementation does not currently store user consent once it has been provided. To enable this functionality, create a custom implementation.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultOAuth2RequestFactory</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">org.springframework.security.oauth2.provider.request.DefaultOAuth2RequestFactory</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">oAuth2RequestFactory</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Implementation of the Spring <span class="keyword apiname">org.springframework.security.oauth2.provider.OAuth2RequestFactory</span> interface that is used for managing <span class="keyword apiname">AuthorizationRequest</span>, <span class="keyword apiname">TokenRequest</span>, <span class="keyword apiname">OAuth2Request</span> Spring objects.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p>Not applicable</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">defaultOauthClientDetailsUserService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">org.springframework.security.oauth2.provider.client.ClientDetailsUserDetailsService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "><p><samp class="ph codeph">oauthClientDetailsUserService</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Implementation of the Spring <span class="keyword apiname">org.springframework.security.core.userdetails.UserDetailsService</span> interface that loads user-specific data for OAuth clients</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "> </td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">oauth2ExceptionRender</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">org.springframework.security.oauth2.provider.error.DefaultOAuth2ExceptionRenderer</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Implementation of the <span class="keyword apiname">Springorg.springframework.security.oauth2.provider.error.OAuth2ExceptionRenderer</span> interface that can render exceptions using message converters.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "> </td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">oauthAccessDeniedHandler</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">org.springframework.security.oauth2.provider.error.OAuth2AccessDeniedHandler</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Implementation of the Spring <span class="keyword apiname">org.springframework.security.web.access.AccessDeniedHandler</span> interface that is specific for OAuth.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e388 "><p><samp class="ph codeph">hybrisUserFilter</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e391 "><p><samp class="ph codeph">de.hybris.platform.webservicescommons.oauth2.HybrisOauth2UserFilter</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d140643e394 "> </td>
<td class="entry" style="vertical-align: top" headers="d140643e397 "><p>Filter to set current user context from an access token.</p></td>
<td class="entry" style="vertical-align: top" headers="d140643e400 "><p><samp class="ph codeph">jwtAuthenticationUserFilter</samp> (<span class="keyword apiname">com.sap.cx.commerce.platform.oauth2.resourceserver.filter.JwtAuthenticationUserFilter</span>) is a related bean that is used for setting current user context from an access token.</p></td>
</tr>
</tbody>
</table>

</div>

</div>

</div>

<div id="loio202acfa790dc41149af8d5a33374a795__section_c3f_qqd_y2c" class="section">

<div class="section section" type="Removed Properties">

## Removed Properties

The following properties have been removed:

<div class="table-wrapper tablenoborder">

<table class="table" data-summary="" data-frame="border" data-border="1" data-rules="all">
<caption><span class="tablecap"><span class="tablecap"></span></span></caption>
<colgroup>
<col style="width: 50%" />
<col style="width: 50%" />
</colgroup>
<thead class="thead" style="text-align:left;">
<tr class="row">
<th id="d140643e1311" class="entry" style="vertical-align: top"><p>Property</p></th>
<th id="d140643e1314" class="entry" style="vertical-align: top"><p>New Implementation Equivalent</p></th>
</tr>
</thead>
<tbody class="tbody">
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_nql_xqd_y2c" class="pre codeblock prettyprint"><code># Allows you to configure the length for generated authorization codes
oauth2.authorizationcode.length=30</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><p>Not applicable as secure authorization codes of the length of 128 characters are generated in the default Spring implementation.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_pzj_yqd_y2c" class="pre codeblock prettyprint"><code># CORS Configuration
corsfilter.oauth2.allowedOrigins=*
corsfilter.oauth2.allowedHeaders=origin content-type accept authorization
corsfilter.oauth2.allowedMethods=GET POST HEAD PUT PATCH DELETE OPTIONS</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_e5b_krd_y2c" class="pre codeblock prettyprint"><code># CORS Configuration
corsfilter.authorizationserver.allowedOrigins=*
corsfilter.authorizationserver.allowedHeaders=origin content-type accept authorization
corsfilter.authorizationserver.allowedMethods=GET POST HEAD PUT PATCH DELETE OPTIONS</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_cyl_mrd_y2c" class="pre codeblock prettyprint"><code># Enabled retry of saving access tokens in a situation when an attempt ends with ModelSavingException being thrown
# due to duplicate access token id caused by two or more threads trying to create the same access token
# in the HybrisOAuthTokenStore.
oauth2.accesstoken.save.retry=true</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><p>Not applicable</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_zqt_nrd_y2c" class="pre codeblock prettyprint"><code># Enables optimization which consists in not updating the OAuthAccessTokenModel object for method call
# DefaultOAuthTokenService::saveAccessToken when is no change to OAuth2Authentication
oauth2.optimize.accesstoken.save.enabled=true</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><p>Not applicable</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_ldg_prd_y2c" class="pre codeblock prettyprint"><code># Enables refresh token processing by applying DB row locking (DB other than HSQLDB) / selective threads locking (for HSQLDB only)
# and also applies DB row locking for refresh token (DB other than HSQLDB) before refresh token removal operation
# DB row locking / selective threads locking acts as a security barrier
# preventing refresh tokens to be consumed more than once by concurrent requests
# Setting this flag to &#39;true&#39; prevents from DuplicateKeyException caused by authenticationIdIdx index unique constraint violation.
oauthauthorizationserver.tokenServices.refreshWithLock=true</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><p>Not applicable</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_kmr_qrd_y2c" class="pre codeblock prettyprint"><code># Enables storing authorization code as SHA signature
oauth2.authorizationcode.stored.as.sha.signature=true</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><p>Not applicable as authorization codes are stored as SHA signature by default.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_kdn_vrd_y2c" class="pre codeblock prettyprint"><code># Global configuration for access token validity time. Used if not configured on OAuth client level.
# 60*60*12 = 12h
oauth2.accessTokenValiditySeconds=43200</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_u23_wrd_y2c" class="pre codeblock prettyprint"><code># validity in seconds of access token when no value is set for client in model OAuthClientDetails attribute accessTokenValiditySeconds
authserver.accessToken.timeToLive.seconds=300</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_g1c_xrd_y2c" class="pre codeblock prettyprint"><code># Global configuration for identity token validity time.
# 60*60*12 = 12h
oauth2.idTokenValiditySeconds=43200</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_bzd_yrd_y2c" class="pre codeblock prettyprint"><code># validity in seconds of OIDC token when no value is set for client in model OAuthClientDetails attribute oidcTokenValiditySeconds
authserver.oidcToken.timeToLive.seconds=1800</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_zrg_1sd_y2c" class="pre codeblock prettyprint"><code># Global configuration for refresh token validity time. Used if not configured on OAuth client level.
# 60*60*24*30 = 30d
oauth2.refreshTokenValiditySeconds=2592000</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_nrb_bsd_y2c" class="pre codeblock prettyprint"><code># validity in seconds of refresh token when no value is set for client in model OAuthClientDetails attribute refreshTokenValiditySeconds
authserver.refreshToken.timeToLive.seconds=3600
# validity in seconds of refresh token issued for public clients when no value is set for client in model OAuthClientDetails attribute refreshTokenValiditySeconds
authserver.publicClients.refreshToken.timeToLive.seconds=3600</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_n3v_csd_y2c" class="pre codeblock prettyprint"><code># Global configuration indicating if refresh token is supported.
oauth2.supportRefreshToken=true</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><p>Not applicable as support for refresh tokens is configured at the OAuth client level through the <samp class="ph codeph">refresh_token</samp> authorization grant type.</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_rjq_msd_y2c" class="pre codeblock prettyprint"><code># Jar scanning setup for Tomcat
oauth2.tomcat.tld.scan
oauth2.tomcat.tld.default.scan.enabled
oauth2.tomcat.pluggability.scan
oauth2.tomcat.pluggability.default.scan.enabled</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_vgk_nsd_y2c" class="pre codeblock prettyprint"><code># Jar scanning setup for Tomcat
authorizationserver.tomcat.tld.scan=jakarta.servlet.jsp.jstl-*.jar
authorizationserver.tomcat.tld.default.scan.enabled=false
authorizationserver.tomcat.pluggability.scan=jakarta.servlet.jsp.jstl-*.jar
authorizationserver.tomcat.pluggability.default.scan.enabled=false</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_l1f_4sd_y2c" class="pre codeblock prettyprint"><code># key id
#oauth2.kid=test1
# keystore location
#oauth2.keystore.location=/security/keystore.jks
# keystore password
#oauth2.keystore.password=nimda123
&#10;#algorithm default RS256 (shouldn&#39;t be changed without having an additional implementation of another algorithm)
#oauth2.algorithm=RS256</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><p>Not applicable since it's not possible to set up separate keystores for each client. A global configuration is supported.</p>
<pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_qv1_ysd_y2c" class="pre codeblock prettyprint"><code>authserver.keystore.location</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_tvn_zsd_y2c" class="pre codeblock prettyprint"><code># Property for defining the time in minutes for cleaning up of oauth codes.
cleanup.cronjob.oauth.code.expiry.time.minutes=5</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_ijk_1td_y2c" class="pre codeblock prettyprint"><code># specifies additional time delay for determining expired oauth2 records in cleanup process:
# the algorithm retrieves records for which the condition expireDate &lt;= current time - delay
authserver.cleanup.cronjob.additional.delay.to.fetch.expired.records.seconds=300
# specifies the cron expression for the cleanup process, a default is every hour.
authserver.cleanup.cronjob.expression=0 0 * ? * *</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_m3t_btd_y2c" class="pre codeblock prettyprint"><code># Property for defining the time in minutes for cleaning up of oauth refresh tokens (60*5)
cleanup.cronjob.oauth.access.token.expiry.time.seconds=300</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_dkn_ctd_y2c" class="pre codeblock prettyprint"><code># specifies additional time delay for determining expired oauth2 records in cleanup process:
# the algorithm retrieves records for which the condition expireDate &lt;= current time - delay
authserver.cleanup.cronjob.additional.delay.to.fetch.expired.records.seconds=300
# specifies the cron expression for the cleanup process, a default is every hour.
authserver.cleanup.cronjob.expression=0 0 * ? * *</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_j5c_2td_y2c" class="pre codeblock prettyprint"><code># Property for defining the time in minutes for cleaning up of oauth refresh tokens (60*5)
cleanup.cronjob.oauth.refresh.token.expiry.time.seconds=300</code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_xs3_2td_y2c" class="pre codeblock prettyprint"><code># specifies additional time delay for determining expired oauth2 records in cleanup process:
# the algorithm retrieves records for which the condition expireDate &lt;= current time - delay
authserver.cleanup.cronjob.additional.delay.to.fetch.expired.records.seconds=300
# specifies the cron expression for the cleanup process, a default is every hour.
authserver.cleanup.cronjob.expression=0 0 * ? * *</code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_gdk_ftd_y2c" class="pre codeblock prettyprint"><code># Specifies if new refresh token should be created during refreshing an Access Token
# reuseRefreshToken = true - old refresh token will be returned, refresh token can be used more than one time
# reuseRefreshToken = false - new refresh token will be created
oauthauthorizationserver.tokenServices.reuseRefreshToken=false    </code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_wth_gtd_y2c" class="pre codeblock prettyprint"><code># specifies whether reuse refresh token
authserver.refreshToken.reuse=false
&#10;# specifies whether reuse refresh token for public clients
authserver.publicClients.refreshToken.reuse=false</code></pre>
<pre id="loio202acfa790dc41149af8d5a33374a795__pre_kjf_wqd_y2c" class="pre"><code></code></pre></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d140643e1311 "><pre id="loio202acfa790dc41149af8d5a33374a795__codeblock_fnb_htd_y2c" class="pre codeblock prettyprint"><code>#Example set of properties to integrate with kyma
#oauth2.kyma.algorithm=RS256
#oauth2.kyma.responseTypes=code,code id_token,id_token,token id_token
#oauth2.kyma.kid=kyma
#oauth2.kyma.keystore.location=/security/keystore.jks
#oauth2.kyma.keystore.password=nimda123
#oauth2.kyma.public.address=www.myshop.com </code></pre></td>
<td class="entry" style="vertical-align: top" headers="d140643e1314 "><p>Not applicable since it's not possible to set up separate keystores for each client. A global configuration is supported.</p></td>
</tr>
</tbody>
</table>

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
