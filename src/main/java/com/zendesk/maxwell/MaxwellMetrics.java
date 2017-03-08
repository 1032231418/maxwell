package com.zendesk.maxwell;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class MaxwellMetrics {
	public static final MetricRegistry registry = new MetricRegistry();

	public static final String metricsName = "MaxwellMetrics";

	static final Logger LOGGER = LoggerFactory.getLogger(MaxwellMetrics.class);

	public static void setup(String metricsReportingType, Long metricsReportingInteval) {
		if (metricsReportingType == null) {
			LOGGER.warn("Metrics will not be exposed: metricsReportingType not configured.");
			return;
		}

		if (metricsReportingType.contains("slf4j")) {
			final Slf4jReporter reporter = Slf4jReporter.forRegistry(registry)
					.outputTo(LOGGER)
					.convertRatesTo(TimeUnit.SECONDS)
					.convertDurationsTo(TimeUnit.MILLISECONDS)
					.build();

			reporter.start(metricsReportingInteval, TimeUnit.SECONDS);
			LOGGER.info("Slf4j metrics reporter enabled");
		}

		if (metricsReportingType.contains("jmx")) {
			final JmxReporter jmxReporter = JmxReporter.forRegistry(registry)
					.convertRatesTo(TimeUnit.SECONDS)
					.convertDurationsTo(TimeUnit.MILLISECONDS)
					.build();
			jmxReporter.start();
			LOGGER.info("Jmx metrics reporter enabled");
		}
	}
}
