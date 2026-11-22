package com.stemotion.domain.collab.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.collab.dto.CollabDtos.LeaseResponse;
import com.stemotion.domain.collab.entity.CollabEnums.LeaseResource;
import com.stemotion.domain.collab.entity.EditLease;
import com.stemotion.domain.collab.repository.EditLeaseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Edit leases (FR-31, BR-31, D16/D17): one lease per script draft or composition of a project,
 * renewed by the editor every 30 s and expiring 90 s after the last renewal. A Leader or an Admin
 * can force-release it. Comments never need a lease.
 */
@Service
@RequiredArgsConstructor
public class EditLeaseService {
    public static final Duration TTL = Duration.ofSeconds(90);
    private static final Logger AUDIT = LoggerFactory.getLogger("stemotion.audit");

    private final EditLeaseRepository leases;
    private final WorkspaceAccess access;
    private final UserRepository users;

    @Transactional
    public LeaseResponse acquire(LeaseResource type, String projectId, String userId) {
        requireEditor(type, projectId, userId);
        LocalDateTime now = LocalDateTime.now();
        EditLease lease = leases.lockByResource(type, projectId).orElse(null);
        if (lease != null && lease.validAt(now) && !lease.getHolderId().equals(userId)) throw held(lease);
        if (lease == null) lease = EditLease.builder().resourceType(type).resourceId(projectId).build();
        boolean continuing = lease.getId() != null && lease.validAt(now) && userId.equals(lease.getHolderId());
        if (!continuing) lease.setAcquiredAt(now);
        lease.setHolderId(userId);
        lease.setRenewedAt(now);
        lease.setExpiresAt(now.plus(TTL));
        lease.setReleasedBy(null);
        try {
            lease = leases.saveAndFlush(lease);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "EDIT_LEASE_HELD", "Someone else has just started editing; try again");
        }
        return response(type, projectId, lease, userId, now);
    }

    @Transactional
    public LeaseResponse release(LeaseResource type, String projectId, String userId, boolean force) {
        LocalDateTime now = LocalDateTime.now();
        EditLease lease = leases.lockByResource(type, projectId).orElse(null);
        if (lease == null || !lease.validAt(now)) return response(type, projectId, lease, userId, now);
        if (!lease.getHolderId().equals(userId)) {
            if (!force) throw ApiException.forbidden("Only the editor holding the lease can release it");
            String workspaceId = access.workspaceOf(projectId);
            access.requireLeaderOrAdmin(workspaceId, userId);
            AUDIT.info("edit-lease force-release project={} type={} holder={} by={}", projectId, type, lease.getHolderId(), userId);
        }
        lease.setReleasedBy(userId);
        leases.save(lease);
        return response(type, projectId, lease, userId, now);
    }

    @Transactional(readOnly = true)
    public LeaseResponse status(LeaseResource type, String projectId, String userId) {
        access.requireProject(projectId, userId, false);
        LocalDateTime now = LocalDateTime.now();
        return response(type, projectId, leases.findByResourceTypeAndResourceId(type, projectId).orElse(null), userId, now);
    }

    /**
     * Called by the script draft and composition writes. A project that uses project roles needs
     * the caller to hold the lease; an older project only refuses a write while someone else holds it.
     */
    @Transactional(readOnly = true)
    public void requireWritable(LeaseResource type, String projectId, String userId) {
        if (userId == null) return;
        LocalDateTime now = LocalDateTime.now();
        EditLease lease = leases.findByResourceTypeAndResourceId(type, projectId).orElse(null);
        boolean valid = lease != null && lease.validAt(now);
        if (valid && !lease.getHolderId().equals(userId)) throw held(lease);
        if (!valid && access.usesProjectRoles(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "EDIT_LEASE_REQUIRED",
                "Start editing first: this project needs the edit lease before saving");
        }
    }

    private void requireEditor(LeaseResource type, String projectId, String userId) {
        String role = type == LeaseResource.SCRIPT_DRAFT ? "WRITER" : "PRODUCER";
        if (!access.hasProjectRole(projectId, userId, role)) {
            throw ApiException.forbidden("Only a " + role + " of this project can edit it");
        }
    }

    private ApiException held(EditLease lease) {
        Map<String, Object> details = new HashMap<>();
        details.put("holderId", lease.getHolderId());
        details.put("holderName", name(lease.getHolderId()));
        details.put("expiresAt", lease.getExpiresAt().toString());
        return new ApiException(HttpStatus.CONFLICT, "EDIT_LEASE_HELD",
            name(lease.getHolderId()) + " is editing this now; it opens read-only until they finish", details);
    }

    private String name(String userId) {
        return users.findById(userId).map(u -> u.getDisplayName() == null ? u.getFullName() : u.getDisplayName()).orElse(userId);
    }

    private LeaseResponse response(LeaseResource type, String projectId, EditLease lease, String me, LocalDateTime now) {
        String holder = lease != null && lease.validAt(now) ? name(lease.getHolderId()) : null;
        return LeaseResponse.of(type.name(), projectId, lease, holder, me, now, access.usesProjectRoles(projectId));
    }
}
