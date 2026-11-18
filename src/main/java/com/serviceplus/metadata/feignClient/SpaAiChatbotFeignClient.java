package com.serviceplus.metadata.feignClient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.serviceplus.metadata.catalogue.dto.ServiceCatalogueChatbotRequestDTO;
import com.serviceplus.metadata.catalogue.dto.ServiceCatalogueDataDTO;

@FeignClient(name = "AICHATBOT", path = "/aichatbot", url = "${aichatbot.service.url}")
public interface SpaAiChatbotFeignClient {

    @PostMapping("/service-catalogue")
    ResponseEntity<?> updateServiceCatalogueConfiguration(
            @RequestBody ServiceCatalogueChatbotRequestDTO request);

    @PostMapping(value = "/service-catalogue", headers = {"X-Ingest-Key=_NDqHbPsej8RdbwbT6XEg5L7g4xPYDxIHU0fuRlxAR0", "Content-Type=application/json"})
    ResponseEntity<?> ingestDocument(@RequestBody ServiceCatalogueDataDTO request);
}
