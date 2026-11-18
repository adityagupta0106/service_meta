package com.serviceplus.metadata.inboxSentboxFilterConfig.service;

import org.springframework.http.ResponseEntity;

import com.serviceplus.metadata.dto.UserSessionDTO;
import com.serviceplus.metadata.inboxSentboxFilterConfig.dto.InboxSentBoxFilterDTO;

import jakarta.servlet.http.HttpServletRequest;

public interface InboxSentBoxFilterService {
	ResponseEntity<?> getAllConfigurations(Integer page, Integer size, UserSessionDTO userSessionDTO);
	ResponseEntity<?> getConfiguration(Long id, UserSessionDTO userSessionDTO);
	ResponseEntity<?> upsertConfiguration(InboxSentBoxFilterDTO inboxSentBoxFilterDTO, UserSessionDTO userSessionDTO);
	ResponseEntity<?> changeStatus(Long id, Boolean isActive, UserSessionDTO userSessionDTO);
	ResponseEntity<?> deleteConfiguration(Long id, UserSessionDTO userSessionDTO);
	ResponseEntity<?> moveToProd(Long id, UserSessionDTO userSessionDetails, HttpServletRequest request);
	ResponseEntity<?> suspend(Long id, UserSessionDTO userSessionDetails, HttpServletRequest request);
}
