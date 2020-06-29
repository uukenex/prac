package my.prac.core.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

public class WebSocketServer {

	HashMap clientMap;

	ServerSocket serverSocket = null;

	Socket socket = null;

	// 생성자

	public WebSocketServer() {

		clientMap = new HashMap(); // 클라이언트의 출력스트림을 저장할 해쉬맵 생성.

		Collections.synchronizedMap(clientMap); // 해쉬맵 동기화 설정.

	}// 생성자----

	public void main() {

		try {

			serverSocket = new ServerSocket(7777); // 7777포트를 열었다.

			System.out.println("종규서버가 시작되었습니다");// 서버시작을 알렸다.

			while (true) { // 서버가 실행되는 동안 클라이언트들의 접속을 기다림.

				socket = serverSocket.accept(); // 클라이언트가 접속을 했다.

				System.out.println(socket.getInetAddress() + ":" + socket.getPort()); // 클라이언트
																						// 정보
																						// (ip,
																						// 포트)
																						// 출력

				Thread jong = new MultiServerRec(socket); // 쓰레드 생성.

				jong.start(); // 쓰레드 시작

			}

		} catch (Exception e) {

			e.printStackTrace();

		}

	}

	// 접속된 모든 클라이언트들에게 메시지를 전달.

	public void allsend(String message) {

		// 출력스트림을 순차적으로 얻어와서 해당 메시지를 출력한다.

		Iterator ssend = clientMap.keySet().iterator();

		while (ssend.hasNext()) {// 클라이언트가 보내는 메세지를 하나씩 꺼내온다.

			try {

				DataOutputStream ssendout = (DataOutputStream) clientMap.get(ssend.next());

				ssendout.writeUTF(message);

			} catch (Exception e) {

				System.out.println("예외:" + e);

			}

		}

	}// allsend()메소드 끝

	// 클라이언트로부터 읽어온 메시지를 다른 클라이언트(socket)에 보내는 역할을 하는 메서드

	class MultiServerRec extends Thread {

		Socket socket;

		DataInputStream in;

		DataOutputStream out;

		// 생성자.

		public MultiServerRec(Socket socket) {

			this.socket = socket;

			try {

				// Socket으로부터 입력스트림을 얻는다.

				in = new DataInputStream(socket.getInputStream());

				// Socket으로부터 출력스트림을 얻는다.

				out = new DataOutputStream(socket.getOutputStream());

			} catch (Exception e) {

				System.out.println("예외:" + e);

			}

		}// 생성자 ------------

		@Override

		public void run() { // 쓰레드를 사용하기 위해서 run()메서드 재정의

			String name = null; // 클라이언트로부터 받은 이름을 저장할 변수.

			try {

				name = in.readUTF(); // 클라이언트에서 처음으로 보내는 메시지는

				// 클라이언트가 사용할 이름이다.

				allsend(name + "님이 입장하셨습니다.");// 입장한거를 알리기 위해 클라이언트들한테 다 보낸다.

				clientMap.put(name, out); // 해쉬맵에 키를 name으로 출력스트림 객체를 저장.

				// 이거 한 이유는 이름별로 말하는걸 보기 위해서이다.

				System.out.println("현재 접속자 수는 " + clientMap.size() + "명 입니다.");

				// 몇명이 접속했는지 알기 위해서 만들었다.

				while (in != null) { // 입력스트림이 null이 아니면 반복.

					allsend(in.readUTF()); // 현재 소켓에서 읽어온메시지를 해쉬맵에 저장된 모든

					// 출력스트림으로 보낸다.

				} // while()---------

			} catch (Exception e) {

				System.out.println(e + "----> ");

			} finally {

				// 예외가 발생할때 퇴장. 해쉬맵에서 해당 데이터 제거.

				// 보통 종료하거나 나가면 java.net.SocketException: 예외발생

				clientMap.remove(name);

				allsend(name + "님이 퇴장하셨습니다.");

				System.out.println("현재 접속자 수는 " + clientMap.size() + "명 입니다.");

			}

		}// run()------------

	}// class MultiServerRec-------------

}
