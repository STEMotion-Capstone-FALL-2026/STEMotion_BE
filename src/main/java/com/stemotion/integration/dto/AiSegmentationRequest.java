package com.stemotion.integration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiSegmentationRequest {

    @NotBlank(message = "rawScript must not be blank")
    private String rawScript;
}
