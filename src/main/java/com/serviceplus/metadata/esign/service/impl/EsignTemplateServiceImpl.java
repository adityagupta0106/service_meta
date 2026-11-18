package com.serviceplus.metadata.esign.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.serviceplus.metadata.dto.UserSessionDTO;
import com.serviceplus.metadata.enums.MoveToProdStatus;
import com.serviceplus.metadata.esign.dto.EsignConfigRequestDTO;
import com.serviceplus.metadata.esign.dto.EsignTemplateDTO;
import com.serviceplus.metadata.esign.dto.EsignTemplateJSONDTO;
import com.serviceplus.metadata.esign.dto.EsignTemplateRequestDTO;
import com.serviceplus.metadata.esign.dto.EsignTemplateResponseDTO;
import com.serviceplus.metadata.esign.dto.EsignTemplateStatusRequestDTO;
import com.serviceplus.metadata.esign.entity.EsignTemplate;
import com.serviceplus.metadata.esign.entity.ServiceEsignConfiguration;
import com.serviceplus.metadata.esign.repository.EsignTemplateRepository;
import com.serviceplus.metadata.esign.repository.ServiceEsignConfigurationRepository;
import com.serviceplus.metadata.esign.service.EsignTemplateService;
import com.serviceplus.metadata.esign.service.PfxCertificateStorageService;
import com.serviceplus.metadata.esign.service.PfxStorageResult;
import com.serviceplus.metadata.exception.SPRuntimeError;
import com.serviceplus.metadata.prod.service.IntegratedPluginsProdPushService;
import com.serviceplus.metadata.service.ServiceMetadataService;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class EsignTemplateServiceImpl implements EsignTemplateService {

	@Value("${move.to.prod.token}")
	private String moveToProdToken;

	private static final Logger log = LoggerFactory.getLogger("esignLogger");
    
    private final EsignTemplateRepository templateRepository;
    private final ServiceEsignConfigurationRepository serviceEsignRepository;
    private final PfxCertificateStorageService certificateStorageService;
    private final ServiceMetadataService serviceMetadataService;
    private final IntegratedPluginsProdPushService integratedPluginsProdPushService;

    public EsignTemplateServiceImpl(EsignTemplateRepository templateRepository,
                                   ServiceEsignConfigurationRepository serviceEsignRepository,
                                   PfxCertificateStorageService certificateStorageService,
                                   ServiceMetadataService serviceMetadataService, IntegratedPluginsProdPushService integratedPluginsProdPushService) {
        this.templateRepository = templateRepository;
        this.serviceEsignRepository = serviceEsignRepository;
        this.certificateStorageService = certificateStorageService;
        this.serviceMetadataService = serviceMetadataService;
        this.integratedPluginsProdPushService = integratedPluginsProdPushService;
    }

    @Override
    @Transactional
    public EsignTemplateResponseDTO createTemplate(EsignTemplateRequestDTO request, MultipartFile pfxFile, UserSessionDTO user) {
        validateUser(user);
        validateTemplateRequest(request);

        if (pfxFile == null || pfxFile.isEmpty()) {
            throw new SPRuntimeError("PFX certificate file is mandatory for template creation", HttpStatus.BAD_REQUEST);
        }

        String tenantId = user.getTenantId();
        if (templateRepository.existsByTenantIdAndTemplateNameIgnoreCase(tenantId, request.getTemplateName().getValue().toString())) {
            throw new SPRuntimeError("An eSign template with name '" + request.getTemplateName() + "' already exists in this tenant", HttpStatus.CONFLICT);
        }

        PfxStorageResult storageResult = certificateStorageService.storeCertificate(pfxFile, tenantId, request.getPfxPassword());

        LocalDateTime now = LocalDateTime.now();
        EsignTemplate entity = new EsignTemplate();
        entity.setTemplateName(request.getTemplateName().getValue().toString());
        entity.setTemplateName(request.getTemplateName());
        entity.setAspId(request.getAspId().trim());
        entity.setHasAppId(request.getHasAppId());
        entity.setAppId(Boolean.TRUE.equals(request.getHasAppId()) ? request.getAppId().trim() : null);
        entity.setPfxStorageReference(storageResult.getStorageReference());
        entity.setPfxFilename(storageResult.getOriginalFilename());
        entity.setPfxFileSize(storageResult.getFileSize());
        entity.setPfxFingerprint(storageResult.getSha256Fingerprint());
        entity.setCertificateExpiryDate(storageResult.getExpiryDate());
        entity.setCertificateSubjectDn(storageResult.getSubjectDn());
        entity.setCertificateIssuerDn(storageResult.getIssuerDn());
        entity.setPfxPassword(request.getPfxPassword());
        entity.setTenantId(tenantId);
        entity.setActive(false);
        entity.setCreatedBy(user.getUserID());
        entity.setCreatedDate(now);
        entity.setUpdatedBy(user.getUserID());
        entity.setUpdatedDate(now);

        EsignTemplate saved = templateRepository.save(entity);
        log.info("eSign template created successfully. id={}, name={}, tenant={}", saved.getId(), saved.getTemplateName(), tenantId);

        return mapToResponseDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EsignTemplateResponseDTO> getTemplates(Boolean active, UserSessionDTO user) {
        validateUser(user);
        String tenantId = user.getTenantId();

        List<EsignTemplate> templates;
        if (active != null) {
            templates = templateRepository.findByTenantIdAndActive(tenantId, active);
        } else {
            templates = templateRepository.findByTenantId(tenantId);
        }

        return templates.stream().map(this::mapToResponseDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EsignTemplateResponseDTO getTemplateDetails(Long templateId, UserSessionDTO user) {
        validateUser(user);
        if (templateId == null) {
            throw new SPRuntimeError("Template ID parameter is mandatory", HttpStatus.BAD_REQUEST);
        }

        EsignTemplate template = templateRepository.findByIdAndTenantId(templateId, user.getTenantId())
                .orElseThrow(() -> new SPRuntimeError("eSign template not found with ID: " + templateId, HttpStatus.NOT_FOUND));

        return mapToResponseDTO(template);
    }

    @Override
    @Transactional
    public EsignTemplateResponseDTO updateTemplate(EsignTemplateRequestDTO request, MultipartFile pfxFile, UserSessionDTO user) {
        validateUser(user);
        if (request.getTemplateId() == null) {
            throw new SPRuntimeError("Template ID is mandatory for update", HttpStatus.BAD_REQUEST);
        }
        validateTemplateRequest(request);

        String tenantId = user.getTenantId();
        EsignTemplate existing = templateRepository.findByIdAndTenantId(request.getTemplateId(), tenantId)
                .orElseThrow(() -> new SPRuntimeError("eSign template not found with ID: " + request.getTemplateId(), HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(existing.getActive())) {
            throw new SPRuntimeError("Active eSign template cannot be modified. Deactivate it first.", HttpStatus.BAD_REQUEST);
        }

        if (templateRepository.existsByTenantIdAndTemplateNameIgnoreCaseAndIdNot(tenantId, request.getTemplateName().getValue().toString(), request.getTemplateId())) {
            throw new SPRuntimeError("Another eSign template with name '" + request.getTemplateName() + "' already exists", HttpStatus.CONFLICT);
        }

        existing.setTemplateName(request.getTemplateName().getValue().toString());
        existing.setAspId(request.getAspId().trim());
        existing.setHasAppId(request.getHasAppId());
        existing.setAppId(Boolean.TRUE.equals(request.getHasAppId()) ? request.getAppId().trim() : null);

        // If replacing PFX file
        if (pfxFile != null && !pfxFile.isEmpty()) {
            PfxStorageResult storageResult = certificateStorageService.storeCertificate(pfxFile, tenantId, request.getPfxPassword());
            existing.setPfxStorageReference(storageResult.getStorageReference());
            existing.setPfxFilename(storageResult.getOriginalFilename());
            existing.setPfxFileSize(storageResult.getFileSize());
            existing.setPfxFingerprint(storageResult.getSha256Fingerprint());
            existing.setCertificateExpiryDate(storageResult.getExpiryDate());
            existing.setCertificateSubjectDn(storageResult.getSubjectDn());
            existing.setCertificateIssuerDn(storageResult.getIssuerDn());
            if (storageResult.getEncryptedPassword() != null) {
                existing.setPfxPassword(storageResult.getEncryptedPassword());
            }
        } else if (request.getPfxPassword() != null && !request.getPfxPassword().trim().isEmpty()) {
            existing.setPfxPassword(request.getPfxPassword());
        }
        existing.setPfxPassword(request.getPfxPassword());
        existing.setUpdatedBy(user.getUserID());
        existing.setUpdatedDate(LocalDateTime.now());

        String currentOnProd = existing.getMoveToProdStatus();
        if (currentOnProd != null && !currentOnProd.trim().isEmpty()) {
            existing.setMoveToProdStatus(MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue());
        } else {
            existing.setMoveToProdStatus(MoveToProdStatus.DEFAULT.getValue());
        }

        EsignTemplate updated = templateRepository.save(existing);
        log.info("eSign template updated successfully. id={}, name={}, tenant={}", updated.getId(), updated.getTemplateName(), tenantId);

        return mapToResponseDTO(updated);
    }

    @Override
    @Transactional
    public EsignTemplateResponseDTO updateTemplateStatus(EsignTemplateStatusRequestDTO request, UserSessionDTO user) {
        validateUser(user);
		if (request == null || request.getTemplateId() == null || request.getActive() == null) {
			throw new SPRuntimeError("Template ID and active status are mandatory", HttpStatus.BAD_REQUEST);
		}

		Boolean newActive = request.getActive();
        String tenantId = user.getTenantId();

        EsignTemplate template = templateRepository.findByIdAndTenantId(request.getTemplateId(), tenantId)
                .orElseThrow(() -> new SPRuntimeError("eSign template not found with ID: " + request.getTemplateId(), HttpStatus.NOT_FOUND));

		if (Boolean.FALSE.equals(newActive) && Boolean.TRUE.equals(template.getActive())) {
            // Guard against deactivating a template that is actively configured in a service
            boolean isActivelyUsed = serviceEsignRepository.existsByEsignTemplateIdAndEsignEnabledTrue(template.getId());
            if (isActivelyUsed) {
                throw new SPRuntimeError("Cannot deactivate eSign template because it is actively configured in one or more services. Please reconfigure those services first.", HttpStatus.CONFLICT);
            }
            // If deactivating a template that was previously pushed to production, mark as NP (modified/inactive after prod)
            String prodStatus = template.getMoveToProdStatus();
            if (prodStatus != null && !prodStatus.trim().isEmpty()) {
                template.setMoveToProdStatus(MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue());
            }
        }

		template.setActive(newActive);
        template.setUpdatedBy(user.getUserID());
        template.setUpdatedDate(LocalDateTime.now());

        EsignTemplate saved = templateRepository.save(template);
		log.info("eSign template active status changed. id={}, active={}, onProd={}, tenant={}", saved.getId(), saved.getActive(), saved.getMoveToProdStatus(), tenantId);

        return mapToResponseDTO(saved);
    }

    private void validateUser(UserSessionDTO user) {
        if (user == null || user.getTenantId() == null || user.getUserID() == null) {
            throw new SPRuntimeError("User session is invalid or unauthenticated", HttpStatus.UNAUTHORIZED);
        }
    }

    private void validateTemplateRequest(EsignTemplateRequestDTO request) {
        if (request == null) {
            throw new SPRuntimeError("Request body cannot be null", HttpStatus.BAD_REQUEST);
        }
        if (request.getTemplateName() == null || request.getTemplateName().getValue().toString().isEmpty()) {
            throw new SPRuntimeError("Template name is mandatory and cannot be blank", HttpStatus.BAD_REQUEST);
        }
        if (request.getAspId() == null || request.getAspId().trim().isEmpty()) {
            throw new SPRuntimeError("ASP ID is mandatory and cannot be blank", HttpStatus.BAD_REQUEST);
        }
        if (request.getHasAppId() == null) {
            throw new SPRuntimeError("hasAppId field is mandatory", HttpStatus.BAD_REQUEST);
        }
        if (Boolean.TRUE.equals(request.getHasAppId())) {
            if (request.getAppId() == null || request.getAppId().trim().isEmpty()) {
                throw new SPRuntimeError("App ID is mandatory when hasAppId is true", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private EsignTemplateResponseDTO mapToResponseDTO(EsignTemplate entity) {
        EsignTemplateResponseDTO dto = new EsignTemplateResponseDTO();
        dto.setTemplateId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        
        String onProd = entity.getMoveToProdStatus() != null ? entity.getMoveToProdStatus() : "";
        dto.setActive(entity.getActive());
        dto.setOnProd(onProd);
        dto.setStatus(computeDisplayStatus(entity.getActive(), onProd));
        
        EsignTemplateJSONDTO json = entity.getTemplateJson();
        if (json != null) {
            EsignTemplateJSONDTO safeJson = new EsignTemplateJSONDTO();
            safeJson.setTemplateName(json.getTemplateName());
            safeJson.setAspId(json.getAspId());
            safeJson.setHasAppId(json.getHasAppId());
            safeJson.setAppId(json.getAppId());
            safeJson.setPfxStorageReference(json.getPfxStorageReference());
            safeJson.setPfxFilename(json.getPfxFilename());
            safeJson.setPfxFileSize(json.getPfxFileSize());
            safeJson.setPfxFingerprint(json.getPfxFingerprint());
            safeJson.setCertificateExpiryDate(json.getCertificateExpiryDate());
            safeJson.setCertificateSubjectDn(json.getCertificateSubjectDn());
            safeJson.setCertificateIssuerDn(json.getCertificateIssuerDn());
            safeJson.setPfxPassword(json.getPfxPassword());
            dto.setTemplateJson(safeJson);
        }

        return dto;
    }

    private String computeDisplayStatus(Boolean active, String onProd) {
        if (Boolean.FALSE.equals(active)) {
            return "Inactive";
        }
        if (onProd == null || onProd.trim().isEmpty()) {
            return "Active";
        }
        if (MoveToProdStatus.MOVED_TO_PRODUCTION.getValue().equals(onProd) ||
            MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue().equals(onProd)) {
            return "Online";
        }
        if (MoveToProdStatus.STOPPED_IN_PRODUCTION.getValue().equals(onProd)) {
            return "Suspended";
        }
        return "Inactive";
    }

	@Override
	@Transactional
	public void moveToProd(Long templateId, UserSessionDTO userSessionDetails, HttpServletRequest request) {
		validateUser(userSessionDetails);
		if (templateId == null) {
			throw new SPRuntimeError("Template ID parameter is mandatory", HttpStatus.BAD_REQUEST);
		}
		try {
			EsignTemplate entity = templateRepository.findByIdAndTenantId(templateId, userSessionDetails.getTenantId())
					.orElseThrow(() -> new SPRuntimeError("Configuration not found with ID: " + templateId, HttpStatus.BAD_REQUEST));
			
			if (!Boolean.TRUE.equals(entity.getActive())) {
				throw new SPRuntimeError("Configuration is not activated.", HttpStatus.BAD_REQUEST);
			}

			String currentProdStatus = entity.getMoveToProdStatus() != null ? entity.getMoveToProdStatus().trim() : "";
			if (MoveToProdStatus.MOVED_TO_PRODUCTION.getValue().equals(currentProdStatus)) {
				throw new SPRuntimeError("Configuration is already moved to production.", HttpStatus.BAD_REQUEST);
			}
			if (MoveToProdStatus.STOPPED_IN_PRODUCTION.getValue().equals(currentProdStatus)) {
				throw new SPRuntimeError("Suspended configuration cannot be moved to production directly. Activate or modify it first.", HttpStatus.BAD_REQUEST);
			}
			if (!MoveToProdStatus.DEFAULT.getValue().equals(currentProdStatus) &&
				!MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue().equals(currentProdStatus)) {
				throw new SPRuntimeError("Configuration cannot be moved to production in current state.", HttpStatus.BAD_REQUEST);
			}
			
			List<ServiceEsignConfiguration> serviceEsignConfigs = serviceEsignRepository.findByEsignTemplateId(entity.getId());
			
			if (serviceEsignConfigs.isEmpty()) {
				throw new SPRuntimeError("Configuration not mapped to any service", HttpStatus.BAD_REQUEST);
			}
			
			EsignTemplateDTO esignTemplate = new EsignTemplateDTO();
			esignTemplate.setActive(entity.getActive());
			esignTemplate.setTemplateId(entity.getId());
			esignTemplate.setTemplateJson(entity.getTemplateJson());
			esignTemplate.setTemplateName(entity.getTemplateName());
			esignTemplate.setTenantId(entity.getTenantId());

			List<EsignConfigRequestDTO> configRequestDTOs = new ArrayList<>();
			EsignConfigRequestDTO configRequestDTO = new EsignConfigRequestDTO();
			configRequestDTO.setTemplates(List.of(esignTemplate));
			configRequestDTOs.add(configRequestDTO);
			
			try {
				integratedPluginsProdPushService.pushToProd(userSessionDetails.getTenantId(), moveToProdToken, configRequestDTOs, request);
				log.info("e-sign config pushed for entity {}", entity.getId());
			} catch (FeignException e) {
				log.error("e-sign service call failed. status={}, body={}", e.status(), e.contentUTF8(), e);
				throw new SPRuntimeError("Failed to sync e-sign configuration", HttpStatus.BAD_GATEWAY);
			}

			entity.setMoveToProdStatus(MoveToProdStatus.MOVED_TO_PRODUCTION.getValue());
			entity.setMoveToProdByUser(userSessionDetails.getUserID());
			entity.setMoveToProdOn(new Date());
			templateRepository.save(entity);

		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				log.error("Error : ESign Configuration move on production :", ex);
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

	}
	
	@Override
	@Transactional
	public void suspend(Long templateId, UserSessionDTO userSessionDetails, HttpServletRequest request) {
		validateUser(userSessionDetails);
		if (templateId == null) {
			throw new SPRuntimeError("Template ID parameter is mandatory", HttpStatus.BAD_REQUEST);
		}
		try {
			EsignTemplate entity = templateRepository.findByIdAndTenantId(templateId, userSessionDetails.getTenantId())
					.orElseThrow(() -> new SPRuntimeError("Configuration not found with ID: " + templateId, HttpStatus.BAD_REQUEST));

			if (!Boolean.TRUE.equals(entity.getActive())) {
				throw new SPRuntimeError("Configuration is not activated.", HttpStatus.BAD_REQUEST);
			}

			String currentProdStatus = entity.getMoveToProdStatus() != null ? entity.getMoveToProdStatus().trim() : "";
			if (MoveToProdStatus.STOPPED_IN_PRODUCTION.getValue().equals(currentProdStatus)) {
				throw new SPRuntimeError("Configuration is already suspended.", HttpStatus.BAD_REQUEST);
			}
			if (!MoveToProdStatus.MOVED_TO_PRODUCTION.getValue().equals(currentProdStatus) &&
				!MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue().equals(currentProdStatus)) {
				throw new SPRuntimeError("Configuration is not online in production.", HttpStatus.BAD_REQUEST);
			}

			List<ServiceEsignConfiguration> serviceEsignConfigs = serviceEsignRepository.findByEsignTemplateId(entity.getId());
			
			if (serviceEsignConfigs.isEmpty()) {
				throw new SPRuntimeError("Configuration not mapped to any service", HttpStatus.BAD_REQUEST);
			}
			
			EsignTemplateDTO esignTemplate = new EsignTemplateDTO();
			esignTemplate.setActive(false);
			esignTemplate.setTemplateId(entity.getId());
			esignTemplate.setTemplateJson(entity.getTemplateJson());
			esignTemplate.setTemplateName(entity.getTemplateName());
			esignTemplate.setTenantId(entity.getTenantId());

			List<EsignConfigRequestDTO> configRequestDTOs = new ArrayList<>();
			EsignConfigRequestDTO configRequestDTO = new EsignConfigRequestDTO();
			configRequestDTO.setTemplates(List.of(esignTemplate));
			configRequestDTOs.add(configRequestDTO);
			
			try {
				integratedPluginsProdPushService.pushToProd(userSessionDetails.getTenantId(), moveToProdToken, configRequestDTOs, request);
				log.info("e-sign suspend config pushed for entity {}", entity.getId());
			} catch (FeignException e) {
				log.error("e-sign service call failed. status={}, body={}", e.status(), e.contentUTF8(), e);
				throw new SPRuntimeError("Failed to sync e-sign configuration", HttpStatus.BAD_GATEWAY);
			}
			
			entity.setMoveToProdStatus(MoveToProdStatus.STOPPED_IN_PRODUCTION.getValue());
			entity.setMoveToProdByUser(userSessionDetails.getUserID());
			entity.setMoveToProdOn(new Date());
			templateRepository.save(entity);
			
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				log.error("Error : ESign Configuration suspend on production :", ex);
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
		
	}

	@Override
	@Transactional
	public void deleteTemplate(Long templateId, UserSessionDTO user) {
		validateUser(user);
		if (templateId == null) {
			throw new SPRuntimeError("Template ID parameter is mandatory", HttpStatus.BAD_REQUEST);
		}

		EsignTemplate template = templateRepository.findByIdAndTenantId(templateId, user.getTenantId())
				.orElseThrow(() -> new SPRuntimeError("eSign template not found with ID: " + templateId, HttpStatus.NOT_FOUND));

		if (Boolean.TRUE.equals(template.getActive())) {
			throw new SPRuntimeError("Active configuration cannot be deleted. Deactivate it first.", HttpStatus.BAD_REQUEST);
		}

		String prodStatus = template.getMoveToProdStatus();
		if (prodStatus != null && !prodStatus.trim().isEmpty()) {
			throw new SPRuntimeError("Configuration has already been moved to production and cannot be deleted.", HttpStatus.BAD_REQUEST);
		}

		boolean isUsedInService = serviceEsignRepository.existsByEsignTemplateId(templateId);
		if (isUsedInService) {
			throw new SPRuntimeError("Cannot delete eSign template because it is associated with one or more services.", HttpStatus.CONFLICT);
		}

		templateRepository.delete(template);
		log.info("eSign template deleted successfully. id={}, tenant={}", templateId, user.getTenantId());
	}
}
