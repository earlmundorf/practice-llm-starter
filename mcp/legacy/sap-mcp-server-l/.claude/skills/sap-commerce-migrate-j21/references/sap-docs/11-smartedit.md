---
source_topic: SMART_EDIT
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/a32fa4b86acf4c899ce3149f4679ac95.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: a32fa4b86acf4c899ce3149f4679ac95
sap_version: v2211
fetched: 2026-04-30
title: "Updating oAuthClient for SmartEdit"
---

> Mirror of [Updating oAuthClient for SmartEdit](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/a32fa4b86acf4c899ce3149f4679ac95.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loioa32fa4b86acf4c899ce3149f4679ac95" class="page task - topic-topic task-task">

# Updating oAuthClient for <span class="ph" translate="no">SmartEdit</span>

<div class="body taskbody">

To ensure <span class="ph" translate="no">SmartEdit</span> works correctly, you need to update oAuthClient.

<div class="section section procedure">

<span id="steps" class="anchor"></span>

<div class="ol steps tasklabel">

## Procedure

</div>

<div class="li step p">

<span class="ph cmd">Update oAuthClient with the following impex:</span>

<div class="itemgroup info">

``` pre
INSERT_UPDATE OAuthClientDetails;clientId[unique=true];resourceIds;scope[mode=append];authorizedGrantTypes;accessTokenValiditySeconds;authorities;registeredredirecturi;requireProofKey;public;
;smartedit;hybris;basic,permissionswebservices;authorization_code,saml_token;3600;ROLE_ADMINGROUP,ROLE_BASECMSMANAGERGROUP,ROLE_PREVIEWMANAGERGROUP;"https://<your.domain.name>/smartedit";true;true; 
```

<div id="n0t" class="title">

**Note**

</div>

Replace `<your.domain.name>` with the domain name of your environment to set the `registeredredirecturi`.

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
