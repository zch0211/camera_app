package com.camera.app.poc.service;

import com.camera.app.poc.dto.PocExecuteRequest;
import com.camera.app.poc.dto.PocExecuteResponse;
import com.camera.app.poc.dto.PocExecutionSchema;

public interface PocExecutionService {

    PocExecutionSchema getExecutionSchema(Long pocId);

    PocExecuteResponse execute(Long pocId, PocExecuteRequest request, String executedBy);
}
