package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.PublishRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishRecordRepository extends JpaRepository<PublishRecord, String> {
    List<PublishRecord> findByProjectIdOrderByCreatedAtDesc(String projectId);

    /**
     * The newest publish record of each project, one row per project: no record of the same
     * project is newer, and on equal created_at the higher id wins, so the choice is stable.
     */
    @org.springframework.data.jpa.repository.Query("select r from PublishRecord r where r.project.id in :ids and not exists ("
        + "select n from PublishRecord n where n.project = r.project and "
        + "(n.createdAt > r.createdAt or (n.createdAt = r.createdAt and n.id > r.id)))")
    List<PublishRecord> findLatestByProjectIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);

}
