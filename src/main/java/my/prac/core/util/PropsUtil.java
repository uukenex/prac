package my.prac.core.util;

import java.io.Reader;
import java.util.Properties;

import org.apache.ibatis.io.Resources;

public class PropsUtil {
	public static String getProperty(String PropsName,String key){
		String resource = "safety/"+PropsName+".properties";
		Properties props = new Properties();
		
		try{
			Reader reader = Resources.getResourceAsReader(resource);
			props.load(reader);
		} catch(Exception e){
			e.printStackTrace();
		}
		
		return props.getProperty(key);
	}
	
}
