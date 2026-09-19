package com.stemotion.domain.script.repository;

import com.stemotion.domain.script.entity.CommentAnnotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentAnnotationRepository extends JpaRepository<CommentAnnotation, String> {

    List<CommentAnnotation> findBySceneIdOrderByCreatedAtAsc(String sceneId);

    List<CommentAnnotation> findBySceneIdAndStatus(String sceneId, String status);
}
