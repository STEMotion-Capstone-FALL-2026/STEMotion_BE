package com.stemotion.domain.script.repository;

import com.stemotion.domain.script.entity.ScriptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptVersionRepository extends JpaRepository<ScriptVersion, String> {

    List<ScriptVersion> findByProjectIdOrderByVersionNoDesc(String projectId);

    List<ScriptVersion> findByCreatedById(String userId);
}
