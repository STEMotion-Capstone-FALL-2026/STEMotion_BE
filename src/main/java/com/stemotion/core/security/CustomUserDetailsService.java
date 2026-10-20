package com.stemotion.core.security;

import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads a user by its id (the JWT subject) rather than by username,
 * so the authenticated principal name is always the user id.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        // Same per-request entry WorkspaceAccess reads, so the user is loaded once per request.
        User user = com.stemotion.core.support.RequestCache.get("user:" + userId, () -> userRepository.findById(userId))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getId())
            .password(user.getPasswordHash() == null ? "" : user.getPasswordHash())
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
            .disabled(user.getStatus() != User.UserStatus.ACTIVE)
            .build();
    }
}
