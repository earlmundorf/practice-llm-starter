---
source_topic: REMOVAL_REST_TEMPLATE_OAUTH2RESTTEMPLATE
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/9f8c9d455de44fd0a204fce82247b206.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: 9f8c9d455de44fd0a204fce82247b206
sap_version: v2211
fetched: 2026-04-30
title: "Preparing for the Removal of All RestTemplate and OAuth2RestTemplate Classes and Methods for the kymaintegration Extension"
---

> Mirror of [Preparing for the Removal of All RestTemplate and OAuth2RestTemplate Classes and Methods for the kymaintegration Extension](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/9f8c9d455de44fd0a204fce82247b206.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loio9f8c9d455de44fd0a204fce82247b206" class="page task - topic-topic task-task">

# Preparing for the Removal of All RestTemplate and OAuth2RestTemplate Classes and Methods for the` kymaintegration` Extension

<div class="body taskbody">

To ensure that this change is compatible with your custom implementations, prepare for the removal of RestTemplate and OAuth2RestTemplate classes and methods for the ` kymaintegration` extension.

<div class="section context">

<span id="steps" class="anchor"></span>

<div class="tasklabel">

## Context

</div>

RestClient is replacing RestTemplate for the `kymaintegration` extension. Follow the procedure below if you have custom implementations that contain any RestTemplate code.

</div>

<div class="section section procedure">

<span id="steps" class="anchor"></span>

<div class="ol steps tasklabel">

## Procedure

</div>

1.  <span class="ph cmd">Make the following changes in the <span class="ph filepath">kymaintegrationservices-spring.xml</span>:</span>
    <div class="itemgroup info">

    1.  Previous Configuration

        ``` pre
        <property name="restTemplateWrapper" ref="kymaDestinationRestTemplateWrapper"/>
        <property name="restTemplate" ref="kymaDestinationRestTemplateWrapper"/>
        <property name="restTemplate" ref="kymaCertificateRestTemplate"/>
        ```

        Change to:

        ``` pre
        <property name="restClientWrapper" ref="kymaDestinationRestClientWrapper"/>
        ```

    2.  Previous Configuration

        ``` pre
        <property name="kymaDestinationRestTemplateWrapper" ref="kymaDestinationRestTemplateWrapper"/>
        <property name="kymaEventRestTemplateWrapper" ref="kymaEventRestTemplateWrapper"/>
        ```

        Change to:

        ``` pre
        <property name="kymaDestinationRestClientWrapper" ref="kymaDestinationRestClientWrapper"/>
         <property name="kymaEventRestClientWrapper" ref="kymaEventRestClientWrapper"/>
        ```

    </div>
2.  <span class="ph cmd">Update any customized Java code that uses `{{de.hybris.platform.kymaintegrationservices.utils.RestTemplateWrapper}}` with `{{de.hybris.platform.kymaintegrationservices.utils.RestClientWrapper}}`.</span>

</div>

</div>

</div>

</div>

<div class="clear">

</div>

</div>

</div>

</div>
