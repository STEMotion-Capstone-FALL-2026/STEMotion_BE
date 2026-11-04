package com.stemotion.domain.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ConfiguredNotificationSenderTest {
    @TempDir Path temporary;
    EmailProperties configured() {
        var p=new EmailProperties();p.setEnabled(true);p.setProvider("brevo");p.setApiKey("fixture-key");
        p.setFromAddress("sender@example.invalid");p.setPublicUrl("https://example.invalid");return p;
    }
    @Test void brevoPayloadUsesBackendCredentialsAndProviderAcceptanceIsReturned() {
        var rest=new RestTemplate();var server=MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email")).andExpect(method(HttpMethod.POST))
            .andExpect(header("api-key","fixture-key")).andExpect(jsonPath("$.to[0].email").value("fixture@gmail.com"))
            .andExpect(jsonPath("$.sender.email").value("sender@example.invalid"))
            .andRespond(withSuccess("{\"messageId\":\"fixture-message\"}",MediaType.APPLICATION_JSON));
        assertThat(new ConfiguredNotificationSender(configured(),rest).send(UUID.randomUUID().toString(),"fixture@gmail.com","<p>Fixture</p>"))
            .isEqualTo("fixture-message");server.verify();
    }
    @Test void providerRateLimitRetriesButConfigurationFailureIsTerminal() {
        for(var status:java.util.List.of(HttpStatus.TOO_MANY_REQUESTS,HttpStatus.UNAUTHORIZED)) {
            var rest=new RestTemplate();var server=MockRestServiceServer.bindTo(rest).build();
            server.expect(anything()).andRespond(withStatus(status).header("Retry-After","120"));
            assertThatThrownBy(()->new ConfiguredNotificationSender(configured(),rest).send(UUID.randomUUID().toString(),"fixture@gmail.com","Fixture"))
                .isInstanceOfSatisfying(NotificationSender.Failure.class,f->{
                    assertThat(f.retryable).isEqualTo(status==HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(f.uncertain).isFalse();assertThat(f.retryAfterSeconds).isEqualTo(120);
                });server.verify();
        }
    }
    @Test void localPreviewNeverUsesTransportAndDoesNotPutRecipientInFilename() throws Exception {
        var p=configured();p.setProvider("local");p.setPreviewDirectory(temporary.toString());
        var rest=new RestTemplate();var server=MockRestServiceServer.bindTo(rest).build();
        var id=UUID.randomUUID().toString();
        assertThat(new ConfiguredNotificationSender(p,rest).send(id,"fixture@gmail.com","<p>Preview</p>")).startsWith("local-preview:");
        assertThat(Files.readString(temporary.resolve(id+".html"))).isEqualTo("<p>Preview</p>");server.verify();
    }
    @Test void realEmailCannotStartWithMissingCredentialsOrInsecurePublicUrl() {
        var p=configured();p.setApiKey("");assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
        p=configured();p.setPublicUrl("http://example.invalid");assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
    @Test void brevoAllowsLocalFrontendLinksButRejectsPublicHttpAndLookalikeHosts() {
        for(String url:java.util.List.of("http://localhost:3000","http://127.0.0.1:3000","http://[::1]:3000")) {
            var p=configured();p.setPublicUrl(url);assertThatCode(p::validate).doesNotThrowAnyException();
        }
        for(String url:java.util.List.of("http://localhost.example.com:3000","http://example.invalid","http://user@localhost:3000")) {
            var p=configured();p.setPublicUrl(url);assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
        }
    }
}
