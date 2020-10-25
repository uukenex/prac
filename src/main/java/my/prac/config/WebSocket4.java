package my.prac.config;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import my.prac.core.util.socketutils;

@ServerEndpoint("/WebSocket4")
public class WebSocket4{
	private static Set<Session> clients = Collections.synchronizedSet(new HashSet<Session>());

	@OnMessage
	public void onMessage(String message, Session session) throws Exception {
		synchronized (clients) {
			for (Session client : clients) {
				client.getBasicRemote().sendText(message);
			}
		}
	}

	@OnOpen
	public void onOpen(Session session) throws Exception {
		clients.add(session);
		String userNick = session.getRequestParameterMap().get("userNick").toString();
		System.out.println(userNick);
		
		String originalStr = userNick; // 테스트

		String [] charSet = {"utf-8","euc-kr","ksc5601","iso-8859-1","x-windows-949"};

		  

		for (int i=0; i<charSet.length; i++) {

		 for (int j=0; j<charSet.length; j++) {

		  try {

		   System.out.println("[" + charSet[i] +"," + charSet[j] +"] = " + new String(originalStr.getBytes(charSet[i]), charSet[j]));

		  } catch (Exception e) {

		   e.printStackTrace();

		  }

		 }

		}
		
		socketutils.addValue(userNick);
		
		for (Session client : clients) {
			try {
				client.getBasicRemote().sendText("00|"+userNick+" 입장. 현재 " + clients.size() + "명");
				client.getBasicRemote().sendText("09|"+socketutils.viewValue());
			} catch (IOException e) {
				continue;
			}
		}

	}

	@OnClose
	public void onClose(Session session) throws Exception {
		clients.remove(session);
		String userNick = session.getRequestParameterMap().get("userNick").toString();
		System.out.println(userNick);
		socketutils.delValue(userNick);
		
		for (Session client : clients) {
			try {
				client.getBasicRemote().sendText("00|"+userNick+" 퇴장. 현재 " + clients.size() + "명");
				client.getBasicRemote().sendText("08|"+userNick);
				client.getBasicRemote().sendText("09|"+socketutils.viewValue());
			} catch (IOException e) {
				continue;
			}
		}
	}
}
