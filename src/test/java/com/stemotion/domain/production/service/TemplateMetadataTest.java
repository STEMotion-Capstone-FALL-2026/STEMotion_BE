package com.stemotion.domain.production.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.production.dto.ProductionDtos.TemplateRequest;
import com.stemotion.domain.production.entity.RemotionTemplate;
import com.stemotion.domain.production.repository.RemotionTemplateRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplateMetadataTest {
    @Test void oldPayloadPreservesExistingDirectorMetadata() {
        var repo=mock(RemotionTemplateRepository.class);
        var template=RemotionTemplate.builder().name("old").subject("general").assetPack("general@0.1.0").slots("{}").build();
        when(repo.findById("id")).thenReturn(Optional.of(template));
        when(repo.save(template)).thenReturn(template);
        var response=new TemplateService(repo).update("id",new TemplateRequest("renamed",null,null,null,true,null,null,null));
        assertThat(response.subject()).isEqualTo("general");
        assertThat(response.assetPack()).isEqualTo("general@0.1.0");
        assertThat(response.slots()).isEqualTo("{}");
    }
    @Test void invalidMetadataFailsBeforePersistence() {
        var repo=mock(RemotionTemplateRepository.class);var service=new TemplateService(repo);
        for (var request : java.util.List.of(
            new TemplateRequest("name",null,null,null,true,"unknown",null,null),
            new TemplateRequest("name",null,null,null,true,null,"latest",null),
            new TemplateRequest("name",null,null,null,true,null,null,"[]"))) {
            assertThatThrownBy(()->service.create(request)).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(repo);
    }

    @Test void recommendedSubjectsAndDefaultThemeAreValidatedAndKept() {
        var repo=mock(RemotionTemplateRepository.class);var service=new TemplateService(repo);
        var template=RemotionTemplate.builder().name("t").build();
        when(repo.findById("id")).thenReturn(Optional.of(template));
        when(repo.save(template)).thenReturn(template);
        var response=service.update("id",new TemplateRequest("t",null,null,null,false,null,null,null,
            java.util.List.of("Physics","Math","Physics"),"emerald"));
        assertThat(response.recommendedSubjects()).containsExactly("Physics","Math");
        assertThat(response.defaultVisualTheme()).isEqualTo("emerald");
        assertThat(response.isActive()).isFalse();
        assertThatThrownBy(()->service.update("id",new TemplateRequest("t",null,null,null,null,null,null,null,
            java.util.List.of("Geography"),null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.update("id",new TemplateRequest("t",null,null,null,null,null,null,null,
            null,"neon"))).isInstanceOf(ApiException.class);
    }
}
