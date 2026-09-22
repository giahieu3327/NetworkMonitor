package com.network_monitor.portal_service.model.entity;

import com.network_monitor.portal_service.model.enums.ChannelType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_name", length = 50, nullable = false)
    private String channelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", length = 20, nullable = false)
    private ChannelType channelType;

    @Column(name = "bot_token", length = 255)
    private String botToken;

    @Column(name = "chat_id", length = 100)
    private String chatId;

    @Column(name = "smtp_host", length = 100)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 100)
    private String smtpUsername;

    @Column(name = "smtp_password", length = 100)
    private String smtpPassword;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
