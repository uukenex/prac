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

@ServerEndpoint("/WebSocket4")
public class WebSocket4 {
	private static Set<Session> clients = Collections.synchronizedSet(new HashSet<Session>());

	@OnMessage
	public void onMessage(String message, Session session) throws IOException {

		/*
		 * String msgArr[] = message.split("|"); String playerName = msgArr[0];
		 * String playerX = msgArr[1]; String playerY = msgArr[2]; String
		 * playerMsg = msgArr[3];
		 * 
		 * String msg = playerName + " : " + playerMsg;
		 */
		synchronized (clients) {
			for (Session client : clients) {

				client.getBasicRemote().sendText(message);
			}
		}
	}

	@OnOpen
	public void onOpen(Session session) {
		clients.add(session);

		for (Session client : clients) {
			try {
				client.getBasicRemote().sendText("|||누군가가 입장헸습니다., 현재 " + clients.size() + "명");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	@OnClose
	public void onClose(Session session) {
		clients.remove(session);

		for (Session client : clients) {
			try {
				client.getBasicRemote().sendText("|||누군가가 퇴장했습니다. 현재 " + clients.size() + "명");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
