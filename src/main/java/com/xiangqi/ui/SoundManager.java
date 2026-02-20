package com.xiangqi.ui;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.net.URL;

/**
 * 本地音效管理（桌面版）
 */
public final class SoundManager {
    private static final SoundManager INSTANCE = new SoundManager();

    private static final String MOVE_SOUND = "/audio/move.wav";
    private static final String MATE_SOUND = "/audio/mate.wav";

    private Clip moveClip;
    private Clip mateClip;
    private boolean initialized;
    private volatile boolean enabled = true;

    private SoundManager() {
    }

    public static SoundManager getInstance() {
        return INSTANCE;
    }

    public synchronized void preload() {
        if (initialized) {
            return;
        }
        moveClip = loadClip(MOVE_SOUND);
        mateClip = loadClip(MATE_SOUND);
        initialized = true;
    }

    public synchronized void playMove() {
        if (!enabled) {
            return;
        }
        if (!initialized) {
            preload();
        }
        play(moveClip);
    }

    public synchronized void playMate() {
        if (!enabled) {
            return;
        }
        if (!initialized) {
            preload();
        }
        play(mateClip);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private Clip loadClip(String resourcePath) {
        try {
            URL url = SoundManager.class.getResource(resourcePath);
            if (url == null) {
                return null;
            }
            AudioInputStream inputStream = AudioSystem.getAudioInputStream(url);
            Clip clip = AudioSystem.getClip();
            clip.open(inputStream);
            inputStream.close();
            return clip;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void play(Clip clip) {
        if (clip == null) {
            return;
        }
        try {
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.setFramePosition(0);
            clip.start();
        } catch (Exception ignored) {
            // 音效异常不影响对局
        }
    }
}
