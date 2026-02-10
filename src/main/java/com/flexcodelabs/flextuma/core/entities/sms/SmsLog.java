package com.flexcodelabs.flextuma.core.entities.sms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "smslog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@JsonInclude(JsonInclude.Include.NON_NULL)

public class SmsLog extends BaseEntity {

    public static final String PLURAL = "smsLogs";
    public static final String NAME_PLURAL = "SMS Logs";
    public static final String NAME_SINGULAR = "SMS Log";
    public static final String READ = "READ_SMS_TEMPLATES";
    public static final String ADD = "ADD_SMS_LOGS";
    public static final String DELETE = "DELETE_SMS_LOGS";
    public static final String UPDATE = "UPDATE_SMS_LOGS";

    private String recipient;

    @Column(columnDefinition = "TEXT", name = "sentcontent")
    private String sentContent;

    @Column(columnDefinition = "TEXT", name = "status")
    private String status;

    @Column(columnDefinition = "TEXT", name = "providerresponse")
    private String providerResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template")
    private SmsTemplate template;

}
