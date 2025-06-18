-- V3__add_performance_indexes.sql
-- Add database indexes for common filter fields to optimize query performance

-- Index for execution_status filtering (most common filter)
CREATE INDEX IF NOT EXISTS idx_execution_status ON execution(execution_status);

-- Index for trade_type filtering
CREATE INDEX IF NOT EXISTS idx_trade_type ON execution(trade_type);

-- Index for destination filtering
CREATE INDEX IF NOT EXISTS idx_destination ON execution(destination);

-- Index for security_id filtering (frequently used for portfolio queries)
CREATE INDEX IF NOT EXISTS idx_security_id ON execution(security_id);

-- Composite index for common sorting combinations
CREATE INDEX IF NOT EXISTS idx_received_timestamp_id ON execution(received_timestamp, id);

-- Composite index for status and timestamp queries (common for monitoring)
CREATE INDEX IF NOT EXISTS idx_status_received_timestamp ON execution(execution_status, received_timestamp);

-- Index for sent_timestamp queries (useful for audit trails)
CREATE INDEX IF NOT EXISTS idx_sent_timestamp ON execution(sent_timestamp);

-- Composite index for trade service integration queries
CREATE INDEX IF NOT EXISTS idx_trade_service_execution_id ON execution(trade_service_execution_id);

-- Partial index for unfilled executions (common operational query)
CREATE INDEX IF NOT EXISTS idx_unfilled_executions ON execution(execution_status, quantity_filled)
WHERE quantity_filled < quantity;

-- Index for version column to optimize optimistic locking
CREATE INDEX IF NOT EXISTS idx_version ON execution(version); 