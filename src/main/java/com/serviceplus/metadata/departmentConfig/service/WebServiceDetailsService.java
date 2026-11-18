package com.serviceplus.metadata.departmentConfig.service;

import static com.serviceplus.metadata.utility.CommonUtil.entityToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.serviceplus.metadata.departmentConfig.dto.WebServiceDetailsResponseDTO;
import com.serviceplus.metadata.departmentConfig.entity.WebServiceDetails;
import com.serviceplus.metadata.departmentConfig.repository.WebServiceRepository;
import com.serviceplus.metadata.dto.CreateUserDto;
import com.serviceplus.metadata.dto.ExternalSystemAuthReqDTO;
import com.serviceplus.metadata.dto.ExternalSystemAuthReqDTO.ApiDetail.ApiParameterDetail;
import com.serviceplus.metadata.dto.ExternalSystemDTO;
import com.serviceplus.metadata.dto.ExternalSystemDTO.ApiDetail;
import com.serviceplus.metadata.dto.ExternalSystemDefDTO;
import com.serviceplus.metadata.dto.ExternalSystemGeneralReqDTO;
import com.serviceplus.metadata.dto.ExternalSystemGeneralReqDTO.GeneralDetail;
import com.serviceplus.metadata.dto.ExternalSystemRegistrationDTO;
import com.serviceplus.metadata.dto.UserSessionDTO;
import com.serviceplus.metadata.entity.ExternalSystemRegistration;
import com.serviceplus.metadata.entity.ExternalSystemUser;
import com.serviceplus.metadata.enums.ExternalSystemParamType;
import com.serviceplus.metadata.enums.ExternalSystemRedirectMethodType;
import com.serviceplus.metadata.enums.MoveToProdStatus;
import com.serviceplus.metadata.exception.SPRuntimeError;
import com.serviceplus.metadata.feignClient.UserManagmentFeignClient;
import com.serviceplus.metadata.prod.service.ExternalSystemProdClient;
import com.serviceplus.metadata.repository.IExternalSystemRegistrationRepository;
import com.serviceplus.metadata.repository.IExternalSystemUserRepository;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class WebServiceDetailsService {

	private static final Logger LOGGER = LogManager.getLogger("webServiceDetailsService");
	
	@Value("${move.to.prod.token}")
    private String moveToProdToken;
	
    private final WebServiceRepository wsDetailRepository;
	private final IExternalSystemRegistrationRepository externalSysRegistrationRepo;
	private final UserManagmentFeignClient userManagementFeignClient;
	private final IExternalSystemUserRepository externalSystemUserRepository;
	private final ExternalSystemProdClient externalSystemProdClient;
    public WebServiceDetailsService(WebServiceRepository wsDetailRepository,
			IExternalSystemRegistrationRepository externalSysRegistrationRepo, UserManagmentFeignClient userManagementFeignClient, IExternalSystemUserRepository externalSystemUserRepository,
			ExternalSystemProdClient externalSystemProdClient) {
		super();
		this.wsDetailRepository = wsDetailRepository;
		this.externalSysRegistrationRepo = externalSysRegistrationRepo;
		this.userManagementFeignClient = userManagementFeignClient;
		this.externalSystemUserRepository = externalSystemUserRepository;
		this.externalSystemProdClient = externalSystemProdClient;
	}

	public WebServiceDetails saveWSDetail(WebServiceDetails template) {
        return wsDetailRepository.save(template);
    }

    public List<WebServiceDetailsResponseDTO> getByTenantId(String tenantId) {
    	List<WebServiceDetails> wsDetailList= wsDetailRepository.findByTenantId(tenantId);
    	List<WebServiceDetailsResponseDTO> responseDTO=new ArrayList<WebServiceDetailsResponseDTO>();
    	for(WebServiceDetails wsDetail:wsDetailList) {
    		WebServiceDetailsResponseDTO response= new WebServiceDetailsResponseDTO();
    		response.setId(wsDetail.getId());
    		response.setMethod(wsDetail.getMethod());
    		response.setTemplateName(wsDetail.getTemplateName());
    		responseDTO.add(response);
    	}
    	return responseDTO;
    }

    public WebServiceDetails getById(String id) {
        return wsDetailRepository.findById(id).orElse(null);
    }

	public List<WebServiceDetails> findByIdAndTenantId(String id,String tenantId) {
		return wsDetailRepository.findByIdAndTenantId(id,tenantId);
	}

	@Transactional
	public ResponseEntity<?> registerExternalSystem(UserSessionDTO userSessionDetails, ExternalSystemGeneralReqDTO externalSystemReqDTO) {
		try {
			ExternalSystemRegistration externalSystemRegistration;
			Long userID = userSessionDetails.getUserID();
			
			Integer departmentId = userSessionDetails.getEntityId();
			Integer locationId = userSessionDetails.getLocationId();
			
			generalValidation(externalSystemReqDTO);
			GeneralDetail generalDetail = externalSystemReqDTO.getGeneralDetail();
			
			if(Objects.isNull(externalSystemReqDTO.getClientId())) {
				externalSystemRegistration = new ExternalSystemRegistration();
				
				String clientId = "" + ThreadLocalRandom.current().nextInt(1000, 1000000);
				
				externalSystemRegistration.setClientId(clientId);
				externalSystemRegistration.setDepartmentId(departmentId);
				externalSystemRegistration.setLocationId(locationId);
				externalSystemRegistration.setCreatedBy(userID);
				externalSystemRegistration.setCreatedOn(new Date());
				externalSystemRegistration.setActive(false);
				externalSystemRegistration.setMoveToProdStatus(MoveToProdStatus.DEFAULT.getValue());
				
			}else {
				externalSystemRegistration = externalSysRegistrationRepo.findByClientId(externalSystemReqDTO.getClientId())
																		.orElseThrow(() -> new SPRuntimeError("Data not found", HttpStatus.BAD_REQUEST));
				if(!departmentId.equals(externalSystemRegistration.getDepartmentId()) || !locationId.equals(externalSystemRegistration.getLocationId())) {
					throw new SPRuntimeError("Unauthorized access", HttpStatus.UNAUTHORIZED);
				}
				if (Boolean.TRUE.equals(externalSystemRegistration.getActive())) {
					throw new SPRuntimeError("Active external system cannot be modified. Deactivate it first.", HttpStatus.BAD_REQUEST);
				}
				String currentOnProd = externalSystemRegistration.getMoveToProdStatus();
				if (currentOnProd != null && !currentOnProd.trim().isEmpty()) {
					externalSystemRegistration.setMoveToProdStatus(MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue());
				} else {
					externalSystemRegistration.setMoveToProdStatus(MoveToProdStatus.DEFAULT.getValue());
				}
				externalSystemRegistration.setModifiedBy(userID);
				externalSystemRegistration.setModifiedOn(new Date());
			}
			BeanUtils.copyProperties(generalDetail, externalSystemRegistration.getExternalSystemDetail().getGeneralDetail());
			externalSystemRegistration.setClientName(generalDetail.getClientName());
			
			ExternalSystemRegistration saveData = externalSysRegistrationRepo.save(externalSystemRegistration);
			//Create User a dedicated DEO user will be auto-created once an external system is registered 
			//by the Department Admin. 
			//The assigned role for this user will be “External System Data Entry Operator”. 
			//The credentials of this user, along with the generated Client ID, 
			//will be shared with the respective Department Admin.
			List<ExternalSystemUser> byExternalSystemRegId = externalSystemUserRepository.findByExternalSystemRegId(saveData.getId());
			if(byExternalSystemRegId.isEmpty()) {
				createDEOUserForExternalSystem(userSessionDetails,saveData);
			}
			return ResponseEntity.ok(Map.of("clientId", saveData.getClientId(),"message","Data Saved"));
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				ex.printStackTrace();
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
	}

	private void createDEOUserForExternalSystem(UserSessionDTO userSessionDetails, ExternalSystemRegistration saveData) {
		CreateUserDto dto = new CreateUserDto();

		dto.setCreatedRole(29);
		dto.setUserCount(1);
		dto.setCreateUserAs(null);

		dto.setlType("LD");
		dto.setlTypeId(null);

		dto.setApprovalRequired(false);
		dto.setAllOrSpecific(1);
        dto.setReferenceId(saveData.getClientId());

		dto.setState(userSessionDetails.getClcId());
		CreateUserDto.DepartmentDetails departmentDetails =new CreateUserDto.DepartmentDetails();

		departmentDetails.setId(userSessionDetails.getEntityId());
		departmentDetails.setSubOffice(userSessionDetails.getEntityLevelId());

		dto.setDepartmentDetails(departmentDetails);

	    ResponseEntity<?> response =userManagementFeignClient.createUser(entityToString(userSessionDetails), dto);
		
	}

	private void generalValidation(ExternalSystemGeneralReqDTO externalSystemReqDTO) {

		// General Detail Validation
		GeneralDetail generalDetail = externalSystemReqDTO.getGeneralDetail();
		String clientName = generalDetail.getClientName();
		ExternalSystemRegistration externalSystemRegistrationDB = externalSysRegistrationRepo.findByClientName(clientName).orElse(null);
		if (!Objects.isNull(externalSystemRegistrationDB) && !externalSystemRegistrationDB.getClientId().equals(externalSystemReqDTO.getClientId())) {
		    throw new SPRuntimeError("Client name already exists", HttpStatus.BAD_REQUEST);
		}

		Boolean redirectUrlFlag = generalDetail.getRedirectUrlFlag();
		if (redirectUrlFlag == null){
			throw new SPRuntimeError("Invalid redirect URL flag", HttpStatus.BAD_REQUEST);
		}

		if (redirectUrlFlag) {
			if (generalDetail.getRedirectUrlProd() == null || generalDetail.getRedirectUrlProd().isBlank()) {
				throw new SPRuntimeError("Redirect production URL cannot be blank.", HttpStatus.BAD_REQUEST);
			}

			if (generalDetail.getRedirectUrlStaging() == null || generalDetail.getRedirectUrlStaging().isBlank()) {
				throw new SPRuntimeError("Redirect staging URL cannot be blank.", HttpStatus.BAD_REQUEST);
			}

			String redirectMethod = generalDetail.getRedirectUrlMethodType();
			if (redirectMethod == null || redirectMethod.isBlank() || !ExternalSystemRedirectMethodType.isValid(redirectMethod)) {
				throw new SPRuntimeError("Invalid redirect method type.", HttpStatus.BAD_REQUEST);
			}

			if (generalDetail.getRedirectUrlParamFlag() == null || generalDetail.getRedirectUrlParamFlag().isBlank()) {
				throw new SPRuntimeError("Redirect URL param flag cannot be blank.", HttpStatus.BAD_REQUEST);
			}

			if (generalDetail.getRedirectionMsg() == null || generalDetail.getRedirectionMsg().isBlank()) {
				throw new SPRuntimeError("Redirect message cannot be blank.", HttpStatus.BAD_REQUEST);
			}

			if (generalDetail.getRedirectionLinkText() == null || generalDetail.getRedirectionLinkText().isBlank()) {
				throw new SPRuntimeError("Redirection link text cannot be blank.", HttpStatus.BAD_REQUEST);
			}

			if (generalDetail.getRedirectionMsgPosition() == null
					|| generalDetail.getRedirectionMsgPosition().isBlank()) {
				throw new SPRuntimeError("Redirection message position cannot be blank.", HttpStatus.BAD_REQUEST);
			}

			if (generalDetail.getRedirectionTime() == null || generalDetail.getRedirectionTime() <= 0) {
				throw new SPRuntimeError("Redirection time should be greater than zero.", HttpStatus.BAD_REQUEST);
			}
		}
	}

	public ResponseEntity<?> saveAuthDetail(UserSessionDTO userSessionDetails, ExternalSystemAuthReqDTO authReqDTO, Integer apiType) {
		try {
			
			com.serviceplus.metadata.dto.ExternalSystemAuthReqDTO.ApiDetail apiDetailCli = authReqDTO.getApiDetail();
			List<ApiParameterDetail> apiParameterDetails = apiDetailCli.getApiParameterDetails();
			for(ApiParameterDetail apiParameterDetail: apiParameterDetails) {
				if (apiParameterDetail.getParamType().isBlank() || !ExternalSystemParamType.isValid(apiParameterDetail.getParamType())) {
					throw new SPRuntimeError("Invalid param type.", HttpStatus.BAD_REQUEST);
				}
			}
			
			ExternalSystemRegistration systemRegistration = externalSysRegistrationRepo.findByClientId(authReqDTO.getClientId())
					.orElseThrow(() -> new SPRuntimeError("Data not found", HttpStatus.BAD_REQUEST));
			Long userId = userSessionDetails.getUserID();
			Integer departmentId = userSessionDetails.getEntityId();
			Integer locationId = userSessionDetails.getLocationId();
			
			if(!departmentId.equals(systemRegistration.getDepartmentId()) || !locationId.equals(systemRegistration.getLocationId())) {
				throw new SPRuntimeError("Unauthorized access", HttpStatus.UNAUTHORIZED);
			}
			
			if (Boolean.TRUE.equals(systemRegistration.getActive())) {
				throw new SPRuntimeError("Active external system cannot be modified. Deactivate it first.", HttpStatus.BAD_REQUEST);
			}
			
			ExternalSystemDTO externalSystemDetailDB = systemRegistration.getExternalSystemDetail();
			List<ApiDetail> apiDetails = externalSystemDetailDB.getApiDetails();
			ApiDetail apiDetailDb = null;
			
			if(apiDetails.size() == 0 && !apiType.equals(2) && !apiType.equals(3)) {
				apiDetailDb = new ApiDetail();
				apiDetailDb.setApiType(apiType);
			}else {
				for(ApiDetail apiDetail : apiDetails) {
					Integer apiTypeDB = apiDetail.getApiType();
					if(apiTypeDB.equals(apiType)){
						apiDetailDb = apiDetail;
						apiDetails.remove(apiDetail);
						break;
					}else if(apiTypeDB.equals(2) && apiType.equals(3)){
						apiDetailDb = apiDetail;
						apiDetailDb.setApiProdUrl(apiDetailCli.getApiProdUrl());
						apiDetailDb.setApiStagingUrl(apiDetailCli.getApiStagingUrl());
						apiDetailDb.setDraftConfig(true);
						String currentOnProd = systemRegistration.getMoveToProdStatus();
						if (currentOnProd != null && !currentOnProd.trim().isEmpty()) {
							systemRegistration.setMoveToProdStatus(MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue());
						} else {
							systemRegistration.setMoveToProdStatus(MoveToProdStatus.DEFAULT.getValue());
						}
						systemRegistration.setModifiedBy(userId);
						systemRegistration.setModifiedOn(new Date());
						externalSysRegistrationRepo.save(systemRegistration);
						return ResponseEntity.ok(Map.of("clientId", systemRegistration.getClientId(),"message","Data Saved"));
					}
				}
			}
			
			if(apiDetails.size() > 0 && (apiType.equals(2) || apiType.equals(3))) {
				apiDetailDb = new ApiDetail();
				apiDetailDb.setApiType(2);
			}
			
			systemRegistration.setModifiedBy(userId);
			systemRegistration.setModifiedOn(new Date());
			String currentOnProd = systemRegistration.getMoveToProdStatus();
			if (currentOnProd != null && !currentOnProd.trim().isEmpty()) {
				systemRegistration.setMoveToProdStatus(MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue());
			} else {
				systemRegistration.setMoveToProdStatus(MoveToProdStatus.DEFAULT.getValue());
			}
			
			BeanUtils.copyProperties(apiDetailCli,apiDetailDb);
			if (apiDetailCli.getApiParameterDetails() != null && !apiDetailCli.getApiParameterDetails().isEmpty()) {

			    List<ExternalSystemDTO.ApiDetail.ApiParameterDetail> paramList = new ArrayList<>();

			    for (ExternalSystemAuthReqDTO.ApiDetail.ApiParameterDetail src : apiDetailCli.getApiParameterDetails()) {
			        ExternalSystemDTO.ApiDetail.ApiParameterDetail target = new ExternalSystemDTO.ApiDetail.ApiParameterDetail();
			        BeanUtils.copyProperties(src, target);
			        paramList.add(target);
			    }
			    apiDetailDb.setApiParameterDetails(paramList);
			}
			apiDetails.add(apiDetailDb);
			externalSystemDetailDB.setApiDetails(apiDetails);
			systemRegistration.setExternalSystemDetail(externalSystemDetailDB);

			externalSysRegistrationRepo.save(systemRegistration);
			
			return ResponseEntity.ok(Map.of("clientId", systemRegistration.getClientId(),"message","Data Saved"));
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				ex.printStackTrace();
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
	}

	public ResponseEntity<?> getAllExternalSystem(UserSessionDTO userSessionDetails, Boolean active) {
		try {
			List<ExternalSystemDefDTO> result = new ArrayList<>();

			Integer departmentId = userSessionDetails.getEntityId();
			Integer locationId = userSessionDetails.getLocationId();

			List<ExternalSystemRegistration> allRecords;

			if (Boolean.TRUE.equals(active)) {
				allRecords = externalSysRegistrationRepo.findByDepartmentIdAndActive(departmentId, true);
			} else {
				allRecords = externalSysRegistrationRepo.findByDepartmentIdAndLocationId(departmentId, locationId);
			}

			for (ExternalSystemRegistration externalSystemRegistration : allRecords) {

				ExternalSystemDefDTO defDTO = new ExternalSystemDefDTO();

				defDTO.setClientId(externalSystemRegistration.getClientId());
				defDTO.setClientName(externalSystemRegistration.getClientName());

				Boolean isActive = Boolean.TRUE.equals(externalSystemRegistration.getActive());

				defDTO.setActive(isActive);
				String onProd = externalSystemRegistration.getMoveToProdStatus() != null ? externalSystemRegistration.getMoveToProdStatus() : "";
				defDTO.setOnProd(onProd);
				defDTO.setStatus(computeDisplayStatus(isActive, onProd));

				result.add(defDTO);
			}

			return ResponseEntity.ok(result);

		} catch (Exception ex) {

			if (ex instanceof SPRuntimeError) {
				throw (SPRuntimeError) ex;
			}

			throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public ResponseEntity<?> getExternalSystem(UserSessionDTO userSessionDetails, String clientId, Integer tabId) {
		try {
			Optional<ExternalSystemRegistration> systemRegistration = externalSysRegistrationRepo.findByClientId(clientId);
			Integer departmentId = userSessionDetails.getEntityId();
			Integer locationId = userSessionDetails.getLocationId();
			if(systemRegistration.isPresent()) {
				ExternalSystemRegistration externalSystemRegistration = systemRegistration.get();
				if (!departmentId.equals(externalSystemRegistration.getDepartmentId())
						|| !locationId.equals(externalSystemRegistration.getLocationId())) {
					throw new SPRuntimeError("Unauthorized access", HttpStatus.UNAUTHORIZED);
				}
				
				if(tabId.equals(0)) {
					ExternalSystemGeneralReqDTO externalSystemGeneralReqDTO = new ExternalSystemGeneralReqDTO();
					externalSystemGeneralReqDTO.setClientId(clientId);
					BeanUtils.copyProperties(externalSystemRegistration.getExternalSystemDetail().getGeneralDetail(),externalSystemGeneralReqDTO.getGeneralDetail());
					return ResponseEntity.ok(externalSystemGeneralReqDTO);
				}else if(tabId.equals(1) || tabId.equals(2) || tabId.equals(3)) {
					ExternalSystemAuthReqDTO externalSystemAuthReqDTO = new ExternalSystemAuthReqDTO();
					externalSystemAuthReqDTO.setClientId(clientId);
					List<ApiDetail> apiDetails = externalSystemRegistration.getExternalSystemDetail().getApiDetails();
					for(ApiDetail apiDetail: apiDetails) {
						if(apiDetail.getApiType().equals(1) && tabId.equals(1)) {
							BeanUtils.copyProperties(apiDetail, externalSystemAuthReqDTO.getApiDetail());
							List<ExternalSystemAuthReqDTO.ApiDetail.ApiParameterDetail> paramList = new ArrayList<>();
							if (apiDetail.getApiParameterDetails() != null) {
							    for (ExternalSystemDTO.ApiDetail.ApiParameterDetail dbParam : apiDetail.getApiParameterDetails()) {
							        ExternalSystemAuthReqDTO.ApiDetail.ApiParameterDetail dtoParam = new ExternalSystemAuthReqDTO.ApiDetail.ApiParameterDetail();
							        BeanUtils.copyProperties(dbParam, dtoParam);
							        paramList.add(dtoParam);
							    }
							}

							externalSystemAuthReqDTO.getApiDetail().setApiParameterDetails(paramList);
							break;
						}
						if(apiDetail.getApiType().equals(2) && tabId.equals(2)){
							apiDetail.setApiProdUrl(null);
							apiDetail.setApiStagingUrl(null);
							BeanUtils.copyProperties(apiDetail, externalSystemAuthReqDTO.getApiDetail());
							List<ExternalSystemAuthReqDTO.ApiDetail.ApiParameterDetail> paramList = new ArrayList<>();
							if (apiDetail.getApiParameterDetails() != null) {
							    for (ExternalSystemDTO.ApiDetail.ApiParameterDetail dbParam : apiDetail.getApiParameterDetails()) {
							        ExternalSystemAuthReqDTO.ApiDetail.ApiParameterDetail dtoParam = new ExternalSystemAuthReqDTO.ApiDetail.ApiParameterDetail();
							        BeanUtils.copyProperties(dbParam, dtoParam);
							        paramList.add(dtoParam);
							    }
							}
							externalSystemAuthReqDTO.getApiDetail().setApiParameterDetails(paramList);
							break;
						}
						
						if(apiDetail.getApiType().equals(2) && tabId.equals(3)) {
							BeanUtils.copyProperties(apiDetail, externalSystemAuthReqDTO.getApiDetail());
							break;
						}
					}
					return ResponseEntity.ok(externalSystemAuthReqDTO);	
				}else {
					throw new SPRuntimeError("Invalid tab.", HttpStatus.BAD_REQUEST);
				}
			}
			return ResponseEntity.ok(Collections.emptyMap());
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				ex.printStackTrace();
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
	}

	public ResponseEntity<?> changeStatus(UserSessionDTO userSessionDetails, String clientId) {
		try {
			ExternalSystemRegistration systemRegistration = externalSysRegistrationRepo.findByClientId(clientId)
					.orElseThrow(() -> new SPRuntimeError("Data not found", HttpStatus.BAD_REQUEST));
			Integer departmentId = userSessionDetails.getEntityId();
			Integer locationId = userSessionDetails.getLocationId();
			
			if(!departmentId.equals(systemRegistration.getDepartmentId()) || !locationId.equals(systemRegistration.getLocationId())) {
				throw new SPRuntimeError("Unauthorized access", HttpStatus.UNAUTHORIZED);
			}
			
			Boolean currentStatus = systemRegistration.getActive();
			
			if (Boolean.FALSE.equals(currentStatus)) {
				if(systemRegistration.getExternalSystemDetail().getApiDetails().size() < 2 ) {					
					throw new SPRuntimeError("API not configured!",  HttpStatus.BAD_REQUEST);
				}
				systemRegistration.setActive(true);
			} else if (Boolean.TRUE.equals(currentStatus)) {
				systemRegistration.setActive(false);
				String prodStatus = systemRegistration.getMoveToProdStatus();
				if (prodStatus != null && !prodStatus.trim().isEmpty()) {
					systemRegistration.setMoveToProdStatus(MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue());
				}
			} else {
				throw new SPRuntimeError("Something went wrong", HttpStatus.BAD_REQUEST);
			}
			systemRegistration.setModifiedBy(userSessionDetails.getUserID());
			systemRegistration.setModifiedOn(new Date());
			externalSysRegistrationRepo.save(systemRegistration);
			String message = Boolean.TRUE.equals(systemRegistration.getActive()) ? "Status activated successfully." : "Status deactivated successfully.";
			return ResponseEntity.ok(Map.of("clientId", systemRegistration.getClientId(), "message", message));
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				ex.printStackTrace();
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
	}

	public ResponseEntity<?> getExternalSystemForMapping(UserSessionDTO userSessionDetails, Integer serviceId) {
		try {
			List<Object> result = new ArrayList<Object>();
			
			Integer departmentId = userSessionDetails.getEntityId();
			List<ExternalSystemRegistration> allRecords = externalSysRegistrationRepo.findByDepartmentIdAndActive(departmentId, true);
			for(ExternalSystemRegistration externalSystemRegistration : allRecords) {
				String onProd = externalSystemRegistration.getMoveToProdStatus() != null ? externalSystemRegistration.getMoveToProdStatus() : "";
				result.add(Map.of("clientId", externalSystemRegistration.getClientId(), "clientName",
						externalSystemRegistration.getClientName(), "active", externalSystemRegistration.getActive(),
						"onProd", onProd, "status", computeDisplayStatus(externalSystemRegistration.getActive(), onProd)));
			}
			
			return ResponseEntity.ok(result);
		} catch (Exception ex) {
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public void moveToProd(UserSessionDTO userSessionDetails, String clientId, HttpServletRequest request) {
		try {
			ExternalSystemRegistration systemRegistration = externalSysRegistrationRepo.findByClientId(clientId)
					.orElseThrow(() -> new SPRuntimeError("External system configuration not found", HttpStatus.BAD_REQUEST));
			Integer departmentId = userSessionDetails.getEntityId();
			Integer locationId = userSessionDetails.getLocationId();
			
			if(!departmentId.equals(systemRegistration.getDepartmentId()) || !locationId.equals(systemRegistration.getLocationId())) {
				throw new SPRuntimeError("Unauthorized access", HttpStatus.UNAUTHORIZED);
			}
			
			if (!Boolean.TRUE.equals(systemRegistration.getActive())) {
				throw new SPRuntimeError("Configuration is not activated.", HttpStatus.BAD_REQUEST);
			}

			String currentProdStatus = systemRegistration.getMoveToProdStatus() != null ? systemRegistration.getMoveToProdStatus().trim() : "";
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

			ExternalSystemRegistrationDTO systemRegistrationDTO = buildExternalSystemRegistrationDTO(systemRegistration);
			try {
				externalSystemProdClient.pushToProd(userSessionDetails.getTenantId(), moveToProdToken, List.of(systemRegistrationDTO), request);
			    LOGGER.info("External System Registration config pushed for entity {}", systemRegistrationDTO.getClientId());
			} catch (FeignException e) {
				LOGGER.error("External System Registration service call failed. status={}, body={}", e.status(), e.contentUTF8(), e);
			    throw new SPRuntimeError("Failed to sync external system configuration", HttpStatus.BAD_GATEWAY);
			}
			
			systemRegistration.setMoveToProdStatus(MoveToProdStatus.MOVED_TO_PRODUCTION.getValue());
			systemRegistration.setMoveToProdOn(new Date());
			systemRegistration.setMoveToProdByUser(userSessionDetails.getUserID());
			
			externalSysRegistrationRepo.save(systemRegistration);
			
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				ex.printStackTrace();
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
	}

	public void suspend(UserSessionDTO userSessionDetails, String clientId, HttpServletRequest request) {
		try {
			ExternalSystemRegistration systemRegistration = externalSysRegistrationRepo.findByClientId(clientId)
					.orElseThrow(() -> new SPRuntimeError("External system configuration not found", HttpStatus.BAD_REQUEST));
			Integer departmentId = userSessionDetails.getEntityId();
			Integer locationId = userSessionDetails.getLocationId();
			
			if(!departmentId.equals(systemRegistration.getDepartmentId()) || !locationId.equals(systemRegistration.getLocationId())) {
				throw new SPRuntimeError("Unauthorized access", HttpStatus.UNAUTHORIZED);
			}
			
			if (!Boolean.TRUE.equals(systemRegistration.getActive())) {
				throw new SPRuntimeError("Configuration is not activated.", HttpStatus.BAD_REQUEST);
			}

			String currentProdStatus = systemRegistration.getMoveToProdStatus() != null ? systemRegistration.getMoveToProdStatus().trim() : "";
			if (MoveToProdStatus.STOPPED_IN_PRODUCTION.getValue().equals(currentProdStatus)) {
				throw new SPRuntimeError("Configuration is already suspended.", HttpStatus.BAD_REQUEST);
			}
			if (!MoveToProdStatus.MOVED_TO_PRODUCTION.getValue().equals(currentProdStatus) &&
				!MoveToProdStatus.MODIFIED_AFTER_PRODUCTION.getValue().equals(currentProdStatus)) {
				throw new SPRuntimeError("Configuration is not online in production.", HttpStatus.BAD_REQUEST);
			}

			ExternalSystemRegistrationDTO systemRegistrationDTO = buildExternalSystemRegistrationDTO(systemRegistration);
			systemRegistrationDTO.setActive(false); // suspend in production
			try {
				externalSystemProdClient.pushToProd(userSessionDetails.getTenantId(), moveToProdToken, List.of(systemRegistrationDTO), request);
			    LOGGER.info("External System Registration suspend config pushed for entity {}", systemRegistrationDTO.getClientId());
			} catch (FeignException e) {
				LOGGER.error("External System Registration suspend service call failed. status={}, body={}", e.status(), e.contentUTF8(), e);
			    throw new SPRuntimeError("Failed to sync external system configuration", HttpStatus.BAD_GATEWAY);
			}
			
			systemRegistration.setActive(true);
			systemRegistration.setMoveToProdStatus(MoveToProdStatus.STOPPED_IN_PRODUCTION.getValue());
			systemRegistration.setMoveToProdOn(new Date());
			systemRegistration.setMoveToProdByUser(userSessionDetails.getUserID());
			
			externalSysRegistrationRepo.save(systemRegistration);
			
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				ex.printStackTrace();
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
	}

	public ResponseEntity<?> deleteExternalSystem(UserSessionDTO userSessionDetails, String clientId) {
		try {
			ExternalSystemRegistration systemRegistration = externalSysRegistrationRepo.findByClientId(clientId)
					.orElseThrow(() -> new SPRuntimeError("External system configuration not found", HttpStatus.BAD_REQUEST));
			Integer departmentId = userSessionDetails.getEntityId();
			Integer locationId = userSessionDetails.getLocationId();

			if (!departmentId.equals(systemRegistration.getDepartmentId()) || !locationId.equals(systemRegistration.getLocationId())) {
				throw new SPRuntimeError("Unauthorized access", HttpStatus.UNAUTHORIZED);
			}

			if (Boolean.TRUE.equals(systemRegistration.getActive())) {
				throw new SPRuntimeError("Active configuration cannot be deleted. Deactivate it first.", HttpStatus.BAD_REQUEST);
			}

			String prodStatus = systemRegistration.getMoveToProdStatus();
			if (prodStatus != null && !prodStatus.trim().isEmpty()) {
				throw new SPRuntimeError("Configuration has already been moved to production and cannot be deleted.", HttpStatus.BAD_REQUEST);
			}

			externalSysRegistrationRepo.delete(systemRegistration);
			return ResponseEntity.ok(Map.of("clientId", clientId, "message", "External System configuration deleted successfully."));
		} catch (Exception ex) {
			if (ex instanceof SPRuntimeError) {
				throw ex;
			} else {
				ex.printStackTrace();
				throw new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
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
	
	public ExternalSystemRegistrationDTO buildExternalSystemRegistrationDTO(
	        ExternalSystemRegistration entity) {

	    if (entity == null) {
	        return null;
	    }

	    ExternalSystemRegistrationDTO dto =
	            new ExternalSystemRegistrationDTO();

	    dto.setClientId(entity.getClientId());
	    dto.setClientName(entity.getClientName());
	    dto.setDepartmentId(entity.getDepartmentId());
	    dto.setLocationId(entity.getLocationId());
	    dto.setActive(entity.getActive());
	    dto.setCallType(entity.getCallType());
	    dto.setExternalSystemDetail(entity.getExternalSystemDetail());

	    return dto;
	}

}
