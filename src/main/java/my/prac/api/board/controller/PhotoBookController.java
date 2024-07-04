package my.prac.api.board.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import my.prac.core.dto.Users;

@Controller
public class PhotoBookController {
	static Logger logger = LoggerFactory.getLogger(PhotoBookController.class);
	//@Autowired
	//PhotoBookService ps;

	// 포토북 페이지로 들어감
	@RequestMapping(value = "/session/myPhoto", method = RequestMethod.GET)
	public String myPhoto(Model model, HttpSession session, @RequestParam String userId,
			@RequestParam String folderName) {
				
		Users users = (Users) session.getAttribute("Users");
		if (users == null) {
			users = new Users();
			users.setUserId(".");
			session.setAttribute("forPage", "session/photobook/photo_sign");
		}
		model.addAttribute("folderName", folderName);

		
		List<String> shareFolderList = null;
		// path와 로그인아이디로 공유폴더리스트를 받아옴
		//shareFolderList = ps.selectFolderName(userId, users.getUserId());

		model.addAttribute("shareFolder", shareFolderList);
		return "session/photobook/photo_sign";
	}

	// 포토북 업로드 ajax
	//private final String UPLOAD_DIR = "c:/Temp/";
	private final String UPLOAD_DIR = "/img/dev2/";

	@RequestMapping(value = "/photo", method = RequestMethod.POST)
	public @ResponseBody List<List<String>> upload(@RequestParam List<MultipartFile> file, @RequestParam String userId,
			@RequestParam String folderName) throws IllegalStateException, IOException {
		List<String> fileName = new ArrayList<>();
		List<String> fileNum = new ArrayList<>();
		List<List<String>> fileNumAndName = new ArrayList<>();

		for (int i = 0; i < file.size(); i++) {

			fileName.add(System.currentTimeMillis() + file.get(i).getOriginalFilename());
			fileNum.add(file.get(i).getOriginalFilename());
			File f = new File(UPLOAD_DIR + userId + "/" + folderName + "/" + fileName.get(i));
			file.get(i).transferTo(f);

		}

		fileNumAndName.add(fileName);
		fileNumAndName.add(fileNum);
		return fileNumAndName;
	}

	// 포토북 새폴더 만들기ajax
	@RequestMapping(value = "/newfolder", method = RequestMethod.POST)
	public @ResponseBody boolean newFoler(@RequestParam String userId, @RequestParam String name,
			HttpServletRequest request) throws IllegalStateException, IOException {
		name = name.replace(" ", "").replace(".", "");
		File dir = new File(UPLOAD_DIR + userId + "/" + name + "/");
		boolean result = false;
		int insertResult=0;
		if (!dir.isDirectory()) {
			//insertResult = ps.insertFolderName(userId, name);
			if (insertResult == 1)// 디렉토리가 없으면 생성
				result = dir.mkdirs();
		}
		return result;
	}

	// 포토북 아이디로 지정된 폴더들 가져오기 ajax
	@RequestMapping(value = "/loadfolder", method = RequestMethod.POST)
	public @ResponseBody List<List<String>> loadfolder(@RequestParam String userId, @RequestParam String folderName,
			Model model, HttpSession session, HttpServletRequest request) throws IllegalStateException, IOException {
		String path;
		File dir = new File(UPLOAD_DIR + userId + "/");
		if (!dir.isDirectory()) {
			// 디렉토리가 없으면 생성
			dir.mkdirs();
		}

		if (folderName != null && !folderName.equals("")) {
			path = UPLOAD_DIR + userId + "/" + folderName + "/";
		} else {
			path = UPLOAD_DIR + userId + "/";
		}
		File dirFile = new File(path);
		File[] fileList = dirFile.listFiles();

		List<String> files = new ArrayList<>();
		List<String> folders = new ArrayList<>();
		List<List<String>> filesAndFolders = new ArrayList<>();
		for (int i = 0; i < fileList.length; i++) {
			int pos = fileList[i].getName().lastIndexOf(".");
			String ext = fileList[i].getName().substring(pos + 1);
			if (ext.equals("jpg") || ext.equals("png") || ext.equals("gif") || ext.equals("bmp")
					||ext.equals("JPG") || ext.equals("PNG") || ext.equals("GIF") || ext.equals("BMP")) {
				files.add(fileList[i].getName());
			} else if (ext.equals("zip")) {

			} else {
				folders.add(fileList[i].getName());
			}
		}

		filesAndFolders.add(files);
		filesAndFolders.add(folders);
		return filesAndFolders;
	}

	@RequestMapping(value = "/delete", method = RequestMethod.POST)
	public @ResponseBody boolean delete(@RequestParam String pathname, HttpServletRequest request)
			throws IllegalStateException, IOException {
		// 넘어올때 인코딩 형식을 다르게 받아줘야함..
		pathname = URLDecoder.decode(pathname, "UTF-8");
		pathname = pathname.replace("/photo_upload/", UPLOAD_DIR);
		pathname = pathname.replace("/", "\\");
		logger.trace("{}", pathname);
		boolean resultMessage = false;

		File f = new File(pathname);
		if (f.delete()) {
			resultMessage = true;
		}
		return resultMessage;
	}

	@RequestMapping(value = "/deleteFolder", method = RequestMethod.POST)
	public @ResponseBody boolean deleteFolder(@RequestParam String pathname, @RequestParam String curUserId,
			HttpServletRequest request) throws IllegalStateException, IOException {
		// 접속중인 폴더주인이름
		curUserId = URLDecoder.decode(curUserId, "UTF-8");
		// 폴더이름
		pathname = URLDecoder.decode(pathname, "UTF-8");
		String realPath = UPLOAD_DIR + curUserId + "/" + pathname;
		logger.trace("{}", realPath);

		boolean resultMessage = false;

		File f = new File(realPath);
		if (f.delete()) {
			resultMessage = true;
		}
		return resultMessage;
	}

	//알집으로 폴더다운로드
	@RequestMapping(value = "/zipdown", method = RequestMethod.POST, produces = "text/plain;charset=UTF-8")
	public @ResponseBody String zip(@RequestParam String pathname
			,@RequestParam String curUserId)
			throws UnsupportedEncodingException {
		String zipFile = pathname + ".zip";
		
		
		
		File dirFile = new File(UPLOAD_DIR+curUserId + "/" +pathname);
		File[] fileList = dirFile.listFiles();
		// 파일리스트 실제 폴더내의것을 가져온다.
		String[] files = new String[fileList.length];
		
		for(int i=0;i<fileList.length;i++){
			logger.trace("filename : {}",fileList[i].getPath());
			files[i]=fileList[i].getPath();
		}
		
		
		byte[] buffer = new byte[1024];
		try {
			// 알집생성
			ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(UPLOAD_DIR + zipFile));

			for (int i = 0; i < files.length; i++) {
				FileInputStream fin = new FileInputStream(files[i]);
				zout.putNextEntry(new ZipEntry(files[i]));

				// 바이트전송
				int length;
				while ((length = fin.read(buffer)) > 0) {
					zout.write(buffer, 0, length);
				}
				zout.closeEntry();

				fin.close();
			}

			zout.close();

			System.out.println("Zip file has been created!");

		} catch (IOException ioe) {
			System.out.println("IOException :" + ioe);
		}

		logger.trace("{}", zipFile);

		return zipFile;
	}
}
