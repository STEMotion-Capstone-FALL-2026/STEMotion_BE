package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.PublishRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishRecordRepository extends JpaRepository<PublishRecord, String> {
    List<PublishRecord> findByProjectIdOrderByCreatedAtDesc(String projectId);

    /** Publish records of several projects, newest first, in one query. */
    @org.springframework.data.jpa.repository.Query("select r from PublishRecord r where r.project.id in :ids order by r.createdAt desc")
    List<PublishRecord> findByProjectIdInNewestFirst(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);
}
