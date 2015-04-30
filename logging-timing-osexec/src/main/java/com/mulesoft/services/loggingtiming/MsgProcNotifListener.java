package com.mulesoft.services.loggingtiming;

import org.mule.api.MuleEvent;
import org.mule.api.context.notification.MessageProcessorNotificationListener;
import org.mule.api.processor.MessageProcessor;
import org.mule.context.notification.MessageProcessorNotification;

/**
 * Listens on notifications about message processor execution starting/ending.
 * 
 * @author gerald.loeffler@mulesoft.com
 */
public class MsgProcNotifListener implements MessageProcessorNotificationListener<MessageProcessorNotification> {

	private final ExecutionEventProcessor proc;

	public MsgProcNotifListener(ExecutionEventProcessor proc) {
		this.proc = proc;
	}

	@Override
	public void onNotification(MessageProcessorNotification n) {
		if (!proc.processCallOuts() && !proc.processMessageProcessors()) return;

		final MessageProcessor mp = n.getProcessor();
		if (proc.ignoreMessageProcessor(mp)) return;

		final long tstamp = n.getTimestamp();
		final MuleEvent evt = n.getSource();
		final String flowName = n.getResourceIdentifier();
		final String mpPath = n.getProcessorPath();

		final String destination = proc.treatAsCallOutToDestination(mp);
		final boolean treatAsCallOut = destination != null;

		final int act = n.getAction();
		if (act == MessageProcessorNotification.MESSAGE_PROCESSOR_PRE_INVOKE) {
			if (treatAsCallOut && proc.processCallOuts()) proc.startCallOut(tstamp, evt.getMessage(), flowName, destination);
			else if (proc.processMessageProcessors()) proc.startMessageProcessor(tstamp, evt, flowName, mp, mpPath);
		} else if (act == MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE) {
			if (treatAsCallOut && proc.processCallOuts()) proc.endCallOut(tstamp, evt.getMessage(), flowName, destination);
			else if (proc.processMessageProcessors()) proc.endMessageProcessor(tstamp, evt, flowName, mp, mpPath);
		}
	}
}
