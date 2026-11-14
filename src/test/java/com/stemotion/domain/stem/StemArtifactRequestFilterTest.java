package com.stemotion.domain.stem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The publish body filter keeps refusing server-owned fields but lets the RF-08 YouTube metadata through. */
class StemArtifactRequestFilterTest {

    private final StemArtifactRequestFilter filter = new StemArtifactRequestFilter(new ObjectMapper());

    private MockHttpServletResponse publish(String json, MockFilterChain chain) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/scripts/p1/publish");
        request.setContentType("application/json");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void youtubeMetadataReachesThePublishEndpoint() throws Exception {
        var chain = new MockFilterChain();
        var response = publish("{\"targets\":[\"INTERNAL_LIBRARY\",\"LMS\",\"YOUTUBE\"],\"title\":\"Đèn dung nham\",\"description\":\"d\","
            + "\"tags\":[\"STEM\"],\"privacyStatus\":\"private\",\"selfDeclaredMadeForKids\":false,\"idempotencyKey\":\"k-12345678\"}", chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void serverOwnedFieldsAreStillRefused() throws Exception {
        var chain = new MockFilterChain();
        var response = publish("{\"targets\":[\"YOUTUBE\"],\"title\":\"t\",\"uploadStatus\":\"UPLOADED\"}", chain);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("STEM_INVALID_FIELD").contains("uploadStatus");
        assertThat(chain.getRequest()).isNull();
    }
}
