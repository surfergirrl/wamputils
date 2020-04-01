package net.iqaros.wamp.core;

import argo.jdom.JsonNode;

@FunctionalInterface
public interface OnWampOpenListener {

  void onWampOpen(Long sessionId, JsonNode details);
}
