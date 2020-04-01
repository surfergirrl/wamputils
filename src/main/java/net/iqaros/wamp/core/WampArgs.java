package net.iqaros.wamp.core;
	import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.iqaros.util.ArgoJsonUtil;

import argo.jdom.JsonNode;
import lombok.Getter;

@Getter
	public class WampArgs {

		JsonNode jsonNode;
		String jsonString;
		List<Object> args;
		
		public WampArgs() {
			//Defaulting to empty list
			this("[]");
		}
		public WampArgs(String str) {
			this.jsonString = str;
			args = new ArrayList<>();
			jsonNode = ArgoJsonUtil.parse(str);
			if (jsonNode != null && jsonNode.isArrayNode()) {
				args = jsonNode.getArrayNode().stream().map(node -> {
					if (node.isNumberValue()) {
						return node.getNumberValue();
					 /* else if (node.isObjectValue()) {
					return node.getStringValue();
					}*/
					} else if (node.isStringValue()) {
						return node.getStringValue();
					}
					//TODO: handle lists and recursion
					return node;//todo: convert to string/number/list etc
				}).collect(Collectors.toList());				
			}			
		}
		
		@Override
		public String toString() {
			return jsonString;
		}
		
		public static void main(String... args) {
			WampArgs w = new WampArgs(); //"[\"aaa\",345,\"{}\"]") ;
			System.out.println(w.toString());
			//System.out.println("args=" + w.getArgs());
			//System.out.println("json=" + w.getJsonNode());			
		}
	}

