package my.prac.core.util;

import java.io.DataInputStream;
import java.net.Socket;

//서버로부터 메시지를 읽는 클래스

public class ReceiveServer extends Thread {

	Socket socket;

	DataInputStream in;

	// Socket을 매개변수로 받는 생성자.

	public ReceiveServer(Socket socket) {

		this.socket = socket;

		try {

			in = new DataInputStream(this.socket.getInputStream());

		} catch (Exception e) {

			System.out.println("예외:" + e);

		}

	}// 생성자 --------------------

	@Override

	public void run() { // run()메소드 재정의

		while (in != null) { // 입력스트림이 null이 아니면..반복

			try {

				System.out.println(in.readUTF());

				// 서버로부터 읽어온 데이터를 콘솔에 출력

			} catch (Exception e) {

				System.out.println("예외:" + e);

			}

		} // while----

	}// run()------

}// class Receiver -------
