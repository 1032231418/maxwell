package com.zendesk.maxwell.schema.columndef;

import com.fasterxml.jackson.annotation.JsonProperty;

abstract public class EnumeratedColumnDef extends ColumnDef  {
	@JsonProperty("enum-values")
	protected String[] enumValues;

	public EnumeratedColumnDef() { }
	public EnumeratedColumnDef(String name, String type, int pos, String [] enumValues) {
		this.name = name;
		this.type = type;
		this.pos = pos;
		this.enumValues = enumValues;
	}

	public String[] getEnumValues() {
		return enumValues;
	}
}
