package net.iqaros.wamp.core;

@FunctionalInterface
public interface OnRpcInvocationListener {

	void onRpcInvocation(Long invocationRequestId, Long rpcMethodId, String options, WampArgs args, WampKwArgs kwArgs);
}
