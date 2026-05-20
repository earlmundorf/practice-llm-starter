package com.coremcp.tools.impl;

import com.coremcp.tools.McpToolHandler;
import com.coremcp.tools.McpToolResult;
import de.hybris.platform.commercefacades.voucher.VoucherFacade;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CartApplyVoucherToolHandler implements McpToolHandler
{
	private VoucherFacade voucherFacade;

	@Override
	public String getName()
	{
		return "cart_apply_voucher";
	}

	@Override
	public String getDescription()
	{
		return "Apply a voucher or coupon code to the current shopping cart. Use this when the user says " +
			"'apply coupon X', 'use my discount code', 'redeem this voucher', or similar intent to attach a " +
			"specific code to their cart. If the user has not named a specific code, call promotions_get first " +
			"to find eligible coupon codes. Returns a confirmation on success, or the platform's validation " +
			"error message on failure (e.g. invalid code, expired, not eligible). Requires customer authentication.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"voucherCode", Map.of("type", "string",
				"description", "The voucher/coupon code to apply to the current cart")
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
			voucherFacade.applyVoucher(voucherCode);
			return McpToolResult.success("Voucher '" + voucherCode + "' applied to cart.");
		}
		catch (final Exception e)
		{
			return McpToolResult.error("Failed to apply voucher: " + e.getMessage());
		}
	}

	@Required
	public void setVoucherFacade(final VoucherFacade voucherFacade)
	{
		this.voucherFacade = voucherFacade;
	}
}
