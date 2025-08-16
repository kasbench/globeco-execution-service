package org.kasbench.globeco_execution_service;

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
