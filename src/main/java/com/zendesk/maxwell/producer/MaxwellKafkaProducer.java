package com.zendesk.maxwell.producer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Properties;

import com.zendesk.maxwell.MaxwellAbstractRowsEvent;
import com.zendesk.maxwell.MaxwellConfig;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KafkaCallback implements Callback {
	static final Logger LOGGER = LoggerFactory.getLogger(MaxwellKafkaProducer.class);
	private final MaxwellConfig config;
	private final MaxwellAbstractRowsEvent event;
	private final String json;
	private final boolean lastRowInEvent;

	public KafkaCallback(MaxwellAbstractRowsEvent e, MaxwellConfig c, String json, boolean lastRowInEvent) {
		this.config = c;
		this.event = e;
		this.json = json;
		this.lastRowInEvent = lastRowInEvent;
	}

	@Override
	public void onCompletion(RecordMetadata md, Exception e) {
		if ( e != null ) {
			e.printStackTrace();
		} else {
			try {
				if ( LOGGER.isDebugEnabled()) {
					LOGGER.debug("->  topic:" + md.topic() + ", partition:" +md.partition() + ", offset:" + md.offset());
					LOGGER.debug("   " + this.json);
					LOGGER.debug("   " + event.getNextBinlogPosition());
					LOGGER.debug("");
				}
				if ( this.lastRowInEvent ) {
					config.setInitialPosition(event.getNextBinlogPosition());
				}
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}
	}
}
public class MaxwellKafkaProducer extends AbstractProducer {
	private final KafkaProducer<byte[], byte[]> kafka;
	private final String topic;
	private final int numPartitions;

	public MaxwellKafkaProducer(MaxwellConfig config, Properties kafkaProperties, String kafkaTopic) {
		super(config);

		topic = (kafkaTopic == null) ? "maxwell": kafkaTopic;

		if ( !kafkaProperties.containsKey("compression.type") ) {
			kafkaProperties.setProperty("compression.type", "gzip"); // enable gzip compression by default
		}

		this.kafka = new KafkaProducer<>(kafkaProperties, new ByteArraySerializer(), new ByteArraySerializer());
		this.numPartitions = kafka.partitionsFor(topic).size(); //returns 1 for new topics
	}

	public String kafkaKey(MaxwellAbstractRowsEvent e) {
		String db = e.getDatabase().getName();
		String table = e.getTable().getName();
		return db + "/" + table;
	}

	public int kafkaPartition(MaxwellAbstractRowsEvent e){
		String db = e.getDatabase().getName();
		return Math.abs(db.hashCode() % numPartitions);
	}

	@Override
	public void push(MaxwellAbstractRowsEvent e) throws Exception {
		Iterator<String> i = e.toJSONStrings().iterator();
		while ( i.hasNext() ) {
			String json = i.next();
			ProducerRecord<byte[], byte[]> record =
					new ProducerRecord<>(topic, kafkaPartition(e), kafkaKey(e).getBytes(), json.getBytes());

			kafka.send(record, new KafkaCallback(e, this.config, json, !i.hasNext()));
		}

	}

}
