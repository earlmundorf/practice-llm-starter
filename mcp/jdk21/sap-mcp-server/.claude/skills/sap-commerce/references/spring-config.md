# Spring Configuration in SAP Commerce

## Table of Contents
1. [Overview](#overview)
2. [Extension Spring Files](#extension-spring-files)
3. [Bean Definition Patterns](#bean-definition-patterns)
4. [Aliasing (Override Beans)](#aliasing)
5. [The Beans XML (Hybris DTOs)](#the-beans-xml)
6. [Converters and Populators](#converters-and-populators)
7. [Event Listeners](#event-listeners)
8. [Interceptors](#interceptors)
9. [CronJobs](#cronjobs)

---

## Overview

SAP Commerce uses Spring Framework for dependency injection and bean management. Each extension contributes its own Spring context files, which are merged into a single application context at startup. The key thing that makes SAP Commerce Spring unique: **bean aliasing** lets you override any bean from any extension without modifying the original.

## Extension Spring Files

Each extension can have multiple Spring config files:

| File | Purpose | Loaded |
|---|---|---|
| `resources/{ext}-spring.xml` | Core beans (services, DAOs, facades) | Always |
| `web/webroot/WEB-INF/config/spring-*.xml` | Web beans (controllers) | Web module only |
| `resources/{ext}-beans.xml` | Hybris bean DTOs (not Spring beans) | Build time (code gen) |

### Core Spring XML structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           http://www.springframework.org/schema/context/spring-context.xsd">

  <!-- Bean definitions here -->

</beans>
```

## Bean Definition Patterns

### Service bean

```xml
<alias name="defaultToolProductService" alias="toolProductService"/>
<bean id="defaultToolProductService"
      class="com.company.core.services.impl.DefaultToolProductService">
  <property name="toolProductDao" ref="toolProductDao"/>
  <property name="modelService" ref="modelService"/>
</bean>
```

### DAO bean

```xml
<alias name="defaultToolProductDao" alias="toolProductDao"/>
<bean id="defaultToolProductDao"
      class="com.company.core.daos.impl.DefaultToolProductDao">
  <property name="flexibleSearchService" ref="flexibleSearchService"/>
</bean>
```

### Facade bean

```xml
<alias name="defaultToolProductFacade" alias="toolProductFacade"/>
<bean id="defaultToolProductFacade"
      class="com.company.facades.impl.DefaultToolProductFacade">
  <property name="toolProductService" ref="toolProductService"/>
  <property name="toolProductConverter" ref="toolProductConverter"/>
</bean>
```

### Why the alias pattern matters

Always define beans as:
```xml
<alias name="defaultXxxService" alias="xxxService"/>
<bean id="defaultXxxService" class="..."/>
```

Other extensions can then override by re-aliasing:
```xml
<!-- In another extension's spring.xml -->
<alias name="customToolProductService" alias="toolProductService"/>
<bean id="customToolProductService"
      class="com.other.services.impl.CustomToolProductService"
      parent="defaultToolProductService">
  <!-- Override or extend behavior -->
</bean>
```

All code that references `toolProductService` now gets the custom implementation — without modifying the original extension.

## Aliasing

### Overriding an OOTB bean

```xml
<alias name="myCustomProductService" alias="productService"/>
<bean id="myCustomProductService"
      class="com.company.core.services.impl.MyCustomProductService"
      parent="defaultProductService"/>
```

### Rules

- **`alias` references should point to the `id`, not another alias.** `parent` should also reference a concrete `id`, not an alias.
- **Load order matters.** Extensions are loaded in dependency order from `localextensions.xml`. Your custom extension (which depends on the base) loads later and wins the alias.
- **Never modify OOTB Spring files.** Always override via aliasing in your own extension.

## The Beans XML (Hybris DTOs)

The `*-beans.xml` file defines data transfer objects (DTOs). These are **not Spring beans** — they're Hybris-specific VO definitions that generate Java classes during build.

```xml
<?xml version="1.0" encoding="ISO-8859-1"?>
<beans xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:noNamespaceSchemaLocation="beans.xsd">

  <bean class="com.company.facades.data.ToolProductData">
    <property name="code" type="String"/>
    <property name="name" type="String"/>
    <property name="toolWeight" type="Double"/>
    <property name="toolCategory" type="String"/>
    <property name="warrantyYears" type="Integer"/>
    <property name="accessories" type="java.util.List&lt;com.company.facades.data.ToolProductData>"/>
  </bean>

  <!-- Extending an existing DTO -->
  <bean class="de.hybris.platform.commercefacades.product.data.ProductData">
    <property name="toolCategory" type="String"/>
  </bean>

</beans>
```

After `ant build`, these generate plain Java classes with getters/setters.

## Converters and Populators

The Converter/Populator pattern is central to the Facade layer.

### Defining a converter + populator

```xml
<bean id="toolProductPopulator"
      class="com.company.facades.populators.ToolProductPopulator"/>

<alias name="defaultToolProductConverter" alias="toolProductConverter"/>
<bean id="defaultToolProductConverter" parent="abstractPopulatingConverter">
  <property name="targetClass" value="com.company.facades.data.ToolProductData"/>
  <property name="populators">
    <list>
      <ref bean="toolProductPopulator"/>
    </list>
  </property>
</bean>
```

### Adding a populator to an existing converter

```xml
<bean parent="modifyPopulatorList">
  <property name="list" ref="productConverter"/>
  <property name="add" ref="myCustomProductPopulator"/>
</bean>
```

### Populator implementation

```java
public class ToolProductPopulator implements Populator<ToolProductModel, ToolProductData> {

  @Override
  public void populate(ToolProductModel source, ToolProductData target)
      throws ConversionException {
    target.setCode(source.getCode());
    target.setName(source.getName());
    target.setToolWeight(source.getToolWeight());
    if (source.getToolCategory() != null) {
      target.setToolCategory(source.getToolCategory().getCode());
    }
  }
}
```

## Event Listeners

React to platform events:

```xml
<bean id="toolProductAfterSaveListener"
      class="com.company.core.event.ToolProductAfterSaveListener"
      parent="abstractEventListener"/>
```

```java
public class ToolProductAfterSaveListener extends AbstractEventListener<AfterItemCreationEvent> {
  @Override
  protected void onEvent(AfterItemCreationEvent event) {
    Object item = event.getSource();
    // React to item creation
  }
}
```

## Interceptors

Interceptors hook into the model lifecycle (prepare, validate, load, remove):

```xml
<bean id="toolProductPrepareInterceptor"
      class="com.company.core.interceptors.ToolProductPrepareInterceptor"/>

<bean id="toolProductPrepareInterceptorMapping"
      class="de.hybris.platform.servicelayer.interceptor.impl.InterceptorMapping">
  <property name="interceptor" ref="toolProductPrepareInterceptor"/>
  <property name="typeCode" value="ToolProduct"/>
</bean>
```

```java
public class ToolProductPrepareInterceptor implements PrepareInterceptor<ToolProductModel> {
  @Override
  public void onPrepare(ToolProductModel model, InterceptorContext ctx)
      throws InterceptorException {
    if (model.getCode() != null) {
      model.setCode(model.getCode().toUpperCase());
    }
  }
}
```

Interceptor types:
- **PrepareInterceptor** — Before save (modify the model)
- **ValidateInterceptor** — Before save (throw exception to reject)
- **InitDefaultsInterceptor** — On `modelService.create()` (set defaults)
- **LoadInterceptor** — After loading from DB
- **RemoveInterceptor** — Before deletion

## CronJobs

### items.xml — Define the CronJob type

```xml
<itemtype code="ToolCleanupCronJob" extends="CronJob"
          autocreate="true" generate="true"
          jaloclass="com.company.core.jalo.ToolCleanupCronJob">
  <attributes>
    <attribute qualifier="daysOld" type="java.lang.Integer">
      <defaultvalue>Integer.valueOf(30)</defaultvalue>
      <persistence type="property"/>
    </attribute>
  </attributes>
</itemtype>
```

### Java — Implement the job

```java
public class ToolCleanupJob extends AbstractJobPerformable<ToolCleanupCronJobModel> {
  @Override
  public PerformResult perform(ToolCleanupCronJobModel cronJob) {
    int daysOld = cronJob.getDaysOld();
    // cleanup logic...
    return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
  }
}
```

### Spring — Register the job

```xml
<bean id="toolCleanupJob"
      class="com.company.core.jobs.ToolCleanupJob"
      parent="abstractJobPerformable"/>
```

### ImpEx — Schedule it

```impex
INSERT_UPDATE CronJob ; code[unique=true]   ; job(code)        ; sessionLanguage(isocode)
                      ; toolCleanupCronJob  ; toolCleanupJob   ; en

INSERT_UPDATE Trigger ; cronJob(code)[unique=true] ; cronExpression
                      ; toolCleanupCronJob          ; 0 0 3 * * ?
```
