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
    
//    private static final Logger LOGGER = Logger.getLogger(QueryCache.class);
    
    public QueryParams getQueryInfo(String commandType, String schTableName, String infoString, Connection masterConn) throws SQLException {
        String tableCommand = schTableName + PIPE_DLIMITER + commandType;
        
        // first check if we have already processed this query
        Integer index = tableCommandMap.get(tableCommand);
        if(index != null) {
            return queryList.get(index);
        }
        else {
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
            
            if (resultSet.next()) {
                String result = resultSet.getString(1);
                
                int startIndex = result.indexOf("(\"");;
                if (startIndex > 0) {
                    colStr = result.substring(startIndex+2, result.indexOf("\")"));
                }
                else {
                    startIndex = result.indexOf("(");
                    colStr = result.substring(startIndex+1, result.indexOf(")"));
                }
                
                uniqCols.addAll(Arrays.asList(colStr.split(COMMA_SPACE_DELIMITER)));
            }
            
            // parse the info string and create the query and param types
            QueryParams queryParams = getQueryParams(commandType, schemaName, tableName, infoString, uniqCols);
            
            queryList.add(queryParams);
            queryParams.setIndex(queryList.size()-1);
            tableCommandMap.put(tableCommand, queryParams.getIndex());
            return queryParams;
        }
    }
    
    public QueryParams getQueryParams(String commandType, String schemaName, String tableName, String infoString, Set<String> uniqCols) {
        QueryParams queryParams = new QueryParams();
        StringBuffer queryBuffer = new StringBuffer();
        StringBuffer insertValues = new StringBuffer();
        int numParamTypes = 0;
        
        switch (commandType.charAt(0)) {
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
        Map<String,String> whereParams = new HashMap<String,String>();
        StringBuffer whereIndices = new StringBuffer();
        
        for(String column : columns) {
            String tokens[] = column.split(COLON_DELIMITER);
            
            if(commandType.charAt(0) != INSERT_COMMAND_TYPE) {
                if (uniqCols.contains(tokens[FIELD_NAME_INDEX])) {
                    whereParams.put(tokens[FIELD_NAME_INDEX], tokens[FIELD_TYPE_INDEX]);
                    if (whereIndices.length() > 0) {
                        whereIndices.append(PIPE_DLIMITER);
                    }
                    whereIndices.append((i+1) * -1);
                }
            }

            if(commandType.charAt(0) != DELETE_COMMAND_TYPE) {
            	
                paramTypeNames.append(tokens[FIELD_TYPE_INDEX]);
                numParamTypes++;
                
                paramColumnNames.append(tokens[FIELD_NAME_INDEX]);
                paramInfoIndices.append(i + 1);
            }
            
            if(i + 1 < columns.length) {
                if(commandType.charAt(0) != DELETE_COMMAND_TYPE) { 
                    if(paramTypeNames.length() > 0) paramTypeNames.append(PIPE_DLIMITER);
                    if(paramColumnNames.length() > 0) paramColumnNames.append(PIPE_DLIMITER);
                    if(paramInfoIndices.length() > 0) paramInfoIndices.append(PIPE_DLIMITER);
                }
            }
            
            
            switch (commandType.charAt(0)) {
            case INSERT_COMMAND_TYPE:
                queryBuffer.append(tokens[FIELD_NAME_INDEX]);
                insertValues.append("?");
                if(i+1 < columns.length) {
                    queryBuffer.append(", ");
                    insertValues.append(", ");
                }
                break;
            case UPDATE_COMMAND_TYPE:
                queryBuffer.append(tokens[FIELD_NAME_INDEX] + " = ? ");
                if(i+1 < columns.length) {
                    queryBuffer.append(", ");
                }
                break;
            case DELETE_COMMAND_TYPE:
                break;
            }
            i++;
        }

        if(commandType.charAt(0) == INSERT_COMMAND_TYPE) {
        	queryBuffer.append(" ) ").append(insertValues).append(" ) ");
        }
        
        if(commandType.charAt(0) != INSERT_COMMAND_TYPE) {
            if(whereParams.size() > 0) {
                i = 0;
                queryBuffer.append(" WHERE ");
                for(Map.Entry<String, String> kv : whereParams.entrySet()) {
                    if (i != 0 && i != whereParams.size()) {
                        queryBuffer.append(" AND ");
                    }
                    queryBuffer.append((kv.getKey() + " = ? "));
                    
                    if (i == 0) {
                    	if(paramTypeNames.length() > 0) paramTypeNames.append(PIPE_DLIMITER);
                    }
                    
                    paramTypeNames.append(kv.getValue());
                    numParamTypes++;
                    
                    if (i + 1 < whereParams.size()) {
                        paramTypeNames.append(PIPE_DLIMITER);
                    }
                    if(commandType.charAt(0) == DELETE_COMMAND_TYPE) { 
                        paramColumnNames.append(kv.getKey());
                    }
                }
            }
        }
        
        // set the query param types and query
        queryParams.setParamColumnNames(paramColumnNames.toString());
        queryParams.setParamTypeNames(paramTypeNames.toString());
         
        if(commandType.charAt(0) != INSERT_COMMAND_TYPE ) {
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
    
    public void init() {
        tableCommandMap = new HashMap<String, Integer>();  // table:operation map of queries
        queryList = new ArrayList<QueryParams>(1024);        
    }
    
    public void flush() {
        tableCommandMap = null;
        queryList = null;
    }
    
    public static void main(String[] args) throws SQLException {
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

    	queryCache.init();

    	BasicDataSource masterDataSource = new BasicDataSource();
        masterDataSource.setUrl(db_url); //"jdbc:postgresql://smvcmpdev:5432/mpdb?user=portaladmin");
        masterDataSource.setDriverClassName("org.postgresql.Driver");
        
        System.out.println("================================ INSERT ==============");
        char commandType = QueryCache.INSERT_COMMAND_TYPE;
        String schTableName = "public.test1";
        String infoString = "id:int4:Mg==:Mg==|c_bytea:bytea:Ng==:Ng==|c_text:text:NA==:NQ==|c_int:int4:!:!";
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

        queryCache.flush();
    }
}
