package com.ucpcommerce.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.services.UcpProfileService;

import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Serves the public UCP discovery profile at
 * {@code GET /occ/v2/{baseSiteId}/.well-known/ucp}.
 *
 * Anonymous by design (the KnowledgeController pattern): UCP requires the
 * profile to be fetchable without credentials. The non-root path is a
 * documented local-testing concession — in production an edge rewrite maps
 * the domain-root /.well-known/ucp onto this route (see docs/README.md, R6).
 */
@Controller
@RequestMapping(value = "/{baseSiteId}")
public class UcpProfileController
{
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Resource(name = "ucpProfileService")
	private UcpProfileService ucpProfileService;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_ANONYMOUS" })
	@RequestMapping(value = "/.well-known/ucp", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public String getProfile(@PathVariable final String baseSiteId, final HttpServletResponse response) throws IOException
	{
		// Permissive CORS — the profile is a public discovery document.
		response.setHeader("Access-Control-Allow-Origin", "*");
		response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
		response.setHeader("Cache-Control", "max-age=300");
		return objectMapper.writeValueAsString(ucpProfileService.buildProfile(baseSiteId));
	}
}
