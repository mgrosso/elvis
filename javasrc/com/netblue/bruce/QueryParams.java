package com.netblue.bruce;

public class QueryParams {
    String query;
    String paramColumnNames;  // pipe delimited
    String paramTypeNames;    // pipe delimited
    String paramInfoIndices;  // pipe delimited
    int numParamTypes = 0; // num question marks in query
    int index = 0;  // index where this query params info is stored in the query cache
    
    public String getQuery() {
        return query;
    }
    public void setQuery(String query) {
        this.query = query;
    }
    public String getParamColumnNames() {
        return paramColumnNames;
    }
    public void setParamColumnNames(String paramColumnNames) {
        this.paramColumnNames = paramColumnNames;
    }
    public String getParamTypeNames() {
        return paramTypeNames;
    }
    public void setParamTypeNames(String paramTypeNames) {
        this.paramTypeNames = paramTypeNames;
    }
    public String getParamInfoIndices() {
        return paramInfoIndices;
    }
    public void setParamInfoIndices(String paramInfoIndices) {
        this.paramInfoIndices = paramInfoIndices;
    }
    public int getNumParamTypes() {
        return numParamTypes;
    }
    public void setNumParamTypes(int numParamTypes) {
        this.numParamTypes = numParamTypes;
    }
    public int getIndex() {
        return index;
    }
    public void setIndex(int index) {
        this.index = index;
    }
    
    public String toString() {
        StringBuffer bf = new StringBuffer();
        bf.append("query=" + this.getQuery());
        bf.append(", paramColumnNames=" + this.getParamColumnNames());
        bf.append(", paramTypeNames=" + this.getParamTypeNames());
        bf.append(", paramInfoIndices=" + this.getParamInfoIndices());
        bf.append(", numParamTypes=" + this.getNumParamTypes());
        bf.append(", index=" + this.getIndex());
        return bf.toString();
    }
}
