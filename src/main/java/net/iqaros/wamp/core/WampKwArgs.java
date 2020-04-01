package net.iqaros.wamp.core;

import java.util.HashMap;
import java.util.Map;

import net.iqaros.util.ArgoJsonUtil;

import argo.jdom.JsonNode;
import lombok.Getter;

@Getter
public class WampKwArgs {

	JsonNode jsonNode;
	String jsonString;
	Map<String,Object> kwArgs;
	
	public WampKwArgs() {
		//defaulting to empty object
		this("{}");
	}
	
	public WampKwArgs(String str) {
		this.jsonString = str;
		this.kwArgs = new HashMap<String,Object>();
		this.jsonNode = ArgoJsonUtil.parse(str);
		if (jsonNode != null && jsonNode.isObjectNode()) {
			jsonNode.getObjectNode().entrySet().stream().forEach(entry -> {
				String key = entry.getKey().getStringValue();
				System.out.println("k v" + " " + key + "=" + entry.getValue() );
				kwArgs.put(key, entry.getValue());//TODO: convert json->str ArgoJsonUtil.format 
			});				
		}			
	}
	/*kwArgsJson.getFields().keySet().forEach(k -> {
			//kwArgs.put(k.getStringValue(),ArgoJsonUtil.format(details.getFields().get(k)));
			String key = k.getStringValue();
			JsonNode v = kwArgsJson.getNode(key);
			if (v instanceof JsonStringNode) {
				kwArgs.put(key, v.getText());
			} else {
				kwArgs.put(key,"NYI: "+ v.getClass().getName());
			}
			//String vstr = ArgoJsonUtil.format(v); //JsonStringNode .getText
		});*/
	@Override
	public String toString() {
		return jsonString;
	}
	
	public static void main(String... args) {
//		WampKwArgs w = new WampKwArgs(); //"{\"aaa\":345}");
		WampKwArgs w = new WampKwArgs("{\"aaa\":345,\"nnn\":\"mmm\"}");
		System.out.println(w.toString());
		//System.out.println("kwArgs=" + w.getKwArgs());
		//System.out.println("json=" + w.getJsonNode());	
	}
}
