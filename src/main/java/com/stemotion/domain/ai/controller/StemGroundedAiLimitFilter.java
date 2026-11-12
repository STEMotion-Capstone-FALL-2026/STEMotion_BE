package com.stemotion.domain.ai.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/** Reuse P3 byte bound without changing the P3 filter or its URL set. */
@Component
@ConditionalOnProperty(name="app.stem.enabled",havingValue="true")
public class StemGroundedAiLimitFilter extends com.stemotion.domain.stem.StemRequestLimitFilter {
    public StemGroundedAiLimitFilter(ObjectMapper json){super(json);}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().matches("/api/v1/ai/scripts/[^/]+/grounded");}
}
