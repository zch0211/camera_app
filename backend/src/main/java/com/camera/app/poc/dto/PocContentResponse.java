package com.camera.app.poc.dto;

import com.camera.app.poc.entity.Poc;
import lombok.Getter;

@Getter
public class PocContentResponse {

    private final Long id;
    private final String name;
    private final String originalFilename;
    private final String language;
    private final boolean previewable;
    private final Boolean truncated;
    private final String contentPreview;
    private final String message;

    private PocContentResponse(Long id, String name, String originalFilename, String language,
                                boolean previewable, Boolean truncated,
                                String contentPreview, String message) {
        this.id = id;
        this.name = name;
        this.originalFilename = originalFilename;
        this.language = language;
        this.previewable = previewable;
        this.truncated = truncated;
        this.contentPreview = contentPreview;
        this.message = message;
    }

    public static PocContentResponse previewable(Poc poc, String content, boolean truncated) {
        return new PocContentResponse(
                poc.getId(), poc.getName(), poc.getOriginalFilename(),
                poc.getLanguage() != null ? poc.getLanguage().name() : null,
                true, truncated, content, null);
    }

    public static PocContentResponse notPreviewable(Poc poc, String reason) {
        return new PocContentResponse(
                poc.getId(), poc.getName(), poc.getOriginalFilename(),
                poc.getLanguage() != null ? poc.getLanguage().name() : null,
                false, null, null, reason);
    }
}
