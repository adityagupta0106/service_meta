package com.serviceplus.metadata.dto;

public class DesignatedOfficerDetails {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private String address;
    private Integer designationId;
    private String designationName;
    private Integer locationId;
    private Integer deliveryUnitLevelId;

    public DesignatedOfficerDetails() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getDesignationId() {
        return designationId;
    }

    public void setDesignationId(Integer designationId) {
        this.designationId = designationId;
    }

    public String getDesignationName() {
        return designationName;
    }

    public void setDesignationName(String designationName) {
        this.designationName = designationName;
    }

    public Integer getLocationId() {
        return locationId;
    }

    public void setLocationId(Integer locationId) {
        this.locationId = locationId;
    }

    public Integer getDeliveryUnitLevelId() {
        return deliveryUnitLevelId;
    }

    public void setDeliveryUnitLevelId(Integer deliveryUnitLevelId) {
        this.deliveryUnitLevelId = deliveryUnitLevelId;
    }

    @Override
    public String toString() {
        return "DesignatedOfficerDetails{" +
                "userId=" + userId +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", address='" + address + '\'' +
                ", designationId=" + designationId +
                ", designationName='" + designationName + '\'' +
                ", locationId=" + locationId +
                ", deliveryUnitLevelId=" + deliveryUnitLevelId +
                '}';
    }
}
