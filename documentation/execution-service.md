
<a name="public.execution"></a>
### _public_.**execution** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| execution_status | varchar(20) |  |  |  | &#10003; |  |  |
| trade_type | varchar(10) |  |  |  | &#10003; |  |  |
| destination | varchar(20) |  |  |  | &#10003; |  |  |
| security_id | char(24) |  |  |  | &#10003; |  |  |
| quantity | decimal(18,8) |  |  |  | &#10003; |  |  |
| limit_price | decimal(18,8) |  |  |  |  |  |  |
| received_timestamp | timestamptz |  |  |  | &#10003; |  |  |
| sent_timestamp | timestamptz |  |  |  |  |  |  |
| trade_service_execution_id | integer |  |  |  |  |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| execution_pk | PRIMARY KEY | id |  |  |  |  |  |

---

Generated at _2025-05-24T11:50:05_ by **pgModeler 1.2.0-beta1**
[PostgreSQL Database Modeler - pgmodeler.io ](https://pgmodeler.io)
Copyright © 2006 - 2025 Raphael Araújo e Silva 
