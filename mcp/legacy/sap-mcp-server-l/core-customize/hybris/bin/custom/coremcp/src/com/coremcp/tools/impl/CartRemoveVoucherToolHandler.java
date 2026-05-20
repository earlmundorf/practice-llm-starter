package com.coremcp.tools.impl;

import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.voucher.VoucherFacade;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CartRemoveVoucherToolHandler implements McpToolHandler
{
	private VoucherFacade voucherFacade;

	@Override
	public String getName()
	{
		return "cart_remove_voucher";
	}

	@Override
	public String getDescription()
	{
		return "Remove a voucher or coupon code that was previously applied to the current shopping cart. " +
			"Use this when the user says 'remove coupon X', 'take off the discount', 'drop the LAPTOP10 code', " +
			"or similar intent to detach a code from their cart. Check the cart's appliedVouchers in the current " +
			"state to confirm the code is actually on the cart before calling. Returns a confirmation on success, " +
			"or the platform's error message on failure (e.g. code not applied, unknown code). Requires customer " +
			"authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"voucherCode", Map.of("type", "string",
				"description", "The voucher/coupon code to remove from the current cart")
		));
		schema.put("required", List.of("voucherCode"));
		return schema;
	}

	@Override
	public McpToolResult execute(final Map<String, Object> args)
	{
		final String voucherCode = (String) args.get("voucherCode");
		if (voucherCode == null || voucherCode.isBlank())
		{
			return McpToolResult.error("voucherCode is required");
		}
		try
		{
			voucherFacade.releaseVoucher(voucherCode);
			return McpToolResult.success("Voucher '" + voucherCode + "' removed from cart.");
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to remove voucher: " + e.getMessage());
		}
	}

	@Required
	public void setVoucherFacade(final VoucherFacade voucherFacade)
	{
		this.voucherFacade = voucherFacade;
	}
}
