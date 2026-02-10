package com.flexcodelabs.flextuma.core.entities.sms;

import com.flexcodelabs.flextuma.core.entities.base.Owner;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "smsconnector", uniqueConstraints = {
        @UniqueConstraint(name = "unique_provider_url", columnNames = { "provider", "url", "creator" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SmsConnector extends Owner {

    public static final String PLURAL = "smsConnectors";
    public static final String NAME_PLURAL = "SMS Connectors";
    public static final String NAME_SINGULAR = "SMS Connector";
    public static final String READ = "READ_SMS_CONNECTORS";
    public static final String ADD = "ADD_SMS_CONNECTORS";
    public static final String DELETE = "DELETE_SMS_CONNECTORS";
    public static final String UPDATE = "UPDATE_SMS_CONNECTORS";

    @NotBlank(message = "Provider name is required")
    private String provider;

    @NotBlank(message = "Url is required")
    private String url;

    @Column(nullable = true)
    private String key;

    @Column(nullable = true)
    private String secret;

    @Column(name = "senderid", nullable = true)
    private String senderId;

    @Column(columnDefinition = "TEXT", name = "extrasettings", nullable = true)
    private String extraSettings;
}