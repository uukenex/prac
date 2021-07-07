package my.prac.core.dto;

import org.springframework.web.multipart.MultipartFile;

public class Editor extends AbstractModel {
	private static final long serialVersionUID = 1L;
	private MultipartFile Filedata;

	public MultipartFile getFiledata() {
		return Filedata;
	}

	public void setFiledata(MultipartFile filedata) {
		Filedata = filedata;
	}
}
