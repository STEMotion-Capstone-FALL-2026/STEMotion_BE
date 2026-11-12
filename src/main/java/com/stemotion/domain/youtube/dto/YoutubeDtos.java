package com.stemotion.domain.youtube.dto;

import com.stemotion.domain.youtube.entity.YoutubeChannelConnection;

import java.time.LocalDateTime;

public final class YoutubeDtos {

    private YoutubeDtos() {}

    public record ConnectResponse(
        String authorizationUrl
    ) {}

    public record ConnectionStatusResponse(
        boolean connected,
        String channelId,
        String channelTitle,
        String connectedBy,
        LocalDateTime connectedAt
    ) {
        public static ConnectionStatusResponse notConnected() {
            return new ConnectionStatusResponse(false, null, null, null, null);
        }

        public static ConnectionStatusResponse from(YoutubeChannelConnection conn) {
            if (conn == null || conn.getRevokedAt() != null) {
                return notConnected();
            }
            return new ConnectionStatusResponse(
                true,
                conn.getChannelId(),
                conn.getChannelTitle(),
                conn.getConnectedBy(),
                conn.getConnectedAt()
            );
        }
    }
}
