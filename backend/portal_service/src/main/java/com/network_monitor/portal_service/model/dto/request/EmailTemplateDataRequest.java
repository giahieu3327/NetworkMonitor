package com.network_monitor.portal_service.model.dto.request;

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

    private String message;

    private String otp;

    private String link;

    private String buttonText;

    private String expireMessage;

    private String footerMessage;
}