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
	private final String key;
	private final boolean lastRowInEvent;

	public KafkaCallback(MaxwellAbstractRowsEvent e, MaxwellConfig c, String key, String json, boolean lastRowInEvent) {
		this.config = c;
		this.event = e;
		this.key = key;
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
					LOGGER.debug("->  key:" + key + ", partition:" +md.partition() + ", offset:" + md.offset());
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

	public int kafkaPartition(MaxwellAbstractRowsEvent e){
		String db = e.getDatabase().getName();
		return Math.abs(db.hashCode() % numPartitions);
	}

	@Override
	public void push(MaxwellAbstractRowsEvent e) throws Exception {
		Iterator<String> i = e.toJSONStrings().iterator();
		Iterator<String> j = e.getPKStrings().iterator();

		while ( i.hasNext() && j.hasNext() ) {
			String json = i.next();
			String key = j.next();

			ProducerRecord<byte[], byte[]> record =
					new ProducerRecord<>(topic, kafkaPartition(e), key.getBytes(), json.getBytes());

			kafka.send(record, new KafkaCallback(e, this.config, key, json, !i.hasNext()));
		}

	}

}
