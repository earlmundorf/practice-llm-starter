---
source_topic: OAUTH
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/d079f886cab647e5a0555e2cae8e4416.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: d079f886cab647e5a0555e2cae8e4416
sap_version: v2211
fetched: 2026-04-30
title: "Moving to the New OAuth Implementation"
---

> Mirror of [Moving to the New OAuth Implementation](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/d079f886cab647e5a0555e2cae8e4416.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loiod079f886cab647e5a0555e2cae8e4416" class="page concept - topic-topic concept-concept">

# Moving to the New OAuth Implementation

<div class="body conbody">

Adjust your OAuth configuration to the new implementation that has been introduced as a result of moving the OAuth functionality from the Spring Security OAuth library, which has reached its EOL, to Spring Security.

In this release, the <span class="keyword apiname yext">oauth2</span> extension has been removed and replaced with a new OAuth implementation that comes with the new <span class="keyword apiname yext">authorizationserver</span>, <span class="keyword apiname yext">resourceserver</span>, and <span class="keyword apiname yext">oauth2commons</span> extensions. The changes are a result of moving the OAuth functionality from the Spring Security OAuth 2.5.x library that reached its EOL in May 2022 to Spring Security 6.x. As Spring Security isn't fully compatible with Spring Security OAuth, a few changes have been made to the OAuth implementation that required creating new extensions and adjusting their configuration to what is supported in Spring Security.

The supported OAuth version depends on the currently supported version of the Spring Authorization Server. For more information on currently supported libraries, see <a href="cba026d2b36c4ab18f89525df92cc815.html" class="xref" title="An update release contains all existing components of SAP Commerce Cloud as opposed to, by way of example only, a hot fix or a module-specific update. The release is delivered as one unit for the purpose of effective testing. This avoids having unlimited combinations of updated modules. The latest productive update release always includes the content of previous update releases.">Update Releases</a>.

Study the following migration guide and carry out any necessary steps to ensure that you've fully migrated to the new OAuth implementation.

<div id="n0t" class="title">

**Note**

</div>

Some steps might have already been covered by a configuration of an extension that you have already included in your build, for example, the <span class="keyword apiname yext">webservicescommons</span> or <span class="keyword apiname yext">apiframework</span> extensions.

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_ufp_nvd_sbc" class="section">

<div class="section section" type="How the New OAuth Implementation Works">

## How the New OAuth Implementation Works

The new OAuth implementation now supports JWTs (JSON Web Tokens) only. All information is now located within a token. Opaque tokens aren't supported anymore.

Here's a high-level summary of the new OAuth flow:

1.  A client requests an access token.

2.  The authorization server returns a signed JWT access token.

3.  A client requests a resource with the signed JWT access token.

4.  The resource server verifies the JWT access token signature with public keys accessible under a JWK endpoint and verifies user permissions.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_xv3_plp_rbc" class="section">

<div class="section section" type="Enabling the New OAuth Implementation">

## Enabling the New OAuth Implementation

<span class="ph pname" translate="no">SAP Commerce Cloud</span> is now shipped with the following new extensions:

- the <span class="keyword apiname yext">authorizationserver</span> extension that holds the authorization server configuration

- the <span class="keyword apiname yext">resourceserver</span> extension that holds the resource server configuration

- the <span class="keyword apiname yext">oauth2commons</span> extension that holds the common configuration for the deprecated and the new implementation, and contains common parts of the <span class="keyword apiname yext">authorizationserver</span> and <span class="keyword apiname yext">resourceserver</span> configuration

The new extensions are part of the default configuration.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_yln_dww_rbc" class="section">

<div class="section section" type="Configuring the New OAuth Implementation">

## Configuring the New OAuth Implementation

To adjust the new OAuth implementation to the needs of your module, use the properties provided with the new extensions.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_h1n_wfs_w2c" class="section">

<div class="section section" type="Configuring OAuth Clients">

## Configuring OAuth Clients

The new implementation supports two types of clients:

- confidential clients

- public clients

Creating OAuth clients is similar to the deprecated OAuth implementation. However, there are some new attributes in <span class="keyword apiname">OAuthClientDetailsModel</span> that you can configure:

<div class="table-wrapper tablenoborder">

<table class="table" data-summary="" data-frame="border" data-border="1" data-rules="all">
<caption><span class="tablecap"><span class="tablecap">New OAuthClientDetailsModel Attributes</span></span></caption>
<colgroup>
<col style="width: 25%" />
<col style="width: 25%" />
<col style="width: 25%" />
<col style="width: 25%" />
</colgroup>
<thead class="thead" style="text-align:left;">
<tr class="row">
<th id="d132989e170" class="entry" style="vertical-align: top"><p>Attribute</p></th>
<th id="d132989e173" class="entry" style="vertical-align: top"><p>Description</p></th>
<th id="d132989e176" class="entry" style="vertical-align: top"><p>Possible Values</p></th>
<th id="d132989e179" class="entry" style="vertical-align: top"><p>Configuration Property with Default Value</p></th>
</tr>
</thead>
<tbody class="tbody">
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e170 "><p><samp class="ph codeph">authenticationMethods</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e173 "><p>Contains a set of authentication methods that clients can use to authenticate themselves.</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e176 "><ul>
<li><p><samp class="ph codeph">client_secret_post</samp></p></li>
<li><p><samp class="ph codeph">client_secret_basic</samp></p></li>
<li><p><samp class="ph codeph">none</samp></p></li>
</ul></td>
<td class="entry" style="vertical-align: top" headers="d132989e179 "><p><samp class="ph codeph">authserver.client.authenticationMethods=client_secret_post,client_secret_basic,none </samp></p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e170 "><p><samp class="ph codeph">authorizationCodeValiditySeconds</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e173 "><p>Configures the validity of authorization codes (in seconds).</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e176 "> </td>
<td class="entry" style="vertical-align: top" headers="d132989e179 "><p><samp class="ph codeph">authserver.authorizationCode.timeToLive.seconds=300 </samp></p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e170 "><p><samp class="ph codeph">requireProofKey</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e173 "><p>Determines whether the PKCE (Proof of Key Code Exchange) flow is required for all authorization code requests.</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e176 "><ul>
<li><p><samp class="ph codeph">true</samp> (default) - PKCE is required</p></li>
<li><p><samp class="ph codeph">false</samp> - PKCE is optional</p></li>
</ul></td>
<td class="entry" style="vertical-align: top" headers="d132989e179 "><p>Not available</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e170 "><p><samp class="ph codeph">public</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e173 "><p>Determines whether a client is public or confidential.</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e176 "><ul>
<li><p><samp class="ph codeph">true</samp> - a client is public</p></li>
<li><p><samp class="ph codeph">false</samp>(default) - a client is confidential</p></li>
</ul></td>
<td class="entry" style="vertical-align: top" headers="d132989e179 "><p>Not available</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e170 "><p><samp class="ph codeph">requireAuthorizationConsent</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e173 "><p>Defines if a consent screen should be displayed to a user during the authorization request flow.</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e176 "><ul>
<li><p><samp class="ph codeph">true</samp> - a consent screen is displayed</p></li>
<li><p><samp class="ph codeph">false</samp>(default) - a consent screen isn't displayed and a user is automatically authenticated</p></li>
</ul></td>
<td class="entry" style="vertical-align: top" headers="d132989e179 "><p>Not available</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e170 "><p><samp class="ph codeph">loginPageUri</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e173 "><p>Defines an URI of an external login page.</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e176 "><p>When you don't specify any value, the default login page is displayed.</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e179 "><p>Not available</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e170 "><p><samp class="ph codeph">authorizations</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e173 "><p>Defines a collection of <span class="keyword apiname">SAPOAuth2Authorization</span> objects related to a client.</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e176 "> </td>
<td class="entry" style="vertical-align: top" headers="d132989e179 "><p>Not available</p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e170 "><p><samp class="ph codeph">oidcTokenValiditySeconds</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e173 "><p>Defines the validity of OIDC tokens (in seconds).</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e176 "> </td>
<td class="entry" style="vertical-align: top" headers="d132989e179 "><p><samp class="ph codeph">authserver.oidcToken.timeToLive.seconds=1800</samp></p></td>
</tr>
</tbody>
</table>

</div>

The `auto-approve` and `resourceIds` attributes are deprecated and aren't used in the default configuration.

<div id="n1t" class="title">

**Note**

</div>

You can configure all <span class="keyword apiname">OAuthClientDetailsModel</span> attributes in <span class="ph" translate="no">Backoffice</span> in the <span class="ph uicontrol">OAuth Clients</span> configuration details or through ImpEx, with the only exception of the `authorizations` attribute that can't be configured through Backoffice.

<div class="sectiondiv subsection">

### PKCE

Setting the `requireProofKey` attribute to `true` means that the PKCE flow is required for all authorization code requests, while setting it to `false` means that PKCE is optional for a client. Using PKCE is obligatory for public clients and optional for confidential clients. However, if not explicitly specified, it's activated by default.

</div>

<div class="sectiondiv subsection">

### Relative Redirect URI for OAuth Clients

According to the OAuth specification, the redirection endpoint URI that is used in the authorization code flow must be an absolute URI. Because of that, the new implementation doesn't allow for using relative redirect URIs. Adapt your configuration of OAuth clients to contain absolute redirect URIs only. If using absolute URIs isn't possible in your configuration, change the behavior by setting the following property to `false`:

``` pre
authserver.authorizationCode.absolute.redirect.uri.check=false
```

By default, the property is set to `true`.

</div>

<div class="sectiondiv subsection">

### Confidential Clients

The value of the `public` attribute of the <span class="keyword apiname">OAuthClientDetails</span> model defines whether a client is public or confidential. By default, the value of this attribute is set to `false`.

</div>

<div class="sectiondiv subsection">

### Public Clients

Set the value of the `public` attribute to `true` for specific public clients.

</div>

<div class="sectiondiv subsection">

### Client Validation

Each created and updated <span class="keyword apiname">OAuthClientDetails</span> object is validated against certain preconditions by <span class="keyword apiname">DefaultOAuthClientDetailsValidator</span>. The preconditions ensure the following:

1.  The list of authorized grant types isn't empty.

2.  A client has at least one assigned authority.

3.  For a client using the authorization code grant type, at least one redirect URI is registered.

4.  A public client has no assigned secrets.

5.  PKCE has been activated for a public client.

6.  A public client has no client credentials grant type activated.

7.  A confidential client has a client secret.

8.  A client model can be successfully converted to a <span class="keyword apiname">RegisteredClient</span> object, which is used by Spring when managing OAuth clients. Essentially, <span class="keyword apiname">OAuthClientDetailsModel</span> is a database representation of this Spring object.

When any of the preconditions aren't met, <span class="keyword apiname">InterceptorException</span> is thrown.

<div class="p">

To streamline the migration process, the following property has been introduced to enable a <span class="q">“dry run”</span> mode for the validator:

``` pre
authserver.client.validation.dry.run=true
```

</div>

When activated, this mode logs validation issues as warnings instead of throwing exceptions. By default, this property is set to `false`, which means that if any of the listed preconditions fail during the creation or update of <span class="keyword apiname">OAuthClientDetails</span>, an exception is thrown.

</div>

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_mpc_1q1_z2c" class="section">

<div class="section section" type="Token TTL Configuration">

## Token TTL Configuration

Use the following properties to configure TTL (time-to-live) of tokens:

<div class="table-wrapper tablenoborder">

| Property | Description | Default Value |
|----|----|----|
| `authserver.refreshToken.timeToLive.seconds` | Default validity (in seconds) of refresh tokens, if not set explicitly in the `refreshTokenValiditySeconds` attribute in the <span class="keyword apiname">OAuthClientDetails</span> model. | `3600` |
| `authserver.accessToken.timeToLive.seconds` | Default validity (in seconds) of access tokens, if not specified for a client in the `accessTokenValiditySeconds` attribute in the <span class="keyword apiname">OAuthClientDetails</span> model. | `300` |
| `authserver.authorizationCode.timeToLive.seconds` | Default validity (in seconds) of authorization codes, if not specified for a client in the `authorizationCodeValiditySeconds` attribute in the <span class="keyword apiname">OAuthClientDetails</span> model. | `300` |
| `authserver.oidcToken.timeToLive.seconds` | Default validity (in seconds) of OIDC, if not specified for a client in the `oidcTokenValiditySeconds` attribute in the <span class="keyword apiname">OAuthClientDetails</span> model. | `1800` |

<span class="tablecap"><span class="tablecap">Token and Code Validity Properties </span></span> {.table summary="" frame="border" border="1" rules="all"}

</div>

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_ixd_hcz_w2c" class="section">

<div class="section section" type="Cleanup of Tokens and Codes">

## Cleanup of Tokens and Codes

Access and refresh tokens and authorization codes are deleted from the database after they expire. The cleanup process can be configured through the following properties:

<div class="table-wrapper tablenoborder">

| Property | Description | Default Value |
|----|----|----|
| `authserver.cleanup.cronjob.additional.delay.to.fetch.expired.records.seconds` | Specifies an additional time delay for determining expired OAuth2 records in the cleanup process. The algorithm retrieves records where the condition is `expireDate` \<= current time - delay. | `300` |
| `authserver.cleanup.cronjob.expression` | Specifies a cron job expression for the cleanup process. The default expression equals every hour. | `0 0 * ? * * ` |

<span class="tablecap"><span class="tablecap">Cleanup Properties</span></span> {.table summary="" frame="border" border="1" rules="all"}

</div>

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_s2n_mcz_w2c" class="section">

<div class="section section" type="Issuer “iss” Claim of JWT Configuration">

## Issuer <span class="q">“iss”</span> Claim of JWT Configuration

You can configure the <span class="q">“iss”</span> issuer claim of a JWT using the following property:

``` pre
authserver.jwt.claims.iss
```

The property defines the JWT issuer. If not specified, the issuer is automatically set to the authorization server's endpoint.

The value of this property isn't specified in the default configuration.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_zlp_2hb_4fc" class="section">

<div class="section section" type="Supported Endpoints">

## Supported Endpoints

<span class="ph pname" translate="no">SAP Commerce Cloud</span> now exposes the following endpoints:

- Issuer endpoint: <span class="ph filepath">https://`<server_url>`/authorizationserver/oauth/</span>

- Authorization endpoint: <span class="ph filepath">https://`<server_url>`/authorizationserver/oauth/authorize</span>

- Token endpoint: `https://``<server_url>``/authorizationserver/oauth/token`

  - Supported authentication methods: `client_secret_basic`, `client_secret_post`

- JWKS URI endpoint: <span class="ph filepath">https://`<server_url>`/authorizationserver/oauth/jwks</span>

- Userinfo endpoint: <span class="ph filepath">https://`<server_url>`/authorizationserver/oauth/userinfo</span>

- Revocation endpoint: <span class="ph filepath">https://`<server_url>`/authorizationserver/oauth/revoke</span>

  - Supported authentication methods: `client_secret_basic`, `client_secret_post`

- Introspection endpoint: <span class="ph filepath">https://`<server_url>`/authorizationserver/oauth/introspect</span>

  - Supported authentication methods: `client_secret_basic`, `client_secret_post`

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_njj_2nx_rbc" class="section">

<div class="section section" type="Supported Flows">

## Supported Flows

The following flows are supported in the new OAuth implementation:

- for confidential clients:

  - authorization code flow

  - authorization code flow + PKCE

  - refresh token flow

  - client credentials flow

  - custom SAML token flow

- for public clients:

  - authorization code flow + PKCE

  - refresh token flow

  - custom SAML token flow

The password flow and implicit flows are not available in the new implementation.

<div id="n2t" class="title">

**Caution**

</div>

Retrieving a token for a public client using the client credentials flow is no longer supported. Consequently, APIs that are secured with any client role will not be accessible to public clients.

To ensure uninterrupted service, configure public APIs to permit consumption without requiring an access token.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_vpl_kqx_rbc" class="section">

<div class="section section" type="Authorization Code Flow">

## Authorization Code Flow

To get an authorization code, use the following request:

``` pre
https://<server_url>/authorizationserver/oauth/authorize?client_id=<client_id>&response_type=code
```

You can add the following additional parameters to an authorization code request:

- <span class="keyword apiname">state</span>: Specifies the parameter that’s responsible for cross-site request forgery prevention

- <span class="keyword apiname">scope</span>: Specifies the scope of an access request

- <span class="keyword apiname">redirect_uri</span>: Specifies the URI to which the authorization server redirects requests after successful authorization

When PKCE is activated, you can include the following parameters:

- `code_challenge`

- `code_challenge_method=S256`

  <div id="n3t" class="title">

  **Note**

  </div>

  The authorization server currently supports the S256 code challenge method only.

<div class="sectiondiv subsection">

### Requesting Access and Refresh Tokens

Use the following to get access or access and refresh tokens:

``` pre
curl -X POST --location "https://<server_url>/authorizationserver/oauth/token" \
-H "Content-Type: application/x-www-form-urlencoded" \
-d 'grant_type=authorization_code&code=<authorization-code>' \
--basic --user <client_id>:<secret>
```

If PKCE is activated, the `code_verifier` parameter is also required.

<div id="n4t" class="title">

**Note**

</div>

In Spring 6, parameters can only be transmitted in <span class="keyword apiname">request-body</span> and must not be included in request URIs. For more information, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdatatracker.ietf.org%2Fdoc%2Fhtml%2Frfc6749%23section-2.3.1" class="extlink" target="_blank" rel="noopener" alt="https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1" title="https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1">Client Password<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

<span class="keyword apiname">OAuthClientDetailsModel</span> now includes a new attribute: `requireAuthorizationConsent`. This Boolean flag determines whether to display a consent screen to users during the authorization request flow. It defaults to `false`, meaning no consent screen is shown unless explicitly activated.

</div>

<div class="sectiondiv subsection">

### Authorization Code Flow + PKCE

The authorization server supports the PKCE flow. In the authorization code flow, using PKCE is obligatory for public clients. It's optional for confidential clients. By default, PKCE is activated for all client, but it can be configured separately for each client using the `requireProofKey` attribute in <span class="keyword apiname">OAuthClientDetailsModel</span>. Setting this attribute to `true` results in making the PKCE flow required for all authorization code requests. Setting it to `false` means that PKCE is optional for a client.

</div>

<div class="sectiondiv subsection">

### Code Verifier and Code Challenge

To use PKCE, you need to generate a code verifier and a code challenge. In the default configuration, the code verifier must be between 43 and 128 characters long. The length of the code verifier can be controlled through the following properties:

<div class="table-wrapper tablenoborder">

| Property | Description | Default Value |
|----|----|----|
| `authserver.authorizationCode.pkce.codeVerifier.length.min` | The minimum length of the code verifier. | `43` |
| `authserver.authorizationCode.pkce.codeVerifier.length.max` | The maximum length of the code verifier. | `128` |

<span class="tablecap"><span class="tablecap">Code Verifier Properties</span></span> {.table summary="" frame="border" border="1" rules="all"}

</div>

The code challenge is generated based on the code verifier through the following algorithm:

``` pre
code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
```

</div>

<div class="sectiondiv subsection">

### Example of Authorization Request to Get Authorization Code

See an example of an authorization request to obtain an authorization code:

``` pre
https://<server_url>/authorizationserver/oauth/authorize?client_id=<client_id>&response_type=code&code_challenge=<code_challenge>&code_challenge_method=S256
```

In the request, specify the following parameters:

- `code_challenge`

- `code_challenge_method=S256`

  <div id="n5t" class="title">

  **Note**

  </div>

  The authorization server currently supports the S256 code challenge method only.

</div>

<div class="sectiondiv subsection">

### Example of Token Request

See an example of a token request:

``` pre
curl -X POST --location "https://<server_url>/authorizationserver/oauth/token" \
-H "Content-Type: application/x-www-form-urlencoded" \
-d 'grant_type=authorization_code&code=<authorization-code>&code_verifier=<verifier>' \
--basic --user <client_id>:<secret>
```

In the request, specify the `code_verifier` parameter. The request returns a token if the `CODE_CHALLENGE` that is used in the authorization request has been created based on the `VERIFIER`.

<div id="n6t" class="title">

**Note**

</div>

In Spring 6, parameters can only be transmitted in <span class="keyword apiname">request-body</span> and must not be included in request URIs. For more information, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdatatracker.ietf.org%2Fdoc%2Fhtml%2Frfc6749%23section-2.3.1" class="extlink" target="_blank" rel="noopener" alt="https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1" title="https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1">https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

</div>

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_pvx_xrx_rbc" class="section">

<div class="section section" type="Refresh Token Flow">

## Refresh Token Flow

To get an access or refresh token in a refresh token flow, use the following request:

``` pre
curl -X POST --location "https://<server_url>/authorizationserver/oauth/token" \
-H "Content-Type: application/x-www-form-urlencoded" \
-d 'grant_type=refresh_token&refresh_token=<refresh-token>' \
--basic --user <client_id>:<secret>
```

<div class="sectiondiv subsection">

### Refresh Token Flow for Public Clients

The support for public clients in the refresh token flow can be activated and deactivated in the same way as for confidential clients - by configuring it for selected clients through the `authorizedGrantTypes` attribute in the <span class="keyword apiname">OAuthClientDetails</span> model.

With public clients, the risk of leaked refresh tokens is greater than with confidential clients. If a refresh token is stolen, an attacker can use it to continuously obtain new access tokens without being detected by the authorization server. To mitigate the risks of using the refresh token flow with public clients, the following properties have been introduced:

<div class="table-wrapper tablenoborder">

<table id="loiod079f886cab647e5a0555e2cae8e4416__table_kyw_5hr_pdc" class="table" data-summary="" data-table-id="table_kyw_5hr_pdc" data-frame="border" data-border="1" data-rules="all">
<caption><span class="tablecap"><span class="tablecap">Public Client Properties</span></span></caption>
<colgroup>
<col style="width: 33%" />
<col style="width: 33%" />
<col style="width: 33%" />
</colgroup>
<thead class="thead" style="text-align:left;">
<tr class="row">
<th id="d132989e1275" class="entry" style="vertical-align: top"><p>Property</p></th>
<th id="d132989e1278" class="entry" style="vertical-align: top"><p>Description</p></th>
<th id="d132989e1281" class="entry" style="vertical-align: top"><p>Default Value</p></th>
</tr>
</thead>
<tbody class="tbody">
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e1275 "><p><samp class="ph codeph">authserver.publicClients.refreshToken.timeToLive.seconds</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e1278 "><p>Sets the maximum lifetime (in seconds) of refresh tokens that are obtained by public clients.</p>
<p>To set a lifetime of refresh tokens for a specific client, use the <samp class="ph codeph">refreshTokenValiditySeconds</samp> attribute in the <span class="keyword apiname">OAuthClientDetails</span> model.</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e1281 "><p><samp class="ph codeph">3600</samp></p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e1275 "><p><samp class="ph codeph">authserver.publicClients.refreshToken.reuse</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e1278 "><p>Specifies whether refresh tokens are reused on every use of the refresh token flow. If set to <samp class="ph codeph">false</samp>, new refresh tokens are issued for public clients. Reusing refresh tokens on every flow reuse is part of the default behavior in OAuth.</p></td>
<td class="entry" style="vertical-align: top" headers="d132989e1281 "><p><samp class="ph codeph">false</samp></p></td>
</tr>
<tr class="row">
<td class="entry" style="vertical-align: top" headers="d132989e1275 "><p><samp class="ph codeph">authserver.publicClients.rotated.refreshToken.limit.timeToLive</samp></p></td>
<td class="entry" style="vertical-align: top" headers="d132989e1278 "><p>Sets the TTL (time-to-live) for refresh tokens.</p>
<p>If reusing refresh tokens for public clients is deactivated through the <samp class="ph codeph">authserver.publicClients.refreshToken.reuse=false</samp> property, the default lifetime of new refresh tokens is limited to the lifetime of the initial refresh tokens that are initially obtained through the authorization code flow. For example, the refresh token lifetime is one hour and the access token lifetime is 15 minutes:</p>
<div class="p">
<ol>
<li><p>An access and refresh tokens are issued for a public client.</p></li>
<li><p>After 15 minutes, the refresh token is used to get a new access token.</p></li>
<li><p>The refresh token that is returned is now valid for 45 minutes.</p></li>
<li><p>This continues until an hour passes from the moment the initial refresh token was issued.</p></li>
<li><p>At this point, the refresh token is no longer valid and the user has to authenticate again through the authorization code flow.</p></li>
</ol>
</div></td>
<td class="entry" style="vertical-align: top" headers="d132989e1281 "><p><samp class="ph codeph">true</samp></p></td>
</tr>
</tbody>
</table>

</div>

</div>

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_afy_vby_rbc" class="section">

<div class="section section" type="Client Credentials Flow">

## Client Credentials Flow

To get an access token in a client credentials flow, use the following request:

``` pre
curl -X POST --location "https://<server_url>/authorizationserver/oauth/token" \
-H "Content-Type: application/x-www-form-urlencoded" \
-d 'grant_type=client_credentials' \
--basic --user <client_id>:<secret>
```

You can add the following additional parameter to the authorization code request:

- <span class="keyword apiname">scope</span>: Specifies the parameter that’s responsible for cross-site request forgery prevention

<div id="n7t" class="title">

**Note**

</div>

In Spring 6, parameters can only be transmitted in <span class="keyword apiname">request-body</span> and must not be included in request URIs. For more information, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdatatracker.ietf.org%2Fdoc%2Fhtml%2Frfc6749%23section-2.3.1" class="extlink" target="_blank" rel="noopener" alt="https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1" title="https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1">https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_mss_mxc_mfc" class="section">

<div class="section section" type="Custom SAML Token Flow">

## Custom SAML Token Flow

SAML token flow is a custom grant flow that allows you to obtain access token using the login token issued by the <span class="keyword apiname yext">samlsinglesignon</span> extension during the SSO (Single Sign-On) authentication. The flow can be configured for both public and confidential clients.

To enable the custom SAML token flow, ensure the following:

- You have included the <span class="keyword apiname yext">samlsinglesignon</span> extension in your configuration.

- Your OAuth clients must have the SAML token flow listed as a supported flow in the `authorizedGrantTypes` attribute

The custom SAML token flow supports the following tokens:

- access tokens

- refresh tokens - only when the relevant client has the `refresh_token` listed as a supported flow

To request tokens in this flow, use the following endpoint:

``` pre
https://<server_url>/authorizationserver/oauth/token
```

The following parameters are required in requests:

- `grant_type=saml_token`

- `client_id=``<client_id>`

- `client_secret=``<client_secret>` - required only for confidential clients

The following parameters are optional:

- `scope` - with space-separated values

The token requests must include the SAML login token with the name matching the `sso.cookie.name` property.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_jxp_xxf_x2c" class="section">

<div class="section section" type="Custom Token Granter">

## Custom Token Granter

Previously, implementing a custom token granter was achieved by implementing a <span class="keyword apiname">TokenGranter</span> interface. In the new authorization server, you can configuring it by following the steps:

1.  Implement the <span class="keyword apiname">CustomAuthenticationConverter</span> interface by creating a new class. This implementation must return an instance of <span class="keyword apiname">AbstractCustomAuthenticationToken</span>. Use <span class="keyword apiname">CustomUserAuthenticationToken</span> for flows that require both users and clients, such as the authorization code flow. For flows that require clients only, such as the client credentials flow, use <span class="keyword apiname">CustomClientAuthenticationToken</span>. Authentication providers then use these authentication tokens to generate the necessary OAuth tokens.

    - To ensure that access tokens include the desired scopes, use the constructor of the <span class="keyword apiname">AbstractCustomAuthenticationToken</span> implementation that accepts the <span class="keyword apiname">scopes</span> parameter. The authorization server later issues a JWT access token that contains the specified scopes in the <span class="keyword apiname">scope</span> claim.

    - For flows that require both users and client: To enable refresh tokens, set the `generateRefreshToken` property to `true` when creating the <span class="keyword apiname">CustomUserAuthenticationToken</span> object. Note that to receive refresh tokens, your OAuth clients must have the refresh token flow listed as a supported flow.

    ``` pre
    public class ExampleUserAuthenticationConverter implements CustomAuthenticationConverter
    {
        private static final AuthorizationGrantType EXAMPLE_AUTHORIZATION_GRANT_TYPE = new AuthorizationGrantType("example");
        private final UserDetailsService userDetailsService;
     
        public ExampleUserAuthenticationConverter(final UserDetailsService userDetailsService)
        {
            this.userDetailsService = userDetailsService;
        }
     
        @Override
        public CustomUserAuthenticationToken convert(final HttpServletRequest request)
        {
            final String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
            if (!EXAMPLE_AUTHORIZATION_GRANT_TYPE.getValue().equals(grantType))
            {
                return null;
            }
            final String userId = request.getParameter("userId");
     
            final OAuth2ClientAuthenticationToken oAuth2ClientAuthenticationToken = (OAuth2ClientAuthenticationToken) SecurityContextHolder.getContext()
                                                                                                                                           .getAuthentication();
            final UserDetails loadedUser = userDetailsService.loadUserByUsername(userId);
     
            final Authentication userAuth = new UsernamePasswordAuthenticationToken(userId, null, loadedUser.getAuthorities());
     
            if (someCondition())
            {
                return new CustomUserAuthenticationToken(userAuth, oAuth2ClientAuthenticationToken, EXAMPLE_AUTHORIZATION_GRANT_TYPE);
            }
            else
            {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
            }
        }
     
        private boolean someCondition()
        {
            return true;
        }
     
    }
    ```

2.  Register your authentication converter as a Spring bean to ensure that it's recognized by the authorization server:

    ``` pre
    <bean id="exampleUserAuthenticationConverter" class="com.sap.cx.commerce.platform.oauth2.authorizationserver.spring.ExampleUserAuthenticationConverter" />
    ```

3.  Ensure that the <span class="keyword apiname">OAuthClientDetails</span> object includes your new grant type in the `authorizedGrantTypes` attribute.

4.  After registering your bean, create requests using it:

    ``` pre
    curl --request POST \
      --url https://<server_url>/authorizationserver/oauth/token \
      --header 'Content-Type: application/x-www-form-urlencoded' \
      --data client_id=mobile_android \
      --data client_secret=<client_secret> \
      --data grant_type=<grant_type> \
      --data userId=admin
    ```

5.  Optionally, if your custom grant flow requires customizations of client authentication, you can create your own <span class="keyword apiname">AuthenticationConverter</span> and <span class="keyword apiname">AuthenticationProvider</span> by implementing <span class="keyword apiname">CustomAuthenticationPurposeConverter</span> and <span class="keyword apiname">CustomAuthenticationPurposeProvider</span> and declaring the necessary beans in your Spring configuration file. The class that implements <span class="keyword apiname">CustomAuthenticationPurposeConverter</span> is used to validate token requests and the relevant authentication method. The class that implements <span class="keyword apiname">CustomAuthenticationPurposeProvider</span> is used to authenticate OAuth clients.

    ``` pre
    public class ExampleAuthenticationConverter implements CustomAuthenticationPurposeConverter
    {
        private static final AuthorizationGrantType EXAMPLE_AUTHORIZATION_GRANT_TYPE = new AuthorizationGrantType("example");
     
        @Override
        public Authentication convert(final HttpServletRequest request)
        {
            final String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
     
            if (!isExampleGrantType(request) || !someCondition(request))
            {
                return null;
            }
             
            final ClientAuthenticationMethod exampleAuthenticationMethod = new ClientAuthenticationMethod("example");
            final String credential = request.getParameter(OAuth2ParameterNames.CLIENT_SECRET);
     
            return new OAuth2ClientAuthenticationToken(clientId, exampleAuthenticationMethod, credential, null);
        }
     
        private boolean isExampleGrantType(final HttpServletRequest request)
        {
            final String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
     
            return EXAMPLE_AUTHORIZATION_GRANT_TYPE.getValue().equals(grantType);
        }
         
        private boolean someCondition(final HttpServletRequest request)
        {
            return true;
        }
    }
    ```

    ``` pre
    public class ExampleAuthenticationProvider implements CustomAuthenticationPurposeProvider
    {
        private static final ClientAuthenticationMethod EXAMPLE_AUTHENTICATION_METHOD = new ClientAuthenticationMethod("example");
        private final RegisteredClientRepository registeredClientRepository;
     
        public ExampleAuthenticationProvider(final RegisteredClientRepository registeredClientRepository)
        {
            this.registeredClientRepository = registeredClientRepository;
        }
     
        @Override
        public Authentication authenticate(final Authentication authentication) throws AuthenticationException
        {
            final OAuth2ClientAuthenticationToken clientAuthentication = (OAuth2ClientAuthenticationToken) authentication;
     
            if (!EXAMPLE_AUTHENTICATION_METHOD.equals(clientAuthentication.getClientAuthenticationMethod() || !someCondition(authentication))
            {
                return null;
            }
     
            final String clientId = clientAuthentication.getPrincipal().toString();
            final RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(clientId);
     
            validateRegisteredClient(registeredClient);
     
            return new OAuth2ClientAuthenticationToken(registeredClient,clientAuthentication.getClientAuthenticationMethod(), clientAuthentication.getCredentials());
        }
     
        @Override
        public boolean supports(final Class<?> authentication)
        {
            return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
        }
     
        private boolean someCondition(final Authentication authentication)
        {
            return true;
        }
     
        private void validateRegisteredClient(final RegisteredClient registeredClient)
        {
            // method implementation
        } 
    }
    ```

    ``` pre
    <bean id="exampleAuthenticationConverter" class="com.sap.cx.commerce.foo.bar.ExampleAuthenticationConverter"/>
    <bean id="exampleAuthenticationProvider" class="com.sap.cx.commerce.foo.bar.ExampleAuthenticationProvider"/>
    ```

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_xkk_z2y_rbc" class="section">

<div class="section section" type="Token Introspection">

## Token Introspection

To introspect access tokens, use the following request:

``` pre
curl -X POST --location "https://<server_url>/authorizationserver/oauth/introspect" \
-H "Content-Type: application/x-www-form-urlencoded" \
-d 'token=<token>' \
--basic --user <client_id>:<secret>
```

<div id="n8t" class="title">

**Note**

</div>

In Spring 6, parameters can only be transmitted in <span class="keyword apiname">request-body</span> and must not be included in request URIs. For more information, see <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdatatracker.ietf.org%2Fdoc%2Fhtml%2Frfc6749%23section-2.3.1" class="extlink" target="_blank" rel="noopener" alt="https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1" title="https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1">Client Password<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_esn_fjy_rbc" class="section">

<div class="section section" type="Accessing Resources with Access Tokens">

## Accessing Resources with Access Tokens

To access secured resources, use requests with an HTTP Authorization header containing an access token:

``` pre
Authorization: Bearer <access-token>
```

<div class="sectiondiv subsection">

### User Roles

For authorization code flow, the authorization server does not include information about user roles in the JWT access tokens. Instead, roles are retrieved from the database when the token is presented to the resource server, based on the currently signed-in user's assigned groups.

</div>

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_scb_x1g_x2c" class="section">

<div class="section section" type="Token Revocation">

## Token Revocation

In the new authorization server, a custom revocation system has been created for access token revocation. Since access tokens aren't meant to be persisted, an in-memory revocation list has been introduced that is designed to work quickly and be safe for short-lived tokens. Each time an access token is revoked, it's added to the revocation list that is checked against by the system during each request to the resource server.

The revocation list is applied in the following cases:

- Password change

- Creation of a new access token with a refresh token with the old access token being revoked when `authserver.refreshToken.reuse` and `authserver.publicClients.refreshToken.reuse` are set to `false`

- Revocation by the <span class="ph filepath">/revoke</span> endpoint

- Removal of authorization from the database

- Deserialization failure when fetching authorization from the database

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_hcy_n3g_x2c" class="section">

<div class="section section" type="OpenID">

## OpenID

<span class="ph pname" translate="no">SAP Commerce Cloud</span> supports the OpenID Connect (OIDC) protocol as an OIDC provider. It can now only be used with the authorization code flow since the implicit flow is deprecated.

For more information on how to create OIDC clients, see <a href="dd2b99ce92ea48088e66ec6cb115f063.html" class="xref" title="OpenID Connect eliminates the need for developers to manage or store passwords. It enables user authentication for client applications with the Platform acting as the identity provider.">Platform as an OpenId Connect Identity Provider</a>.

When configuring OIDC, there's no need to create <span class="keyword apiname">OpenIDClientDetails</span> instances as creating <span class="keyword apiname">OAuthClientDetails</span> is sufficient. You only need to add the `openid` scope to <span class="keyword apiname">OAuthClientDetails</span>.

The use of <span class="keyword apiname">ExternalScopes</span> is no longer supported.

Defining separate keystores for each client is no longer supported.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_cjn_3tg_x2c" class="section">

<div class="section section" type="Keystore Configuration">

## Keystore Configuration

Keys used for signing JWTs and verifying signatures are stored in a keystore and accessible through a JWK endpoint. <span class="ph">You do not need to create or manage a keystore. <span class="ph pname" translate="no">SAP Commerce Cloud</span> automatically generates and rotates these private keys every sixty days to maintain compliance with SAP security policy.</span>

<div id="n9t" class="title">

**Note**

</div>

The TTL of the JWTs you generate must not exceed sixty days. If a token is older than sixty days, the server refuses to validate it. According to security best practices, the TTL of a JWT should not exceed a few minutes.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_hyw_mv2_sbc" class="section">

<div class="section section" type="Preventing Brute-Force Attacks">

## Preventing Brute-Force Attacks

To prevent brute-force attacks, use the following property to set the maximum number of failed client authentication attempts:

``` pre
oauth2.maxAuthenticationAttempts
```

<div class="sectiondiv subsection">

### Revoking OAuth Tokens on User Disabling

When a user account becomes disabled by the brute force attack protection mechanism or deleted from the system, all the OAuth tokens previously issued to that user are revoked and deleted from the database. Previous access tokens and refresh tokens become invalid and can't be used anymore. Before getting access tokens for disabled user accounts, re-enable the accounts first.

Any attempt to use a revoked refresh token of a disabled user results in the following error response:

``` pre
{"error":"invalid_grant"}
```

When introspecting access and refresh tokens of disabled users, the OAuth authorization server notifies the client that these tokens are inactive.

</div>

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_wrh_gtn_x2c" class="section">

<div class="section section" type="Changes in Scope Handling">

## Changes in Scope Handling

In the new OAuth implementation, scopes are handled differently in requests. The authorization server no longer automatically applies predefined scopes when the <span class="keyword apiname">scope</span> parameter is omitted from requests. Consequently, access tokens issued as a response don't include any scope-related claims.

If necessary, update your authorization requests in the authorization code flow and token requests in the client credentials flow to explicitly include any necessary scopes.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_ffd_zwn_x2c" class="section">

<div class="section section" type="Customizing JWT Access Token">

## Customizing JWT Access Token

To customize JWT access tokens to ensure that they contain custom claims, implement a customizer:

1.  Create a new class that implements the <span class="keyword apiname">ExtensibleOAuth2TokenCustomizer</span> interface:

    ``` pre
    com.sap.cx.commerce.platform.oauth2.authorizationserver.custom.ExtensibleOAuth2TokenCustomizer
    ```

2.  Optionally, you can override the <span class="keyword apiname">Set\<AuthorizationGrantType\> getSupportedAuthGrantTypes()</span> method and return the set of supported authorization grant types. This ensures that the customizer is only used for the specified grant types. For example, if you have created a new custom grant type, you can return a set containing only that new grant type in the <span class="keyword apiname">getSupportedAuthGrantTypes()</span> method. This way, the customizer is used only when the authorization server is processing the custom grant type, and not for other standard grant types. If you don't override this method and leave it returning an empty set, the customizer is used for every authorization grant type processed by the authorization server.

3.  Create your custom claims by inserting your custom logic in to the <span class="keyword apiname">customize</span> method:

    ``` pre
    @Override
    public void customize(final JwtEncodingContext context) {
        context.getClaims().claims((claims) -> {
            claims.put("claim-1", "value-1");
        });
    }
    ```

4.  Register your customizer as a bean in your application context:

    ``` pre
    <bean id="firstCustomizer" class="package.YourJWTCustomizer"/>
    ```

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_xgv_qsd_gfc" class="section">

<div class="section section" type="Custom Login Pages in Authorization Code Flow">

## Custom Login Pages in Authorization Code Flow

You can implement a custom login page for your storefront in the authorization code flow. For more information, see <a href="ca1176a372b242a6abd75a39fe803eea.html#loioca1176a372b242a6abd75a39fe803eea" class="xref" title="Configure custom login pages for your client applications in the authorization code flow to enable personalized user experiences, enhance security, and boost client satisfaction.">Custom Login Page</a>.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_yq1_yvn_nfc" class="section">

<div class="section section" type="Verification Process for Existing Clients">

## Verification Process for Existing Clients

After upgrading to the new OAuth implementation, use <span class="ph" translate="no">Backoffice</span> to review your existing clients. There have been changes to the prerequisites for adding and saving clients, as described in the <span class="q">“Client Validation”</span> section, that could affect your configuration.

After the upgrade, new <span class="keyword apiname">OAuthClientDetails</span> attributes are available in client configuration in <span class="ph" translate="no">Backoffice</span>. Some of these attributes may not be explicitly set and now have default values:

- <span class="ph uicontrol">Require PKCE Proof Key</span>: If this attribute isn't explicitly set, it's activated by default. This means you need to adjust authorization code flow requests for the authorization code flow to include PKCE. If you don't make this adjustment, you may encounter errors during the authorization code flow requests similar to the following:

  ``` pre
  error=invalid_request&error_description=OAuth 2.0 Parameter: code_challenge
  ```

  You can also consider deactivating this attribute for confidential clients if you don't want to include PKCE in your flow.

- <span class="ph uicontrol">OAuth require authorization consent</span>: If this attribute isn't explicitly set, it's deactivated by default.

- <span class="ph uicontrol">Public client</span>: This attribute is automatically set after the upgrade based on whether a client secret is present. If no secret is present during the upgrade process, this attribute is set to `true`.

For more information on the new attributes, see the <span class="q">“Configuring OAuth Clients”</span> section.

The following attributes that were previously optional are now required:

- <span class="ph uicontrol">OAuth authorities</span>: At least once authority is required.

- <span class="ph uicontrol">OAuth authorized grant types</span>: At least one authorized grant type is required.

Additionally, review and adjust token validity settings as default values have been changed. If you don't modify them, the default values are used.

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_w53_m2s_pfc" class="section">

<div class="section section" type="Orphaned Types Cleanup">

## Orphaned Types Cleanup

After the upgrade, you can clean up outdated or orphaned types that are related to the deprecated <span class="keyword apiname yext">oauth2</span> extension by navigating to <span class="ph menucascade">![Start of the navigation path](themes/sap-light/img/navstart.gif "Start of the navigation path")<span class="ph uicontrol">Maintenance</span> ![Next navigation step](themes/sap-light/img/navstep.gif "Next navigation step") <span class="ph uicontrol">Cleanup</span> ![Next navigation step](themes/sap-light/img/navstep.gif "Next navigation step") <span class="ph uicontrol">Type System</span>![End of the navigation path](themes/sap-light/img/navend.gif "End of the navigation path")</span> in <span class="ph" translate="no">SAP Commerce <span class="ph">Cloud </span>Administration Console</span> and performing the cleanup procedure for the following types:

- <span class="keyword apiname">OAuthAccessToken</span>

- <span class="keyword apiname">OAuthAuthorizationCode</span>

- <span class="keyword apiname">OAuthRefreshToken</span>

- <span class="keyword apiname">User2TokenRelation</span>

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_qpd_syv_tfc" class="section">

<div class="section section" type="Punchout Token for Punchout Flow">

## Punchout Token for Punchout Flow

To generate an oauth token for the punchout flow, we've added a new grant type:<span class="ph uicontrol"> punchout_token</span>. This grant type retrieves the token needed to call commerce. It is a non-public client, so the <span class="ph uicontrol">clientSecret</span> field is now required.

<div class="p">

You can customize the <span class="keyword parmname">clientId</span> and <span class="keyword parmname">clientSecret</span> by setting value for these properties:

- <span class="ph uicontrol">b2bpunchout.oauthclient.clientId=punchout_client</span>

- <span class="ph uicontrol">b2bpunchout.oauthclient.clientSecret=secret</span>

</div>

<div class="p">

Here is a sample impex to add the new grant type:

``` pre
INSERT_UPDATE OAuthClientDetails;clientId[unique=true]   ;resourceIds   ;scope      ;authorizedGrantTypes           ;authorities                            ;clientSecret    ;registeredRedirectUri
                                ;punchout_client         ;hybris        ;basic      ;punchout_token                 ;ROLE_CLIENT                            ;secret          ;
```

</div>

<div class="p">

By default, punchout calls the commerce OAuth token URL to obtain a new token. If you have your own OAuth token URL, update these properties:

- <span class="ph uicontrol">b2bpunchout.oauth.host=https://localhost:9002</span>

- <span class="ph uicontrol">b2bpunchout.oauth.tokenUrl=/authorizationserver/oauth/token</span>

</div>

To securely call the OAuth token URL, use a keystore file to generate the secure client.

You can also ignore host SSL verification by updating this property to <span class="ph uicontrol">true</span>. By default, it is <span class="ph uicontrol">false</span>: <span class="ph uicontrol">b2bpunchout.oauth2.keyselector.http.ssl.ignoreHostnameVerification=false</span>

<div class="p">

Additionally, configure the keystore information:

- <span class="ph uicontrol">b2bpunchout.keystore.path=your.keystore.path</span>

- <span class="ph uicontrol">b2bpunchout.keystore.password=your.keystore.password</span>

- <span class="ph uicontrol">b2bpunchout.keystore.type=your.keystore.type</span>

</div>

</div>

</div>

<div id="loiod079f886cab647e5a0555e2cae8e4416__section_bcs_l1x_tfc" class="section">

<div class="section section" type="Breaking Changes in SmartEdit">

## Breaking Changes in <span class="ph" translate="no">SmartEdit</span>

The deprecated Spring Security OAuth library has been replaced by Spring Security, resulting in breaking changes to authentication in the <span class="ph" translate="no">SmartEdit</span> web application.

If you encounter any errors while building the front-end code for customized <span class="ph" translate="no">SmartEdit</span> extensions, update this code as the followings.

<div id="n10t" class="title">

**Note**

</div>

The update is not necessary if you do not have customized <span class="ph" translate="no">SmartEdit</span> extensions or encounter no errors while building the <span class="ph" translate="no">SmartEdit</span> front-end code.

- Breaking changes in `smartedit-commons`, including `smart-utils`:

  <div class="p">

  The followings are the affected constants, interfaces, and classes in the `smart-utils` and `smartedit-comons` applications:
  - LoginDialogComponent

  - IAuthenticationService

  - IReAuthInProgress

  - ILoginData

  - AuthenticationService

  - ICredentialsMap

  - IAuthMap

  - HttpAuthInterceptor

  - I18N_ROOT_RESOURCE_URI

  - DEFAULT_AUTH_MAP

  - DEFAULT_CREDENTIALS_MAP

  - WHO_AM_I_RESOURCE_URI_TOKEN

  - WHO_AM_I_RESOURCE_URI

  </div>

  <div class="p">

  The following table lists the detailed breaking changes from `smart-utils` and `smartedit-commons`.
  <div class="table-wrapper tablenoborder">

  <table class="table" data-summary="" data-frame="border" data-border="1" data-rules="all">
  <colgroup>
  <col style="width: 33%" />
  <col style="width: 33%" />
  <col style="width: 33%" />
  </colgroup>
  <thead class="thead" style="text-align:left;">
  <tr class="row">
  <th id="d132989e2245" class="entry" style="vertical-align: top"><p>File</p></th>
  <th id="d132989e2248" class="entry" style="vertical-align: top"><p>Changed</p></th>
  <th id="d132989e2251" class="entry" style="vertical-align: top"><p>Removed</p></th>
  </tr>
  </thead>
  <tbody class="tbody">
  <tr class="row">
  <td class="entry" style="vertical-align: top" headers="d132989e2245 "><p><span class="ph filepath">smart-utils/src/components/login-dialog/login-dialog.component.ts</span></p></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2248 "><p>The constructor signature of class <samp class="ph codeph">LoginDialogComponent</samp> has been changed to:</p>
  <div class="p">
  <pre id="loiod079f886cab647e5a0555e2cae8e4416__codeblock_jgy_r4y_nfc" class="pre codeblock prettyprint"><code>constructor(
                                                              private readonly modalRef: DialogRef,
                                                              private readonly logService: LogService,
                                                              private readonly sessionService: ISessionService,
                                                              private readonly storageService: IStorageService,
                                                              private readonly authenticationService: IAuthenticationService,
                                                              private readonly ssoAuthenticationHelper: SSOAuthenticationHelper,
                                                              private readonly platformAuthenticationHelper: PlatformAuthenticationHelper,
                                                              @Optional() @Inject(LoginDialogResourceProvider) public resource: LoginDialogResource,
                                                              @Inject(EVENT_SERVICE) protected eventService: IEventService,
                                                              private readonly windowUtils: WindowUtils
                                                              )</code></pre>
  </div></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2251 "><p>The removed public methods in class <samp class="ph codeph">LoginDialogComponent</samp>:</p>
  <ul>
  <li><p>signinWithCredentials</p></li>
  <li><p>openBackofficeWindow</p></li>
  </ul></td>
  </tr>
  <tr class="row">
  <td class="entry" style="vertical-align: top" headers="d132989e2245 "><p><span class="ph filepath">smart-utils/src/interfaces/i-authentication-service.ts</span></p></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2248 "><p>The changed public methods in class <samp class="ph codeph">IAuthenticationService</samp>:</p>
  <ul>
  <li><p>From <samp class="ph codeph">reauthInProgress: IReAuthInProgress</samp> to <samp class="ph codeph">reauthInProgress: boolean</samp>.</p></li>
  <li><p>From <samp class="ph codeph">authenticate(resource: string): Promise&lt;void&gt;</samp> to <samp class="ph codeph">authenticate(): Promise&lt;void&gt;</samp>.</p></li>
  <li><p>From <samp class="ph codeph">isReAuthInProgress(entryPoint: string): Promise&lt;boolean&gt;</samp> to <samp class="ph codeph">isReAuthInProgress(): Promise&lt;boolean&gt;</samp>.</p></li>
  <li><p>From <samp class="ph codeph">setReAuthInProgress(entryPoint: string): Promise&lt;void&gt;</samp> to <samp class="ph codeph">setReAuthInProgress(): Promise&lt;void&gt;</samp>.</p></li>
  <li><p>From <samp class="ph codeph">isAuthenticated(url: string): Promise&lt;boolean&gt;</samp> to <samp class="ph codeph">isAuthenticated(): Promise&lt;boolean&gt;</samp>.</p></li>
  </ul></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2251 "><ul>
  <li><p>The removed public method in class <samp class="ph codeph">IAuthenticationService</samp>: <samp class="ph codeph">filterEntryPoints(resource: string): Promise&lt;string[]&gt;</samp></p></li>
  <li><pre id="loiod079f886cab647e5a0555e2cae8e4416__codeblock_oxs_2qy_nfc" class="pre codeblock prettyprint"><code>interface IReAuthInProgress {
                                                                  [endPoint: string]: boolean;</code></pre></li>
  </ul></td>
  </tr>
  <tr class="row">
  <td class="entry" style="vertical-align: top" headers="d132989e2245 "><p><span class="ph filepath">smart-utils/src/interfaces/i-login-data.ts</span></p></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2248 "> </td>
  <td class="entry" style="vertical-align: top" headers="d132989e2251 "><pre id="loiod079f886cab647e5a0555e2cae8e4416__codeblock_oj1_gqy_nfc" class="pre codeblock prettyprint"><code>interface ILoginData {
                                                          clientCredentials: ICredentialsMapRecord;
                                                          }</code></pre></td>
  </tr>
  <tr class="row">
  <td class="entry" style="vertical-align: top" headers="d132989e2245 "><p><span class="ph filepath">smart-utils/src/services/authentication/authentication.service.ts</span></p></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2248 "><p>The changed public methods in class <samp class="ph codeph">AuthenticationService</samp>:</p>
  <ul>
  <li><p>From <samp class="ph codeph">authenticate(resource: string): Promise&lt;void&gt;</samp> to <samp class="ph codeph">authenticate(): Promise&lt;void&gt;</samp>.</p></li>
  <li><p>From <samp class="ph codeph"> isReAuthInProgress(entryPoint: string): Promise&lt;boolean&gt;</samp> to <samp class="ph codeph">isReAuthInProgress(): Promise&lt;boolean&gt;</samp>.</p></li>
  <li><p>From <samp class="ph codeph">setReAuthInProgress(entryPoint: string): Promise&lt;void&gt; </samp> to <samp class="ph codeph">setReAuthInProgress(): Promise&lt;void&gt;</samp>.</p></li>
  <li><p>From <samp class="ph codeph">isAuthenticated(url: string): Promise&lt;boolean&gt;</samp> to <samp class="ph codeph">isAuthenticated(): Promise&lt;boolean&gt;</samp>.</p></li>
  </ul></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2251 "><ul>
  <li><p>The removed public and protected methods in class <samp class="ph codeph">AuthenticationService</samp>:</p>
  <ul>
  <li><p><samp class="ph codeph">filterEntryPoints(resource: string): Promise&lt;string[]&gt;</samp></p></li>
  <li><p><samp class="ph codeph">protected _findLoginData(resource: string): Promise</samp></p></li>
  </ul></li>
  <li><pre id="loiod079f886cab647e5a0555e2cae8e4416__codeblock_zhp_hry_nfc" class="pre codeblock prettyprint"><code>interface ICredentialsMap {
                                                                  [entryPoint: string]: ICredentialsMapRecord;
                                                                  }</code></pre></li>
  <li><pre id="loiod079f886cab647e5a0555e2cae8e4416__codeblock_yhm_3ry_nfc" class="pre codeblock prettyprint"><code>interface IAuthMap {
                                                                  [entryPoint: string]: string;
                                                                  } </code></pre></li>
  </ul></td>
  </tr>
  <tr class="row">
  <td class="entry" style="vertical-align: top" headers="d132989e2245 "><p><span class="ph filepath">smart-utils/src/services/interceptors/http-auth.interceptor.ts</span></p></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2248 "><p>The constructor signature of class <samp class="ph codeph">HttpAuthInterceptor</samp> has been changed to:</p>
  <div class="p">
  <pre id="loiod079f886cab647e5a0555e2cae8e4416__codeblock_hbp_xqy_nfc" class="pre codeblock prettyprint"><code>constructor(
                                                              private injector: Injector,
                                                              private httpUtils: HttpUtils,
                                                              @Inject(I18N_RESOURCE_URI_TOKEN) private I18N_RESOURCE_URI: string
                                                              )</code></pre>
  </div></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2251 "> </td>
  </tr>
  <tr class="row">
  <td class="entry" style="vertical-align: top" headers="d132989e2245 "><p><span class="ph filepath">smart-utils/src/constants.ts</span></p></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2248 "> </td>
  <td class="entry" style="vertical-align: top" headers="d132989e2251 "><pre id="loiod079f886cab647e5a0555e2cae8e4416__codeblock_v55_lry_nfc" class="pre codeblock prettyprint"><code>export const I18N_ROOT_RESOURCE_URI = &#39;/smarteditwebservices/v1/i18n&#39;;
                                                          &#10;                                                        export const DEFAULT_AUTH_MAP = {
                                                          [&#39;^(?!&#39; + I18N_ROOT_RESOURCE_URI + &#39;/.*$).*$&#39;]: DEFAULT_AUTHENTICATION_ENTRY_POINT
                                                          };
                                                          &#10;                                                        export const DEFAULT_CREDENTIALS_MAP = {
                                                          [DEFAULT_AUTHENTICATION_ENTRY_POINT]: {
                                                          client_id: DEFAULT_AUTHENTICATION_CLIENT_ID
                                                          }
                                                          };</code></pre>
  <pre class="pre codeblock prettyprint"><code>WHO_AM_I_RESOURCE_URI_TOKEN</code></pre>
  <pre class="pre codeblock prettyprint"><code>WHO_AM_I_RESOURCE_URI</code></pre></td>
  </tr>
  </tbody>
  </table>

  </div>

  </div>

- Breaking changes in `smartedit-container`：

  <div class="p">

  The followings are the affected constants, interfaces, and classes in the `smartedit-container` application:
  - ConfigurationExtractorService

  - BootstrapService

  </div>

  <div class="p">

  The following table lists the detailed breaking changes from `smartedit-container`.
  <div class="table-wrapper tablenoborder">

  <table class="table" data-summary="" data-frame="border" data-border="1" data-rules="all">
  <colgroup>
  <col style="width: 33%" />
  <col style="width: 33%" />
  <col style="width: 33%" />
  </colgroup>
  <thead class="thead" style="text-align:left;">
  <tr class="row">
  <th id="d132989e2552" class="entry" style="vertical-align: top"><p>File</p></th>
  <th id="d132989e2555" class="entry" style="vertical-align: top"><p>Changed</p></th>
  <th id="d132989e2558" class="entry" style="vertical-align: top"><p>Removed</p></th>
  </tr>
  </thead>
  <tbody class="tbody">
  <tr class="row">
  <td class="entry" style="vertical-align: top" headers="d132989e2552 "><p><span class="ph filepath">smartedit-container/src/services/bootstrap/BootstrapServices.ts</span></p></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2555 "><p>The constructor signature of class <samp class="ph codeph">BootstrapService</samp> has been changed to:</p>
  <div class="p">
  <pre id="loiod079f886cab647e5a0555e2cae8e4416__codeblock_pkw_nwy_nfc" class="pre codeblock prettyprint"><code>constructor(
                                                          private sharedDataService: ISharedDataService,
                                                          private logService: LogService,
                                                          private httpClient: HttpClient,
                                                          private promiseUtils: PromiseUtils,
                                                          private smarteditBootstrapGateway: SmarteditBootstrapGateway,
                                                          private moduleUtils: ModuleUtils,
                                                          private alertService: IAlertService,
                                                          private translate: TranslateService,
                                                          @Inject(&#39;SMARTEDIT_INNER_FILES&#39;) private SMARTEDIT_INNER_FILES: string[],
                                                          @Inject(&#39;SMARTEDIT_INNER_FILES_POST&#39;) private SMARTEDIT_INNER_FILES_POST: string[]
                                                          ) {}</code></pre>
  </div></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2558 "> </td>
  </tr>
  <tr class="row">
  <td class="entry" style="vertical-align: top" headers="d132989e2552 "><p><span class="ph filepath">smartedit-container/src/services/bootstrap/ConfigurationExtractorService.ts</span></p></td>
  <td class="entry" style="vertical-align: top" headers="d132989e2555 "> </td>
  <td class="entry" style="vertical-align: top" headers="d132989e2558 "><p>The removed class： <samp class="ph codeph">ConfigurationExtractorService</samp></p></td>
  </tr>
  </tbody>
  </table>

  </div>

  </div>

</div>

</div>

</div>

<div class="related-links">

<div class="relinfo">

<div class="relinfotitle">

Related Information

</div>

<div>

<a href="/docs/link-disclaimer?site=https%3A%2F%2Fdatatracker.ietf.org%2Fdoc%2Fhtml%2Frfc7636" class="extlink" target="_blank" rel="noopener" alt="https://datatracker.ietf.org/doc/html/rfc7636" title="https://datatracker.ietf.org/doc/html/rfc7636">https://datatracker.ietf.org/doc/html/rfc7636<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>

</div>

<div>

<a href="81dd1f27a1044dd49257546cae590e51.html" class="link" title="OAuth is the default authorization protocol in SAP Commerce Cloud that allows third-party applications to access user data without exposing login credentials. It enhances security and user experience by enabling seamless integration between different services.">OAuth Support (JDK 21)</a>

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
