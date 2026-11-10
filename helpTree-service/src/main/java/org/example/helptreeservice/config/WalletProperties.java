package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.wallet")
public class WalletProperties {

    /**
     * Export is a single response, so an unbounded history could exhaust
     * memory. A user who exceeds this must narrow the date range.
     */
    private int exportMaxRows = 50_000;

    /** Cron for the VIP expiry reminder sweep. */
    private String vipReminderCron = "0 0 10 * * *";

    /** Comma-separated days-before-expiry at which reminders are sent. */
    private String vipReminderDays = "7,3,1";

    /** Maximum number of VIP users inspected per sweep. */
    private int vipReminderBatchSize = 500;
}
