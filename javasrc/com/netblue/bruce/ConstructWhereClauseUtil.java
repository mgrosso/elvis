package com.netblue.bruce;

public class ConstructWhereClauseUtil {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String[] queries = {"CREATE UNIQUE INDEX test_comp_key_1_pkey ON test.test_comp_key_1 USING btree (id)",
							"CREATE UNIQUE INDEX test_comp_key_1_name_key ON test.test_comp_key_1 USING btree (name, \"time\", phone)",
							"CREATE UNIQUE INDEX test_comp_key_1_time_key ON test.test_comp_key_1 USING btree (\"time\", name)",
							"CREATE UNIQUE INDEX test_comp_key_1_phone_key ON test.test_comp_key_1 USING btree (phone, \"time\")"};

		for(String query:queries) {
			String[] uniqColumns = getUniqColumns(getStuffInsideBrackets(query));
			if(uniqColumns != null) {
				for(String column:uniqColumns) {
					System.out.printf("[%s] ",column);
				}
			}
			System.out.println("\n"+prepareWhereClause(uniqColumns));
		}
	}
	
	public static String prepareWhereClause(String[] uniqColumns) {
		if(uniqColumns == null || uniqColumns.length == 0) 
			return null;
		
		StringBuffer whereClause = new StringBuffer(" ");
		for(int i = 0; i < uniqColumns.length; i++) {
			if(i > 0 && i < uniqColumns.length)
				whereClause.append(" AND ");
			whereClause.append(uniqColumns[i]+"= ? ");
		}
		return whereClause.toString();
	}
	
	public static String[] getUniqColumns(String stuffInsideBrackets) {
		if(stuffInsideBrackets == null || stuffInsideBrackets.trim().length() == 0) 
			return null;
		
		String stuffInsideBracketsNoDQuots = stuffInsideBrackets.replace('"', ' ');
		String[] uniqColumns = stuffInsideBracketsNoDQuots.split(",");
		
		for(int i = 0 ; i < uniqColumns.length ; i++) {
			uniqColumns[i] = uniqColumns[i].trim();
		}
		
		return uniqColumns;
	}
	
	public static String getStuffInsideBrackets(String query) {
		if(query == null || query.trim().length() == 0) 
			return null;
		
		int begin = query.indexOf('(');
		int end = query.indexOf(')');
		if(end > begin && begin != -1 && end != -1) {
			return query.substring(begin+1,end);
		} else
			return null;
	}

}
