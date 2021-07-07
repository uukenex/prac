package my.prac.api.board.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import my.prac.core.dto.Editor;

@Controller
public class EditorController {
	static Logger logger = LoggerFactory.getLogger(EditorController.class);

	@RequestMapping("/multiplePhotoUpload")
	public void multiplePhotoUpload(HttpServletRequest request, HttpServletResponse response) {

		try {
			// 파일정보
			String sFileInfo = "";
			// 파일명을 받는다 - 일반 원본파일명
			String filename = request.getHeader("file-name");
			// 파일 확장자
			String filename_ext = filename.substring(filename.lastIndexOf(".") + 1);
			// 확장자를소문자로 변경
			filename_ext = filename_ext.toLowerCase();
			// 파일 기본경로
			String dftFilePath = request.getSession().getServletContext().getRealPath("/");
			// 파일 기본경로 _ 상세경로
			// String filePath = dftFilePath + "smarteditor" +
			// File.separator+"Temp" + File.separator;
			String filePath = "/img/dev2" + File.separator + "Temp" + File.separator;
			logger.info("상세 파일경로 : {}", filePath);
			/*
			 * filePath=
			 * "c:"+File.separator+"Users"+File.separator+"1-718-8"+File.
			 * separator+"git"+File.separator+"academi"+File.separator+"academi"
			 * +File.separator+"src"+File.separator+"main"+File.separator+
			 * "webapp"+File.separator+"photo_upload";
			 */
			// logger.trace("상세 파일경로 : {}",filePath);
			File file = new File(filePath);
			if (!file.exists()) {
				logger.info("상세 파일경로생성 : {}", file);
				file.mkdirs();
			}
			String realFileNm = "";
			SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
			String today = formatter.format(new java.util.Date());
			realFileNm = today + UUID.randomUUID().toString() + filename.substring(filename.lastIndexOf("."));
			String rlFileNm = filePath + realFileNm;
			///////////////// 서버에 파일쓰기 /////////////////
			InputStream is = request.getInputStream();
			File newFile = new File(rlFileNm);
			OutputStream os = new FileOutputStream(rlFileNm);
			int numRead;
			byte b[] = new byte[Integer.parseInt(request.getHeader("file-size"))];
			while ((numRead = is.read(b, 0, b.length)) != -1) {
				os.write(b, 0, numRead);
			}
			if (is != null) {
				is.close();
			}
			os.flush();
			os.close();
			///////////////// 서버에 파일쓰기 /////////////////
			// 정보 출력
			sFileInfo += "&bNewLine=true";
			// img 태그의 title 속성을 원본파일명으로 적용시켜주기 위함
			sFileInfo += "&sFileName=" + filename;
			sFileInfo += "&sFileURL=" + "/imgServer/" + realFileNm;
			// sFileInfo += "&sFileURL="+"/img/dev2/Temp/"+realFileNm;
			// 가상경로는 스프링 폴더 내에 만듬
			logger.info("가상경로 : {}  : 에 파일쓰기", sFileInfo);
			// logger.info("가상경로 : {} : 에 파일쓰기","/img/dev2/Temp/"+realFileNm);
			PrintWriter print = response.getWriter();
			print.print(sFileInfo);
			print.flush();
			print.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// http://www.gnujava.com/board/article_view.jsp?board_no=3&article_no=7801
	@RequestMapping("/file_uploader")
	public String normal_file_uploader2(HttpServletRequest request, HttpServletResponse response, Editor editor) {
		String return1 = request.getParameter("callback");
		String return2 = "?callback_func=" + request.getParameter("callback_func");
		String return3 = "";
		String name = "";
		try {
			if (editor.getFiledata() != null && editor.getFiledata().getOriginalFilename() != null
					&& !editor.getFiledata().getOriginalFilename().equals("")) {
				// 기존 상단 코드를 막고 하단코드를 이용
				name = editor.getFiledata().getOriginalFilename()
						.substring(editor.getFiledata().getOriginalFilename().lastIndexOf(File.separator) + 1);
				String filename_ext = name.substring(name.lastIndexOf(".") + 1);
				filename_ext = filename_ext.toLowerCase();
				String[] allow_file = { "jpg", "png", "bmp", "gif" };
				int cnt = 0;
				for (int i = 0; i < allow_file.length; i++) {
					if (filename_ext.equals(allow_file[i])) {
						cnt++;
					}
				}
				if (cnt == 0) {
					return3 = "&errstr=" + name;
				} else {
					// 파일 기본경로
					String dftFilePath = request.getSession().getServletContext().getRealPath("/");
					// 파일 기본경로 _ 상세경로
					String filePath = "/img/dev2" + File.separator + "Temp" + File.separator;
					File file = new File(filePath);
					if (!file.exists()) {
						file.mkdirs();
					}
					String realFileNm = "";
					SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
					String today = formatter.format(new java.util.Date());
					realFileNm = today + UUID.randomUUID().toString() + name.substring(name.lastIndexOf("."));
					String rlFileNm = filePath + realFileNm;
					///////////////// 서버에 파일쓰기 /////////////////
					editor.getFiledata().transferTo(new File(rlFileNm));
					///////////////// 서버에 파일쓰기 /////////////////
					return3 += "&bNewLine=true";
					return3 += "&sFileName=" + name;
					return3 += "&sFileURL=/imgServer/" + realFileNm;
				}
			} else {
				return3 += "&errstr=error";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "redirect:" + return1 + return2 + return3;
	}

	@RequestMapping("/se2_callback")
	public String nomalPhotoUploadCallback(HttpServletRequest request, HttpServletResponse response) {
		return "common/se2_callback";
	}

}
