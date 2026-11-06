package com.stemotion.domain.stem;

import com.stemotion.domain.script.service.ScriptService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@ConditionalOnProperty(name="app.stem.artifact.enabled",havingValue="true")
public class StemArtifactController {
    private final StemArtifactService artifacts;
    private final ScriptService scripts;
    public StemArtifactController(StemArtifactService artifacts,ScriptService scripts){this.artifacts=artifacts;this.scripts=scripts;}
    @GetMapping("/api/v1/scripts/{projectId}/stem-artifact-status")
    public Map<String,Object> status(@PathVariable String projectId,@AuthenticationPrincipal UserDetails actor){
        return artifacts.status(scripts.getOrThrow(projectId),actor.getUsername());
    }
}
