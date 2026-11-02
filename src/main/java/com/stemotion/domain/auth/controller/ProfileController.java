package com.stemotion.domain.auth.controller;
import com.stemotion.domain.auth.dto.GoogleAuthDtos.*;
import com.stemotion.domain.auth.dto.AuthDtos.AuthResponse;
import com.stemotion.domain.auth.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api/v1/profile") @RequiredArgsConstructor
public class ProfileController {
    private final GoogleAuthService service;
    @GetMapping public Profile get(Principal principal) { return service.profile(principal.getName()); }
    @PatchMapping public Profile rename(Principal principal, @Valid @RequestBody Rename body) { return service.rename(principal.getName(), body.fullName()); }
    @PostMapping("/google/link-challenge")
    public Challenge challenge(Principal principal, @Valid @RequestBody LinkChallenge body) { return service.linkChallenge(principal.getName(), body.currentPassword()); }
    @PostMapping("/google/link")
    public AuthResponse link(Principal principal, @Valid @RequestBody Credential body) { return service.exchange(body, principal.getName()); }
}
