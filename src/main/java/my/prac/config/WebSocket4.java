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
		socketutils.addValue(session.getRequestParameterMap().get("userNick").toString());
		
		for (Session client : clients) {
			try {
				client.getBasicRemote().sendText("00|누군가가 입장헸습니다. 현재 " + clients.size() + "명");
				client.getBasicRemote().sendText("09|"+socketutils.viewValue());
			} catch (IOException e) {
				continue;
			}
		}

	}

	@OnClose
	public void onClose(Session session) throws Exception {
		clients.remove(session);
		socketutils.delValue(session.getRequestParameterMap().get("userNick").toString());
		
		for (Session client : clients) {
			try {
				client.getBasicRemote().sendText("00|누군가가 퇴장했습니다. 현재 " + clients.size() + "명");
				client.getBasicRemote().sendText("09|"+socketutils.viewValue());
			} catch (IOException e) {
				continue;
			}
		}
	}
}
