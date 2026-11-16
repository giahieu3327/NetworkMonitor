
package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Thiết bị liên quan, có thể NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    // Quy tắc ngưỡng gây ra incident, có thể NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "threshold_rule_id")
    private ThresholdRule thresholdRule;

    // INFO, WARNING, CRITICAL.
    @Column(
        name = "severity",
        length = 20,
        nullable = false
    )
    private String severity;

    @Column(
        name = "title",
        length = 200,
        nullable = false
    )
    private String title;

    // SQL cho phép NULL.
    @Column(
        name = "description",
        columnDefinition = "text"
    )
    private String description;

    // ID tiến trình Camunda.
    @Column(name = "camunda_process_id", length = 100)
    private String camundaProcessId;

    // OPEN, ACKNOWLEDGED, RESOLVED.
    @Builder.Default
    @Column(
        name = "status",
        length = 30,
        nullable = false
    )
    private String status = "OPEN";

    @Builder.Default
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Chỉ có giá trị khi incident được giải quyết.
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
