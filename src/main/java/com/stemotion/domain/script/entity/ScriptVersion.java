package com.stemotion.domain.script.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * An immutable-by-convention snapshot of a project's script. A new version is
 * created on every resubmission so reviewers can diff iterations.
 *
 * <p>Uses the same {@link ScriptStatus} vocabulary as the project, which is the
 * spelling the front end's STEMScript contract expects.
 */
@Entity
@Table(name = "script_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScriptVersion extends BaseEntity {

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ScriptStatus status = ScriptStatus.DRAFT;

    /** Reviewer note attached when the version is returned for changes. */
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    /** Marks the version the producer renders from. */
    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;

    /**
     * Bumped on every save of the working draft. A save names the revision it was
     * edited from, so a save from an older tab is a 409, not a silent overwrite (V22).
     */
    @Column(name = "draft_revision", nullable = false)
    @Builder.Default
    private Integer draftRevision = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
