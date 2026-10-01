package com.network_monitor.portal_service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OtpUtils {

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final int OTP_LENGTH = 6;

    public static String generateOtp() {

        int min = 100_000;
        int max = 999_999;

        return String.valueOf(
                RANDOM.nextInt(max - min + 1) + min
        );
    }
}