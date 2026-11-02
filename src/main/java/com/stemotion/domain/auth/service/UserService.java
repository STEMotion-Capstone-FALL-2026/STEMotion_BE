package com.stemotion.domain.auth.service;

import com.stemotion.domain.auth.entity.SystemRole;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.dto.AuthDtos.UpdateUserRequest;
import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.stemotion.domain.auth.dto.AuthDtos.UserResponse;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(RegisterRequest request) {
        PasswordRules.requireNew(request.password());
        String username = AccountNames.username(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "USERNAME_TAKEN", "Tài khoản đã tồn tại.");
        }
        User user = User.builder().fullName(AccountNames.fullName(request.fullName())).username(username)
            .passwordHash(passwordEncoder.encode(request.password()))
            .systemRole(request.systemRole() == null ? SystemRole.USER : request.systemRole())
            .status(User.UserStatus.ACTIVE)
            .mustChangePassword(true)
            .build();
        // Return the account only, never a JWT belonging to the new user.
        User saved = userRepository.save(user);
        audit(saved, "CREATE", saved.getSystemRole(), saved.getStatus());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.hydrateProfiles(userRepository.findAll()).stream().map(UserResponse::from).toList();
    }

    /** One page of accounts, alphabetical by name, paged in SQL. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserResponse> findAll(com.stemotion.core.web.PageParams paging) {
        var pageable = org.springframework.data.domain.PageRequest.of(paging.page(), paging.size(),
            org.springframework.data.domain.Sort.by("fullName").ascending());
        var page = userRepository.findAll(pageable);
        userRepository.hydrateProfiles(page.getContent());
        return page.map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(String id) {
        return UserResponse.from(getOrThrow(id));
    }

    @Transactional
    public UserResponse update(String id, UpdateUserRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public UserResponse update(String id, UpdateUserRequest request, Long expectedVersion) {
        userRepository.lockSystemAdministration();
        User user = userRepository.lockById(id).orElseThrow(() -> ApiException.notFound("User", id));
        checkVersion(user, expectedVersion);
        var oldRole = user.getSystemRole();
        var oldStatus = user.getStatus();
        userRepository.hydrateProfiles(List.of(user));
        boolean hadLogin = hasLogin(user);
        long activeAdmins = userRepository.countAdministratorsWithLogin(SystemRole.ADMIN, User.UserStatus.ACTIVE);
        if (request.fullName() != null) user.setFullName(AccountNames.fullName(request.fullName()));
        if (request.systemRole() != null) user.setSystemRole(request.systemRole());
        if (request.status() != null) user.setStatus(request.status());
        protectLastAdmin(user, oldRole, oldStatus, false, activeAdmins, hadLogin);
        if (oldRole != user.getSystemRole() || oldStatus != user.getStatus()) {
            user.setAuthVersion(user.getAuthVersion() + 1);
            audit(user, "UPDATE_ACCESS", oldRole, oldStatus);
        }
        if (request.password() != null && !request.password().isBlank()) {
            PasswordRules.requireNew(request.password());
            if (user.getPasswordHash() == null) {
                throw ApiException.badRequest("Tài khoản này không có phương thức đăng nhập bằng mật khẩu.");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setMustChangePassword(true);
            user.setAuthVersion(user.getAuthVersion() + 1);
        }
        userRepository.saveAndFlush(user);
        userRepository.hydrateProfiles(List.of(user));
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(String id) {
        delete(id, null);
    }

    @Transactional
    public void delete(String id, Long expectedVersion) {
        userRepository.lockSystemAdministration();
        User user = userRepository.lockById(id).orElseThrow(() -> ApiException.notFound("User", id));
        checkVersion(user, expectedVersion);
        userRepository.hydrateProfiles(List.of(user));
        protectLastAdmin(user, user.getSystemRole(), user.getStatus(), true,
            userRepository.countAdministratorsWithLogin(SystemRole.ADMIN, User.UserStatus.ACTIVE), hasLogin(user));
        if (user.getGoogleIdentity() != null) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "ACCOUNT_HAS_GOOGLE_IDENTITY",
                "Tài khoản đã liên kết Google. Hãy vô hiệu hóa tài khoản để giữ nguyên danh tính và dữ liệu.");
        }
        audit(user, "DELETE", user.getSystemRole(), user.getStatus());
        userRepository.deleteById(id);
    }

    private void checkVersion(User user, Long version) {
        if (version != null && version != user.getVersion())
            throw ApiException.conflict("Tài khoản đã thay đổi. Vui lòng tải lại.");
    }

    private void protectLastAdmin(User user, SystemRole oldRole,
            User.UserStatus oldStatus, boolean deleting, long activeAdmins, boolean hadLogin) {
        if (oldRole == SystemRole.ADMIN && oldStatus == User.UserStatus.ACTIVE
                && hadLogin && (deleting || user.getSystemRole() != oldRole || user.getStatus() != oldStatus)
                && activeAdmins <= 1)
            throw ApiException.conflict("Hệ thống phải có ít nhất một quản trị viên đang hoạt động.");
    }

    private boolean hasLogin(User user) {
        return user.getPasswordHash()!=null || (user.getGoogleIdentity()!=null && user.getGoogleIdentity().getWhitelist().isActive());
    }

    private void audit(User user, String action, SystemRole oldRole, User.UserStatus oldStatus) {
        var actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        userRepository.auditAdministration(actor == null ? "internal" : actor.getName(), user.getId(), action,
            oldRole.name(), user.getSystemRole().name(), oldStatus.name(), user.getStatus().name());
    }

    private User getOrThrow(String id) {
        return userRepository.findForAuthentication(id)
            .orElseThrow(() -> ApiException.notFound("User", id));
    }
}
