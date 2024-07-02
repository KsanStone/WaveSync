package me.ksanstone.wavesync.wavesync.service;

/**
 * Written in java to avoid shl shenanigans
 */
public class ProcessUtil {

    public static int packArgb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int interpolateArgb(float a, float r, float g, float b, float a2, float r2, float g2, float b2, float v) {
        return packArgb(
                (int) ((a + (a2 - a) * v) * 255),
                (int) ((r + (r2 - r) * v) * 255),
                (int) ((g + (g2 - g) * v) * 255),
                (int) ((b + (b2 - b) * v) * 255)
        );
    }
}
