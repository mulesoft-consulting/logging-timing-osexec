package com.mulesoft.services.loggingtiming;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.processor.LoggerMessageProcessor;
import org.mule.api.processor.MessageProcessor;
import org.mule.module.db.internal.processor.AbstractDbMessageProcessor;
import org.mule.module.http.internal.request.DefaultHttpRequester;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;

public class ExecutionEventProcessor {

	private static final Log LOG = LogFactory.getLog(ExecutionEventProcessor.class);

	private static final String START_TIMESTAMPS_MAP_FLOW_VAR_NAME = "startTimestamps";

	private static final String FLOW_LIKE_TIMING_ID = ":flowlike:";
	private static final String MSG_PROC_TIMING_ID = ":mp:";
	private static final String CALL_OUT_TIMING_ID = ":callout:";

	private static final String UNITS_MS = "milliseconds";

	private final ConcurrentMap<String, Long> fallbackStartTimestamps = new ConcurrentHashMap<String, Long>();

	/**
	 * Whether to ignore the execution of the given message processor for the purpose of logging/timing.
	 */
	public boolean ignore(MessageProcessor mp) {
		if (mp == null) return true;

		if (mp instanceof LoggerMessageProcessor) return true; // TODO may actually want to __not__ ignore the time spent on logging!
		if (mp.getClass().isAnonymousClass()) return true; // flow-ref to private flow
		if (mp instanceof SubflowInterceptingChainLifecycleWrapper) return true; // flow-ref to sub-flow
		if (mp instanceof OutboundEndpoint) return true; // outbound endpoints are captured as call-outs

		return false;
	}

	/**
	 * Returns a non-null destination String if the execution of the given message processor should be treated as a call-out (to the
	 * returned destination) rather than as a generic message processor execution step.
	 */
	public String treatAsCallOutToDestination(MessageProcessor mp) {
		if (mp == null) return null;

		if (mp instanceof AbstractDbMessageProcessor) return "JDBC Database"; // TODO return JDBC URL or similar
		if (mp instanceof DefaultHttpRequester) return ((DefaultHttpRequester) mp).getHost();

		return null;
	}

	/**
	 * Register the start of the local execution of the flow with the given name or an unknown exception strategy, where flowName is null in
	 * the latter case.
	 */
	public void startFlowLike(long timestamp, MuleEvent evt, String flowName) {
		final MuleMessage msg = evt.getMessage();
		final String name = flowLikeName(flowName);
		log(evt.getMuleContext(), msg, flowName, timestamp, "=====> starting " + name);
		startTiming(timestamp, msg, FLOW_LIKE_TIMING_ID, name);
	}

	/**
	 * Register the end of the local execution of the flow with the given name or an unknown exception strategy, where flowName is null in
	 * the latter case.
	 */
	public void endFlowLike(long timestamp, MuleEvent evt, String flowName) {
		final MuleMessage msg = evt.getMessage();
		final String name = flowLikeName(flowName);
		final long dt = endTiming(timestamp, msg, FLOW_LIKE_TIMING_ID, name);
		final Monitor mon = MonitorFactory.add(name, UNITS_MS, dt);
		log(evt.getMuleContext(), msg, flowName, timestamp, "<===== ending " + name + " | " + dt + " / " + monLogMsg(mon));
	}

	/**
	 * Register the start of the execution of the given message processor at the given path within the local execution of the flow with the
	 * given name or an exception strategy triggered from the flow with that name.
	 */
	public void startMessageProcessor(long timestamp, MuleEvent evt, String flowName, MessageProcessor mp, String processorPath) {
		final MuleMessage msg = evt.getMessage();
		log(evt.getMuleContext(), msg, flowName, timestamp, "-----> starting " + mpLogMsg(mp, processorPath));
		startTiming(timestamp, msg, MSG_PROC_TIMING_ID, processorPath);
	}

	/**
	 * Register the end of the execution of the given message processor at the given path within the local execution of the flow with the
	 * given name or an exception strategy triggered from the flow with that name.
	 */
	public void endMessageProcessor(long timestamp, MuleEvent evt, String flowName, MessageProcessor mp, String processorPath) {
		final MuleMessage msg = evt.getMessage();
		final String name = mpLogMsg(mp, processorPath);
		final long dt = endTiming(timestamp, msg, MSG_PROC_TIMING_ID, processorPath);
		final Monitor mon = MonitorFactory.add(name, UNITS_MS, dt);
		log(evt.getMuleContext(), msg, flowName, timestamp, "<----- ending " + name + " | " + dt + " / " + monLogMsg(mon));
	}

	/**
	 * Register the start of calling-out from local execution of the flow with the given name to the given destination/endpoint. If flowName
	 * is null then the call-out is made in the context of an exception strategy.
	 */
	public void startCallOut(long timestamp, MuleMessage msg, String flowName, String destination) {
		log(msg.getMuleContext(), msg, flowName, timestamp, ">>>>> calling-out to " + destination);
		startTiming(timestamp, msg, CALL_OUT_TIMING_ID, destination);
	}

	/**
	 * Register the end of calling-out from local execution of the flow with the given name to the given destination/endpoint. If flowName
	 * is null then the call-out is made in the context of an exception strategy.
	 */
	public void endCallOut(long timestamp, MuleMessage msg, String flowName, String destination) {
		final long dt = endTiming(timestamp, msg, CALL_OUT_TIMING_ID, destination);
		final Monitor mon = MonitorFactory.add(destination, UNITS_MS, dt);
		log(msg.getMuleContext(), msg, flowName, timestamp, "<<<<< returned from " + destination + " | " + dt + " / " + monLogMsg(mon));
	}

	private String flowLikeName(String flowName) {
		return flowName == null ? "exception-strategy" : flowName;
	}

	private String mpLogMsg(MessageProcessor mp, String path) {
		return (mp == null ? "processor" : mp.getClass().getSimpleName()) + " at " + path;
	}

	private String monLogMsg(Monitor mon) {
		return new StringBuilder().append(mon.getAvg()).append(" (").append(Math.round(mon.getHits())).append(")").toString();
	}

	private void log(MuleContext ctxt, MuleMessage msg, String flowName, long timestamp, String logmsg) {
		final String msgId = msg == null ? null : msg.getMessageRootId();
		if (ctxt == null) log(null, null, null, msgId, flowName, timestamp, logmsg);
		else log(ctxt.getClusterId(), ctxt.getClusterNodeId(), ctxt.getConfiguration().getId(), flowName, msgId, timestamp, logmsg);
	}

	private void log(String clusterId, Integer clusterNodeId, String app, String flowName, String messageId, long timestamp, String logmsg) {
		LOG.info(clusterId + " / " + clusterNodeId + " | " + app + " / " + flowName + " | " + messageId + " | " + timestamp + " | "
		        + logmsg);
	}

	/**
	 * Start the timing characterised by the given values, using the given timestamp (in milliseconds) as the start time.
	 */
	protected void startTiming(long startTimestamp, MuleMessage msg, String timingId, String timedObjectId) {
		final String key = startTstampsKey(msg, timingId, timedObjectId);
		final Long prevTstamp = startTstamps(msg).putIfAbsent(key, startTimestamp);
		if (prevTstamp != null) LOG.error("unexpectedly found pre-existing start timestamp for timing with key " + key
		        + " - keeping that timestamp");
	}

	/**
	 * End the timing characterised by the given values, using the given timestamp (in milliseconds) as the end time, and returning the time
	 * (in milliseconds) elapsed since the start of that timing.
	 */
	protected long endTiming(long endTimestamp, MuleMessage msg, String timingId, String timedObjectId) {
		final String key = startTstampsKey(msg, timingId, timedObjectId);
		final Long startTstamp = startTstamps(msg).remove(key);
		if (startTstamp == null) {
			// LOG.warn("found no start timestamp for timing with key " + key + " and so cannot end timing - assuming elapsed time of 0");
			return 0;
		}

		final long elapsed = endTimestamp - startTstamp;
		if (elapsed < 0) {
			LOG.error("elapsed time for timing with key " + key + " is negative - forcing it to be 0");
			return 0;
		}

		return elapsed;
	}

	/**
	 * Returns a map that associates start timestamps (in milliseconds) with a String key identifying a timing. That map is kept in a flow
	 * variable of the given message, i.e. the map exists as long as the flow is in scope.
	 */
	private ConcurrentMap<String, Long> startTstamps(MuleMessage msg) {
		synchronized (msg) { // TODO would like to avoid this but it's required for correctness
			ConcurrentMap<String, Long> map = msg.getInvocationProperty(START_TIMESTAMPS_MAP_FLOW_VAR_NAME);
			if (map == null) {
				map = new ConcurrentHashMap<String, Long>();
				try {
					msg.setInvocationProperty(START_TIMESTAMPS_MAP_FLOW_VAR_NAME, map);
				} catch (Exception e) {
					LOG.warn("performing timing in a context that doesn't allow flow variables to be set - falling-back to singleton map of start timestamps");
					return fallbackStartTimestamps;
				}
			}
			return map;
		}
	}

	/**
	 * Create a key that identifies the timing characterised by the given values in the map returned by {@link #startTstamps(MuleMessage)}.
	 */
	private String startTstampsKey(MuleMessage msg, String timingId, String timedObjectId) {
		return new StringBuilder(msg.getMessageRootId()).append(timingId).append(timedObjectId).toString();
	}
}
