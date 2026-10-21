package com.kvs.academic.enums;

/** Browser events reported during a proctored attempt (US-06007); TAB_SWITCH and WINDOW_BLUR count against the limit. */
public enum ProctorEventType {
    TAB_SWITCH,
    WINDOW_BLUR,
    FULLSCREEN_EXIT,
    COPY_PASTE
}
