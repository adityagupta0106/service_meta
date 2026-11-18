package com.serviceplus.metadata.esign.dto;

public class EsignTemplateDTO {
	private Long templateId;
	private String templateName;
	private Boolean active = false;
	private EsignTemplateJSONDTO templateJson;
	private String tenantId;

	public Long getTemplateId() {
		return templateId;
	}

	public void setTemplateId(Long templateId) {
		this.templateId = templateId;
	}

	public String getTemplateName() {
		return templateName;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public EsignTemplateJSONDTO getTemplateJson() {
		return templateJson;
	}

	public void setTemplateJson(EsignTemplateJSONDTO templateJson) {
		this.templateJson = templateJson;
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

}
