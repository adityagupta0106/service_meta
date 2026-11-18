
package com.serviceplus.metadata.prod.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceplus.metadata.dto.ExternalSystemRegistrationDTO;
import com.serviceplus.metadata.exception.SPRuntimeError;
import com.serviceplus.metadata.feignClient.TenantConfigurationFeignClient;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class ExternalSystemProdPushService extends AbstractProdPushService<ExternalSystemRegistrationDTO>
		implements ExternalSystemProdClient {

	private static final String ENDPOINT = "/spgateway/externalprod/o/move-to-prod";

	public ExternalSystemProdPushService(TenantConfigurationFeignClient tenantConfigurationFeignClient,
			@Qualifier("prodSyncRestClient") RestClient restClient,
			@Value("${sync.prod.allowed-hosts}") Set<String> allowedHosts,
			@Value("${sync.prod.enabled:false}") boolean enabled,ObjectMapper objectMapper) {

		super(tenantConfigurationFeignClient, restClient, allowedHosts, enabled, objectMapper);
	}

	@Override
	protected String endpoint() {
		return ENDPOINT;
	}

	@Override
	public Map<String, String> pushToProd(String tenantId, String prodToken,
			List<ExternalSystemRegistrationDTO> payload, HttpServletRequest request) {

		if (payload == null || payload.isEmpty()) {
			throw new SPRuntimeError("Nothing to push", HttpStatus.BAD_REQUEST);
		}

		logger.info("Pushing {} external system configuration(s) to prod, tenant={}", payload.size(), tenantId);

		return post(tenantId, prodToken, payload, request);
	}
}
