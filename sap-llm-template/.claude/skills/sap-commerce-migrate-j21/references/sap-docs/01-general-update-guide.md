---
source_topic: GENERAL
source_url: https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/9efd1f6212134dec8236a146cac4c98a.html
sap_product: SAP_COMMERCE_CLOUD_PUBLIC_CLOUD
deliverable_hash: 75d4c3895cb346008545900bffe851ce
topic_loio: 9efd1f6212134dec8236a146cac4c98a
sap_version: v2211
fetched: 2026-04-30
title: "Framework Update - Java JDK 21 and Spring 6.2"
---

> Mirror of [Framework Update - Java JDK 21 and Spring 6.2](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/75d4c3895cb346008545900bffe851ce/9efd1f6212134dec8236a146cac4c98a.html) — fetched 2026-04-30 via reverse-engineered SAP Help Portal JSON API.
> Authoritative source is the URL above; re-run `scripts/fetch_sap_docs.sh` to refresh.

<div id="d4h5-main-container" class="container_12" role="application">

<div id="d4h5-section-container" class="grid_12">

<div id="d4h5-main-content" class="grid_8 alpha omega">

<div class="section">

<div id="loio9efd1f6212134dec8236a146cac4c98a" class="page concept - topic-topic concept-concept">

# Framework Update - Java JDK 21 and Spring 6.2

<div class="body conbody">

<span class="ph pname" translate="no">SAP Commerce Cloud</span> has been updated to leverage Java JDK 21 and Spring Framework 6.2, bringing the platform in line with modern industry standards but requiring adjustments to project configurations and custom extensions. To simplify the update, it’s recommended to use the OpenRewrite recipes to automatically handle most common changes. Despite the provided automation, manual code review and updates are necessary to complete the update process.

With the new version of the <span class="ph pname" translate="no">SAP Commerce Cloud</span>, the product has been updated to a newer version of Java with long-term support. Spring Framework has also been updated to the next major version. Both Java and Spring are fundamental components within <span class="ph pname" translate="no">SAP Commerce Cloud</span>. This change allows us to keep up to date with industry standards, maintain continuous innovation and stay current with the overall Java-based ecosystem. This major technical update comes with changes that require adjustments to your project configuration and custom extensions, for example, update from javax.\* to jakarta.\* namespace, or changes in OAuth that are described in-depth in <a href="d079f886cab647e5a0555e2cae8e4416.html" class="xref" title="Adjust your OAuth configuration to the new implementation that has been introduced as a result of moving the OAuth functionality from the Spring Security OAuth library, which has reached its EOL, to Spring Security.">Moving to the New OAuth Implementation</a>. The scope of the required changes and the number of applicable upgrade steps may differ depending on your project configuration and custom extensions within it.

You can adapt to these changes in one of the two following ways:

- In a partially automated way, leveraging the OSS OpenRewrite recipes.

- Fully manually following all update steps and additional changes made by the update recipes within the OpenRewrite recipes.

The suggested approach is to consider using the first option, leveraging the OpenRewrite recipes, to smooth the update and reduce the overall complexity. For more information about OpenRewrite, visit <a href="/docs/link-disclaimer?site=https%3A%2F%2Fdocs.openrewrite.org%2F" class="extlink" target="_blank" rel="noopener" alt="https://docs.openrewrite.org/" title="https://docs.openrewrite.org/">https://docs.openrewrite.org/<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

<div id="loio9efd1f6212134dec8236a146cac4c98a__section_rcx_djk_pfc" class="section">

<div class="section section" type="Update with the Help of the OpenRewrite Recipes">

## Update with the Help of the OpenRewrite Recipes

This approach reduces the efforts related to the update to JDK21 and Spring Framework 6.2, thanks to the usage of the recipes and OpenRewrite tool, partially automating required adjustments. This is the recommended approach and we suggest using it on your project to make the update experience easier.

To learn how to use and run the OpenRewrite recipes related to the Java and Spring Framework update, see <a href="/docs/link-disclaimer?site=https://me.sap.com/notes/3618495" target="_blank" rel="noopener">3618495 <img src="themes/sap-light/img/sap_link.png" title="Information published on SAP site" class="link-sap" data-border="0" alt="Information published on SAP site" /></a>: Using OpenRewrite recipes to support SAP Commerce Cloud JDK21 Framework Update adoption.

Keep in mind that automated changes made with the help of OpenRewrite and provided recipes may not be sufficient for your given project and custom code. Recipes target the most common cases found by SAP developers working on the <span class="ph pname" translate="no">SAP Commerce Cloud</span> code base, and by the OSS community working on multiple Java projects and performing similar technical updates. Some of the specific cases may still require manual adjustments and closer review from the developers of the given customer project.

Some of the places where the manual review or adjustment is needed will be marked with a comment starting with the following phrase:

``` pre
FRAMEWORK_UPDATE - TODO (...)
```

This, however, isn't exhaustive. There may also be project-specific places unmarked with comments that automation didn't manage to identify and will also need your attention.

</div>

</div>

<div id="loio9efd1f6212134dec8236a146cac4c98a__section_lnm_2jk_pfc" class="section">

<div class="section section" type="Fully Manual Update">

## Fully Manual Update

This approach assumes that the suggested OpenRewrite recipes and update recipes aren't used for the technical update. This is a fully valid approach, but may come with additional cost and a less smooth experience for the project developers.

For a fully manual update, we recommend reviewing the described update steps within the documentation. This list doesn't include steps that we found covered by the OpenRewrite update described in the first approach. Therefore, we recommend reviewing the recipe tree that was part of the automation from the first approach to get a better understanding of all the changes. What can also be useful are generally available update guides for Spring Framework, for example <a href="/docs/link-disclaimer?site=https%3A%2F%2Fgithub.com%2Fspring-projects%2Fspring-framework%2Fwiki%2FUpgrading-to-Spring-Framework-6.x" class="extlink" target="_blank" rel="noopener" alt="https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x" title="https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x">Upgrading to Spring Framework 6.x. x<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a> and <a href="/docs/link-disclaimer?site=https%3A%2F%2Fopenjdk.org%2Fprojects%2Fjdk%2F21%2F" class="extlink" target="_blank" rel="noopener" alt="https://openjdk.org/projects/jdk/21/" title="https://openjdk.org/projects/jdk/21/">Java 21<img src="themes/sap-light/img/3rd_link.png" title="Information published on non-SAP site" class="link-external" data-border="0" alt="Information published on non-SAP site" /></a>.

For OpenRewrite, a recipe can consist of different recipes. The overall recipe list behind the update recipe is extensive and includes recipes created by SAP developers and the open-source community. When reviewing the recipe tree content, be advised that not all recipes may be applicable or affect the code. Mere presence within the recipe tree doesn't mean that the recipe would introduce changes to the particular project. This is highly dependent on the preconditions for a given recipe to be met within a project, for instance, presence/usage of a class within the project, usage of a particular method that needs to be adjusted and so forth.

<div class="p">

Based on that, it isn't feasible to present only recipes that modified the <span class="ph pname" translate="no">SAP Commerce Cloud</span> source code and are part of the provided update recipe. To present the overall set of possible changes we provide a full set of the recipe update tree that should be reviewed, see <a href="https://help.sap.com/doc/e2b2b11f475843888d63beb185f32f28/v2211/en-US" class="xref" target="_blank" rel="noopener" alt="https://help.sap.com/doc/e2b2b11f475843888d63beb185f32f28/v2211/en-US" title="https://help.sap.com/doc/e2b2b11f475843888d63beb185f32f28/v2211/en-US">SAP Commerce Cloud Framework Update Recipe Tree</a>.

<div id="n0t" class="title">

**Note**

</div>

Due to the extent of the report, it can't be provided directly within the documentation, but is shipped structured HTML file that allows checking the recipe name, description or pre-filled parameters to provide general knowledge regarding the recipe, and possible changes that it could introduce.

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
