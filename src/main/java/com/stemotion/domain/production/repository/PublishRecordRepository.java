package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.PublishRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishRecordRepository extends JpaRepository<PublishRecord, String> {
    List<PublishRecord> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
