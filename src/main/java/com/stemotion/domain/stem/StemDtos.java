package com.stemotion.domain.stem;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAlias;
/** P3/RF-11 schema. No client-supplied verification or semantic hashes. */
public final class StemDtos {
 private StemDtos() {}
 public record ContextEntry(String key,String condition,String adaptations) {}
 public record UsageContext(ContextEntry primary,List<ContextEntry> secondary) {}
 public record Objective(String key,String outcomeId,String learnerAction,String observableEvidence) {}
 public record Task(String key,String learnerAction,String teacherSupport,String materials,String safety,String assessmentEvidence,String executionContextKey) {}
 public record Contribution(String component,String explanation,List<String> objectiveKeys,List<String> taskKeys) {}
 public record LessonStep(String stepKey,List<String> sceneIds,String activityKey,List<String> objectiveKeys) {
  public LessonStep {
   sceneIds=sceneIds==null?List.of():sceneIds;
   objectiveKeys=objectiveKeys==null?List.of():objectiveKeys;
  }
 }
 public record Brief(String policyVersionId,String topicVersionId,String gradeKey,List<String> subjectKeys,List<String> outcomeIds,String scopeOfClip,UsageContext context,List<String> distributionChannels,List<Objective> objectives,List<Task> studentTasks,List<Contribution> contributions,String safetyNotes,String rawLegacy,@JsonAlias({"steps","lessonSteps"}) List<LessonStep> steps) {
  public Brief {
   subjectKeys=subjectKeys==null?List.of():subjectKeys;
   outcomeIds=outcomeIds==null?List.of():outcomeIds;
   distributionChannels=distributionChannels==null?List.of():distributionChannels;
   objectives=objectives==null?List.of():objectives;
   studentTasks=studentTasks==null?List.of():studentTasks;
   contributions=contributions==null?List.of():contributions;
   steps=steps==null?List.of():steps;
  }
  public Brief(String policyVersionId,String topicVersionId,String gradeKey,List<String> subjectKeys,List<String> outcomeIds,String scopeOfClip,UsageContext context,List<String> distributionChannels,List<Objective> objectives,List<Task> studentTasks,List<Contribution> contributions,String safetyNotes,String rawLegacy){
   this(policyVersionId,topicVersionId,gradeKey,subjectKeys,outcomeIds,scopeOfClip,context,distributionChannels,objectives,studentTasks,contributions,safetyNotes,rawLegacy,List.of());
  }
 }
 public record Evidence(String id,String sceneId,String taskKey,String objectiveKey,String component,String explanation) {}
 public record Create(String schemaVersion,Integer expectedScriptRevision,String idempotencyKey) {}
 public record Save(String schemaVersion,Integer baseRevision,Integer expectedScriptRevision,String idempotencyKey,Brief brief,List<Evidence> evidence) {}
 public record Settings(Integer baseRevision,String mode,List<String> policyVersionIds,String defaultPolicyVersionId,String idempotencyKey,String legacyModeSelection) {}
 public record Validate(Integer expectedBriefRevision,Integer expectedScriptRevision,String phase,Integer expectedCompositionRevision) {}
 public record ChangeTarget(String expectedVersionId,Integer expectedScriptRevision,Integer expectedBriefRevision,String idempotencyKey) {}
 public record Criterion(String key,String judgment,String rationale,List<String> evidenceIds) {}
 public record Assessment(Integer expectedBriefRevision,Integer expectedScriptRevision,Integer expectedCompositionRevision,Integer expectedModeRevision,String rubricVersionId,List<Criterion> criteria,String idempotencyKey,@JsonAlias({"expectedFingerprint","fingerprint"}) String expectedFingerprint) {
  public Assessment(Integer expectedBriefRevision,Integer expectedScriptRevision,Integer expectedCompositionRevision,Integer expectedModeRevision,String rubricVersionId,List<Criterion> criteria,String idempotencyKey){
   this(expectedBriefRevision,expectedScriptRevision,expectedCompositionRevision,expectedModeRevision,rubricVersionId,criteria,idempotencyKey,null);
  }
 }
 public record ImportResult(int created, int skipped) {}
 public record Finding(String ruleId,String field,String severity,String message,String basis) {
  public Finding(String ruleId,String field,String severity,String message){this(ruleId,field,severity,message,"PRODUCT_RULE");}
 }
 public record LegacySuggestion(String primaryKey,List<String> channelKeys,boolean requiresConfirmation,String reason) {}
 public record View(String schemaVersion,String briefId,String scriptVersionId,int revision,String verificationStatus,String readiness,String effectiveMode,int modeRevision,Brief brief,List<Evidence> evidence,List<Finding> findings,LegacySuggestion legacySuggestion,String scriptFingerprint,String compositionFingerprint) {}
 public static Brief empty() { return new Brief(null,null,null,List.of(),List.of(),null,new UsageContext(null,List.of()),List.of(),List.of(),List.of(),List.of(),null,null,List.of()); }
}
