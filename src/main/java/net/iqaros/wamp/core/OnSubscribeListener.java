package net.iqaros.wamp.core;


@FunctionalInterface
public interface OnSubscribeListener {
	void onSubscribed(Long requestId, Long subscriptionId);
}
