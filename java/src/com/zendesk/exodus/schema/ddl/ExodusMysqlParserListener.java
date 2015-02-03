package com.zendesk.exodus.schema.ddl;

import java.util.List;

import org.antlr.v4.runtime.tree.ErrorNode;

import com.zendesk.exodus.schema.columndef.ColumnDef;
import com.zendesk.exodus.schema.ddl.mysqlParser.Add_columnContext;
import com.zendesk.exodus.schema.ddl.mysqlParser.Alter_tbl_preambleContext;
import com.zendesk.exodus.schema.ddl.mysqlParser.Charset_defContext;
import com.zendesk.exodus.schema.ddl.mysqlParser.Col_positionContext;
import com.zendesk.exodus.schema.ddl.mysqlParser.Data_typeContext;
import com.zendesk.exodus.schema.ddl.mysqlParser.Int_flagsContext;

abstract class ColumnMod {
	public String name;

	public ColumnMod(String name) {
		this.name = name;
	}
}

class ColumnPosition {
	enum Position { FIRST, AFTER, DEFAULT };

	public Position position;
	public String afterColumn;

	public ColumnPosition() {
		position = Position.DEFAULT;
	}
}

class AddColumnMod extends ColumnMod {
	public ColumnDef definition;
	public ColumnPosition position;

	public AddColumnMod(String name, ColumnDef d, ColumnPosition position) {
		super(name);
		this.definition = d;
		this.position = position;
	}
}

class RemoveColumnMod extends ColumnMod {
	public RemoveColumnMod(String name) {
		super(name);
	}
}

class ExodusSQLSyntaxRrror extends RuntimeException {
	public ExodusSQLSyntaxRrror(String message) {
		super(message);
	}
}

public class ExodusMysqlParserListener extends mysqlBaseListener {
	private TableAlter alterStatement;

	public TableAlter getAlterStatement() {
		return alterStatement;
	}

	private final String currentDatabase;

	ExodusMysqlParserListener(String currentDatabase)  {
		this.currentDatabase = currentDatabase;
	}

	@Override
	public void visitErrorNode(ErrorNode node) {
		this.alterStatement = null;
		System.out.println(node.getParent().getText());
		throw new ExodusSQLSyntaxRrror(node.getText());
	}

	private String unquote(String ident) {
		return ident.replaceAll("^`", "").replaceAll("`$", "");
	}

	@Override
	public void exitAlter_tbl_preamble(Alter_tbl_preambleContext ctx) {
		alterStatement = new TableAlter(currentDatabase);

		if ( ctx.table_name().id().size() > 1 ) {
			alterStatement.database  = unquote(ctx.table_name().id(0).getText());
			alterStatement.tableName = unquote(ctx.table_name().id(1).getText());
		} else {
			alterStatement.tableName = unquote(ctx.table_name().id(0).getText());
		}
		System.out.println(alterStatement);
	}

	private boolean isSigned(List<Int_flagsContext> flags) {
		for ( Int_flagsContext flag : flags ) {
			if ( flag.UNSIGNED() != null ) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void exitAdd_column(Add_columnContext ctx) {
		String name = unquote(ctx.col_name().getText());
		String colType = null, colEncoding = null;
		boolean signed = true;
		Data_typeContext dctx = ctx.column_definition().data_type();

		if ( dctx.generic_type() != null ) {
			colType = dctx.generic_type().col_type.getText();
		} else if ( dctx.signed_type() != null ) {
			colType = dctx.signed_type().col_type.getText();
			signed = isSigned(dctx.signed_type().int_flags());
		} else if ( dctx.string_type() != null ) {
			colType = dctx.string_type().col_type.getText();

			Charset_defContext charsetDef = dctx.string_type().charset_def();
			if ( charsetDef != null && charsetDef.character_set(0) != null ) {
				colEncoding = charsetDef.character_set(0).IDENT().getText();
			} else {
				// BIG TODO: default to database,table,encodings
				colEncoding = "utf8";
			}
		}


		ColumnDef def = ColumnDef.build(alterStatement.tableName, name, colEncoding, colType.toLowerCase(), -1, signed);

		ColumnPosition p = new ColumnPosition();

		Col_positionContext pctx = ctx.col_position();
		if ( pctx != null ) {
			if ( pctx.FIRST() != null ) {
				p.position = ColumnPosition.Position.FIRST;
			} else if ( pctx.AFTER() != null ) {
				p.position = ColumnPosition.Position.AFTER;
				p.afterColumn = unquote(pctx.id().getText());
			}
		}

		System.out.println(ctx.column_definition().toStringTree());
		alterStatement.columnMods.add(new AddColumnMod(name, def, p));
	}
}
