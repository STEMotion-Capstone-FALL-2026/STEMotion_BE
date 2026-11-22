package com.stemotion.domain.collab.entity;

/** States and kinds of the collaboration records (Report 4 sections 2.2 and 3.10). */
public final class CollabEnums {
    private CollabEnums() {}

    public enum ProposalStatus { PENDING, APPROVED, REJECTED }

    public enum MemberStatus { ACTIVE, REVOKED }

    public enum JoinRequestStatus { PENDING, APPROVED, REJECTED, REVOKED }

    public enum LeaseResource { SCRIPT_DRAFT, COMPOSITION }

    public enum ContributionType { SCRIPT_SUBMITTED, COMPOSITION_REVISED, REVIEW_DECIDED, NOTE_RESOLVED, PUBLISHED }
}
