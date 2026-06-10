package com.coremcp.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coremcp.services.LlmClient;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.search.ProductSearchFacade;
import de.hybris.platform.commercefacades.search.data.SearchStateData;
import de.hybris.platform.commerceservices.search.facetdata.ProductSearchPageData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;


/**
 * Covers the vision-analysis parsing paths (fenced JSON, malformed JSON, failed
 * call) and the 3-tier catalog search with cross-tier deduplication.
 */
@UnitTest
public class DefaultVisualSearchServiceTest
{
	private DefaultVisualSearchService service;
	private LlmClient llmClient;
	private ProductSearchFacade<ProductData> productSearchFacade;

	@Before
	@SuppressWarnings("unchecked")
	public void setUp()
	{
		llmClient = mock(LlmClient.class);
		productSearchFacade = mock(ProductSearchFacade.class);

		service = new DefaultVisualSearchService();
		service.setLlmClient(llmClient);
		service.setProductSearchFacade(productSearchFacade);
		service.setVisionModel("gpt-4o");
		service.setMaxResults(10);
	}

	private Map<String, Object> visionResponse(final String content)
	{
		final Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "assistant");
		message.put("content", content);
		final Map<String, Object> choice = new LinkedHashMap<>();
		choice.put("message", message);
		choice.put("finish_reason", "stop");
		return Map.of("choices", List.of(choice));
	}

	private ProductData product(final String code)
	{
		final ProductData p = new ProductData();
		p.setCode(code);
		p.setName("Product " + code);
		return p;
	}

	private ProductSearchPageData<SearchStateData, ProductData> page(final ProductData... products)
	{
		final ProductSearchPageData<SearchStateData, ProductData> page = new ProductSearchPageData<>();
		page.setResults(List.of(products));
		return page;
	}

	private void stubVision(final String content)
	{
		final Map<String, Object> response = visionResponse(content);
		when(llmClient.chatCompletion(any(), isNull(), anyString())).thenReturn(response);
	}

	@Test
	public void identifiedProductSearchesTier1AndReturnsOccShapedMatches()
	{
		stubVision("""
			{"productName":"WH-1000XM5","brand":"Sony","category":"Headphones",
			 "searchTerms":["sony headphones","headphones"],
			 "reasoning":"Distinctive earcup design.","confidence":"high"}""");
		final ProductSearchPageData<SearchStateData, ProductData> tier1 = page(product("HEADPHONES_1"));
		when(productSearchFacade.textSearch(any(SearchStateData.class), any(PageableData.class)))
			.thenReturn((ProductSearchPageData) tier1);

		final Map<String, Object> result = service.searchByImage("aW1hZ2U=", "image/png");

		assertEquals("Distinctive earcup design.", result.get("visionAnalysis"));
		@SuppressWarnings("unchecked")
		final Map<String, Object> aiDetail = (Map<String, Object>) result.get("aiDetail");
		assertEquals("Sony", aiDetail.get("brand"));
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("products");
		assertTrue(products.size() >= 1);
		assertEquals("bestMatch", products.get(0).get("matchType"));
		@SuppressWarnings("unchecked")
		final Map<String, Object> first = (Map<String, Object>) products.get(0).get("product");
		assertEquals("HEADPHONES_1", first.get("code"));
	}

	@Test
	public void fencedJsonFromModelIsStrippedBeforeParsing()
	{
		stubVision("```json\n{\"category\":\"Speakers\",\"reasoning\":\"Round grille.\"}\n```");
		when(productSearchFacade.textSearch(any(SearchStateData.class), any(PageableData.class)))
			.thenReturn((ProductSearchPageData) page(product("SPEAKER_1")));

		final Map<String, Object> result = service.searchByImage("aW1hZ2U=", "image/jpeg");

		assertEquals("Round grille.", result.get("visionAnalysis"));
	}

	@Test
	public void malformedVisionJsonDegradesGracefully()
	{
		stubVision("I looked at the picture and it seems to be headphones, not JSON though.");

		final Map<String, Object> result = service.searchByImage("aW1hZ2U=", "image/png");

		assertTrue(((String) result.get("visionAnalysis")).startsWith("Unable to analyze image"));
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("products");
		assertTrue(products.isEmpty());
	}

	@Test
	public void visionCallFailureDegradesGracefully()
	{
		when(llmClient.chatCompletion(any(), isNull(), anyString())).thenThrow(new RuntimeException("provider down"));

		final Map<String, Object> result = service.searchByImage("aW1hZ2U=", "image/png");

		assertTrue(((String) result.get("visionAnalysis")).contains("Unable to analyze image"));
		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("products");
		assertTrue(products.isEmpty());
	}

	@Test
	public void duplicateProductsAcrossTiersAreDeduplicated()
	{
		stubVision("""
			{"productName":"Gaming Mouse","brand":"Acme","category":"Mice",
			 "searchTerms":["acme mouse","gaming mouse"],
			 "reasoning":"RGB mouse.","confidence":"medium"}""");
		// Tier 1 (brand + name) returns A; tier 2 (extra terms) returns A and B.
		final ProductSearchPageData<SearchStateData, ProductData> tier1 = page(product("MOUSE_A"));
		final ProductSearchPageData<SearchStateData, ProductData> tier2 = page(product("MOUSE_A"), product("MOUSE_B"));
		when(productSearchFacade.textSearch(any(SearchStateData.class), any(PageableData.class)))
			.thenReturn((ProductSearchPageData) tier1, (ProductSearchPageData) tier2);

		final Map<String, Object> result = service.searchByImage("aW1hZ2U=", "image/png");

		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("products");
		final long mouseACount = products.stream()
			.map(m -> (Map<String, Object>) m.get("product"))
			.filter(p -> "MOUSE_A".equals(p.get("code")))
			.count();
		assertEquals(1, mouseACount);
		assertEquals(2, products.size());
	}

	@Test
	public void maxResultsCapsTheMatchList()
	{
		service.setMaxResults(1);
		stubVision("""
			{"productName":"Keyboard","brand":"Acme","category":"Keyboards",
			 "searchTerms":["acme keyboard","keyboard"],
			 "reasoning":"Mechanical keyboard.","confidence":"medium"}""");
		when(productSearchFacade.textSearch(any(SearchStateData.class), any(PageableData.class)))
			.thenReturn((ProductSearchPageData) page(product("KB_1"), product("KB_2"), product("KB_3")));

		final Map<String, Object> result = service.searchByImage("aW1hZ2U=", "image/png");

		@SuppressWarnings("unchecked")
		final List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("products");
		assertEquals(1, products.size());
	}
}
