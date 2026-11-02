package com.stemotion.domain.auth.controller;
import com.stemotion.domain.auth.dto.GoogleAuthDtos.*;
import com.stemotion.domain.auth.entity.GoogleWhitelist;
import com.stemotion.domain.auth.repository.GoogleWhitelistRepository;
import com.stemotion.domain.auth.service.GoogleAuthService;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.web.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController @RequestMapping("/api/v1/admin/google-whitelist")
@PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class GoogleWhitelistController {
    private final GoogleWhitelistRepository records;
    private final com.stemotion.domain.auth.service.GoogleWhitelistService service;
    private final com.stemotion.domain.notification.NotificationService notifications;
    @GetMapping @Transactional(readOnly=true)
    public ResponseEntity<List<WhitelistRow>> list(@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String query,
        @RequestParam(required=false) String status) {
        if (page < 0 || size < 1 || size > 100 || (status != null && !List.of("ACTIVE","REVOKED").contains(status)))
            throw ApiException.badRequest("Bộ lọc hoặc phân trang không hợp lệ.");
        var result = records.search(query.trim().toLowerCase(java.util.Locale.ROOT), status == null, status == null ? "ACTIVE" : status,
            PageRequest.of(page,size,Sort.by("email")));
        var summaries = notifications.summaries(result.getContent().stream().map(GoogleWhitelist::getId).toList());
        return PagedResponse.of(result.map(w -> row(w,summaries.get(w.getId()))));
    }
    @PostMapping @Transactional
    public ResponseEntity<WhitelistRow> add(Principal principal, @Valid @RequestBody WhitelistCreate body) {
        return ResponseEntity.status(201).body(row(service.add(body.email(),principal.getName())));
    }
    @PatchMapping("/{id}") @Transactional
    public WhitelistRow update(Principal principal, @PathVariable String id, @Valid @RequestBody WhitelistUpdate body) {
        return row(service.update(id,body.status(),body.version(),principal.getName()));
    }
    public record ResendRequest(@jakarta.validation.constraints.NotNull Long version) {}
    @PostMapping("/{id}/notifications/resend")
    public ResponseEntity<Void> resend(Principal principal, @PathVariable String id, @Valid @RequestBody ResendRequest body) {
        service.resend(id,body.version(),principal.getName());
        return ResponseEntity.accepted().build();
    }
    private WhitelistRow row(GoogleWhitelist w) {
        return row(w,notifications.summaries(List.of(w.getId())).get(w.getId()));
    }
    private WhitelistRow row(GoogleWhitelist w, com.stemotion.domain.notification.NotificationService.Summary summary) {
        return new WhitelistRow(w.getId(),w.getEmail(),w.getStatus(),w.getVersion(),w.getCreatedAt(),
            w.getIdentity() == null ? null : w.getIdentity().getUser().getId(),summary);
    }
}
