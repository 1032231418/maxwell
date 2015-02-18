package com.zendesk.maxwell.schema.ddl;

import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zendesk.maxwell.schema.Database;
import com.zendesk.maxwell.schema.Schema;
import com.zendesk.maxwell.schema.Table;

public abstract class SchemaChange {
    final static Logger LOGGER = LoggerFactory.getLogger(SchemaChange.class);

	public abstract Schema apply(Schema originalSchema) throws SchemaSyncError;

	public static List<SchemaChange> parse(String currentDB, String sql) {
		ANTLRInputStream input = new ANTLRInputStream(sql);
		mysqlLexer lexer = new mysqlLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		mysqlParser parser = new mysqlParser(tokens);

		MysqlParserListener listener = new MysqlParserListener(currentDB);

		LOGGER.debug("SQL_PARSE <- \"" + sql + "\"");
		ParseTree tree = parser.parse();

		ParseTreeWalker.DEFAULT.walk(listener, tree);
		LOGGER.debug("SQL_PARSE ->   " + tree.toStringTree(parser));

		return listener.getSchemaChanges();
	}

}
