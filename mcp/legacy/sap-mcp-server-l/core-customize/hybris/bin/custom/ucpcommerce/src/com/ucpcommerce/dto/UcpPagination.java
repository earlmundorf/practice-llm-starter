package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pagination block on UCP catalog search responses (snake_case wire names).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpPagination
{
	@JsonProperty("current_page")
	private Integer currentPage;

	@JsonProperty("page_size")
	private Integer pageSize;

	@JsonProperty("total_results")
	private Long totalResults;

	@JsonProperty("total_pages")
	private Integer totalPages;

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
