package com.mulesoft.services.loggingtiming;

import org.mule.api.MuleEvent;
import org.mule.api.context.notification.PipelineMessageNotificationListener;
import org.mule.context.notification.PipelineMessageNotification;

/**
 * Listens on notifications about flow execution starting/ending.
 * 
 * @author gerald.loeffler@mulesoft.com
 */
public class PipelineMsgNotifListener implements PipelineMessageNotificationListener<PipelineMessageNotification> {

	private final ExecutionEventProcessor proc;

	public PipelineMsgNotifListener(ExecutionEventProcessor proc) {
		this.proc = proc;
	}

	@Override
	public void onNotification(PipelineMessageNotification n) {
		final long tstamp = n.getTimestamp();
		final MuleEvent evt = (MuleEvent) n.getSource();
		final String flowName = n.getResourceIdentifier();

		final int act = n.getAction();
		if (act == PipelineMessageNotification.PROCESS_START) proc.startFlowLike(tstamp, evt, flowName);
		else if (act == PipelineMessageNotification.PROCESS_COMPLETE) proc.endFlowLike(tstamp, evt, flowName);
	}
}
