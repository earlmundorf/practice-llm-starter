package com.ucpcommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in a UCP response's {@code messages[]} array — the protocol's
 * business-error/notice envelope (runbook §2.2): business problems are
 * reported as messages inside an HTTP-200 / non-isError payload, never as
 * transport-level failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UcpMessage
{
	/** Message severity: a recoverable problem the client can fix and retry. */
	public static final String SEVERITY_RECOVERABLE = "recoverable";
	/** Message severity: a terminal problem for this request/resource. */
	public static final String SEVERITY_UNRECOVERABLE = "unrecoverable";

	@JsonProperty("type")
	private String type;

	@JsonProperty("code")
	private String code;

	@JsonProperty("severity")
	private String severity;

	@JsonProperty("content")
	private String content;

	public UcpMessage()
	{
		// for Jackson
	}

	public UcpMessage(final String type, final String code, final String severity, final String content)
	{
		this.type = type;
		this.code = code;
		this.severity = severity;
		this.content = content;
	}

	public String getType()
	{
		return type;
	}

	public void setType(final String type)
	{
		this.type = type;
	}

	public String getCode()
	{
		return code;
	}

	public void setCode(final String code)
	{
		this.code = code;
	}

	public String getSeverity()
	{
		return severity;
	}

	public void setSeverity(final String severity)
	{
		this.severity = severity;
	}

	public String getContent()
	{
		return content;
	}

	public void setContent(final String content)
	{
		this.content = content;
	}
}
