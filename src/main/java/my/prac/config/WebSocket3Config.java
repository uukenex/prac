package my.prac.config;

import org.json.simple.JSONObject;

import io.socket.client.IO;
import io.socket.emitter.Emitter;

public class WebSocket3Config implements Emitter.Listener {

	private io.socket.client.Socket socket = null;

	public WebSocket3Config() {

		// TODO Auto-generated constructor stub

		try {

			socket = IO.socket("http://localhost:80");

			socket.connect();

			socket.on("subscribe", this); // subscribe

			JSONObject jobj = new JSONObject();

			jobj.put("msg", "init");

			jobj.put("id", "jdh1");

			socket.emit("init", jobj);

		} catch (Exception e) {

			// TODO: handle exception

		}

	}

	public void send(String id, String msg) throws Exception {

		try {

			JSONObject jobj = new JSONObject();

			jobj.put("msg", msg);

			jobj.put("id", id);

			socket.emit("publish", jobj);

		} catch (Exception e) {

			System.out.println(e.getMessage());

		}

	}

	@Override

	public void call(Object... arg0) {

		// TODO Auto-generated method stub

		String msg = arg0[0].toString();

		System.out.println("받은내용:" + msg);

	}

}
