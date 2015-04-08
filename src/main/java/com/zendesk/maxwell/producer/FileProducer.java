package com.zendesk.maxwell.producer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.zendesk.maxwell.MaxwellAbstractRowsEvent;
import com.zendesk.maxwell.MaxwellConfig;

public class FileProducer extends AbstractProducer {
	private final File file;
	private final FileWriter fileWriter;

	public FileProducer(MaxwellConfig config, String filename) throws IOException {
		super(config);
		this.file = new File(filename);
		this.fileWriter = new FileWriter(this.file, true);
	}

	@Override
	public void push(MaxwellAbstractRowsEvent e) throws Exception {
		for (String json : e.toJSONStrings() ) {
			this.fileWriter.write(json);
			this.fileWriter.write('\n');
			this.fileWriter.flush();
		}
		this.onComplete(e);
		config.setInitialPosition(e.getNextBinlogPosition());
	}
}
