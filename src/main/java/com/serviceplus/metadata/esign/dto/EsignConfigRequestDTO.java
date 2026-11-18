package com.serviceplus.metadata.esign.dto;

import java.util.List;

public class EsignConfigRequestDTO {
	private static final long serialVersionUID = 1L;
    private EsignConfigurationKafkaDTO freezeEvent;
    private List<EsignTemplateDTO> templates;
	public EsignConfigurationKafkaDTO getFreezeEvent() {
		return freezeEvent;
	}
	public void setFreezeEvent(EsignConfigurationKafkaDTO freezeEvent) {
		this.freezeEvent = freezeEvent;
	}
	public List<EsignTemplateDTO> getTemplates() {
		return templates;
	}
	public void setTemplates(List<EsignTemplateDTO> templates) {
		this.templates = templates;
	}
    
}
