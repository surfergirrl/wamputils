package net.iqaros.wamp.core;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.iqaros.util.ArgoJsonUtil;

import argo.jdom.JsonNode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class WampMessage {

	private String message;
	private WampMessageType messageType;
	
	private Long requestId;
	private WampArgs args;
	private WampKwArgs kwArgs;

	private JsonNode jsonList;
	private List<Object> wmsg = null;

	public WampMessage(WampMessageType messageType, Long requestId, WampArgs args, WampKwArgs kwArgs) {
		this.messageType = messageType;
		this.requestId = requestId;
		this.args = args;
		this.kwArgs = kwArgs;
		//TODO: put message together properly and test this
		this.message = "[" + messageType.getMessageNumber() + "," +String.valueOf(requestId)+ "," + args.toString() + "," + kwArgs.toString() +"]";
		this.wmsg = Arrays.asList(messageType.getMessageNumber(),requestId, args.toString(),kwArgs.toString());
	}
	
	public WampMessage(String entireMessage) {
		this.message = entireMessage;
		try {
			jsonList = ArgoJsonUtil.parse(entireMessage.toString());

			if (jsonList.isArrayNode()) {
				wmsg = jsonList.getArrayNode().stream().map(item -> {
					if (item.isStringValue()) {
						return item.getStringValue();
					} else if (item.isNumberValue()) {
						return item.getNumberValue();
					} else if (item.isObjectNode() || item.isArrayNode()) {
						return ArgoJsonUtil.format(item);
					} else  {
						System.out.println("unknown type:" + item.getClass().getName() + " " + item);
						return item;
					}
				}).collect(Collectors.toList());
			} else {
				System.out.println("Error processing incoming message, not json array: " + entireMessage.toString());
				return;
			}

			if (wmsg == null) { // || wmsg.size() < 3) {
				System.out.println("Error processing incoming message, not enough info: " + wmsg);
				return;
			}

			messageType = WampMessageType.fromMessageNumber(wmsg.get(0));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int size() {
		return wmsg.size();
	}
}
