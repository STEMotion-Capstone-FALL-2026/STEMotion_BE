package com.stemotion.domain.script.repository;

import com.stemotion.domain.script.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SceneRepository extends JpaRepository<Scene, String> {

    List<Scene> findByScriptVersionIdOrderByOrderNoAsc(String versionId);
}
