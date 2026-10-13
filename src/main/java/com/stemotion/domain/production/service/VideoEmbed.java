package com.stemotion.domain.production.service;

import com.stemotion.core.exception.ApiException;
import java.net.URI;
import org.springframework.web.util.HtmlUtils;

/** Direct artifact embed; does not point at a nonexistent FE /embed route. */
public final class VideoEmbed {
    private VideoEmbed() {}
    public static String build(String videoUrl) {
        try {
            URI uri=URI.create(videoUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            throw ApiException.badRequest("An LMS embed requires a public HTTPS video URL");
        }
        return "<iframe src=\"%s\" width=\"100%%\" height=\"540\" allowfullscreen></iframe>"
            .formatted(HtmlUtils.htmlEscape(videoUrl));
    }
}
