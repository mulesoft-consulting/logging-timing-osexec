package com.mulesoft.services.loggingtiming;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mule.api.MuleContext;
import org.mule.api.context.MuleContextAware;
import org.mule.context.notification.NotificationException;

/**
 * Define an eagerly initialised singleton-bean of this class in one Mule XML config file per app and it will log and time important
 * activities in that app.
 * 
 * <pre>
 * <spring:bean class="com.dlg.emerald.muleesb.notification.loggingtiming.LoggingAndTimingNotificationsListenerSetup" lazy-init="false" scope="singleton" />
 * </pre>
 * 
 * @author gerald.loeffler@mulesoft.com
 */
public class LoggingAndTimingNotificationsListenerSetup implements MuleContextAware {

	protected static final Log LOG = LogFactory.getLog(LoggingAndTimingNotificationsListenerSetup.class);

	// may convert to setter so that can be injected by Spring
	private final ExecutionEventProcessor proc = new ExecutionEventProcessor();

	@Override
	public void setMuleContext(MuleContext ctxt) {
		try {
			registerListeners(ctxt);
		} catch (NotificationException e) {
			throw new RuntimeException(e);
		}
	}

	protected void registerListeners(MuleContext ctxt) throws NotificationException {
		ctxt.registerListener(new EndpointMsgNotifListener(proc));
		ctxt.registerListener(new ExceptionStratNotifListener(proc));
		ctxt.registerListener(new MsgProcNotifListener(proc));
		ctxt.registerListener(new PipelineMsgNotifListener(proc));
	}
}
