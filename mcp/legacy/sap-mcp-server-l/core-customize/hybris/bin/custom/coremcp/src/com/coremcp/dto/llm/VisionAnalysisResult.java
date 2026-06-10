package com.coremcp.dto.llm;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured result of the vision model's product-identification prompt
 * (see DefaultVisualSearchService.SYSTEM_PROMPT for the requested shape).
 * Unknown fields the model adds are retained in {@link #getExtras()} so the
 * "aiDetail" transparency payload loses nothing.
 *
 * Internal Jackson DTO — deliberately not a *-beans.xml generated class
 * (these payloads never cross the OCC data-mapping pipeline).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VisionAnalysisResult
{
	private String productName;
	private String brand;
	private String category;
	private String color;
	private String material;
	private List<String> searchTerms;
	private String reasoning;
	private String description;
	private String confidence;

	private final Map<String, Object> extras = new LinkedHashMap<>();

	/** Result used when the model output could not be parsed or the call failed. */
	public static VisionAnalysisResult unavailable(final String reason)
	{
		final VisionAnalysisResult result = new VisionAnalysisResult();
		result.setReasoning(reason);
		return result;
	}

	/** The user-facing explanation, with the documented fallback chain. */
	public String resolveReasoning()
	{
		if (reasoning != null && !reasoning.isBlank())
		{
			return reasoning;
		}
		if (description != null && !description.isBlank())
		{
			return description;
		}
		return "Unable to analyze image";
	}

	public String getProductName()
	{
		return productName;
	}

	public void setProductName(final String productName)
	{
		this.productName = productName;
	}

	public String getBrand()
	{
		return brand;
	}

	public void setBrand(final String brand)
	{
		this.brand = brand;
	}

	public String getCategory()
	{
		return category;
	}

	public void setCategory(final String category)
	{
		this.category = category;
	}

	public String getColor()
	{
		return color;
	}

	public void setColor(final String color)
	{
		this.color = color;
	}

	public String getMaterial()
	{
		return material;
	}

	public void setMaterial(final String material)
	{
		this.material = material;
	}

	public List<String> getSearchTerms()
	{
		return searchTerms;
	}

	public void setSearchTerms(final List<String> searchTerms)
	{
		this.searchTerms = searchTerms;
	}

	public String getReasoning()
	{
		return reasoning;
	}

	public void setReasoning(final String reasoning)
	{
		this.reasoning = reasoning;
	}

	public String getDescription()
	{
		return description;
	}

	public void setDescription(final String description)
	{
		this.description = description;
	}

	public String getConfidence()
	{
		return confidence;
	}

	public void setConfidence(final String confidence)
	{
		this.confidence = confidence;
	}

	@JsonAnyGetter
	public Map<String, Object> getExtras()
	{
		return extras;
	}

	@JsonAnySetter
	public void putExtra(final String key, final Object value)
	{
		extras.put(key, value);
	}
}
