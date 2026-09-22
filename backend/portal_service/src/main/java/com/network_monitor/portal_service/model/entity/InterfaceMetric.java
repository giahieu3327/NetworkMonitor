package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "interface_metrics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterfaceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interface_id", nullable = false)
    private DeviceInterface deviceInterface;

    @Builder.Default
    @Column(name = "bytes_in_bps")
    private Long bytesInBps = 0L;

    @Builder.Default
    @Column(name = "bytes_out_bps")
    private Long bytesOutBps = 0L;

    @Builder.Default
    @Column(name = "packets_in")
    private Long packetsIn = 0L;

    @Builder.Default
    @Column(name = "packets_out")
    private Long packetsOut = 0L;

    @Builder.Default
    @Column(name = "errors_in")
    private Integer errorsIn = 0;

    @Builder.Default
    @Column(name = "errors_out")
    private Integer errorsOut = 0;

    @Builder.Default
    @Column(name = "discards_in")
    private Integer discardsIn = 0;

    @Builder.Default
    @Column(name = "discards_out")
    private Integer discardsOut = 0;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private OffsetDateTime timestamp;
}
