package com.ucpcommerce.services.impl;

import com.ucpcommerce.dto.UcpCheckoutSession;
import com.ucpcommerce.enums.UcpCheckoutStatus;
import com.ucpcommerce.model.UcpCheckoutSessionEntryModel;
import com.ucpcommerce.services.UcpCheckoutSessionService;

import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * DB-backed UCP checkout-session store ({@link UcpCheckoutSessionEntryModel}
 * items) — the mirror of coremcp's {@code PersistedMcpSessionService}.
 * Cluster-safe: UCP clients echo the opaque {@code checkout.id} per call (no
 * cookies, no transport session), so any CCv2 node must resolve any id.
 *
 * Expired entries are evicted lazily on access; abandoned rows are swept by
 * {@code UcpCheckoutSessionCleanupJob}.
 */
public class PersistedUcpCheckoutSessionService implements UcpCheckoutSessionService
{
	private static final Logger LOG = LoggerFactory.getLogger(PersistedUcpCheckoutSessionService.class);

	private static final String CHECKOUT_ID_PREFIX = "ucp_chk_";

	private static final String QUERY_BY_CHECKOUT_ID =
		"SELECT {pk} FROM {UcpCheckoutSessionEntry} WHERE {checkoutId} = ?checkoutId";

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private int ttlMinutes = 30;

	@Override
	public UcpCheckoutSession create(final String cartCode, final String status, final String buyerJson)
	{
		final String checkoutId = CHECKOUT_ID_PREFIX
			+ UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		final UcpCheckoutSessionEntryModel entry = modelService.create(UcpCheckoutSessionEntryModel.class);
		entry.setCheckoutId(checkoutId);
		entry.setCartCode(cartCode);
		entry.setStatus(statusFromCode(status));
		entry.setBuyerJson(buyerJson);
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
		LOG.debug("Created UCP checkout session {} for cart {}", checkoutId, cartCode);
		return toDto(entry);
	}

	@Override
	public UcpCheckoutSession get(final String checkoutId)
	{
		final UcpCheckoutSessionEntryModel entry = findEntry(checkoutId);
		if (entry == null)
		{
			return null;
		}
		if (isExpired(entry))
		{
			modelService.remove(entry);
			return null;
		}
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
		return toDto(entry);
	}

	@Override
	public void update(final String checkoutId, final String cartCode, final String status)
	{
		final UcpCheckoutSessionEntryModel entry = findEntry(checkoutId);
		if (entry == null || isExpired(entry))
		{
			return;
		}
		entry.setCartCode(cartCode);
		entry.setStatus(statusFromCode(status));
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
	}

	@Override
	public void updateBuyer(final String checkoutId, final String buyerJson)
	{
		final UcpCheckoutSessionEntryModel entry = findEntry(checkoutId);
		if (entry == null || isExpired(entry))
		{
			return;
		}
		entry.setBuyerJson(buyerJson);
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
	}

	@Override
	public void beginCompletion(final String checkoutId, final String idempotencyKey)
	{
		final UcpCheckoutSessionEntryModel entry = findEntry(checkoutId);
		if (entry == null || isExpired(entry))
		{
			return;
		}
		entry.setStatus(UcpCheckoutStatus.COMPLETE_IN_PROGRESS);
		entry.setIdempotencyKey(idempotencyKey);
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
	}

	@Override
	public void failCompletion(final String checkoutId)
	{
		final UcpCheckoutSessionEntryModel entry = findEntry(checkoutId);
		if (entry == null || isExpired(entry))
		{
			return;
		}
		entry.setStatus(UcpCheckoutStatus.READY_FOR_COMPLETE);
		// Clear the accepted key so a retry (same or new key) re-executes.
		entry.setIdempotencyKey(null);
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
	}

	@Override
	public void recordCompletion(final String checkoutId, final String completionResponseJson,
		final String orderCode)
	{
		final UcpCheckoutSessionEntryModel entry = findEntry(checkoutId);
		if (entry == null || isExpired(entry))
		{
			return;
		}
		// One save = one atomic entry update (runbook §5.2).
		entry.setStatus(UcpCheckoutStatus.COMPLETED);
		entry.setCompletionResponseJson(completionResponseJson);
		entry.setOrderCode(orderCode);
		entry.setLastAccessedAt(new Date());
		modelService.save(entry);
		LOG.debug("UCP checkout {} completed as order {}", checkoutId, orderCode);
	}

	private UcpCheckoutSessionEntryModel findEntry(final String checkoutId)
	{
		if (checkoutId == null)
		{
			return null;
		}
		final FlexibleSearchQuery query = new FlexibleSearchQuery(QUERY_BY_CHECKOUT_ID);
		query.addQueryParameter("checkoutId", checkoutId);
		final SearchResult<UcpCheckoutSessionEntryModel> result = flexibleSearchService.search(query);
		return result.getResult().isEmpty() ? null : result.getResult().get(0);
	}

	private boolean isExpired(final UcpCheckoutSessionEntryModel entry)
	{
		final Date lastAccessed = entry.getLastAccessedAt();
		if (lastAccessed == null)
		{
			return true;
		}
		return lastAccessed.toInstant().plus(Duration.ofMinutes(ttlMinutes)).isBefore(Instant.now());
	}

	/** UCP wire code string → generated hybris enum (codes match exactly). */
	protected UcpCheckoutStatus statusFromCode(final String code)
	{
		for (final UcpCheckoutStatus value : UcpCheckoutStatus.values())
		{
			if (value.getCode().equals(code))
			{
				return value;
			}
		}
		throw new IllegalArgumentException("Unknown UCP checkout status code: " + code);
	}

	private UcpCheckoutSession toDto(final UcpCheckoutSessionEntryModel entry)
	{
		final UcpCheckoutSession session = new UcpCheckoutSession();
		session.setCheckoutId(entry.getCheckoutId());
		session.setCartCode(entry.getCartCode());
		session.setStatus(entry.getStatus() != null ? entry.getStatus().getCode() : null);
		session.setBuyerJson(entry.getBuyerJson());
		session.setIdempotencyKey(entry.getIdempotencyKey());
		session.setCompletionResponseJson(entry.getCompletionResponseJson());
		session.setOrderCode(entry.getOrderCode());
		if (entry.getCreationtime() != null)
		{
			session.setCreatedAt(entry.getCreationtime().toInstant());
		}
		if (entry.getLastAccessedAt() != null)
		{
			session.setLastAccessedAt(entry.getLastAccessedAt().toInstant());
		}
		return session;
	}

	public void setTtlMinutes(final int ttlMinutes)
	{
		this.ttlMinutes = ttlMinutes;
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
}
