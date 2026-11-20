package com.network_monitor.portal_service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NameUtils {

    public static String[] splitFullName(String fullName) {

        if (fullName == null || fullName.isBlank()) {
            return new String[]{"", ""};
        }

        String normalized = fullName.trim().replaceAll("\\s+", " ");

        int lastSpaceIndex = normalized.lastIndexOf(" ");

        if (lastSpaceIndex == -1) {
            return new String[]{normalized, ""};
        }

        String firstName = normalized.substring(0, lastSpaceIndex);
        String lastName = normalized.substring(lastSpaceIndex + 1);

        return new String[]{firstName, lastName};
    }
}
