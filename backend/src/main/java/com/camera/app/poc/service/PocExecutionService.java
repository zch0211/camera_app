package com.camera.app.poc.service;

import com.camera.app.poc.dto.PocExecuteRequest;
import com.camera.app.poc.dto.PocExecuteResponse;

public interface PocExecutionService {

    PocExecuteResponse execute(Long pocId, PocExecuteRequest request);
}
