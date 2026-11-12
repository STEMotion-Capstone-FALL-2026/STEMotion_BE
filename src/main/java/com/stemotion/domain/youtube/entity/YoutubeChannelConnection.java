package com.stemotion.domain.youtube.entity;

import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "youtube_channel_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YoutubeChannelConnection extends BaseEntity {

    @Column(name = "workspace_id", length = 36, nullable = false)
    private String workspaceId;

    @Column(name = "channel_id", length = 128, nullable = false)
    private String channelId;

    @Column(name = "channel_title", columnDefinition = "text", nullable = false)
    private String channelTitle;

    @Column(name = "refresh_token_ciphertext", columnDefinition = "text", nullable = false)
    private String refreshTokenCiphertext;

    @Column(name = "scopes", columnDefinition = "text", nullable = false)
    private String scopes;

    @Column(name = "connected_by", length = 36, nullable = false)
    private String connectedBy;

    @Column(name = "connected_at", nullable = false)
    @Builder.Default
    private LocalDateTime connectedAt = LocalDateTime.now();

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
