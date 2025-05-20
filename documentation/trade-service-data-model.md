
<a name="public.blotter"></a>
### _public_.**blotter** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| abbreviation | varchar(20) |  |  |  | &#10003; |  |  |
| name | varchar(100) |  |  |  | &#10003; |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| blotter_pk | PRIMARY KEY | id |  |  |  |  |  |

---

<a name="public.destination"></a>
### _public_.**destination** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| abbreviation | varchar(20) |  |  |  | &#10003; |  |  |
| description | varchar(60) |  |  |  | &#10003; |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| destination_pk | PRIMARY KEY | id |  |  |  |  |  |

---

<a name="public.execution"></a>
### _public_.**execution** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| execution_timestamp | timestamptz |  |  |  | &#10003; | CURRENT_TIMESTAMP |  |
| execution_status_id | integer |  | &#10003; |  | &#10003; |  |  |
| blotter_id | integer |  | &#10003; |  |  |  |  |
| trade_type_id | integer |  | &#10003; |  |  |  |  |
| trade_order_id | integer |  | &#10003; |  | &#10003; |  |  |
| destination_id | integer |  | &#10003; |  | &#10003; |  |  |
| quantity_ordered | smallint |  |  |  |  |  |  |
| quantity_placed | decimal(18,8) |  |  |  | &#10003; |  |  |
| quantity_filled | decimal(18,8) |  |  |  | &#10003; | 0 |  |
| limit_price | decimal(18,8) |  |  |  |  |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| execution_pk | PRIMARY KEY | id |  |  |  |  |  |
| execution_status__fk | FOREIGN KEY | execution_status_id | [public.execution_status](#public.execution_status) | CASCADE | RESTRICT |  |  |
| trade_type__fk | FOREIGN KEY | trade_type_id | [public.trade_type](#public.trade_type) | CASCADE | SET NULL |  |  |
| trade_order__fk | FOREIGN KEY | trade_order_id | [public.trade_order](#public.trade_order) | CASCADE | RESTRICT |  |  |
| destination__fk | FOREIGN KEY | destination_id | [public.destination](#public.destination) | CASCADE | RESTRICT |  |  |
| blotter__fk | FOREIGN KEY | blotter_id | [public.blotter](#public.blotter) | CASCADE | SET NULL |  |  |

---

<a name="public.execution_status"></a>
### _public_.**execution_status** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| abbreviation | varchar(20) |  |  |  | &#10003; |  |  |
| description | varchar(60) |  |  |  | &#10003; |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| execution_status_pk | PRIMARY KEY | id |  |  |  |  |  |

---

<a name="public.trade_order"></a>
### _public_.**trade_order** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| order_id | integer |  |  |  | &#10003; |  |  |
| portfolio_id | char(24) |  |  |  | &#10003; |  |  |
| order_type | char(10) |  |  |  | &#10003; |  |  |
| security_id | char(24) |  |  |  | &#10003; |  |  |
| quantity | decimal(18,8) |  |  |  | &#10003; |  |  |
| limit_price | decimal(18,8) |  |  |  |  |  |  |
| trade_timestamp | timestamptz |  |  |  | &#10003; | CURRENT_TIMESTAMP |  |
| version | integer |  |  |  | &#10003; | 1 |  |
| blotter_id | integer |  | &#10003; |  |  |  |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| trade_pk | PRIMARY KEY | id |  |  |  |  |  |
| blotter__fk | FOREIGN KEY | blotter_id | [public.blotter](#public.blotter) | CASCADE | SET NULL |  |  |

#### Indexes
| Name | Type | Column(s) | Expression(s) | Predicate | Description |
|  --- | --- | --- | --- | --- | --- |
| trade_order_order_id_ndx | btree | order_id |  |  |  |

---

<a name="public.trade_type"></a>
### _public_.**trade_type** `Table`
| Name | Data type  | PK | FK | UQ  | Not null | Default value | Description |
| --- | --- | :---: | :---: | :---: | :---: | --- | --- |
| id | serial | &#10003; |  |  | &#10003; |  |  |
| abbreviation | varchar(10) |  |  |  | &#10003; |  |  |
| description | varchar(60) |  |  |  | &#10003; |  |  |
| version | integer |  |  |  | &#10003; | 1 |  |

#### Constraints
| Name | Type | Column(s) | References | On Update | On Delete | Expression | Description |
|  --- | --- | --- | --- | --- | --- | --- | --- |
| trade_type_pk | PRIMARY KEY | id |  |  |  |  |  |

---

Generated at _2025-05-13T17:01:23_ by **pgModeler 1.2.0-beta1**
[PostgreSQL Database Modeler - pgmodeler.io ](https://pgmodeler.io)
Copyright © 2006 - 2025 Raphael Araújo e Silva 
