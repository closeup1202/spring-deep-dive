package com.exam.jvmheap.memory;

/** 바이트 수를 로그에서 읽을 수 있는 형태로 바꾼다. */
public final class Bytes {

    private Bytes() {
    }

    public static String human(long bytes) {
        if (bytes < 0) {
            return "-" + human(-bytes);
        }
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return "%.1fKB".formatted(bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return "%.1fMB".formatted(bytes / (1024.0 * 1024));
        }
        return "%.2fGB".formatted(bytes / (1024.0 * 1024 * 1024));
    }

    public static long mb(long megabytes) {
        return megabytes * 1024 * 1024;
    }
}
