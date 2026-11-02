package com.stemotion.domain.auth.service;

import com.stemotion.domain.auth.entity.SystemRole;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.JwtService;
import com.stemotion.domain.auth.dto.GoogleAuthDtos.Credential;
import com.stemotion.domain.auth.entity.*;
import com.stemotion.domain.auth.repository.*;
import com.stemotion.integration.client.GoogleTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Policies are tested separately from the real-signature fixture tests. */
class GoogleAuthPolicyTest {
    private final GoogleWhitelistRepository whitelist=mock(GoogleWhitelistRepository.class);
    private final GoogleIdentityRepository identities=mock(GoogleIdentityRepository.class);
    private final GoogleChallengeRepository challenges=mock(GoogleChallengeRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final GoogleTokenVerifier verifier=mock(GoogleTokenVerifier.class);
    private final PasswordEncoder passwords=mock(PasswordEncoder.class);
    private final JwtService jwt=mock(JwtService.class);
    private final AuthService auth=new AuthService(users,passwords,jwt,verifier);
    private final GoogleAuthService service=new GoogleAuthService(whitelist,identities,challenges,users,verifier,auth,passwords);
    private GoogleChallenge challenge;
    private final Credential credential=new Credential("token","challenge","secret");
    @BeforeEach void prepare() throws Exception {
        ReflectionTestUtils.setField(service,"authEnabled",true);
        ReflectionTestUtils.setField(service,"googleEnabled",true);
        when(verifier.isConfigured()).thenReturn(true);
        when(verifier.verify("token")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("sub-1","fixture@gmail.com","Fixture User","nonce"));
        challenge=new GoogleChallenge();challenge.setId("challenge");challenge.setPurpose("LOGIN");
        challenge.setNonceHash(digest("nonce"));challenge.setSecretHash(digest("secret"));challenge.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(challenges.lockById("challenge")).thenReturn(Optional.of(challenge));
        when(users.saveAndFlush(any())).thenAnswer(call->{User u=call.getArgument(0);if(u.getId()==null)u.setId("google-user");return u;});
        when(identities.saveAndFlush(any())).thenAnswer(call->{GoogleIdentity i=call.getArgument(0);i.setId("identity");return i;});
    }
    @Test void outsideWhitelistNeverCreatesOrLinksAUser() {
        when(whitelist.lockByEmail("fixture@gmail.com")).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.exchange(credential,null)).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("ACCOUNT_NOT_IN_SYSTEM"));
        verify(users,never()).saveAndFlush(any());verify(identities,never()).saveAndFlush(any());
        assertThat(challenge.getConsumedAt()).isNull();
    }
    @Test void approvedGoogleOnlyUserHasNoPasswordOrForcedPasswordChange() {
        approve();
        var response=service.exchange(credential,null);
        assertThat(response.user().username()).isNull();assertThat(response.user().hasLocalPassword()).isFalse();
        assertThat(response.user().googleLinked()).isTrue();
        assertThat(response.session().authMethod()).isEqualTo("GOOGLE");
        assertThat(response.session().requiresPasswordChange()).isFalse();
        assertThat(response.user().systemRole()).isEqualTo(SystemRole.USER);
        assertThat(challenge.getConsumedAt()).isNotNull();
    }
    @Test void consumedOrWrongPurposeChallengeCannotMutateAccounts() {
        challenge.setConsumedAt(LocalDateTime.now());
        assertThatThrownBy(()->service.exchange(credential,null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(whitelist);verify(users,never()).saveAndFlush(any());
    }
    @Test void identityOwnedByAnotherUserCannotBeMergedDuringLink() {
        var w=approve();challenge.setPurpose("LINK");challenge.setUserId("local-user");challenge.setAuthVersion(0L);
        User local=User.builder().fullName("Local User").username("local").passwordHash("hash").build();local.setId("local-user");
        User other=User.builder().fullName("Other User").build();other.setId("other-user");
        GoogleIdentity identity=new GoogleIdentity();identity.setUser(other);identity.setWhitelist(w);identity.setSubject("sub-1");identity.setEmail("fixture@gmail.com");
        when(users.lockById("local-user")).thenReturn(Optional.of(local));
        when(identities.findByWhitelistId(w.getId())).thenReturn(Optional.of(identity));
        assertThatThrownBy(()->service.exchange(credential,"local-user")).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("GOOGLE_IDENTITY_IN_USE"));
        verify(users,never()).saveAndFlush(any());verify(identities,never()).saveAndFlush(any());
        assertThat(identity.getUser().getId()).isEqualTo("other-user");
    }
    private GoogleWhitelist approve(){var w=new GoogleWhitelist();w.setId("whitelist");w.setEmail("fixture@gmail.com");when(whitelist.lockByEmail(w.getEmail())).thenReturn(Optional.of(w));return w;}
    private static String digest(String s) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
}
