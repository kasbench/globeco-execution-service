package org.kasbench.globeco_execution_service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class MetricsDebugConfig {

    private static final Logger logger = LoggerFactory.getLogger(MetricsDebugConfig.class);
    
    private final MeterRegistry meterRegistry;
    
    public MetricsDebugConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        logger.info("MetricsDebugConfig initialized with MeterRegistry: {}", meterRegistry.getClass().getSimpleName());
    }

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        logger.info("Application ready. Checking available metrics...");
        
        // List all available metrics
        var meterNames = meterRegistry.getMeters().stream()
            .map(meter -> meter.getId().getName())
            .filter(name -> name.contains("tomcat"))
            .distinct()
            .sorted()
            .toList();
            
        logger.info("Found {} Tomcat-related metrics:", meterNames.size());
        meterNames.forEach(name -> logger.info("  - {}", name));
        
        // Check if we have the specific metrics we want
        boolean hasTomcatThreadsBusy = meterNames.stream().anyMatch(name -> name.contains("threads") && name.contains("busy"));
        boolean hasTomcatThreadsCurrent = meterNames.stream().anyMatch(name -> name.contains("threads") && name.contains("current"));
        
        logger.info("Has tomcat threads busy metric: {}", hasTomcatThreadsBusy);
        logger.info("Has tomcat threads current metric: {}", hasTomcatThreadsCurrent);
        
        // List all metrics that contain "thread" 
        var threadMetrics = meterRegistry.getMeters().stream()
            .map(meter -> meter.getId().getName())
            .filter(name -> name.toLowerCase().contains("thread"))
            .distinct()
            .sorted()
            .toList();
            
        logger.info("All thread-related metrics ({}):", threadMetrics.size());
        threadMetrics.forEach(name -> logger.info("  - {}", name));
    }
}
