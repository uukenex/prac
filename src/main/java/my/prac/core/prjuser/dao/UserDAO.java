package my.prac.core.prjuser.dao;

import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Repository;

import my.prac.core.dto.Users;

@Repository("core.prjuser.UserDAO")
public interface UserDAO {
	// insert처리
	public int insert(Users user);

	// 로그인처리
	public Users selectById(String userId);

	// 아이디 찾기
	public List<String> selectByNameAndEmail(HashMap<String, Object> hashMap);

	// 비번찾기
	public String selectPass(HashMap<String, Object> hashMap);

	// 정보변경
	public int updateUser(HashMap<String, Object> hashMap);

	// id 중복확인
	public int checkId(String userId);

	// 닉네임 중복확인
	public int checkNick(String userNick);

	// id로 닉네임을 찾음
	public String searchNickById(String userId);

	public int updatePass(HashMap<String, Object> hashMap);

	public int insertUsertracking(HashMap<String, Object> hashMap);

	public int updateLastLoginDate(HashMap<String, Object> hashMap);

	// [2026-10-08] 게시판 카카오 로그인 -- 회원 등록/갱신(MERGE)과 게시판 사용자 ID 조회
	public int mergeBoardKakaoMember(HashMap<String, Object> hashMap);

	public String selectBoardKakaoUserId(String kakaoId);
}
