package com.ambacoding.ambatap.service.shizuku;

oneway interface ITouchEventListener {
    /** Baris getevent sampai dan termasuk satu SYN_REPORT. */
    void onLines(in String[] lines);

    void onClosed(String reason);
}
