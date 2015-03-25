package com.zendesk.maxwell.schema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import snaq.db.ConnectionPool;

import com.zendesk.maxwell.BinlogPosition;

// todo: rename something better
public class SchemaPosition implements Runnable {
	static final Logger LOGGER = LoggerFactory.getLogger(SchemaPosition.class);
	private final Long serverID;
	private BinlogPosition lastPosition;
	private final AtomicReference<BinlogPosition> position;
	private final AtomicBoolean run;
	private Thread thread;
	private final ConnectionPool connectionPool;

	public SchemaPosition(ConnectionPool pool, Long serverID) {
		this.connectionPool = pool;
		this.serverID = serverID;
		this.lastPosition = null;
		this.position = new AtomicReference<>();
		this.run = new AtomicBoolean(false);
	}

	public void start() {
		this.thread = new Thread(this, "Position Flush Thread");
		this.run.set(true);
		thread.start();
	}

	public void stop() {
		this.run.set(false);

		thread.interrupt();

		while ( thread.isAlive() ) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) { }
		}
	}

	@Override
	public void run() {
		while ( true && run.get() ) {
			BinlogPosition newPosition = position.get();

			if ( newPosition != null && !newPosition.equals(lastPosition) ) {
				store(newPosition);
				lastPosition = newPosition;
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) { }
		}

		store(position.get());
	}


	private void store(BinlogPosition newPosition) {
		String sql = "INSERT INTO `maxwell`.`positions` set "
				+ "server_id = ?, "
				+ "binlog_file = ?, "
				+ "binlog_position = ? "
				+ "ON DUPLICATE KEY UPDATE binlog_file=?, binlog_position=?";
		try(Connection c = connectionPool.getConnection() ){
			PreparedStatement s = c.prepareStatement(sql);

			LOGGER.debug("Writing initial position: " + newPosition);
			s.setLong(1, serverID);
			s.setString(2, newPosition.getFile());
			s.setLong(3, newPosition.getOffset());
			s.setString(4, newPosition.getFile());
			s.setLong(5, newPosition.getOffset());

			s.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void set(BinlogPosition p) {
		position.set(p);
	}

	public BinlogPosition get() throws SQLException {
		BinlogPosition p = position.get();
		if ( p != null )
			return p;

		try ( Connection c = connectionPool.getConnection() ) {
			PreparedStatement s = c.prepareStatement("SELECT * from `maxwell`.`positions` where server_id = ?");
			s.setLong(1, serverID);

			ResultSet rs = s.executeQuery();
			if ( !rs.next() )
				return null;

			return new BinlogPosition(rs.getLong("binlog_position"), rs.getString("binlog_file"));
		}
	}
}