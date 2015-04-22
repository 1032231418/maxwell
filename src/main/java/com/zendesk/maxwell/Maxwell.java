package com.zendesk.maxwell;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zendesk.maxwell.producer.AbstractProducer;
import com.zendesk.maxwell.schema.Schema;
import com.zendesk.maxwell.schema.SchemaCapturer;
import com.zendesk.maxwell.schema.SchemaStore;
import com.zendesk.maxwell.schema.ddl.SchemaSyncError;

public class Maxwell {
	private Schema schema;
	private MaxwellConfig config;
	private MaxwellContext context;
	static final Logger LOGGER = LoggerFactory.getLogger(Maxwell.class);

	private void initFirstRun(Connection connection) throws SQLException, IOException, SchemaSyncError {
		LOGGER.info("Maxwell is capturing initial schema");
		SchemaCapturer capturer = new SchemaCapturer(connection);
		this.schema = capturer.capture();

		BinlogPosition pos = BinlogPosition.capture(connection);
		SchemaStore store = new SchemaStore(connection, this.schema, pos);
		store.save();

		this.context.setInitialPosition(pos);
	}

	private void run(String[] args) throws Exception {
		this.config = MaxwellConfig.buildConfig("config.properties", args);

		if ( this.config.log_level != null )
			MaxwellLogging.setLevel(this.config.log_level);

		this.context = new MaxwellContext(this.config);

		try ( Connection connection = this.context.getConnectionPool().getConnection() ) {
			MaxwellMysqlStatus.ensureMysqlState(connection);
			SchemaStore.ensureMaxwellSchema(connection);

			if ( this.context.getInitialPosition() != null ) {
				LOGGER.info("Maxwell is booting, starting at " + this.context.getInitialPosition());
				SchemaStore store = SchemaStore.restore(connection, this.context.getInitialPosition());
				this.schema = store.getSchema();
			} else {
				initFirstRun(connection);
			}
		} catch ( SQLException e ) {
			LOGGER.error("Failed to connect to mysql server @ " + this.config.getConnectionURI());
			LOGGER.error(e.getLocalizedMessage());
			return;
		}

		AbstractProducer producer = this.context.getProducer();

		MaxwellParser p = new MaxwellParser(this.schema, producer, this.context, this.context.getInitialPosition());
		p.run();

	}

	public static void main(String[] args) {
		try {
			new Maxwell().run(args);
		} catch ( Exception e ) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
