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

    public record Search(StemSubject subject,String gradeLevel,Integer page,Integer size,String grade,String educationLevel,String subjectId,String outcomeId,String usageContext,String contextRole,String channel,String verificationStatus,String q) {
        com.stemotion.domain.library.service.StemLibraryQuery.Filters filters(){
            if(grade!=null && gradeLevel!=null && !grade.trim().equalsIgnoreCase(gradeLevel.trim()))throw com.stemotion.core.exception.ApiException.badRequest("grade and gradeLevel conflict");
            return new com.stemotion.domain.library.service.StemLibraryQuery.Filters(subject,grade==null?gradeLevel:grade,educationLevel,subjectId,outcomeId,usageContext,contextRole,channel,verificationStatus,q);
        }
    }
    @GetMapping("/clips")
    @Operation(summary = "Published snapshot-backed learning cards with server filtering and paging")
    public ResponseEntity<List<LibraryItem>> findClips(@org.springframework.web.bind.annotation.ModelAttribute Search query){
        var filters=query.filters();
        return com.stemotion.core.web.PagedResponse.list(com.stemotion.core.web.PageParams.of(query.page(),query.size()),
            ()->libraryService.search(filters,null),p->libraryService.searchPage(filters,p));
    }

    @GetMapping("/clips/{projectId}")
    @Operation(summary = "One published clip, with its export links")
    public ResponseEntity<LibraryItem> findClip(@PathVariable String projectId) {
        return ResponseEntity.ok(libraryService.findOne(projectId));
    }

    @GetMapping("/filters")
    @Operation(summary = "Frozen metadata facets using the same filters as the list")
    public ResponseEntity<java.util.Map<String,List<String>>> filters(@org.springframework.web.bind.annotation.ModelAttribute Search query){
        return ResponseEntity.ok(libraryService.facets(query.filters()));
    }
}
