package my.prac.core.util;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

//서버로 메시지를 전송하는 클래스 

public class SendServer extends Thread {

	Socket socket;

	DataOutputStream out;

	String name;

	// 생성자 ( 매개변수로 소켓과 사용자 이름 받습니다. )

	public SendServer(Socket socket, String name) { // 소켓과 사용자 이름을 받는다.

		this.socket = socket;

		try {

			out = new DataOutputStream(this.socket.getOutputStream());

			this.name = name; // 받아온 사용자이름을 전역변수에 저장, 다른 메서드인 run()에서 사용하기위함.

		} catch (Exception e) {

			System.out.println("예외:" + e);

		}

	}

	@Override

	public void run() { // run()메소드 재정의

		Scanner s = new Scanner(System.in);

		// 키보드로부터 입력을 받기위한 스캐너 객체 생성

		// 서버에 입력한 사용자이름을 보내준다.

		try {

			out.writeUTF(name);

		} catch (IOException e) {

			System.out.println("예외:" + e);

		}

		while (out != null) { // 출력스트림이 null이 아니면..반복

			try { // while문 안에 try-catch문을 사용한 이유는 while문 내부에서 예외가 발생하더라도

				// 계속 반복할수있게 하기위해서이다.

				out.writeUTF(name + " : " + s.nextLine()); // 키보드로부터 입력받은 문자열을
															// 서버로 보낸다.

			} catch (IOException e) {

				System.out.println("예외:" + e);

			}

		} // while------

	}// run()------

}// class Sender-------
