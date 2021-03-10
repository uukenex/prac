package my.prac.config;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import my.prac.core.util.socketutils2;

@ServerEndpoint("/WebSocket5")
public class WebSocket5 {
	private static Set<Session> clients = Collections.synchronizedSet(new HashSet<Session>());

	@OnMessage
	public void onMessage(String message, Session session) throws Exception {
		try {
			synchronized (clients) {
				for (Session client : clients) {
					client.getBasicRemote().sendText(message);
				}
			}
		} catch (Exception e) {

		}

	}

	@OnOpen
	public void onOpen(Session session) throws Exception {
		try {
			clients.add(session);
			String userNick = session.getRequestParameterMap().get("userNick").toString();
			userNick = new String(userNick.getBytes("iso-8859-1"), "utf-8");
			socketutils2.addValue(userNick);

			for (Session client : clients) {
				try {
					client.getBasicRemote().sendText("00|" + userNick + " 입장. 현재 " + clients.size() + "명");
					client.getBasicRemote().sendText("07|" + userNick);
					client.getBasicRemote().sendText("09|" + socketutils2.viewValue());
				} catch (IOException e) {
					continue;
				}
			}
		} catch (Exception e2) {

		}

	}

	@OnClose
	public void onClose(Session session) throws Exception {
		try {
			clients.remove(session);
			String userNick = session.getRequestParameterMap().get("userNick").toString();
			userNick = new String(userNick.getBytes("iso-8859-1"), "utf-8");
			socketutils2.delValue(userNick);

			for (Session client : clients) {
				try {
					client.getBasicRemote().sendText("00|" + userNick + " 퇴장. 현재 " + clients.size() + "명");
					client.getBasicRemote().sendText("08|" + userNick);
					client.getBasicRemote().sendText("09|" + socketutils2.viewValue());
				} catch (IOException e) {
					continue;
				}
			}
		} catch (Exception e2) {

		}

	}

	@OnError
	public void error(Session session, Throwable t) {

	}
}

/*
 * //한글인코딩테스트 https://chirow.tistory.com/404 String [] charSet =
 * {"utf-8","euc-kr","ksc5601","iso-8859-1","x-windows-949"}; for (int i=0;
 * i<charSet.length; i++) { for (int j=0; j<charSet.length; j++) { try {
 * System.out.println("[" + charSet[i] +"," + charSet[j] +"] = " + ); } catch
 * (Exception e) { e.printStackTrace(); } } }
 */