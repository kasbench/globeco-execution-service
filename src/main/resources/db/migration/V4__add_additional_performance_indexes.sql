-- V4__add_additional_performance_indexes.sql
-- Add additional database indexes for performance optimization based on common query patterns

-- Composite index for pagination with common filters
CREATE INDEX IF NOT EXISTS idx_status_timestamp_id ON execution(execution_status, received_timestamp DESC, id);

-- Composite index for trade type and timestamp queries
CREATE INDEX IF NOT EXISTS idx_trade_type_timestamp ON execution(trade_type, received_timestamp DESC);

-- Composite index for destination and timestamp queries  
CREATE INDEX IF NOT EXISTS idx_destination_timestamp ON execution(destination, received_timestamp DESC);

-- Composite index for security and timestamp queries (portfolio views)
CREATE INDEX IF NOT EXISTS idx_security_timestamp ON execution(security_id, received_timestamp DESC);

-- Index for quantity comparisons (useful for fill ratio queries)
CREATE INDEX IF NOT EXISTS idx_quantity_filled ON execution(quantity_filled);

-- Composite index for status and quantity (operational monitoring)
CREATE INDEX IF NOT EXISTS idx_status_quantity_filled ON execution(execution_status, quantity_filled);

-- Index for limit price queries (useful for price analysis)
CREATE INDEX IF NOT EXISTS idx_limit_price ON execution(limit_price) WHERE limit_price IS NOT NULL;

-- Covering index for common SELECT fields to avoid table lookups
CREATE INDEX IF NOT EXISTS idx_covering_common_fields ON execution(
    execution_status, 
    trade_type, 
    destination, 
    security_id, 
    received_timestamp DESC
) INCLUDE (id, quantity, limit_price, sent_timestamp, quantity_filled, average_price, version);