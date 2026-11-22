package com.stemotion.domain.collab.repository;

import com.stemotion.domain.collab.entity.ContributionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContributionEventRepository extends JpaRepository<ContributionEvent, String> {
    List<ContributionEvent> findByProjectIdOrderByOccurredAtDesc(String projectId);

    Page<ContributionEvent> findByProjectIdOrderByOccurredAtDesc(String projectId, Pageable pageable);
}
