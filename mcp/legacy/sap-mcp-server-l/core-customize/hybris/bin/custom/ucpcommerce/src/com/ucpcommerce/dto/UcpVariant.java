package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The official {@code variant.json} purchasable unit ({@code id} /
 * {@code title} / {@code description} / {@code price} required). ThinkShop
 * products are variantless on the storefront, so each catalog product carries
 * exactly one variant mirroring the product itself — the id a variant
 * advertises here is the same id checkout accepts in {@code line_items}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UcpVariant
{
	@JsonProperty("id")
	private String id;

	@JsonProperty("sku")
	private String sku;

	@JsonProperty("title")
	private String title;

	@JsonProperty("description")
	private UcpDescription description;

	@JsonProperty("price")
	private UcpPrice price;

	@JsonProperty("availability")
	private Availability availability;

	/**
	 * Lookup-response correlation ({@code catalog_lookup.json#lookup_variant}
	 * — REQUIRED there, min 1): which request identifier resolved to this
	 * variant and how. Null/omitted outside lookup responses.
	 */
	@JsonProperty("inputs")
	private List<InputCorrelation> inputs;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getSku()
	{
		return sku;
	}

	public void setSku(final String sku)
	{
		this.sku = sku;
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

	public UcpPrice getPrice()
	{
		return price;
	}

	public void setPrice(final UcpPrice price)
	{
		this.price = price;
	}

	public Availability getAvailability()
	{
		return availability;
	}

	public void setAvailability(final Availability availability)
	{
		this.availability = availability;
	}

	public List<InputCorrelation> getInputs()
	{
		return inputs;
	}

	public void setInputs(final List<InputCorrelation> inputs)
	{
		this.inputs = inputs;
	}

	/** One {@code input_correlation.json} entry: request id → this variant. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class InputCorrelation
	{
		public static final String MATCH_EXACT = "exact";

		@JsonProperty("id")
		private String id;

		@JsonProperty("match")
		private String match;

		public InputCorrelation()
		{
			// for Jackson
		}

		public InputCorrelation(final String id, final String match)
		{
			this.id = id;
			this.match = match;
		}

		public String getId()
		{
			return id;
		}

		public void setId(final String id)
		{
			this.id = id;
		}

		public String getMatch()
		{
			return match;
		}

		public void setMatch(final String match)
		{
			this.match = match;
		}
	}

	/** Variant availability: {@code available} + a well-known {@code status} string. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class Availability
	{
		@JsonProperty("available")
		private Boolean available;

		@JsonProperty("status")
		private String status;

		public Availability()
		{
			// for Jackson
		}

		public Availability(final Boolean available, final String status)
		{
			this.available = available;
			this.status = status;
		}

		public Boolean getAvailable()
		{
			return available;
		}

		public void setAvailable(final Boolean available)
		{
			this.available = available;
		}

		public String getStatus()
		{
			return status;
		}

		public void setStatus(final String status)
		{
			this.status = status;
		}
	}
}
