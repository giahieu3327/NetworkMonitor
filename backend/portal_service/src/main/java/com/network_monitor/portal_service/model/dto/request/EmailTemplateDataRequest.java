package com.network_monitor.portal_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplateDataRequest {

    private String title;

    private String userName;

    private String message;

    private String otp;

    private String verificationLink;

    private String buttonText;

    private int expireMinutes;
}