-- Add quantity_filled and average_price columns to execution table

ALTER TABLE public.execution
    ADD COLUMN quantity_filled decimal(18,8) DEFAULT 0,
    ADD COLUMN average_price decimal(18,8) DEFAULT NULL; 