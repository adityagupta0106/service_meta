package com.serviceplus.metadata.entity;

import java.util.Date;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.serviceplus.metadata.dto.ExternalSystemDTO;
import com.serviceplus.metadata.enums.MoveToProdStatus;
import com.serviceplus.metadata.utility.ApplicationConstants;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "external_system_registration", schema = ApplicationConstants.SP_SCHEMA_NAME)
public class ExternalSystemRegistration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "client_id", nullable = false)
	private String clientId;
	
	@Column(name = "client_name", nullable = false)
	private String clientName;

	@Column(name = "department_id", nullable = false)
	private Integer departmentId;

	@Column(name = "location_id", nullable = false)
	private Integer locationId;

	@Column(name = "c_by")
	private Long createdBy;

	@Column(name = "c_date")
	private Date createdOn;

	@Column(name = "u_by")
	private Long modifiedBy;

	@Column(name = "u_date")
	private Date modifiedOn;

	@Column(name = "active")
	private Boolean active = false;
	
	@Column(name = "call_type")
	private String callType;

	@Column(name = "configuration_json", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private ExternalSystemDTO externalSystemDetail;

	@Column(name = "move_to_prod_status", nullable = false)
    private String moveToProdStatus = MoveToProdStatus.DEFAULT.getValue();

    @Column(name = "move_to_prod_on")
    private Date moveToProdOn;

    @Column(name = "move_to_prod_by_user")
    private Long moveToProdByUser;
    
	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Long createdBy) {
		this.createdBy = createdBy;
	}

	public Long getModifiedBy() {
		return modifiedBy;
	}

	public void setModifiedBy(Long modifiedBy) {
		this.modifiedBy = modifiedBy;
	}

	public Date getCreatedOn() {
		return createdOn;
	}

	public void setCreatedOn(Date createdOn) {
		this.createdOn = createdOn;
	}

	public Date getModifiedOn() {
		return modifiedOn;
	}

	public void setModifiedOn(Date modifiedOn) {
		this.modifiedOn = modifiedOn;
	}

	public String getCallType() {
		return callType;
	}

	public void setCallType(String callType) {
		this.callType = callType;
	}

	public ExternalSystemDTO getExternalSystemDetail() {
		if (externalSystemDetail == null) {
	        externalSystemDetail = new ExternalSystemDTO();
	    }
		return externalSystemDetail;
	}

	public void setExternalSystemDetail(ExternalSystemDTO externalSystemDetail) {
		this.externalSystemDetail = externalSystemDetail;
	}

	public String getMoveToProdStatus() {
		return moveToProdStatus;
	}

	public void setMoveToProdStatus(String moveToProdStatus) {
		this.moveToProdStatus = moveToProdStatus;
	}

	public Date getMoveToProdOn() {
		return moveToProdOn;
	}

	public void setMoveToProdOn(Date moveToProdOn) {
		this.moveToProdOn = moveToProdOn;
	}

	public Long getMoveToProdByUser() {
		return moveToProdByUser;
	}

	public void setMoveToProdByUser(Long moveToProdByUser) {
		this.moveToProdByUser = moveToProdByUser;
	}

}
