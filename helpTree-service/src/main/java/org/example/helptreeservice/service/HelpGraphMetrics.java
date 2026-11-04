package org.example.helptreeservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Observability for graph construction. The graph itself is a derived view,
 * so its size and build time are the two numbers that tell us whether the
 * in-memory projection needs a database-side subgraph query.
 */
@Service
@RequiredArgsConstructor
public class HelpGraphMetrics {

    private final MeterRegistry meterRegistry;

    public void record(boolean global,
                       long durationNanos,
                       int users,
                       int totalHelps,
                       int returnedEdges,
                       boolean truncated) {
        String scope = global ? "global" : "user";

        Timer.builder("helptree.help.graph.build")
                .description("Time to build the confirmed-help graph projection")
                .tag("scope", scope)
                .register(meterRegistry)
                .record(Duration.ofNanos(durationNanos));

        DistributionSummary.builder("helptree.help.graph.size")
                .description("Confirmed help acts in the selected graph subgraph")
                .baseUnit("helps")
                .tag("scope", scope)
                .register(meterRegistry)
                .record(totalHelps);

        if (truncated) {
            Counter.builder("helptree.help.graph.truncated")
                    .description("Graph responses limited by maxEdges")
                    .tag("scope", scope)
                    .register(meterRegistry)
                    .increment();
        }
    }
}
