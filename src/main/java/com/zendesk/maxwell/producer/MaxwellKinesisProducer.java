package com.zendesk.maxwell.producer;

import java.nio.ByteBuffer;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import com.zendesk.maxwell.MaxwellContext;
import com.zendesk.maxwell.replication.BinlogPosition;
import com.zendesk.maxwell.row.RowMap;

import com.amazonaws.services.kinesis.producer.Attempt;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.UserRecordFailedException;
import com.amazonaws.services.kinesis.producer.UserRecordResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KinesisCallback implements FutureCallback<UserRecordResult> {
	public static final Logger logger = LoggerFactory.getLogger(KinesisCallback.class);

	private InflightMessageList inflightMessages;
	private final MaxwellContext context;
	private final BinlogPosition position;
	private final boolean isTXCommit;
	private final String json;
	private final String key;

	public KinesisCallback(InflightMessageList inflightMessages, BinlogPosition position, boolean isTXCommit, MaxwellContext context, String key, String json) {
		this.inflightMessages = inflightMessages;
		this.context = context;
		this.position = position;
		this.isTXCommit = isTXCommit;
		this.key = key;
		this.json = json;
	}

	@Override
	public void onFailure(Throwable t) {
		logger.error(t.getClass().getSimpleName() + " @ " + position + " -- " + key);
		logger.error(t.getLocalizedMessage());

		if(t instanceof UserRecordFailedException) {
			Attempt last = Iterables.getLast(((UserRecordFailedException) t).getResult().getAttempts());
			logger.error(String.format("Record failed to put - %s : %s", last.getErrorCode(), last.getErrorMessage()));
		}

		logger.error("Exception during put", t);

		markCompleted();
	};

	@Override
	public void onSuccess(UserRecordResult result) {
		if(logger.isDebugEnabled()) {
			logger.debug("->  key:" + key + ", shard id:" + result.getShardId() + ", sequence number:" + result.getSequenceNumber());
			logger.debug("   " + json);
			logger.debug("   " + position);
			logger.debug("");
		}

		markCompleted();
	};

	private void markCompleted() {
		if(isTXCommit) {
			BinlogPosition newPosition = inflightMessages.completeMessage(position);

			if(newPosition != null) {
				context.setPosition(newPosition);
			}
		}
	}
}

public class MaxwellKinesisProducer extends AbstractProducer {
	private static final Logger logger = LoggerFactory.getLogger(MaxwellKinesisProducer.class);

	private final InflightMessageList inflightMessages;

	private final KinesisProducer kinesisProducer;
	private final String kinesisStream;

	public MaxwellKinesisProducer(MaxwellContext context, String kinesisStream) {
		super(context);

		this.inflightMessages = new InflightMessageList();

		this.kinesisStream = kinesisStream;

		this.kinesisProducer = new KinesisProducer();
	}

	@Override
	public void push(RowMap r) throws Exception {
		String key = r.pkToJsonArray();
		String value = r.toJSON(outputConfig);

		if(value == null) { // heartbeat row or other row with suppressed output
			inflightMessages.addMessage(r.getPosition());
			BinlogPosition newPosition = inflightMessages.completeMessage(r.getPosition());

			if(newPosition != null) {
				context.setPosition(newPosition);
			}

			return;
		}

		if(r.isTXCommit()) {
			inflightMessages.addMessage(r.getPosition());
		}

		// release the reference to ease memory pressure
		if(!KinesisCallback.logger.isDebugEnabled()) {
			value = null;
		}

		ByteBuffer encodedValue = ByteBuffer.wrap(value.getBytes("UTF-8"));
		ListenableFuture<UserRecordResult> future = kinesisProducer.addUserRecord(kinesisStream, key, encodedValue);

		KinesisCallback callback = new KinesisCallback(inflightMessages, r.getPosition(), r.isTXCommit(), context, key, value);

		Futures.addCallback(future, callback);
	}
}
