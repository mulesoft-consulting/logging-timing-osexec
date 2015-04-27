package com.mulesoft.services.loggingtiming;

import org.mule.api.MuleMessage;
import org.mule.api.context.notification.EndpointMessageNotificationListener;
import org.mule.context.notification.EndpointMessageNotification;

/**
 * Listens on notifications about outgoing communication with an external endpoint starting/ending.
 * 
 * @author gerald.loeffler@mulesoft.com
 */
public class EndpointMsgNotifListener implements EndpointMessageNotificationListener<EndpointMessageNotification> {

	private final ExecutionEventProcessor proc;

	public EndpointMsgNotifListener(ExecutionEventProcessor proc) {
		this.proc = proc;
	}

	@Override
	public void onNotification(EndpointMessageNotification n) {
		final long tstamp = n.getTimestamp();
		final MuleMessage msg = n.getSource();
		final String flowName = n.getResourceIdentifier();
		final String destination = n.getEndpoint();

		final int act = n.getAction();
		if (act == EndpointMessageNotification.MESSAGE_DISPATCH_BEGIN || act == EndpointMessageNotification.MESSAGE_REQUEST_BEGIN
		        || act == EndpointMessageNotification.MESSAGE_SEND_BEGIN) proc.startCallOut(tstamp, msg, flowName, destination);
		else if (act == EndpointMessageNotification.MESSAGE_DISPATCH_END || act == EndpointMessageNotification.MESSAGE_REQUEST_END
		        || act == EndpointMessageNotification.MESSAGE_SEND_END) proc.endCallOut(tstamp, msg, flowName, destination);
	}
}
