-- The connector's user, as the platform creates it (compose init script, CNPG managed role); V2__outbox.sql grants it
create role cdc login replication password 'cdc';
