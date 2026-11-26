-- Work orders and stock live in the journal now (V3, V5); the quotes stay a plain table (not event-sourced).
drop table work_order;
drop table material;
