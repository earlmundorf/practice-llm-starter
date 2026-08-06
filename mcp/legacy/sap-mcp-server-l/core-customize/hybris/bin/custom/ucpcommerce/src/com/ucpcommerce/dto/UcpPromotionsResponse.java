package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Response payload for the custom {@code com.thinkshop.promotions} capability
 * ({@code get_promotions} tool): {@code ucp} envelope + promotion-rule and
 * (optionally) coupon metadata as returned by coremcp's
 * {@code PromotionQueryService} — rule/coupon codes, names, statuses and
 * dates, not computed discounts (those surface in checkout totals when the
 * Drools rules fire).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpPromotionsResponse
{
	@JsonProperty("ucp")
	private UcpEnvelope ucp;

	@JsonProperty("promotions")
	private List<Map<String, Object>> promotions;

	@JsonProperty("coupons")
	private List<Map<String, Object>> coupons;

	@JsonProperty("messages")
	private List<UcpMessage> messages;

	public UcpEnvelope getUcp()
	{
		return ucp;
	}

	public void setUcp(final UcpEnvelope ucp)
	{
		this.ucp = ucp;
	}

	public List<Map<String, Object>> getPromotions()
	{
		return promotions;
	}

	public void setPromotions(final List<Map<String, Object>> promotions)
	{
		this.promotions = promotions;
	}

	public List<Map<String, Object>> getCoupons()
	{
		return coupons;
	}

	public void setCoupons(final List<Map<String, Object>> coupons)
	{
		this.coupons = coupons;
	}

	public List<UcpMessage> getMessages()
	{
		return messages;
	}

	public void setMessages(final List<UcpMessage> messages)
	{
		this.messages = messages;
	}
}
