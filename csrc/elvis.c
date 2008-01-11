/*
 * Bruce - A PostgreSQL Database Replication System
 *
 * Portions Copyright (c) 2007, Connexus Corporation
 *
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose, without fee, and without a written
 * agreement is hereby granted, provided that the above copyright notice and
 * this paragraph and the following two paragraphs appear in all copies.
 *
 * IN NO EVENT SHALL CONNEXUS CORPORATION BE LIABLE TO ANY PARTY FOR DIRECT,
 * INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST
 * PROFITS, ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION,
 * EVEN IF CONNEXUS CORPORATION HAS BEEN ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * CONNEXUS CORPORATION SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE. THE SOFTWARE PROVIDED HEREUNDER IS ON AN "AS IS"
 * BASIS, AND CONNEXUS CORPORATION HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE,
 * SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
*/

#include "postgres.h"

#include "fmgr.h"
#include "pgstat.h"
#include "miscadmin.h"
#include "access/genam.h"
#include "access/transam.h"
#include "catalog/dependency.h"
#include "catalog/indexing.h"
#include "catalog/pg_database.h"
#include "commands/comment.h"
#include "commands/dbcommands.h"
#include "commands/trigger.h"
#include "executor/spi.h"
#include "storage/freespace.h"
#include "storage/proc.h"
#include "storage/procarray.h"
#include "tcop/tcopprot.h"
#include "utils/builtins.h"
#include "utils/flatfiles.h"
#include "utils/fmgroids.h"
#include "utils/int8.h"
#include "utils/lsyscache.h"

#include <stdlib.h>
#include <string.h>
#include <signal.h>

char *url = "$URL: https://svn.codehaus.org/bruce/branches/1.0/csrc/bruce.c $";
char *id = "$Id: bruce.c 125 2007-12-13 07:14:48Z rklahn $";

/* For a PG extention to work version >= 8.2, it must include fmgr.h and include this source */
#ifdef PG_MODULE_MAGIC
PG_MODULE_MAGIC;
#endif

#define colSep "|"
#define fieldSep ":"
#define fieldNull "!"
#define success 1
#define failure 0

Datum serializeRow(HeapTuple new_row,HeapTuple old_row,TupleDesc desc);
Datum serializeCol(char *name,char *type,char *old,char *new);
char *ConvertDatum2CString(Oid type,Datum d,bool isnull);
char *deB64(char *s,bool *b);
char *Datum2CString(Datum d);
char *currentLogID(void);
void insertTransactionLog(char *cmd_type,char *schema,char *table,Datum row_data);
Oid getTypeOid(char *typeName);
bool colInUnique(char **uCols,int uColsCount,char *colName);

PG_FUNCTION_INFO_V1(logTransactionTrigger);
Datum logTransactionTrigger(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(logSnapshot);
Datum logSnapshot(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(denyAccessTrigger);
Datum denyAccessTrigger(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(daemonMode);
Datum daemonMode(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(normalMode);
Datum normalMode(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(applyLogTransaction);
Datum applyLogTransaction(PG_FUNCTION_ARGS);

PG_FUNCTION_INFO_V1(applyLogTransaction2);
Datum applyLogTransaction2(PG_FUNCTION_ARGS);

PG_FUNCTION_INFO_V1(debug_fakeapplylog);
Datum debug_fakeapplylog(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(debug_setcacheitem);
Datum debug_setcacheitem(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(debug_peekcacheitem);
Datum debug_peekcacheitem(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(debug_parseinfo);
Datum debug_parseinfo(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(debug_applyinfo);
Datum debug_applyinfo(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(debug_echo);
Datum debug_echo(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(set_tightmem);
Datum set_tightmem(PG_FUNCTION_ARGS);


#define MODE_UNSET 0
#define MODE_NORMAL 1
#define MODE_DAEMON 2

#define INITIAL_CACHE_SIZE 100
#define STACK_STRING_SIZE 10240

#define NO_TIGHT_MEM 0
#define TIGHT_MEM 1

/* Presume we are on a slave node until told otherwise */
static int replication_mode = MODE_NORMAL; 
static int tight_mem = NO_TIGHT_MEM; 

static TransactionId currentXid = InvalidTransactionId;

/* 
 * structure used to cache query plans across transactions, as well as other
 * information needed to use those plans or debug issues.
 */

typedef struct statement_cache_item_struct  {
    char *              query_string;
    char *              param_type_names_delimited;
    char *              param_info_indices_delimited;
    size_t              num_params ;
    size_t              num_info_columns_to_parse ;
    char **             param_type_names;
    char **             param_index_strings;
    int *               param_indices; //corresponds to items in the change
    Oid  *              parameter_oids ;
    Oid  *              conversion_func_oids ;
    Oid  *              conversion_func_parm_oids ;
    void *              plan;
    Datum *             plan_values;
    char *              nulls;    // nulls[i] = plan_values[i] is null ? 'n' : ' '
    char **             raw_old;
    char **             raw_new;
    char **             decoded_values;
} statement_cache_item  ;

/* make a copy. should go away when this backend goes away */
static char *  bruce_copy_string( const char *src );

static statement_cache_item **_cache=NULL;
static unsigned int _cache_size =INITIAL_CACHE_SIZE;
static char * const uninitialized ="not initialized";
static char * const empty_string="";
static char * const null_string="null";

/* SPI_palloc, then memset with null */
static void *bruce_alloc(size_t amount);
static void bruce_free(void *f);

/* frees string and resets string to uninitialized="not initialized" */
static void clean_string(char **s );

/* make a cache item from procedure arguments. */
static statement_cache_item  *  make_statement_cache_item( 
    const char * query_string, 
    const char * param_type_names_delimited, 
    size_t num_params,
    const char *param_info_indices_delimited
    );

/* clean_cache_item resets pointers that would otherwise point to memory that
 * will be freed after this transaction. */
static void clean_cache_item( statement_cache_item *cache);

/* free out the cache array. */
static void                     init_cache();

/* getCached will return null if the object does not exist yet. */
static statement_cache_item *   get_cached_item(size_t index);

/* store a cache item at index index, making space for it if need be. */
static void store_cache_item(size_t index, statement_cache_item *item);

/* copy array, */
static void ensure_cache_room(size_t index);

/* make , */
static void execute_query(statement_cache_item * item);

/* return a cache item, one way or another. */
static statement_cache_item * get_or_make_cache_item(
        size_t cache_index,
        size_t num_params,
        const char *query_string,
        const char *param_type_names_delimited,
        const char *param_info_indices_delimited
        );

/* find the maximum absolute value of the integers in an integer array */
static size_t find_max_abs( int *intarray, size_t arraylen );

/* 1- parse the change_info, placing pointers to base64 of old and new values into old/new arrays */
static void parse_change_info(statement_cache_item *item,char *change_info);

/* 2- parse the change_info, placing pointers to base64 of old and new values into old/new arrays */
static void decode_values(statement_cache_item *item );

/* 3- call postgres function to decode base 64. unlike deB64, this handles no special cases. */
char *decode_base64(char *s );

/* take a numeric string indicating the position of a parameter within the info string, convert to a number, and place into an array. */
static void callback_param_index( statement_cache_item *cache_item, int param, char *field_value);

/* take a string indicating the type of a parameter and place the conversion function and arguments into an array. */
static void callback_param_type_name( statement_cache_item *cache_item, int param, char *field_value);

/* 
 * callback function pointer, must accept a cache item, an int indicating param
 * position, and a string that is the delimited field parsed from the separated values. 
 * */
typedef void (*cache_parse_callback)(statement_cache_item *,int , char *);

static void parse_delimited( const char *original, char *delimited, char **dest, const char * delimiters, size_t expected_fields, statement_cache_item *cache_item, cache_parse_callback callback );

/* following methods handy for supporting the debug printing out of stuff. */
static void bruce_append_string( char *dest, size_t destlen, const char *src );
static void bruce_append_list( char *dest, size_t destlen, const char *prefix, char **list, size_t listlen, char *delim );
static void bruce_append_number( char *dest, size_t destlen, int number );
static void bruce_append_number_list( char *dest, size_t destlen, const char *prefix, int *list, size_t listlen, char *delim );

static char * debug_cache_item(statement_cache_item *cache_item);

static void applyLogTransaction2_inner(
    statement_cache_item ** cache_item ,
    size_t cache_index  ,
    size_t num_params  ,
    char *query_string ,
    char *param_type_names_delimited ,
    char *param_info_indices_delimited ,
    char *change_info  
    );

static char *  bruce_copy_string( const char *src ){
    unsigned int len ;
    char *dest ;
    if(!src){
        ereport(ERROR,(errmsg_internal("bruce error, asked to copy null string.")));
    }
    len = strlen(src);
    dest = (char *)bruce_alloc(len+1);
    strncpy( dest, src, len+1);
    return dest;
}

static void bruce_append_string( char *dest, size_t destlen, const char *src ){
    if(!dest || !src || !*src ){
        return;
    }
    strncat(dest,src, destlen-(strlen(dest)+1));
}

static void bruce_append_number( char *dest, size_t destlen, int number ){
    char buf [destlen];
    snprintf(buf,destlen,"%i",number);
    bruce_append_string(dest,destlen,buf);
}

static void bruce_append_list( char *dest, size_t destlen, const char *prefix, char **list, size_t listlen, char *delim ){
    size_t i;
    if(!dest || !destlen || !list || !listlen ){
        return;
    }
    if(prefix){
        bruce_append_string(dest,destlen,prefix);
    }
    for( i=0; i< listlen; ++i ){
        bruce_append_string(dest,destlen,list[i]);
        if(delim){
            bruce_append_string(dest,destlen,delim);
        }
    }
}

static void bruce_append_number_list( char *dest, size_t destlen, const char *prefix, int *list, size_t listlen, char *delim ){
    size_t i;
    if(!dest || !destlen || !list || !listlen ){
        return;
    }
    if(prefix){
        bruce_append_string(dest,destlen,prefix);
    }
    for( i=0; i< listlen; ++i ){
        bruce_append_number(dest,destlen,list[i]);
        if(i<listlen-1 && delim ){
            bruce_append_string(dest,destlen,delim);
        }
    }
}

static void bruce_free(void *f){
    free(f);
}

static void bruce_free_ptr(void **f){
    if(f && *f){
        free(*f);
        *f=NULL;
    }
}

static void *bruce_alloc(size_t amount){
    //using SPI_palloc here causes cache memory to be overwritten after subsequent calls.
    void * giveback = malloc(amount);
    memset(giveback,'\0',amount);
    return giveback;
}

static statement_cache_item  *  make_statement_cache_item( 
        const char * query_string,
        const char * param_type_names_delimited,
        size_t num_params,
        const char *param_info_indices_delimited
){
    statement_cache_item *giveback;
    void *plan;
    void *tmp = bruce_alloc(sizeof(statement_cache_item));

    giveback = ( statement_cache_item *) tmp;

    //first just copy parameters over. may not copy query string and column
    //names in the future to enable scalibility to more tables with less memory
    //overhead.  good for debugging though.
    giveback->query_string                  =bruce_copy_string(query_string);
    giveback->param_type_names_delimited    =bruce_copy_string(param_type_names_delimited);
    giveback->num_params                    =num_params;
    giveback->param_info_indices_delimited  =bruce_copy_string(param_info_indices_delimited);
    giveback->nulls                         =(char*)bruce_alloc(sizeof(char)*(num_params + 1));

    giveback->param_type_names              =(char **)bruce_alloc(sizeof(char *)*num_params);
    giveback->param_index_strings           =(char **)bruce_alloc(sizeof(char *)*num_params);

    giveback->param_indices                 =(int *)bruce_alloc(sizeof(int *)*num_params);
    giveback->parameter_oids                =(Oid *)bruce_alloc(sizeof(Oid)*num_params);
    giveback->conversion_func_oids          =(Oid *)bruce_alloc(sizeof(Oid)*num_params);
    giveback->conversion_func_parm_oids     =(Oid *)bruce_alloc(sizeof(Oid)*num_params);
    giveback->plan_values                   =(Datum *)bruce_alloc(sizeof(Datum)*num_params);

    giveback->decoded_values                =(char**)bruce_alloc(sizeof(char *)*num_params);

    parse_delimited(
        param_type_names_delimited,
        giveback->param_type_names_delimited,
        giveback->param_type_names,
        colSep, num_params, giveback, callback_param_type_name);

    parse_delimited(
        param_info_indices_delimited,
        giveback->param_info_indices_delimited,
        giveback->param_index_strings,
        colSep, num_params, giveback, callback_param_index);

    giveback->num_info_columns_to_parse =find_max_abs(giveback->param_indices,num_params);
    giveback->raw_new                   =(char**)bruce_alloc(sizeof(char *)*giveback->num_info_columns_to_parse);
    giveback->raw_old                   =(char**)bruce_alloc(sizeof(char *)*giveback->num_info_columns_to_parse);

    plan = SPI_prepare(giveback->query_string,giveback->num_params,giveback->parameter_oids);
    if(plan==NULL){
        ereport(ERROR,(errmsg_internal(
            "bruce error, applyLogTransaction2 could not create plan for query [%s]", 
            giveback->query_string )));
    }
    giveback->plan=SPI_saveplan(plan);
    clean_cache_item(giveback);
    return giveback;
};

static void clean_cache_item( statement_cache_item *cache){
    size_t i;
    for(i=0; i<cache->num_params; ++i){
        cache->decoded_values[i] = uninitialized;
        cache->plan_values[i] = (Datum)NULL;
    }
    for(i=0; i<cache->num_info_columns_to_parse; ++i){
        cache->raw_new[i] = uninitialized;
        cache->raw_old[i] = uninitialized;
    }
    if(tight_mem){
        clean_string(&cache->query_string);
        clean_string(&cache->param_type_names_delimited);
        clean_string(&cache->param_info_indices_delimited);

        bruce_free_ptr((void **)&cache->param_type_names);
        bruce_free_ptr((void **)&cache->param_index_strings);
    }
}

static void clean_string(char **s ){
    if(!s || !*s || *s == uninitialized || *s == null_string || *s == empty_string ){
        return;
    }
    bruce_free(*s);
    *s=uninitialized;
}

static size_t find_max_abs( int *intarray, size_t arraylen ){
    int giveback=0;
    size_t i;
    for( i=0; i < arraylen; ++i ){
        if(intarray[i] > giveback){
            giveback=intarray[i];
        }else if(intarray[i] * -1 > giveback){
            giveback=intarray[i] * -1;
        }
    }
    return giveback;
}

/* take a numeric string indicating the position of a parameter within the info string, convert to a number, and place into an array. */
static void callback_param_index( statement_cache_item *cache_item, int param, char *field_value){
    char *endptr;
    long int c = strtol(field_value,&endptr,10);
    if( c==0 || *field_value=='\0' || !endptr || *endptr != '\0' ){
        ereport(ERROR,(errmsg_internal(
            "bruce error, applyLogTransaction2 could convert to int index %d for query paramater[%s]", 
            param,field_value)));
    }
    //
    //for inserts, the "old" value is actually the new value to use.  see
    //comments in parse_change_info() function and read section 33.3, "Writing
    //trigger functions in C" in the postgres manual.  The java daemon will be
    //passing us parameter indices using the intuitive but incorrect notion
    //that inserts should reference the "new" section.
    //
    if(strcasestr(cache_item->query_string, "INSERT")){
        c = -1 * c;
    }
    cache_item->param_indices[param]= c;
}

/* take a string indicating the type of a parameter and place the conversion function and arguments into an array. */
static void callback_param_type_name( statement_cache_item *cache_item, int param, char *field_value){
    //ereport(NOTICE,(errmsg_internal(
    //"callback_param_type_name: param=%i field_value=%s", param,field_value)));
    cache_item->param_type_names[param] = field_value ;
    cache_item->parameter_oids[param] = getTypeOid(field_value);//get oid of the parameter type
    getTypeInputInfo(
        cache_item->parameter_oids[param],                //in arg: oid of the parameter type
        &cache_item->conversion_func_oids[param],         //out arg: conversion function oid
        &cache_item->conversion_func_parm_oids[param]     //out arg: oid of the conversion function type parameter
    );
}

static void parse_delimited( const char *original, char *delimited, char **dest, const char * delimiters, size_t expected_fields, statement_cache_item *cache_item, cache_parse_callback callback ){
    size_t i;
    char *strseparg=delimited;
    if( ! ( original && delimited && delimiters && dest && expected_fields && *original && *delimited && *delimiters )){
        //does not return.
        ereport(ERROR,(errmsg_internal("bruce error, applyLogTransaction2 received null arguments or could not allocate memory.")));
    }
    for( i = 0 ; i<expected_fields && strseparg && *strseparg ; ++i ){
        dest[i] = strsep(&strseparg,delimiters);
    }
    if( strseparg || i != expected_fields ){
        ereport(ERROR,(errmsg_internal("bruce error, applyLogTransaction2 could not parse %s with delimiter %s into %d args.",
            original, delimiters, expected_fields )));
    }
    for(i=0; i< expected_fields; ++i ){
        callback(cache_item, i, dest[i] );
    }
}

static char * debug_cache_item(statement_cache_item *cache_item){
    char output[STACK_STRING_SIZE];
    memset(output,'\0',STACK_STRING_SIZE);
    if(cache_item==NULL){
        bruce_append_string(output,STACK_STRING_SIZE,null_string);
        return bruce_copy_string(output);
    }
    bruce_append_string(output,STACK_STRING_SIZE,"query: ");
    bruce_append_string(output,STACK_STRING_SIZE,cache_item->query_string);
    bruce_append_string(output,STACK_STRING_SIZE,"\nnum_params: ");
    bruce_append_number(output,STACK_STRING_SIZE,(int)cache_item->num_params);
    bruce_append_string(output,STACK_STRING_SIZE,"\nnum_info_columns_to_parse: ");
    bruce_append_number(output,STACK_STRING_SIZE,(int)cache_item->num_info_columns_to_parse);
    bruce_append_list(output,STACK_STRING_SIZE,"\nparameter type names: ",cache_item->param_type_names,cache_item->num_params, ",");
    bruce_append_number_list(output,STACK_STRING_SIZE,"\nparameter info indices: ",cache_item->param_indices,cache_item->num_params, ",");
    bruce_append_list(output,STACK_STRING_SIZE,"\ndecoded parameter string values: ",cache_item->decoded_values,cache_item->num_params, ",");
    bruce_append_list(output,STACK_STRING_SIZE,"\ninfo strings raw_old: ",cache_item->raw_old,cache_item->num_info_columns_to_parse, ",");
    bruce_append_list(output,STACK_STRING_SIZE,"\ninfo strings raw_new: ",cache_item->raw_new,cache_item->num_info_columns_to_parse, ",");
    bruce_append_string(output,STACK_STRING_SIZE,"\nnulls '");
    bruce_append_string(output,STACK_STRING_SIZE,cache_item->nulls);
    bruce_append_string(output,STACK_STRING_SIZE,"'\n");
    return bruce_copy_string(output);
}


/* free out the cache array. */
static void                     init_cache(){
    _cache = (statement_cache_item **) bruce_alloc(sizeof(statement_cache_item*)*_cache_size);
}

/* getCached will return null if the object does not exist yet. */
static statement_cache_item *   get_cached_item(size_t index){
    if(index > _cache_size - 1  || _cache == NULL ){
        return NULL;
    }
    return *(_cache + index);//value here might also be null.
}

/* store a cache item at index index, making space for it if need be. */
static void store_cache_item(size_t index, statement_cache_item *item){
    ensure_cache_room(index);
    *(_cache+index)=item;
}

/* copy array, */
static void ensure_cache_room(size_t index){
    statement_cache_item **oldcache;
    size_t old_mem_size;
    if( index >= _cache_size ){
        oldcache=_cache;
        old_mem_size=_cache_size * sizeof(statement_cache_item *);
        _cache_size = index * 2;
        init_cache();
        if(oldcache){//might not even be initialized yet.
            memcpy(_cache,oldcache,old_mem_size );
            bruce_free(oldcache);
        }
    }else if(_cache==NULL){
        init_cache();
    }
}

/* return a cache item, one way or another. */
static statement_cache_item * get_or_make_cache_item(
        size_t cache_index,
        size_t num_params,
        const char *query_string,
        const char *param_type_names_delimited,
        const char *param_info_indices_delimited
        ){
        statement_cache_item *giveback;
        giveback = get_cached_item(cache_index );
        if(giveback != NULL ){
            return giveback;
        }
        giveback = make_statement_cache_item(
                        query_string, 
                        param_type_names_delimited, 
                        num_params,
                        param_info_indices_delimited
                        );
        store_cache_item(cache_index,giveback);
        return giveback;
}

/* 1- parse the change_info, placing pointers to raw base64 of old and new values into old/new arrays */
static void parse_change_info(statement_cache_item *item,char *change_info){
    // in bruce.transactionlog you will see rows like this...
    //
    // rowid|xaction|cmdtype|tabname|info
    //         1 | 1900649736 | U       | datafeeds.feed_offer_site | id:int8:MTA4Nw==:MTA4Nw==|site_id:int8:MTA2:MTA2|feed_offer_id:int8:NTE=:NTE=|start_time:timestamp:MjAwNy0wNC0wMiAxMjowMDowMC4yNg==:MjAwNy0wNC0wMiAxMjowMDowMC4yNg==|end_time:timestamp:!:!|is_active:bool:dA==:dA==|last_feed_response:varchar:MjAw:MjAw|last_feed_timestamp:timestamp:MjAwNy0xMi0xOCAxMzo1OTo1OS43ODU=:MjAwNy0xMi0xOCAxMzo1OTo1OS43ODU=|version:int8:MA==:MA==|last_update_time:timestamp:MjAwNy0xMi0xOCAyMDozMjozOC4yNDQ=:MjAwNy0xMi0xOCAyMTozMjo0NC40NDk=
    // 4462805 | 3385844160 | I       | sites.tracking      | id:int8:MTA5MjM3NjMwMA==:!|visit_date:timestamp:MjAwNy0xMi0xMyAwMToyNzo0Ny42OTQ=:!|name:varchar:Y2lk:!|value:varchar:OTk=:!|source:varchar:!:!|visit_id:int8:NDUwMzU5OTc2Mzk2OTE1OQ==:!
    //  5524544 | 3389775382 | D       | sites.flow_experience_promotion | flow_experience_id:int4:MTkw:!|promotion_id:int4:MTAwMDkw:!|rank:int4:MA==:!|create_date:timestamp:MjAwNy0xMS0wNSAxNTo0ODoyMi4wMzg5NDM=:!|version:int8:MA==:!|last_update_time:timestamp:MjAwNy0xMS0wNSAxNTo0ODoyMi4wMzg=:!
    //
    //
    //this function parses the info column, looking specifically for the old and new values for each column 
    //of the replicated table row.
    //
    //for each pipe delimited field
    //this field should look something like this "id:int8:MTA4Nw==:MTA4Nw=="
    //skip the first two colons to get to the start of the old base64 value
    //
    //examples below use imaginary two column info string.
    //   "id:int8:MTA4Nw==:MTA4Nw==|site_id:int8:MTA2:MTA2"
    //
    //interpret thusly:
    //"name of column 1" : "type of column 1" : "old column 1 value in base64 : "new column 1 value in base64"
    // followed by optional '|',  and then "name of column 2" and so on.
    //
    //there is one problem with this interpretation.  in postgres c functions acting as triggers, the "new" value
    //is only available for updates.  inserts and deletes are both supposed to use the "old" value, and receive
    //null for the "new" value.  thats intuitive for deletes, but counter intuitive for inserts.  The java code
    //will be passing us index values that are positive or negative according to the intuitive interpretation. 
    //rather than "fix" that in the parse here, we'll do that when we interpret the parameter indexes.
    //
    //to see this, note in the sample rows above that both the insert and delete have values in the "old" part
    //but not in the "new" part.
    //
    //comments below identify where the pointer 'c' is using ascii art to move the c underneath the right
    //character.
    //
    //   "id:int8:MTA4Nw==:MTA4Nw==|site_id:int8:MTA2:MTA2"
    //    c
    //
    size_t f=0;
    size_t skip=2;
    char *c=change_info;
    for( 
        f=0,c=change_info ; 
        c && *c && f < item->num_info_columns_to_parse ;
        ++f
    ){  
        for(skip=2; skip && c && *c; --skip ){
            c=strchr(c,':');
            if(!c || !*c || *c != ':' ){
                ereport(ERROR,(errmsg_internal("could not parse info string: problem skipping first two. field=%u first part of info string=%s", f,change_info)));
            }
            ++c;//move past : to start of type description or start of raw_old
        }
        if(!c || !*c || *c=='|' ){
            ereport(ERROR,(errmsg_internal("could not parse info string: raw_old looks wrong. field=%u first part of info string=%s", f,change_info)));
        }
        //   "id:int8:MTA4Nw==:MTA4Nw==|site_id:int8:MTA2:MTA2"
        //            c
        //
        item->raw_old[f]=c;
        if( *c==':' ){
            //noop: zero length field, eg id:varchar::MTA4Nw==|
        } else{
            c=strchr(c,':');
        }
        if(!c || !*c || *c!=':' ){
            ereport(ERROR,(errmsg_internal("could not parse info string: missing third ':'.  field=%u first part of info string: %s", f,change_info)));
        }
        *c='\0';//null terminate the raw_old
        ++c;//advance to the first char of raw_new
        if(f==item->num_info_columns_to_parse-1 && ! *c ){
            //the raw_new of the last field is zero length
            item->raw_new[f]=c;
        }else if( !*c || *c==':' ){
            ereport(ERROR,(errmsg_internal("could not parse info string: raw_new looks wrong. field=%u first part of info string: %s",f,change_info)));
        }
        //   "id:int8:MTA4Nw==\0MTA4Nw==|site_id:int8:MTA2:MTA2"
        //            ^         c
        //raw_old[i]__|
        //
        item->raw_new[f]=c;
        if(f<item->num_info_columns_to_parse -1 ){
            if(*c=='|'){ 
                //zero length field, eg id:varchar:MTA4Nw==:|
                //noop: previous field was empty, so dont advance c, we are already on a '|'
            }else{
                c=strchr(c,'|');
            }
            if( !c || !*c || *c!= '|' ){
                ereport(ERROR,(errmsg_internal("could not parse info string: expected '|'. field=%u first part of info string: %s",f,change_info)));
            }
            //   "id:int8:MTA4Nw==\0MTA4Nw==|site_id:int8:MTA2:MTA2"
            //            ^         ^       c
            //raw_old[i]__|         |____________raw_new[i]
            *c='\0';
            ++c;
            if( !*c || *c== '|' || *c==':' ){
                ereport(ERROR,(errmsg_internal("could not parse info string: expected to be on first char of next columns name. field=%u first part of info string: %s", f,change_info)));
            }
            //   "id:int8:MTA4Nw==\0MTA4Nw==\0site_id:int8:MTA2:MTA2"
            //            ^         ^         c
            //raw_old[i]__|         |____________raw_new[i]
            //
        }else{ //do nothing, this is the last column
            //   "id:int8:MTA4Nw==\0MTA4Nw==\0site_id:int8:MTA2\0MTA2"
            //                                             ^     ^   c
            //                             raw_old[i]______|     |____________raw_new[i]
            //
        }
    }
    if( f < item->num_info_columns_to_parse ){
        ereport(ERROR,(errmsg_internal("could not parse info string: not enough columns in info string. field=%u first part of info string: %s",f,change_info)));
    }
}

/* 2- decode the parameter values, checking for null, and updating the nulls string too.  */
static void decode_values(statement_cache_item *item ){
    size_t i;
    char *rawvalue;
    int rawindex ;
    int index ;
    for( i=0; i< item->num_params; ++i ){
        //parameter indices count from one, but c arrays count from zero. 
        //also, negative indices imply using the old values of the column in this part
        //of the query, while positive means use the new value. typically where clauses use
        //the old values and values clauses and update column= clauses use new.
        rawindex = item->param_indices[i] ;
        index = (rawindex < 0 ? (rawindex +1)*-1 : rawindex-1 ); 
        rawvalue= (rawindex < 0 ) ? item->raw_old[index] : item->raw_new[index];
        if(!rawvalue){
            ereport(ERROR,(errmsg_internal("could not parse info string: possible bug. null rawvalue?")));
        }
        if(*rawvalue=='!'){
            item->nulls[i]='n';
            item->decoded_values[i] = null_string;
            item->plan_values[i] = (Datum)NULL;
        }else{
            item->nulls[i]=' ';
            if( ! *rawvalue ){
                item->decoded_values[i]=empty_string;
            }else{
                item->decoded_values[i] = decode_base64(rawvalue);
            }
            item->plan_values[i] = 
                OidFunctionCall3(
                    item->conversion_func_oids[i],
                    CStringGetDatum(item->decoded_values[i]),
                    ObjectIdGetDatum(item->conversion_func_parm_oids[i]),
                    Int32GetDatum(-1));
        }
    }
}

char *decode_base64(char *s ) {
    return Datum2CString(DirectFunctionCall2(binary_decode,
        DirectFunctionCall1(textin,CStringGetDatum(s)),
        DirectFunctionCall1(textin,PointerGetDatum("base64"))));
}

static void execute_query(statement_cache_item *item ){
    int queryResult=SPI_execp(item->plan,item->plan_values,item->nulls,0);
    if (queryResult<0) {
        ereport(ERROR,(errmsg_internal("SPI_execp() failed")));
    }
    if (SPI_processed!=1) {
        ereport(ERROR,(errmsg_internal("%d rows updated, deleted, or inserted. Expected one and only one row.",
                   SPI_processed)));
    }
    SPI_finish();
}

static void applyLogTransaction2_inner(
    statement_cache_item ** cache_item ,
    size_t cache_index  ,
    size_t num_params  ,
    char *query_string ,
    char *param_type_names_delimited ,
    char *param_info_indices_delimited ,
    char *change_info  
    ){
    if (SPI_connect()<0){
        ereport(ERROR,(errmsg_internal("SPI_connect failed in daemonMode()")));
    }
    if (!num_params || 
            ! query_string || ! *query_string || 
            ! change_info || ! *change_info || 
            ! param_type_names_delimited  || ! *param_type_names_delimited ||
            ! param_info_indices_delimited  || ! *param_info_indices_delimited ) {
        ereport(ERROR,(errmsg_internal("null or zero length argument")));
    }
    //0- create the cache item if it does not exist already.
    //1- parse the change_info, placing pointers to base64 of old and new values into old/new arrays
    //2- fill Datam array, plan_values, with the decoded values 
    //3- execute query and validate results affect one row.
    //4- cleanup and return true.
    *cache_item =  get_or_make_cache_item(
        cache_index, num_params, query_string,
        param_type_names_delimited, param_info_indices_delimited);
    parse_change_info(*cache_item,change_info);
    decode_values(*cache_item);
}

Datum applyLogTransaction2(PG_FUNCTION_ARGS) {
    statement_cache_item * cache_item ;
    size_t cache_index  =                 PG_GETARG_UINT32(0);//shared index used for memoizing.
    size_t num_params  =                  PG_GETARG_UINT32(1);//number of question marks in sql
    char *query_string =                  Datum2CString(PG_GETARG_DATUM(2));//sql query for dml
    char *param_type_names_delimited =    Datum2CString(PG_GETARG_DATUM(3));//pipe delimited parameter type names
    char *param_info_indices_delimited =  Datum2CString(PG_GETARG_DATUM(4));//pipe delimited indices into the info string, with negative numbers indicating to use old values.
    char *change_info  =                  Datum2CString(PG_GETARG_DATUM(5));// bruce.transactinlog.info string

    //ereport(NOTICE,(errmsg_internal("info:%u,%u,%s,%s,%s,%s",cache_index,num_params,query_string,param_type_names_delimited,param_info_indices_delimited,change_info)));
    replication_mode=MODE_DAEMON;
    applyLogTransaction2_inner(&cache_item,
        cache_index, num_params, query_string,
        param_type_names_delimited, param_info_indices_delimited, change_info);
    execute_query(cache_item);
    SPI_finish();
    return BoolGetDatum(success);
};


Datum debug_fakeapplylog(PG_FUNCTION_ARGS) {
    statement_cache_item * cache_item ;
    char *giveback ;
    size_t cache_index  =                 PG_GETARG_UINT32(0);//shared index used for memoizing.
    size_t num_params  =                  PG_GETARG_UINT32(1);//number of question marks in sql
    char *query_string =                  Datum2CString(PG_GETARG_DATUM(2));//sql query for dml
    char *param_type_names_delimited =    Datum2CString(PG_GETARG_DATUM(3));//pipe delimited parameter type names
    char *param_info_indices_delimited =  Datum2CString(PG_GETARG_DATUM(4));//pipe delimited indices into the info string, with negative numbers indicating to use old values.
    char *change_info  =                  Datum2CString(PG_GETARG_DATUM(5));// bruce.transactinlog.info string

    applyLogTransaction2_inner(&cache_item,
        cache_index, num_params, query_string,
        param_type_names_delimited, param_info_indices_delimited, change_info);

    giveback =debug_cache_item(cache_item);
    SPI_finish();
    PG_RETURN_CSTRING( giveback );
}

Datum debug_setcacheitem(PG_FUNCTION_ARGS){
    statement_cache_item * cache_item ;
    char *giveback ;
    size_t cache_index  =                 PG_GETARG_UINT32(0);//shared index used for memoizing.
    size_t num_params  =                  PG_GETARG_UINT32(1);//number of question marks in sql
    char *query_string =                  Datum2CString(PG_GETARG_DATUM(2));//sql query for dml
    char *param_type_names_delimited =    Datum2CString(PG_GETARG_DATUM(3));//pipe delimited parameter type names
    char *param_info_indices_delimited =  Datum2CString(PG_GETARG_DATUM(4));//pipe delimited indices into the info string, with negative numbers indicating to use old values.
    if (SPI_connect()<0){
        ereport(ERROR,(errmsg_internal("SPI_connect failed in daemonMode()")));
    }
    if (!num_params || 
            ! query_string || ! *query_string || 
            ! param_type_names_delimited  || ! *param_type_names_delimited ||
            ! param_info_indices_delimited  || ! *param_info_indices_delimited ) {
        ereport(ERROR,(errmsg_internal("null or zero length argument")));
    }
    store_cache_item(cache_index,NULL);
    cache_item =  get_or_make_cache_item(
        cache_index, num_params, query_string,
        param_type_names_delimited, param_info_indices_delimited);
    giveback =debug_cache_item(cache_item);
    SPI_finish();
    PG_RETURN_CSTRING( giveback );
}

Datum debug_peekcacheitem(PG_FUNCTION_ARGS){
    char *giveback ;
    size_t cache_index  =                 PG_GETARG_UINT32(0);//shared index used for memoizing.
    giveback =debug_cache_item(get_cached_item(cache_index));
    SPI_finish();
    PG_RETURN_CSTRING( giveback );
}

Datum debug_parseinfo(PG_FUNCTION_ARGS){
    statement_cache_item * cache_item ;
    char *giveback ;
    size_t cache_index  =                 PG_GETARG_UINT32(0);//shared index used for memoizing.
    char *change_info  =                  Datum2CString(PG_GETARG_DATUM(1));// bruce.transactinlog.info string
    if (SPI_connect()<0){
        ereport(ERROR,(errmsg_internal("SPI_connect failed in daemonMode()")));
    }
    if ( ! change_info  || ! *change_info ) {
        ereport(ERROR,(errmsg_internal("null or zero length argument")));
    }
    cache_item =  get_cached_item(cache_index);
    if ( ! cache_item  ) {
        ereport(ERROR,(errmsg_internal("cache index %u is not initialized",cache_index)));
    }
    parse_change_info(cache_item,change_info);
    decode_values(cache_item);
    giveback =debug_cache_item(cache_item);
    SPI_finish();
    clean_cache_item(cache_item);
    PG_RETURN_CSTRING( giveback );
}

Datum debug_applyinfo(PG_FUNCTION_ARGS){
    statement_cache_item * cache_item ;
    size_t cache_index  =                 PG_GETARG_UINT32(0);//shared index used for memoizing.
    char *change_info  =                  Datum2CString(PG_GETARG_DATUM(1));// bruce.transactinlog.info string
    if (SPI_connect()<0){
        ereport(ERROR,(errmsg_internal("SPI_connect failed in daemonMode()")));
    }
    if ( ! change_info  || ! *change_info ) {
        ereport(ERROR,(errmsg_internal("null or zero length argument")));
    }
    cache_item =  get_cached_item(cache_index);
    if ( ! cache_item  ) {
        ereport(ERROR,(errmsg_internal("cache index %u is not initialized",cache_index)));
    }
    parse_change_info(cache_item,change_info);
    ereport(NOTICE,(errmsg_internal(debug_cache_item(cache_item))));
    decode_values(cache_item);
    ereport(NOTICE,(errmsg_internal(debug_cache_item(cache_item))));
    execute_query(cache_item);
    SPI_finish();
    clean_cache_item(cache_item);
    return BoolGetDatum(success);
}

Datum debug_echo(PG_FUNCTION_ARGS){
    char *giveback ;
    size_t cache_index  ;
    char *query_string ;
    cache_index  =                 PG_GETARG_UINT32(0);//shared index used for memoizing.
    query_string =                  Datum2CString(PG_GETARG_DATUM(1));//sql query for dml
    if (SPI_connect()<0){
        ereport(ERROR,(errmsg_internal("SPI_connect failed in daemonMode()")));
    }
    if ( ! query_string  || ! cache_index ) {
        ereport(ERROR,(errmsg_internal("null or zero length or zero argument")));
    }
    giveback =bruce_copy_string(query_string);
    SPI_finish();
    //ereport(ERROR,(errmsg_internal("got here: %s",giveback)));
    PG_RETURN_POINTER( giveback );
}

Datum set_tightmem(PG_FUNCTION_ARGS){
    size_t arg  =                 PG_GETARG_UINT32(0);//shared index used for memoizing.
    if(arg){
        tight_mem=TIGHT_MEM;
        return BoolGetDatum(success);
    }
    tight_mem=NO_TIGHT_MEM;
    return BoolGetDatum(failure);
}


/* Apply an update, delete, or insert logged by logTransactionTrigger to a 
   specified table */
Datum applyLogTransaction(PG_FUNCTION_ARGS) {
  char *tTypeS=Datum2CString(PG_GETARG_DATUM(0));
  char *tTableS=Datum2CString(PG_GETARG_DATUM(1));
  char *tInfoS=Datum2CString(PG_GETARG_DATUM(2));
  char *cols[1024];
  struct colS {
    char *colName;
    char *colType;
    Oid typInput; /* Needed to convert a string back to the pg internal representation of a type */
    Oid typIOParam; /* Ditto */
    char *oldColS;
    bool oldIsNull;
    char *newColS;
    bool newIsNull;
  } colSs[1024];
  void *plan;
  Oid plan_types[2048];
  Datum plan_values[2048];
  char query[STACK_STRING_SIZE];
  char *uCols[1024];
  int uColsCount=0;
 int numCols = 0;
  int bindParms = 0;
  int i;
  int queryResult;

  ereport(DEBUG1,(errmsg_internal("info:%s",tInfoS)));

  /* Connect to the Server Programming Interface */
  if (SPI_connect()<0)
    ereport(ERROR,(errmsg_internal("SPI_connect failed in applyLogTransaction()")));

  /* Break up the info string into Column tokens */
  numCols=0;
  for (cols[numCols]=strsep(&tInfoS,colSep);cols[numCols];cols[numCols]=strsep(&tInfoS,colSep)) {
    numCols++;
  }

  /* Deseralize each column */
  for (i=0;i<numCols;i++) {
    colSs[i].colName=strsep(&cols[i],fieldSep);
    colSs[i].colType=strsep(&cols[i],fieldSep);
    colSs[i].oldColS=deB64(strsep(&cols[i],fieldSep),&colSs[i].oldIsNull);
    colSs[i].newColS=deB64(strsep(&cols[i],fieldSep),&colSs[i].newIsNull);
    getTypeInputInfo(getTypeOid(colSs[i].colType),&colSs[i].typInput,&colSs[i].typIOParam);
  }

  /* Does this table have a primary key, or lacking that, a unique index */
  /* If we come out of this code with uColsCount>0, then, yes. */
  sprintf(query,"select pg_get_indexdef(indexrelid) from pg_index where indisunique = true and indrelid = (select oid from pg_class where relname = substring('%s' from '%s') and relnamespace = (select oid from pg_namespace where nspname = substring('%s' from '%s'))) and indexprs is null order by indisprimary desc",tTableS,"\\\\.(.*)$",tTableS,"^(.*)\\\\.");
  plan=SPI_prepare(query,0,plan_types);
  queryResult=SPI_exec(query,1);
  if (queryResult<0) {
    ereport(ERROR,(errmsg_internal("SPI_execp() failed for p/uidx")));
  }
  uCols[0]='\0';
  if (SPI_processed>0) {
    char *bos,*eos, *uidx;
    uidx=SPI_getvalue(SPI_tuptable->vals[0],SPI_tuptable->tupdesc,1);
    ereport(DEBUG2,(errmsg_internal("p/uidx:%s",uidx)));
    /* We are processing a string that looks like this:
       CREATE UNIQUE INDEX category_promotion_pkey ON sites.category_promotion USING btree (category_id, promotion_id)
       and out of this we want the uCols array to look like:
       uCols[0]="category_id";
       uCols[1]="promotion_id";
       
       but we also need to handle the case like this:
       CREATE UNIQUE INDEX web_set_pkey ON heartbeat.web_set USING btree ("time")
       where the col list begins or ends with a reserved word, which PgSQL will put in double
       quotes, which is why we are looking for the col string to begin with either (" or just (
       , and similary for the end of the string.
    */
    if ((bos=strstr(uidx,"(\""))) {
      bos+=2;
    } else {
      if ((bos=strstr(uidx,"("))) {
	bos++;
      } else {
	ereport(ERROR,(errmsg_internal("Unable to find begining of unique column list from %s",uidx)));
      }
    }
    if ((eos=strstr(bos,"\")")) || (eos=strstr(bos,")"))) {
      eos[0]='\0';
    } else {
      ereport(ERROR,(errmsg_internal("Unable to find end of unique column list from %s",uidx)));
    }
    ereport(DEBUG2,(errmsg_internal("bos:%s",bos)));
#define DELIM "\", "
    for (uCols[uColsCount]=strsep(&bos,DELIM);
	 uCols[uColsCount];
	 uCols[uColsCount]=strsep(&bos,DELIM)) {
      if (strlen(uCols[uColsCount])!=0) {
	ereport(DEBUG2,(errmsg_internal("uCols[%d]:%s",uColsCount,uCols[uColsCount])));
	uColsCount++;
      }
    }
  }

  switch (tTypeS[0]) {
  case 'I':
    /* Insert */
    {
      char values[STACK_STRING_SIZE];
      char tempS[STACK_STRING_SIZE];
      sprintf(query,"insert into %s (",tTableS);
      sprintf(values," values (");
      for (i=0;i<numCols;i++) {
	sprintf(tempS,"%s%s",query,colSs[i].colName);
	strcpy(query,tempS);
	if (colSs[i].oldIsNull) {
	  sprintf(tempS,"%sNULL",values);
	  strcpy(values,tempS);
	} else {
	  bindParms++;
	  sprintf(tempS,"%s$%d",values,bindParms);
	  strcpy(values,tempS);
	  ereport(DEBUG1,(errmsg_internal("%s $%d:%s",
					  colSs[i].colName,
					  bindParms,
					  colSs[i].oldColS)));
	  plan_types[bindParms-1]=getTypeOid(colSs[i].colType);
	  /* Convert string to a Datum of the right type */
	  plan_values[bindParms-1]=OidFunctionCall3(colSs[i].typInput,
						    CStringGetDatum(colSs[i].oldColS),
						    ObjectIdGetDatum(colSs[i].typIOParam),
						    Int32GetDatum(-1));
	}
	/* Not the last col? Add appropritate field seperators */
	if (i<numCols-1) {
	  sprintf(tempS,"%s,",query);
	  strcpy(query,tempS);
	  sprintf(tempS,"%s,",values);
	  strcpy(values,tempS);
	}
      }
      sprintf(tempS,"%s)%s)",query,values);
      strcpy(query,tempS);
    }
    break;
  case 'U':
    /* Update */
    {
      char whereC[STACK_STRING_SIZE];
      char tempS[STACK_STRING_SIZE];
      int numUniqueInWhere=0;
      bindParms = 0;
      sprintf(query,"update %s set ",tTableS);
      sprintf(whereC,"where ");
      for (i=0;i<numCols;i++) {
	if (colInUnique(uCols,uColsCount,colSs[i].colName)) {
	  if (colSs[i].oldIsNull) {
	    sprintf(tempS,"%s%s is null",whereC,colSs[i].colName);
	    strcpy(whereC,tempS);
	  } else {
	    bindParms++;
	    sprintf(tempS,"%s%s = $%d",whereC,colSs[i].colName,bindParms);
	    strcpy(whereC,tempS);
	    ereport(DEBUG1,(errmsg_internal("%s $%d:%s",
					    colSs[i].colName,
					    bindParms,
					    colSs[i].oldColS)));
	    plan_types[bindParms-1]=getTypeOid(colSs[i].colType);
	    plan_values[bindParms-1]=OidFunctionCall3(colSs[i].typInput,
						      CStringGetDatum(colSs[i].oldColS),
						      ObjectIdGetDatum(colSs[i].typIOParam),
						      Int32GetDatum(-1));
	  }
	  numUniqueInWhere++;
	  if ((numUniqueInWhere<uColsCount) || 
	      ((uColsCount == 0) && (i<numCols-1))) {
	    sprintf(tempS,"%s and ",whereC);
	    strcpy(whereC,tempS);
	  }
	}
	if (colSs[i].newIsNull) {
	  sprintf(tempS,"%s%s = null",query,colSs[i].colName);
	  strcpy(query,tempS);
	} else {
	  bindParms++;
	  sprintf(tempS,"%s%s = $%d",query,colSs[i].colName,bindParms);
	  strcpy(query,tempS);
	  ereport(DEBUG1,(errmsg_internal("%s $%d:%s",
					  colSs[i].colName,
					  bindParms,
					  colSs[i].newColS)));
	  plan_types[bindParms-1]=getTypeOid(colSs[i].colType);
	  plan_values[bindParms-1]=OidFunctionCall3(colSs[i].typInput,
						    CStringGetDatum(colSs[i].newColS),
						    ObjectIdGetDatum(colSs[i].typIOParam),
						    Int32GetDatum(-1));
	}
	if (i<numCols-1) {
	  sprintf(tempS,"%s, ",query);
	  strcpy(query,tempS);
	}
      }
      sprintf(tempS,"%s %s",query,whereC);
      strcpy(query,tempS);
    }
    break;
  case 'D':
    /* Delete */
    {
      char tempS[STACK_STRING_SIZE];
      int numUniqueInWhere=0;
      bindParms = 0;
      sprintf(query,"delete from %s where ",tTableS);
      for (i=0;i<numCols;i++) {
	if (colInUnique(uCols,uColsCount,colSs[i].colName)) {
	  if (colSs[i].oldIsNull) {
	    sprintf(tempS,"%s%s is null",query,colSs[i].colName);
	    strcpy(query,tempS);
	  } else {
	    bindParms++;
	    sprintf(tempS,"%s%s = $%d",query,colSs[i].colName,bindParms);
	    strcpy(query,tempS);
	    ereport(DEBUG1,(errmsg_internal("%s $%d:%s",
					    colSs[i].colName,
					    bindParms,
					    colSs[i].oldColS)));
	    plan_types[bindParms-1]=getTypeOid(colSs[i].colType);
	    plan_values[bindParms-1]=OidFunctionCall3(colSs[i].typInput,
						      CStringGetDatum(colSs[i].oldColS),
						      ObjectIdGetDatum(colSs[i].typIOParam),
						      Int32GetDatum(-1));
	  }
	  numUniqueInWhere++;
	  if ((numUniqueInWhere<uColsCount) || 
	      ((uColsCount == 0) && (i<numCols-1))) {
	    sprintf(tempS,"%s and ",query);
	    strcpy(query,tempS);
	  }
	}
      }
    }
    break;
  default:
    /* Bogus */
    ereport(ERROR,(errmsg_internal("Unknown value for transaction type. Should be 'I','U', or 'D' for Insert, Update or Delete.")));
  }

  ereport(DEBUG1,(errmsg_internal("query:%s",query)));
  plan=SPI_prepare(query,bindParms,plan_types);
  queryResult=SPI_execp(plan,plan_values,NULL,0);
  if (queryResult<0) {
    ereport(ERROR,(errmsg_internal("SPI_execp() failed")));
  }
  if (SPI_processed!=1) {
    ereport(ERROR,(errmsg_internal("%d rows updated, deleted, or inserted. Expected one and only one row.",
				   SPI_processed)));
  }
  SPI_finish();
  return BoolGetDatum(success);
}

/* Log the current transaction state in the snapshot log */
Datum logSnapshot(PG_FUNCTION_ARGS) {
  Datum ox; /* Outstanding Transaction list, comma separated 1,2 */
  int xcnt;
  char query[1024];
  Oid plan_types[4];
  Datum plan_values[4];
  void *plan;

  /* Make sure we only snapshot once per transaction */
  if (!TransactionIdEquals(currentXid,GetTopTransactionId())) {
    
    currentXid=GetTopTransactionId();

    if (SerializableSnapshot == NULL) 
      ereport(ERROR,(errmsg_internal("SerializableSnapshot is NULL in logSnapshot()")));

    /* Connect to the Server Programming Interface */
    if (SPI_connect()<0)
      ereport(ERROR,(errmsg_internal("SPI_connect failed in logSnapshot()")));
    
    ox=DirectFunctionCall1(textin,PointerGetDatum(""));
    
    /* Build a comma separated list of outstanding transaction as a text datum */
    for (xcnt=0;xcnt<SerializableSnapshot->xcnt;xcnt++) {
      /* If not the first transation in the list, add the field seporator */
      if (xcnt!=0) 
	ox=DirectFunctionCall2(textcat,
			       ox,
			       DirectFunctionCall1(textin,PointerGetDatum(",")));
      ox=DirectFunctionCall2(textcat,
			     ox,
			     DirectFunctionCall1(textin,DirectFunctionCall1(xidout,SerializableSnapshot->xip[xcnt])));
    }
    
    /* build out the insert statement */
    sprintf(query,
	    "insert into bruce.snapshotlog_%s (current_xaction,min_xaction,max_xaction,outstanding_xactions) values ($1,$2,$3,$4);",
	    currentLogID());
    
    plan_types[0]=INT8OID;
    plan_values[0]=DirectFunctionCall1(int8in,DirectFunctionCall1(xidout,TransactionIdGetDatum(currentXid)));

    plan_types[1]=INT8OID;
    plan_values[1]=DirectFunctionCall1(int8in,DirectFunctionCall1(xidout,
								  TransactionIdGetDatum(SerializableSnapshot->xmin)));
    plan_types[2]=INT8OID;
    plan_values[2]=DirectFunctionCall1(int8in,DirectFunctionCall1(xidout,
								  TransactionIdGetDatum(SerializableSnapshot->xmax)));
    plan_types[3]=TEXTOID;
    plan_values[3]=ox;
    
    plan=SPI_prepare(query,4,plan_types);
    SPI_execp(plan,plan_values,NULL,0);
    SPI_finish();
  }
  return PointerGetDatum(NULL);
}

/* Called as a trigger from most tables */
Datum logTransactionTrigger(PG_FUNCTION_ARGS) {
  TriggerData *td;
  char cmd_type[2];
  Datum row_data;
  
  /* Make sure we got called as a trigger */
  if (!CALLED_AS_TRIGGER(fcinfo))
    ereport(ERROR,(errmsg_internal("logTransaction() not called as trigger")));
  
  /* Get the trigger context */
  td = (TriggerData *) (fcinfo->context);

  /* Make sure we got fired AFTER and for EACH ROW */
  if (!TRIGGER_FIRED_AFTER(td->tg_event))
    ereport(ERROR,(errmsg_internal("logTransaction() must be fired as an AFTER trigger")));
  if (!TRIGGER_FIRED_FOR_ROW(td->tg_event))
    ereport(ERROR,(errmsg_internal("logTransaction() must be fired as a FOR EACH ROW trigger")));
    
  /* Determine command type */
  if (TRIGGER_FIRED_BY_INSERT(td->tg_event)) cmd_type[0] = 'I';
  if (TRIGGER_FIRED_BY_UPDATE(td->tg_event)) cmd_type[0] = 'U';
  if (TRIGGER_FIRED_BY_DELETE(td->tg_event)) cmd_type[0] = 'D';
  cmd_type[1]='\0';
  

  /* Connect to the Server Programming Interface */
  if (SPI_connect()<0)
    ereport(ERROR,(errmsg_internal("SPI_connect failed in logTransaction()")));

  row_data=serializeRow(td->tg_newtuple,td->tg_trigtuple,td->tg_relation->rd_att);

  insertTransactionLog(cmd_type,SPI_getnspname(td->tg_relation),SPI_getrelname(td->tg_relation),row_data);

  SPI_finish();
  return PointerGetDatum(NULL);
}

/* Deny updates within denyAccessTrigger() */
Datum normalMode(PG_FUNCTION_ARGS) {
  replication_mode=MODE_NORMAL;
  return PointerGetDatum(NULL);
}

/* Permit the daemon to perform table updates, when underlying table has denyAccessTriger() */
Datum daemonMode(PG_FUNCTION_ARGS) {
  replication_mode=MODE_DAEMON;
  init_cache();
  return PointerGetDatum(NULL);
}

/* Prevent access to tables under replication on slave nodes */
Datum denyAccessTrigger(PG_FUNCTION_ARGS) {
  TriggerData *tg;
  int rc;

  /* Make sure called as trigger, then get the trigger context */
  if (!CALLED_AS_TRIGGER(fcinfo))
    ereport(ERROR,(errmsg_internal("denyAccessTrigger() not called as trigger")));
  tg=(TriggerData *) (fcinfo->context);

  /* Check all denyAccessTrigger() calling requirments */
  if (!TRIGGER_FIRED_BEFORE(tg->tg_event)) 
    ereport(ERROR,(errmsg_internal("denyAccessTrigger() must be fired BEFORE")));
  if (!TRIGGER_FIRED_FOR_ROW(tg->tg_event)) 
    ereport(ERROR,(errmsg_internal("denyAccessTrigger() must be fired FOR EACH ROW")));

  /* Connect to the Server Programing Interface */
  if ((rc=SPI_connect())<0)
    ereport(ERROR,(errmsg_internal("denyAccessTrigger(): Unable to connect to SPI")));

  if ((replication_mode==MODE_NORMAL) || (replication_mode==MODE_UNSET)) {
    /* We are on a slave, attempting to update a replicated table. Bad move. */
    replication_mode=MODE_NORMAL;
    ereport(ERROR,(errmsg_internal("denyAccessTrigger(): Table %s is replicated, and should not be modified on a slave node.",
				   NameStr(tg->tg_relation->rd_rel->relname))));
    /* Unreachable */
  }

  /* This is for the case where we are on a slave node, applying transactions (we are the replication thread) */
  SPI_finish();
  if (TRIGGER_FIRED_BY_UPDATE(tg->tg_event))
    return PointerGetDatum(tg->tg_newtuple);
  else
    return PointerGetDatum(tg->tg_trigtuple);
}

Datum serializeRow(HeapTuple new_row,HeapTuple old_row,TupleDesc desc) {
  Datum retD;
  int cCol;
  
  retD=DirectFunctionCall1(textin,PointerGetDatum(""));

  for (cCol=1;cCol<=desc->natts;cCol++) {
    char *oldCC=NULL;
    char *newCC=NULL;
    if (desc->attrs[cCol-1]->attisdropped) continue;
    if (old_row!=NULL) {
      oldCC=SPI_getvalue(old_row,desc,cCol);
    }
    if (new_row!=NULL) {
      newCC=SPI_getvalue(new_row,desc,cCol);
    }
    retD=DirectFunctionCall2(textcat,
			     retD,
			     serializeCol(SPI_fname(desc,cCol),
					  SPI_gettype(desc,cCol),
					  oldCC,
					  newCC));
    /* Not last col */
    if (cCol<desc->natts) 
      retD=DirectFunctionCall2(textcat,
			       retD,
			       DirectFunctionCall1(textin,PointerGetDatum(colSep)));
  }
  return retD;
}

/* Serialize a single collum */
Datum serializeCol(char *name,char *type,char *old,char *new) {
  Datum retD;

  retD=DirectFunctionCall1(textin,PointerGetDatum(name));
  retD=DirectFunctionCall2(textcat,
			   retD,
			   DirectFunctionCall1(textin,PointerGetDatum(fieldSep)));
  retD=DirectFunctionCall2(textcat,
			   retD,
			   DirectFunctionCall1(textin,PointerGetDatum(type)));
  retD=DirectFunctionCall2(textcat,
			   retD,
			   DirectFunctionCall1(textin,PointerGetDatum(fieldSep)));
  if (old==NULL) {
    retD=DirectFunctionCall2(textcat,
			     retD,
			     DirectFunctionCall1(textin,PointerGetDatum(fieldNull)));
  } else {
    retD=DirectFunctionCall2(textcat,
			     retD,
			     DirectFunctionCall2(binary_encode,
						 DirectFunctionCall1(textin,CStringGetDatum(old)),
						 DirectFunctionCall1(textin,PointerGetDatum("base64"))));
  }
  retD=DirectFunctionCall2(textcat,
			   retD,
			   DirectFunctionCall1(textin,PointerGetDatum(fieldSep)));
  if (new==NULL) {
    retD=DirectFunctionCall2(textcat,
			     retD,
			     DirectFunctionCall1(textin,PointerGetDatum(fieldNull)));
  } else {
    retD=DirectFunctionCall2(textcat,
			     retD,
			     DirectFunctionCall2(binary_encode,
						 DirectFunctionCall1(textin,CStringGetDatum(new)),
						 DirectFunctionCall1(textin,PointerGetDatum("base64"))));
  }
  return retD;
}

/* Determine the current log id. Safe to presume we are SPI connected */
char *currentLogID() {
  SPI_exec("select max(id) from bruce.currentlog",1);
  if (SPI_processed!=1) 
    ereport(ERROR,(errmsg_internal("Unable to determine current transaction/snapshot log id in currentLogID()")));
  return(SPI_getvalue(SPI_tuptable->vals[0],SPI_tuptable->tupdesc,1));
}

/* Return a 'c' string from a presumed text Datum */
char *Datum2CString(Datum d) {
  return DatumGetCString(DirectFunctionCall1(textout,d));
}

/* Return a 'c' string from a presumed non-text Datum */
/* Borowed from postgresql source at src/backend/executed/spi.c:SPI_getvalue() */
char *ConvertDatum2CString(Oid type,Datum d,bool isnull) {
  Oid foutoid;
  bool typisvarlena;
  Datum val;
  Datum retval;

  /* Easy case. Null datum. Null string. */
  if (isnull) {
    return NULL;
  }

  getTypeOutputInfo(type,&foutoid,&typisvarlena);
  
  /* Detoast if we are toasty */
  if (typisvarlena) 
    val = PointerGetDatum(PG_DETOAST_DATUM(d));
  else
    val = d;

  retval = OidFunctionCall1(foutoid,val);
  
  /* clean up detoasted copy if we were toasty */
  if (val != d) pfree(DatumGetPointer(val));

  return DatumGetCString(retval);
}

/* Convert a serialized collum (see SerializeRow) back into normal form */
/* As you can see in SerializeRow(), there are a few special cases, like "!" meaning "null" */
char *deB64(char *s,bool *isnull) {
  *isnull = failure; /* default is 'is not null' */
  if ((s==NULL) || (strcmp(s,"")==0)) {
    return ("");
  }
  if (strcmp(s,fieldNull)==0) {
    *isnull = success;
    return("");
  }
  return decode_base64(s);
}

/* Insert an entry into the transaction log. Safe to assume we are SPI_Connect()ed */
void insertTransactionLog(char *cmd_type,char *schema,char *table,Datum row_data) {
  char query[1024];
  Oid plan_types[2];
  Datum plan_values[2];
  void *plan;

  sprintf(query,
	  "insert into bruce.transactionlog_%s (xaction,cmdtype,tabname,info) values ($1,'%s','%s.%s',$2);",
	  currentLogID(),
	  cmd_type,
	  schema,
	  table);
  
  plan_types[0]=INT8OID;
  plan_values[0]=DirectFunctionCall1(int8in,DirectFunctionCall1(xidout,GetTopTransactionId()));
  plan_types[1]=TEXTOID;
  plan_values[1]=row_data;
  
  plan=SPI_prepare(query,2,plan_types);
  SPI_execp(plan,plan_values,NULL,0);
}

/* Given a type name, obtain the types OID. Safe to assume we are SPI_Connect()ed */
Oid getTypeOid(char *typeName) {
  char query[1024];
  Oid retVal;

  retVal=(Oid) NULL;

  sprintf(query,
	  "select oid from pg_type where typname = '%s'",
	  typeName);

  SPI_exec(query,1);

  if (SPI_processed == 1) { 
    char *oidS;
    Datum newOidD;

    oidS=SPI_getvalue(SPI_tuptable->vals[0],SPI_tuptable->tupdesc,1);
    newOidD=DirectFunctionCall1(oidin,CStringGetDatum(oidS));
    retVal=DatumGetObjectId(DirectFunctionCall1(oidin,CStringGetDatum(oidS)));
  } else {
    ereport(ERROR,(errmsg_internal("Type %s does not exist",typeName)));
  }

  return(retVal);
}

/* Given a list of unique columns, determine if a column name is in the list */
/* An empty list should be treated as a list with all column names in it */
bool colInUnique(char **uCols,int uColsCount,char *colName) {
  int i;
  if (uColsCount==0) {
    return (1==1);
  }
  for (i=0;i<uColsCount;i++) {
    if (strcmp(uCols[i],colName)==0) {
      return (1==1);
    }
  }
  return (1==0);
}
