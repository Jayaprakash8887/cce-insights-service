package org.openphc.cce.insights.config;

import org.springframework.context.annotation.Configuration;

/**
 * Metrics configuration. Timers are auto-registered by Micrometer via @Timed or
 * programmatic Timer.builder() at point-of-use. Unused pre-registered beans have been removed.
 */
@Configuration
public class MetricsConfig {
}
