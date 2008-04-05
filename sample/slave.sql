CREATE TABLE replication_test
(
  id serial NOT NULL,
  amount integer,
  value character varying,
  CONSTRAINT replication_test_pkey PRIMARY KEY (id)
);

CREATE TRIGGER replication_test_t
    BEFORE INSERT OR DELETE OR UPDATE ON replication_test
    FOR EACH ROW
    EXECUTE PROCEDURE denyaccesstrigger();
