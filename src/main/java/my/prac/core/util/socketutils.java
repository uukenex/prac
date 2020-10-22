package my.prac.core.util;

import java.util.ArrayList;
import java.util.List;


public class socketutils {
static List<String> socket_user_list = new ArrayList<>();
	
	public static void addValue(String arg0){
		socket_user_list.add(arg0);
	}
	public static void delValue(String arg0){
		socket_user_list.remove(arg0);
	}
	public static String viewValue(){
		return socket_user_list.toString();
	}
}

