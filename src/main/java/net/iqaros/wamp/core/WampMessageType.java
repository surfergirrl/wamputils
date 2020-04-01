package net.iqaros.wamp.core;

import lombok.Getter;

@Getter
public enum WampMessageType {
  HELLO(1),
  WELCOME(2),
  ABORT(3),
  GOODBYE(6),
  ERROR(8),
  PUBLISH(16),
  SUBSCRIBE(32),
  SUBSCRIBED(33),
  UNSUBSCRIBE(34),
  UNSUBSCRIBED(35),
  EVENT(36),
  RPC_CALL(48),
  RPC_RESULT(50),
  REGISTER(64),
  REGISTERED(65),
  UNREGISTER(66),
  UNREGISTERED(67),
  RPC_INVOCATION(68),
  INTERRUPT(69),
  RPC_YIELD(70),
  UNKNOWN(404);
  
  private final int messageNumber;

  public String getMessageName() {
    return this.name();
  }
  
  WampMessageType(int messageNumber) {
      this.messageNumber = messageNumber;
  }

  public static WampMessageType fromMessageNumber(Object messageNumber) {
    int messageNr = Integer.parseInt((String) messageNumber);
    WampMessageType result = WampMessageType.UNKNOWN;
    for (WampMessageType ee : WampMessageType.values()) {
        if (ee.getMessageNumber() == messageNr) {
            result = ee;
            break;
        }
    }
    return result;
  }
}
