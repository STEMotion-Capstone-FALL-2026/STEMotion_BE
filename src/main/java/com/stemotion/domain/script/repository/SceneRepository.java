package com.stemotion.domain.script.repository;

import com.stemotion.domain.script.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SceneRepository extends JpaRepository<Scene, String> {

    List<Scene> findByScriptVersionIdOrderByOrderNoAsc(String versionId);

    @org.springframework.data.jpa.repository.Query("select count(f) > 0 from FeedbackComment f where f.scene.id = :sceneId")
    boolean hasFeedback(@org.springframework.data.repository.query.Param("sceneId") String sceneId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Scene s where s.scriptVersion.id = :versionId order by s.orderNo")
    List<Scene> findForUpdateByScriptVersionId(@org.springframework.data.repository.query.Param("versionId") String versionId);

    void deleteByScriptVersionId(String versionId);
}
