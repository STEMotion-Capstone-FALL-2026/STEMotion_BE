package com.stemotion.domain.auth.controller;

import com.stemotion.domain.auth.dto.AuthDtos.AuthResponse;
import com.stemotion.domain.auth.dto.AuthDtos.LoginRequest;
import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import com.stemotion.domain.auth.dto.AuthDtos.UserResponse;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login and current session; public registration disabled")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Disabled: accounts are provisioned by administrators")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for a JWT")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Return the profile of the authenticated user")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            throw ApiException.unauthorized("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        }
        return ResponseEntity.ok(authService.getCurrentUser(principal.getUsername()));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the authenticated user")
    public ResponseEntity<UserResponse> changePassword(
        @AuthenticationPrincipal UserDetails principal,
        @Valid @RequestBody com.stemotion.domain.auth.dto.AuthDtos.ChangePasswordRequest request
    ) {
        if (principal == null) {
            throw ApiException.unauthorized("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        }
        return ResponseEntity.ok(authService.changePassword(principal.getUsername(), request));
    }
}
