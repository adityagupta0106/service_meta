package com.serviceplus.metadata.inboxSentboxFilterConfig.entity;

import java.util.Date;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.serviceplus.metadata.dto.LabelValue;
import com.serviceplus.metadata.enums.MoveToProdStatus;
import com.serviceplus.metadata.inboxSentboxFilterConfig.dto.FilterJSONDTO;
import com.serviceplus.metadata.utility.ApplicationConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_filter_history", schema = ApplicationConstants.SP_SCHEMA_NAME)
public class ServiceFilterHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id", nullable = false)
    private Long historyId;

    @Column(name = "modification_id", nullable = false)
    private Long modificationId;

    @Column(name = "archived_at", nullable = false)
    private Date archivedAt;

    @Column(name = "base_service_id", nullable = false)
    private Long baseServiceId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "filter_name", nullable = false)
    private String filterName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tasks_json", columnDefinition = "jsonb")
    private List<LabelValue> tasks;

    @Column(name = "filter_type", nullable = false, length = 1)
    private Character filterType;

    @Column(name = "filter_condition")
    private String condition = "AND";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_json", columnDefinition = "jsonb")
    private FilterJSONDTO filterJSON;

    @Column(name = "active", nullable = false)
    private Boolean active = false;
    
    @Column(name = "cr_date", nullable = false)
    private Date crDate;

    @Column(name = "up_date")
    private Date upDate;

    @Column(name = "cr_by")
    private Long userId;
    
    @Column(name = "move_to_prod_status", nullable = false)
    private String moveToProdStatus = MoveToProdStatus.DEFAULT.getValue();

    @Column(name = "move_to_prod_on")
    private Date moveToProdOn;

    @Column(name = "move_to_prod_by_user")
    private Long moveToProdByUser;

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public Long getModificationId() {
        return modificationId;
    }

    public void setModificationId(Long modificationId) {
        this.modificationId = modificationId;
    }

    public Date getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Date archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public Long getBaseServiceId() {
        return baseServiceId;
    }

    public void setBaseServiceId(Long baseServiceId) {
        this.baseServiceId = baseServiceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getFilterName() {
        return filterName;
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }

    public List<LabelValue> getTasks() {
        return tasks;
    }

    public void setTasks(List<LabelValue> tasks) {
        this.tasks = tasks;
    }

    public Character getFilterType() {
        return filterType;
    }

    public void setFilterType(Character filterType) {
        this.filterType = filterType;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public FilterJSONDTO getFilterJSON() {
        return filterJSON;
    }

    public void setFilterJSON(FilterJSONDTO filterJSON) {
        this.filterJSON = filterJSON;
    }

    public Date getCrDate() {
        return crDate;
    }

    public void setCrDate(Date crDate) {
        this.crDate = crDate;
    }

    public Date getUpDate() {
        return upDate;
    }

    public void setUpDate(Date upDate) {
        this.upDate = upDate;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}
    
	
}
