package my.prac.core.util;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class WebSocketClient {
	public void main() {

		Scanner sc = new Scanner(System.in);

		System.out.print("이름을 입력해 주세요 : ");// 처음에 이름을 보내기 위해서이다.

		String name = sc.next();

		System.out.print("ip를 입력하세요 : ");

		String ip = sc.next();

		try {

			String ServerIP = ip;

			Socket socket = new Socket(ServerIP, 7777); // 소켓 객체 생성

			System.out.println("서버와 연결이 되었습니다......");

			// 사용자로부터 얻은 문자열을 서버로 전송해주는 역할을 하는 쓰레드.

			Thread sender = new SendServer(socket, name);

			// 서버에서 보내는 메시지를 사용자의 콘솔에 출력하는 쓰레드.

			Thread receiver = new ReceiveServer(socket);

			System.out.println("채팅방에 입장하였습니다.");

			sender.start(); // 스레드 시동

			receiver.start(); // 스레드 시동

		} catch (UnknownHostException e) {

			// e.printStackTrace();

		} catch (IOException e) {

			// e.printStackTrace();

		} catch (Exception e) {

			System.out.println(e);

		}

	}

}
