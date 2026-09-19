package com.stemotion.domain.ai.controller;

import com.stemotion.domain.ai.dto.AiDtos.AiActionResponse;
import com.stemotion.domain.ai.dto.AiDtos.GenerateScriptRequest;
import com.stemotion.domain.ai.dto.AiDtos.ScriptActionRequest;
import com.stemotion.domain.ai.service.AiScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Script Intelligence", description = "Gemini-backed drafting and analysis, human-in-the-loop")
public class AiController {

    private final AiScriptService aiScriptService;

    @GetMapping("/health")
    @Operation(summary = "Whether an AI key is configured on this environment")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("configured", aiScriptService.isConfigured()));
    }

    @PostMapping("/script/generate")
    @Operation(summary = "Draft a full STEM script from a topic")
    public ResponseEntity<AiActionResponse> generate(@Valid @RequestBody GenerateScriptRequest request) {
        return ResponseEntity.ok(aiScriptService.generateScript(request));
    }

    @PostMapping("/script/action")
    @Operation(summary = "Run resegment, grade, extract or terms over a script")
    public ResponseEntity<AiActionResponse> action(@Valid @RequestBody ScriptActionRequest request) {
        return ResponseEntity.ok(aiScriptService.runAction(request));
    }
}
