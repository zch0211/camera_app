package com.camera.app.discovery.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BatchImportRequest {

    @NotEmpty
    private List<Long> resultIds;

    /** Optional: default location to set on imported assets */
    private String defaultLocation;

    /** Optional: default orgId to set on imported assets */
    private Long defaultOrgId;
}
