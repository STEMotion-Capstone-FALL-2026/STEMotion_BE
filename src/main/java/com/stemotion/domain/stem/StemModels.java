package com.stemotion.domain.stem;
import com.fasterxml.jackson.databind.JsonNode;
/** JDBC domain entities. Versioned catalog is a discriminated hierarchy, not scientific verification. */
public final class StemModels {
 private StemModels() {}
 public enum Kind { TOPIC, TOPIC_VERSION, SOURCE_EDITION, OUTCOME, PROFILE, POLICY, RUBRIC }
 public record CatalogVersion(String id,Kind kind,String logicalKey,int version,String parentId,String sourceId,String status,JsonNode data,int revision,String verificationStatus) {}
 public record StemBrief(String id,String versionId,int revision,String status,StemDtos.Brief content) {}
 public record WorkspaceMode(String workspaceId,int revision,String mode,java.util.List<String> policyIds,String defaultPolicyId) {}
}
