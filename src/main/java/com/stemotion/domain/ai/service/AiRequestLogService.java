package com.stemotion.domain.ai.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.ai.entity.AiRequest;
import com.stemotion.domain.ai.repository.AiRequestRepository;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.integration.client.AiGateway.Generation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Audit writes survive failure/rollback of the caller. Never stores credentials or raw exceptions. */
@Service @RequiredArgsConstructor
public class AiRequestLogService {
    private final AiRequestRepository requests;
    private final UserRepository users;
    private final AiCostCalculator costs;

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public String start(AiRequest.RequestType type,String model,String requestData) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        User requester=auth!=null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())
            ? users.findById(auth.getName()).orElseThrow(()->ApiException.unauthorized("Unknown AI requester")) : null;
        return requests.saveAndFlush(AiRequest.builder().requester(requester).requestType(type)
            .model(model).requestData(requestData).build()).getId();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void complete(String id,Generation generation,long elapsed,String failureCode) {
        var request=requests.findById(id).orElseThrow(()->ApiException.notFound("AiRequest",id));
        request.setLatencyMs((int)Math.min(Integer.MAX_VALUE,Math.max(0,elapsed)));
        request.setCallStatus(generation==null?AiRequest.CallStatus.FAILED:AiRequest.CallStatus.SUCCEEDED);
        if(generation!=null) {
            request.setModel(generation.model());
            request.setResponseData(generation.result().toString());
            request.setInputTokens(generation.inputTokens());
            request.setOutputTokens(generation.outputTokens());
            Integer total=generation.totalTokens();
            if(total==null && generation.inputTokens()!=null && generation.outputTokens()!=null) {
                long sum=(long)generation.inputTokens()+generation.outputTokens();
                if(sum<=Integer.MAX_VALUE) total=(int)sum;
            }
            request.setCostTokens(total);
        } else {
            // Only internal error codes, never provider exception messages/URLs/keys.
            request.setResponseData("{\"error\":\""+failureCode.replaceAll("[^A-Z0-9_]", "")+"\"}");
        }
        // Stamp the exact rates/version on this request; later config changes cannot reprice history.
        var estimate = generation == null ? null : costs.estimate(generation.model(), generation.inputTokens(), generation.outputTokens());
        request.setMonetaryCost(estimate == null ? null : estimate.usd());
        request.setCostCurrency(estimate == null ? null : "USD");
        request.setPricingVersion(estimate == null ? null : estimate.price().version());
        request.setPricingInputPerMillion(estimate == null ? null : estimate.price().inputPerMillion());
        request.setPricingOutputPerMillion(estimate == null ? null : estimate.price().outputPerMillion());
        requests.saveAndFlush(request);
    }
    @Transactional
    public AiRequest.Decision decide(String id,AiRequest.Decision decision,String actorId) {
        if(decision==null || decision==AiRequest.Decision.PENDING) throw ApiException.badRequest("Choose ACCEPTED or IGNORED");
        var request=requests.findForDecision(id).orElseThrow(()->ApiException.notFound("AiRequest",id));
        var actor=users.findById(actorId).orElseThrow(()->ApiException.unauthorized("Unknown AI requester"));
        if(actor.getRole()!=User.UserRole.ADMIN && (request.getRequester()==null || !actorId.equals(request.getRequester().getId())))
            throw ApiException.forbidden("Only the requester or admin can decide this suggestion");
        if(request.getCallStatus()!=AiRequest.CallStatus.SUCCEEDED) throw ApiException.conflict("Only successful suggestions can be decided");
        if(request.getDecision()!=AiRequest.Decision.PENDING && request.getDecision()!=decision)
            throw ApiException.conflict("Suggestion already has a decision");
        request.setDecision(decision);
        requests.save(request);
        return decision;
    }
}
