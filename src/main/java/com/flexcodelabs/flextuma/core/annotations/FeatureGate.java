package com.flexcodelabs.flextuma.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as requiring a specific feature to be enabled for the calling
 * user's organisation. If the feature is disabled for the organisation, a 403
 * Forbidden is thrown.
 *
 * <p>
 * Default behaviour: if no
 * {@link com.flexcodelabs.flextuma.core.entities.feature.TenantFeature}
 * record exists for the organisation + key, the feature is
 * <strong>allowed</strong>.
 * Only explicit {@code enabled = false} records block access.
 *
 * <p>
 * Users without an organisation (e.g. SUPER_ADMIN / system users) always bypass
 * the check.
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * {@literal @}FeatureGate("BULK_CAMPAIGN")
 * public void sendCampaign(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureGate {
    /**
     * The feature key that must be enabled, e.g. {@code "BULK_CAMPAIGN"},
     * {@code "WHATSAPP_SEND"}.
     */
    String value();
}
