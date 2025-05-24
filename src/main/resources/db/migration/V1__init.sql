-- Initial schema for execution service

CREATE TABLE public.execution (
    id serial NOT NULL,
    execution_status varchar(20) NOT NULL,
    trade_type varchar(10) NOT NULL,
    destination varchar(20) NOT NULL,
    security_id char(24) NOT NULL,
    quantity decimal(18,8) NOT NULL,
    limit_price decimal(18,8),
    received_timestamp timestamptz NOT NULL,
    sent_timestamp timestamptz,
    version integer NOT NULL DEFAULT 1,
    trade_service_execution_id integer NULL,
    CONSTRAINT execution_pk PRIMARY KEY (id)
);

--ALTER TABLE public.execution OWNER TO postgres; 