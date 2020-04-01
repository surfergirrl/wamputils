package net.iqaros.wamp.core;

@FunctionalInterface
public interface OnWampEventListener {

  void onWampEvent(Long metaSubscriptionId, String detailsString);
}
