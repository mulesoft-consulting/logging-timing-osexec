package com.mulesoft.services.loggingtiming;

import org.mule.api.MuleEvent;
import org.mule.api.context.notification.ExceptionStrategyNotificationListener;
import org.mule.context.notification.ExceptionStrategyNotification;

/**
 * Listens on notifications about exception strategy execution starting/ending.
 * 
 * @author gerald.loeffler@mulesoft.com
 */
public class ExceptionStratNotifListener implements ExceptionStrategyNotificationListener<ExceptionStrategyNotification> {

	private final ExecutionEventProcessor proc;

	public ExceptionStratNotifListener(ExecutionEventProcessor proc) {
		this.proc = proc;
	}

	@Override
	public void onNotification(ExceptionStrategyNotification n) {
		if (!proc.processFlowLikes()) return;

		final long tstamp = n.getTimestamp();
		final MuleEvent evt = (MuleEvent) n.getSource();

		final int act = n.getAction();
		if (act == ExceptionStrategyNotification.PROCESS_START) proc.startFlowLike(tstamp, evt, null);
		else if (act == ExceptionStrategyNotification.PROCESS_END) proc.endFlowLike(tstamp, evt, null);
	}
}
