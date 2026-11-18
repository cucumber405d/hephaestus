-- One PostgreSQL server, one database and one user per service: the data stays separate, the stack stays light.
create user ordering with password 'ordering';
create database ordering owner ordering;
create user payment with password 'payment';
create database payment owner payment;
create user inventory with password 'inventory';
create database inventory owner inventory;
create user delivery with password 'delivery';
create database delivery owner delivery;
