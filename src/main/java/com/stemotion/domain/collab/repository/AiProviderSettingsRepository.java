package com.stemotion.domain.collab.repository;

import com.stemotion.domain.collab.entity.AiProviderSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiProviderSettingsRepository extends JpaRepository<AiProviderSettings, String> {
    /** The settings are a single row; the oldest one wins if a race ever created two. */
    Optional<AiProviderSettings> findFirstByOrderByCreatedAtAsc();
}
