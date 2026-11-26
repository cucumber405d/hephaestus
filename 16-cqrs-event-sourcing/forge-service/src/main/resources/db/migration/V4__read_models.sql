-- Read models (CQRS read side): rebuilt from the journal at any time, never written by commands.

-- workshop-board: every work order with its latest status
create table read.workshop_board
(
    work_order_id          varchar(80) primary key,
    order_id               uuid        not null,
    material               varchar(40) not null,
    quantity               int         not null,
    status                 varchar(30) not null,
    smith                  varchar(60),
    failure_reason         varchar(200),
    -- projection lag = updated_at - last_event_recorded_at (drill 5)
    last_event_recorded_at timestamptz not null,
    updated_at             timestamptz not null
);

create index workshop_board_status on read.workshop_board (status);

-- smith-load: work in progress and done per smith; smith_assignment remembers who took which work order
create table read.smith_load
(
    smith       varchar(60) primary key,
    in_progress int not null,
    done        int not null
);

create table read.smith_assignment
(
    work_order_id varchar(80) primary key,
    smith         varchar(60) not null
);

-- stock-ledger: stock per material and every movement
create table read.stock_ledger
(
    material varchar(40) primary key,
    on_hand  int not null
);

create table read.stock_movement
(
    position      bigint primary key,
    material      varchar(40) not null,
    delta         int         not null,
    source        varchar(20) not null,
    work_order_id varchar(80),
    recorded_at   timestamptz not null
);

insert into read.projection_checkpoint (name, tx_id, position)
values ('workshop-board', '0', 0),
       ('smith-load', '0', 0),
       ('stock-ledger', '0', 0),
       ('forge-events-publisher', '0', 0);
