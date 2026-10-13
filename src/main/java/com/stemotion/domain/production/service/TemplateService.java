package com.stemotion.domain.production.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.production.dto.ProductionDtos.TemplateRequest;
import com.stemotion.domain.production.dto.ProductionDtos.TemplateResponse;
import com.stemotion.domain.production.entity.RemotionTemplate;
import com.stemotion.domain.production.repository.RemotionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-managed catalogue of the Remotion templates a scene can map to.
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final RemotionTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public List<TemplateResponse> findAll(String category, boolean activeOnly) {
        List<RemotionTemplate> templates;
        if (category != null) {
            templates = templateRepository.findByCategoryAndIsActiveTrue(category);
        } else if (activeOnly) {
            templates = templateRepository.findByIsActiveTrueOrderByNameAsc();
        } else {
            templates = templateRepository.findAll();
        }
        return templates.stream().map(TemplateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse findById(String id) {
        return TemplateResponse.from(getOrThrow(id));
    }

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        validateMetadata(request);
        templateRepository.findByName(request.name()).ifPresent(existing -> {
            throw ApiException.conflict("Template name already exists: " + request.name());
        });

        RemotionTemplate template = RemotionTemplate.builder()
            .subject(request.subject())
            .assetPack(request.assetPack())
            .slots(request.slots())
            .name(request.name())
            .category(request.category())
            .propsSchema(request.propsSchema())
            .previewUrl(request.previewUrl())
            .isActive(request.isActive() == null || request.isActive())
            .build();

        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse update(String id, TemplateRequest request) {
        validateMetadata(request);
        RemotionTemplate template = getOrThrow(id);
        if (request.subject() != null) template.setSubject(request.subject());
        if (request.assetPack() != null) template.setAssetPack(request.assetPack());
        if (request.slots() != null) template.setSlots(request.slots());
        template.setName(request.name());
        template.setCategory(request.category());
        template.setPropsSchema(request.propsSchema());
        template.setPreviewUrl(request.previewUrl());
        if (request.isActive() != null) {
            template.setIsActive(request.isActive());
        }
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public void delete(String id) {
        if (!templateRepository.existsById(id)) {
            throw ApiException.notFound("RemotionTemplate", id);
        }
        templateRepository.deleteById(id);
    }

    private RemotionTemplate getOrThrow(String id) {
        return templateRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("RemotionTemplate", id));
    }

    private void validateMetadata(TemplateRequest request) {
        if (request.subject() != null && !List.of("general", "physics", "chemistry", "biology", "math", "informatics").contains(request.subject())) {
            throw ApiException.badRequest("Unknown template subject");
        }
        if (request.assetPack() != null && !request.assetPack().matches("[a-z_]+@[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw ApiException.badRequest("assetPack must contain a pinned semantic version");
        }
        if (request.slots() != null) {
            try {
                var slots = new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.slots());
                if (slots == null || !slots.isObject() || request.slots().length() > 20000) throw new IllegalArgumentException();
            } catch (Exception e) { throw ApiException.badRequest("slots must be a JSON object"); }
        }
    }
}
