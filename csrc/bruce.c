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

#include <string.h>
#include <signal.h>

char *url = "$URL$";
char *id = "$Id$";

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

#define MODE_UNSET 0
#define MODE_NORMAL 1
#define MODE_DAEMON 2
/* Presume we are on a slave node until told otherwise */
static int replication_mode = MODE_NORMAL; 

static TransactionId currentXid = InvalidTransactionId;

/* Apply an update, delete, or insert logged by logTransactionTrigger to a 
   specified table */
Datum applyLogTransaction(PG_FUNCTION_ARGS) {
  char *tTypeS=Datum2CString(PG_GETARG_DATUM(0));
  char *tTableS=Datum2CString(PG_GETARG_DATUM(1));
  char *tInfoS=Datum2CString(PG_GETARG_DATUM(2));
  char *cols[1024];
  void *plan;
  Oid plan_types[2048];
  Datum plan_values[2048];
  char query[10240];
  char *uCols[1024];
  int uColsCount=0;
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
  int numCols = 0;
  int bindParms = 0;
  int i;
  int queryResult;

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
    bos=strstr(uidx,"(");
    bos++;
    eos=strstr(bos,")");
    eos[0]='\0';
    for (uCols[uColsCount]=strsep(&bos,", ");uCols[uColsCount];uCols[uColsCount]=strsep(&bos,", ")) {
      if (strlen(uCols[uColsCount])!=0) {
	uColsCount++;
      }
    }
  }

  switch (tTypeS[0]) {
  case 'I':
    /* Insert */
    {
      char values[10240];
      char tempS[10240];
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
      char whereC[10240];
      char tempS[10240];
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
      char tempS[10240];
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
	    plan_types[bindParms-1]=getTypeOid(colSs[i].colType);
	    plan_values[bindParms-1]=OidFunctionCall3(colSs[i].typInput,
						      CStringGetDatum(colSs[i].oldColS),
						      ObjectIdGetDatum(colSs[i].typIOParam),
						      Int32GetDatum(-1));
	  }
	}
	numUniqueInWhere++;
	if ((numUniqueInWhere<uColsCount) || 
	    ((uColsCount == 0) && (i<numCols-1))) {
	  sprintf(tempS,"%s and ",query);
	  strcpy(query,tempS);
	}
      }
    }
    break;
  default:
    /* Bogus */
    ereport(ERROR,(errmsg_internal("Unknown value for transaction type. Should be 'I','U', or 'D' for Insert, Update or Delete.")));
  }

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
    
    plan_types[0]=INT4OID;
    plan_values[0]=DirectFunctionCall1(int4in,DirectFunctionCall1(xidout,TransactionIdGetDatum(currentXid)));

    plan_types[1]=INT4OID;
    plan_values[1]=DirectFunctionCall1(int4in,DirectFunctionCall1(xidout,
								  TransactionIdGetDatum(SerializableSnapshot->xmin)));
    plan_types[2]=INT4OID;
    plan_values[2]=DirectFunctionCall1(int4in,DirectFunctionCall1(xidout,
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
  char *retval;
  *isnull = failure; /* default is 'is not null' */
  if ((s==NULL) || (strcmp(s,"")==0)) {
    return ("");
  }
  if (strcmp(s,fieldNull)==0) {
    *isnull = success;
    return("");
  }
  retval=Datum2CString(DirectFunctionCall2(binary_decode,
					   DirectFunctionCall1(textin,CStringGetDatum(s)),
					   DirectFunctionCall1(textin,PointerGetDatum("base64"))));
  return retval;
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
  
  plan_types[0]=INT4OID;
  plan_values[0]=DirectFunctionCall1(int4in,DirectFunctionCall1(xidout,GetTopTransactionId()));
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
