package com.netblue.bruce;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;

public class QueryCache {
    public static final char INSERT_COMMAND_TYPE = 'I';
    public static final char UPDATE_COMMAND_TYPE = 'U';
    public static final char DELETE_COMMAND_TYPE = 'D';
    public static final String ESCPD_PIPE_DLIMITER = "\\|";
    public static final String PIPE_DLIMITER = "|";
    public static final String COLON_DELIMITER = ":";
    public static final String COMMA_SPACE_DELIMITER = ", ";
    
    public static final int FIELD_NAME_INDEX = 0;
    public static final int FIELD_TYPE_INDEX = 1;
    
    private static final String UNIQ_INDX_QUERY = "select pg_get_indexdef(indexrelid) from pg_index where indisunique = true and " +
            "indrelid = (select oid from pg_class where relname = ? and relnamespace = (select oid from pg_namespace where nspname = ?)) " +
            "and indexprs is null order by indisprimary desc";
    
    Map<String, Integer> tableCommandMap = null;  // table:operation map of queries
    List<QueryParams> queryList = null;
    
    private static final Logger LOGGER = Logger.getLogger(QueryCache.class);

    private String addParam( int p ){
        return " $" + p + " ";
    }
    
    public QueryParams getQueryInfo(String commandType, String schTableName, String infoString, Connection masterConn) throws Exception {
        String tableCommand = schTableName + PIPE_DLIMITER + commandType;
        
        // first check if we have already processed this query
        Integer index = tableCommandMap.get(tableCommand);
        if(index != null) {
            //LOGGER.trace("returning index "+index + " for "+tableCommand);
            return queryList.get(index);
        }
        else {
            LOGGER.trace("making query for "+tableCommand);
            // get the schema and table name
            String[] tokens = schTableName.split("\\.");
            String schemaName = tokens[0];
            String tableName = tokens[1];

            PreparedStatement pstmt = masterConn.prepareStatement(UNIQ_INDX_QUERY);
            pstmt.setString(1, tableName);
            pstmt.setString(2, schemaName);
          
            ResultSet resultSet = pstmt.executeQuery();
            Set<String> uniqCols = new HashSet<String>();
            String colStr;
            
            boolean uniqFound=false;
            String result ="";

            if (resultSet.next()) {
                result = resultSet.getString(1);
                String[] uniqColumnsArr = ConstructWhereClauseUtil.getUniqColumns(ConstructWhereClauseUtil.getStuffInsideBrackets(result));
                if(uniqColumnsArr != null && uniqColumnsArr.length != 0){
                	uniqCols.addAll(Arrays.asList(uniqColumnsArr));
                } 
            }
            char command = commandType.charAt(0);
            if(uniqCols.size()==0 && ( command == DELETE_COMMAND_TYPE || command == UPDATE_COMMAND_TYPE )){
                throw new RuntimeException( 
                    "no unique columns found for delete or update command (this would be ok for insert). table+command=" 
                    + tableCommand+" query="+UNIQ_INDX_QUERY );
            }
            
            // parse the info string and create the query and param types
            QueryParams queryParams = getQueryParams(commandType, schemaName, tableName, infoString, uniqCols);
            
            queryList.add(queryParams);
            queryParams.setIndex(queryList.size()-1);
            tableCommandMap.put(tableCommand, queryParams.getIndex());
            LOGGER.debug("created at index "+queryParams.getIndex()+" for "+tableCommand +
                " uniq columns found were ["+result+"] and so queryParams: "+queryParams);
            return queryParams;
        }
    }
    
    public QueryParams getQueryParams(String commandType, String schemaName, String tableName, String infoString, Set<String> uniqCols) {
        QueryParams queryParams = new QueryParams();
        StringBuffer queryBuffer = new StringBuffer();
        StringBuffer insertValues = new StringBuffer();
        int numParamTypes = 0;
        char command = commandType.charAt(0);
        
        switch (command) {
            case INSERT_COMMAND_TYPE:
                queryBuffer.append("INSERT INTO "+schemaName+ "." + tableName +" ( ");
                insertValues.append(" VALUES ( ");
                break;
            case UPDATE_COMMAND_TYPE:
                queryBuffer.append("UPDATE "+schemaName+ "." + tableName + " SET ");
                break;
            case DELETE_COMMAND_TYPE:
                queryBuffer.append("DELETE FROM "+schemaName+ "." +tableName +" ");
                break;
        }
        
        String[] columns = infoString.split(ESCPD_PIPE_DLIMITER);
        
        int i = 0;
        StringBuffer paramTypeNames = new StringBuffer();
        StringBuffer paramColumnNames = new StringBuffer();
        StringBuffer paramInfoIndices = new StringBuffer();
        //Map<String,String> whereParams = new HashMap<String,String>();
        List<ParamNameAndType> whereParams = new ArrayList<ParamNameAndType>();
        
        StringBuffer whereIndices = new StringBuffer();
        
        for(String column : columns) {
            String tokens[] = column.split(COLON_DELIMITER);
            
            if(command != INSERT_COMMAND_TYPE) {
                if (uniqCols.contains(tokens[FIELD_NAME_INDEX])) {
                    //whereParams.put(tokens[FIELD_NAME_INDEX], tokens[FIELD_TYPE_INDEX]);
                	whereParams.add(new ParamNameAndType(tokens[FIELD_NAME_INDEX], tokens[FIELD_TYPE_INDEX]));
                    if (whereIndices.length() > 0) {
                        whereIndices.append(PIPE_DLIMITER);
                    }
                    whereIndices.append((i+1) * -1);
                }
            }

            if(command != DELETE_COMMAND_TYPE) {
            	
                paramTypeNames.append(tokens[FIELD_TYPE_INDEX]);
                numParamTypes++;
                
                paramColumnNames.append(tokens[FIELD_NAME_INDEX]);
                paramInfoIndices.append(i + 1);
            }
            
            if(i + 1 < columns.length) {
                if(command != DELETE_COMMAND_TYPE) { 
                    if(paramTypeNames.length() > 0) paramTypeNames.append(PIPE_DLIMITER);
                    if(paramColumnNames.length() > 0) paramColumnNames.append(PIPE_DLIMITER);
                    if(paramInfoIndices.length() > 0) paramInfoIndices.append(PIPE_DLIMITER);
                }
            }
            
            
            switch (command) {
            case INSERT_COMMAND_TYPE:
                queryBuffer.append(tokens[FIELD_NAME_INDEX]);
                insertValues.append(addParam(numParamTypes));
                if(i+1 < columns.length) {
                    queryBuffer.append(", ");
                    insertValues.append(", ");
                }
                break;
            case UPDATE_COMMAND_TYPE:
                queryBuffer.append(tokens[FIELD_NAME_INDEX] + " = "+ addParam(numParamTypes));
                if(i+1 < columns.length) {
                    queryBuffer.append(", ");
                }
                break;
            case DELETE_COMMAND_TYPE:
                break;
            }
            ++i;
        }

        if(command == INSERT_COMMAND_TYPE) {
        	queryBuffer.append(" ) ").append(insertValues).append(" ) ");
        }
        
        if(command != INSERT_COMMAND_TYPE) {
            if(whereParams.size() > 0) {
                i = 0;
                queryBuffer.append(" WHERE ");
                //for(Map.Entry<String, String> kv : whereParams.entrySet()) {
                for(ParamNameAndType paramNameNType : whereParams) {
                    if (i > 0 && i < whereParams.size()) {
                        queryBuffer.append(" AND ");
                    }
                    //queryBuffer.append((kv.getKey() + " = " + addParam(numParamTypes+1)));
                    queryBuffer.append((paramNameNType.paramName + " = " + addParam(numParamTypes+1)));
                    
                    if (i == 0) {
                    	if(paramTypeNames.length() > 0) paramTypeNames.append(PIPE_DLIMITER);
                    }
                    
                    //paramTypeNames.append(kv.getValue());
                    paramTypeNames.append(paramNameNType.paramType);
                    numParamTypes++;
                    
                    if (i + 1 < whereParams.size()) {
                        paramTypeNames.append(PIPE_DLIMITER);
                    }
                    if(command == DELETE_COMMAND_TYPE) { 
                        paramColumnNames.append(paramNameNType.paramName);
                        if (i + 1 < whereParams.size()) {
                        	paramColumnNames.append(PIPE_DLIMITER);
                        }
                    }
                    ++i;
                }
            }
        }
        
        // set the query param types and query
        queryParams.setParamColumnNames(paramColumnNames.toString());
        queryParams.setParamTypeNames(paramTypeNames.toString());
         
        if(command != INSERT_COMMAND_TYPE ) {
        	//Ideally these should always be where for delete and update 
        	if(paramInfoIndices.length() > 0 && whereIndices.length() > 0)
        	{
        		paramInfoIndices.append(PIPE_DLIMITER);
        	}
        	paramInfoIndices.append(whereIndices.toString());
        }
        queryParams.setParamInfoIndices(paramInfoIndices.toString());
        queryParams.setQuery(queryBuffer.toString());
        queryParams.setNumParamTypes(numParamTypes);
        
        return queryParams;
    }
    
    public QueryCache() {
        tableCommandMap = new HashMap<String, Integer>();  // table:operation map of queries
        queryList = new ArrayList<QueryParams>(1024);        
    }
    
    public static void main(String[] args) throws Exception {
//	Make sure you have following table in the database you entered in the commandline
//    	CREATE TABLE test1
//    	(
//    	    id serial NOT NULL primary key,
//    	    c_bytea bytea,
//    	    c_text text,
//    	    c_int integer
//    	);
    	
    	String db_url = args[0];
    	QueryCache queryCache = new QueryCache();

    	BasicDataSource masterDataSource = new BasicDataSource();
        masterDataSource.setUrl(db_url); //"jdbc:postgresql://smvcmpdev:5432/mpdb?user=portaladmin");
        masterDataSource.setDriverClassName("org.postgresql.Driver");
        
        System.out.println("================================ INSERT ==============");
        char commandType = QueryCache.INSERT_COMMAND_TYPE;
        String schTableName = "public.check_delete_1";
        String infoString = "day_id:int4:MjYyMw==:!|p:int8:MTkwMDAwMTkxOQ==:!|search_term:varchar:RG9vciBIYW5naW5n:!|is_new_user:int2:MA==:!|views:int8:MQ==:!|submits:int8:MA==:!|confirms:int8:MA==:!|revenue:int8:MA==:!|payout:int8:MA==:!|auctions:int8:!:!";
        Connection conn = masterDataSource.getConnection();
        
        QueryParams queryParams = queryCache.getQueryInfo(String.valueOf(commandType), schTableName, infoString, conn);
        System.out.println("insert queryinfo: " + queryParams.toString());

        System.out.println("================================ UPDATE ==============");
        commandType = QueryCache.UPDATE_COMMAND_TYPE;
        
        queryParams = queryCache.getQueryInfo(String.valueOf(commandType), schTableName, infoString, conn);
        System.out.println("update queryinfo: " + queryParams.toString());

        System.out.println("================================ DELETE ==============");
        commandType = QueryCache.DELETE_COMMAND_TYPE;
        
        queryParams = queryCache.getQueryInfo(String.valueOf(commandType), schTableName, infoString, conn);
        System.out.println("delete queryinfo: " + queryParams.toString());

    }
}

class ParamNameAndType {
	public ParamNameAndType(String paramName, String paramType) {
		this.paramName = paramName;
		this.paramType = paramType;
	}
	public String paramName;
	public String paramType;
}


/**
*This is the output generated against following test tables
*<pre>
*
* create table check_delete (
* day_id int not null,
* p bigint not null,
* search_term varchar not null,
* is_new_user smallint not null,
* views bigint not null,
* submits bigint not null,
* confirms bigint not null,
* revenue bigint not null,
* payout bigint not null,
* auctions bigint,
* primary key (day_id, p, search_term, is_new_user)
* );
* 
* insert into check_delete (day_id,p,search_term,is_new_user,views,submits,confirms,revenue,payout,auctions)
* values (1,1,'search_term',1,1,1,1,1,1,1);
* 
* insert into check_delete (day_id,p,search_term,is_new_user,views,submits,confirms,revenue,payout,auctions)
* values (2,2,'search_term 2',2,2,2,2,2,2,2);
* 
* ========================================================================================================================================== output
* ================================ INSERT ==============
* insert queryinfo: query=INSERT INTO public.check_delete ( day_id, p, search_term, is_new_user, views, submits, confirms, revenue, payout, auctions )  VALUES (  $1 ,  $2 ,  $3 ,  $4 ,  $5 ,  $6 ,  $7 ,  $8 ,  $9 ,  $10  )
*  paramColumnNames=day_id|p|search_term|is_new_user|views|submits|confirms|revenue|payout|auctions
*  paramTypeNames=int4|int8|varchar|int2|int8|int8|int8|int8|int8|int8
*  paramInfoIndices=1|2|3|4|5|6|7|8|9|10
*  numParamTypes=10
*  index=0
* ================================ UPDATE ==============
* update queryinfo: query=UPDATE public.check_delete SET day_id =  $1 , p =  $2 , search_term =  $3 , is_new_user =  $4 , views =  $5 , submits =  $6 , confirms =  $7 , revenue =  $8 , payout =  $9 , auctions =  $10  WHERE day_id =  $11  AND p =  $12  AND search_term =  $13  AND is_new_user =  $14
*  paramColumnNames=day_id|p|search_term|is_new_user|views|submits|confirms|revenue|payout|auctions
*  paramTypeNames=int4|int8|varchar|int2|int8|int8|int8|int8|int8|int8|int4|int8|varchar|int2
*  paramInfoIndices=1|2|3|4|5|6|7|8|9|10|-1|-2|-3|-4
*  numParamTypes=14
*  index=1
* ================================ DELETE ==============
* delete queryinfo: query=DELETE FROM public.check_delete  WHERE day_id =  $1  AND p =  $2  AND search_term =  $3  AND is_new_user =  $4
*  paramColumnNames=day_id|p|search_term|is_new_user
*  paramTypeNames=int4|int8|varchar|int2
*  paramInfoIndices=-1|-2|-3|-4
*  numParamTypes=4
*  index=2
* 
* 
* ========================================= some changes in table =======================================================================================
* create table check_delete_1 (
* day_id int not null,
* p bigint not null,
* search_term varchar not null,
* is_new_user smallint not null,
* views bigint not null,
* submits bigint not null,
* confirms bigint not null,
* revenue bigint not null,
* payout bigint not null,
* auctions bigint,
* primary key (day_id, p, search_term, is_new_user,payout)
* );
* 
* insert into check_delete_1 (day_id,p,search_term,is_new_user,views,submits,confirms,revenue,payout,auctions)
* values (1,1,'search_term',1,1,1,1,1,1,1);
* 
* insert into check_delete_1 (day_id,p,search_term,is_new_user,views,submits,confirms,revenue,payout,auctions)
* values (2,2,'search_term 2',2,2,2,2,2,2,2);
* 
* =========================================================================================================================== Output
* ================================ INSERT ==============
* insert queryinfo: query=INSERT INTO public.check_delete ( day_id, p, search_term, is_new_user, views, submits, confirms, revenue, payout, auctions )  VALUES (  $1 ,  $2 ,  $3 ,  $4 ,  $5 ,  $6 ,  $7 ,  $8 ,  $9 ,  $10  )
*  paramColumnNames=day_id|p|search_term|is_new_user|views|submits|confirms|revenue|payout|auctions
*  paramTypeNames=int4|int8|varchar|int2|int8|int8|int8|int8|int8|int8
*  paramInfoIndices=1|2|3|4|5|6|7|8|9|10
*  numParamTypes=10
*  index=0
* ================================ UPDATE ==============
* update queryinfo: query=UPDATE public.check_delete SET day_id =  $1 , p =  $2 , search_term =  $3 , is_new_user =  $4 , views =  $5 , submits =  $6 , confirms =  $7 , revenue =  $8 , payout =  $9 , auctions =  $10  WHERE day_id =  $11  AND p =  $12  AND search_term =  $13  AND is_new_user =  $14
*  paramColumnNames=day_id|p|search_term|is_new_user|views|submits|confirms|revenue|payout|auctions
*  paramTypeNames=int4|int8|varchar|int2|int8|int8|int8|int8|int8|int8|int4|int8|varchar|int2
*  paramInfoIndices=1|2|3|4|5|6|7|8|9|10|-1|-2|-3|-4
*  numParamTypes=14
*  index=1
* ================================ DELETE ==============
* delete queryinfo: query=DELETE FROM public.check_delete  WHERE day_id =  $1  AND p =  $2  AND search_term =  $3  AND is_new_user =  $4
*  paramColumnNames=day_id|p|search_term|is_new_user
*  paramTypeNames=int4|int8|varchar|int2
*  paramInfoIndices=-1|-2|-3|-4
*  numParamTypes=4
*  index=2
* </pre>
*/
