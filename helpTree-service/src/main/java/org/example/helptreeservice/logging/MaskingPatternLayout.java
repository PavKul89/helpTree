package org.example.helptreeservice.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.example.helptreeservice.util.SensitiveDataMasker;

/**
 * Console layout that runs every formatted line through the shared masker.
 *
 * <p>Application code must not log secrets or personal data, but a missed
 * statement or a third-party exception message can still contain an email, a
 * phone number, a bearer token or a complaint excerpt. Masking at the very last
 * step covers all of them without changing the message that the application
 * recorded.</p>
 */
public class MaskingPatternLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        return SensitiveDataMasker.maskSensitive(super.doLayout(event));
    }
}
