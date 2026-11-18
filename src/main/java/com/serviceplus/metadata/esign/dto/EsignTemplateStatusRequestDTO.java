package com.serviceplus.metadata.esign.dto;

import jakarta.validation.constraints.NotNull;

public class EsignTemplateStatusRequestDTO {

    @NotNull(message = "Template ID is mandatory")
    private Long templateId;

    @NotNull(message = "Active status is mandatory")
    private Boolean active;
    
    public EsignTemplateStatusRequestDTO() {
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}   
    
}
