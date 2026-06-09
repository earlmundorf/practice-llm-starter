package com.coremcp.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coremcp.services.KnowledgeSearchService;
import com.coremcp.tools.McpToolResult;

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


@UnitTest
public class InfoToolHandlersTest
{
	private InfoGetToolHandler getHandler;
	private InfoSearchToolHandler searchHandler;

	@Mock
	private KnowledgeSearchService search;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);
		getHandler = new InfoGetToolHandler();
		getHandler.setKnowledgeSearchService(search);
		searchHandler = new InfoSearchToolHandler();
		searchHandler.setKnowledgeSearchService(search);
		when(search.toJson(any(Document.class))).thenAnswer(inv -> {
			final Document d = (Document) inv.getArguments()[0];
			final Map<String, Object> j = new LinkedHashMap<>();
			j.put("uid", d.getFieldValue("uid"));
			j.put("title", d.getFieldValue("title"));
			return j;
		});
	}

	// ── info_get ────────────────────────────────────────────────────────────

	@Test
	public void testInfoGetSchemaRequiresUid()
	{
		final Map<String, Object> schema = getHandler.getInputSchema();
		assertEquals("object", schema.get("type"));
		@SuppressWarnings("unchecked")
		final List<String> required = (List<String>) schema.get("required");
		assertTrue(required.contains("uid"));
	}

	@Test
	public void testInfoGetReturnsEntryAsJson()
	{
		when(search.getByUid("returns-policy")).thenReturn(Optional.of(stubDoc("returns-policy", "Returns & refunds")));

		final McpToolResult result = getHandler.execute(Map.of("uid", "returns-policy"));

		assertFalse(result.isError());
		final String json = result.getContent();
		assertTrue(json.contains("\"uid\":\"returns-policy\""));
		assertTrue(json.contains("\"title\":\"Returns & refunds\""));
	}

	@Test
	public void testInfoGetReturnsErrorWhenMissing()
	{
		when(search.getByUid("nope")).thenReturn(Optional.empty());

		final McpToolResult result = getHandler.execute(Map.of("uid", "nope"));

		assertTrue(result.isError());
		assertTrue(result.getContent().contains("nope"));
	}

	@Test
	public void testInfoGetReturnsErrorOnException()
	{
		when(search.getByUid(anyString())).thenThrow(new RuntimeException("boom"));

		final McpToolResult result = getHandler.execute(Map.of("uid", "x"));

		assertTrue(result.isError());
		assertTrue(result.getContent().contains("boom"));
	}

	// ── info_search ─────────────────────────────────────────────────────────

	@Test
	public void testInfoSearchSchemaShape()
	{
		final Map<String, Object> schema = searchHandler.getInputSchema();
		assertEquals("object", schema.get("type"));
		@SuppressWarnings("unchecked")
		final Map<String, Object> props = (Map<String, Object>) schema.get("properties");
		assertNotNull(props.get("query"));
		assertNotNull(props.get("category"));
		assertNotNull(props.get("pageSize"));
		@SuppressWarnings("unchecked")
		final List<String> required = (List<String>) schema.get("required");
		assertTrue(required.contains("query"));
	}

	@Test
	public void testInfoSearchReturnsResultsArray()
	{
		when(search.search(eq("policy"), eq(null), anyInt()))
			.thenReturn(List.of(stubDoc("returns-policy", "Returns"), stubDoc("shipping-info", "Shipping")));

		final McpToolResult result = searchHandler.execute(Map.of("query", "policy"));

		assertFalse(result.isError());
		final String json = result.getContent();
		assertTrue(json.contains("\"count\":2"));
		assertTrue(json.contains("returns-policy"));
		assertTrue(json.contains("shipping-info"));
	}

	@Test
	public void testInfoSearchPassesCategoryAndPageSize()
	{
		when(search.search(anyString(), anyString(), anyInt())).thenReturn(List.of());

		searchHandler.execute(Map.of("query", "shipping", "category", "policy", "pageSize", 3));

		verify(search).search("shipping", "policy", 3);
	}

	@Test
	public void testInfoSearchDefaultsPageSizeTo5()
	{
		when(search.search(anyString(), any(String.class), anyInt())).thenReturn(List.of());

		searchHandler.execute(Map.of("query", "x"));

		verify(search).search("x", null, 5);
	}

	@Test
	public void testInfoSearchReturnsErrorOnException()
	{
		when(search.search("x", null, 5)).thenThrow(new RuntimeException("solr down"));

		final McpToolResult result = searchHandler.execute(Map.of("query", "x"));

		assertTrue(result.isError());
		assertTrue(result.getContent().contains("solr down"));
	}

	// ── helpers ─────────────────────────────────────────────────────────────

	private static Document stubDoc(final String uid, final String title)
	{
		final Document d = mock(Document.class);
		when(d.getFieldValue("uid")).thenReturn(uid);
		when(d.getFieldValue("title")).thenReturn(title);
		return d;
	}
}
