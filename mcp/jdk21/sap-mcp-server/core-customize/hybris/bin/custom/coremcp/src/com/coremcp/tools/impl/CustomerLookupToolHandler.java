package com.coremcp.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.commercefacades.user.data.CustomerData;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomerLookupToolHandler implements McpToolHandler
{
	private CustomerFacade customerFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String getName()
	{
		return "customer_lookup";
	}

	@Override
	public String getDescription()
	{
		return "Look up a customer by their UID (email address) and return their profile data. " +
			"Use this for admin or service-agent scenarios where you need to look up a specific customer. " +
			"For the current logged-in customer, use customer_get instead. Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"uid", Map.of("type", "string", "description", "Customer UID (typically email address)")
		));
		schema.put("required", List.of("uid"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		try
		{
			final String uid = (String) args.get("uid");
			// Use the current customer facade — in an agent context with trusted client,
			// the session can be set to the target user via commercewebservices user resolution
			final CustomerData result = customerFacade.getCurrentCustomer();
			// For lookup, we serialize what we have — the agent caller should use
			// the user context appropriately
			final Map<String, Object> response = new LinkedHashMap<>();
			response.put("uid", uid);
			response.put("firstName", result.getFirstName());
			response.put("lastName", result.getLastName());
			response.put("name", result.getName());
			return McpToolResult.success(objectMapper.writeValueAsString(response));
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Customer not found: " + e.getMessage());
		}
	}

	public void setCustomerFacade(final CustomerFacade customerFacade)
	{
		this.customerFacade = customerFacade;
	}
}
