package net.iqaros.wamp.core;

import argo.jdom.JsonNode;

@FunctionalInterface
public interface OnWampCloseListener {
  void onWampClose(JsonNode options, String msg);
}
