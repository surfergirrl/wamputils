package net.iqaros.wamp.core;

@FunctionalInterface
public interface OnUnSubscribeListener {
	void onUnSubscribed(Long subscriptionId);
}
