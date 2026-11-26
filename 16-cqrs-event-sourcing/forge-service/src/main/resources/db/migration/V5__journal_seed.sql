-- The journal's first events: the opening stock (what 15 seeded into the material table) and three historical work
-- orders written before WorkOrderStarted named its smith (schema version 1) — the upcaster reads them as "unknown".
-- The history touches no material stream (the opening stock stays as it was) and never reaches Kafka: the publisher's
-- checkpoint is set behind it in this same transaction.
insert into forge.event (stream_id, stream_type, version, type, schema_version, payload, metadata)
values ('material-IRON-01', 'Material', 1, 'StockReceived', 1, '{"quantity":20,"source":"INITIAL"}', '{}'),
       ('material-STEEL-01', 'Material', 1, 'StockReceived', 1, '{"quantity":20,"source":"INITIAL"}', '{}');

insert into forge.event (stream_id, stream_type, version, type, schema_version, payload, metadata)
select 'FW-HIST-' || n, 'WorkOrder', v.version, v.type, v.schema_version, v.payload::jsonb, '{"history":"V5"}'
  from generate_series(1, 3) as n,
       lateral (values (1, 'WorkOrderRequested', 1,
                        json_build_object('workOrderId', 'FW-HIST-' || n,
                                          'orderId', '00000000-0000-0000-0000-00000000000' || n,
                                          'material', 'IRON-01', 'quantity', 1)::text),
                       (2, 'MaterialAllocated', 1, '{"quantity":1}'),
                       (3, 'WorkOrderStarted', 1, '{}'),
                       (4, 'WorkOrderCompleted', 1, '{}')) as v(version, type, schema_version, payload)
 order by n, v.version;

update read.projection_checkpoint
   set tx_id = pg_current_xact_id(), position = (select max(global_position) from forge.event)
 where name = 'forge-events-publisher';
