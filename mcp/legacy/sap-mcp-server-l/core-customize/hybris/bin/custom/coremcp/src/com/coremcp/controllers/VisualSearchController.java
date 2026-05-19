package com.coremcp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.services.VisualSearchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.Set;

/**
 * REST controller for visual product search.
 * Accepts a base64 image and returns matching catalog products.
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class VisualSearchController
{
	private static final Logger LOG = LoggerFactory.getLogger(VisualSearchController.class);
	private static final long MAX_IMAGE_SIZE = 10_000_000L; // ~10MB base64
	private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
		"image/jpeg", "image/png", "image/webp", "image/gif"
	);

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Resource(name = "visualSearchService")
	private VisualSearchService visualSearchService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT" })
	@RequestMapping(value = "/agent/visual-search", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	@SuppressWarnings("unchecked")
	public String handleVisualSearch(@RequestBody final String body, final HttpServletResponse response)
	{
		try
		{
			final Map<String, Object> request = objectMapper.readValue(body, Map.class);

			final String image = (String) request.get("image");
			if (image == null || image.isBlank())
			{
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return objectMapper.writeValueAsString(Map.of("error", "image (base64) is required"));
			}

			if (image.length() > MAX_IMAGE_SIZE)
			{
				response.setStatus(413);
				return objectMapper.writeValueAsString(Map.of("error",
					"Image exceeds maximum size of 10MB"));
			}

			final String mimeType = (String) request.getOrDefault("mimeType", "image/jpeg");
			if (!ALLOWED_MIME_TYPES.contains(mimeType))
			{
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return objectMapper.writeValueAsString(Map.of("error",
					"Unsupported image type: " + mimeType + ". Allowed: " + ALLOWED_MIME_TYPES));
			}

			final Map<String, Object> result = visualSearchService.searchByImage(image, mimeType);
			return objectMapper.writeValueAsString(result);
		}
		catch (final Exception e)
		{
			LOG.error("Visual search error", e);
			try
			{
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return objectMapper.writeValueAsString(Map.of("error", e.getMessage()));
			}
			catch (final Exception jsonError)
			{
				return "{\"error\":\"Internal server error\"}";
			}
		}
	}
}
