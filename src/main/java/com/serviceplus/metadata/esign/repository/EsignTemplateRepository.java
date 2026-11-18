package com.serviceplus.metadata.esign.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.metadata.esign.entity.EsignTemplate;

@Repository
public interface EsignTemplateRepository extends JpaRepository<EsignTemplate, Long> {

    Optional<EsignTemplate> findByIdAndTenantId(Long id, String tenantId);

    List<EsignTemplate> findByTenantId(String tenantId);

    List<EsignTemplate> findByTenantIdAndActive(String tenantId, Boolean active);

    boolean existsByTenantIdAndTemplateNameIgnoreCase(String tenantId, String templateName);

    boolean existsByTenantIdAndTemplateNameIgnoreCaseAndIdNot(String tenantId, String templateName, Long id);

    boolean existsByIdAndTenantIdAndActive(Long id, String tenantId, Boolean active);
}
