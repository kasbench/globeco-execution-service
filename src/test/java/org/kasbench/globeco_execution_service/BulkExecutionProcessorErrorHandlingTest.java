package org.kasbench.globeco_execution_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for enhanced error handling and detailed error reporting in BulkExecutionProcessor.
 * Focuses on validation error reporting with specific error codes and field information.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class BulkExecutionProcessorErrorHandlingTest {

    @Mock
    private BatchExecutionProperties batchProperties;
    
    @Mock
    private BatchProcessingMetrics metrics;
    
    @Mock
    private BatchSizeOptimizer batchSizeOptimizer;
    
    private BulkExecutionProcessor bulkExecutionProcessor;

    @BeforeEach
    void setUp() {
        when(batchSizeOptimizer.calculateBatchSplits(anyInt())).thenReturn(new int[]{500});
        doNothing().when(batchSizeOptimizer).recordBatchPerformance(anyInt(), anyLong(), anyBoolean());
        
        bulkExecutionProcessor = new BulkExecutionProcessor(batchProperties, metrics, batchSizeOptimizer);
    }

    @Test
    void testDetailedValidationErrors_MissingRequiredFields() {
        // Arrange
        List<ExecutionPostDTO> requests = new ArrayList<>();
        
        // Request with missing execution status
        ExecutionPostDTO missingStatus = new ExecutionPostDTO();
        missingStatus.setTradeType("BUY");
        missingStatus.setDestination("NYSE");
        missingStatus.setSecurityId("AAPL001");
        missingStatus.setQuantity(BigDecimal.valueOf(100));
        requests.add(missingStatus);
        
        // Request with missing trade type
        ExecutionPostDTO missingTradeType = new ExecutionPostDTO();
        missingTradeType.setExecutionStatus("NEW");
        missingTradeType.setDestination("NYSE");
        missingTradeType.setSecurityId("GOOGL001");
        missingTradeType.setQuantity(BigDecimal.valueOf(200));
        requests.add(missingTradeType);
        
        // Request with missing destination
        ExecutionPostDTO missingDestination = new ExecutionPostDTO();
        missingDestination.setExecutionStatus("NEW");
        missingDestination.setTradeType("SELL");
        missingDestination.setSecurityId("MSFT001");
        missingDestination.setQuantity(BigDecimal.valueOf(50));
        requests.add(missingDestination);
        
        // Request with missing security ID
        ExecutionPostDTO missingSecurityId = new ExecutionPostDTO();
        missingSecurityId.setExecutionStatus("NEW");
        missingSecurityId.setTradeType("BUY");
        missingSecurityId.setDestination("NASDAQ");
        missingSecurityId.setQuantity(BigDecimal.valueOf(75));
        requests.add(missingSecurityId);
        
        // Request with missing quantity
        ExecutionPostDTO missingQuantity = new ExecutionPostDTO();
        missingQuantity.setExecutionStatus("NEW");
        missingQuantity.setTradeType("BUY");
        missingQuantity.setDestination("NYSE");
        missingQuantity.setSecurityId("TSLA001");
        requests.add(missingQuantity);
        
        // Act
        BulkExecutionProcessor.BatchProcessingContext context = bulkExecutionProcessor.processBatch(requests);
        
        // Assert
        assertEquals(5, context.getValidationErrors().size());
        assertEquals(0, context.getValidatedExecutions().size());
        
        // Check detailed error information
        BulkExecutionProcessor.ValidationException statusError = context.getValidationErrors().get(0);
        assertEquals("MISSING_REQUIRED_FIELD", statusError.getErrorCode());
        assertEquals("executionStatus", statusError.getFieldName());
        assertTrue(statusError.getDetailedMessage().contains("Code: MISSING_REQUIRED_FIELD"));
        assertTrue(statusError.getDetailedMessage().contains("Field: executionStatus"));
        
        BulkExecutionProcessor.ValidationException tradeTypeError = context.getValidationErrors().get(1);
        assertEquals("MISSING_REQUIRED_FIELD", tradeTypeError.getErrorCode());
        assertEquals("tradeType", tradeTypeError.getFieldName());
        
        BulkExecutionProcessor.ValidationException destinationError = context.getValidationErrors().get(2);
        assertEquals("MISSING_REQUIRED_FIELD", destinationError.getErrorCode());
        assertEquals("destination", destinationError.getFieldName());
        
        BulkExecutionProcessor.ValidationException securityIdError = context.getValidationErrors().get(3);
        assertEquals("MISSING_REQUIRED_FIELD", securityIdError.getErrorCode());
        assertEquals("securityId", securityIdError.getFieldName());
        
        BulkExecutionProcessor.ValidationException quantityError = context.getValidationErrors().get(4);
        assertEquals("MISSING_REQUIRED_FIELD", quantityError.getErrorCode());
        assertEquals("quantity", quantityError.getFieldName());
    }

    @Test
    void testDetailedValidationErrors_FieldTooLong() {
        // Arrange
        List<ExecutionPostDTO> requests = new ArrayList<>();
        
        // Request with execution status too long
        ExecutionPostDTO longStatus = new ExecutionPostDTO();
        longStatus.setExecutionStatus("THIS_EXECUTION_STATUS_IS_WAY_TOO_LONG_FOR_THE_FIELD");
        longStatus.setTradeType("BUY");
        longStatus.setDestination("NYSE");
        longStatus.setSecurityId("AAPL001");
        longStatus.setQuantity(BigDecimal.valueOf(100));
        requests.add(longStatus);
        
        // Request with trade type too long
        ExecutionPostDTO longTradeType = new ExecutionPostDTO();
        longTradeType.setExecutionStatus("NEW");
        longTradeType.setTradeType("VERY_LONG_TRADE_TYPE");
        longTradeType.setDestination("NYSE");
        longTradeType.setSecurityId("GOOGL001");
        longTradeType.setQuantity(BigDecimal.valueOf(200));
        requests.add(longTradeType);
        
        // Request with destination too long
        ExecutionPostDTO longDestination = new ExecutionPostDTO();
        longDestination.setExecutionStatus("NEW");
        longDestination.setTradeType("SELL");
        longDestination.setDestination("THIS_DESTINATION_NAME_IS_TOO_LONG");
        longDestination.setSecurityId("MSFT001");
        longDestination.setQuantity(BigDecimal.valueOf(50));
        requests.add(longDestination);
        
        // Request with security ID too long
        ExecutionPostDTO longSecurityId = new ExecutionPostDTO();
        longSecurityId.setExecutionStatus("NEW");
        longSecurityId.setTradeType("BUY");
        longSecurityId.setDestination("NASDAQ");
        longSecurityId.setSecurityId("THIS_SECURITY_ID_IS_WAY_TOO_LONG_FOR_THE_DATABASE_FIELD");
        longSecurityId.setQuantity(BigDecimal.valueOf(75));
        requests.add(longSecurityId);
        
        // Act
        BulkExecutionProcessor.BatchProcessingContext context = bulkExecutionProcessor.processBatch(requests);
        
        // Assert
        assertEquals(4, context.getValidationErrors().size());
        
        // Check detailed error information
        BulkExecutionProcessor.ValidationException statusError = context.getValidationErrors().get(0);
        assertEquals("FIELD_TOO_LONG", statusError.getErrorCode());
        assertEquals("executionStatus", statusError.getFieldName());
        assertTrue(statusError.getMessage().contains("cannot exceed 20 characters"));
        
        BulkExecutionProcessor.ValidationException tradeTypeError = context.getValidationErrors().get(1);
        assertEquals("FIELD_TOO_LONG", tradeTypeError.getErrorCode());
        assertEquals("tradeType", tradeTypeError.getFieldName());
        assertTrue(tradeTypeError.getMessage().contains("cannot exceed 10 characters"));
        
        BulkExecutionProcessor.ValidationException destinationError = context.getValidationErrors().get(2);
        assertEquals("FIELD_TOO_LONG", destinationError.getErrorCode());
        assertEquals("destination", destinationError.getFieldName());
        assertTrue(destinationError.getMessage().contains("cannot exceed 20 characters"));
        
        BulkExecutionProcessor.ValidationException securityIdError = context.getValidationErrors().get(3);
        assertEquals("FIELD_TOO_LONG", securityIdError.getErrorCode());
        assertEquals("securityId", securityIdError.getFieldName());
        assertTrue(securityIdError.getMessage().contains("cannot exceed 24 characters"));
    }

    @Test
    void testDetailedValidationErrors_InvalidValues() {
        // Arrange
        List<ExecutionPostDTO> requests = new ArrayList<>();
        
        // Request with invalid quantity (zero)
        ExecutionPostDTO zeroQuantity = new ExecutionPostDTO();
        zeroQuantity.setExecutionStatus("NEW");
        zeroQuantity.setTradeType("BUY");
        zeroQuantity.setDestination("NYSE");
        zeroQuantity.setSecurityId("AAPL001");
        zeroQuantity.setQuantity(BigDecimal.ZERO);
        requests.add(zeroQuantity);
        
        // Request with invalid quantity (negative)
        ExecutionPostDTO negativeQuantity = new ExecutionPostDTO();
        negativeQuantity.setExecutionStatus("NEW");
        negativeQuantity.setTradeType("BUY");
        negativeQuantity.setDestination("NYSE");
        negativeQuantity.setSecurityId("GOOGL001");
        negativeQuantity.setQuantity(BigDecimal.valueOf(-100));
        requests.add(negativeQuantity);
        
        // Request with invalid limit price (zero)
        ExecutionPostDTO zeroLimitPrice = new ExecutionPostDTO();
        zeroLimitPrice.setExecutionStatus("NEW");
        zeroLimitPrice.setTradeType("SELL");
        zeroLimitPrice.setDestination("NASDAQ");
        zeroLimitPrice.setSecurityId("MSFT001");
        zeroLimitPrice.setQuantity(BigDecimal.valueOf(50));
        zeroLimitPrice.setLimitPrice(BigDecimal.ZERO);
        requests.add(zeroLimitPrice);
        
        // Request with invalid limit price (negative)
        ExecutionPostDTO negativeLimitPrice = new ExecutionPostDTO();
        negativeLimitPrice.setExecutionStatus("NEW");
        negativeLimitPrice.setTradeType("BUY");
        negativeLimitPrice.setDestination("NYSE");
        negativeLimitPrice.setSecurityId("TSLA001");
        negativeLimitPrice.setQuantity(BigDecimal.valueOf(25));
        negativeLimitPrice.setLimitPrice(BigDecimal.valueOf(-150.0));
        requests.add(negativeLimitPrice);
        
        // Act
        BulkExecutionProcessor.BatchProcessingContext context = bulkExecutionProcessor.processBatch(requests);
        
        // Assert
        assertEquals(4, context.getValidationErrors().size());
        
        // Check detailed error information
        BulkExecutionProcessor.ValidationException quantityError1 = context.getValidationErrors().get(0);
        assertEquals("INVALID_VALUE", quantityError1.getErrorCode());
        assertEquals("quantity", quantityError1.getFieldName());
        assertTrue(quantityError1.getMessage().contains("must be greater than zero"));
        
        BulkExecutionProcessor.ValidationException quantityError2 = context.getValidationErrors().get(1);
        assertEquals("INVALID_VALUE", quantityError2.getErrorCode());
        assertEquals("quantity", quantityError2.getFieldName());
        
        BulkExecutionProcessor.ValidationException limitPriceError1 = context.getValidationErrors().get(2);
        assertEquals("INVALID_VALUE", limitPriceError1.getErrorCode());
        assertEquals("limitPrice", limitPriceError1.getFieldName());
        assertTrue(limitPriceError1.getMessage().contains("must be greater than zero when specified"));
        
        BulkExecutionProcessor.ValidationException limitPriceError2 = context.getValidationErrors().get(3);
        assertEquals("INVALID_VALUE", limitPriceError2.getErrorCode());
        assertEquals("limitPrice", limitPriceError2.getFieldName());
    }

    @Test
    void testDetailedValidationErrors_InvalidEnumValues() {
        // Arrange
        List<ExecutionPostDTO> requests = new ArrayList<>();
        
        // Request with invalid trade type
        ExecutionPostDTO invalidTradeType = new ExecutionPostDTO();
        invalidTradeType.setExecutionStatus("NEW");
        invalidTradeType.setTradeType("INVALID_TRADE_TYPE");
        invalidTradeType.setDestination("NYSE");
        invalidTradeType.setSecurityId("AAPL001");
        invalidTradeType.setQuantity(BigDecimal.valueOf(100));
        requests.add(invalidTradeType);
        
        // Request with invalid execution status
        ExecutionPostDTO invalidStatus = new ExecutionPostDTO();
        invalidStatus.setExecutionStatus("INVALID_STATUS");
        invalidStatus.setTradeType("BUY");
        invalidStatus.setDestination("NYSE");
        invalidStatus.setSecurityId("GOOGL001");
        invalidStatus.setQuantity(BigDecimal.valueOf(200));
        requests.add(invalidStatus);
        
        // Act
        BulkExecutionProcessor.BatchProcessingContext context = bulkExecutionProcessor.processBatch(requests);
        
        // Assert
        assertEquals(2, context.getValidationErrors().size());
        
        // Check detailed error information
        BulkExecutionProcessor.ValidationException tradeTypeError = context.getValidationErrors().get(0);
        assertEquals("INVALID_ENUM_VALUE", tradeTypeError.getErrorCode());
        assertEquals("tradeType", tradeTypeError.getFieldName());
        assertTrue(tradeTypeError.getMessage().contains("Must be BUY or SELL"));
        
        BulkExecutionProcessor.ValidationException statusError = context.getValidationErrors().get(1);
        assertEquals("INVALID_ENUM_VALUE", statusError.getErrorCode());
        assertEquals("executionStatus", statusError.getFieldName());
        assertTrue(statusError.getMessage().contains("Must be NEW, PENDING, FILLED, CANCELLED, or REJECTED"));
    }

    @Test
    void testDetailedValidationErrors_NullRequest() {
        // Arrange
        List<ExecutionPostDTO> requests = new ArrayList<>();
        requests.add(null);
        
        // Act
        BulkExecutionProcessor.BatchProcessingContext context = bulkExecutionProcessor.processBatch(requests);
        
        // Assert
        assertEquals(1, context.getValidationErrors().size());
        
        BulkExecutionProcessor.ValidationException nullError = context.getValidationErrors().get(0);
        assertEquals("NULL_REQUEST", nullError.getErrorCode());
        assertNull(nullError.getFieldName());
        assertTrue(nullError.getMessage().contains("cannot be null"));
    }

    @Test
    void testMixedValidationScenario() {
        // Arrange
        List<ExecutionPostDTO> requests = new ArrayList<>();
        
        // Valid request
        ExecutionPostDTO validRequest = new ExecutionPostDTO();
        validRequest.setExecutionStatus("NEW");
        validRequest.setTradeType("BUY");
        validRequest.setDestination("NYSE");
        validRequest.setSecurityId("AAPL001");
        validRequest.setQuantity(BigDecimal.valueOf(100));
        validRequest.setLimitPrice(BigDecimal.valueOf(150.0));
        requests.add(validRequest);
        
        // Invalid request (missing field)
        ExecutionPostDTO missingField = new ExecutionPostDTO();
        missingField.setExecutionStatus("NEW");
        missingField.setTradeType("BUY");
        // Missing destination
        missingField.setSecurityId("GOOGL001");
        missingField.setQuantity(BigDecimal.valueOf(200));
        requests.add(missingField);
        
        // Invalid request (field too long)
        ExecutionPostDTO fieldTooLong = new ExecutionPostDTO();
        fieldTooLong.setExecutionStatus("NEW");
        fieldTooLong.setTradeType("VERY_LONG_TRADE_TYPE");
        fieldTooLong.setDestination("NYSE");
        fieldTooLong.setSecurityId("MSFT001");
        fieldTooLong.setQuantity(BigDecimal.valueOf(50));
        requests.add(fieldTooLong);
        
        // Invalid request (invalid value)
        ExecutionPostDTO invalidValue = new ExecutionPostDTO();
        invalidValue.setExecutionStatus("NEW");
        invalidValue.setTradeType("BUY");
        invalidValue.setDestination("NASDAQ");
        invalidValue.setSecurityId("TSLA001");
        invalidValue.setQuantity(BigDecimal.valueOf(-25)); // Negative quantity
        requests.add(invalidValue);
        
        // Another valid request
        ExecutionPostDTO anotherValid = new ExecutionPostDTO();
        anotherValid.setExecutionStatus("PENDING");
        anotherValid.setTradeType("SELL");
        anotherValid.setDestination("NYSE");
        anotherValid.setSecurityId("NVDA001");
        anotherValid.setQuantity(BigDecimal.valueOf(75));
        requests.add(anotherValid);
        
        // Act
        BulkExecutionProcessor.BatchProcessingContext context = bulkExecutionProcessor.processBatch(requests);
        
        // Assert
        assertEquals(5, context.getTotalRequested());
        assertEquals(3, context.getValidationErrors().size()); // 3 invalid requests
        assertEquals(2, context.getValidCount()); // 2 valid requests
        
        // Check that validation errors have correct indices
        assertTrue(context.getValidationErrors().containsKey(1)); // Missing field
        assertTrue(context.getValidationErrors().containsKey(2)); // Field too long
        assertTrue(context.getValidationErrors().containsKey(3)); // Invalid value
        
        // Check that valid requests don't have errors
        assertFalse(context.getValidationErrors().containsKey(0));
        assertFalse(context.getValidationErrors().containsKey(4));
        
        // Check detailed error information for each type
        BulkExecutionProcessor.ValidationException missingFieldError = context.getValidationErrors().get(1);
        assertEquals("MISSING_REQUIRED_FIELD", missingFieldError.getErrorCode());
        assertEquals("destination", missingFieldError.getFieldName());
        
        BulkExecutionProcessor.ValidationException fieldTooLongError = context.getValidationErrors().get(2);
        assertEquals("FIELD_TOO_LONG", fieldTooLongError.getErrorCode());
        assertEquals("tradeType", fieldTooLongError.getFieldName());
        
        BulkExecutionProcessor.ValidationException invalidValueError = context.getValidationErrors().get(3);
        assertEquals("INVALID_VALUE", invalidValueError.getErrorCode());
        assertEquals("quantity", invalidValueError.getFieldName());
    }

    @Test
    void testValidationErrorDetailedMessage() {
        // Arrange
        BulkExecutionProcessor.ValidationException error = new BulkExecutionProcessor.ValidationException(
            "Field is required", "MISSING_REQUIRED_FIELD", "testField"
        );
        
        // Act
        String detailedMessage = error.getDetailedMessage();
        
        // Assert
        assertTrue(detailedMessage.contains("Field is required"));
        assertTrue(detailedMessage.contains("Code: MISSING_REQUIRED_FIELD"));
        assertTrue(detailedMessage.contains("Field: testField"));
    }

    @Test
    void testValidationErrorDetailedMessage_NoCodeOrField() {
        // Arrange
        BulkExecutionProcessor.ValidationException error = new BulkExecutionProcessor.ValidationException(
            "Simple error message"
        );
        
        // Act
        String detailedMessage = error.getDetailedMessage();
        
        // Assert
        assertEquals("Simple error message [Code: VALIDATION_ERROR]", detailedMessage);
    }
}