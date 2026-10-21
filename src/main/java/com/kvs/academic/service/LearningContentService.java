package com.kvs.academic.service;

import com.kvs.academic.dto.*;
import com.kvs.academic.entity.Grade;
import com.kvs.academic.entity.LearningAsset;
import com.kvs.academic.entity.Section;
import com.kvs.academic.entity.StudentMaster;
import com.kvs.academic.entity.Subject;
import com.kvs.academic.entity.VideoWatchProgress;
import com.kvs.academic.enums.AssetType;
import com.kvs.academic.repo.LearningAssetRepository;
import com.kvs.academic.repo.VideoWatchProgressRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * E-learning assets (US-06005) and lecture video watch-time tracking (US-06018). A video counts as completed once the
 * student has reached 90% of its length; skipping ahead does not count as watching because only the furthest position
 * reached in continuous playback reports (position ≤ previous furthest + reported watched seconds) moves completion.
 */
@Service
@Transactional
public class LearningContentService {

    static final BigDecimal COMPLETION_THRESHOLD = BigDecimal.valueOf(90);
    static final String CONTENT_PATH = "/api/v1/lms/assets/%s/content";

    @Autowired private AcademicContext ctx;
    @Autowired private AcademicAuditService audit;
    @Autowired private AssetStore store;
    @Autowired private LearningAssetRepository assetRepository;
    @Autowired private VideoWatchProgressRepository watchRepository;

    public LearningAssetResponse upload(MultipartFile file, String title, UUID gradeId, UUID subjectId, String chapter, AssetType declared,
                                        Integer durationSeconds) {
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new BadRequestException("title is required (up to 200 characters)");
        }
        Grade grade = ctx.grade(gradeId);
        Subject subject = ctx.subject(subjectId);
        AssetType type = store.typeOf(file.getContentType());
        if (declared != null && declared != type) {
            throw new BadRequestException("assetType " + declared + " does not match the file type " + file.getContentType(), "CONTENT_MISMATCH");
        }
        if (type == AssetType.VIDEO && (durationSeconds == null || durationSeconds < 1)) {
            throw new BadRequestException("durationSeconds is required for a lecture video");
        }
        AssetStore.StoredAsset stored = store.store(grade.getTenantId(), file);
        LearningAsset asset = assetRepository.save(LearningAsset.builder().tenantId(grade.getTenantId()).gradeId(gradeId).subjectId(subjectId)
                .chapter(chapter).title(title.trim()).assetType(type).fileName(safeName(file.getOriginalFilename()))
                .contentType(file.getContentType()).sizeBytes(file.getSize()).storagePath(stored.relativePath())
                .checksumSha256(stored.checksumSha256()).durationSeconds(type == AssetType.VIDEO ? durationSeconds : null)
                .uploadedBy(ctx.actor()).build());
        audit.logSuccess("LEARNING_ASSET", "LEARNING_ASSET_UPLOADED", type + " '" + asset.getTitle() + "' for " + grade.getGradeName() + " "
                + subject.getSubjectCode() + " (" + file.getSize() + " bytes)");
        return toResponse(asset, subject);
    }

    public LearningAssetResponse addLink(CreateAssetLinkRequest request) {
        Grade grade = ctx.grade(request.getGradeId());
        Subject subject = ctx.subject(request.getSubjectId());
        AssetType type = request.getAssetType() == null ? AssetType.LINK : request.getAssetType();
        if (type != AssetType.LINK && type != AssetType.VIDEO) {
            throw new BadRequestException("A link can only be a LINK or a VIDEO; upload PDFs, e-books and SCORM packages as files");
        }
        if (type == AssetType.VIDEO && request.getDurationSeconds() == null) {
            throw new BadRequestException("durationSeconds is required for a lecture video");
        }
        LearningAsset asset = assetRepository.save(LearningAsset.builder().tenantId(grade.getTenantId()).gradeId(grade.getGradeId())
                .subjectId(subject.getSubjectId()).chapter(request.getChapter()).title(request.getTitle().trim()).assetType(type)
                .externalUrl(request.getUrl()).durationSeconds(type == AssetType.VIDEO ? request.getDurationSeconds() : null)
                .uploadedBy(ctx.actor()).build());
        audit.logSuccess("LEARNING_ASSET", "LEARNING_ASSET_LINKED", type + " '" + asset.getTitle() + "'");
        return toResponse(asset, subject);
    }

    /** Assets of a class; a parent passes their child's id and sees the child's class. */
    @Transactional(readOnly = true)
    public List<LearningAssetResponse> list(UUID gradeId, UUID subjectId, AssetType type, UUID studentId) {
        UUID grade = gradeId;
        if (studentId != null) {
            grade = ctx.student(studentId).getGradeId();
        } else if (ctx.isParentOnly()) {
            throw new BadRequestException("studentId is required");
        }
        if (grade == null) {
            throw new BadRequestException("gradeId or studentId is required");
        }
        Grade g = ctx.grade(grade);
        Map<UUID, Subject> subjects = ctx.subjects(g.getTenantId());
        return assetRepository.findByTenantIdAndGradeIdOrderByCreatedAtDesc(g.getTenantId(), grade).stream()
                .filter(a -> subjectId == null || subjectId.equals(a.getSubjectId()))
                .filter(a -> type == null || type == a.getAssetType())
                .map(a -> toResponse(a, subjects.get(a.getSubjectId()))).toList();
    }

    @Transactional(readOnly = true)
    public LearningAsset asset(UUID assetId) {
        LearningAsset asset = assetRepository.findById(assetId).orElseThrow(() -> new ResourceNotFoundException("Learning asset", "assetId", assetId));
        ctx.requireTenant(asset.getTenantId(), "Learning asset", "assetId", assetId);
        if (ctx.isParentOnly() && ctx.ownChildren().stream().noneMatch(s -> asset.getGradeId().equals(s.getGradeId()))) {
            throw new ForbiddenException("This asset is for another class");
        }
        return asset;
    }

    @Transactional(readOnly = true)
    public byte[] content(LearningAsset asset) {
        if (asset.getStoragePath() == null) {
            throw new ConflictException("This asset is an external link: " + asset.getExternalUrl(), "EXTERNAL_ASSET");
        }
        return store.read(asset.getStoragePath());
    }

    // ------------------------------------------------------------------ US-06018 watch time

    public WatchProgressResponse recordProgress(UUID assetId, WatchProgressRequest request) {
        LearningAsset asset = asset(assetId);
        if (asset.getAssetType() != AssetType.VIDEO || asset.getDurationSeconds() == null) {
            throw new BadRequestException("Watch time is tracked for lecture videos only");
        }
        StudentMaster student = ctx.student(request.getStudentId());
        if (!asset.getGradeId().equals(student.getGradeId())) {
            throw new BadRequestException("This video is for another class");
        }
        int duration = asset.getDurationSeconds();
        int watched = request.getWatchedSeconds() == null ? 0 : request.getWatchedSeconds();
        VideoWatchProgress progress = watchRepository.findByAssetIdAndStudentId(assetId, student.getStudentId())
                .orElseGet(() -> VideoWatchProgress.builder().assetId(assetId).studentId(student.getStudentId()).build());
        int position = Math.min(request.getPositionSeconds(), duration);
        // Only continuous playback moves the furthest point: a jump past what was actually watched is ignored
        int reachable = progress.getFurthestPositionSeconds() + watched;
        progress.setFurthestPositionSeconds(Math.max(progress.getFurthestPositionSeconds(), Math.min(position, reachable)));
        progress.setWatchedSeconds(progress.getWatchedSeconds() + watched);
        progress.setCompletionPercent(BigDecimal.valueOf(progress.getFurthestPositionSeconds() * 100.0 / duration).setScale(2, RoundingMode.HALF_UP));
        boolean wasCompleted = Boolean.TRUE.equals(progress.getCompleted());
        progress.setCompleted(progress.getCompletionPercent().compareTo(COMPLETION_THRESHOLD) >= 0);
        progress.setLastWatchedAt(Instant.now());
        VideoWatchProgress saved = watchRepository.save(progress);
        if (!wasCompleted && saved.getCompleted()) {
            audit.logSuccess("VIDEO_WATCH", "VIDEO_COMPLETED", asset.getTitle() + " by " + AcademicContext.studentName(student));
        }
        return WatchProgressResponse.builder().assetId(assetId).studentId(student.getStudentId()).watchedSeconds(saved.getWatchedSeconds())
                .furthestPositionSeconds(saved.getFurthestPositionSeconds()).durationSeconds(duration)
                .completionPercent(saved.getCompletionPercent()).completed(saved.getCompleted()).lastWatchedAt(saved.getLastWatchedAt()).build();
    }

    @Transactional(readOnly = true)
    public WatchReportResponse watchReport(UUID assetId, UUID sectionId) {
        LearningAsset asset = asset(assetId);
        Section section = ctx.section(sectionId);
        if (!section.getGradeId().equals(asset.getGradeId())) {
            throw new BadRequestException(ctx.className(section) + " is not the class this video is for");
        }
        Map<UUID, VideoWatchProgress> progress = watchRepository.findByAssetId(assetId).stream()
                .collect(Collectors.toMap(VideoWatchProgress::getStudentId, p -> p));
        List<WatchReportRow> rows = ctx.roster(sectionId).stream().map(s -> {
            VideoWatchProgress p = progress.get(s.getStudentId());
            return WatchReportRow.builder().studentId(s.getStudentId()).studentName(AcademicContext.studentName(s)).rollNumber(s.getRollNumber())
                    .watchedSeconds(p == null ? 0 : p.getWatchedSeconds()).completionPercent(p == null ? BigDecimal.ZERO : p.getCompletionPercent())
                    .completed(p != null && p.getCompleted()).lastWatchedAt(p == null ? null : p.getLastWatchedAt()).build();
        }).toList();
        int started = (int) rows.stream().filter(r -> r.getWatchedSeconds() > 0).count();
        int completed = (int) rows.stream().filter(WatchReportRow::getCompleted).count();
        BigDecimal average = rows.isEmpty() ? BigDecimal.ZERO : rows.stream().map(WatchReportRow::getCompletionPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(rows.size()), 1, RoundingMode.HALF_UP);
        return WatchReportResponse.builder().assetId(assetId).title(asset.getTitle()).durationSeconds(asset.getDurationSeconds())
                .sectionId(sectionId).className(ctx.className(section)).students(rows.size()).started(started).completed(completed)
                .completionRatePercent(SchedulingService.percent(completed, rows.size())).averageCompletionPercent(average).rows(rows).build();
    }

    // ------------------------------------------------------------------ helpers

    LearningAssetResponse toResponse(LearningAsset a, Subject subject) {
        return LearningAssetResponse.builder().assetId(a.getAssetId()).title(a.getTitle()).assetType(a.getAssetType()).gradeId(a.getGradeId())
                .subjectId(a.getSubjectId()).subjectCode(subject == null ? null : subject.getSubjectCode()).chapter(a.getChapter())
                .fileName(a.getFileName()).contentType(a.getContentType()).sizeBytes(a.getSizeBytes()).checksumSha256(a.getChecksumSha256())
                .externalUrl(a.getExternalUrl()).durationSeconds(a.getDurationSeconds())
                .contentUrl(a.getStoragePath() == null ? a.getExternalUrl() : String.format(CONTENT_PATH, a.getAssetId()))
                .uploadedBy(a.getUploadedBy()).uploadedAt(a.getCreatedAt()).build();
    }

    /** Keeps only the last path segment of a client file name and drops control characters. */
    private static String safeName(String name) {
        if (name == null) {
            return null;
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "");
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }
}
