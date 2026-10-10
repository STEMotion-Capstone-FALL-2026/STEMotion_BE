package com.stemotion.domain.workspace.service;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.*;
import com.stemotion.domain.workspace.entity.*;
import com.stemotion.domain.workspace.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceInvitationTest {
    final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final WorkspaceService service = new WorkspaceService(workspaces, members,
        mock(ClipProjectRepository.class), users);
    WorkspaceMember seat;
    User actor;

    @BeforeEach void setup() {
        var workspace = new Workspace(); workspace.setId("workspace");
        when(workspaces.findById("workspace")).thenReturn(Optional.of(workspace));
        when(members.save(any())).thenAnswer(call -> call.getArgument(0));
        actor = User.builder().email("invited@example.invalid").status(User.UserStatus.ACTIVE).build();
        actor.setId("user");
        when(users.findById("user")).thenReturn(Optional.of(actor));
    }

    InvitationResponse issue() {
        var invitation = service.invite("workspace", new InviteMemberRequest(
            "Invited@example.invalid", User.UserRole.ADMIN));
        var captor = org.mockito.ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(members).save(captor.capture());
        seat = captor.getValue();
        when(members.findByInvitationTokenHash(seat.getInvitationTokenHash())).thenReturn(Optional.of(seat));
        return invitation;
    }

    @Test void invitationStaysInactiveAndRawTokenIsNeverStored() {
        var invitation = issue();
        assertThat(seat.getStatus()).isEqualTo(MemberStatus.INVITED);
        assertThat(seat.getUser()).isNull();
        assertThat(invitation.token()).hasSize(43);
        assertThat(seat.getInvitationTokenHash()).hasSize(64).isNotEqualTo(invitation.token());
        verify(users, never()).findByEmail(anyString());
    }

    @Test void matchingAccountNeedsTokenAndTokenIsSingleUse() {
        var invitation = issue();
        assertThat(service.acceptInvitation(invitation.token(), "user").status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(seat.getUser()).isEqualTo(actor);
        assertThat(seat.getInvitationTokenHash()).isNull();
        assertThatThrownBy(() -> service.acceptInvitation(invitation.token(), "user")).isInstanceOf(ApiException.class);
    }

    @Test void anotherEmailCannotClaimSeatEvenWithToken() {
        var invitation = issue(); actor.setEmail("other@example.invalid");
        assertThatThrownBy(() -> service.acceptInvitation(invitation.token(), "user")).isInstanceOf(ApiException.class);
        assertThat(seat.getStatus()).isEqualTo(MemberStatus.INVITED);
    }

    @Test void expiredOrMissingTokensDoNotGrantAccess() {
        var invitation = issue(); seat.setInvitationExpiresAt(Instant.EPOCH);
        assertThatThrownBy(() -> service.acceptInvitation(invitation.token(), "user")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.acceptInvitation("", "user")).isInstanceOf(ApiException.class);
        assertThat(seat.getUser()).isNull();
    }

    @Test void revokedTokenDoesNotGrantAccess() {
        var invitation = issue();
        when(members.findByInvitationTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.acceptInvitation(invitation.token(), "user")).isInstanceOf(ApiException.class);
    }

    @Test void memberPatchCannotBypassAcceptance() {
        issue(); seat.setId("seat");
        when(members.findById("seat")).thenReturn(Optional.of(seat));
        assertThatThrownBy(() -> service.updateMember("workspace", "seat",
            new UpdateMemberRequest(null, MemberStatus.ACTIVE))).isInstanceOf(ApiException.class);
    }
}
