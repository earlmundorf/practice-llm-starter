package com.ucpcommerce.tools.impl;

import com.coremcp.services.PromotionQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucpcommerce.constants.UcpcommerceConstants;
import com.ucpcommerce.dto.UcpEnvelope;
import com.ucpcommerce.dto.UcpPromotionsResponse;
import com.ucpcommerce.tools.UcpTool;
import com.ucpcommerce.tools.UcpToolContext;

import de.hybris.platform.util.Config;

import org.springframework.beans.factory.annotation.Required;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom capability {@code com.thinkshop.promotions} (design R7):
 * {@code get_promotions} — active promotion rules and coupon codes via
 * coremcp's {@code PromotionQueryService} (the {@code
 * <requires-extension name="coremcp"/>} reuse). Metadata only — the computed
 * discounts surface in checkout totals when the Drools rules fire.
 */
public class GetPromotionsTool implements UcpTool
{
	private final ObjectMapper objectMapper = new ObjectMapper();
	private PromotionQueryService promotionQueryService;

	@Override
	public String getName()
	{
		return "get_promotions";
	}

	@Override
	public String getDescription()
	{
		return "List the store's promotion rules and coupon codes (custom capability com.thinkshop.promotions). " +
			"Returns rule codes, names, statuses and dates plus active coupons — the actual discounts appear in " +
			"checkout totals when the promotion engine fires during update_checkout.";
	}

	@Override
	public Map<String, Object> getInputSchema()
	{
		final Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"active_only", Map.of("type", "boolean",
				"description", "Only promotions/coupons currently active (within date range)", "default", true),
			"include_coupons", Map.of("type", "boolean",
				"description", "Also return coupon codes", "default", true)
		));
		schema.put("required", List.of());
		return schema;
	}

	@Override
	public String execute(final Map<String, Object> args, final UcpToolContext context) throws Exception
	{
		final boolean activeOnly = !(args.get("active_only") instanceof Boolean)
			|| Boolean.TRUE.equals(args.get("active_only"));
		final boolean includeCoupons = !(args.get("include_coupons") instanceof Boolean)
			|| Boolean.TRUE.equals(args.get("include_coupons"));

		final UcpPromotionsResponse response = new UcpPromotionsResponse();
		response.setUcp(successEnvelope());
		response.setPromotions(promotionQueryService.getPromotions(activeOnly));
		if (includeCoupons)
		{
			response.setCoupons(promotionQueryService.getCoupons(activeOnly));
		}
		return objectMapper.writeValueAsString(response);
	}

	protected UcpEnvelope successEnvelope()
	{
		final UcpEnvelope envelope = new UcpEnvelope(getPinnedUcpVersion());
		envelope.setStatus("success");
		return envelope;
	}

	protected String getPinnedUcpVersion()
	{
		return Config.getString(UcpcommerceConstants.UCP_VERSION_PROPERTY, UcpcommerceConstants.UCP_VERSION_DEFAULT);
	}

	@Required
	public void setPromotionQueryService(final PromotionQueryService promotionQueryService)
	{
		this.promotionQueryService = promotionQueryService;
	}
}
