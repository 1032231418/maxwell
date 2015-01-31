package com.zendesk.exodus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.zendesk.exodus.schema.SchemaCapturer;

public class AbstractMaxwellTest {
	protected static MysqlIsolatedServer server;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		server = new MysqlIsolatedServer();
		server.boot();
	}

	private String getSQLDir() {
		 final String dir = System.getProperty("user.dir");
		 return dir + "/../spec/sql";
	}


	private void resetMaster() throws SQLException, IOException {
		List<String> queries = new ArrayList<String>(Arrays.asList(
				"DROP DATABASE if exists shard_1",
				"CREATE DATABASE shard_1",
				"USE shard_1"
		));

		for ( File file: new File(getSQLDir()).listFiles()) {
			if ( !file.getName().endsWith(".sql"))
				continue;

			byte[] sql = Files.readAllBytes(file.toPath());
			String s = new String(sql);
			if ( s != null ) {
				queries.add(s);
			}
		}

		queries.add("RESET MASTER");

		server.executeList(queries);
	}

	private void generateBinlogEvents() throws IOException, SQLException {
		Path p = Paths.get(System.getProperty("user.dir") + "/../spec/rows.sql");
		List<String>sql = Files.readAllLines(p, Charset.forName("UTF-8"));

		server.executeList(sql);
	}

	@Before
	public void setupMysql() throws SQLException, IOException, InterruptedException {
		resetMaster();
		generateBinlogEvents();
	}

	protected List<ExodusAbstractRowsEvent>getRowsForSQL(String queries[], ExodusFilter filter) throws Exception {
		BinlogPosition start = BinlogPosition.capture(server.getConnection());
		ExodusParser p = new ExodusParser(server.getBaseDir() + "/mysqld", start.getFile());
		SchemaCapturer capturer = new SchemaCapturer(server.getConnection());

		ArrayList<ExodusAbstractRowsEvent> list = new ArrayList<>();

		p.setSchema(capturer.capture());
		p.setFilter(filter);

        server.executeList(Arrays.asList(queries));

        p.setStartOffset(start.getOffset());

        ExodusAbstractRowsEvent e;
        while ( (e = p.getEvent()) != null )
        	list.add(e);

        return list;
	}


	@After
	public void tearDown() throws Exception {
	}
}
