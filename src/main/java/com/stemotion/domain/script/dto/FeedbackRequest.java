package com.stemotion.domain.script.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotBlank(message = "Reviewer ID is required")
    private String reviewerId; // Tạm thời truyền ID, sau này lấy từ JWT
    
    @NotBlank(message = "Feedback note is required")
    private String feedbackNote;
}
