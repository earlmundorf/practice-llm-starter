package com.coremcp.services;

import de.hybris.platform.solrfacetsearch.search.Document;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeSearchService
{
	Optional<Document> getByUid(String uid);

	List<Document> search(String query, String category, int pageSize);

	Map<String, Object> toJson(Document doc);
}
