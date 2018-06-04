package dakaraphi.devtools.tracing;

import dakaraphi.devtools.tracing.config.Tracer;
import dakaraphi.devtools.tracing.config.Tracer.VariableCondition;
import dakaraphi.devtools.tracing.config.TracingConfig.LogConfig;
import dakaraphi.devtools.tracing.logger.TraceLogger;

public class ApplicationHooks {
	// http://jboss-javassist.github.io/javassist/tutorial/tutorial2.html#before
    public static void logMethodParameters(final int tracerId, final String classname, final String methodname, final Object[] parameters) {
		Tracer tracerConfig = TracingAgent.tracingConfig.tracers.get(tracerId);
		if (!shouldLog(tracerConfig, parameters)) return;

		LogConfig logConfig = TracingAgent.tracingConfig.logConfig;
		StringBuilder builder = new StringBuilder();
		builder.append(classname + ": "+methodname);
		if (logConfig.multiLine) builder.append('\n');
		int count = 0;

		if (parameters != null) {
			for ( final Object parameter : parameters ) {
				if ( parameter instanceof Object[]) {
					int listIndex = 0;
					for ( final Object nestedParameter : (Object[])parameter) {
						builder.append(" :list-value "+listIndex+++": " + nestedParameter);
					}
				} else {
					builder.append(" :value "+count+++": " + parameter);
				}
				if (logConfig.multiLine) builder.append('\n');
			}
		}

		writeLog(tracerConfig, builder.toString());
	}
	
	public static void writeLog(Tracer tracerConfig, String text) {
		LogConfig logConfig = TracingAgent.tracingConfig.logConfig;
		StringBuilder builder = new StringBuilder();
		String time = new java.text.SimpleDateFormat("hh:mm:ss,SSS").format(new java.util.Date());
		builder.append(time);
		if (tracerConfig.name != null) builder.append(" [" +tracerConfig.name+ "]");
		builder.append(" thread:[");
		if (logConfig.threadId) builder.append(Thread.currentThread().getId());
		if (logConfig.threadName) builder.append(Thread.currentThread().getName());
		builder.append("]: ");	
		builder.append(text);

		if (tracerConfig.includeStackTrace) appendStackFrames(builder);

		TraceLogger.trace(builder.toString());
	}

	private static void appendStackFrames(StringBuilder content) {
		StackTraceElement[] stackFrames = Thread.currentThread().getStackTrace();
		for (StackTraceElement stackFrame : stackFrames) {
			content.append(" " + formatStackFrame(stackFrame));
			content.append('\n');
		}
	}

	private static String formatStackFrame(StackTraceElement stackFrame) {
		return stackFrame.getClassName() + "." + stackFrame.getMethodName() + "(" + stackFrame.getFileName() + ":" + stackFrame.getLineNumber() + ")";
	}

	private static boolean shouldLog(Tracer tracerConfig, Object[] parameters) {
		boolean shouldLog = true;
		if (tracerConfig.logWhen != null) {

			// check variable conditions
			for (VariableCondition variable : tracerConfig.logWhen.variableValues) {
				Object parameterValue = parameters[variable.index];
				if (parameterValue == null || !variable.valueRegex.matcher(parameterValue.toString()).matches()) {
					return false;
				}
			}

			// check thread condition
			if (tracerConfig.logWhen.threadNameRegex != null && !tracerConfig.logWhen.threadNameRegex.matcher(Thread.currentThread().getName()).matches()) {
				return false;
			}

			// check stack frame condition
			if (tracerConfig.logWhen.stackFramesRegex != null) {
				Throwable throwable = new Throwable();
				boolean foundInStackFrames = false;
				for (StackTraceElement stackFrame : throwable.getStackTrace()) {
					String frameDescription = formatStackFrame(stackFrame);
					if (tracerConfig.logWhen.stackFramesRegex.matcher(frameDescription).matches()) {
						foundInStackFrames = true;
						break;
					}
				}
				shouldLog = foundInStackFrames;
			}
		}
		return shouldLog;
	}
}