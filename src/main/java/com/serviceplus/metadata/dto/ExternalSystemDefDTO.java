package com.serviceplus.metadata.dto;

public class ExternalSystemDefDTO {
	private String clientId;
	private String clientName;
	private Boolean active;
	private String onProd;
	private String status;

	public String getClientId() {
		return clientId;
	}
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
	public String getClientName() {
		return clientName;
	}
	public void setClientName(String clientName) {
		this.clientName = clientName;
	}
	public Boolean getActive() {
		return active;
	}
	public void setActive(Boolean active) {
		this.active = active;
	}
	public String getOnProd() {
		return onProd;
	}
	public void setOnProd(String onProd) {
		this.onProd = onProd;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
}
