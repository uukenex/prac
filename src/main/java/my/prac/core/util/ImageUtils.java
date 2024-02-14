package my.prac.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Random;

public class ImageUtils {
	
	public static String RandomAlphaNum() {
		char[] rnd = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '9', '8', '7', '6', '5', '4', '3', '2', '1',
				'0', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
				'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E',
				'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };

		Random random = new Random();
		
		String pass="";
		for (int j = 0; j < 6; j++) {
			pass = pass + rnd[random.nextInt(rnd.length)];
		}
		
		return pass;
	}

	
	public static boolean nioCopy(String inFilePath, String outFilePath) {
	    File orgFile = new File(inFilePath);
	    File outFile = new File(outFilePath);

	    try {
	        Files.copy(orgFile.toPath(), outFile.toPath(),
	                        StandardCopyOption.REPLACE_EXISTING);
	    } catch (IOException e) {
	        e.printStackTrace();
	        return false;
	    }
	    return true;
	}
}