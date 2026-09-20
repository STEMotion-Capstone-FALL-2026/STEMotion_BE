package com.stemotion.domain.library.controller;

import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.domain.library.dto.LibraryDtos.LibraryItem;
import com.stemotion.domain.library.service.LibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/library")
@RequiredArgsConstructor
@Tag(name = "Media library", description = "Published clips with subject and grade filters")
public class LibraryController {

    private final LibraryService libraryService;

    @GetMapping("/clips")
    @Operation(summary = "List published clips, filtered by subject and grade")
    public ResponseEntity<List<LibraryItem>> findClips(
        @RequestParam(required = false) StemSubject subject,
        @RequestParam(required = false) String gradeLevel
    ) {
        return ResponseEntity.ok(libraryService.findPublished(subject, gradeLevel));
    }

    @GetMapping("/clips/{projectId}")
    @Operation(summary = "One published clip, with its export links")
    public ResponseEntity<LibraryItem> findClip(@PathVariable String projectId) {
        return ResponseEntity.ok(libraryService.findOne(projectId));
    }

    @GetMapping("/filters")
    @Operation(summary = "Subjects and grades that actually have published clips")
    public ResponseEntity<LibraryService.LibraryFilters> filters() {
        return ResponseEntity.ok(libraryService.availableFilters());
    }
}
