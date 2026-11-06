package com.stemotion.domain.script.dto;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class ScriptDtos {

    private ScriptDtos() {}

    /** Creates a clip project inside a workspace. */
    public record CreateScriptRequest(
        @NotBlank(message = "title is required")
        String title,

        @NotNull(message = "subject is required")
        StemSubject subject,

        String gradeLevel,

        Integer fps,

        @Valid
        List<SceneRequest> scenes
    ) {}

    /** Saves a draft: metadata plus the full ordered scene list. */
    public record UpdateScriptRequest(
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer fps,
        String summary,

        @Valid
        List<SceneRequest> scenes,

        /**
         * The draft revision the edit started from ({@code draftRevision} of the loaded script).
         * A different current revision is a 409. Optional while older clients do not send it.
         */
        @jakarta.validation.constraints.Min(value = 0, message = "baseRevision must not be negative")
        Integer baseRevision
    ) {}

    /** Mirrors the front end's STEMScript interface. */
    public record ScriptResponse(
        String id,
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer totalDurationSeconds,
        ScriptStatus scriptStatus,
        VideoStatus videoStatus,
        Integer fps,
        List<SceneResponse> scenes,
        String workspaceId,
        String ownerId,
        String ownerName,
        Integer currentVersionNo,
        String videoUrl,
        LocalDateTime createdAt,
        /** The production composition's visual theme; "default" before the producer picks one. */
        String visualTheme,
        /** Revision of the working draft; send it back as {@code baseRevision} when saving. */
        Integer draftRevision
    ) {}

    /** Lightweight row for dashboards and the pipeline board. */
    public record ScriptSummaryResponse(
        String id,
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer totalDurationSeconds,
        ScriptStatus scriptStatus,
        VideoStatus videoStatus,
        String ownerName,
        Integer sceneCount,
        LocalDateTime updatedAt
    ) {}

    /** Reviewer decision payload for approve / request-changes. */
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictDecisionReader.class)
    public record ReviewDecisionRequest(String note, com.stemotion.domain.stem.StemDtos.Assessment stemAssessment, com.stemotion.domain.stem.StemDtos.ChangeTarget changeTarget) {
        public ReviewDecisionRequest(String note) { this(note, null, null); }
        public ReviewDecisionRequest(String note,com.stemotion.domain.stem.StemDtos.Assessment assessment) { this(note,assessment,null); }
    }
    public static class StrictDecisionReader extends com.fasterxml.jackson.databind.JsonDeserializer<ReviewDecisionRequest> {
        @Override public ReviewDecisionRequest deserialize(com.fasterxml.jackson.core.JsonParser parser, com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            com.fasterxml.jackson.databind.JsonNode tree=parser.getCodec().readTree(parser);
            var validator=new com.stemotion.domain.stem.StemValidator(new com.fasterxml.jackson.databind.ObjectMapper());
            validator.limits(tree,0);
            if(!tree.isObject())throw com.stemotion.domain.stem.StemValidator.invalid("request","Expected review object");
            var fields=tree.fieldNames();while(fields.hasNext()){String key=fields.next();if(!java.util.Set.of("note","stemAssessment","changeTarget").contains(key))throw com.stemotion.domain.stem.StemValidator.invalid(key,"Unknown/server-owned review field");}
            if(tree.hasNonNull("note")&&!tree.get("note").isTextual())throw com.stemotion.domain.stem.StemValidator.invalid("note","Expected string");
            return new ReviewDecisionRequest(tree.hasNonNull("note")?tree.get("note").asText():null,tree.hasNonNull("stemAssessment")?validator.parse(tree.get("stemAssessment"),com.stemotion.domain.stem.StemDtos.Assessment.class):null,tree.hasNonNull("changeTarget")?validator.parse(tree.get("changeTarget"),com.stemotion.domain.stem.StemDtos.ChangeTarget.class):null);
        }
    }

    public record ScriptVersionResponse(
        String id,
        Integer versionNo,
        String title,
        String summary,
        ScriptStatus status,
        String reviewNote,
        Boolean isCurrent,
        String createdById,
        String createdByName,
        LocalDateTime createdAt
    ) {}

    /** Side-by-side payload backing the version diff modal. */
    public record VersionDiffResponse(
        ScriptVersionResponse left,
        List<SceneResponse> leftScenes,
        ScriptVersionResponse right,
        List<SceneResponse> rightScenes
    ) {}
}
