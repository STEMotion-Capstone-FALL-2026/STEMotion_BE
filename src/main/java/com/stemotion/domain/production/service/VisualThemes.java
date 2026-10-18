package com.stemotion.domain.production.service;

import com.stemotion.core.exception.ApiException;

import java.util.List;

/** The visual themes the Remotion compositions can draw with (FE ProducerStudio theme picker). */
public final class VisualThemes {

    public static final String DEFAULT = "default";
    public static final List<String> ALL = List.of(DEFAULT, "cyber", "emerald", "sunset", "academic");

    private VisualThemes() {}

    /** The theme itself, or 400 when it is not one of {@link #ALL}. */
    public static String require(String theme) {
        if (!ALL.contains(theme)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_VISUAL_THEME",
                "visualTheme must be one of " + String.join(", ", ALL));
        }
        return theme;
    }
}
