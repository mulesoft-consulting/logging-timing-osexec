package com.mulesoft.services.osexec;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mule.DefaultMuleMessage;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.processor.MessageProcessor;

/**
 * A {@link MessageProcessor} that retrieves the payload as a {@link String} and executes it as an OS-level command using
 * {@link Runtime#exec(String)}. The output of that command is then set as the payload of the returned message.
 * 
 * @author gerald.loeffler@mulesoft.com
 */
public class OSExec implements MessageProcessor {

	private static final Log LOG = LogFactory.getLog(OSExec.class);

	@Override
	public MuleEvent process(MuleEvent event) throws MuleException {
		final String cmd = event.getMessage().getPayload(String.class);
		LOG.debug("executing command '" + cmd + "'");

		final List<String> outputLines = execN(cmd);
		final String output = StringUtils.join(outputLines, "\r\n");
		LOG.debug("returning output '" + output + "' from execution of command '" + cmd + "'");

		event.setMessage(new DefaultMuleMessage(output, event.getMuleContext()));
		return event;
	}

	@SuppressWarnings("unchecked")
	private List<String> execN(final String cmd) {
		try {
			return IOUtils.readLines(execAndWait(cmd));
		} catch (Exception e) {
			LOG.error(e.getMessage());
			return Collections.emptyList();
		}
	}

	private InputStream execAndWait(final String cmd) throws Exception {
		final Process process = Runtime.getRuntime().exec(cmd);
		final InputStream is = process.getInputStream();
		process.waitFor();
		return is;
	}
}
