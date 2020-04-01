package net.iqaros.wamp.core;


@FunctionalInterface
public interface OnRpcRegisterListener {
	void onRegistered(Long methodId, Long arg);
}
