-- ** Database generated with pgModeler (PostgreSQL Database Modeler).
-- ** pgModeler version: 1.2.0-beta1
-- ** PostgreSQL version: 17.0
-- ** Project Site: pgmodeler.io
-- ** Model Author: ---

-- ** Database creation must be performed outside a multi lined SQL file. 
-- ** These commands were put in this file only as a convenience.

-- object: new_database | type: DATABASE --
-- DROP DATABASE IF EXISTS new_database;
-- CREATE DATABASE new_database;
-- ddl-end --


SET search_path TO pg_catalog,public;
-- ddl-end --

-- object: public.execution | type: TABLE --
-- DROP TABLE IF EXISTS public.execution CASCADE;
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
	trade_service_execution_id integer,
	version integer NOT NULL DEFAULT 1,
	CONSTRAINT execution_pk PRIMARY KEY (id)
);
-- ddl-end --
-- ALTER TABLE public.execution OWNER TO postgres;
-- ddl-end --


