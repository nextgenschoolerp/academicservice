package com.kvs.academic.service;

import com.kvs.academic.dto.BellScheduleResponse;
import com.kvs.academic.dto.BellSlotResponse;
import com.kvs.academic.dto.TeacherTimetableResponse;
import com.kvs.academic.dto.TimetableEntryResponse;
import com.kvs.academic.enums.SlotType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders master faculty timetables (US-06015): one landscape A4 page per teacher, slots of the bell schedule down the side
 * (breaks shaded) and working days across, each cell showing the class and subject. Pure rendering; no persistence.
 */
public final class FacultyTimetablePdfRenderer {

    private static final float MARGIN = 36;
    private static final float LABEL_WIDTH = 110;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private FacultyTimetablePdfRenderer() {
    }

    public static byte[] render(BellScheduleResponse schedule, List<TeacherTimetableResponse> teachers) {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        String generated = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(STAMP);
        List<Integer> days = schedule.getWorkingDays();
        List<BellSlotResponse> slots = schedule.getSlots();

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Master faculty timetable - " + schedule.getScheduleName());
            for (TeacherTimetableResponse teacher : teachers) {
                PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                document.addPage(page);
                float width = page.getMediaBox().getWidth();
                float top = page.getMediaBox().getHeight() - MARGIN;
                float columnWidth = (width - 2 * MARGIN - LABEL_WIDTH) / Math.max(1, days.size());
                float rowHeight = Math.min(34, (top - 70 - MARGIN) / Math.max(1, slots.size() + 1));
                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    text(cs, bold, 15, MARGIN, top, teacher.getTeacherName());
                    text(cs, regular, 9, MARGIN, top - 15, schedule.getScheduleName() + "   |   " + teacher.getPeriodsPerWeek() + " periods a week (cap "
                            + teacher.getWeeklyMaxPeriods() + ")");
                    float y = top - 40;
                    // header row
                    text(cs, bold, 9, MARGIN + 4, y - 13, "Period");
                    for (int d = 0; d < days.size(); d++) {
                        text(cs, bold, 9, MARGIN + LABEL_WIDTH + d * columnWidth + 4, y - 13, SchedulingService.dayName(days.get(d)));
                    }
                    line(cs, MARGIN, y, width - MARGIN, y);
                    y -= rowHeight;
                    line(cs, MARGIN, y, width - MARGIN, y);
                    for (BellSlotResponse slot : slots) {
                        boolean teaching = slot.getSlotType() == SlotType.PERIOD;
                        if (!teaching) {
                            cs.setNonStrokingColor(0.92f, 0.92f, 0.92f);
                            cs.addRect(MARGIN, y - rowHeight, width - 2 * MARGIN, rowHeight);
                            cs.fill();
                            cs.setNonStrokingColor(0f, 0f, 0f);
                        }
                        text(cs, teaching ? bold : regular, 8, MARGIN + 4, y - 12, slot.getLabel());
                        text(cs, regular, 7, MARGIN + 4, y - 22, slot.getStartTime().format(TIME) + " - " + slot.getEndTime().format(TIME));
                        if (teaching) {
                            for (int d = 0; d < days.size(); d++) {
                                int day = days.get(d);
                                TimetableEntryResponse cell = teacher.getEntries().stream()
                                        .filter(e -> e.getDayOfWeek() == day && e.getPeriodNumber().equals(slot.getPeriodNumber())).findFirst().orElse(null);
                                if (cell != null) {
                                    float x = MARGIN + LABEL_WIDTH + d * columnWidth + 4;
                                    text(cs, bold, 8, x, y - 12, cell.getClassName());
                                    text(cs, regular, 7, x, y - 22, cell.getSubjectCode() == null ? "" : cell.getSubjectCode());
                                }
                            }
                        }
                        y -= rowHeight;
                        line(cs, MARGIN, y, width - MARGIN, y);
                    }
                    float bottom = y;
                    float headerTop = top - 40;
                    line(cs, MARGIN, headerTop, MARGIN, bottom);
                    for (int d = 0; d <= days.size(); d++) {
                        float x = MARGIN + LABEL_WIDTH + d * columnWidth;
                        line(cs, x, headerTop, x, bottom);
                    }
                    text(cs, regular, 7, MARGIN, MARGIN - 12, "Generated " + generated + "   Substitutions for absent staff are posted separately each morning.");
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render faculty timetable PDF", e);
        }
    }

    private static void line(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.setLineWidth(0.5f);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(winAnsiSafe(font, value == null ? "" : value));
        cs.endText();
    }

    /** Standard-14 fonts only cover WinAnsi; replace anything else so a name never breaks the export. */
    private static String winAnsiSafe(PDType1Font font, String value) {
        StringBuilder sb = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }
}
