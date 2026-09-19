package com.stemotion.domain.production.controller;

import com.stemotion.domain.production.dto.ProductionDtos.PublishRequest;
import com.stemotion.domain.production.dto.ProductionDtos.PublishResponse;
import com.stemotion.domain.production.service.PublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scripts/{projectId}")
@RequiredArgsConstructor
@Tag(name = "Publishing", description = "LMS embed codes and YouTube publishing")
public class PublishController {

    private final PublishService publishService;

    @GetMapping("/publications")
    public ResponseEntity<List<PublishResponse>> history(@PathVariable String projectId) {
        return ResponseEntity.ok(publishService.findByProject(projectId));
    }

    @PostMapping("/publish")
    @Operation(summary = "Publish an approved clip to the selected targets")
    @PreAuthorize("hasAnyRole('PRODUCER', 'ADMIN')")
    public ResponseEntity<PublishResponse> publish(
        @PathVariable String projectId,
        @Valid @RequestBody PublishRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        PublishResponse published = publishService.publish(projectId, request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(published);
    }
}
