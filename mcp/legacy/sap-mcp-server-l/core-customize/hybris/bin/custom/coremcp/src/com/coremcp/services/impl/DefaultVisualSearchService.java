package com.coremcp.services.impl;

import com.coremcp.dto.llm.VisionAnalysisResult;
import com.coremcp.services.LlmClient;
import com.coremcp.services.VisualSearchService;

import de.hybris.platform.commercefacades.product.data.ImageData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.product.data.StockData;
import de.hybris.platform.commercefacades.search.ProductSearchFacade;
import de.hybris.platform.commercefacades.search.data.SearchQueryData;
import de.hybris.platform.commercefacades.search.data.SearchStateData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link VisualSearchService}.
 * Uses the configured {@link LlmClient} to call a vision-capable model,
 * then searches the catalog via {@link ProductSearchFacade}.
 *
 * Returns full OCC-shaped product data (same format as /products/search)
 * so the frontend can use the same mappers and components.
 */
public class DefaultVisualSearchService implements VisualSearchService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultVisualSearchService.class);

	// Defaults for these live in project.properties (coremcp.visualsearch.*) and are
	// injected via coremcp-spring.xml.
	private String visionModel = "gpt-4o";
	private int maxResults = 10;

	private static final String SYSTEM_PROMPT = """
		You are a product identification expert for an electronics e-commerce store.
		Analyze the image carefully and respond with valid JSON.

		Think step by step:
		1. What type of product is this? Be as specific as possible.
		2. Can you identify the brand? Look for logos, distinctive design elements, or form factors.
		3. Can you identify the exact model or product name?
		4. What visual attributes do you see? (color, material, size, distinguishing features)
		5. What category does this belong to? (e.g., Headphones, Laptops, Cameras, Smartphones, Speakers, Monitors, Keyboards, Mice, Tablets)
		6. What search terms would be most effective to find this product in an electronics catalog?

		Respond ONLY with valid JSON in this exact format:
		{
		  "productName": "specific product name if identifiable, or generic name like 'wireless headphones'",
		  "brand": "brand name if identifiable, or null",
		  "category": "most specific product category",
		  "color": "primary color if relevant, or null",
		  "material": "material if identifiable, or null",
		  "searchTerms": ["term1", "term2", "term3"],
		  "reasoning": "2-3 sentences explaining what you see in the image, why you identified it this way, and what features or details led to your conclusion. This will be shown to the user.",
		  "confidence": "high if you can identify the exact product, medium if you can identify the type, low if uncertain"
		}

		Be specific with productName when possible (e.g., "Sony WH-1000XM5" not just "headphones").
		The searchTerms array should include 2-4 terms ordered from most specific to most general,
		designed to work well with a Solr full-text search index.
		""";

	private LlmClient llmClient;
	private ProductSearchFacade<ProductData> productSearchFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public Map<String, Object> searchByImage(final String base64Image, final String mimeType)
	{
		final Map<String, Object> result = new LinkedHashMap<>();

		// Step 1: Send image to the configured vision model
		final VisionAnalysisResult analysis = analyzeImage(base64Image, mimeType);
		result.put("visionAnalysis", analysis.resolveReasoning());
		result.put("aiDetail", objectMapper.convertValue(analysis, Map.class)); // full AI response for transparency

		// Step 2: Search catalog using AI-suggested search terms (3-tier)
		final List<Map<String, Object>> matches = new ArrayList<>();

		final String brand = analysis.getBrand();
		final String productName = analysis.getProductName();
		final String category = analysis.getCategory();
		final List<String> searchTerms = analysis.getSearchTerms();

		// Tier 1: Exact — brand + product name (or first search term)
		if (brand != null && productName != null)
		{
			addMatches(matches, searchCatalog(brand + " " + productName, 3), "bestMatch", 0.95);
		}
		else if (searchTerms != null && !searchTerms.isEmpty())
		{
			addMatches(matches, searchCatalog(searchTerms.get(0), 3), "bestMatch", 0.9);
		}

		// Tier 2: Similar — use additional search terms
		if (matches.size() < maxResults)
		{
			if (searchTerms != null && searchTerms.size() > 1)
			{
				for (int i = 1; i < searchTerms.size() && matches.size() < maxResults; i++)
				{
					addMatches(matches, searchCatalog(searchTerms.get(i), 5), "similar", 0.7);
				}
			}
			else
			{
				final String broadQuery = buildBroadQuery(productName,
					analysis.getColor(), analysis.getMaterial(), category);
				if (!broadQuery.isBlank())
				{
					addMatches(matches, searchCatalog(broadQuery, 5), "similar", 0.7);
				}
			}
		}

		// Tier 3: Fallback — category only
		if (matches.isEmpty() && category != null)
		{
			addMatches(matches, searchCatalog(category, 5), "explore", 0.4);
		}

		result.put("products", matches.stream().limit(maxResults).toList());
		return result;
	}

	/**
	 * Adds products to the match list, skipping duplicates by product code.
	 */
	private void addMatches(final List<Map<String, Object>> matches, final List<ProductData> products,
		final String matchType, final double confidence)
	{
		for (final ProductData p : products)
		{
			if (matches.stream().noneMatch(m -> productCode(m).equals(p.getCode())))
			{
				matches.add(buildMatch(p, matchType, confidence));
			}
		}
	}

	/**
	 * Sends the image to the configured vision-capable LLM provider and parses the
	 * structured identification result.
	 */
	private VisionAnalysisResult analyzeImage(final String base64Image, final String mimeType)
	{
		try
		{
			final String effectiveMime = (mimeType != null && !mimeType.isBlank()) ? mimeType : "image/jpeg";
			final String dataUrl = "data:" + effectiveMime + ";base64," + base64Image;

			final List<Map<String, Object>> messages = List.of(
				Map.of("role", "system", "content", SYSTEM_PROMPT),
				Map.of("role", "user", "content", List.of(
					Map.of("type", "image_url",
						"image_url", Map.of("url", dataUrl, "detail", "high")),
					Map.of("type", "text",
						"text", "Identify this product and extract structured attributes for catalog search.")
				))
			);

			final Map<String, Object> response = llmClient.chatCompletion(messages, null, visionModel);
			final String rawContent = com.coremcp.dto.llm.LlmChatResponse.parse(response).getContent();
			if (rawContent.isBlank())
			{
				LOG.warn("No content in vision response");
				return VisionAnalysisResult.unavailable("Unable to analyze image");
			}

			final String content = rawContent.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
			try
			{
				return objectMapper.readValue(content, VisionAnalysisResult.class);
			}
			catch (final Exception parseFailure)
			{
				LOG.error("Vision model returned unparseable JSON ({}): {}", parseFailure.getMessage(), content);
				return VisionAnalysisResult.unavailable("Unable to analyze image: model returned malformed analysis");
			}
		}
		catch (final Exception e)
		{
			LOG.error("Vision analysis failed: {}", e.getMessage(), e);
			return VisionAnalysisResult.unavailable("Unable to analyze image: " + e.getMessage());
		}
	}

	private List<ProductData> searchCatalog(final String query, final int limit)
	{
		try
		{
			final SearchStateData searchState = new SearchStateData();
			final SearchQueryData queryData = new SearchQueryData();
			queryData.setValue(query);
			searchState.setQuery(queryData);

			final PageableData pageable = new PageableData();
			pageable.setCurrentPage(0);
			pageable.setPageSize(limit);

			final Object searchResult = productSearchFacade.textSearch(searchState, pageable);
			@SuppressWarnings("unchecked")
			final List<ProductData> results = ((de.hybris.platform.commerceservices.search.facetdata.ProductSearchPageData<SearchStateData, ProductData>) searchResult).getResults();
			return results != null ? results : List.of();
		}
		catch (final Exception e)
		{
			LOG.warn("Catalog search failed for '{}': {}", query, e.getMessage());
			return List.of();
		}
	}

	private String buildBroadQuery(final String productName, final String color,
		final String material, final String category)
	{
		final List<String> parts = new ArrayList<>();
		if (productName != null) parts.add(productName);
		if (color != null) parts.add(color);
		if (material != null) parts.add(material);
		if (category != null) parts.add(category);
		return String.join(" ", parts);
	}

	@SuppressWarnings("unchecked")
	private String productCode(final Map<String, Object> match)
	{
		final Map<String, Object> product = (Map<String, Object>) match.get("product");
		return product != null ? (String) product.getOrDefault("code", "") : "";
	}

	/**
	 * Builds a match entry with full OCC-shaped product data.
	 * The product map matches what /products/search returns so the frontend
	 * can use the same mapOccProduct() mapper and ProductCard component.
	 */
	private Map<String, Object> buildMatch(final ProductData product, final String matchType, final double confidence)
	{
		final Map<String, Object> productMap = new LinkedHashMap<>();
		productMap.put("code", product.getCode());
		productMap.put("name", product.getName());
		productMap.put("description", product.getDescription());
		productMap.put("summary", product.getSummary());

		// Price — full OCC shape: { value, formattedValue, currencyIso }
		final PriceData price = product.getPrice();
		if (price != null)
		{
			final Map<String, Object> priceMap = new LinkedHashMap<>();
			priceMap.put("value", price.getValue());
			priceMap.put("formattedValue", price.getFormattedValue());
			if (price.getCurrencyIso() != null)
			{
				priceMap.put("currencyIso", price.getCurrencyIso());
			}
			productMap.put("price", priceMap);
		}

		// Stock — full OCC shape: { stockLevel, stockLevelStatus }
		final StockData stock = product.getStock();
		if (stock != null)
		{
			final Map<String, Object> stockMap = new LinkedHashMap<>();
			if (stock.getStockLevel() != null)
			{
				stockMap.put("stockLevel", stock.getStockLevel());
			}
			if (stock.getStockLevelStatus() != null)
			{
				stockMap.put("stockLevelStatus", stock.getStockLevelStatus().toString());
			}
			productMap.put("stock", stockMap);
		}

		// Images — full OCC shape: [{ format, url }]
		if (product.getImages() != null && !product.getImages().isEmpty())
		{
			final List<Map<String, String>> images = new ArrayList<>();
			for (final ImageData img : product.getImages())
			{
				final Map<String, String> imgMap = new LinkedHashMap<>();
				if (img.getFormat() != null) imgMap.put("format", img.getFormat());
				if (img.getUrl() != null) imgMap.put("url", img.getUrl());
				images.add(imgMap);
			}
			productMap.put("images", images);
		}

		// Categories
		if (product.getCategories() != null && !product.getCategories().isEmpty())
		{
			final List<Map<String, String>> categories = new ArrayList<>();
			product.getCategories().forEach(cat -> {
				final Map<String, String> catMap = new LinkedHashMap<>();
				catMap.put("code", cat.getCode());
				catMap.put("name", cat.getName());
				categories.add(catMap);
			});
			productMap.put("categories", categories);
		}

		// Average rating
		if (product.getAverageRating() != null)
		{
			productMap.put("averageRating", product.getAverageRating());
		}

		final Map<String, Object> match = new LinkedHashMap<>();
		match.put("product", productMap);
		match.put("matchType", matchType);
		match.put("confidence", confidence);
		return match;
	}

	public void setVisionModel(final String visionModel)
	{
		this.visionModel = visionModel;
	}

	public void setMaxResults(final int maxResults)
	{
		this.maxResults = maxResults;
	}

	public void setLlmClient(final LlmClient llmClient)
	{
		this.llmClient = llmClient;
	}

	public void setProductSearchFacade(final ProductSearchFacade<ProductData> productSearchFacade)
	{
		this.productSearchFacade = productSearchFacade;
	}
}
