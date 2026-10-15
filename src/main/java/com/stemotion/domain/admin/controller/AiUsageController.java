package com.stemotion.domain.admin.controller;

import com.stemotion.domain.admin.service.AiUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/ai-usage")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AiUsageController {
    private final AiUsageService service;
    @GetMapping
    public AiUsageService.UsagePage usage(
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required=false) String model, @RequestParam(required=false) String userId,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.find(from, to, model, userId, page, size);
    }
}
