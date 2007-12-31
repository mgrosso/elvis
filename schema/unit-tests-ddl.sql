DROP TABLE test2;
DROP TABLE test1;

DROP SCHEMA regextest;
CREATE SCHEMA regextest;
CREATE TABLE regextest.blue(c_int integer);
CREATE TABLE regextest.red(c_int integer);
CREATE TABLE regextest.green(c_int integer);
CREATE TABLE regextest.orange(c_int integer);

DROP SCHEMA regextest_s2;
CREATE SCHEMA regextest_s2;
CREATE TABLE regextest_s2.blue2(c_int integer);
CREATE TABLE regextest_s2.red2(c_int integer);
CREATE TABLE regextest_s2.green2(c_int integer);
CREATE TABLE regextest_s2.orange2(c_int integer);

CREATE TABLE test2
(
    id integer primary key,
    c_bytea bytea,
    c_text text,
    c_int integer
);

CREATE TABLE test1
(
    id serial NOT NULL primary key,
    c_bytea bytea,
    c_text text,
    c_int integer
);


CREATE TRIGGER test1_t
    AFTER INSERT OR DELETE OR UPDATE ON test1
    FOR EACH ROW
    EXECUTE PROCEDURE bruce.logtransactiontrigger();

CREATE TRIGGER test1_s
    BEFORE INSERT OR DELETE OR UPDATE ON test1
    FOR EACH STATEMENT
    EXECUTE PROCEDURE bruce.logsnapshottrigger();

CREATE TRIGGER test2_t
    BEFORE INSERT OR DELETE OR UPDATE ON test2
    FOR EACH ROW
    EXECUTE PROCEDURE bruce.denyaccesstrigger();

