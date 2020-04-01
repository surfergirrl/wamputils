package net.iqaros.wamp.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;


import argo.jdom.JsonNode;
import io.crossbar.autobahn.wamp.types.Publication;
import io.crossbar.autobahn.wamp.utils.IDGenerator;
import net.iqaros.util.ArgoJsonUtil;
import net.iqaros.util.LogstashJsonLogger;

public class WampWebSocketClient implements OnWampOpenListener, OnWampCloseListener, OnSubscribeListener, OnRpcResultListener {
  private LogstashJsonLogger LOGGER = LogstashJsonLogger.of();

  private ExecutorService mExecutor;

  private String wampStatus = "";
  private WampWebSocketListener listener;

  private CompletableFuture<Long> sessionId;
  private CompletableFuture<WebSocket> webSocket;

  private IDGenerator idGenerator;

  // hashmap of requestId and subscriptionId:
  private Map<Long, CompletableFuture<Long>> subscriptions = new HashMap<>();
  private Map<Long, String> subscriptionTopics = new HashMap<>();
  // unsubscribes
  private Map<Long, CompletableFuture<Long>> unSubscriptions = new HashMap<>();
  // rpcCalls
  private Map<Long, CompletableFuture<WampMessage>> rpcCallFutures = new HashMap<>();
  private Map<Long, String> rpcCallNames = new HashMap<>();

  //goodbyes when quitting:
  private CompletableFuture<String> waitforWRGoodbye = new CompletableFuture<String>();

  // constants:
  private final String CR = "\n";

  public WampWebSocketClient() {
    listener = new WampWebSocketListener();
    //TODO: consider other types of executors...
    mExecutor = Executors.newWorkStealingPool();

    // sessionId will be completed when wamp router is connected and hello-welcome
    // messages have been exchanged:
    sessionId = new CompletableFuture<>();
    webSocket = new CompletableFuture<>();

    idGenerator = new IDGenerator();
  }

  public JsonNode getStatus() {
    // json string
    StringBuilder result = new StringBuilder();
    result.append("{");
    result.append("\"wampStatus\":").append("\"").append(wampStatus).append("\"").append(",");
    result.append("\"webSocket\":").append("\"").append(webSocket.isDone()).append("\"").append(",");

    result.append("\"sessionId\":");
    if (sessionId.isDone()) {
      result.append(getSessionId()).append(",");
    } else {
      result.append(0).append(",");
    }
    result.append("\"metaSubscriptions\":");
    List<String> sList = subscriptions.values().stream().map(s -> {
      try {
        if (s.isDone()) {
          return String.valueOf(s.get());
        } else {
          return "-1";
        }
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
        return "-2";
      }
    }).collect(Collectors.toList());
    result.append(sList.toString());
    result.append("}");
    //System.out.println("parsing:" + result.toString());
    return ArgoJsonUtil.parse(result.toString());
  }

  public boolean isConnected() {
    return this.webSocket.isDone() && this.sessionId.isDone();
  }
  
  private Long getSessionId() {
    try {
      return this.sessionId.get();
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.log(Level.WARNING, "Exception getting sessionId: " + e.getMessage(), e);
      return Long.valueOf(0);
    }
  }

  // Connects to wamp router and returns session id if all is well
  // TOOD: return some status code, not the web socket...
  public CompletableFuture<Long> connect(String webSocketUrl, String realm) {
    CompletableFuture<Long> sessionIdFuture = new CompletableFuture<Long>();
    wampStatus = "CONNECTING";
    CompletableFuture.runAsync(() -> {
      try {
        URI wsURI = URI.create(webSocketUrl);
        LOGGER.log(Level.INFO, "connecting to wamp router: " + wsURI + ", realm:" + realm);
        listener.addEventListeners(this, this, this);
        listener.addRpcResultListener(this);
        HttpClient httpClient = HttpClient.newHttpClient();
        //open web socket and set protocol to WAMP v2:
        CompletableFuture<WebSocket> socket = httpClient.newWebSocketBuilder().subprotocols("wamp.2.json").buildAsync(wsURI, listener).toCompletableFuture();

        socket.whenComplete((s, e) -> {
          if (e != null) {
            LOGGER.log(Level.WARNING, "connect exception: " + e.getMessage(), e);
            if (e.getMessage().contains("WebSocketHandshakeException")) {
              var ex = ((java.net.http.WebSocketHandshakeException) e.getCause()).getResponse();
              StringBuilder sb = new StringBuilder();
              sb.append("Body:\t" + ex.body());
              sb.append("Headers:");
              ex.headers().map().forEach((k, v) -> sb.append("\t" + k + ":  " + v));
              sb.append("HTTP request:  " + ex.request());
              sb.append("HTTP version:  " + ex.version());
              sb.append("Previous Reponse?:  " + ex.previousResponse());
              LOGGER.log(Level.WARNING, sb.toString());
            } else if (e.getMessage().contains("java.net.ConnectException")) {
              StringBuilder sb = new StringBuilder().append("Cannot connect to wamp router: " + e.getMessage());
              sb.append("url:").append(webSocketUrl).append(CR);
              sb.append("realm:").append(realm).append(CR);
              LOGGER.log(Level.WARNING, sb.toString());
              //TODO: larm här?
            }
            webSocket.completeExceptionally(e);
            wampStatus = "WEB_SOCKET_NOT_AVAILABLE";
            sessionIdFuture.complete(-2L);
          }
          if (s != null) {
            LOGGER.log(Level.INFO, "Got websocket, accepted protocol: " + s.getSubprotocol());
            wampStatus = "WEB_SOCKET_AVAILABLE";

            // prepare for welcome response:
            // onWelcomeFuture.wait(timeoutMillis); add timeout if no response from router?!
            sessionId.whenComplete((sid, ex) -> {
              if (ex != null) {
                wampStatus = "WELCOME_FAILED";
                LOGGER.log(Level.WARNING,"Did not recieve WELCOME message from wamp router", ex);
                webSocket.completeExceptionally(ex);
                sessionIdFuture.complete(-3L);
              } else {
                wampStatus = "WELCOME_RECEIVED";
                LOGGER.log(Level.INFO, "Receieved WELCOME message from wamp router: " + sid);
                webSocket.complete(s);
                sessionIdFuture.complete(sid);
              }
            });

            // send hello message
            sendHello(s, realm).whenComplete((response, ex) -> {
              if (ex == null) {
                wampStatus = "HELLO_SENT";
                LOGGER.log(Level.INFO, "Sent HELLO to wamp router");
                // waiting for welcome message to complete
                // Todo: start timer here - we want to timeout if no welcome received
              } else {
                LOGGER.log(Level.WARNING, "Failed to send HELLO: " + ex.getMessage(), ex);
                wampStatus = "HELLO_FAILED";
                webSocket.completeExceptionally(ex);
                sessionIdFuture.complete(0L);
              }
            });
          }
        });

      } catch (Exception ex) {
        LOGGER.log(Level.WARNING, "Exception connecting to wamp router: " + ex.getMessage(), ex);
        webSocket.completeExceptionally(ex);
        sessionIdFuture.complete(-1L);
      }
    }, mExecutor);
    return sessionIdFuture;
  }

  public CompletableFuture<String> disconnectFromWampRouter() {
    //TODO: initiate close, unless initiated from wamp router?
    waitforWRGoodbye = new CompletableFuture<String>();
    wampStatus ="DISCONNECTING_FROM_WR";
    LOGGER.log(Level.INFO, "disconnect: Closing/disconnecting from wamp router");
    try {
      LOGGER.log(Level.INFO, "Sending goodbye ");
      WebSocket socket = webSocket.get();
      sendGoodbye(socket).whenComplete((a, e) -> {
        // goodbye message sent to wr, wait for goodbye response?!
        if (e != null) {
          LOGGER.log(Level.INFO, "Goodbye exception: " + e.getMessage());
          wampStatus ="SENT_GOODBYE_TO_WR_FAIL";
          e.printStackTrace();
        } else {
          wampStatus ="SENT_GOODBYE_TO_WR";
        }
      });
      //TODO: remove this wait
      Thread.sleep(3000); //wait for response before close
    } catch (Exception ex) {
      LOGGER.log(Level.WARNING, "Exception in disconnectFromWampRouter: " + ex.getMessage(), ex);
      wampStatus ="SENT_GOODBYE_TO_WR_EXCEPTION";
    }
    return waitforWRGoodbye;
  }

  private CompletableFuture<WebSocket> closeWebSocket() {
    CompletableFuture<WebSocket> futureWebSocketClose;
    try {
      futureWebSocketClose = webSocket.get().sendClose(WebSocket.NORMAL_CLOSURE, "{\"message\":\"ws.close byebye\"}");
    } catch (Exception ex) {
      LOGGER.log(Level.WARNING, "Exception in closeWebSocket 2: " + ex.getMessage(), ex);
      futureWebSocketClose = new CompletableFuture<WebSocket>();
      futureWebSocketClose.completeExceptionally(ex);
    }

    futureWebSocketClose.whenComplete((ws, ex) -> {
      if (ex != null) {
        LOGGER.log(Level.WARNING, "Exception in closeWebSocket 1: " + ex.getMessage(), ex);
      } else {
        LOGGER.log(Level.INFO, "Websocket disconnecting, close message was sent");
      }
    });

    return futureWebSocketClose;
  }

  public CompletableFuture<Long> subscribe(String topic) {
    long requestId = idGenerator.next();
    CompletableFuture<Long> suscriptionFuture = new CompletableFuture<Long>();

    String subscribeStr = "[" + WampMessageType.SUBSCRIBE.getMessageNumber() + ", " + requestId + ",{},\"" + topic + "\"]";
    LOGGER.log(Level.INFO, "RAW message, subscribe: " + subscribeStr);

    try {
      subscriptions.put(requestId, suscriptionFuture);
      subscriptionTopics.put(requestId, topic);
      webSocket.get().sendText(subscribeStr, true);
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.log(Level.WARNING, "subscribe exception: " + e.getMessage(), e);
      suscriptionFuture.completeExceptionally(e);
    }
    return suscriptionFuture;
  }

  public CompletableFuture<Long> unSubscribe(Long subscriptionId) {
    long requestId = idGenerator.next();
    CompletableFuture<Long> unSuscriptionFuture = new CompletableFuture<Long>();
    LOGGER.log(Level.INFO, "Unsubscribe: subscriptionId= " + subscriptionId);
    if (subscriptions.containsKey(subscriptionId)) { //TODO: value not key ?! requestId is key
      CompletableFuture<Long> subFuture = subscriptions.get(subscriptionId);
      LOGGER.log(Level.INFO, "removing subscription: " + subFuture.isDone());
      subFuture.whenComplete((s, e) -> {
        if (e == null) {
          LOGGER.log(Level.INFO, "removed subscription: " + s);
        } else {
          LOGGER.log(Level.WARNING, "remove subscription failed " + e.getMessage(), e);
        }
      });
    }
    // Unsubscribe unsubscribe = new Unsubscribe(requestId, subscriptionId);
    // System.out.println("unsubscribe 1:" + unsubscribe.marshal().toString());
    String unSubscribeStr = "[" + WampMessageType.UNSUBSCRIBE.getMessageNumber() + ", " + requestId + ", " + subscriptionId + "]";
    LOGGER.log(Level.INFO, "RAW message: " + unSubscribeStr);

    try {
      unSubscriptions.put(requestId, unSuscriptionFuture);
      webSocket.get().sendText(unSubscribeStr, true).thenAccept(a -> {
        LOGGER.log(Level.INFO, "successful unsubscribe: " + subscriptionId);
      }).exceptionally(e -> {
        LOGGER.log(Level.WARNING, "exception in unsubscribe: " + e.getMessage());
        return null;
      });
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.log(Level.WARNING, "unsubscribe exception: " + e.getMessage() + ", subscriptionId=" + subscriptionId,
          e);
      unSuscriptionFuture.completeExceptionally(e);
    }
    return unSuscriptionFuture;
  }

  public CompletableFuture<WampMessage> rpcCall(String method, WampArgs args, WampKwArgs kwArgs) {
    LOGGER.log(Level.INFO, "rpcCall: " + method);
    CompletableFuture<WampMessage> future = new CompletableFuture<WampMessage>();
    Long requestId = idGenerator.next();
    rpcCallFutures.put(requestId, future);
    rpcCallNames.put(requestId, method);

    String callStr = "[" + WampMessageType.RPC_CALL.getMessageNumber() + ", " + requestId + ", {}, \"" + method + "\"," + args.toString() + "," + kwArgs.toString() + "]";
    LOGGER.log(Level.INFO, "RAW message, rpcCall: " + callStr);
    try {
      webSocket.get().sendText(callStr, true);
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.log(Level.WARNING, "rpcCall exception: " + e.getMessage(), e);
      future.completeExceptionally(e);
    }

    /*future.whenComplete((wampMessage, ex) -> {
      if (wampMessage != null) {
        LOGGER.log(Level.INFO, "rpcCall was sent " + method + ", " + wampMessage);
        // TODO: notify listener?
      } else {
        LOGGER.log(Level.WARNING, "rpcCall failed: " + ex.getMessage(), ex);
      }
    });*/
    return future;
  }

  public CompletableFuture<Long> registerRpcMethod(String procedure) {
    CompletableFuture<Long> future = new CompletableFuture<>();
    Long requestId = idGenerator.next();
    String reg = "[" + WampMessageType.REGISTER.getMessageNumber() + ", " + requestId + ", {}, \"" + procedure + "\"]";
    //HashMap<String, Object> kwArgs = new HashMap<>();
    //kwArgs.put("invoke", "first"); // TODO: jsonString
    LOGGER.log(Level.INFO, "RAW message, rpcMethodRegister:" + reg);
    // System.out.println("sending rpcRegister: " + reg);
    // String r = "[" + WampMessageType.REGISTER.getMessageNumber() + ", " +
    // requestId + " ," + ArgoJsonUtil.format(kwArgs) + "]";
    // if (!webSocket.isDone()) {
    // System.out.println("websocket not yet available");
    try {
      webSocket.get().sendText(reg, true).whenComplete((socket, ex) -> {
        if (ex == null) {
          LOGGER.log(Level.INFO, "rpcRegister sent");
          future.complete(requestId);
        } else {
          LOGGER.log(Level.WARNING, "rpcRegister failed", ex);
          future.completeExceptionally(ex);
        }
      });
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.log(Level.INFO,"Exception sending rpcRegister: " + e.getMessage());
      future.completeExceptionally(e);
    }
    return future;
  }

  // unregister rpc methods when closing down:
  public void unRegister(Long rpcMethodId) {
    Long requestId = idGenerator.next();
    // Unregister unregister = new Unregister(requestId, rpcMethodId);
    // System.out.println("unregister1: " + unregister.marshal().toString());
    String unReg = "[" + WampMessageType.UNREGISTER.getMessageNumber() + ", " + requestId + " ," + rpcMethodId
        + "]";
    LOGGER.log(Level.INFO, "RAW message unregister=" + unReg);
    try {
      webSocket.get().sendText(unReg, true).whenComplete((ws, ex) -> {
        if (ex == null) {
          LOGGER.log(Level.INFO, "rpcUnregisterMethod message sent");
        } else {
          LOGGER.log(Level.WARNING, "rpcUnregisterMethod failed: " + ex.getMessage(), ex);
        }
      });
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.log(Level.WARNING, "Exception unregistering method: " + e.getMessage(), e);
    }
  }

  public CompletableFuture<Publication> publish(String topic, WampArgs args, WampKwArgs kwArgs) {
    LOGGER.log(Level.INFO, "publish: topic=" + topic);
    CompletableFuture<Publication> publicationFuture = new CompletableFuture<Publication>();
    Long requestId = idGenerator.next();

    // Publish publishMessage = new Publish(requestId, topic, args.getArgs(),
    // kwArgs.getKwArgs(), false, true, false);
    // System.out.println("publish 1: " + publishMessage.marshal().toString());

    // //String publishString = "["+ WampMessageType.PUBLISH.getMessageNumber() +
    // "," + requestId +",{},\"" + topic + "\",[" + args.toString() +
    // ",900,\"UNIQUE\"]," + kwArgs.toString() + "]";
    String publishString = "[" + WampMessageType.PUBLISH.getMessageNumber() + "," + requestId + ",{},\"" + topic + "\"," + args.toString() + "," + kwArgs.toString() + "]";
    // LOGGER.log(Level.INFO,"RAW message - publish: " + publishString); 
    try {
      webSocket.get().sendText(publishString, true).whenComplete((ws, ex) -> {
        if (ex == null) {
          // ws is websocket...
          LOGGER.log(Level.INFO, "publish success: " + topic);
          publicationFuture.complete(new Publication(requestId));
        } else {
          LOGGER.log(Level.WARNING, "publish failed: " + ex.getMessage());
          publicationFuture.completeExceptionally(ex);
        }
      });
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.log(Level.WARNING, "publish exception: " + e.getMessage());
      publicationFuture.completeExceptionally(e);
    }

    return publicationFuture;
  }

  // the first message sent from buffy to wamp-router when connecting
  private CompletableFuture<WebSocket> sendHello(WebSocket socket, String realm) {
    Map<String, Map> roles = new HashMap<>();
    roles.put("publisher", new HashMap<>());
    roles.put("subscriber", new HashMap<>());
    roles.put("caller", new HashMap<>());
    roles.put("callee", new HashMap<>());

    List<String> authMethods = new ArrayList<>();
    authMethods.add("anonymous");
    String authId = "backend";
    /*
     * Hello hello = new Hello(realm, roles, authMethods, authId, authExtra);
     * System.out.println("hello 1: " + hello.marshal());
     */
    // JsonNode options =
    // ArgoJsonUtil.parse("{\"authmethods\":[\"anonymous\"],\"roles\":{\"caller\":{},\"subscriber\":{},\"callee\":{},\"publisher\":{}},\"authid\":\"backend\"}");
    String helloStr = "[" + WampMessageType.HELLO.getMessageNumber() + ",\"" + realm
        + "\",{\"authmethods\":[\"anonymous\"],\"roles\":{\"caller\":{},\"subscriber\":{},\"callee\":{},\"publisher\":{}},\"authid\":\""
        + authId + "\"}]";
    return socket.sendText(helloStr, true);
  }

  // the first message sent from buffy to wamp-router when connecting
  private CompletableFuture<WebSocket> sendGoodbye(WebSocket socket) {
    //	Goodbye goodbye = new Goodbye("test1", "message1");
    //String gbs = goodbye.marshal().toString();
    //LOGGER.log(Level.INFO, "Goodbye 1: " + goodbye.marshal());
    // str="6, {message=buffy}, bye from]"
    // JsonNode options =
    // ArgoJsonUtil.parse("{\"authmethods\":[\"anonymous\"],\"roles\":{\"caller\":{},\"subscriber\":{},\"callee\":{},\"publisher\":{}},\"authid\":\"backend\"}");
    String goodByeStr = "[" + WampMessageType.GOODBYE.getMessageNumber() + ",{\"message\":\"wamp.close.normal\"},\"buffytest2\"]";
    LOGGER.log(Level.INFO, "goodByeStr: " + goodByeStr);

    CompletableFuture<WebSocket> future = socket.sendText(goodByeStr, true);
    return future;
  }

  public void sendRpcYield(Long invocationRequestId, WampArgs args, WampKwArgs kwArgs) {
    // sending response to rpc invocation
    String yieldString = ("[" + WampMessageType.RPC_YIELD.getMessageNumber() + "," + invocationRequestId + ",{},"
        + args.toString() + "," + kwArgs.toString() + "]");
    LOGGER.log(Level.INFO, "RAW message sendRpcYield: " + yieldString);
    try {
      webSocket.get().sendText(yieldString, true).whenComplete((socket, ex) -> {
        LOGGER.log(Level.INFO, "sent rpcYield: " + yieldString + ": success=" + (ex == null));
      });
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.log(Level.WARNING, "Exception sending rpcYield: " + e.getMessage(), e);
    }
  }

  @Override
  public void onWampClose(JsonNode options, String message) {
    LOGGER.log(Level.INFO, "onWampClose: options=" + options + ", message=" + message);
    //TODO: close web socket here?!
    // TODO: unregister subscriptions and rpc-methods
    // subSub.cancel(true);
    // subUnSub.cancel(true);

    // TODO: wait for wr reponse here: unless WR initiated close... 
    if (waitforWRGoodbye != null) {
      waitforWRGoodbye.complete("got disconnect from WR");
      //} else {
      //waitforBuffyGoodbye = new CompletableFuture<>(); //TODO: need to close the buffy wamp client...
    }
    closeWebSocket().whenComplete((b, ex) -> {
      System.out.println("disconnected webSocket");
    });

  }

  @Override
  public void onWampOpen(Long sessionId, JsonNode details) {
    // We have created a session in the wamp router after initial exchange of hello-welcome
    LOGGER.log(Level.INFO, "onWampOpen: " + sessionId); // + ", details: " + ArgoJsonUtil.format(details));
    this.sessionId.complete(sessionId);
  }

  @Override
  public void onSubscribed(Long requestId, Long subscriptionId) {
    // find matching request in subscribed list and set future to complete
    LOGGER.log(Level.INFO, "onSubscribed: requestId=" + requestId + ", subscriptionId=" + subscriptionId);
    if (subscriptions.containsKey(requestId)) {
      //CompletableFuture<Long> s = subscriptions.get(requestId);
      String topic = subscriptionTopics.get(requestId);
      LOGGER.log(Level.INFO,"Successful subscription: requestId=" + requestId + ", subscriptionId=" + subscriptionId + ", topic=" + topic);
      subscriptions.get(requestId).complete(subscriptionId);
    }
  }

  @Override
  public void onRpcResult(Long requestId, String options, WampArgs args, WampKwArgs kwArgs) {
    LOGGER.log(Level.INFO, "onRpcResult: " + requestId + "," + options + ", " + args + " " + kwArgs);
    if (rpcCallFutures.containsKey(requestId)) {// TODO: stega igenom keyset?!
      LOGGER.log(Level.INFO, "Successful rpc call, found matching requestId " + requestId);
      WampMessage wampMessage = new WampMessage(WampMessageType.RPC_RESULT, requestId, args, kwArgs);
      rpcCallFutures.get(requestId).complete(wampMessage);
    } else {
      LOGGER.log(Level.WARNING, "Successful rpc call but NO matching requestId found: " + requestId + " " + rpcCallFutures.keySet());
    }

  }

  /*
   * TODO: private <T> T addListener(ArrayList<T> listeners, T listener) {
   * listeners.add(listener); return listener; }
   * 
   * private <T> void removeListener(ArrayList<T> listeners, T listener) { if
   * (listeners.contains(listener)) { listeners.remove(listener); } }
   */
  public void addWampEventListener(OnWampEventListener e) {
    listener.addWampEventListener(e);
  }

  public void addRpcListener(OnRpcResultListener r) {
    listener.addRpcResultListener(r);
  }

  public void addRpcRegisterListener(OnRpcRegisterListener r) {
    listener.addRpcRegisterListener(r);
  }

  public void addRpcInvocationListener(OnRpcInvocationListener r) {
    listener.addRpcInvocationListener(r);
  }

}
