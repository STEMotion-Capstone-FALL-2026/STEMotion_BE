package com.stemotion.domain.auth.service;

import com.fasterxml.jackson.databind.*;
import com.stemotion.domain.auth.dto.AuthDtos.*;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.AddMemberRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RoleContractTest {
    // Match Boot's permissive global setting: account requests explicitly reject unknown fields.
    private final ObjectMapper json=new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    @Test void rolesCannotCrossSystemAndWorkspaceBoundaries() throws Exception {
        assertThat(json.readValue("{\"systemRole\":\"USER\"}",RegisterRequest.class).systemRole().name()).isEqualTo("USER");
        assertThatThrownBy(()->json.readValue("{\"systemRole\":\"WRITER\"}",RegisterRequest.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(()->json.readValue("{\"role\":\"ADMIN\"}",RegisterRequest.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(()->json.readValue("{\"role\":\"ADMIN\"}",UpdateUserRequest.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(()->json.readValue("{\"userId\":\"fixture\",\"role\":\"ADMIN\"}",AddMemberRequest.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(()->json.readValue("{\"userId\":\"fixture\",\"role\":\"USER\"}",AddMemberRequest.class)).isInstanceOf(Exception.class);
    }
}
