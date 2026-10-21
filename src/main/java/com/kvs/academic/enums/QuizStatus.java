package com.kvs.academic.enums;

/** Quiz lifecycle (US-06006, US-06014): questions and rules change only while DRAFT. */
public enum QuizStatus {
    DRAFT,
    PUBLISHED,
    CLOSED
}
