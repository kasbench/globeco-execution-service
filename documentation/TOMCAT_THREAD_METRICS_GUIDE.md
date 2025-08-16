# Tomcat Thread Metrics Implementation Guide

This guide provides step-by-step instructions for adding Tomcat thread pool metrics to Spring Boot Java microservices without breaking existing custom metrics.

## Overview

The implementation adds four key Tomcat thread pool metrics:
- `tomcat.threads.busy` - Number of active/busy threads
- `tomcat.threads.current` - Current number of threads in pool
- `tomcat.threads.config.max` - Maximum configured threads
- `tomcat.threads.queue.size` - Size of the thread pool queue

## Prerequisites

- Spring Boot 3.x application with embedded Tomcat
- Spring Boot Actuator dependency
- Micrometer metrics support

## Step 1: Create the TomcatThreadMetricsConfig Class

Create a new file: `src/main/java/{your.package}/TomcatThreadMetricsConfig.java`

```java
package {your.package}; // Replace with your actual package

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration  
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TomcatThreadMetricsConfig {

    private static final Logger logger = LoggerFactory.getLogger(TomcatThreadMetricsConfig.class);
    private final MeterRegistry meterRegistry;

    public TomcatThreadMetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        logger.info("TomcatThreadMetricsConfig initialized with MeterRegistry: {}", meterRegistry.getClass().getSimpleName());
    }

    @EventListener
    public void onWebServerInitialized(WebServerInitializedEvent event) {
        if (event.getWebServer() instanceof TomcatWebServer tomcatWebServer) {
            logger.info("TomcatWebServer detected, registering thread pool metrics");
            registerThreadPoolMetrics(tomcatWebServer);
        } else {
            logger.info("Not a TomcatWebServer: {}", event.getWebServer().getClass().getSimpleName());
        }
    }
    
    private void registerThreadPoolMetrics(TomcatWebServer tomcatWebServer) {
        try {
            var tomcat = tomcatWebServer.getTomcat();
            var server = tomcat.getServer();
            var services = server.findServices();
            
            logger.info("Found {} Tomcat services", services.length);
            
            for (var service : services) {
                var connectors = service.findConnectors();
                logger.info("Found {} connectors in service", connectors.length);
                
                for (var connector : connectors) {
                    try {
                        var protocolHandler = connector.getProtocolHandler();
                        logger.info("Processing connector on port {} with protocol handler: {}", 
                                   connector.getPort(), protocolHandler.getClass().getSimpleName());
                        
                        // Get the executor
                        Method getExecutorMethod = protocolHandler.getClass().getMethod("getExecutor");
                        Object executor = getExecutorMethod.invoke(protocolHandler);
                        
                        logger.info("Executor type: {}", executor != null ? executor.getClass().getName() : "null");
                        
                        // Check if it's a ThreadPoolExecutor or extends it
                        if (executor != null && isThreadPoolExecutor(executor)) {
                            logger.info("Found compatible ThreadPoolExecutor, registering metrics using reflection");
                            
                            try {
                                // Use reflection to call methods since we can't cast to java.util.concurrent.ThreadPoolExecutor
                                Method getActiveCountMethod = executor.getClass().getMethod("getActiveCount");
                                Method getPoolSizeMethod = executor.getClass().getMethod("getPoolSize");
                                Method getMaximumPoolSizeMethod = executor.getClass().getMethod("getMaximumPoolSize");
                                Method getQueueMethod = executor.getClass().getMethod("getQueue");
                                
                                // Register the metrics using reflection-based suppliers
                                meterRegistry.gauge("tomcat.threads.busy", executor, 
                                    exec -> {
                                        try {
                                            return ((Number) getActiveCountMethod.invoke(exec)).doubleValue();
                                        } catch (Exception e) {
                                            logger.warn("Failed to get active count", e);
                                            return 0.0;
                                        }
                                    });
                                    
                                meterRegistry.gauge("tomcat.threads.current", executor, 
                                    exec -> {
                                        try {
                                            return ((Number) getPoolSizeMethod.invoke(exec)).doubleValue();
                                        } catch (Exception e) {
                                            logger.warn("Failed to get pool size", e);
                                            return 0.0;
                                        }
                                    });
                                    
                                meterRegistry.gauge("tomcat.threads.config.max", executor, 
                                    exec -> {
                                        try {
                                            return ((Number) getMaximumPoolSizeMethod.invoke(exec)).doubleValue();
                                        } catch (Exception e) {
                                            logger.warn("Failed to get maximum pool size", e);
                                            return 0.0;
                                        }
                                    });
                                    
                                meterRegistry.gauge("tomcat.threads.queue.size", executor, 
                                    exec -> {
                                        try {
                                            Object queue = getQueueMethod.invoke(exec);
                                            Method sizeMethod = queue.getClass().getMethod("size");
                                            return ((Number) sizeMethod.invoke(queue)).doubleValue();
                                        } catch (Exception e) {
                                            logger.warn("Failed to get queue size", e);
                                            return 0.0;
                                        }
                                    });
                                    
                                logger.info("Successfully registered Tomcat thread metrics for port {}", connector.getPort());
                                
                                // Log current values
                                try {
                                    int activeCount = (Integer) getActiveCountMethod.invoke(executor);
                                    int poolSize = (Integer) getPoolSizeMethod.invoke(executor);
                                    int maxPoolSize = (Integer) getMaximumPoolSizeMethod.invoke(executor);
                                    Object queue = getQueueMethod.invoke(executor);
                                    int queueSize = (Integer) queue.getClass().getMethod("size").invoke(queue);
                                    
                                    logger.info("  - tomcat.threads.busy: {}", activeCount);
                                    logger.info("  - tomcat.threads.current: {}", poolSize);
                                    logger.info("  - tomcat.threads.config.max: {}", maxPoolSize);
                                    logger.info("  - tomcat.threads.queue.size: {}", queueSize);
                                } catch (Exception e) {
                                    logger.warn("Failed to log current metric values", e);
                                }
                                
                            } catch (Exception e) {
                                logger.error("Failed to register metrics using reflection for port {}", connector.getPort(), e);
                            }
                            
                        } else {
                            logger.warn("Executor is not ThreadPoolExecutor for port {}: {}", 
                                       connector.getPort(), executor != null ? executor.getClass().getName() : "null");
                        }
                    } catch (Exception e) {
                        logger.error("Failed to register metrics for connector on port {}", connector.getPort(), e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to register Tomcat thread pool metrics", e);
        }
    }
    
    private boolean isThreadPoolExecutor(Object executor) {
        // Check if it's an instance of java.util.concurrent.ThreadPoolExecutor
        if (executor instanceof ThreadPoolExecutor) {
            return true;
        }
        
        // Check by class name for different ThreadPoolExecutor implementations
        String className = executor.getClass().getName();
        if (className.equals("java.util.concurrent.ThreadPoolExecutor") ||
            className.equals("org.apache.tomcat.util.threads.ThreadPoolExecutor")) {
            return true;
        }
        
        // Check if it extends java.util.concurrent.ThreadPoolExecutor
        Class<?> clazz = executor.getClass();
        while (clazz != null) {
            if (clazz.getName().equals("java.util.concurrent.ThreadPoolExecutor")) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        
        return false;
    }
}
```

## Step 2: Update application.properties

Add these properties to your `src/main/resources/application.properties`:

```properties
# Enable Tomcat thread pool metrics
management.metrics.binders.tomcat.enabled=true
server.tomcat.mbeans-registry.enabled=true
management.endpoint.metrics.enabled=true

# Ensure metrics endpoint is exposed (if not already present)
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always

# Enable Tomcat metrics (if not already present)
management.metrics.enable.tomcat=true
```

**⚠️ IMPORTANT**: Only add properties that don't already exist in your application.properties file. If any of these properties are already configured, leave them as-is to avoid breaking existing functionality.

## Step 3: Verify Dependencies

Ensure your `build.gradle` (or `pom.xml`) includes these dependencies:

### Gradle
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'io.micrometer:micrometer-core' // Usually included with actuator
}
```

### Maven
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

## Step 4: Build and Test

1. **Build the application**:
   ```bash
   ./gradlew build -x test
   ```

2. **Deploy/Start the application**

3. **Verify metrics registration** by checking the logs for:
   ```
   TomcatThreadMetricsConfig initialized with MeterRegistry: ...
   TomcatWebServer detected, registering thread pool metrics
   Successfully registered Tomcat thread metrics for port XXXX
   ```

4. **Test metrics endpoint**:
   ```bash
   curl http://localhost:{port}/actuator/metrics
   ```
   
   Look for these new metrics in the response:
   - `tomcat.threads.busy`
   - `tomcat.threads.current`
   - `tomcat.threads.config.max`
   - `tomcat.threads.queue.size`

5. **Get specific metric values**:
   ```bash
   curl http://localhost:{port}/actuator/metrics/tomcat.threads.busy
   curl http://localhost:{port}/actuator/metrics/tomcat.threads.current
   ```

## Troubleshooting

### Common Issues and Solutions

1. **Metrics not appearing**:
   - Check logs for initialization messages
   - Verify the application is using embedded Tomcat (not Jetty or Undertow)
   - Ensure Spring Boot Actuator is configured correctly

2. **Build failures**:
   - Verify all required dependencies are present
   - Check for package import conflicts
   - Ensure you're using compatible Spring Boot version (3.x recommended)

3. **Log shows "Not a TomcatWebServer"**:
   - Your application might be using a different embedded server
   - This implementation only works with embedded Tomcat

4. **Reflection errors**:
   - Usually indicates version compatibility issues
   - Check Tomcat version compatibility with your Spring Boot version

### Verification Checklist

- [ ] Class created in correct package location
- [ ] Package name updated in the class
- [ ] Properties added to application.properties (only new ones)
- [ ] Application builds successfully
- [ ] Logs show successful initialization
- [ ] Metrics appear in `/actuator/metrics` endpoint
- [ ] Individual metrics return values when queried
- [ ] Existing custom metrics still work

## Integration with Monitoring Systems

Once implemented, these metrics will be automatically available to:

- **Prometheus**: Via `/actuator/prometheus` endpoint
- **OpenTelemetry**: If you have OTLP configured
- **Grafana**: Through Prometheus data source
- **Any monitoring system**: That can scrape Actuator metrics

## Metric Descriptions

| Metric Name | Description | Type | Typical Range |
|-------------|-------------|------|---------------|
| `tomcat.threads.busy` | Number of threads currently processing requests | Gauge | 0 to max threads |
| `tomcat.threads.current` | Current number of threads in the pool | Gauge | min to max threads |
| `tomcat.threads.config.max` | Maximum number of threads configured | Gauge | Fixed configuration value |
| `tomcat.threads.queue.size` | Number of requests waiting in queue | Gauge | 0 to queue capacity |

## Best Practices

1. **Monitor all four metrics together** to get a complete picture of thread pool health
2. **Set up alerts** when `busy/current` ratio is consistently high (>80%)
3. **Watch queue size** - growing queues indicate potential bottlenecks
4. **Test under load** to verify metrics accuracy during high traffic

## Compatibility

- **Spring Boot**: 3.x (tested with 3.4.5)
- **Java**: 17+ (uses modern Java features)
- **Tomcat**: Embedded Tomcat (any version compatible with Spring Boot 3.x)
- **Micrometer**: 1.10+ (included with Spring Boot 3.x)

This implementation is non-invasive and will not interfere with existing metrics or configurations.
