CREATE TABLE replication_test
(
  id serial NOT NULL,
  amount integer,
  value character varying,
  CONSTRAINT replication_test_pkey PRIMARY KEY (id)
);

CREATE TRIGGER replication_test_t
    AFTER INSERT OR DELETE OR UPDATE ON replication_test
    FOR EACH ROW
    EXECUTE PROCEDURE logtransactiontrigger();

CREATE TRIGGER replication_test_s
    BEFORE INSERT OR DELETE OR UPDATE ON replication_test
    FOR EACH STATEMENT
    EXECUTE PROCEDURE logsnapshottrigger();

INFO main com.netblue.bruce.admin.NodeBuilder -

CREATE TRIGGER replication_test.replicate_this_tx
    AFTER INSERT OR DELETE OR UPDATE ON replication_test.replicate_this
    FOR EACH ROW
    EXECUTE PROCEDURE logtransactiontrigger()

SELECT logsnapshot();