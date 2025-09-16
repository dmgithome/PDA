package com.pda.hcw.rfid;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.util.Log;

/**
 * 简化版蜂鸣管理器：
 * - 仅提供最小间隔限流；
 * - 不做队列/平滑/心跳；
 * - 与UHF-G“读到即响”或“业务增量即响”两种模式配合上层调用。
 */
public class RfidBeepManager {
    private static final String TAG = "RfidBeepManager";

    // 音色与时长
    private static final int BEEP_TONE_MULTI = ToneGenerator.TONE_PROP_BEEP;
    private static final int BEEP_TONE_SINGLE = ToneGenerator.TONE_PROP_ACK;
    private static final int BEEP_DURATION_MULTI_MS = 60;   // 多标签短促
    private static final int BEEP_DURATION_SINGLE_MS = 120; // 单标签略长
    private static final int MIN_GAP_MULTI_MS = 40;         // 多标签最小间隔
    private static final int MIN_GAP_SINGLE_MS = 120;       // 单标签最小间隔

    private ToneGenerator toneGenerator;
    private boolean active = false;
    private boolean enabled = true;
    private boolean multiMode = true;
    private long lastBeepAt = 0L;

    public synchronized void start() {
        if (active) return;
        active = true;
        lastBeepAt = 0L;
        try { if (toneGenerator == null) toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); } catch (Throwable ignored) {}
        Log.d(TAG, "beep started");
    }

    public synchronized void stop() {
        if (!active) return;
        active = false;
        Log.d(TAG, "beep stopped");
    }

    public synchronized void release() {
        stop();
        try { if (toneGenerator != null) toneGenerator.release(); } catch (Throwable ignored) {}
        toneGenerator = null;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setMultiMode(boolean multi) { this.multiMode = multi; }

    /**
     * 请求一次蜂鸣：若距上次发声超过最小间隔则发声，否则忽略。
     */
    public void requestBeep() {
        if (!active || !enabled) return;
        int gap = multiMode ? MIN_GAP_MULTI_MS : MIN_GAP_SINGLE_MS;
        long now = System.currentTimeMillis();
        if (now - lastBeepAt < gap) return;
        try {
            if (toneGenerator == null) toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            int tone = multiMode ? BEEP_TONE_MULTI : BEEP_TONE_SINGLE;
            int dur = multiMode ? BEEP_DURATION_MULTI_MS : BEEP_DURATION_SINGLE_MS;
            toneGenerator.startTone(tone, dur);
            lastBeepAt = now;
        } catch (Throwable t) {
            Log.w(TAG, "beep failed", t);
        }
    }

    /**
     * 停止失败提示：连续快速蜂鸣三次。
     */
    public void alarmNotStopped() {
        if (!active || !enabled) return;
        try {
            if (toneGenerator == null) toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            for (int i = 0; i < 3; i++) {
                toneGenerator.startTone(BEEP_TONE_SINGLE, 120);
                Thread.sleep(150);
            }
        } catch (Throwable t) {
            Log.w(TAG, "alarmNotStopped failed", t);
        }
    }
}
