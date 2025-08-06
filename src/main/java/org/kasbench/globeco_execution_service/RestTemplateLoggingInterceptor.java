package org.kasbench.globeco_execution_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestTemplate interceptor for logging request/response times and detecting slow calls.
 */
public class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(RestTemplateLoggingInterceptor.class);
    private static final int SLOW_REQUEST_THRESHOLD_MS = 1000; // Log warning for requests > 1s
    
    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, 
            byte[] body, 
            ClientHttpRequestExecution execution) throws IOException {
        
        long startTime = System.currentTimeMillis();
        String uri = request.getURI().toString();
        
        try {
            ClientHttpResponse response = execution.execute(request, body);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            if (duration > SLOW_REQUEST_THRESHOLD_MS) {
                logger.warn("SLOW HTTP REQUEST: {} {} - {}ms - Status: {}", 
                    request.getMethod(), uri, duration, response.getStatusCode());
            } else {
                logger.debug("HTTP REQUEST: {} {} - {}ms - Status: {}", 
                    request.getMethod(), uri, duration, response.getStatusCode());
            }
            
            return response;
            
        } catch (IOException e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            logger.error("HTTP REQUEST FAILED: {} {} - {}ms - Error: {}", 
                request.getMethod(), uri, duration, e.getMessage());
            
            throw e;
        }
    }
}