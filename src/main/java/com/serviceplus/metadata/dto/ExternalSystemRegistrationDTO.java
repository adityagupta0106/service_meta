package com.serviceplus.metadata.dto;

public class ExternalSystemRegistrationDTO {

    private String clientId;
    private String clientName;
    private Integer departmentId;
    private Integer locationId;
    private Boolean active = false;
    private String callType;
    private ExternalSystemDTO externalSystemDetail;

    public ExternalSystemRegistrationDTO() {
    }

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

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public Integer getLocationId() {
        return locationId;
    }

    public void setLocationId(Integer locationId) {
        this.locationId = locationId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public ExternalSystemDTO getExternalSystemDetail() {
        return externalSystemDetail;
    }

    public void setExternalSystemDetail(
            ExternalSystemDTO externalSystemDetail) {
        this.externalSystemDetail = externalSystemDetail;
    }
}