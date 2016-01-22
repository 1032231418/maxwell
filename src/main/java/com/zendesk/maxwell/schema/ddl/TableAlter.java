package com.zendesk.maxwell.schema.ddl;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.zendesk.maxwell.schema.Database;
import com.zendesk.maxwell.schema.Schema;
import com.zendesk.maxwell.schema.Table;

@JsonAppend(attrs = @JsonAppend.Attr(propName = "type", value = "table-alter"))

public class TableAlter extends SchemaChange {
	public String database;
	public String table;
	@JsonProperty("columns")
	public ArrayList<ColumnMod> columnMods;
	@JsonProperty("new_database")
	public String newDatabase;
	@JsonProperty("new_table")
	public String newTableName;

	public String convertCharset;
	public String defaultCharset;

	@JsonProperty("primary_keys")
	public List<String> pks;


	public TableAlter(String database, String table) {
		this.database = database;
		this.table = table;
		this.columnMods = new ArrayList<>();
	}

	@Override
	public String toString() {
		return "TableAlter<database: " + database + ", table:" + table + ">";
	}

	@Override
	public Schema apply(Schema originalSchema) throws SchemaSyncError {
		Schema newSchema = originalSchema.copy();

		Database database = newSchema.findDatabase(this.database);
		if ( database == null ) {
			throw new SchemaSyncError("Couldn't find database: " + this.database);
		}

		Table table = database.findTable(this.table);
		if ( table == null ) {
			throw new SchemaSyncError("Couldn't find table: " + this.database + "." + this.table);
		}


		if ( newTableName != null && newDatabase != null ) {
			Database destDB = newSchema.findDatabase(this.newDatabase);
			if ( destDB == null )
				throw new SchemaSyncError("Couldn't find database " + this.database);

			table.rename(newTableName);

			database.getTableList().remove(table);
			destDB.addTable(table);
		}

		for (ColumnMod mod : columnMods) {
			mod.apply(table);
		}

		if ( this.pks != null ) {
			table.setPKList(this.pks);
		}
		table.setDefaultColumnEncodings();

		return newSchema;
	}
}