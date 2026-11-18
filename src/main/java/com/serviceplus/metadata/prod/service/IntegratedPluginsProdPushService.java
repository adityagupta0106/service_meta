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
import com.serviceplus.metadata.esign.dto.EsignConfigRequestDTO;
import com.serviceplus.metadata.exception.SPRuntimeError;
import com.serviceplus.metadata.feignClient.TenantConfigurationFeignClient;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class IntegratedPluginsProdPushService extends AbstractProdPushService<EsignConfigRequestDTO>
		implements IntegratedPluginsProdClient {

	private static final String ENDPOINT = "/spgateway/pluginsprod/o/move-to-prod";

	public IntegratedPluginsProdPushService(TenantConfigurationFeignClient tenantConfigurationFeignClient,
			@Qualifier("prodSyncRestClient") RestClient restClient,
			@Value("${sync.prod.allowed-hosts}") Set<String> allowedHosts,
			@Value("${sync.prod.enabled:true}") boolean enabled, ObjectMapper objectMapper) {

		super(tenantConfigurationFeignClient, restClient, allowedHosts, enabled, objectMapper);
	}

	@Override
	protected String endpoint() {
		return ENDPOINT;
	}

	@Override
	public Map<String, String> pushToProd(String tenantId, String prodToken, List<EsignConfigRequestDTO> payload, HttpServletRequest request) {

		if (payload == null || payload.isEmpty()) {
			throw new SPRuntimeError("No eSign configurations to push", HttpStatus.BAD_REQUEST);
		}

		logger.info("Pushing {} eSign configuration(s) to prod, tenant={}", payload.size(), tenantId);

		return post(tenantId, prodToken, payload, request);
	}
}