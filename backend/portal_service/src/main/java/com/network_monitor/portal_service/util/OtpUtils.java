package com.network_monitor.portal_service.util;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;

@UtilityClass
public class OtpUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    public String generateOtp() {
        int max = (int) Math.pow(10, OTP_LENGTH);
        int otp = RANDOM.nextInt(max);

        return String.format(
                "%0" + OTP_LENGTH + "d",
                otp
        );
    }
}