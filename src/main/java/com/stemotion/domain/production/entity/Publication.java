package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity @Table(name = "publications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Publication extends BaseEntity {
    public enum Platform { YOUTUBE, INTERNAL_LIBRARY }
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false)
    private ClipProject project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="clip_id")
    private Clip clip;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="publisher_id")
    private User publisher;
    @Enumerated(EnumType.STRING) @Column(name="platform",nullable=false,length=32)
    private Platform platform;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="metadata",columnDefinition="jsonb")
    private String metadata;
    @Column(name="published_at")
    private LocalDateTime publishedAt;
}
