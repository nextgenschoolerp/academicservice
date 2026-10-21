package com.kvs.academic.controller;

import com.kvs.academic.config.AcademicRoles;
import com.kvs.academic.dto.*;
import com.kvs.academic.entity.LearningAsset;
import com.kvs.academic.enums.AssetType;
import com.kvs.academic.service.LearningContentService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Module 06 e-learning content. Covers user stories:
 * - US-06005: Upload digital learning assets, lecture videos, and reference e-books
 * - US-06018: Monitor student video lecture watch-time durations and completion rates
 */
@RestController
@RequestMapping("/api/v1/lms/assets")
public class LearningContentController {

    @Autowired
    private LearningContentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AcademicRoles.ASSET_UPLOAD)
    public ResponseEntity<ApiResponse<LearningAssetResponse>> upload(@RequestPart("file") MultipartFile file,
                                                                     @RequestParam("title") String title,
                                                                     @RequestParam("gradeId") UUID gradeId,
                                                                     @RequestParam("subjectId") UUID subjectId,
                                                                     @RequestParam(value = "chapter", required = false) String chapter,
                                                                     @RequestParam(value = "assetType", required = false) AssetType assetType,
                                                                     @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Asset uploaded",
                service.upload(file, title, gradeId, subjectId, chapter, assetType, durationSeconds)));
    }

    @PostMapping("/links")
    @PreAuthorize(AcademicRoles.ASSET_UPLOAD)
    public ResponseEntity<ApiResponse<LearningAssetResponse>> addLink(@Valid @RequestBody CreateAssetLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Link added", service.addLink(request)));
    }

    @GetMapping
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<ApiResponse<List<LearningAssetResponse>>> list(@RequestParam(value = "gradeId", required = false) UUID gradeId,
                                                                        @RequestParam(value = "subjectId", required = false) UUID subjectId,
                                                                        @RequestParam(value = "assetType", required = false) AssetType assetType,
                                                                        @RequestParam(value = "studentId", required = false) UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(service.list(gradeId, subjectId, assetType, studentId)));
    }

    @GetMapping("/{assetId}/content")
    @PreAuthorize(AcademicRoles.STAFF_OR_PARENT)
    public ResponseEntity<byte[]> content(@PathVariable("assetId") UUID assetId) {
        LearningAsset asset = service.asset(assetId);
        byte[] bytes = service.content(asset);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(asset.getFileName() == null ? "asset" : asset.getFileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }

    // ------------------------------------------------------------------ US-06018

    @PostMapping("/{assetId}/watch-progress")
    @PreAuthorize(AcademicRoles.WATCH_TRACK)
    public ResponseEntity<ApiResponse<WatchProgressResponse>> recordProgress(@PathVariable("assetId") UUID assetId,
                                                                             @Valid @RequestBody WatchProgressRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.recordProgress(assetId, request)));
    }

    @GetMapping("/{assetId}/watch-report")
    @PreAuthorize(AcademicRoles.WATCH_REPORT)
    public ResponseEntity<ApiResponse<WatchReportResponse>> watchReport(@PathVariable("assetId") UUID assetId,
                                                                        @RequestParam("sectionId") UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.success(service.watchReport(assetId, sectionId)));
    }
}
