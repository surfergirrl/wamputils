package net.iqaros.wamp;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import argo.jdom.JsonNode;
import net.iqaros.util.ArgoJsonUtil;
import net.iqaros.wamp.core.OnRpcResultListener;
import net.iqaros.wamp.core.WampArgs;
import net.iqaros.wamp.core.WampKwArgs;
import net.iqaros.wamp.core.WampWebSocketClient;

public class gamma {

	public static void main(String... strings) {
		CountDownLatch countdownLatch = new CountDownLatch(2);
		System.out.println("starting");
		OnRpcResultListener rpcListener = new OnRpcResultListener() {
			@Override
			public void onRpcResult(Long requestId, String options, WampArgs args, WampKwArgs kwargs) {
				System.out.println("rpcResult" + requestId + args.getArgs() + ", kwargs=" + kwargs.getKwArgs().toString());
				countdownLatch.countDown();		
			}
	      };
	      
		
		WampWebSocketClient wampClient = new WampWebSocketClient();
		wampClient.addRpcListener(rpcListener);
		WampArgs args = new WampArgs();
		String ATTRIBUTE_GET ="iqapi.attribute.get";
		String str = "{\"demarcation\":\"v2.GheqBAhMRC1DT1MtMaoECUxJRC1DT1MtMQ\",\"attribute\":\"city\"}";
		JsonNode json = ArgoJsonUtil.parse(str);
		System.out.println("json check: " + ArgoJsonUtil.format(json));
		WampKwArgs kwargs = new WampKwArgs(str);
		System.out.println("kwargs =" + kwargs.getJsonString());
		//CompletableFuture<Long> connection =
		wampClient
				.connect("wss://gamma-iqaros.comhem.com/ws", "iqaros") //anonymous
				.thenAccept(sessionId -> {
					System.out.println("connected: " + sessionId);
					wampClient.rpcCall(ATTRIBUTE_GET, args, kwargs).thenAccept(b -> {
						System.out.println("rpc was called" + b);
						countdownLatch.countDown();	
					});
				});
		try {
			countdownLatch.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			System.out.println("closing");
			wampClient.disconnectFromWampRouter().thenAccept(done -> {
				System.out.println("disconnected:" + done);
			});
		 //});
			//wait for response before close
		  try {
			  Thread.sleep(3000); 
		  } catch (Exception ex) {}
		}
	}
		
}
