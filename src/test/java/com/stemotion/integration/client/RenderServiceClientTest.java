package com.stemotion.integration.client;

import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderServiceClientTest {
    @Test
    void undeployedWorkerReturnsServiceUnavailableWithoutSubmittingAJob() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.getRender().setBaseUrl("");
        RenderServiceClient client = new RenderServiceClient(properties);

        assertThatThrownBy(() -> client.enqueue("job-1", "FullSTEMVideo", Map.of(),
            1920, 1080, 30, "banmai"))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                org.assertj.core.api.Assertions.assertThat(error.getStatus())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                org.assertj.core.api.Assertions.assertThat(error.getCode())
                    .isEqualTo("RENDER_NOT_CONFIGURED");
            });
    }
}
