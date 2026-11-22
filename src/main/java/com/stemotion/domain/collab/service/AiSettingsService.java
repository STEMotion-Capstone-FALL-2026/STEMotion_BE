package com.stemotion.domain.collab.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.ai.repository.AiRequestRepository;
import com.stemotion.domain.collab.dto.CollabDtos;
import com.stemotion.domain.collab.dto.CollabDtos.AiSettingsRequest;
import com.stemotion.domain.collab.dto.CollabDtos.AiSettingsResponse;
import com.stemotion.domain.collab.entity.AiProviderSettings;
import com.stemotion.domain.collab.repository.AiProviderSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Admin AI/TTS settings and budgets (UC-34, FR-44, D04/D07). Provider secrets stay in the server
 * environment; only names and USD limits are stored. Without a saved row the defaults apply:
 * USD 20.00 a month and USD 0.50 a clip.
 */
@Service
@RequiredArgsConstructor
public class AiSettingsService {
    private final AiProviderSettingsRepository settings;
    private final AiRequestRepository requests;
    private final WorkspaceAccess access;

    @Transactional(readOnly = true)
    public AiProviderSettings current() {
        return settings.findFirstByOrderByCreatedAtAsc().orElseGet(() -> AiProviderSettings.builder().build());
    }

    @Transactional(readOnly = true)
    public AiSettingsResponse get(String userId) {
        requireAdmin(userId);
        return AiSettingsResponse.of(current(), requests.spentSince(monthStart()));
    }

    @Transactional
    public AiSettingsResponse update(AiSettingsRequest request, String userId) {
        requireAdmin(userId);
        AiProviderSettings s = settings.findFirstByOrderByCreatedAtAsc().orElseGet(() -> AiProviderSettings.builder().build());
        if (request.baseRevision() != s.getRevision()) {
            throw ApiException.staleRevision("AI_SETTINGS", s.getRevision(), request.baseRevision(),
                "The AI settings were changed elsewhere (revision " + s.getRevision() + "); reload them before saving");
        }
        List<String> providers = CollabDtos.split(CollabDtos.join(request.allowedAiProviders()));
        if (providers.isEmpty()) throw ApiException.badRequest("Allow at least one AI provider");
        s.setAllowedAiProviders(String.join(",", providers));
        s.setAllowedModels(CollabDtos.join(request.allowedModels()));
        s.setAllowedVoices(CollabDtos.join(request.allowedVoices()));
        s.setMonthlyBudgetUsd(request.monthlyBudgetUsd().setScale(2, java.math.RoundingMode.HALF_UP));
        s.setPerClipCapUsd(request.perClipCapUsd().setScale(2, java.math.RoundingMode.HALF_UP));
        s.setRevision(s.getRevision() + 1);
        s.setUpdatedBy(userId);
        return AiSettingsResponse.of(settings.saveAndFlush(s), requests.spentSince(monthStart()));
    }

    /**
     * Called before every AI provider call. A call is refused with 429 when the money spent this
     * month, or on this project, has reached its limit; the provider is not contacted.
     */
    @Transactional(readOnly = true)
    public void checkBudget(String projectId) {
        AiProviderSettings s = current();
        BigDecimal month = requests.spentSince(monthStart());
        if (month.compareTo(s.getMonthlyBudgetUsd()) >= 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_BUDGET_EXCEEDED",
                "Hạn mức AI tháng này (" + s.getMonthlyBudgetUsd() + " USD) đã dùng hết; Admin có thể nâng hạn mức",
                Map.of("spentUsd", month, "limitUsd", s.getMonthlyBudgetUsd()));
        }
        if (projectId == null) return;
        BigDecimal clip = requests.spentOnProject(projectId);
        if (clip.compareTo(s.getPerClipCapUsd()) >= 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_CLIP_BUDGET_EXCEEDED",
                "Clip này đã dùng hết hạn mức AI (" + s.getPerClipCapUsd() + " USD mỗi clip); Admin có thể nâng hạn mức",
                Map.of("spentUsd", clip, "limitUsd", s.getPerClipCapUsd()));
        }
    }

    /** A narration voice outside the allowed list is refused; an empty list allows every voice. */
    @Transactional(readOnly = true)
    public void requireAllowedVoice(String voice) {
        if (voice == null || voice.isBlank()) return;
        List<String> allowed = CollabDtos.split(current().getAllowedVoices());
        if (!allowed.isEmpty() && !allowed.contains(voice.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VOICE_NOT_ALLOWED",
                "Giọng đọc này chưa được Admin cho phép", Map.of("allowedVoices", allowed));
        }
    }

    private void requireAdmin(String userId) {
        if (!access.isAdmin(userId)) throw ApiException.forbidden("Only an Admin manages AI settings");
    }

    static LocalDateTime monthStart() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }
}
