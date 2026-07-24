package com.ucpcommerce.services.impl;

import com.ucpcommerce.model.UcpIdempotencyRecordModel;
import com.ucpcommerce.services.UcpIdempotencyService;

import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.servicelayer.user.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

/**
 * DB-backed idempotency store (CCv2 multi-node safe like the checkout session
 * store): one row per (user, operation, key), the unique {@code cacheKey}
 * index arbitrates concurrent first-writers.
 */
public class DefaultUcpIdempotencyService implements UcpIdempotencyService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultUcpIdempotencyService.class);

	private static final String QUERY_BY_CACHE_KEY =
		"SELECT {pk} FROM {UcpIdempotencyRecord} WHERE {cacheKey} = ?cacheKey";

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private UserService userService;

	@Override
	public Consultation consult(final String operation, final String idempotencyKey, final String requestHash)
	{
		final UcpIdempotencyRecordModel record = findRecord(cacheKey(operation, idempotencyKey));
		if (record == null)
		{
			return new Consultation(Outcome.NEW, null);
		}
		if (requestHash != null && requestHash.equals(record.getRequestHash()))
		{
			return new Consultation(Outcome.REPLAY, record.getResponseJson());
		}
		return new Consultation(Outcome.CONFLICT, null);
	}

	@Override
	public void record(final String operation, final String idempotencyKey, final String requestHash,
		final String responseJson)
	{
		try
		{
			final UcpIdempotencyRecordModel record = modelService.create(UcpIdempotencyRecordModel.class);
			record.setCacheKey(cacheKey(operation, idempotencyKey));
			record.setRequestHash(requestHash);
			record.setResponseJson(responseJson);
			modelService.save(record);
		}
		catch (final Exception e)
		{
			// A concurrent first-writer on another node won the unique index —
			// its record governs; this call's response was still returned.
			LOG.debug("UCP idempotency: could not record {} {}: {}", operation, idempotencyKey, e.getMessage());
		}
	}

	private String cacheKey(final String operation, final String idempotencyKey)
	{
		String uid = "anonymous";
		try
		{
			uid = userService.getCurrentUser().getUid();
		}
		catch (final Exception e)
		{
			LOG.debug("UCP idempotency: no current user: {}", e.getMessage());
		}
		return uid + "|" + operation + "|" + idempotencyKey;
	}

	private UcpIdempotencyRecordModel findRecord(final String cacheKey)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(QUERY_BY_CACHE_KEY);
		query.addQueryParameter("cacheKey", cacheKey);
		final SearchResult<UcpIdempotencyRecordModel> result = flexibleSearchService.search(query);
		return result.getResult().isEmpty() ? null : result.getResult().get(0);
	}

	@Required
	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	@Required
	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	@Required
	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
