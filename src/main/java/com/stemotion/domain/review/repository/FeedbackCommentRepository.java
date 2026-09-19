package com.stemotion.domain.review.repository;

import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.domain.review.entity.FeedbackComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackCommentRepository extends JpaRepository<FeedbackComment, String> {

    List<FeedbackComment> findByProjectIdOrderByTimestampSecAsc(String projectId);

    List<FeedbackComment> findByProjectIdAndStatusOrderByTimestampSecAsc(String projectId, FeedbackStatus status);

    List<FeedbackComment> findBySceneIdOrderByCreatedAtAsc(String sceneId);

    long countByProjectIdAndStatus(String projectId, FeedbackStatus status);
}
