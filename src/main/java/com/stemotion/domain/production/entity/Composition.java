package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.entity.ScriptVersion;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The producer's working copy of an approved script (proposal, Production
 * Workspace): the same ordered scenes and templates, with the per-scene
 * properties the producer has tuned. The approved script version is never
 * edited; a render takes this copy when it exists.
 */
@Entity
@Table(name = "compositions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Composition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_version_id", nullable = false, unique = true)
    private ScriptVersion scriptVersion;

    /** Composition version: 1 on the first save, +1 on every later save. */
    @Column(name = "revision", nullable = false)
    private Integer revision;

    /** Scenes in the SceneData shape, durations in the project's fps. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenes", nullable = false, columnDefinition = "jsonb")
    private String scenes;

    /** The producer who saved the latest revision (ERD: Producer edits Composition). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producer_id")
    private User producer;
}
