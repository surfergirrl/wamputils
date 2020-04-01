package net.iqaros.wamp.core;

import java.util.List;


@FunctionalInterface
public interface OnMessageListener {

	void onMessage(WampMessageType wampMessageType, List<Object> wmsg);
}
