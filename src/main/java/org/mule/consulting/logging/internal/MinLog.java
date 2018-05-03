package org.mule.consulting.logging.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import java.util.LinkedHashMap;
import java.util.UUID;

import org.mule.runtime.api.util.MultiMap;
import org.mule.runtime.extension.api.annotation.dsl.xml.ParameterDsl;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.runtime.process.CompletionCallback;
import org.mule.runtime.extension.api.runtime.route.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * This class is a container for operations, every public method in this class will be taken as an extension operation.
 */
public class MinLog {
	private final Logger LOGGER = LoggerFactory.getLogger(MinLog.class);

  /**
   * Example of an operation that uses the configuration and a connection instance to perform some action.
   */
  @MediaType(value = ANY, strict = false)
  public String retrieveInfo(@Config MinimalloggingConfiguration configuration, @Connection MinimalloggingConnection connection){
    return "Using Configuration [" + configuration.getConfigId() + "] with Connection id [" + connection.getId() + "]";
  }

	/**
	 * Generate a transaction id if required, otherwise return the current
	 * transaction id.
	 */
	@MediaType(value = ANY, strict = false)
	public LinkedHashMap<String, String> setTransactionProperties(@Optional MultiMap headers) {
		LinkedHashMap<String, String> retvalue = new LinkedHashMap<String, String>();

		if (headers != null) {
			if (headers.get("client_id") != null) {
				retvalue.put("client_id", (String) headers.get("client_id"));
			}
			if (headers.get("x-transaction-id") != null) {
				retvalue.put("x-transaction-id", (String) headers.get("x-transaction-id"));
			} else {
				retvalue.put("x-transaction-id", UUID.randomUUID().toString());
				logMessage("Generated x-transaction-id", retvalue);
			}
		}
		return retvalue;
	}

	public void timed(@Optional(defaultValue="#[{}]") @ParameterDsl(allowInlineDefinition=false) LinkedHashMap<String, String> transactionProperties, Chain operations,
			CompletionCallback<Object, Object> callback) {
		
		long startTime = System.currentTimeMillis();

		LinkedHashMap<String, String> tempMap = new LinkedHashMap<String, String>();
		if (transactionProperties != null) {
			for (String item : transactionProperties.keySet()) {
				tempMap.put(item, transactionProperties.get(item));
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("enter ");
		logMessage(sb.toString(), tempMap);

		operations.process(result -> {
			long elapsedTime = System.currentTimeMillis() - startTime;
			tempMap.put("elapsedMS", Long.toString(elapsedTime));
			StringBuilder sbsuccess = new StringBuilder();
			sbsuccess.append("exit ");
			logMessage(sbsuccess.toString(), tempMap);
			callback.success(result);
		}, (error, previous) -> {
			long elapsedTime = System.currentTimeMillis() - startTime;
			tempMap.put("elapsedMS", Long.toString(elapsedTime));
			StringBuilder sberror = new StringBuilder();
			sberror.append("exit with error ").append(error.getMessage());
			logMessage(sberror.toString(), tempMap);
			callback.error(error);
		});
	}
	
	private void logMessage(String msg, LinkedHashMap <String, String> transactionProperties) {
		ObjectMapper mapper = new ObjectMapper();
		String payload = null;
		try {
			payload = mapper.writeValueAsString(transactionProperties);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		StringBuilder sb = new StringBuilder();
		sb.append(msg).append(" ").append(payload);
		LOGGER.info(sb.toString());
	}
}
