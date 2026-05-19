package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.customer.CustomerFacade;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomerGetToolHandler implements McpToolHandler
{
	private CustomerFacade customerFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "customer_get";
	}

	@Override
	public String getDescription()
	{
		return "Get the current customer's profile including name, email, and saved addresses. " +
			"Use this when the user asks 'who am I', 'my account', 'my profile', or when you need " +
			"the customer's name or address for checkout. Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Collections.emptyMap());
		schema.put("required", List.of());
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final Object result = customerFacade.getCurrentCustomer();
			return McpToolResult.success(objectMapper.writeValueAsString(result));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to get customer: " + e.getMessage());
		}
	}

	public void setCustomerFacade(final CustomerFacade customerFacade)
	{
		this.customerFacade = customerFacade;
	}
}
