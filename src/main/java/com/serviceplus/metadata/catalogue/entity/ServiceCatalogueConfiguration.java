package com.serviceplus.metadata.catalogue.entity;

import java.time.OffsetDateTime;
import java.util.Date;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.serviceplus.metadata.enums.MoveToProdStatus;
import com.serviceplus.metadata.utility.ApplicationConstants;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_catalogue_configuration", schema = ApplicationConstants.SP_SCHEMA_NAME)
public class ServiceCatalogueConfiguration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "configuration_code", nullable = false, unique = true)
	private String configurationCode;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "description")
	private String description;

	@Column(name = "service_definer_id", nullable = false)
	private Long serviceDefinerId;

	@Column(name = "active", nullable = false)
	private Boolean active = false;
	
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "data", columnDefinition = "jsonb", nullable = false)
	private JsonNode data;

	@Column(name = "cr_on")
	private OffsetDateTime createdOn;

	@Column(name = "up_on")
	private OffsetDateTime updatedOn;

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

	@PrePersist
	public void prePersist() {

		OffsetDateTime now = OffsetDateTime.now();

		if (createdOn == null) {
			createdOn = now;
		}

		if (updatedOn == null) {
			updatedOn = now;
		}

		if (active == null) {
	        active = false;
	    }
	}

	@PreUpdate
	public void preUpdate() {
		updatedOn = OffsetDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getConfigurationCode() {
		return configurationCode;
	}

	public void setConfigurationCode(String configurationCode) {
		this.configurationCode = configurationCode;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Long getServiceDefinerId() {
		return serviceDefinerId;
	}

	public void setServiceDefinerId(Long serviceDefinerId) {
		this.serviceDefinerId = serviceDefinerId;
	}

	public JsonNode getData() {
		return data;
	}

	public void setData(JsonNode data) {
		this.data = data;
	}

	public OffsetDateTime getCreatedOn() {
		return createdOn;
	}

	public void setCreatedOn(OffsetDateTime createdOn) {
		this.createdOn = createdOn;
	}

	public OffsetDateTime getUpdatedOn() {
		return updatedOn;
	}

	public void setUpdatedOn(OffsetDateTime updatedOn) {
		this.updatedOn = updatedOn;
	}
}
