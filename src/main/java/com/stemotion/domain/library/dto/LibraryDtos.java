package com.stemotion.domain.library.dto;

import com.stemotion.core.enums.StemEnums.StemSubject;

import java.time.LocalDateTime;

public final class LibraryDtos {

    private LibraryDtos() {}

    /** A published clip as the internal library lists it. */
    public record LibraryItem(
        String id,
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer totalDurationSeconds,
        Integer sceneCount,
        String ownerName,
        String workspaceName,
        /** Direct MP4 link for download. */
        String videoUrl,
        /** Ready-to-paste iframe for Canvas/Moodle. */
        String embedCode,
        String youtubeUrl,
        LocalDateTime publishedAt
    ) {}
}
