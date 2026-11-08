package org.example.helptreeservice.exception;

import lombok.Getter;
import org.example.helptreeservice.enums.FeatureFlag;

/**
 * Raised when an endpoint belongs to a feature that an operator disabled.
 *
 * <p>This is a deliberate, temporary outage of one product area, not a bug in
 * the request: the global handler maps it to {@code 503} and the stable
 * {@code FEATURE_DISABLED} code so a client can distinguish it from a generic
 * server error.</p>
 */
@Getter
public class FeatureDisabledException extends RuntimeException {

    private final FeatureFlag feature;

    public FeatureDisabledException(FeatureFlag feature) {
        super("Функция временно отключена: " + feature.getTitle());
        this.feature = feature;
    }
}
