package com.stemotion.domain.youtube.repository;

import com.stemotion.domain.youtube.entity.YoutubeChannelConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YoutubeChannelConnectionRepository extends JpaRepository<YoutubeChannelConnection, String> {

    Optional<YoutubeChannelConnection> findFirstByWorkspaceIdAndRevokedAtIsNullOrderByConnectedAtDesc(String workspaceId);

    List<YoutubeChannelConnection> findByWorkspaceId(String workspaceId);
}
