package ru.strbnm.blocker_service.dto;

import jakarta.annotation.Nullable;

public record CheckResult(boolean isBlocked, @Nullable String reason) {
    public static CheckResult allowed() {
        return new CheckResult(false, null);
    }

    public static CheckResult blocked(String reason) {
        return new CheckResult(true, reason);
    }
}