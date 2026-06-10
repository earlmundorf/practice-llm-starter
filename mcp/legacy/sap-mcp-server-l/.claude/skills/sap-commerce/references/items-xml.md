# items.xml — Type System Reference

## Table of Contents
1. [Overview](#overview)
2. [File Structure](#file-structure)
3. [Item Types](#item-types)
4. [Attributes](#attributes)
5. [Relations](#relations)
6. [Enumerations](#enumerations)
7. [Collection and Map Types](#collection-and-map-types)
8. [Code Generation](#code-generation)
9. [Common Patterns](#common-patterns)
10. [Gotchas](#gotchas)

---

## Overview

The `items.xml` file defines the SAP Commerce **type system** — the data model. Each extension has its own `{extension}-items.xml` in the `resources/` directory. During build, the platform:

1. Merges all items.xml files across extensions
2. Generates Java Model classes in `gensrc/`
3. Creates/updates database tables and columns

This is the single source of truth for the data model. You never write Model classes by hand.

## File Structure

```xml
<?xml version="1.0" encoding="ISO-8859-1"?>
<items xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:noNamespaceSchemaLocation="items.xsd">

  <atomictypes>
    <!-- Rarely needed: custom primitive mappings -->
  </atomictypes>

  <collectiontypes>
    <!-- Typed collections -->
  </collectiontypes>

  <enumtypes>
    <!-- Enumerations -->
  </enumtypes>

  <maptypes>
    <!-- Typed maps -->
  </maptypes>

  <relations>
    <!-- 1:n and n:m relationships -->
  </relations>

  <itemtypes>
    <!-- The main event: type definitions -->
  </itemtypes>

</items>
```

## Item Types

### Creating a new type

```xml
<itemtype code="ToolProduct" extends="Product"
          autocreate="true" generate="true"
          jaloclass="com.coremcp.jalo.ToolProduct">
  <deployment table="ToolProducts" typecode="25000"/>
  <attributes>
    <attribute qualifier="toolWeight" type="java.lang.Double">
      <description>Weight of the tool in kilograms</description>
      <modifiers optional="true"/>
      <persistence type="property"/>
    </attribute>
    <attribute qualifier="warrantyYears" type="java.lang.Integer">
      <description>Warranty period in years</description>
      <defaultvalue>Integer.valueOf(1)</defaultvalue>
      <modifiers optional="true"/>
      <persistence type="property"/>
    </attribute>
  </attributes>
</itemtype>
```

Key attributes on `<itemtype>`:
- `code` — Unique type name (becomes the Model class name with "Model" suffix)
- `extends` — Parent type (defaults to `GenericItem`)
- `autocreate="true"` — Create the type in the DB (set `true` for new types)
- `generate="true"` — Generate the Java Model class
- `jaloclass` — Legacy Jalo class (still required syntactically)
- `<deployment table="..." typecode="..."/>` — Required for **new** types. Typecode must be unique across the system (use 10000+ for custom types)

### Extending an existing type (adding attributes)

```xml
<itemtype code="Product" autocreate="false" generate="false">
  <attributes>
    <attribute qualifier="toolCategory" type="ToolCategoryEnum">
      <description>Category classification for tools</description>
      <modifiers optional="true"/>
      <persistence type="property"/>
    </attribute>
  </attributes>
</itemtype>
```

When extending, set `autocreate="false"` and `generate="false"` — the type already exists, you're just adding to it.

## Attributes

### Persistence types

```xml
<!-- Standard property column -->
<persistence type="property"/>

<!-- Dynamic attribute (computed, no DB column) -->
<persistence type="dynamic" attributeHandler="myDynamicHandler"/>

<!-- Jalo-only (deprecated, avoid) -->
<persistence type="jalo"/>
```

### Modifiers

```xml
<modifiers read="true"       <!-- Getter generated (default true) -->
           write="true"      <!-- Setter generated (default true) -->
           optional="true"   <!-- Nullable (default true) -->
           unique="false"    <!-- Unique constraint -->
           initial="false"   <!-- Set only on creation, immutable after -->
           search="true"     <!-- Indexed for FlexibleSearch -->
           partof="false"/>  <!-- Cascade delete with parent -->
```

### Common attribute types

| items.xml type | Java type | Notes |
|---|---|---|
| `java.lang.String` | `String` | Standard string |
| `java.lang.Integer` | `Integer` | |
| `java.lang.Long` | `Long` | |
| `java.lang.Double` | `Double` | |
| `java.lang.Boolean` | `Boolean` | |
| `java.util.Date` | `Date` | |
| `localized:java.lang.String` | `String` (per locale) | Localized string — getter takes `Locale` param |
| `MediaModel` or `Media` | `MediaModel` | Reference to media/files |
| `CatalogVersion` | `CatalogVersionModel` | Catalog version reference |
| A custom enum code | The enum type | |
| A custom item type code | That type's Model | |

### Localized attributes

```xml
<attribute qualifier="description" type="localized:java.lang.String">
  <persistence type="property">
    <columntype database="oracle">
      <value>CLOB</value>
    </columntype>
    <columntype>
      <value>HYBRIS.LONG_STRING</value>
    </columntype>
  </persistence>
</attribute>
```

Usage in Java:
```java
model.getDescription(Locale.ENGLISH);
model.setDescription("Tool description", Locale.ENGLISH);
```

### Dynamic attributes

Dynamic attributes have no database column — they're computed at runtime:

```xml
<attribute qualifier="displayName" type="java.lang.String">
  <modifiers read="true" write="false"/>
  <persistence type="dynamic" attributeHandler="toolDisplayNameHandler"/>
</attribute>
```

You must implement the handler:

```java
public class ToolDisplayNameHandler implements DynamicAttributeHandler<String, ToolProductModel> {
  @Override
  public String get(ToolProductModel model) {
    return model.getCode() + " - " + model.getName();
  }

  @Override
  public void set(ToolProductModel model, String value) {
    throw new UnsupportedOperationException("displayName is read-only");
  }
}
```

Register in Spring:
```xml
<bean id="toolDisplayNameHandler"
      class="com.company.core.attributes.ToolDisplayNameHandler"/>
```

## Relations

### One-to-Many

```xml
<relation code="Brand2ToolProductRelation" localized="false"
          autocreate="true" generate="true">
  <deployment table="Brand2ToolProduct" typecode="25010"/>
  <sourceElement type="Brand" qualifier="brand" cardinality="one">
    <modifiers optional="true"/>
  </sourceElement>
  <targetElement type="ToolProduct" qualifier="toolProducts" cardinality="many"
                 collectiontype="list" ordered="true">
    <modifiers optional="true"/>
  </targetElement>
</relation>
```

This generates:
- `BrandModel.getToolProducts()` → `List<ToolProductModel>`
- `ToolProductModel.getBrand()` → `BrandModel`

### Many-to-Many

```xml
<relation code="ToolProduct2AccessoryRelation" localized="false"
          autocreate="true" generate="true">
  <deployment table="Tool2Accessory" typecode="25011"/>
  <sourceElement type="ToolProduct" qualifier="mainTools" cardinality="many"/>
  <targetElement type="ToolProduct" qualifier="accessories" cardinality="many"/>
</relation>
```

## Enumerations

```xml
<enumtypes>
  <enumtype code="ToolCategoryEnum" autocreate="true" generate="true" dynamic="true">
    <value code="HAND_TOOL"/>
    <value code="POWER_TOOL"/>
    <value code="MEASUREMENT"/>
    <value code="SAFETY"/>
  </enumtype>
</enumtypes>
```

- `dynamic="true"` — Values can be added at runtime via ImpEx/backoffice (recommended for most cases)
- `dynamic="false"` — Values are fixed at build time (generates a Java enum)

## Collection and Map Types

```xml
<collectiontypes>
  <collectiontype code="StringCollection"
                  elementtype="java.lang.String"
                  type="collection"/>
</collectiontypes>

<maptypes>
  <maptype code="StringToStringMap"
           argumenttype="java.lang.String"
           returntype="java.lang.String"/>
</maptypes>
```

Use sparingly — relations are preferred over collection types for item references, because collections are serialized into a single DB column (poor for querying).

## Code Generation

After modifying items.xml, always run (from `core-customize/`):

```bash
./gradlew yclean ybuild
```

(then `stopServer yupdatesystem startServer` to apply the schema change — see CLAUDE.md)

This generates in `gensrc/`:
- `GeneratedToolProductModel.java` — Base class with getters/setters
- `ToolProductModel.java` — Your customizable subclass (only generated once, then preserved)

The generated Model is what you use everywhere in Java code:
```java
ToolProductModel tool = modelService.create(ToolProductModel.class);
tool.setCode("HAMMER-001");
tool.setToolWeight(1.5);
modelService.save(tool);
```

## Common Patterns

### Catalog-versioned type
```xml
<itemtype code="ToolProduct" extends="Product"
          autocreate="true" generate="true"
          jaloclass="com.company.core.jalo.ToolProduct">
  <deployment table="ToolProducts" typecode="25000"/>
  <!-- Inherits catalogVersion from Product -->
</itemtype>
```

### Type with unique key
```xml
<attribute qualifier="code" type="java.lang.String">
  <modifiers unique="true" optional="false"/>
  <persistence type="property"/>
</attribute>
```

### Custom indexes
```xml
<itemtype code="ToolProduct" ...>
  <indexes>
    <index name="toolCodeIdx" unique="true">
      <key attribute="code"/>
      <key attribute="catalogVersion"/>
    </index>
  </indexes>
</itemtype>
```

## Gotchas

- **Typecodes must be globally unique.** Use 10000-32767 for custom types. Check existing ones in HAC → Types → Composed Types.
- **Never change a typecode** after it's deployed to production — it's used as a primary key prefix.
- **`autocreate` vs `generate`**: `autocreate` controls DB type creation; `generate` controls Java code generation. For extending existing types, both should be `false`.
- **Removing attributes is hard.** The platform doesn't drop columns automatically. You need manual DB migration or a full reinitialize.
- **Collection types stored in a single column** — Don't use for large collections or queryable data. Use relations instead.
- **Always run `./gradlew yclean ybuild`** after items.xml changes — incremental builds won't pick up type system changes, and apply the schema with `yupdatesystem` (server stopped).
