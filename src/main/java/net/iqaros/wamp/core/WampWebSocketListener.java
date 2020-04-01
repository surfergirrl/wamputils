package net.iqaros.wamp.core;

import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

import argo.jdom.JsonNode;
import net.iqaros.util.ArgoJsonUtil;
import net.iqaros.util.LogstashJsonLogger;

public class WampWebSocketListener implements WebSocket.Listener {
  private LogstashJsonLogger LOGGER = LogstashJsonLogger.of();

  private Long sessionId = Long.valueOf(0);

  private StringBuilder text;
  private StringBuilder parts;
  private CompletableFuture<?> accumulatedMessage;

  private List<OnWampOpenListener> onWampOpenListeners;
  private List<OnWampCloseListener> onWampCloseListeners;
  private List<OnSubscribeListener> onSubscribeListeners;
  private List<OnUnSubscribeListener> onUnSubscribeListeners;
  private List<OnWampEventListener> onEventListeners;
  private List<OnRpcResultListener> onRpcResultListeners;
  private List<OnMessageListener> onMessageListeners;
  private List<OnRpcInvocationListener> onRpcInvocationListeners;
  private List<OnRpcRegisterListener> onRpcRegisterListeners;

  public WampWebSocketListener() {
    text = new StringBuilder();
    parts = new StringBuilder();
    accumulatedMessage = new CompletableFuture<>();

    onWampOpenListeners = new ArrayList<>();
    onWampCloseListeners = new ArrayList<>();
    onSubscribeListeners = new ArrayList<>();
    onUnSubscribeListeners = new ArrayList<>();
    onEventListeners = new ArrayList<>();
    onRpcResultListeners = new ArrayList<>();
    onMessageListeners = new ArrayList<>();
    onRpcInvocationListeners = new ArrayList<>();
    onRpcRegisterListeners = new ArrayList<>();
  }

  public CompletionStage<?> onText(WebSocket webSocket, CharSequence message, boolean last) {
    parts.append(message);
    webSocket.request(1);
    if (last) {
      processCompleteTextMessage(parts);
      parts = new StringBuilder();
      accumulatedMessage.complete(null);
      CompletionStage<?> cf = accumulatedMessage;
      accumulatedMessage = new CompletableFuture<>();
      return cf;
    }
    return accumulatedMessage;
  }

  void processCompleteTextMessage(StringBuilder str) {
    WampMessage wm = new WampMessage(str.toString());
    LOGGER.log(Level.INFO, "RAW message from WR: " + wm.getMessage());
    String kwArgsString = "{}";
    String argsString = "[]";

    try {
      switch (wm.getMessageType()) {
      case WELCOME: // welcome
        sessionId = Long.valueOf((String) wm.getWmsg().get(1));
        LOGGER.log(Level.INFO, "Welcome to the wamp router! sessionId=" + sessionId);
        JsonNode details = ArgoJsonUtil.parse((String) wm.getWmsg().get(2));
        onWampOpenListeners.stream().forEach(l -> l.onWampOpen(sessionId, details));
        break;
      case ERROR:
        LOGGER.log(Level.INFO, "Error");
        printArgs(wm.getMessageType(), wm.getWmsg());
        break;
      case ABORT:
      case GOODBYE:
        LOGGER.log(Level.INFO, "Wamp router sent goodbye/abort! disconnecting...");
        printArgs(wm.getMessageType(), wm.getWmsg());
        JsonNode options1 = ArgoJsonUtil.parse((String) wm.getWmsg().get(1));
        String msg = (String) wm.getWmsg().get(2);
        onWampCloseListeners.stream().forEach(l -> l.onWampClose(options1, msg));
        break;
      case SUBSCRIBED:
        Long requestId = Long.valueOf((String) wm.getWmsg().get(1));
        Long subscriptionId = Long.valueOf((String) wm.getWmsg().get(2));
        onSubscribeListeners.stream().forEach(l -> {
          l.onSubscribed(requestId, subscriptionId);
        });
        break;
      case REGISTERED:
        LOGGER.log(Level.INFO, "Rpc method registered: " + str);
        // printArgs(wm.wampMessageType, wmsg);//[65,4,61]
        Long reqId = Long.valueOf((String) wm.getWmsg().get(1));
        Long rpcMethodId = Long.valueOf((String) wm.getWmsg().get(2));
        // TODO: rpcRegistration.complete(subscriptionId)
        onRpcRegisterListeners.stream().forEach(l -> {
          l.onRegistered(reqId, rpcMethodId);
        });
        break;
      case UNREGISTERED:
        LOGGER.log(Level.INFO, "Rpc method unRegistered: " + str);
        onMessageListeners.stream().forEach(l -> {
          l.onMessage(wm.getMessageType(), Arrays.asList("UNREGISTERED: " + str));
        });
        break;
      case EVENT:
        Long request = Long.valueOf((String) wm.getWmsg().get(1));
        String detailsEvent = (String) wm.getWmsg().get(4);
        LOGGER.log(Level.INFO, "Event: " + request + ", details: " + detailsEvent);
        onEventListeners.stream().forEach(l -> {
          l.onWampEvent(request, detailsEvent);
        });
        break;
      case RPC_RESULT: // result
        Long rid = Long.valueOf((String) wm.getWmsg().get(1));
        String options = (String) wm.getWmsg().get(2);
        if (wm.getWmsg().size() > 3) {
          argsString = (String) wm.getWmsg().get(3);
        }
        if (wm.getWmsg().size() > 4) {
          kwArgsString = (String) wm.getWmsg().get(4);
        }
        WampArgs args = new WampArgs(argsString);
        WampKwArgs kwArgs = new WampKwArgs(kwArgsString); // always empty?
        onRpcResultListeners.stream().forEach(l -> {
          l.onRpcResult(rid, options, args, kwArgs);
        });
        break;
      case RPC_INVOCATION:
        LOGGER.log(Level.INFO, "Invocation from client to Buffy RPC Method");
        Long clientRequestId = Long.valueOf((String) wm.getWmsg().get(1));
        Long rpcMethodId1 = Long.valueOf((String) wm.getWmsg().get(2));
        String options3 = (String) wm.getWmsg().get(3);
        if (wm.getWmsg().size() > 5) {
          kwArgsString = (String) wm.getWmsg().get(5);
        }
        if (wm.getWmsg().size() > 4) {
          argsString = (String) wm.getWmsg().get(4);
        }
        WampArgs argsS = new WampArgs(argsString);
        WampKwArgs kwArgsS = new WampKwArgs(kwArgsString);
        onRpcInvocationListeners.stream().forEach(l -> {
          l.onRpcInvocation(clientRequestId, rpcMethodId1, options3, argsS, kwArgsS);
        });
        break;
      case UNSUBSCRIBED:
        Long sid = Long.valueOf((String) wm.getWmsg().get(1));
        LOGGER.log(Level.INFO, "unsubscribed to " + sid);
        // printArgs(wm.getMessageType(), wm.getWmsg());
        // Long sid = Long.valueOf((String) wm.getWmsg().get(2));
        onUnSubscribeListeners.stream().forEach(l -> {
          l.onUnSubscribed(sid);
        });
        break;
      case UNKNOWN: // new message type
        LOGGER.log(Level.WARNING, "Warning: unhandled message type: " + wm.getMessageType());
        printArgs(wm.getMessageType(), wm.getWmsg());
        break;
      default:
        LOGGER.log(Level.WARNING,
            "unHandled messageType received:" + wm.getMessageType() + ", wmsg.size=" + wm.size());
        printArgs(wm.getMessageType(), wm.getWmsg());
        onMessageListeners.stream().forEach(l -> {
          l.onMessage(wm.getMessageType(), Arrays.asList("NYI"));
        });
        break;
      }
      ;

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Exception processing message from Wamp Router (WR): " + e.getMessage());
      LOGGER.log(Level.INFO, "Problematic message is : " + wm.getMessage());
      e.printStackTrace();
    }

  }

  private void printArgs(WampMessageType mt, List<Object> wmsg) {
    try {
      LOGGER.log(Level.INFO,"***" + mt + ", " + wmsg.size() + " args");
      if (wmsg.size() > 3) {
        for (int i = 0; i < wmsg.size(); i++) {
          LOGGER.log(Level.INFO, "arg[" + i + "]=" + wmsg.get(i));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void addMessageListener(OnMessageListener listener) {
    this.onMessageListeners.add(listener);
  }

  public void addWampEventListener(OnWampEventListener listener) {
    this.onEventListeners.add(listener);
  }

  public void addRpcResultListener(OnRpcResultListener listener) {
    this.onRpcResultListeners.add(listener);
  }

  public void addRpcInvocationListener(OnRpcInvocationListener listener) {
    this.onRpcInvocationListeners.add(listener);
  }

  public void addRpcRegisterListener(OnRpcRegisterListener listener) {
    this.onRpcRegisterListeners.add(listener);
  }

  public void addEventListeners(OnWampOpenListener onOpenListener, OnWampCloseListener onCloseListener,OnSubscribeListener onSubScribeListener) {
    this.onWampOpenListeners.add(onOpenListener);
    this.onWampCloseListeners.add(onCloseListener);
    this.onSubscribeListeners.add(onSubScribeListener);
  }

  public void removeEventListeners() {
    this.onWampOpenListeners = null;
    this.onWampCloseListeners = null;
    this.onSubscribeListeners = null;
    this.onEventListeners = null;
    this.onRpcResultListeners = null;
    this.onMessageListeners = null;
    this.onRpcRegisterListeners = null;
    this.onRpcInvocationListeners = null;
  }

}
