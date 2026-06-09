package com.coremcp.services.impl;

import com.coremcp.services.KnowledgeSearchService;

import de.hybris.platform.solrfacetsearch.config.FacetSearchConfig;
import de.hybris.platform.solrfacetsearch.config.FacetSearchConfigService;
import de.hybris.platform.solrfacetsearch.config.IndexedType;
import de.hybris.platform.solrfacetsearch.search.Document;
import de.hybris.platform.solrfacetsearch.search.FacetSearchService;
import de.hybris.platform.solrfacetsearch.search.SearchQuery;
import de.hybris.platform.solrfacetsearch.search.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin Solr-only knowledge search. Reads fields directly from the indexed
 * documents — no Model loading, no FlexibleSearch. Same shape as
 * ProductSearchFacade's textSearch path (templated free-text query against
 * the knowledgeIndex), minus product-specific data conversion.
 */
public class DefaultKnowledgeSearchService implements KnowledgeSearchService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultKnowledgeSearchService.class);
	private static final String FACET_CONFIG = "knowledgeIndex";
	// resolveIndexedType keys by item-type CODE, not SolrIndexedType.identifier.
	private static final String INDEXED_TYPE = "KnowledgeEntry";
	private static final String TEMPLATE = "DEFAULT";

	private FacetSearchService facetSearchService;
	private FacetSearchConfigService facetSearchConfigService;

	@Override
	public Optional<Document> getByUid(final String uid)
	{
		if (uid == null || uid.isBlank()) return Optional.empty();
		try
		{
			final FacetSearchConfig config = facetSearchConfigService.getConfiguration(FACET_CONFIG);
			final IndexedType indexedType = facetSearchConfigService.resolveIndexedType(config, INDEXED_TYPE);
			final SearchQuery sq = facetSearchService.createSearchQueryFromTemplate(config, indexedType, TEMPLATE);
			sq.addFilterQuery("uid", uid);
			sq.setPageSize(1);
			final SearchResult r = facetSearchService.search(sq);
			return r.getDocuments().stream().findFirst();
		}
		catch (final Exception ex)
		{
			LOG.warn("info_get '{}' failed: {}", uid, ex.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public List<Document> search(final String query, final String category, final int pageSize)
	{
		try
		{
			final FacetSearchConfig config = facetSearchConfigService.getConfiguration(FACET_CONFIG);
			final IndexedType indexedType = facetSearchConfigService.resolveIndexedType(config, INDEXED_TYPE);
			final SearchQuery sq = facetSearchService.createFreeTextSearchQueryFromTemplate(
				config, indexedType, TEMPLATE, query == null ? "" : query);
			sq.setPageSize(Math.max(1, Math.min(pageSize, 50)));
			if (category != null && !category.isBlank())
			{
				sq.addFilterQuery("category", category);
			}
			return facetSearchService.search(sq).getDocuments();
		}
		catch (final Exception ex)
		{
			LOG.warn("info_search '{}' failed: {}", query, ex.getMessage());
			return List.of();
		}
	}

	@Override
	public Map<String, Object> toJson(final Document doc)
	{
		final Map<String, Object> j = new LinkedHashMap<>();
		j.put("uid", doc.getFieldValue("uid"));
		j.put("category", doc.getFieldValue("category"));
		j.put("title", doc.getFieldValue("title"));
		j.put("summary", doc.getFieldValue("summary"));
		j.put("body", doc.getFieldValue("body"));
		j.put("tags", doc.getFieldValue("tags"));
		j.put("priority", doc.getFieldValue("priority"));
		j.put("imageUrl", doc.getFieldValue("imageUrl"));
		return j;
	}

	@Required public void setFacetSearchService(final FacetSearchService s) { this.facetSearchService = s; }
	@Required public void setFacetSearchConfigService(final FacetSearchConfigService s) { this.facetSearchConfigService = s; }
}
