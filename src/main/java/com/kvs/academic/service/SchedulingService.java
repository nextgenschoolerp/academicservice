package com.kvs.academic.service;

import com.kvs.academic.dto.*;
import com.kvs.academic.entity.BellPeriod;
import com.kvs.academic.entity.BellSchedule;
import com.kvs.academic.entity.FacultyWorkload;
import com.kvs.academic.entity.Section;
import com.kvs.academic.entity.Subject;
import com.kvs.academic.entity.SubjectAllocation;
import com.kvs.academic.entity.TimetableEntry;
import com.kvs.academic.enums.SlotType;
import com.kvs.academic.enums.SubjectType;
import com.kvs.academic.repo.BellPeriodRepository;
import com.kvs.academic.repo.BellScheduleRepository;
import com.kvs.academic.repo.FacultyWorkloadRepository;
import com.kvs.academic.repo.ProxySubstitutionRepository;
import com.kvs.academic.repo.SubjectAllocationRepository;
import com.kvs.academic.repo.SubjectRepository;
import com.kvs.academic.repo.TimetableEntryRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bell schedules (US-06000), the subject catalog, faculty workload caps and subject-teacher allocations (US-06001),
 * the constraint-based timetable generator and manual moves (US-06002) and timetable views (US-06015 uses them for the PDF).
 */
@Service
@Slf4j
@Transactional
public class SchedulingService {

    static final int MIN_PERIOD_MINUTES = 20;
    static final int MAX_SLOT_MINUTES = 120;

    @Autowired private AcademicContext ctx;
    @Autowired private AcademicAuditService audit;
    @Autowired private BellScheduleRepository scheduleRepository;
    @Autowired private BellPeriodRepository periodRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private FacultyWorkloadRepository workloadRepository;
    @Autowired private SubjectAllocationRepository allocationRepository;
    @Autowired private TimetableEntryRepository entryRepository;
    @Autowired private ProxySubstitutionRepository substitutionRepository;

    // ------------------------------------------------------------------ US-06000 bell schedules

    public BellScheduleResponse createSchedule(CreateBellScheduleRequest request) {
        String tenant = ctx.tenant(request.getTenantId());
        List<BellSlotRequest> slots = new ArrayList<>(request.getSlots());
        slots.sort(Comparator.comparing(BellSlotRequest::getStartTime));
        validateSlots(slots);
        List<Integer> days = request.getWorkingDays().stream().distinct().sorted().toList();

        boolean activate = Boolean.TRUE.equals(request.getActivate())
                || scheduleRepository.findByTenantIdAndBranchIdAndActiveTrue(tenant, request.getBranchId()).isEmpty();
        if (activate) {
            deactivateOthers(tenant, request.getBranchId());
        }
        BellSchedule schedule = scheduleRepository.save(BellSchedule.builder()
                .tenantId(tenant).branchId(request.getBranchId()).scheduleName(request.getScheduleName().trim())
                .workingDays(days.stream().map(String::valueOf).collect(Collectors.joining(","))).active(activate).build());
        int sequence = 0;
        int period = 0;
        for (BellSlotRequest slot : slots) {
            periodRepository.save(BellPeriod.builder().bellScheduleId(schedule.getBellScheduleId()).sequenceNo(++sequence)
                    .slotType(slot.getSlotType()).periodNumber(slot.getSlotType() == SlotType.PERIOD ? ++period : null)
                    .label(slot.getLabel().trim()).startTime(slot.getStartTime()).endTime(slot.getEndTime()).build());
        }
        audit.logSuccess("BELL_SCHEDULE", "BELL_SCHEDULE_CREATED", schedule.getScheduleName() + " (" + period + " periods, days " + days + ")");
        return toResponse(schedule);
    }

    /** Slots must not overlap; teaching periods last 20–120 minutes and at least one slot is a teaching period. */
    private static void validateSlots(List<BellSlotRequest> slots) {
        if (slots.stream().noneMatch(s -> s.getSlotType() == SlotType.PERIOD)) {
            throw new BadRequestException("A bell schedule needs at least one teaching period");
        }
        LocalTime previousEnd = null;
        String previousLabel = null;
        for (BellSlotRequest slot : slots) {
            if (!slot.getEndTime().isAfter(slot.getStartTime())) {
                throw new BadRequestException(slot.getLabel() + " must end after it starts");
            }
            long minutes = Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
            if (minutes > MAX_SLOT_MINUTES) {
                throw new BadRequestException(slot.getLabel() + " is longer than " + MAX_SLOT_MINUTES + " minutes");
            }
            if (slot.getSlotType() == SlotType.PERIOD && minutes < MIN_PERIOD_MINUTES) {
                throw new BadRequestException(slot.getLabel() + " is shorter than " + MIN_PERIOD_MINUTES + " minutes");
            }
            if (previousEnd != null && slot.getStartTime().isBefore(previousEnd)) {
                throw new BadRequestException(slot.getLabel() + " overlaps " + previousLabel, "OVERLAPPING_SLOTS");
            }
            previousEnd = slot.getEndTime();
            previousLabel = slot.getLabel();
        }
    }

    @Transactional(readOnly = true)
    public List<BellScheduleResponse> listSchedules(String tenantId, String branchId) {
        String tenant = ctx.tenant(tenantId);
        List<BellSchedule> schedules = branchId == null
                ? scheduleRepository.findByTenantIdOrderByScheduleNameAsc(tenant)
                : scheduleRepository.findByTenantIdAndBranchIdOrderByScheduleNameAsc(tenant, branchId);
        return schedules.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BellScheduleResponse getSchedule(UUID scheduleId) {
        return toResponse(schedule(scheduleId));
    }

    public BellScheduleResponse activate(UUID scheduleId) {
        BellSchedule schedule = schedule(scheduleId);
        deactivateOthers(schedule.getTenantId(), schedule.getBranchId());
        schedule.setActive(true);
        audit.logSuccess("BELL_SCHEDULE", "BELL_SCHEDULE_ACTIVATED", schedule.getScheduleName());
        return toResponse(scheduleRepository.save(schedule));
    }

    private void deactivateOthers(String tenant, String branchId) {
        for (BellSchedule other : scheduleRepository.findByTenantIdAndBranchIdAndActiveTrue(tenant, branchId)) {
            other.setActive(false);
            scheduleRepository.save(other);
        }
    }

    BellSchedule schedule(UUID scheduleId) {
        BellSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Bell schedule", "bellScheduleId", scheduleId));
        ctx.requireTenant(schedule.getTenantId(), "Bell schedule", "bellScheduleId", scheduleId);
        return schedule;
    }

    List<BellPeriod> teachingPeriods(UUID scheduleId) {
        return periodRepository.findByBellScheduleIdOrderBySequenceNoAsc(scheduleId).stream()
                .filter(p -> p.getSlotType() == SlotType.PERIOD).toList();
    }

    static List<Integer> workingDays(BellSchedule schedule) {
        return Arrays.stream(schedule.getWorkingDays().split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::valueOf).toList();
    }

    private BellScheduleResponse toResponse(BellSchedule schedule) {
        List<BellPeriod> slots = periodRepository.findByBellScheduleIdOrderBySequenceNoAsc(schedule.getBellScheduleId());
        List<BellPeriod> teaching = slots.stream().filter(p -> p.getSlotType() == SlotType.PERIOD).toList();
        List<Integer> days = workingDays(schedule);
        return BellScheduleResponse.builder()
                .bellScheduleId(schedule.getBellScheduleId()).tenantId(schedule.getTenantId()).branchId(schedule.getBranchId())
                .scheduleName(schedule.getScheduleName()).workingDays(days).active(schedule.getActive())
                .teachingPeriodsPerDay(teaching.size()).teachingPeriodsPerWeek(teaching.size() * days.size())
                .teachingMinutesPerDay(teaching.stream().mapToLong(p -> Duration.between(p.getStartTime(), p.getEndTime()).toMinutes()).sum())
                .schoolStart(slots.isEmpty() ? null : slots.get(0).getStartTime())
                .schoolEnd(slots.isEmpty() ? null : slots.get(slots.size() - 1).getEndTime())
                .slots(slots.stream().map(p -> BellSlotResponse.builder().sequenceNo(p.getSequenceNo()).slotType(p.getSlotType())
                        .periodNumber(p.getPeriodNumber()).label(p.getLabel()).startTime(p.getStartTime()).endTime(p.getEndTime())
                        .durationMinutes(Duration.between(p.getStartTime(), p.getEndTime()).toMinutes()).build()).toList())
                .build();
    }

    // ------------------------------------------------------------------ US-06001 subjects, workloads, allocations

    public SubjectResponse createSubject(CreateSubjectRequest request) {
        String tenant = ctx.tenant(request.getTenantId());
        if (subjectRepository.findByTenantIdAndSubjectCode(tenant, request.getSubjectCode()).isPresent()) {
            throw new ConflictException("Subject " + request.getSubjectCode() + " already exists", "DUPLICATE_SUBJECT");
        }
        Subject subject = subjectRepository.save(Subject.builder().tenantId(tenant).subjectCode(request.getSubjectCode())
                .subjectName(request.getSubjectName().trim())
                .subjectType(request.getSubjectType() == null ? SubjectType.THEORY : request.getSubjectType()).build());
        audit.logSuccess("SUBJECT", "SUBJECT_CREATED", subject.getSubjectCode());
        return toResponse(subject);
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> listSubjects(String tenantId) {
        return subjectRepository.findByTenantIdOrderBySubjectCodeAsc(ctx.tenant(tenantId)).stream().map(SchedulingService::toResponse).toList();
    }

    static SubjectResponse toResponse(Subject s) {
        return SubjectResponse.builder().subjectId(s.getSubjectId()).subjectCode(s.getSubjectCode()).subjectName(s.getSubjectName())
                .subjectType(s.getSubjectType()).build();
    }

    /** Creates or updates a teacher's caps; lowering a cap below what is already allocated is refused. */
    public FacultyWorkloadResponse saveWorkload(FacultyWorkloadRequest request) {
        String tenant = ctx.tenant(request.getTenantId());
        if (request.getDailyMaxPeriods() > request.getWeeklyMaxPeriods()) {
            throw new BadRequestException("dailyMaxPeriods cannot exceed weeklyMaxPeriods");
        }
        int allocated = allocationRepository.findByTenantIdAndTeacherId(tenant, request.getTeacherId()).stream()
                .mapToInt(SubjectAllocation::getPeriodsPerWeek).sum();
        if (allocated > request.getWeeklyMaxPeriods()) {
            throw new ConflictException(request.getTeacherName() + " already has " + allocated + " periods allocated; the weekly cap cannot be "
                    + request.getWeeklyMaxPeriods(), "WORKLOAD_CAP_EXCEEDED");
        }
        FacultyWorkload workload = workloadRepository.findByTenantIdAndTeacherId(tenant, request.getTeacherId())
                .orElseGet(() -> FacultyWorkload.builder().tenantId(tenant).teacherId(request.getTeacherId()).build());
        boolean created = workload.getWorkloadId() == null;
        workload.setBranchId(request.getBranchId());
        workload.setTeacherName(request.getTeacherName().trim());
        workload.setWeeklyMaxPeriods(request.getWeeklyMaxPeriods());
        workload.setDailyMaxPeriods(request.getDailyMaxPeriods());
        workload.setQualifiedSubjectCodes(request.getQualifiedSubjectCodes() == null ? null
                : request.getQualifiedSubjectCodes().stream().map(String::trim).map(String::toUpperCase).distinct().collect(Collectors.joining(",")));
        workload = workloadRepository.save(workload);
        audit.logSuccess("FACULTY_WORKLOAD", created ? "FACULTY_WORKLOAD_CREATED" : "FACULTY_WORKLOAD_UPDATED",
                workload.getTeacherName() + ": " + workload.getWeeklyMaxPeriods() + "/week, " + workload.getDailyMaxPeriods() + "/day");
        return toResponse(workload);
    }

    @Transactional(readOnly = true)
    public List<FacultyWorkloadResponse> listWorkloads(String tenantId) {
        return workloadRepository.findByTenantIdOrderByTeacherNameAsc(ctx.tenant(tenantId)).stream().map(this::toResponse).toList();
    }

    FacultyWorkload workload(String tenant, String teacherId) {
        return workloadRepository.findByTenantIdAndTeacherId(tenant, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty workload", "teacherId", teacherId));
    }

    static List<String> qualified(FacultyWorkload workload) {
        String codes = workload.getQualifiedSubjectCodes();
        return codes == null || codes.isBlank() ? List.of() : Arrays.asList(codes.split(","));
    }

    private FacultyWorkloadResponse toResponse(FacultyWorkload w) {
        List<SubjectAllocation> allocations = allocationRepository.findByTenantIdAndTeacherId(w.getTenantId(), w.getTeacherId());
        int allocated = allocations.stream().mapToInt(SubjectAllocation::getPeriodsPerWeek).sum();
        int timetabled = entryRepository.findByTenantIdAndTeacherIdOrderByDayOfWeekAscPeriodNumberAsc(w.getTenantId(), w.getTeacherId()).size();
        return FacultyWorkloadResponse.builder().workloadId(w.getWorkloadId()).teacherId(w.getTeacherId()).teacherName(w.getTeacherName())
                .weeklyMaxPeriods(w.getWeeklyMaxPeriods()).dailyMaxPeriods(w.getDailyMaxPeriods()).qualifiedSubjectCodes(qualified(w))
                .allocatedPeriods(allocated).timetabledPeriods(timetabled).remainingPeriods(w.getWeeklyMaxPeriods() - allocated)
                .utilisationPercent(percent(allocated, w.getWeeklyMaxPeriods()))
                .allocations(allocations.stream().map(this::toResponse).toList()).build();
    }

    /** Creates the allocation, or replaces the teacher / periods of the section's existing allocation for that subject. */
    public AllocationResponse allocate(AllocationRequest request) {
        Section section = ctx.section(request.getSectionId());
        Subject subject = ctx.subject(request.getSubjectId());
        String tenant = subject.getTenantId();
        FacultyWorkload workload = workload(tenant, request.getTeacherId());
        List<String> qualified = qualified(workload);
        if (!qualified.isEmpty() && !qualified.contains(subject.getSubjectCode())) {
            throw new ConflictException(workload.getTeacherName() + " is not qualified to teach " + subject.getSubjectCode()
                    + " (qualified: " + String.join(", ", qualified) + ")", "TEACHER_NOT_QUALIFIED");
        }
        SubjectAllocation allocation = allocationRepository.findBySectionIdAndSubjectId(section.getSectionId(), subject.getSubjectId())
                .orElseGet(() -> SubjectAllocation.builder().tenantId(tenant).gradeId(section.getGradeId()).sectionId(section.getSectionId())
                        .subjectId(subject.getSubjectId()).build());
        int alreadyAllocated = allocationRepository.findByTenantIdAndTeacherId(tenant, request.getTeacherId()).stream()
                .filter(a -> !a.getAllocationId().equals(allocation.getAllocationId()))
                .mapToInt(SubjectAllocation::getPeriodsPerWeek).sum();
        if (alreadyAllocated + request.getPeriodsPerWeek() > workload.getWeeklyMaxPeriods()) {
            throw new ConflictException(workload.getTeacherName() + " has " + alreadyAllocated + " of " + workload.getWeeklyMaxPeriods()
                    + " weekly periods allocated; " + request.getPeriodsPerWeek() + " more would exceed the cap", "WORKLOAD_CAP_EXCEEDED");
        }
        allocation.setTeacherId(request.getTeacherId());
        allocation.setPeriodsPerWeek(request.getPeriodsPerWeek());
        SubjectAllocation saved = allocationRepository.save(allocation);
        audit.logSuccess("SUBJECT_ALLOCATION", "SUBJECT_ALLOCATED", ctx.className(section) + " " + subject.getSubjectCode() + " → "
                + workload.getTeacherName() + " (" + request.getPeriodsPerWeek() + "/week)");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> listAllocations(UUID sectionId, String teacherId) {
        if (sectionId != null) {
            ctx.section(sectionId);
            return allocationRepository.findBySectionId(sectionId).stream().map(this::toResponse).toList();
        }
        String tenant = ctx.tenant(null);
        List<SubjectAllocation> list = teacherId == null ? allocationRepository.findByTenantId(tenant)
                : allocationRepository.findByTenantIdAndTeacherId(tenant, teacherId);
        return list.stream().map(this::toResponse).toList();
    }

    public void deleteAllocation(UUID allocationId) {
        SubjectAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation", "allocationId", allocationId));
        ctx.requireTenant(allocation.getTenantId(), "Allocation", "allocationId", allocationId);
        allocationRepository.delete(allocation);
        audit.logSuccess("SUBJECT_ALLOCATION", "SUBJECT_ALLOCATION_REMOVED", allocationId.toString());
    }

    private AllocationResponse toResponse(SubjectAllocation a) {
        Subject subject = subjectRepository.findById(a.getSubjectId()).orElse(null);
        return AllocationResponse.builder().allocationId(a.getAllocationId()).sectionId(a.getSectionId()).className(ctx.className(a.getSectionId()))
                .subjectId(a.getSubjectId()).subjectCode(subject == null ? null : subject.getSubjectCode())
                .subjectName(subject == null ? null : subject.getSubjectName()).teacherId(a.getTeacherId())
                .teacherName(ctx.teacherName(a.getTenantId(), a.getTeacherId())).periodsPerWeek(a.getPeriodsPerWeek()).build();
    }

    // ------------------------------------------------------------------ US-06002 timetable generator

    /**
     * Places every allocated period of the given sections into the bell schedule's teaching slots. Hard constraints:
     * a class has one period per slot; a teacher is never in two places at overlapping times (including other sections'
     * existing timetables); a teacher's daily and weekly caps hold; a subject appears at most N times a day in a class.
     * Periods that cannot be placed are reported, never forced. The sections' previous timetables are replaced, and proxy
     * substitutions from today on for the replaced periods are cleared (they would point at periods that no longer exist).
     */
    public TimetableGenerationResponse generate(GenerateTimetableRequest request) {
        BellSchedule schedule = schedule(request.getBellScheduleId());
        String tenant = schedule.getTenantId();
        List<BellPeriod> periods = teachingPeriods(schedule.getBellScheduleId());
        List<Integer> days = workingDays(schedule);
        int maxPerDay = request.getMaxPeriodsPerSubjectPerDay() == null ? 2 : request.getMaxPeriodsPerSubjectPerDay();
        LinkedHashSet<UUID> sectionIds = new LinkedHashSet<>(request.getSectionIds());
        Map<UUID, Section> sections = new LinkedHashMap<>();
        for (UUID id : sectionIds) {
            Section section = ctx.section(id);
            if (!tenant.equalsIgnoreCase(ctx.tenantOf(section))) {
                throw new ResourceNotFoundException("Section", "sectionId", id);
            }
            sections.put(id, section);
        }
        Map<UUID, Subject> subjects = ctx.subjects(tenant);
        Map<String, FacultyWorkload> workloads = workloadRepository.findByTenantIdOrderByTeacherNameAsc(tenant).stream()
                .collect(Collectors.toMap(FacultyWorkload::getTeacherId, w -> w));

        TeacherCalendar calendar = new TeacherCalendar();
        entryRepository.findByTenantId(tenant).stream().filter(e -> !sectionIds.contains(e.getSectionId())).forEach(calendar::add);

        List<TimetableEntry> created = new ArrayList<>();
        List<SectionGenerationResult> results = new ArrayList<>();
        for (Section section : sections.values()) {
            List<SubjectAllocation> allocations = new ArrayList<>(allocationRepository.findBySectionId(section.getSectionId()));
            allocations.sort(Comparator.comparing(SubjectAllocation::getPeriodsPerWeek).reversed()
                    .thenComparing(a -> subjects.containsKey(a.getSubjectId()) ? subjects.get(a.getSubjectId()).getSubjectCode() : ""));
            boolean[][] taken = new boolean[days.size()][periods.size()];
            List<UnplacedPeriods> unplaced = new ArrayList<>();
            int required = 0;
            int placedTotal = 0;
            for (int a = 0; a < allocations.size(); a++) {
                SubjectAllocation allocation = allocations.get(a);
                required += allocation.getPeriodsPerWeek();
                FacultyWorkload workload = workloads.get(allocation.getTeacherId());
                Subject subject = subjects.get(allocation.getSubjectId());
                String code = subject == null ? "?" : subject.getSubjectCode();
                if (workload == null) {
                    unplaced.add(unplaced(code, allocation, allocation.getTeacherId(), allocation.getPeriodsPerWeek(),
                            "The teacher has no workload profile"));
                    continue;
                }
                int[] perDay = new int[days.size()];
                int placed = 0;
                String reason = null;
                for (int n = 0; n < allocation.getPeriodsPerWeek(); n++) {
                    int[] slot = null;
                    Set<String> blocks = new LinkedHashSet<>();
                    if (calendar.weekly(workload.getTeacherId()) >= workload.getWeeklyMaxPeriods()) {
                        blocks.add("the teacher's weekly cap of " + workload.getWeeklyMaxPeriods() + " periods is reached");
                    } else {
                        for (int d : dayOrder(perDay, taken)) {
                            int day = days.get(d);
                            if (perDay[d] >= maxPerDay) {
                                blocks.add(code + " already has " + maxPerDay + " periods on each remaining day");
                                continue;
                            }
                            if (calendar.daily(workload.getTeacherId(), day) >= workload.getDailyMaxPeriods()) {
                                blocks.add("the teacher's daily cap of " + workload.getDailyMaxPeriods() + " periods is reached");
                                continue;
                            }
                            for (int k = 0; k < periods.size() && slot == null; k++) {
                                int p = (k + a + n) % periods.size();
                                BellPeriod period = periods.get(p);
                                if (taken[d][p]) {
                                    blocks.add("the class has no free period left");
                                } else if (calendar.busy(workload.getTeacherId(), day, period.getStartTime(), period.getEndTime())) {
                                    blocks.add("the teacher is teaching another class whenever this class is free");
                                } else {
                                    slot = new int[]{d, p};
                                }
                            }
                            if (slot != null) {
                                break;
                            }
                        }
                    }
                    if (slot == null) {
                        reason = blocks.isEmpty() ? "No free period" : blocks.iterator().next();
                        break;
                    }
                    BellPeriod period = periods.get(slot[1]);
                    TimetableEntry entry = TimetableEntry.builder().tenantId(tenant).bellScheduleId(schedule.getBellScheduleId())
                            .gradeId(section.getGradeId()).sectionId(section.getSectionId()).subjectId(allocation.getSubjectId())
                            .teacherId(workload.getTeacherId()).dayOfWeek(days.get(slot[0])).periodNumber(period.getPeriodNumber())
                            .startTime(period.getStartTime()).endTime(period.getEndTime()).build();
                    created.add(entry);
                    calendar.add(entry);
                    taken[slot[0]][slot[1]] = true;
                    perDay[slot[0]]++;
                    placed++;
                }
                placedTotal += placed;
                if (placed < allocation.getPeriodsPerWeek()) {
                    unplaced.add(unplaced(code, allocation, workload.getTeacherName(), allocation.getPeriodsPerWeek() - placed, reason));
                }
            }
            int capacity = days.size() * periods.size();
            results.add(SectionGenerationResult.builder().sectionId(section.getSectionId()).className(ctx.className(section))
                    .periodsRequired(required).periodsPlaced(placedTotal).freePeriods(capacity - placedTotal).unplaced(unplaced).build());
        }
        // Upcoming proxies point at the periods being replaced; past ones stay as the record of who covered what
        List<UUID> replaced = entryRepository.findBySectionIdIn(sectionIds).stream().map(TimetableEntry::getTimetableId).toList();
        long cleared = replaced.isEmpty() ? 0
                : substitutionRepository.deleteByTimetableIdInAndSubstitutionDateGreaterThanEqual(replaced, LocalDate.now());
        entryRepository.deleteBySectionIdIn(sectionIds);
        entryRepository.saveAll(created);

        int required = results.stream().mapToInt(SectionGenerationResult::getPeriodsRequired).sum();
        int placed = results.stream().mapToInt(SectionGenerationResult::getPeriodsPlaced).sum();
        audit.logSuccess("TIMETABLE", "TIMETABLE_GENERATED", sections.size() + " sections, " + placed + " of " + required + " periods placed"
                + (cleared > 0 ? ", " + cleared + " upcoming substitutions cleared" : ""));
        log.info("Generated timetables for {} sections: {}/{} periods", sections.size(), placed, required);
        return TimetableGenerationResponse.builder().bellScheduleId(schedule.getBellScheduleId()).sectionsScheduled(sections.size())
                .periodsRequired(required).periodsPlaced(placed).conflictFree(true).sections(results)
                .substitutionsCleared((int) cleared).build();
    }

    /** Days with the fewest periods of this subject first, then the class's least loaded days, so subjects spread across the week. */
    private static List<Integer> dayOrder(int[] perDay, boolean[][] taken) {
        List<Integer> order = new ArrayList<>();
        for (int d = 0; d < perDay.length; d++) {
            order.add(d);
        }
        order.sort(Comparator.<Integer>comparingInt(d -> perDay[d]).thenComparingInt(d -> load(taken[d])).thenComparingInt(d -> d));
        return order;
    }

    private static int load(boolean[] day) {
        int n = 0;
        for (boolean b : day) {
            n += b ? 1 : 0;
        }
        return n;
    }

    private static UnplacedPeriods unplaced(String code, SubjectAllocation allocation, String teacherName, int missing, String reason) {
        return UnplacedPeriods.builder().subjectCode(code).teacherId(allocation.getTeacherId()).teacherName(teacherName)
                .missingPeriods(missing).reason(reason).build();
    }

    /** Teaching commitments per teacher and day, checked by time overlap so schedules with different bell times still clash. */
    static final class TeacherCalendar {
        private final Map<String, List<LocalTime[]>> slots = new HashMap<>();
        private final Map<String, Integer> weekly = new HashMap<>();

        void add(TimetableEntry e) {
            add(e.getTeacherId(), e.getDayOfWeek(), e.getStartTime(), e.getEndTime());
        }

        void add(String teacherId, int day, LocalTime start, LocalTime end) {
            slots.computeIfAbsent(teacherId + "|" + day, k -> new ArrayList<>()).add(new LocalTime[]{start, end});
            weekly.merge(teacherId, 1, Integer::sum);
        }

        boolean busy(String teacherId, int day, LocalTime start, LocalTime end) {
            return slots.getOrDefault(teacherId + "|" + day, List.of()).stream().anyMatch(s -> overlaps(s[0], s[1], start, end));
        }

        int daily(String teacherId, int day) {
            return slots.getOrDefault(teacherId + "|" + day, List.of()).size();
        }

        int weekly(String teacherId) {
            return weekly.getOrDefault(teacherId, 0);
        }
    }

    static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /** Moves a period (and optionally gives it to another teacher), refusing any clash the generator would have avoided. */
    public TimetableEntryResponse moveEntry(UUID timetableId, MoveTimetableEntryRequest request) {
        TimetableEntry entry = entryRepository.findById(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable period", "timetableId", timetableId));
        ctx.requireTenant(entry.getTenantId(), "Timetable period", "timetableId", timetableId);
        BellSchedule schedule = schedule(entry.getBellScheduleId());
        if (!workingDays(schedule).contains(request.getDayOfWeek())) {
            throw new BadRequestException(dayName(request.getDayOfWeek()) + " is not a working day of " + schedule.getScheduleName());
        }
        BellPeriod period = teachingPeriods(schedule.getBellScheduleId()).stream()
                .filter(p -> p.getPeriodNumber().equals(request.getPeriodNumber())).findFirst()
                .orElseThrow(() -> new BadRequestException("Period " + request.getPeriodNumber() + " does not exist in " + schedule.getScheduleName()));
        String teacherId = request.getTeacherId() == null || request.getTeacherId().isBlank() ? entry.getTeacherId() : request.getTeacherId();
        FacultyWorkload workload = workload(entry.getTenantId(), teacherId);
        Subject subject = ctx.subject(entry.getSubjectId());
        if (!teacherId.equals(entry.getTeacherId())) {
            List<String> qualified = qualified(workload);
            if (!qualified.isEmpty() && !qualified.contains(subject.getSubjectCode())) {
                throw new ConflictException(workload.getTeacherName() + " is not qualified to teach " + subject.getSubjectCode(), "TEACHER_NOT_QUALIFIED");
            }
        }
        boolean slotTaken = entryRepository.findBySectionIdAndDayOfWeek(entry.getSectionId(), request.getDayOfWeek()).stream()
                .anyMatch(e -> !e.getTimetableId().equals(timetableId) && e.getPeriodNumber().equals(request.getPeriodNumber()));
        if (slotTaken) {
            throw new ConflictException(ctx.className(entry.getSectionId()) + " already has a period at " + dayName(request.getDayOfWeek())
                    + " period " + request.getPeriodNumber(), "SECTION_SLOT_TAKEN");
        }
        List<TimetableEntry> teacherDay = entryRepository.findByTenantIdAndTeacherIdOrderByDayOfWeekAscPeriodNumberAsc(entry.getTenantId(), teacherId)
                .stream().filter(e -> !e.getTimetableId().equals(timetableId) && e.getDayOfWeek().equals(request.getDayOfWeek())).toList();
        teacherDay.stream().filter(e -> overlaps(e.getStartTime(), e.getEndTime(), period.getStartTime(), period.getEndTime())).findFirst()
                .ifPresent(clash -> {
                    throw new ConflictException(workload.getTeacherName() + " already teaches " + ctx.className(clash.getSectionId()) + " at "
                            + dayName(request.getDayOfWeek()) + " " + clash.getStartTime(), "TEACHER_DOUBLE_BOOKED");
                });
        if (teacherDay.size() >= workload.getDailyMaxPeriods()) {
            throw new ConflictException(workload.getTeacherName() + " already has " + workload.getDailyMaxPeriods() + " periods on "
                    + dayName(request.getDayOfWeek()), "DAILY_CAP_EXCEEDED");
        }
        entry.setDayOfWeek(request.getDayOfWeek());
        entry.setPeriodNumber(period.getPeriodNumber());
        entry.setStartTime(period.getStartTime());
        entry.setEndTime(period.getEndTime());
        entry.setTeacherId(teacherId);
        TimetableEntry saved = entryRepository.save(entry);
        audit.logSuccess("TIMETABLE", "TIMETABLE_PERIOD_MOVED", ctx.className(entry.getSectionId()) + " " + subject.getSubjectCode() + " → "
                + dayName(saved.getDayOfWeek()) + " P" + saved.getPeriodNumber() + " (" + workload.getTeacherName() + ")");
        return toResponse(saved, ctx.subjects(entry.getTenantId()), ctx.teacherNames(entry.getTenantId()));
    }

    // ------------------------------------------------------------------ timetable views

    @Transactional(readOnly = true)
    public ClassTimetableResponse sectionTimetable(UUID sectionId) {
        Section section = ctx.section(sectionId);
        String tenant = ctx.tenantOf(section);
        List<TimetableEntry> entries = entryRepository.findBySectionIdOrderByDayOfWeekAscPeriodNumberAsc(sectionId);
        Map<UUID, Subject> subjects = ctx.subjects(tenant);
        Map<String, String> names = ctx.teacherNames(tenant);
        return ClassTimetableResponse.builder().sectionId(sectionId).className(ctx.className(section))
                .bellScheduleId(entries.isEmpty() ? null : entries.get(0).getBellScheduleId()).periodsPerWeek(entries.size())
                .entries(entries.stream().map(e -> toResponse(e, subjects, names)).toList()).build();
    }

    @Transactional(readOnly = true)
    public ClassTimetableResponse studentTimetable(UUID studentId) {
        var student = ctx.student(studentId);
        if (student.getSectionId() == null) {
            throw new ConflictException(AcademicContext.studentName(student) + " is not placed in a section yet", "NO_SECTION");
        }
        return sectionTimetable(student.getSectionId());
    }

    @Transactional(readOnly = true)
    public TeacherTimetableResponse teacherTimetable(String teacherId) {
        String tenant = ctx.tenant(null);
        return teacherTimetable(tenant, teacherId, null);
    }

    TeacherTimetableResponse teacherTimetable(String tenant, String teacherId, UUID bellScheduleId) {
        FacultyWorkload workload = workload(tenant, teacherId);
        Map<UUID, Subject> subjects = ctx.subjects(tenant);
        Map<String, String> names = ctx.teacherNames(tenant);
        List<TimetableEntry> entries = entryRepository.findByTenantIdAndTeacherIdOrderByDayOfWeekAscPeriodNumberAsc(tenant, teacherId).stream()
                .filter(e -> bellScheduleId == null || bellScheduleId.equals(e.getBellScheduleId()))
                .sorted(Comparator.comparing(TimetableEntry::getDayOfWeek).thenComparing(TimetableEntry::getStartTime)).toList();
        return TeacherTimetableResponse.builder().teacherId(teacherId).teacherName(workload.getTeacherName()).periodsPerWeek(entries.size())
                .weeklyMaxPeriods(workload.getWeeklyMaxPeriods()).entries(entries.stream().map(e -> toResponse(e, subjects, names)).toList()).build();
    }

    /** US-06015: every teacher's weekly timetable for one bell schedule, as a PDF for the staff room noticeboard (the export is audited). */
    public byte[] facultyTimetablePdf(UUID bellScheduleId, String teacherId) {
        BellSchedule schedule = schedule(bellScheduleId);
        String tenant = schedule.getTenantId();
        List<FacultyWorkload> teachers = workloadRepository.findByTenantIdOrderByTeacherNameAsc(tenant).stream()
                .filter(w -> teacherId == null || teacherId.equals(w.getTeacherId())).toList();
        if (teachers.isEmpty()) {
            throw new ResourceNotFoundException("Faculty workload", "teacherId", teacherId == null ? "(any)" : teacherId);
        }
        List<TeacherTimetableResponse> timetables = teachers.stream()
                .map(t -> teacherTimetable(tenant, t.getTeacherId(), bellScheduleId)).toList();
        byte[] pdf = FacultyTimetablePdfRenderer.render(toResponse(schedule), timetables);
        audit.logSuccess("TIMETABLE", "FACULTY_TIMETABLE_EXPORTED", schedule.getScheduleName() + ", " + timetables.size() + " teachers");
        return pdf;
    }

    TimetableEntryResponse toResponse(TimetableEntry e, Map<UUID, Subject> subjects, Map<String, String> teacherNames) {
        Subject subject = subjects.get(e.getSubjectId());
        return TimetableEntryResponse.builder().timetableId(e.getTimetableId()).dayOfWeek(e.getDayOfWeek()).dayName(dayName(e.getDayOfWeek()))
                .periodNumber(e.getPeriodNumber()).startTime(e.getStartTime()).endTime(e.getEndTime()).sectionId(e.getSectionId())
                .className(ctx.className(e.getSectionId())).subjectId(e.getSubjectId())
                .subjectCode(subject == null ? null : subject.getSubjectCode()).subjectName(subject == null ? null : subject.getSubjectName())
                .teacherId(e.getTeacherId()).teacherName(teacherNames.getOrDefault(e.getTeacherId(), e.getTeacherId())).build();
    }

    static String dayName(int dayOfWeek) {
        return DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    static BigDecimal percent(long part, long whole) {
        return whole == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }
}
