package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.Publication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PublicationRepository extends JpaRepository<Publication,String> {
    Optional<Publication> findByProjectIdAndPlatform(String projectId,Publication.Platform platform);
    boolean existsByProjectIdAndPlatform(String projectId,Publication.Platform platform);
}
