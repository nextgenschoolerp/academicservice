package com.kvs.academic.service;

import com.kvs.academic.dto.*;
import com.kvs.academic.entity.FacultyAbsence;
import com.kvs.academic.entity.FacultyWorkload;
import com.kvs.academic.entity.ProxySubstitution;
import com.kvs.academic.entity.Subject;
import com.kvs.academic.entity.SubjectAllocation;
import com.kvs.academic.entity.TimetableEntry;
import com.kvs.academic.enums.AssignmentMode;
import com.kvs.academic.enums.SubstitutionStatus;
import com.kvs.academic.repo.FacultyAbsenceRepository;
import com.kvs.academic.repo.FacultyWorkloadRepository;
import com.kvs.academic.repo.ProxySubstitutionRepository;
import com.kvs.academic.repo.SubjectAllocationRepository;
import com.kvs.academic.repo.TimetableEntryRepository;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Morning roll call absences and proxy substitution (US-06003). For every period an absent teacher has that day, the
 * optimiser picks a teacher who is free at that time, not absent and under their daily cap; it prefers teachers qualified
 * for the subject, then those who already teach the class, then whoever has covered the fewest proxies in the last 7 days.
 */
@Service
@Slf4j
@Transactional
public class SubstitutionService {

    @Autowired private AcademicContext ctx;
    @Autowired private AcademicAuditService audit;
    @Autowired private FacultyAbsenceRepository absenceRepository;
    @Autowired private ProxySubstitutionRepository substitutionRepository;
    @Autowired private TimetableEntryRepository entryRepository;
    @Autowired private FacultyWorkloadRepository workloadRepository;
    @Autowired private SubjectAllocationRepository allocationRepository;

    public AbsenceResponse reportAbsence(ReportAbsenceRequest request) {
        String tenant = ctx.tenant(null);
        FacultyWorkload teacher = workloadRepository.findByTenantIdAndTeacherId(tenant, request.getTeacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Faculty workload", "teacherId", request.getTeacherId()));
        if (absenceRepository.findByTenantIdAndTeacherIdAndAbsenceDate(tenant, request.getTeacherId(), request.getAbsenceDate()).isPresent()) {
            throw new ConflictException(teacher.getTeacherName() + " is already marked absent on " + request.getAbsenceDate(), "ABSENCE_ALREADY_REPORTED");
        }
        FacultyAbsence absence = absenceRepository.save(FacultyAbsence.builder().tenantId(tenant).teacherId(request.getTeacherId())
                .absenceDate(request.getAbsenceDate()).reason(request.getReason()).build());
        audit.logSuccess("FACULTY_ABSENCE", "FACULTY_ABSENCE_REPORTED", teacher.getTeacherName() + " on " + request.getAbsenceDate());
        return toResponse(absence, teacher.getTeacherName());
    }

    @Transactional(readOnly = true)
    public List<AbsenceResponse> listAbsences(LocalDate date) {
        String tenant = ctx.tenant(null);
        Map<String, String> names = ctx.teacherNames(tenant);
        return absenceRepository.findByTenantIdAndAbsenceDate(tenant, date).stream()
                .map(a -> toResponse(a, names.getOrDefault(a.getTeacherId(), a.getTeacherId()))).toList();
    }

    private AbsenceResponse toResponse(FacultyAbsence a, String name) {
        int periods = entryRepository.findByTenantIdAndDayOfWeek(a.getTenantId(), a.getAbsenceDate().getDayOfWeek().getValue()).stream()
                .filter(e -> e.getTeacherId().equals(a.getTeacherId())).toList().size();
        return AbsenceResponse.builder().absenceId(a.getAbsenceId()).teacherId(a.getTeacherId()).teacherName(name)
                .absenceDate(a.getAbsenceDate()).reason(a.getReason()).periodsAffected(periods).build();
    }

    /** Assigns a proxy to every period of the day's absent teachers that has none yet; already assigned periods are kept. */
    public SubstitutionDayResponse autoAssign(LocalDate date) {
        String tenant = ctx.tenant(null);
        Day day = loadDay(tenant, date);
        int assigned = 0;
        int uncovered = 0;
        List<TimetableEntry> toCover = day.entries.stream().filter(e -> day.absent.contains(e.getTeacherId()))
                .sorted(Comparator.comparing(TimetableEntry::getStartTime).thenComparing(e -> ctx.className(e.getSectionId()))).toList();
        for (TimetableEntry entry : toCover) {
            ProxySubstitution existing = day.byTimetable.get(entry.getTimetableId());
            if (existing != null && existing.getStatus() == SubstitutionStatus.ASSIGNED) {
                continue;
            }
            ProxySubstitution substitution = existing != null ? existing : ProxySubstitution.builder().tenantId(tenant)
                    .timetableId(entry.getTimetableId()).substitutionDate(date).build();
            substitution.setAbsentTeacherId(entry.getTeacherId());
            substitution.setAssignmentMode(AssignmentMode.AUTO);
            Candidate best = bestCandidate(day, entry);
            if (best == null) {
                substitution.setProxyTeacherId(null);
                substitution.setStatus(SubstitutionStatus.UNCOVERED);
                substitution.setMatchReason("No teacher is free and under their daily cap in period " + entry.getPeriodNumber());
                uncovered++;
            } else {
                substitution.setProxyTeacherId(best.workload.getTeacherId());
                substitution.setStatus(SubstitutionStatus.ASSIGNED);
                substitution.setMatchReason(best.reason());
                day.book(best.workload.getTeacherId(), entry);
                assigned++;
            }
            day.byTimetable.put(entry.getTimetableId(), substitutionRepository.save(substitution));
        }
        audit.logSuccess("PROXY_SUBSTITUTION", "PROXIES_AUTO_ASSIGNED", date + ": " + assigned + " assigned, " + uncovered + " uncovered");
        log.info("Proxy run for {}: {} assigned, {} uncovered", date, assigned, uncovered);
        return daySheet(tenant, date);
    }

    @Transactional(readOnly = true)
    public SubstitutionDayResponse daySheet(LocalDate date) {
        return daySheet(ctx.tenant(null), date);
    }

    /** Hands the period to another teacher (checked like the optimiser does), or cancels the substitution. */
    public SubstitutionResponse reassign(UUID proxyId, ReassignProxyRequest request) {
        ProxySubstitution substitution = substitutionRepository.findById(proxyId)
                .orElseThrow(() -> new ResourceNotFoundException("Substitution", "proxyId", proxyId));
        ctx.requireTenant(substitution.getTenantId(), "Substitution", "proxyId", proxyId);
        TimetableEntry entry = entryRepository.findById(substitution.getTimetableId())
                .orElseThrow(() -> new ResourceNotFoundException("Timetable period", "timetableId", substitution.getTimetableId()));
        if (Boolean.TRUE.equals(request.getCancel())) {
            substitution.setStatus(SubstitutionStatus.CANCELLED);
            substitution.setProxyTeacherId(null);
            substitution.setAssignmentMode(AssignmentMode.MANUAL);
            substitution.setMatchReason("Cancelled by " + ctx.actor());
        } else {
            if (request.getProxyTeacherId() == null || request.getProxyTeacherId().isBlank()) {
                throw new com.kvs.core.exception.BadRequestException("proxyTeacherId is required unless cancel is true");
            }
            Day day = loadDay(substitution.getTenantId(), substitution.getSubstitutionDate());
            day.unbook(substitution);
            FacultyWorkload proxy = workloadRepository.findByTenantIdAndTeacherId(substitution.getTenantId(), request.getProxyTeacherId())
                    .orElseThrow(() -> new ResourceNotFoundException("Faculty workload", "teacherId", request.getProxyTeacherId()));
            String blocker = day.blocker(proxy, entry);
            if (blocker != null) {
                throw new ConflictException(proxy.getTeacherName() + " cannot take this period: " + blocker, "PROXY_NOT_AVAILABLE");
            }
            substitution.setProxyTeacherId(proxy.getTeacherId());
            substitution.setStatus(SubstitutionStatus.ASSIGNED);
            substitution.setAssignmentMode(AssignmentMode.MANUAL);
            substitution.setMatchReason("Chosen by " + ctx.actor());
        }
        ProxySubstitution saved = substitutionRepository.save(substitution);
        audit.logSuccess("PROXY_SUBSTITUTION", Boolean.TRUE.equals(request.getCancel()) ? "PROXY_CANCELLED" : "PROXY_REASSIGNED",
                proxyId + " → " + saved.getProxyTeacherId());
        return toResponse(saved, entry, ctx.subjects(saved.getTenantId()), ctx.teacherNames(saved.getTenantId()));
    }

    // ------------------------------------------------------------------ the day's commitments

    private Day loadDay(String tenant, LocalDate date) {
        Day day = new Day(date);
        day.absent = absenceRepository.findByTenantIdAndAbsenceDate(tenant, date).stream().map(FacultyAbsence::getTeacherId)
                .collect(Collectors.toSet());
        day.entries = entryRepository.findByTenantIdAndDayOfWeek(tenant, date.getDayOfWeek().getValue());
        day.entriesById = day.entries.stream().collect(Collectors.toMap(TimetableEntry::getTimetableId, e -> e));
        day.workloads = workloadRepository.findByTenantIdOrderByTeacherNameAsc(tenant);
        day.subjects = ctx.subjects(tenant);
        for (TimetableEntry e : day.entries) {
            day.teaching.computeIfAbsent(e.getTeacherId(), k -> new ArrayList<>()).add(e);
        }
        for (ProxySubstitution s : substitutionRepository.findByTenantIdAndSubstitutionDate(tenant, date)) {
            day.byTimetable.put(s.getTimetableId(), s);
            TimetableEntry e = day.entriesById.get(s.getTimetableId());
            if (s.getStatus() == SubstitutionStatus.ASSIGNED && s.getProxyTeacherId() != null && e != null) {
                day.proxies.computeIfAbsent(s.getProxyTeacherId(), k -> new ArrayList<>()).add(e);
            }
        }
        for (ProxySubstitution s : substitutionRepository.findByTenantIdAndSubstitutionDateBetween(tenant, date.minusDays(6), date)) {
            if (s.getStatus() == SubstitutionStatus.ASSIGNED && s.getProxyTeacherId() != null) {
                day.weekProxies.merge(s.getProxyTeacherId(), 1, Integer::sum);
            }
        }
        for (SubjectAllocation a : allocationRepository.findByTenantId(tenant)) {
            day.teachesSection.add(a.getTeacherId() + "|" + a.getSectionId());
            Subject subject = day.subjects.get(a.getSubjectId());
            if (subject != null) {
                day.teachesSubject.add(a.getTeacherId() + "|" + subject.getSubjectCode());
            }
        }
        return day;
    }

    private Candidate bestCandidate(Day day, TimetableEntry entry) {
        Subject subject = day.subjects.get(entry.getSubjectId());
        String code = subject == null ? "" : subject.getSubjectCode();
        return day.workloads.stream()
                .filter(w -> day.blocker(w, entry) == null)
                .map(w -> new Candidate(w,
                        SchedulingService.qualified(w).contains(code) || day.teachesSubject.contains(w.getTeacherId() + "|" + code),
                        day.teachesSection.contains(w.getTeacherId() + "|" + entry.getSectionId()),
                        day.weekProxies.getOrDefault(w.getTeacherId(), 0), day.load(w.getTeacherId()), code, entry.getPeriodNumber()))
                .min(Comparator.comparing((Candidate c) -> !c.qualified).thenComparing(c -> !c.knowsClass)
                        .thenComparingInt(c -> c.weekProxies).thenComparingInt(c -> c.dayLoad).thenComparing(c -> c.workload.getTeacherName()))
                .orElse(null);
    }

    private SubstitutionDayResponse daySheet(String tenant, LocalDate date) {
        Day day = loadDay(tenant, date);
        Map<String, String> names = ctx.teacherNames(tenant);
        List<SubstitutionResponse> rows = day.byTimetable.values().stream()
                .filter(s -> day.entriesById.containsKey(s.getTimetableId()))
                .map(s -> toResponse(s, day.entriesById.get(s.getTimetableId()), day.subjects, names))
                .sorted(Comparator.comparing(SubstitutionResponse::getStartTime).thenComparing(SubstitutionResponse::getClassName))
                .toList();
        long toCover = day.entries.stream().filter(e -> day.absent.contains(e.getTeacherId())).count();
        return SubstitutionDayResponse.builder().date(date).dayName(SchedulingService.dayName(date.getDayOfWeek().getValue()))
                .absentTeachers(day.absent.size()).periodsToCover((int) toCover)
                .assigned((int) rows.stream().filter(r -> r.getStatus() == SubstitutionStatus.ASSIGNED).count())
                .uncovered((int) rows.stream().filter(r -> r.getStatus() == SubstitutionStatus.UNCOVERED).count())
                .substitutions(rows).build();
    }

    private SubstitutionResponse toResponse(ProxySubstitution s, TimetableEntry e, Map<UUID, Subject> subjects, Map<String, String> names) {
        Subject subject = subjects.get(e.getSubjectId());
        return SubstitutionResponse.builder().proxyId(s.getProxyId()).substitutionDate(s.getSubstitutionDate()).periodNumber(e.getPeriodNumber())
                .startTime(e.getStartTime()).endTime(e.getEndTime()).sectionId(e.getSectionId()).className(ctx.className(e.getSectionId()))
                .subjectCode(subject == null ? null : subject.getSubjectCode()).absentTeacherId(s.getAbsentTeacherId())
                .absentTeacherName(names.getOrDefault(s.getAbsentTeacherId(), s.getAbsentTeacherId())).proxyTeacherId(s.getProxyTeacherId())
                .proxyTeacherName(s.getProxyTeacherId() == null ? null : names.getOrDefault(s.getProxyTeacherId(), s.getProxyTeacherId()))
                .status(s.getStatus()).assignmentMode(s.getAssignmentMode()).matchReason(s.getMatchReason()).build();
    }

    private record Candidate(FacultyWorkload workload, boolean qualified, boolean knowsClass, int weekProxies, int dayLoad,
                             String subjectCode, int period) {
        String reason() {
            List<String> parts = new ArrayList<>();
            parts.add(qualified ? "Teaches " + subjectCode : "Free teacher (not a " + subjectCode + " teacher)");
            if (knowsClass) {
                parts.add("already teaches this class");
            }
            parts.add("free in period " + period);
            parts.add(weekProxies + " prox" + (weekProxies == 1 ? "y" : "ies") + " in the last 7 days");
            return String.join("; ", parts);
        }
    }

    /** Who teaches or covers what on one date. */
    private static final class Day {
        final LocalDate date;
        Set<String> absent = Set.of();
        List<TimetableEntry> entries = List.of();
        Map<UUID, TimetableEntry> entriesById = Map.of();
        List<FacultyWorkload> workloads = List.of();
        Map<UUID, Subject> subjects = Map.of();
        final Map<String, List<TimetableEntry>> teaching = new HashMap<>();
        final Map<String, List<TimetableEntry>> proxies = new HashMap<>();
        final Map<UUID, ProxySubstitution> byTimetable = new HashMap<>();
        final Map<String, Integer> weekProxies = new HashMap<>();
        final Set<String> teachesSection = new HashSet<>();
        final Set<String> teachesSubject = new HashSet<>();

        Day(LocalDate date) {
            this.date = date;
        }

        int load(String teacherId) {
            return teaching.getOrDefault(teacherId, List.of()).size() + proxies.getOrDefault(teacherId, List.of()).size();
        }

        /** Why the teacher cannot cover the period, or null when they can. */
        String blocker(FacultyWorkload w, TimetableEntry entry) {
            String id = w.getTeacherId();
            if (id.equals(entry.getTeacherId()) || absent.contains(id)) {
                return "they are absent on " + date;
            }
            boolean busy = teaching.getOrDefault(id, List.of()).stream()
                    .anyMatch(e -> SchedulingService.overlaps(e.getStartTime(), e.getEndTime(), entry.getStartTime(), entry.getEndTime()));
            if (busy) {
                return "they teach another class at " + entry.getStartTime();
            }
            boolean covering = proxies.getOrDefault(id, List.of()).stream()
                    .anyMatch(e -> SchedulingService.overlaps(e.getStartTime(), e.getEndTime(), entry.getStartTime(), entry.getEndTime()));
            if (covering) {
                return "they already cover another class at " + entry.getStartTime();
            }
            if (load(id) >= w.getDailyMaxPeriods()) {
                return "they already have " + w.getDailyMaxPeriods() + " periods that day";
            }
            return null;
        }

        void book(String teacherId, TimetableEntry entry) {
            proxies.computeIfAbsent(teacherId, k -> new ArrayList<>()).add(entry);
            weekProxies.merge(teacherId, 1, Integer::sum);
        }

        void unbook(ProxySubstitution s) {
            if (s.getProxyTeacherId() != null) {
                proxies.getOrDefault(s.getProxyTeacherId(), new ArrayList<>()).removeIf(e -> e.getTimetableId().equals(s.getTimetableId()));
            }
        }
    }
}
