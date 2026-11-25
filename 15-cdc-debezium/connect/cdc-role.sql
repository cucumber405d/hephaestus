-- The connector's database user (Compose); on the platform a CNPG managed role. V2__outbox.sql grants it the outbox.
create role cdc login replication password 'cdc';
