package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A UCP <em>order</em> line item ({@code order_line_item.json}) — unlike the
 * checkout line item, {@code quantity} is an object ({@code original} /
 * {@code total} / {@code fulfilled}) and a derived {@code status} is
 * REQUIRED. No fulfillment process runs on this demo platform, so a placed
 * order's lines are always {@code processing} with {@code fulfilled=0}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpOrderLineItem
{
	public static final String STATUS_PROCESSING = "processing";

	@JsonProperty("id")
	private String id;

	@JsonProperty("item")
	private UcpProduct item;

	@JsonProperty("quantity")
	private Quantity quantity;

	@JsonProperty("totals")
	private List<UcpTotal> totals;

	/** {@code processing} / {@code partial} / {@code fulfilled} / {@code removed}. */
	@JsonProperty("status")
	private String status;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public UcpProduct getItem()
	{
		return item;
	}

	public void setItem(final UcpProduct item)
	{
		this.item = item;
	}

	public Quantity getQuantity()
	{
		return quantity;
	}

	public void setQuantity(final Quantity quantity)
	{
		this.quantity = quantity;
	}

	public List<UcpTotal> getTotals()
	{
		return totals;
	}

	public void setTotals(final List<UcpTotal> totals)
	{
		this.totals = totals;
	}

	public String getStatus()
	{
		return status;
	}

	public void setStatus(final String status)
	{
		this.status = status;
	}

	/** The order-line quantity block: {@code total} + {@code fulfilled} required. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class Quantity
	{
		@JsonProperty("original")
		private Long original;

		@JsonProperty("total")
		private Long total;

		@JsonProperty("fulfilled")
		private Long fulfilled;

		public Quantity()
		{
			// for Jackson
		}

		public Quantity(final Long original, final Long total, final Long fulfilled)
		{
			this.original = original;
			this.total = total;
			this.fulfilled = fulfilled;
		}

		public Long getOriginal()
		{
			return original;
		}

		public void setOriginal(final Long original)
		{
			this.original = original;
		}

		public Long getTotal()
		{
			return total;
		}

		public void setTotal(final Long total)
		{
			this.total = total;
		}

		public Long getFulfilled()
		{
			return fulfilled;
		}

		public void setFulfilled(final Long fulfilled)
		{
			this.fulfilled = fulfilled;
		}
	}
}
