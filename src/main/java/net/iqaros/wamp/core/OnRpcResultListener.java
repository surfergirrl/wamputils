package net.iqaros.wamp.core;

@FunctionalInterface
public interface OnRpcResultListener {
	void onRpcResult(Long requestId, String options, WampArgs args, WampKwArgs kwArgs);
}
