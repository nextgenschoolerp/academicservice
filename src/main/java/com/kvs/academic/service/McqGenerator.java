package com.kvs.academic.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates fill-in-the-blank multiple-choice questions from chapter text (US-06006). This is the platform's built-in
 * generator, which needs no external AI service: each usable sentence loses one key term (a proper noun phrase, a number or a long
 * content word) and the distractors are other key terms of the same kind from the chapter (numbers get nearby numbers).
 * Output is deterministic for the same text, so a teacher can regenerate and get the same draft.
 */
public final class McqGenerator {

    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern PROPER = Pattern.compile("\\b([A-Z][a-z]+(?:\\s+(?:of\\s+)?[A-Z][a-z]+)*)\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{2,4}(?:\\.\\d+)?)\\b");
    private static final Pattern WORD = Pattern.compile("\\b([a-z][a-z-]{6,})\\b");
    private static final Set<String> STOP = Set.of("because", "between", "through", "however", "another", "without", "therefore",
            "although", "important", "different", "several", "usually", "including", "example", "called", "process", "certain",
            "whereas", "whether", "against", "towards", "becomes", "provide", "provides", "contain", "contains", "include", "includes");
    private static final Set<String> SENTENCE_STARTERS = Set.of("The", "A", "An", "This", "These", "Those", "It", "In", "On", "At", "When",
            "Its", "Their", "They", "There", "Most", "Some", "Many", "All", "Each", "During", "After", "Before", "For", "As");

    private McqGenerator() {
    }

    public record Generated(String questionText, List<String> options, int correctIndex, String explanation) {
    }

    public record Result(List<Generated> questions, int sentencesScanned) {
    }

    private enum Kind { PROPER, NUMBER, WORD }

    private record Term(String text, Kind kind) {
    }

    public static Result generate(String chapterText, int count) {
        String text = chapterText.replaceAll("\\s+", " ").trim();
        List<String> sentences = Arrays.stream(SENTENCE.split(text)).map(String::trim)
                .filter(s -> {
                    int words = s.split(" ").length;
                    return words >= 6 && words <= 45;
                }).toList();

        Map<Kind, LinkedHashMap<String, Integer>> pool = new EnumMap<>(Kind.class);
        for (Kind k : Kind.values()) {
            pool.put(k, new LinkedHashMap<>());
        }
        for (String s : sentences) {
            for (Term t : terms(s)) {
                pool.get(t.kind()).merge(t.text(), 1, Integer::sum);
            }
        }

        List<Generated> out = new ArrayList<>();
        Set<String> usedAnswers = new HashSet<>();
        for (String sentence : sentences) {
            if (out.size() >= count) {
                break;
            }
            Term answer = terms(sentence).stream()
                    .filter(t -> !usedAnswers.contains(t.text().toLowerCase(Locale.ROOT)))
                    .filter(t -> occurrences(sentence, t.text()) == 1)
                    .min(Comparator.comparingInt((Term t) -> t.kind().ordinal()).thenComparing(t -> -t.text().length()))
                    .orElse(null);
            if (answer == null) {
                continue;
            }
            List<String> distractors = distractors(answer, sentence, pool);
            if (distractors.size() < 3) {
                continue;
            }
            List<String> options = new ArrayList<>(distractors.subList(0, 3));
            options.add(answer.text());
            Collections.shuffle(options, new Random(sentence.hashCode()));
            String blanked = sentence.replaceFirst("\\b" + Pattern.quote(answer.text()) + "\\b", "______");
            out.add(new Generated("Fill in the blank: " + blanked, options, options.indexOf(answer.text()), sentence));
            usedAnswers.add(answer.text().toLowerCase(Locale.ROOT));
        }
        return new Result(out, sentences.size());
    }

    private static List<Term> terms(String sentence) {
        List<Term> terms = new ArrayList<>();
        String firstWord = sentence.split(" ")[0].replaceAll("\\W", "");
        Matcher proper = PROPER.matcher(sentence);
        while (proper.find()) {
            String phrase = proper.group(1);
            // the sentence's first word is capitalised anyway; drop it from the phrase (or skip a lone starter word)
            if (proper.start() == 0) {
                String[] words = phrase.split(" ", 2);
                if (SENTENCE_STARTERS.contains(words[0]) || words.length == 1) {
                    if (words.length == 1) {
                        continue;
                    }
                    phrase = words[1].replaceFirst("^of ", "");
                }
            }
            if (phrase.length() >= 4 && !phrase.equals(firstWord)) {
                terms.add(new Term(phrase, Kind.PROPER));
            }
        }
        Matcher number = NUMBER.matcher(sentence);
        while (number.find()) {
            terms.add(new Term(number.group(1), Kind.NUMBER));
        }
        Matcher word = WORD.matcher(sentence);
        while (word.find()) {
            if (!STOP.contains(word.group(1)) && !word.group(1).endsWith("ly")) {
                terms.add(new Term(word.group(1), Kind.WORD));
            }
        }
        return terms;
    }

    private static int occurrences(String sentence, String term) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(term) + "\\b").matcher(sentence);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static List<String> distractors(Term answer, String sentence, Map<Kind, LinkedHashMap<String, Integer>> pool) {
        if (answer.kind() == Kind.NUMBER) {
            return nearbyNumbers(answer.text(), sentence, pool.get(Kind.NUMBER).keySet());
        }
        String lowerSentence = sentence.toLowerCase(Locale.ROOT);
        return pool.get(answer.kind()).entrySet().stream()
                .map(Map.Entry::getKey)
                .filter(t -> !t.equalsIgnoreCase(answer.text()) && !lowerSentence.contains(t.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparingInt((String t) -> Math.abs(t.length() - answer.text().length())).thenComparing(t -> t))
                .limit(3).collect(Collectors.toList());
    }

    /** Other numbers from the chapter first, then plausible neighbours (a year ±, a quantity scaled). */
    private static List<String> nearbyNumbers(String answer, String sentence, Set<String> chapterNumbers) {
        LinkedHashSet<String> picks = new LinkedHashSet<>();
        for (String n : chapterNumbers) {
            if (!n.equals(answer) && !sentence.contains(n) && n.length() == answer.length()) {
                picks.add(n);
            }
        }
        if (!answer.contains(".")) {
            long value = Long.parseLong(answer);
            long[] offsets = answer.length() == 4 && value > 1000 && value < 2100 ? new long[]{-5, 3, 10, -12, 7}
                    : new long[]{value / 2, -value / 4, value, value / 3};
            for (long offset : offsets) {
                long candidate = value + offset;
                if (candidate > 0 && candidate != value) {
                    picks.add(String.valueOf(candidate));
                }
            }
        }
        return picks.stream().limit(3).collect(Collectors.toList());
    }
}
