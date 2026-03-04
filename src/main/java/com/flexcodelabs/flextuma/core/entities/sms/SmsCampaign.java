package com.flexcodelabs.flextuma.core.entities.sms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.enums.SmsCampaignStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "smscampaign")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmsCampaign extends Owner {

    public static final String PLURAL = "campaigns";
    public static final String NAME_PLURAL = "SmsCampaigns";
    public static final String NAME_SINGULAR = "SmsCampaign";

    public static final String ALL = "ALL";
    public static final String READ = ALL;
    public static final String ADD = ALL;
    public static final String DELETE = ALL;
    public static final String UPDATE = ALL;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private SmsTemplate template;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SmsCampaignStatus status = SmsCampaignStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String recipients;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_id", nullable = false)
    private SmsConnector connector;

}
