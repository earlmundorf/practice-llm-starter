package com.ucpcommerce.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coremcp.services.KnowledgeSearchService;
import com.coremcp.services.PromotionQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.tools.UcpToolContext;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.solrfacetsearch.search.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


/**
 * Custom-capability tool passthrough tests (the {@code InfoToolHandlersTest}
 * pattern): {@code get_promotions} over a mocked coremcp
 * {@code PromotionQueryService}, {@code search_knowledge}/{@code get_knowledge}
 * over a mocked {@code KnowledgeSearchService}.
 */
@UnitTest
public class ThinkshopToolsTest
{
	private static final String PINNED_VERSION = "2026-04-08";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private PromotionQueryService promotionQueryService;
	@Mock
	private KnowledgeSearchService knowledgeSearchService;

	private GetPromotionsTool getPromotionsTool;
	private SearchKnowledgeTool searchKnowledgeTool;
	private GetKnowledgeTool getKnowledgeTool;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);

		getPromotionsTool = new GetPromotionsTool()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}
		};
		getPromotionsTool.setPromotionQueryService(promotionQueryService);

		searchKnowledgeTool = new SearchKnowledgeTool()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}
		};
		searchKnowledgeTool.setKnowledgeSearchService(knowledgeSearchService);

		getKnowledgeTool = new GetKnowledgeTool()
		{
			@Override
			protected String getPinnedUcpVersion()
			{
				return PINNED_VERSION;
			}
		};
		getKnowledgeTool.setKnowledgeSearchService(knowledgeSearchService);
	}

	private static Map<String, Object> promo(final String code)
	{
		final Map<String, Object> map = new LinkedHashMap<>();
		map.put("code", code);
		map.put("status", "PUBLISHED");
		return map;
	}

	// ── get_promotions ──────────────────────────────────────────────────────

	@Test
	public void getPromotionsWrapsRulesAndCouponsInAUcpEnvelope() throws Exception
	{
		when(promotionQueryService.getPromotions(true)).thenReturn(
			List.of(promo("bogo_mouse"), promo("free_shipping_1000")));
		when(promotionQueryService.getCoupons(true)).thenReturn(
			List.of(Map.of("couponId", "LAPTOP10")));

		final JsonNode root = objectMapper.readTree(
			getPromotionsTool.execute(Map.of(), UcpToolContext.EMPTY));

		assertEquals(PINNED_VERSION, root.path("ucp").path("version").asText());
		assertEquals("success", root.path("ucp").path("status").asText());
		assertEquals(2, root.path("promotions").size());
		assertEquals("bogo_mouse", root.path("promotions").path(0).path("code").asText());
		assertEquals(1, root.path("coupons").size());
		assertEquals("LAPTOP10", root.path("coupons").path(0).path("couponId").asText());
	}

	@Test
	public void getPromotionsOmitsCouponsWhenNotRequested() throws Exception
	{
		when(promotionQueryService.getPromotions(true)).thenReturn(List.of(promo("bogo_mouse")));

		final JsonNode root = objectMapper.readTree(
			getPromotionsTool.execute(Map.of("include_coupons", false), UcpToolContext.EMPTY));

		assertTrue("coupons key must be absent, not an empty array", root.path("coupons").isMissingNode());
	}

	@Test
	public void getPromotionsPassesActiveOnlyThrough() throws Exception
	{
		when(promotionQueryService.getPromotions(false)).thenReturn(List.of());
		when(promotionQueryService.getCoupons(false)).thenReturn(List.of());

		getPromotionsTool.execute(Map.of("active_only", false), UcpToolContext.EMPTY);

		verify(promotionQueryService).getPromotions(false);
		verify(promotionQueryService).getCoupons(false);
	}

	// ── search_knowledge ────────────────────────────────────────────────────

	@Test
	public void searchKnowledgeWrapsResultsWithCount() throws Exception
	{
		final Document doc = mock(Document.class);
		when(knowledgeSearchService.search("returns", null, 5)).thenReturn(List.of(doc));
		when(knowledgeSearchService.toJson(doc)).thenReturn(
			Map.of("uid", "returns-policy", "category", "policy", "title", "Returns Policy"));

		final JsonNode root = objectMapper.readTree(
			searchKnowledgeTool.execute(Map.of("query", "returns"), UcpToolContext.EMPTY));

		assertEquals("success", root.path("ucp").path("status").asText());
		assertEquals(1, root.path("results").size());
		assertEquals("returns-policy", root.path("results").path(0).path("uid").asText());
		assertEquals(1, root.path("count").asInt());
	}

	@Test
	public void searchKnowledgePassesCategoryAndPageSizeThrough() throws Exception
	{
		when(knowledgeSearchService.search("deals", "promo", 3)).thenReturn(List.of());

		final JsonNode root = objectMapper.readTree(searchKnowledgeTool.execute(
			Map.of("query", "deals", "category", "promo", "page_size", 3), UcpToolContext.EMPTY));

		verify(knowledgeSearchService).search(eq("deals"), eq("promo"), eq(3));
		assertEquals(0, root.path("count").asInt());
		assertTrue(root.path("results").isArray());
	}

	@Test
	public void searchKnowledgeWithoutQueryIsAClientProtocolBug()
	{
		try
		{
			searchKnowledgeTool.execute(Map.of(), UcpToolContext.EMPTY);
			fail("expected IllegalArgumentException");
		}
		catch (final Exception e)
		{
			assertTrue(e instanceof IllegalArgumentException);
		}
	}

	// ── get_knowledge ───────────────────────────────────────────────────────

	@Test
	public void getKnowledgeReturnsTheEntry() throws Exception
	{
		final Document doc = mock(Document.class);
		when(knowledgeSearchService.getByUid("returns-policy")).thenReturn(Optional.of(doc));
		when(knowledgeSearchService.toJson(doc)).thenReturn(
			Map.of("uid", "returns-policy", "title", "Returns Policy", "body", "30 days."));

		final JsonNode root = objectMapper.readTree(
			getKnowledgeTool.execute(Map.of("uid", "returns-policy"), UcpToolContext.EMPTY));

		assertEquals("success", root.path("ucp").path("status").asText());
		assertEquals("returns-policy", root.path("entry").path("uid").asText());
		assertEquals("30 days.", root.path("entry").path("body").asText());
	}

	@Test
	public void getKnowledgeUnknownUidIsAnUnrecoverableNotFoundPayload() throws Exception
	{
		when(knowledgeSearchService.getByUid("nope")).thenReturn(Optional.empty());

		final JsonNode root = objectMapper.readTree(
			getKnowledgeTool.execute(Map.of("uid", "nope"), UcpToolContext.EMPTY));

		// Business error inside the payload — never an isError/500.
		assertEquals("error", root.path("ucp").path("status").asText());
		assertTrue(root.path("entry").isMissingNode());
		assertEquals("not_found", root.path("messages").path(0).path("code").asText());
		assertEquals("unrecoverable", root.path("messages").path(0).path("severity").asText());
	}

	@Test
	public void getKnowledgeWithoutUidIsAClientProtocolBug()
	{
		try
		{
			getKnowledgeTool.execute(Map.of(), UcpToolContext.EMPTY);
			fail("expected IllegalArgumentException");
		}
		catch (final Exception e)
		{
			assertTrue(e instanceof IllegalArgumentException);
		}
	}
}
