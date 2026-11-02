package com.stemotion.integration.client;
import com.stemotion.integration.config.IntegrationProperties;
import com.stemotion.core.exception.ApiException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

/** Local RSA fixture exercises real cryptographic verification without Google or network access. */
class GoogleSignatureVerifierTest {
    private static KeyPair keys() throws Exception { var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);return g.generateKeyPair(); }
    private static String token(KeyPair keys,String audience) {
        return Jwts.builder().subject("test-subject").issuer("https://accounts.google.com")
            .audience().add(audience).and().claim("email","fixture@gmail.com").claim("email_verified",true)
            .claim("nonce","fixture-nonce").expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(keys.getPrivate()).compact();
    }
    private static GoogleTokenVerifier verifier(KeyPair keys) {
        var p=new IntegrationProperties();p.getGoogle().setClientId("fixture-client");
        return new GoogleTokenVerifier(p,NimbusJwtDecoder.withPublicKey((RSAPublicKey)keys.getPublic()).build());
    }
    @Test void signatureAndAudienceMustBothBeValid() throws Exception {
        var keys=keys();var verifier=verifier(keys);
        assertThat(verifier.verify(token(keys,"fixture-client")).nonce()).isEqualTo("fixture-nonce");
        assertThatThrownBy(()->verifier.verify(token(keys,"other-client"))).isInstanceOf(ApiException.class);
        var other=keys();
        assertThatThrownBy(()->verifier.verify(token(other,"fixture-client"))).isInstanceOf(ApiException.class);
    }
}
