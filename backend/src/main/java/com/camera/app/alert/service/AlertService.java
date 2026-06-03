package com.camera.app.alert.service;

import com.camera.app.alert.dto.*;
import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.alert.entity.AlertSourceType;
import com.camera.app.alert.entity.AlertStatus;
import com.camera.app.common.response.PageResult;

public interface AlertService {

    PageResult<AlertListItemResponse> listAlerts(String keyword, AlertSeverity severity,
            AlertStatus status, AlertSourceType sourceType, Long assetId, int page, int size);

    AlertDetailResponse getAlert(Long id);

    AlertDetailResponse createAlert(AlertCreateRequest request, String createdBy);

    AlertDetailResponse confirm(Long id, String comment, String operatorUsername);

    AlertDetailResponse markFalsePositive(Long id, String comment, String operatorUsername);

    AlertDetailResponse resolve(Long id, String comment, String operatorUsername);

    AlertDetailResponse ignore(Long id, String comment, String operatorUsername);
}
