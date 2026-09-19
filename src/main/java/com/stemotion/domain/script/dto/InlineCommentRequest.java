package com.stemotion.domain.script.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InlineCommentRequest {
    @NotBlank(message = "Reviewer ID is required")
    private String reviewerId; // Tạm thời truyền ID, sau này lấy từ JWT
    
    @NotBlank(message = "Content is required")
    private String content;
    
    private String timecode;
}
