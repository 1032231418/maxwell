package com.zendesk.exodus.schema.columndef;

public abstract class ColumnDef {
	protected final String tableName;
	protected final String name;
	protected final String type;
	private final int pos;

	public ColumnDef(String tableName, String name, String type, int pos) {
		this.tableName = tableName;
		this.name = name;
		this.type = type;
		this.pos = pos;
	}

	public abstract boolean matchesMysqlType(int type);
	public abstract String toSQL(Object value);

	public static ColumnDef build(String tableName, String name, String encoding, String type, int pos, boolean signed) {
		switch(type) {
		case "tinyint":
		case "smallint":
		case "mediumint":
		case "int":
			return new IntColumnDef(tableName, name, type, pos, signed);
		case "bigint":
			return new BigIntColumnDef(tableName, name, type, pos, signed);
		case "tinytext":
		case "text":
		case "mediumtext":
		case "longtext":
		case "varchar":
		case "char":
			return new StringColumnDef(tableName, name, type, pos, encoding);
		case "tinyblob":
		case "blob":
		case "mediumblob":
		case "longblob":
			return new StringColumnDef(tableName, name, type, pos, "binary");
		case "float":
		case "double":
			return new FloatColumnDef(tableName, name, type, pos);
		case "decimal":
			return new DecimalColumnDef(tableName, name, type, pos);
		case "date":
			return new DateColumnDef(tableName, name, type, pos);
		case "datetime":
		case "timestamp":
			return new DateTimeColumnDef(tableName, name, type, pos);
		case "year":
			return new YearColumnDef(tableName, name, type, pos);
		case "time":
		case "bit":
		default:
			throw new IllegalArgumentException("unsupported column type " + type);
		}
	}

	public String getName() {
		return name;
	}

	public String getTableName() {
		return tableName;
	}

	public String getType() {
		return type;
	}

	public int getPos() {
		return pos;
	}
}
