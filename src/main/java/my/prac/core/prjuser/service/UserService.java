package my.prac.core.prjuser.service;

import java.util.HashMap;
import java.util.List;

import my.prac.core.dto.Users;

public interface UserService {
	// insert처리 - 회원가입 서비스 . 결과가 몇개인지 리턴
	public int joinUser(Users user);

	// 로그인처리 - 로그인 서비스,사용자 모든정보 받아옴
	public Users login(String userId);

	// 아이디 찾기 -이름과 Email로 아이디 찾기 서비스
	public List<String> SearchId(String userName, String userEmail);

	// 비번찾기 - id,이름과 Email로 비번 찾기 서비스
	public String SearchPass(String userId, String userName, String userEmail);

	// 정보변경 - 정보 변경 서비스 .비밀번호, 연락처, 이메일, 닉네임을 변경
	public int updateUser(String userId, String userPass, String userPhone, String userEmail, String userNick);

	// id중복검색
	public int checkId(String userId);

	// 닉네임 중복검색
	public int checkNick(String userNick);

	// id로 닉네임을 찾음
	public String searchNickById(String userId);

	public int updatePass(String userId, String userPass);

	public int insertUsertracking(HashMap<String, Object> hashMap);
}
