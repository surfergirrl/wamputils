package net.iqaros.util;

import static argo.jdom.JsonNodeBuilders.anObjectBuilder;
import static argo.jdom.JsonNodeFactories.array;
import static argo.jdom.JsonNodeFactories.field;
import static argo.jdom.JsonNodeFactories.object;
import static argo.jdom.JsonNodeFactories.string;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.comhem.lan.utils.argo.ArgoUtils;

//import com.comhem.lan.utils.argo.ArgoUtils;

import argo.jdom.JsonField;
import argo.jdom.JsonNode;
import argo.jdom.JsonObjectNodeBuilder;
import argo.jdom.JsonRootNode;
import argo.saj.InvalidSyntaxException;

public class ArgoJsonUtil {

	public static JsonNode getArray(List<JsonNode> nodes) {
		return array(nodes);
	}

	public static JsonNode error(String errorMessage) {
		return object(field("message", string(errorMessage)));
	}

	public static JsonNode error(Throwable ex) {
		return error(ex.getMessage() == null ? "" : ex.getMessage());
	}

	public static String format(JsonNode jsonNode) {
		return ArgoUtils.format((JsonRootNode) jsonNode);
	}

	public static JsonNode parse(String jsonString) {
		try {
			return ArgoUtils.parse(jsonString);
		} catch (InvalidSyntaxException e) {
			e.printStackTrace();
			return error("Invalid argo json: "+jsonString);
		}
	}

	public static JsonNode getJsonNode(String key, String value) {
		return object(field(string(key),string(value)));
	}

	public JsonObjectNodeBuilder getJsonNodeBuilder() {
		return anObjectBuilder();
	}

	public static JsonRootNode createDetails(Map<String, String> details) {
		List<JsonField> fields = details.keySet().stream()
				.map(key -> {return getField(key,details.get(key));})
				.collect(Collectors.toList());
		JsonNode node = object(fields.stream().collect(Collectors.toList()));
		return (JsonRootNode) node;	
	}

	public static JsonRootNode createDetails(String mac, String server, Throwable ex) {
		Map<String, String> details = new HashMap<>();
		details.put("mac",mac);
		details.put("server",server);
		if (ex != null) {
			details.put("exceptionType", ex.getClass().getSimpleName());
			details.put("message", ex.getMessage());
		}
		return createDetails(details); 
	}


	public static JsonRootNode createDetails(String mac, String server, String method, String status) {
		Map<String,String> info = new HashMap<String,String>();
		info.put("method", method);
		info.put("mac", mac);
		info.put("server", server);
		info.put("statusCode", status);
		return createDetails(info); 
	}

	private static JsonField getField(String key, String value) {
		return field(key,string(value));
	}

	public static JsonRootNode getJsonArray(List<JsonNode> list) {
		return array(list);
	}

	public static JsonNode emptyList() {
		return parse("[]");
	}


}
