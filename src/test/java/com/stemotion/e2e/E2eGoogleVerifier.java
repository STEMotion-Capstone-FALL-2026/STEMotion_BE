package com.stemotion.e2e;
import com.stemotion.integration.client.GoogleTokenVerifier;
import com.stemotion.integration.config.IntegrationProperties;
import com.stemotion.core.exception.ApiException;

/** Only instantiated by the isolated E2eServer, never packaged in the application JAR. */
final class E2eGoogleVerifier extends GoogleTokenVerifier {
    E2eGoogleVerifier(IntegrationProperties properties) { super(properties); }
    @Override public boolean isConfigured() { return true; }
    @Override public GoogleIdentity verify(String credential) {
        String[] parts = credential.split(":",4);
        if (parts.length != 4 || !parts[0].equals("e2e")) throw ApiException.unauthorized("Invalid E2E credential");
        return new GoogleIdentity(parts[1],parts[2],"E2E Google user",parts[3]);
    }
}
