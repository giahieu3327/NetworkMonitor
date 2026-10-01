package com.network_monitor.portal_service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.util.Base64;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenUtils {

    private static final SecureRandom RANDOM =
            new SecureRandom();

    /**
     * Tạo random token dạng Base64 URL-safe.
     *
     * @param byteLength số byte random
     * @return random token
     */
    public static String generateRandomToken(
            int byteLength
    ) {

        if (byteLength <= 0) {
            throw new IllegalArgumentException(
                    "byteLength phải lớn hơn 0"
            );
        }

        byte[] bytes =
                new byte[byteLength];

        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}