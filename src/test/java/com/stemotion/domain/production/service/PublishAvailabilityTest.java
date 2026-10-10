package com.stemotion.domain.production.service;

import com.stemotion.core.enums.StemEnums.PublishTarget;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.PublishRequest;
import com.stemotion.domain.production.repository.PublishRecordRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.review.service.ClipQaService;
import com.stemotion.domain.script.service.ScriptService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublishAvailabilityTest {
    @ParameterizedTest
    @EnumSource(value = PublishTarget.class, names = {"YOUTUBE", "LMS"})
    void unsupportedTargetCannotMarkProjectPublished(PublishTarget target) {
        var projects = mock(ClipProjectRepository.class);
        var records = mock(PublishRecordRepository.class);
        var scripts = mock(ScriptService.class);
        var qa = mock(ClipQaService.class);
        when(scripts.getOrThrow("project")).thenReturn(new ClipProject());
        var service = new PublishService(records, projects, scripts, mock(AuthService.class), qa);
        assertThatThrownBy(() -> service.publish("project", new PublishRequest(
            List.of(target), "Video", "Description"), "user"))
            .isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("PUBLISH_TARGET_UNAVAILABLE"));
        verifyNoInteractions(records, projects, qa);
    }
}
