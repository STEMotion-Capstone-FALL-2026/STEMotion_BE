package com.stemotion.integration.controller;

import com.stemotion.integration.dto.AiSegmentationRequest;
import com.stemotion.integration.dto.AiSegmentationResponse;
import com.stemotion.integration.service.GeminiAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final GeminiAiService geminiAiService;

    @PostMapping("/segment")
    public ResponseEntity<AiSegmentationResponse> segmentScript(
            @Valid @RequestBody AiSegmentationRequest request) {
        AiSegmentationResponse response = geminiAiService.segmentScript(request.getRawScript());
        return ResponseEntity.ok(response);
    }
}
