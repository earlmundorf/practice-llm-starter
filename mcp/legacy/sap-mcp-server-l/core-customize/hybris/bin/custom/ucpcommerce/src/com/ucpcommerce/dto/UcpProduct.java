package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A UCP product. Two uses:
 * <ul>
 *   <li><strong>checkout line-item {@code item}</strong> — the minimal
 *       {@code item.json} shape ({@code id} / {@code title} / integer
 *       minor-unit {@code price}); catalog-only fields stay null/omitted;</li>
 *   <li><strong>catalog payloads</strong> — the official
 *       {@code product.json} shape: {@code description} is a formats OBJECT,
 *       {@code price_range} and at least one entry in {@code variants} are
 *       REQUIRED. ThinkShop products are variantless, so the single variant
 *       mirrors the product. The flat {@code price} / {@code currency} /
 *       {@code availability} convenience fields are kept alongside
 *       (schema-tolerated extras).</li>
 * </ul>
 *
 * <p><strong>Money is integer minor units</strong> ($12.99 → 1299),
 * converted exactly once at the marshalling boundary by
 * {@code UcpMoneyConverter} — never ad hoc (the silent-100×-bug guard).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpProduct
{
	@JsonProperty("id")
	private String id;

	@JsonProperty("title")
	private String title;

	/** Official {@code description.json} formats object (catalog payloads). */
	@JsonProperty("description")
	private UcpDescription description;

	/** Integer minor units (e.g. cents for USD). */
	@JsonProperty("price")
	private Long price;

	@JsonProperty("currency")
	private String currency;

	/** {@code in_stock} / {@code low_stock} / {@code out_of_stock}. */
	@JsonProperty("availability")
	private String availability;

	/** Price range across variants (REQUIRED by {@code product.json}). */
	@JsonProperty("price_range")
	private UcpPriceRange priceRange;

	/** Purchasable variants (REQUIRED, min 1 — the single ThinkShop variant). */
	@JsonProperty("variants")
	private List<UcpVariant> variants;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle(final String title)
	{
		this.title = title;
	}

	public UcpDescription getDescription()
	{
		return description;
	}

	public void setDescription(final UcpDescription description)
	{
		this.description = description;
	}

	public Long getPrice()
	{
		return price;
	}

	public void setPrice(final Long price)
	{
		this.price = price;
	}

	public String getCurrency()
	{
		return currency;
	}

	public void setCurrency(final String currency)
	{
		this.currency = currency;
	}

	public String getAvailability()
	{
		return availability;
	}

	public void setAvailability(final String availability)
	{
		this.availability = availability;
	}

	public UcpPriceRange getPriceRange()
	{
		return priceRange;
	}

	public void setPriceRange(final UcpPriceRange priceRange)
	{
		this.priceRange = priceRange;
	}

	public List<UcpVariant> getVariants()
	{
		return variants;
	}

	public void setVariants(final List<UcpVariant> variants)
	{
		this.variants = variants;
	}
}
