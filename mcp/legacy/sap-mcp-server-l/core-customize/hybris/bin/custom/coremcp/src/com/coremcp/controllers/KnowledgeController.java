package com.coremcp.controllers;

import com.coremcp.services.KnowledgeSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping(value = "/{baseSiteId}")
public class KnowledgeController
{
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Resource(name = "knowledgeSearchService")
	private KnowledgeSearchService search;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_ANONYMOUS" })
	@RequestMapping(value = "/info/{uid}", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public String getOne(@PathVariable final String baseSiteId, @PathVariable final String uid,
			final HttpServletResponse response) throws IOException
	{
		final var doc = search.getByUid(uid);
		if (doc.isEmpty())
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return objectMapper.writeValueAsString(Map.of("error", "Not found", "uid", uid));
		}
		return objectMapper.writeValueAsString(search.toJson(doc.get()));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_ANONYMOUS" })
	@RequestMapping(value = "/info/search", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public String search(@PathVariable final String baseSiteId,
			@RequestParam(name = "q", required = false, defaultValue = "") final String q,
			@RequestParam(name = "category", required = false) final String category,
			@RequestParam(name = "pageSize", required = false, defaultValue = "5") final int pageSize) throws IOException
	{
		final List<Map<String, Object>> results = search.search(q, category, pageSize).stream().map(search::toJson).toList();
		return objectMapper.writeValueAsString(Map.of("results", results, "count", results.size()));
	}
}
