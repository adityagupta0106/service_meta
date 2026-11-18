package com.serviceplus.metadata.prod.service;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

public interface ProductionClient<T> {
	Map<String, String> pushToProd(String tenantId, String prodToken, List<T> payload, HttpServletRequest request);
}