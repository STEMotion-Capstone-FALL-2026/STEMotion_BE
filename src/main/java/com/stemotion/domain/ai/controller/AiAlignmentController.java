package com.stemotion.domain.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.client.AiAlignmentClient;
import com.stemotion.integration.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiAlignmentController {
    private final AiAlignmentClient client;
    private final IntegrationProperties properties;

    @PostMapping(value="/transcribe/align", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('WRITER','PRODUCER','ADMIN')")
    public JsonNode align(@RequestParam MultipartFile file, @RequestParam String text,
                         @RequestParam(defaultValue="vi") String language) {
        return client.align(file, text, language);
    }

    @PostMapping(value="/internal/transcribe/align", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public JsonNode workerAlign(@RequestHeader(value="X-AI-Alignment-Key", required=false) String supplied,
                               @RequestParam MultipartFile file, @RequestParam String text,
                               @RequestParam(defaultValue="vi") String language) {
        String expected = properties.getAi().getAlignmentWorkerKey();
        if (expected == null || expected.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            "AI_ALIGNMENT_NOT_CONFIGURED", "Worker alignment key is not configured");
        if (supplied == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.unauthorized("Invalid worker alignment key");
        }
        return client.align(file, text, language);
    }
}
