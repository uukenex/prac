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
		synchronized (clients) {
			for (Session client : clients) {
				// if (!client.equals(session)) {
				client.getBasicRemote().sendText(message);
				// }
			}
		}
	}

	@OnOpen
	public void onOpen(Session session) {
		clients.add(session);
		/*
		 * for (Session client : clients) { // if (!client.equals(session)) {
		 * try { client.getBasicRemote().sendText("누군가가 입장했습니다."); } catch
		 * (IOException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); } // } }
		 */
	}

	@OnClose
	public void onClose(Session session) {
		clients.remove(session);
		/*
		 * for (Session client : clients) { // if (!client.equals(session)) {
		 * try { client.getBasicRemote().sendText("누군가가 떠났습니다."); } catch
		 * (IOException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); } // } }
		 */
	}
}
