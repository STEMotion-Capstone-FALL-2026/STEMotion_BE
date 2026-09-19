package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Where and when an approved clip was published.
 */
@Entity
@Table(name = "publish_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by", nullable = false)
    private User publishedBy;

    /** Comma-separated PublishTarget names, e.g. "LMS,YOUTUBE". */
    @Column(name = "targets", nullable = false)
    private String targets;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "lms_embed_code", columnDefinition = "TEXT")
    private String lmsEmbedCode;

    @Column(name = "youtube_url")
    private String youtubeUrl;
}
