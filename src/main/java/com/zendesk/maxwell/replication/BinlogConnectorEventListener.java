package com.zendesk.maxwell.replication;

import com.codahale.metrics.Timer;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.GtidEventData;
import com.github.shyiko.mysql.binlog.event.QueryEventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class BinlogConnectorEventListener implements BinaryLogClient.EventListener,
					      BinaryLogClient.LifecycleListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(BinlogConnectorEventListener.class);

	private final BlockingQueue<BinlogConnectorEvent> queue;
	private Timer queueTimer;
	private Timer mysqlTxExecutionTimer;
	protected final AtomicBoolean mustStop = new AtomicBoolean(false);
	private final BinaryLogClient client;
	private String gtid;

	public BinlogConnectorEventListener(
		BinaryLogClient client,
		BlockingQueue<BinlogConnectorEvent> q,
		Timer queueTimer,
		Timer mysqlTxExecutionTimer) {
		this.client = client;
		this.queue = q;
		this.queueTimer = queueTimer;
		this.mysqlTxExecutionTimer = mysqlTxExecutionTimer;
	}

	public void stop() {
		mustStop.set(true);
	}

	@Override
	public void onEvent(Event event) {
		long eventSeenAt = 0L;

		if (event.getHeader().getEventType() == EventType.QUERY) {
			QueryEventData queryEventData = event.getData();
			mysqlTxExecutionTimer.update(queryEventData.getExecutionTime(), TimeUnit.SECONDS);
		}

		boolean trackMetrics = event.getHeader().getEventType() == EventType.XID;
		if (trackMetrics) {
			eventSeenAt = System.currentTimeMillis();
		}

		while (mustStop.get() != true) {
			if (event.getHeader().getEventType() == EventType.GTID) {
				gtid = ((GtidEventData)event.getData()).getGtid();
			}

			BinlogConnectorEvent ep = new BinlogConnectorEvent(event, client.getBinlogFilename(), client.getGtidSet(), gtid);
			try {
				if ( queue.offer(ep, 100, TimeUnit.MILLISECONDS ) ) {
					break;
				}
			} catch (InterruptedException e) {
				return;
			}
		}

		if (trackMetrics) {
			queueTimer.update(System.currentTimeMillis() - eventSeenAt, TimeUnit.MILLISECONDS);
		}
	}

	@Override
	public void onConnect(BinaryLogClient client) {
		LOGGER.info("Binlog connected.");
	};

	@Override
	public void onCommunicationFailure(BinaryLogClient client, Exception ex) {
		LOGGER.warn("Communication failure.", ex);
	}

	@Override
	public void onEventDeserializationFailure(BinaryLogClient client, Exception ex) {
		LOGGER.warn("Event deserialization failure.", ex);
	}

	@Override
	public void onDisconnect(BinaryLogClient client) {
		LOGGER.info("Binlog disconnected.");
	}
}

