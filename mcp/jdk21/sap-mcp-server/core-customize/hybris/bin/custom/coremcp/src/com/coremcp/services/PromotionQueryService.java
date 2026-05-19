package com.coremcp.services;

import java.util.List;
import java.util.Map;

/**
 * Service for querying promotion rules and coupons from the type system.
 */
public interface PromotionQueryService
{
	/**
	 * Returns promotion rules, optionally filtered to only active ones.
	 *
	 * @param activeOnly if true, only return promotions within their date range
	 * @return list of promotion data maps (code, name, status, startDate, endDate, description)
	 */
	List<Map<String, Object>> getPromotions(boolean activeOnly);

	/**
	 * Returns coupons with redemption counts, optionally filtered to active only.
	 *
	 * @param activeOnly if true, only return active coupons
	 * @return list of coupon data maps (couponId, name, active, redemption counts, dates)
	 */
	List<Map<String, Object>> getCoupons(boolean activeOnly);
}
