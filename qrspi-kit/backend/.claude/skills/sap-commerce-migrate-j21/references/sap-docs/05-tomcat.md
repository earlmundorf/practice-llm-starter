---
source_topic: TOMCAT
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/b8a9332e0f6d455dab5f22d6e027497d.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: b8a9332e0f6d455dab5f22d6e027497d
sap_version: v2211
fetched: 2026-04-30
title: "Tomcat"
---

> Mirror of [Tomcat](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/b8a9332e0f6d455dab5f22d6e027497d.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loiob8a9332e0f6d455dab5f22d6e027497d" class="page concept - topic-topic concept-concept">

# Tomcat

<div class="body conbody">

Review all manual steps related to Tomcat update and adapt your <span class="ph pname" translate="no">SAP Commerce Cloud</span> implementation accordingly.

</div>

<div class="related-links">

1.  **<a href="387ce64104124c13a1a67023b4380342.html" class="link">Replacing setAttribute() Method for Tomcat Connector</a>**\
    <div class="linkdesc">

    Tomcat was updated from version 9.0.95 to version 10.1.x.

    </div>
2.  **<a href="99ae59b30ab145e1a3fc899d196043eb.html" class="link">Configuring Tomcat SSL Connector</a>**\
    <div class="linkdesc">

    Update your configuration if necessary to ensure proper SSL setup after Tomcat 10.x SSL connector configuration attributes were moved.

    </div>
3.  **<a href="57ae1f52efcc4541bb92d945cea9bdf8.html" class="link">Adjusting to new filter API after Tomcat update</a>**\
    <div class="linkdesc">

    After update to Spring 6 and JDK 21 and some of the libraries have been updated to newer versions. Updates include replacement of <span class="keyword apiname">javax.servlet</span> with <span class="keyword apiname">jakarta.servlet</span> and the Tomcat version.

    </div>
4.  **<a href="8285d7bd0f0f40dbac10a1afb816ef61.html" class="link">Adapting security headers configuration in Tomcat 10.1.X</a>**\
    <div class="linkdesc">

    Tomcat was upgraded to 10.1.x. As per Tomcat's documentation, <span class="keyword apiname">X-XSS-Protection</span> header was deprecated and it has been removed starting with Tomcat 11.0.x.

    </div>

</div>

</div>

</div>

<div class="clear">

</div>

</div>

</div>

</div>
