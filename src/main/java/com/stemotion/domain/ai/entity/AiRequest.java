package com.stemotion.domain.ai.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.production.entity.Clip;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

@Entity @Table(name="ai_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiRequest extends BaseEntity {
    public enum RequestType { ANALYSIS, SEGMENTATION, TTS, CAPTION, QA }
    public enum Decision { PENDING, ACCEPTED, IGNORED }
    public enum CallStatus { UNKNOWN, PENDING, SUCCEEDED, FAILED }
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="requester_id")
    private User requester;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="script_version_id")
    private ScriptVersion scriptVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="scene_id")
    private Scene scene;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="clip_id")
    private Clip clip;
    @Enumerated(EnumType.STRING) @Column(name="request_type",nullable=false,length=32)
    private RequestType requestType;
    @Column(name="model",nullable=false)
    private String model;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="request_data",columnDefinition="jsonb")
    private String requestData;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="response_data",columnDefinition="jsonb")
    private String responseData;
    @Enumerated(EnumType.STRING) @Column(name="decision",length=16)
    @Builder.Default private Decision decision=Decision.PENDING;
    @Enumerated(EnumType.STRING) @Column(name="call_status",nullable=false,length=16)
    @Builder.Default private CallStatus callStatus=CallStatus.PENDING;
    @Column(name="cost_tokens") private Integer costTokens;
    @Column(name="input_tokens") private Integer inputTokens;
    @Column(name="output_tokens") private Integer outputTokens;
    @Column(name="latency_ms") private Integer latencyMs;
    @Column(name="monetary_cost",precision=20,scale=10) private BigDecimal monetaryCost;
    @Column(name="cost_currency",length=3) private String costCurrency;
}
