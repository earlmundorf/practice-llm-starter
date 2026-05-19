package com.coremcp.services.impl;

import com.coremcp.services.PromotionQueryService;
import de.hybris.platform.couponservices.model.AbstractCouponModel;
import de.hybris.platform.couponservices.model.SingleCodeCouponModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.promotionengineservices.model.PromotionSourceRuleModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.servicelayer.user.UserService;

import org.apache.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link PromotionQueryService}.
 * Queries PromotionSourceRule and AbstractCoupon via FlexibleSearch using typed models.
 */
public class DefaultPromotionQueryService implements PromotionQueryService
{
	private static final Logger LOG = Logger.getLogger(DefaultPromotionQueryService.class);
	private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

	private FlexibleSearchService flexibleSearchService;
	private UserService userService;

	@Override
	public List<Map<String, Object>> getPromotions(final boolean activeOnly)
	{
		final Date now = new Date();
		final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

		String query = "SELECT {pk} FROM {PromotionSourceRule}";
		final FlexibleSearchQuery fsq;

		if (activeOnly)
		{
			query += " WHERE ({startDate} IS NULL OR {startDate} <= ?now) AND ({endDate} IS NULL OR {endDate} >= ?now)";
			fsq = new FlexibleSearchQuery(query);
			fsq.addQueryParameter("now", now);
		}
		else
		{
			fsq = new FlexibleSearchQuery(query);
		}

		final SearchResult<PromotionSourceRuleModel> searchResult = flexibleSearchService.search(fsq);
		final List<Map<String, Object>> promotions = new ArrayList<>();

		for (final PromotionSourceRuleModel rule : searchResult.getResult())
		{
			final Map<String, Object> promo = new LinkedHashMap<>();
			promo.put("code", rule.getCode());
			promo.put("name", rule.getName());
			promo.put("status", rule.getStatus() != null ? rule.getStatus().toString() : null);
			promo.put("startDate", rule.getStartDate() != null ? dateFormat.format(rule.getStartDate()) : null);
			promo.put("endDate", rule.getEndDate() != null ? dateFormat.format(rule.getEndDate()) : null);
			promo.put("description", rule.getDescription());
			promotions.add(promo);
		}

		return promotions;
	}

	@Override
	public List<Map<String, Object>> getCoupons(final boolean activeOnly)
	{
		final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

		String query = "SELECT {pk} FROM {AbstractCoupon}";
		final FlexibleSearchQuery fsq;

		if (activeOnly)
		{
			query += " WHERE {active} = ?active";
			fsq = new FlexibleSearchQuery(query);
			fsq.addQueryParameter("active", Boolean.TRUE);
		}
		else
		{
			fsq = new FlexibleSearchQuery(query);
		}

		final SearchResult<AbstractCouponModel> searchResult = flexibleSearchService.search(fsq);

		// Batch-fetch total redemption counts to avoid N+1
		final Map<String, Integer> totalRedemptions = queryTotalRedemptions();
		final Map<String, Integer> userRedemptions = queryUserRedemptions();

		final List<Map<String, Object>> coupons = new ArrayList<>();

		for (final AbstractCouponModel coupon : searchResult.getResult())
		{
			final Map<String, Object> data = new LinkedHashMap<>();
			final String couponId = coupon.getCouponId();
			data.put("couponId", couponId);
			data.put("name", coupon.getName());
			data.put("active", coupon.getActive());

			// SingleCodeCoupon-specific fields
			if (coupon instanceof SingleCodeCouponModel)
			{
				final SingleCodeCouponModel singleCoupon = (SingleCodeCouponModel) coupon;
				if (singleCoupon.getMaxRedemptionsPerCustomer() != null)
				{
					data.put("maxRedemptionsPerCustomer", singleCoupon.getMaxRedemptionsPerCustomer());
				}
				if (singleCoupon.getMaxTotalRedemptions() != null)
				{
					data.put("maxTotalRedemptions", singleCoupon.getMaxTotalRedemptions());
				}
			}

			data.put("totalRedemptions", totalRedemptions.getOrDefault(couponId, 0));
			data.put("currentUserRedemptions", userRedemptions.getOrDefault(couponId, 0));

			// Dates (available on AbstractCouponModel)
			if (coupon.getStartDate() != null)
			{
				data.put("startDate", dateFormat.format(coupon.getStartDate()));
			}
			if (coupon.getEndDate() != null)
			{
				data.put("endDate", dateFormat.format(coupon.getEndDate()));
			}

			coupons.add(data);
		}

		return coupons;
	}

	/**
	 * Batch query: total redemptions per coupon code.
	 */
	private Map<String, Integer> queryTotalRedemptions()
	{
		try
		{
			final FlexibleSearchQuery fsq = new FlexibleSearchQuery(
				"SELECT {couponCode}, COUNT({pk}) FROM {CouponRedemption} GROUP BY {couponCode}");
			fsq.setResultClassList(List.of(String.class, Integer.class));
			final SearchResult<List<Object>> result = flexibleSearchService.search(fsq);

			final Map<String, Integer> counts = new HashMap<>();
			for (final List<Object> row : result.getResult())
			{
				counts.put((String) row.get(0), (Integer) row.get(1));
			}
			return counts;
		}
		catch (final Exception e)
		{
			LOG.warn("Failed to query total redemptions: " + e.getMessage());
			return Collections.emptyMap();
		}
	}

	/**
	 * Batch query: redemptions per coupon code for the current user.
	 */
	private Map<String, Integer> queryUserRedemptions()
	{
		try
		{
			final UserModel currentUser = userService.getCurrentUser();
			if (currentUser == null || userService.isAnonymousUser(currentUser))
			{
				return Collections.emptyMap();
			}

			final FlexibleSearchQuery fsq = new FlexibleSearchQuery(
				"SELECT {couponCode}, COUNT({pk}) FROM {CouponRedemption} WHERE {user} = ?user GROUP BY {couponCode}");
			fsq.addQueryParameter("user", currentUser);
			fsq.setResultClassList(List.of(String.class, Integer.class));
			final SearchResult<List<Object>> result = flexibleSearchService.search(fsq);

			final Map<String, Integer> counts = new HashMap<>();
			for (final List<Object> row : result.getResult())
			{
				counts.put((String) row.get(0), (Integer) row.get(1));
			}
			return counts;
		}
		catch (final Exception e)
		{
			LOG.warn("Failed to query user redemptions: " + e.getMessage());
			return Collections.emptyMap();
		}
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
