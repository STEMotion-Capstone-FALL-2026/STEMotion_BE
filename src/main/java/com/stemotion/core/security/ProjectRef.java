package com.stemotion.core.security;

/** The ids access checks need about a project, without loading the project. */
public record ProjectRef(String projectId, String workspaceId, String ownerId) {}
