package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pagination block on UCP responses. The official {@code pagination.json}
 * response block is cursor-based — {@code has_next_page} is REQUIRED and
 * {@code cursor} must be present when it is true; this page-number binding
 * uses the next page number as the cursor. {@code total_count} plus the
 * page-number convenience fields ride along as schema-tolerated extras.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpPagination
{
	/** REQUIRED by the official response pagination block. */
	@JsonProperty("has_next_page")
	private Boolean hasNextPage;

	/** Next page number as a string — REQUIRED when has_next_page is true. */
	@JsonProperty("cursor")
	private String cursor;

	@JsonProperty("total_count")
	private Long totalCount;

	@JsonProperty("current_page")
	private Integer currentPage;

	@JsonProperty("page_size")
	private Integer pageSize;

	@JsonProperty("total_results")
	private Long totalResults;

	@JsonProperty("total_pages")
	private Integer totalPages;

	public Boolean getHasNextPage()
	{
		return hasNextPage;
	}

	public void setHasNextPage(final Boolean hasNextPage)
	{
		this.hasNextPage = hasNextPage;
	}

	public String getCursor()
	{
		return cursor;
	}

	public void setCursor(final String cursor)
	{
		this.cursor = cursor;
	}

	public Long getTotalCount()
	{
		return totalCount;
	}

	public void setTotalCount(final Long totalCount)
	{
		this.totalCount = totalCount;
	}

	public Integer getCurrentPage()
	{
		return currentPage;
	}

	public void setCurrentPage(final Integer currentPage)
	{
		this.currentPage = currentPage;
	}

	public Integer getPageSize()
	{
		return pageSize;
	}

	public void setPageSize(final Integer pageSize)
	{
		this.pageSize = pageSize;
	}

	public Long getTotalResults()
	{
		return totalResults;
	}

	public void setTotalResults(final Long totalResults)
	{
		this.totalResults = totalResults;
	}

	public Integer getTotalPages()
	{
		return totalPages;
	}

	public void setTotalPages(final Integer totalPages)
	{
		this.totalPages = totalPages;
	}
}
