package com.zendesk.maxwell;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;
import com.djdch.log4j.StaticShutdownCallbackRegistry;
import com.zendesk.maxwell.recovery.Recovery;
import com.zendesk.maxwell.recovery.RecoveryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zendesk.maxwell.bootstrap.AbstractBootstrapper;
import com.zendesk.maxwell.producer.AbstractProducer;
import com.zendesk.maxwell.schema.MysqlSchemaStore;
import com.zendesk.maxwell.schema.SchemaStoreSchema;

public class Maxwell implements Runnable {
	protected MaxwellConfig config;
	protected MaxwellContext context;
	protected MaxwellReplicator replicator;
	static final Logger LOGGER = LoggerFactory.getLogger(Maxwell.class);

	public Maxwell(MaxwellConfig config) throws SQLException {
		this.config = config;
		this.context = new MaxwellContext(this.config);
		this.context.probeConnections();
	}

	public void run() {
		try {
			start();
		} catch (Exception e) {
			LOGGER.error("maxwell encountered an exception", e);
		}
	}

	public void terminate() {
		try {
			// send a final heartbeat through the system
			context.heartbeat();
			Thread.sleep(100);

			if ( this.replicator != null)
				replicator.stopLoop();
			} catch (TimeoutException e) {
				System.err.println("Timed out trying to shutdown maxwell parser thread.");
			} catch (InterruptedException e) {
			} catch (Exception e) {
		}

		if ( this.context != null )
			context.terminate();

		replicator = null;
		context = null;
	}

	protected BinlogPosition getInitialPosition() throws Exception {
		/* first method:  do we have a stored position for this server? */
		BinlogPosition initial = this.context.getInitialPosition();

		/* second method: are we recovering from a master swap? */
		if ( initial == null ) {
			RecoveryInfo recoveryInfo = this.context.getRecoveryInfo();

			if ( recoveryInfo != null ) {
				Recovery masterRecovery = new Recovery(
					config.replicationMysql,
					config.databaseName,
					this.context.getReplicationConnectionPool(),
					this.context.getCaseSensitivity(),
					recoveryInfo
				);

				initial = masterRecovery.recover();
			}
		}

		/* third method: capture the current master postiion. */
		if ( initial == null ) {
			try ( Connection c = context.getReplicationConnection() ) {
				initial = BinlogPosition.capture(c);
			}
		}
		return initial;
	}

	private void start() throws Exception {
		try ( Connection connection = this.context.getReplicationConnection();
			  Connection rawConnection = this.context.getRawMaxwellConnection() ) {
			MaxwellMysqlStatus.ensureReplicationMysqlState(connection);
			MaxwellMysqlStatus.ensureMaxwellMysqlState(rawConnection);

			SchemaStoreSchema.ensureMaxwellSchema(rawConnection, this.config.databaseName);

			try ( Connection schemaConnection = this.context.getMaxwellConnection() ) {
				SchemaStoreSchema.upgradeSchemaStoreSchema(schemaConnection);
			}

			String producerClass = this.context.getProducer().getClass().getSimpleName();
			LOGGER.info("Maxwell is booting (" + producerClass + "), starting at " + this.context.getInitialPosition());
		} catch ( SQLException e ) {
			LOGGER.error("SQLException: " + e.getLocalizedMessage());
			LOGGER.error(e.getLocalizedMessage());
			return;
		}

		AbstractProducer producer = this.context.getProducer();
		AbstractBootstrapper bootstrapper = this.context.getBootstrapper();

		BinlogPosition initPosition = getInitialPosition();
		MysqlSchemaStore mysqlSchemaStore = new MysqlSchemaStore(this.context, initPosition);
		this.replicator = new MaxwellReplicator(mysqlSchemaStore, producer, bootstrapper, this.context, initPosition);

		bootstrapper.resume(producer, replicator);

		replicator.setFilter(context.getFilter());

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				terminate();
			}
		});

		this.context.start();
		replicator.runLoop();
	}

	public static void main(String[] args) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				StaticShutdownCallbackRegistry.invoke();
			}
		});

		try {
			MaxwellConfig config = new MaxwellConfig(args);

			if ( config.log_level != null )
				MaxwellLogging.setLevel(config.log_level);

			new Maxwell(config).start();
		} catch ( Exception e ) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
