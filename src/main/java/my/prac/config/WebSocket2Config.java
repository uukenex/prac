package my.prac.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// WebSocket의 호스트 주소 설정
@RequestMapping("/websocket2")
public class WebSocket2Config extends TextWebSocketHandler {
	// WebSocket으로 브라우저가 접속하면 요청되는 함수
	// 세션 리스트
	private List<WebSocketSession> sessionList = new ArrayList<WebSocketSession>();

	// 클라이언트가 연결 되었을 때 실행
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		System.out.println("인입확인");
		sessionList.add(session);
	}

	// 클라이언트가 웹소켓 서버로 메시지를 전송했을 때 실행
	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		// 모든 유저에게 메세지 출력
		System.out.println(message.getPayload());
		for (WebSocketSession sess : sessionList) {
			sess.sendMessage(new TextMessage(message.getPayload()));
		}
	}

	// 클라이언트 연결을 끊었을 때 실행
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		sessionList.remove(session);
	}
}
