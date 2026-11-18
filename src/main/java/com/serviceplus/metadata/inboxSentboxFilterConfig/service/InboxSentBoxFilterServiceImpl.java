package com.serviceplus.metadata.inboxSentboxFilterConfig.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.serviceplus.metadata.dto.LabelValue;
import com.serviceplus.metadata.dto.UserSessionDTO;
import com.serviceplus.metadata.entity.ServiceProcessFlow;
import com.serviceplus.metadata.enums.MoveToProdStatus;
import com.serviceplus.metadata.exception.SPRuntimeError;
import com.serviceplus.metadata.inboxSentboxFilterConfig.dto.FilterJSONDTO;
import com.serviceplus.metadata.inboxSentboxFilterConfig.dto.FilterKafkaMessage;
import com.serviceplus.metadata.inboxSentboxFilterConfig.dto.InboxSentBoxFilterDTO;
import com.serviceplus.metadata.inboxSentboxFilterConfig.dto.InboxSentBoxFilterListDTO;
import com.serviceplus.metadata.inboxSentboxFilterConfig.dto.InboxSentBoxFilterPageDTO;
import com.serviceplus.metadata.inboxSentboxFilterConfig.entity.InboxSentBoxFilter;
import com.serviceplus.metadata.inboxSentboxFilterConfig.enums.FilterType;
import com.serviceplus.metadata.inboxSentboxFilterConfig.repository.InboxSentBoxFilterRepository;
import com.serviceplus.metadata.prod.service.FilterConfigProdPushService;
import com.serviceplus.metadata.repository.IProcessFlowRepository;
import com.serviceplus.metadata.service.ServiceMetadataService;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class InboxSentBoxFilterServiceImpl implements InboxSentBoxFilterService {

	private static final Logger LOGGER = LogManager.getLogger(InboxSentBoxFilterServiceImpl.class);
	
	@Value("${move.to.prod.token}")
    private String moveToProdToken;
	
	private final InboxSentBoxFilterRepository inboxSentBoxFilterRepository;
	private final IProcessFlowRepository flowRepository;
	private final ServiceMetadataService serviceMetadataService;
	private final FilterConfigProdPushService filterConfigProdPushService;

	public InboxSentBoxFilterServiceImpl(InboxSentBoxFilterRepository inboxSentBoxFilterRepository, IProcessFlowRepository flowRepository, ServiceMetadataService serviceMetadataService, FilterConfigProdPushService filterConfigProdPushService) {
		this.inboxSentBoxFilterRepository = inboxSentBoxFilterRepository;
		this.flowRepository = flowRepository;
		this.serviceMetadataService = serviceMetadataService;
		this.filterConfigProdPushService = filterConfigProdPushService;
	}
	
	@Override
	public ResponseEntity<?> moveToProd(Long id, UserSessionDTO userSessionDetails, HttpServletRequest request) {
		try {
			InboxSentBoxFilter entity = inboxSentBoxFilterRepository.findById(id)
					.orElseThrow(() -> new SPRuntimeError("Inbox/Sent Box filter configuration not found.", HttpStatus.BAD_REQUEST));
			
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

			FilterKafkaMessage filterKafkaMessage = serviceMetadataService.buildInboxSentBoxFilterConfigKafkaMessage(List.of(entity));
    		
			try {
				filterConfigProdPushService.pushToProd(userSessionDetails.getTenantId(), moveToProdToken, List.of(filterKafkaMessage), request);
			    LOGGER.info("Filter config pushed for entity {}", entity.getId());
			} catch (FeignException e) {
				LOGGER.error("Filter service call failed. status={}, body={}", e.status(), e.contentUTF8(), e);
			    throw new SPRuntimeError("Failed to sync filter configuration", HttpStatus.BAD_GATEWAY);
			}
			
			entity.setMoveToProdStatus(MoveToProdStatus.MOVED_TO_PRODUCTION.getValue());
			entity.setMoveToProdByUser(userSessionDetails.getUserID());
			entity.setMoveToProdOn(new Date());
			inboxSentBoxFilterRepository.save(entity);

			return ResponseEntity.ok(Map.of("id", id, "message", "Configuration Move to Production Successfully."));
			
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				LOGGER.error("Error : Filter Configuration move to production :", ex);
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
	}

	@Override
	public ResponseEntity<?> suspend(Long id, UserSessionDTO userSessionDetails, HttpServletRequest request) {
		try {
			InboxSentBoxFilter entity = inboxSentBoxFilterRepository.findById(id)
					.orElseThrow(() -> new SPRuntimeError("Inbox/Sent Box filter configuration not found.", HttpStatus.BAD_REQUEST));
			
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

			entity.setActive(false);
			FilterKafkaMessage filterKafkaMessage = serviceMetadataService.buildInboxSentBoxFilterConfigKafkaMessage(List.of(entity));

			try {
				filterConfigProdPushService.pushToProd(userSessionDetails.getTenantId(), moveToProdToken, List.of(filterKafkaMessage), request);
				LOGGER.info("Filter suspend config pushed for entity {}", entity.getId());
			} catch (FeignException e) {
				LOGGER.error("Filter service call failed. status={}, body={}", e.status(), e.contentUTF8(), e);
				throw new SPRuntimeError("Failed to sync filter configuration", HttpStatus.BAD_GATEWAY);
			}

			entity.setActive(true);
			entity.setMoveToProdStatus(MoveToProdStatus.STOPPED_IN_PRODUCTION.getValue());
			entity.setMoveToProdByUser(userSessionDetails.getUserID());
			entity.setMoveToProdOn(new Date());
			inboxSentBoxFilterRepository.save(entity);

			return ResponseEntity.ok(Map.of("id", id, "message", "Configuration Suspend Successfully."));

		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				LOGGER.error("Error : Filter Configuration suspend on production :", ex);
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
	}
	
	@Override
	public ResponseEntity<?> getAllConfigurations(Integer page, Integer size, UserSessionDTO userSessionDTO) {
		try {
			if (page == null || page < 0) { page = 0; }

	        if (size == null || size <= 0) { size = 10; }
	        if (size > 100) { size = 100; }
			Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
	        
			Page<InboxSentBoxFilter> filterPage = inboxSentBoxFilterRepository.findByUserId(userSessionDTO.getUserID(), pageable);
			List<InboxSentBoxFilterListDTO> records = filterPage.getContent().stream().map(this::prepareListDTO).collect(Collectors.toList());
			InboxSentBoxFilterPageDTO response = new InboxSentBoxFilterPageDTO();

	        response.setRecords(records);
	        response.setPage(filterPage.getNumber());
	        response.setSize(filterPage.getSize());
	        response.setTotalRecords(filterPage.getTotalElements());
	        response.setTotalPages(filterPage.getTotalPages());
	        response.setFirst(filterPage.isFirst());
	        response.setLast(filterPage.isLast());

	        return ResponseEntity.ok(response);
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw (SPRuntimeError) ex;
			}
			LOGGER.error("Service : Error while fetching all inbox/sent box filter configurations", ex);
			throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<?> getConfiguration(Long id, UserSessionDTO userSessionDTO) {
		try {
			InboxSentBoxFilter inboxSentBoxFilter = inboxSentBoxFilterRepository
					.findById(id)
					.orElseThrow(() -> new SPRuntimeError("Inbox/Sent Box filter configuration not found.",
							HttpStatus.BAD_REQUEST));
			
			InboxSentBoxFilterDTO dto = new InboxSentBoxFilterDTO();

			dto.setId(inboxSentBoxFilter.getId());
			dto.setName(inboxSentBoxFilter.getFilterName());
			Character outputTypeValue = inboxSentBoxFilter.getFilterType();
			FilterType outputType = FilterType.fromValue(outputTypeValue.toString());
			LabelValue outputTypeLabel = new LabelValue();
			outputTypeLabel.setLabel(outputType.name());
			outputTypeLabel.setValue(outputType.getValue());
			dto.setFilterType(outputTypeLabel);
			dto.setTargetTask(inboxSentBoxFilter.getTasks());
			FilterJSONDTO filterJSON = inboxSentBoxFilter.getFilterJSON();
			if(filterJSON != null) {
				dto.setService(filterJSON.getService());
				dto.setInputFilters(filterJSON.getFilterInput());
				dto.setOutputFilters(filterJSON.getFilterOutput());
			}

			return ResponseEntity.ok(dto);

		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw (SPRuntimeError) ex;
			}
			LOGGER.error("Service : Error while fetching inbox/sent box filter configuration for id: {}", id, ex);
			throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<?> upsertConfiguration(InboxSentBoxFilterDTO inboxSentBoxFilterDTO, UserSessionDTO userSessionDTO) {
		try {
			InboxSentBoxFilter inboxSentBoxFilter = null;
			String filterName = inboxSentBoxFilterDTO.getName().trim();
			Long serviceId = inboxSentBoxFilterDTO.getService().getValue() != null ? Long.valueOf(inboxSentBoxFilterDTO.getService().getValue().toString()) : null;
			validateFilterName(filterName, inboxSentBoxFilterDTO.getId());
			validateServiceTask(serviceId, inboxSentBoxFilterDTO.getTargetTask(), inboxSentBoxFilterDTO.getId());
			ServiceProcessFlow serviceProcessFlow = flowRepository.findByServiceId(serviceId.intValue());
			if (serviceProcessFlow == null) {
				throw new SPRuntimeError("Invalid Service Id", HttpStatus.BAD_REQUEST);
			}
			if (inboxSentBoxFilterDTO.getId() == null) {
				inboxSentBoxFilter = new InboxSentBoxFilter();
				inboxSentBoxFilter.setTenantId(userSessionDTO.getTenantId());
				inboxSentBoxFilter.setCrDate(new Date());
				inboxSentBoxFilter.setUserId(userSessionDTO.getUserID());
				inboxSentBoxFilter.setActive(false);
				inboxSentBoxFilter.setMoveToProdStatus(MoveToProdStatus.DEFAULT.getValue());
				
			} else {
				inboxSentBoxFilter = inboxSentBoxFilterRepository
						.findByIdAndUserId(inboxSentBoxFilterDTO.getId(), userSessionDTO.getUserID())
						.orElseThrow(() -> new SPRuntimeError("Inbox/Sent Box filter configuration not found.",
								HttpStatus.BAD_REQUEST));
				if (Boolean.TRUE.equals(inboxSentBoxFilter.getActive())) {
					throw new SPRuntimeError("Active configuration cannot be edited. Deactivate it first.", HttpStatus.BAD_REQUEST);
				}

				String currentOnProd = inboxSentBoxFilter.getMoveToProdStatus();
				if (currentOnProd != null && !currentOnProd.trim().isEmpty()) {
					inboxSentBoxFilter.setMoveToProdStatus(MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue());
				} else {
					inboxSentBoxFilter.setMoveToProdStatus(MoveToProdStatus.DEFAULT.getValue());
				}
			}
			
			inboxSentBoxFilter.setServiceId(serviceId);
			inboxSentBoxFilter.setBaseServiceId(serviceId / 10000);
			inboxSentBoxFilter.setTasks(inboxSentBoxFilterDTO.getTargetTask());
			inboxSentBoxFilter.setUpDate(new Date());
			
			FilterJSONDTO filterJSONDTO = new FilterJSONDTO();
			filterJSONDTO.setFilterInput(inboxSentBoxFilterDTO.getInputFilters());
			filterJSONDTO.setFilterOutput(inboxSentBoxFilterDTO.getOutputFilters());
			filterJSONDTO.setService(inboxSentBoxFilterDTO.getService());
			
			inboxSentBoxFilter.setFilterJSON(filterJSONDTO);
			inboxSentBoxFilter.setFilterName(filterName);

			String outputTypeValue = inboxSentBoxFilterDTO.getFilterType().getValue().toString();

			FilterType outputType;
			try {
				outputType = FilterType.fromValue(outputTypeValue);
			} catch (IllegalArgumentException ex) {
				throw new SPRuntimeError("Invalid outputType: " + outputTypeValue, HttpStatus.BAD_REQUEST);
			}

			inboxSentBoxFilter.setFilterType(outputType.getValue().charAt(0));
			InboxSentBoxFilter saveRecords = inboxSentBoxFilterRepository.save(inboxSentBoxFilter);

			String message = inboxSentBoxFilterDTO.getId() == null
								? "Inbox/Sent Box filter configuration created successfully."
								: "Inbox/Sent Box filter configuration updated successfully.";
			
			return ResponseEntity.ok(Map.of("message",message, "id", saveRecords.getId()));
			
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw (SPRuntimeError) ex;
			}
			LOGGER.error("Service : Error while saving/updating inbox/sent box filter configuration", ex);
			throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<?> changeStatus( Long id, Boolean isActive, UserSessionDTO userSessionDTO) {
		try {
			InboxSentBoxFilter inboxSentBoxFilter = inboxSentBoxFilterRepository.findById(id).orElseThrow(
					() -> new SPRuntimeError("Inbox/Sent Box filter configuration not found.", HttpStatus.BAD_REQUEST));

			Boolean currentStatus = inboxSentBoxFilter.getActive();
			if (Boolean.TRUE.equals(isActive)) {
				if (Boolean.TRUE.equals(currentStatus)) {
					throw new SPRuntimeError("Inbox/Sent Box filter configuration is already active.", HttpStatus.BAD_REQUEST);
				}
	            inboxSentBoxFilter.setActive(true);
			}
			else {
				if (Boolean.FALSE.equals(currentStatus)) {
					throw new SPRuntimeError("Inbox/Sent Box filter configuration is already inactive.", HttpStatus.BAD_REQUEST);
				}
		        inboxSentBoxFilter.setActive(false);
				String prodStatus = inboxSentBoxFilter.getMoveToProdStatus();
				if (prodStatus != null && !prodStatus.trim().isEmpty()) {
					inboxSentBoxFilter.setMoveToProdStatus(MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue());
				}
			}
			inboxSentBoxFilter.setUpDate(new Date());
			serviceMetadataService.rebuildServiceMetaDataForInboxSentBoxFilter(inboxSentBoxFilter.getServiceId().intValue(),inboxSentBoxFilter.getFilterJSON());
			
			inboxSentBoxFilterRepository.save(inboxSentBoxFilter);
			String message = Boolean.TRUE.equals(isActive)
					? "Inbox/Sent Box filter configuration activated successfully."
					: "Inbox/Sent Box filter configuration deactivated successfully.";
			return ResponseEntity.ok(Map.of("message", message, "id", inboxSentBoxFilter.getId()));
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw (SPRuntimeError) ex;
			}
			LOGGER.error("Service : Error while changing status of inbox/sent box filter configuration for id: {}", id, ex);
			throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<?> deleteConfiguration(Long id, UserSessionDTO userSessionDTO) {
		try {
			InboxSentBoxFilter inboxSentBoxFilter = inboxSentBoxFilterRepository
					.findById(id)
					.orElseThrow(() -> new SPRuntimeError("Inbox/Sent Box filter configuration not found.",
							HttpStatus.BAD_REQUEST));

			if (Boolean.TRUE.equals(inboxSentBoxFilter.getActive())) {
				throw new SPRuntimeError("Active configuration cannot be deleted. Deactivate it first.", HttpStatus.BAD_REQUEST);
			}

			String prodStatus = inboxSentBoxFilter.getMoveToProdStatus();
			if (prodStatus != null && !prodStatus.trim().isEmpty()) {
				throw new SPRuntimeError("Configuration has already been moved to production and cannot be deleted.", HttpStatus.BAD_REQUEST);
			}

			inboxSentBoxFilterRepository.delete(inboxSentBoxFilter);
			
			serviceMetadataService.rebuildServiceMetaDataForInboxSentBoxFilter(inboxSentBoxFilter.getServiceId().intValue(),inboxSentBoxFilter.getFilterJSON());
			
			return ResponseEntity.ok(Map.of("message", "Inbox/Sent Box filter configuration deleted successfully.", "id", inboxSentBoxFilter.getId()));
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw (SPRuntimeError) ex;
			}
			LOGGER.error("Service : Error while deleting inbox/sent box filter configuration for id: {}", id, ex);
			throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private void validateFilterName(String filterName, Long id) {
		if (id == null) {
			if (inboxSentBoxFilterRepository.existsByFilterName(filterName)) {
				throw new SPRuntimeError("Filter name already exists.", HttpStatus.BAD_REQUEST);
			}
		} else {
			if (inboxSentBoxFilterRepository.existsByFilterNameAndIdNot(filterName, id)) {
				throw new SPRuntimeError("Filter name already exists.", HttpStatus.BAD_REQUEST);
			}
		}
	}
	
	private void validateServiceTask(Long serviceId, List<LabelValue> targetTask, Long id) {
		if (targetTask == null || targetTask.isEmpty()) {
			return;
		}

		for (LabelValue task : targetTask) {
			if (task == null || task.getValue() == null) {
				continue;
			}
			String taskId = task.getValue().toString();
			boolean exists;
			if (id == null) {
				exists = inboxSentBoxFilterRepository.existsByServiceIdAndTaskId(serviceId, taskId);
			} else {
				exists = inboxSentBoxFilterRepository.existsByServiceIdAndTaskIdAndIdNot(serviceId, taskId, id);
			}
			if (exists) {
				throw new SPRuntimeError("A filter configuration already exists for this service and task: " + taskId, HttpStatus.BAD_REQUEST);
			}
		}
	}
	
	private InboxSentBoxFilterListDTO prepareListDTO(InboxSentBoxFilter entity) {
		InboxSentBoxFilterListDTO dto = new InboxSentBoxFilterListDTO();
		dto.setId(entity.getId());
		dto.setFilterName(entity.getFilterName());
		dto.setTargetTask(entity.getTasks());
		FilterType outputType = FilterType.fromValue(entity.getFilterType().toString());
		dto.setOutputType(outputType.name());
		if (entity.getFilterJSON() != null && entity.getFilterJSON().getService() != null) {
			dto.setServiceName(entity.getFilterJSON().getService().getLabel());
		}
		String onProd = entity.getMoveToProdStatus() != null ? entity.getMoveToProdStatus() : "";
		dto.setOnProd(onProd);
		dto.setActive(entity.getActive());
		dto.setStatus(computeDisplayStatus(entity.getActive(), onProd));
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

}
