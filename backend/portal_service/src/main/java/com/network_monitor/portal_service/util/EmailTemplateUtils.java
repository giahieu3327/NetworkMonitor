package com.network_monitor.portal_service.util;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class EmailTemplateUtils {

    public String render(String templatePath, EmailTemplateDataRequest data) {
        try {
            ClassPathResource resource =
                    new ClassPathResource(templatePath);

            if (!resource.exists()) {
                throw new IllegalArgumentException(
                        "Không tìm thấy email template: " + templatePath
                );
            }

            String template;

            try (InputStream inputStream = resource.getInputStream()) {
                template = new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            template = template.replace(
                    "{{title}}",
                    safe(data.getTitle())
            );

            template = template.replace(
                    "{{message}}",
                    safe(data.getMessage())
            );

            template = template.replace(
                    "{{otp}}",
                    safe(data.getOtp())
            );

            template = template.replace(
                    "{{link}}",
                    safe(data.getLink())
            );

            template = template.replace(
                    "{{buttonText}}",
                    safe(data.getButtonText())
            );

            template = template.replace(
                    "{{expireMessage}}",
                    safe(data.getExpireMessage())
            );

            template = template.replace(
                    "{{footerMessage}}",
                    safe(data.getFooterMessage())
            );

            return template;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Không thể render email template.",
                    e
            );
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}