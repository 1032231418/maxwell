package com.zendesk.exodus.schema.ddl;

import java.util.ArrayList;

public class TableAlter {
	public String database;
	public String tableName;
	public ArrayList<ColumnMod> columnMods;
	public String newTableName;
	public String newDatabase;

	public String convertCharset;
	public String defaultCharset;


	public TableAlter(String database) {
		this.database = database;
		this.columnMods = new ArrayList<>();
	}

	@Override
	public String toString() {
		return "TableAlter<database: " + database + ", table:" + tableName + ">";
	}
}