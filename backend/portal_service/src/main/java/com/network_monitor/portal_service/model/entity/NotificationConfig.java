package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "channel_name",
            length = 255,
            nullable = false,
            unique = true
    )
    private String channelName;

    @Column(
            name = "channel_type",
            length = 100,
            nullable = false
    )
    private String channelType;

    @Column(
            name = "bot_token",
            columnDefinition = "TEXT"
    )
    private String botToken;

    @Column(
            name = "chat_id",
            length = 255
    )
    private String chatId;

    @Column(
            name = "smtp_host",
            length = 255
    )
    private String smtpHost;

    @Builder.Default
    @Column(
            name = "smtp_port",
            nullable = false
    )
    private Integer smtpPort = 587;

    @Column(
            name = "smtp_username",
            length = 255
    )
    private String smtpUsername;

    @Column(
            name = "smtp_password",
            columnDefinition = "TEXT"
    )
    private String smtpPassword;

    @Builder.Default
    @Column(
            name = "is_active",
            nullable = false
    )
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(
            name = "updated_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}