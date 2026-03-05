package com.flexcodelabs.flextuma.core.entities.sms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flexcodelabs.flextuma.core.helpers.MaskingUtil;
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

    public static final String PLURAL = "connectors";
    public static final String NAME_PLURAL = "SmsConnectors";
    public static final String NAME_SINGULAR = "SmsConnector";

    public static final String ALL = "ALL";
    public static final String READ = ALL;
    public static final String ADD = ALL;
    public static final String DELETE = ALL;
    public static final String UPDATE = ALL;

    @NotBlank(message = "Provider name is required")
    private String provider;

    @NotBlank(message = "Url is required")
    private String url;

    @Column(nullable = true)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String key;

    @Column(name = "isdefault")
    private Boolean isDefault = true;

    @Column(nullable = true)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String secret;

    @Column(name = "senderid", nullable = true)
    private String senderId;

    @Column(columnDefinition = "TEXT", name = "extrasettings", nullable = true)
    private String extraSettings;

    @JsonProperty("key")
    public String getMaskedKey() {
        return MaskingUtil.mask(this.key);
    }

    @JsonProperty("secret")
    public String getMaskedSecret() {
        return MaskingUtil.mask(this.secret);
    }
}