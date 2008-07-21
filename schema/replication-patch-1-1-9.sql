create language plpgsql;

create or replace function bruce.array_to_set( vals anyarray ) returns setof anyelement as $X$ 
declare
    i           int ;
    len         int ;
begin
    i:=1;
    len := array_upper(vals,1) ;
    loop
        if i > len then
            exit;
        end if;
        return next vals[i];
        i := i+1 ;
    end loop;
end;
$X$ language plpgsql;

create or replace function bruce.execute_sql( statement text ) returns void as $X$
begin
    execute statement ;
end;
$X$ language plpgsql;

create or replace function bruce.execute_sql_array( preamble text, delimited_values text, delimiter text, suffix text ) returns setof void as $X$ 
select bruce.execute_sql( $1 || val || $4 ) from bruce.array_to_set(string_to_array($2,$3)) as val ;
$X$ language sql;


