package com.stemotion.domain.ai.controller;
import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.domain.ai.service.StemGroundedAiService;
import com.stemotion.domain.stem.StemValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(name="app.stem.enabled",havingValue="true")
@RequestMapping("/api/v1/ai/scripts")
public class StemGroundedAiController {
    private final StemGroundedAiService ai;private final StemValidator validator;
    public StemGroundedAiController(StemGroundedAiService ai,StemValidator validator){this.ai=ai;this.validator=validator;}
    @PostMapping("/{projectId}/grounded")
    public java.util.Map<String,Object> grounded(@PathVariable String projectId,@RequestBody JsonNode request,@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal){
        return ai.suggest(projectId,principal.getUsername(),validator.parse(request,StemGroundedAiService.Request.class));
    }
}
