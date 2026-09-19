package com.stemotion.domain.script.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewActionRequest {
    @NotBlank(message = "User ID is required")
    private String userId; // Tạm thời truyền ID, sau này lấy từ JWT
}
