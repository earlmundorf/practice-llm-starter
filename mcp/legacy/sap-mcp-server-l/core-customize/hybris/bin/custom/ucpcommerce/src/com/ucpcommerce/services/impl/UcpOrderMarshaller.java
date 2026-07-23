package com.ucpcommerce.services.impl;

import com.ucpcommerce.dto.UcpOrder;

import de.hybris.platform.commercefacades.order.data.OrderData;

import java.time.format.DateTimeFormatter;

/**
 * Marshals a hybris {@code OrderData} into the UCP {@code order} block
 * embedded in a completed checkout (design S3). Minimal in Phase 5 — id (the
 * order code, which order get/history will accept) plus the placement
 * timestamp; the full UCP order schema lands with the order capability in
 * Phase 6, where this class is extended.
 */
public class UcpOrderMarshaller
{
	public UcpOrder marshal(final OrderData orderData)
	{
		if (orderData == null)
		{
			return null;
		}
		final UcpOrder order = new UcpOrder();
		order.setId(orderData.getCode());
		if (orderData.getCreated() != null)
		{
			order.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(orderData.getCreated().toInstant()));
		}
		return order;
	}
}
