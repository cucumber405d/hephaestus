create table processed_message
(
    message_id   uuid        primary key,
    processed_at timestamptz not null
);

create table purchase_order
(
    id            uuid           primary key,
    customer_id   varchar(64)    not null,
    saga          varchar(20)    not null,
    status        varchar(20)    not null,
    amount        numeric(12, 2) not null,
    reason        varchar(200),
    -- the message that started the saga, kept to re-send it (duplicate experiment)
    first_topic   varchar(60)    not null,
    first_message text           not null,
    created_at    timestamptz    not null
);

create table order_line
(
    order_id uuid        not null references purchase_order (id),
    sku      varchar(40) not null,
    quantity int         not null,
    primary key (order_id, sku)
);

-- What happened, in order: the saga made visible
create table order_history
(
    id       bigserial    primary key,
    order_id uuid         not null references purchase_order (id),
    at       timestamptz  not null,
    entry    varchar(200) not null
);

-- The orchestrator's memory: current step, state, deadline of the step, compensations still awaited
create table saga
(
    order_id              uuid        primary key references purchase_order (id),
    step                  varchar(20) not null,
    state                 varchar(20) not null,
    deadline              timestamptz,
    pending_compensations int         not null default 0,
    -- W3C traceparent of the saga's last step: a timeout continues this trace
    traceparent           varchar(55)
);

create index saga_running_deadline_idx on saga (deadline) where state = 'RUNNING';
