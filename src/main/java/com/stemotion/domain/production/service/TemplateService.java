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

    @Transactional
    public List<TemplateResponse> findAll(String category, boolean activeOnly) {
        ensureDefaultTemplates();
        List<RemotionTemplate> templates;
        if (category != null && !category.isBlank()) {
            templates = activeOnly
                ? templateRepository.findByCategoryAndIsActiveTrue(category)
                : templateRepository.findAll().stream()
                    .filter(t -> category.equalsIgnoreCase(t.getCategory()))
                    .toList();
        } else if (activeOnly) {
            templates = templateRepository.findByIsActiveTrueOrderByNameAsc();
        } else {
            templates = templateRepository.findAll();
        }
        return templates.stream().map(TemplateResponse::from).toList();
    }

    private void ensureDefaultTemplates() {
        if (templateRepository.count() > 0) {
            return;
        }
        List<RemotionTemplate> defaults = List.of(
            RemotionTemplate.builder()
                .name("TITLE_HERO — Mở Đầu & Đặt Vấn Đề (Hook)")
                .category("Physics")
                .propsSchema("{\"sceneType\":\"TITLE_HERO\",\"stemPillar\":\"Science\",\"fields\":[\"title\",\"subtitle\",\"badgeText\",\"narration\"]}")
                .previewUrl("TITLE_HERO")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("ILLUSTRATED_EXPLAINER — Hoạt Họa Flat Vector (Kurzgesagt Style)")
                .category("Physics")
                .propsSchema("{\"sceneType\":\"ILLUSTRATED_EXPLAINER\",\"stemPillar\":\"Science\",\"fields\":[\"themePalette\",\"heroEntities\",\"floatingLabels\",\"bottomCaption\"]}")
                .previewUrl("ILLUSTRATED_EXPLAINER")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("MATH_FORMULA — Biến Đổi Công Thức KaTeX Từng Bước")
                .category("Math")
                .propsSchema("{\"sceneType\":\"MATH_FORMULA\",\"stemPillar\":\"Mathematics\",\"fields\":[\"latex\",\"steps\",\"narration\"]}")
                .previewUrl("MATH_FORMULA")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("GEOMETRY_SPACE — Tọa Độ Không Gian & Hình Học Phẳng")
                .category("Math")
                .propsSchema("{\"sceneType\":\"GEOMETRY_SPACE\",\"stemPillar\":\"Mathematics\",\"fields\":[\"shapeType\",\"dimensions\",\"formulaLatex\",\"properties\"]}")
                .previewUrl("GEOMETRY_SPACE")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("CHEMICAL_REACTION — Cân Bằng Phương Trình & Cấu Tạo Phân Tử")
                .category("Chemistry")
                .propsSchema("{\"sceneType\":\"CHEMICAL_REACTION\",\"stemPillar\":\"Science\",\"fields\":[\"equationLatex\",\"reactants\",\"products\",\"conditions\"]}")
                .previewUrl("CHEMICAL_REACTION")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("PROCESS_TIMELINE — Quy Trình Thiết Kế Kỹ Thuật & Chu Trình Sinh Học")
                .category("Biology")
                .propsSchema("{\"sceneType\":\"PROCESS_TIMELINE\",\"stemPillar\":\"Engineering\",\"fields\":[\"events\",\"narration\"]}")
                .previewUrl("PROCESS_TIMELINE")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("DIAGRAM_EXPLAINER — Sơ Đồ Luồng & Cơ Chế Tế Bào")
                .category("Biology")
                .propsSchema("{\"sceneType\":\"DIAGRAM_EXPLAINER\",\"stemPillar\":\"Science\",\"fields\":[\"diagramType\",\"nodes\",\"centerLabel\"]}")
                .previewUrl("DIAGRAM_EXPLAINER")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("ALGORITHM_WALKTHROUGH — Mô Phỏng Thuật Toán & Trạng Thái Biến")
                .category("ComputerScience")
                .propsSchema("{\"sceneType\":\"ALGORITHM_WALKTHROUGH\",\"stemPillar\":\"Technology\",\"fields\":[\"language\",\"codeSnippet\",\"steps\"]}")
                .previewUrl("ALGORITHM_WALKTHROUGH")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("DATA_CHART — Trực Quan Hóa Dữ Liệu Thực Nghiệm STEM")
                .category("ComputerScience")
                .propsSchema("{\"sceneType\":\"DATA_CHART\",\"stemPillar\":\"Technology\",\"fields\":[\"chartType\",\"xAxisLabel\",\"yAxisLabel\",\"dataPoints\"]}")
                .previewUrl("DATA_CHART")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("COMPARISON_SPLIT — Đối Chiếu Hai Mô Hình / Giải Pháp Kỹ Thuật")
                .category("Chemistry")
                .propsSchema("{\"sceneType\":\"COMPARISON_SPLIT\",\"stemPillar\":\"Engineering\",\"fields\":[\"leftHeading\",\"leftPoints\",\"rightHeading\",\"rightPoints\"]}")
                .previewUrl("COMPARISON_SPLIT")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("STEM_QUIZ — Câu Hỏi Thử Thách Tư Duy (GDPT 2018)")
                .category("Math")
                .propsSchema("{\"sceneType\":\"STEM_QUIZ\",\"stemPillar\":\"Mathematics\",\"fields\":[\"question\",\"options\",\"correctIndex\",\"explanation\"]}")
                .previewUrl("STEM_QUIZ")
                .isActive(true)
                .build(),
            RemotionTemplate.builder()
                .name("OUTRO — Tổng Kết Trọng Tâm & Gợi Ý Mở Rộng")
                .category("Biology")
                .propsSchema("{\"sceneType\":\"OUTRO\",\"stemPillar\":\"Engineering\",\"fields\":[\"summaryPoints\",\"nextLessonSuggestion\",\"instructorName\"]}")
                .previewUrl("OUTRO")
                .isActive(true)
                .build()
        );
        templateRepository.saveAll(defaults);
    }

    @Transactional(readOnly = true)
    public TemplateResponse findById(String id) {
        return TemplateResponse.from(getOrThrow(id));
    }

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        templateRepository.findByName(request.name()).ifPresent(existing -> {
            throw ApiException.conflict("Template name already exists: " + request.name());
        });

        RemotionTemplate template = RemotionTemplate.builder()
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
        RemotionTemplate template = getOrThrow(id);
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
}
