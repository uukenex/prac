package my.prac.core.prjuser.service.impl;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.dto.Users;
import my.prac.core.prjuser.dao.UserDAO;
import my.prac.core.prjuser.service.UserService;

@Service("core.prjuser.UserService")
public class UserServiceImpl implements UserService {
	@Resource(name = "core.prjuser.UserDAO")
	UserDAO urepo;

	// insert처리 - 회원가입 서비스
	@Override
	public int joinUser(Users user) {
		return urepo.insert(user);
	}

	// 로그인처리 - 로그인 서비스,사용자 모든정보 받아옴
	@Override
	public Users login(String userId) {
		return urepo.selectById(userId);
	}

	// 아이디 찾기 -이름과 Email로 아이디 찾기 서비스
	@Override
	public List<String> SearchId(String userName, String userEmail) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("userName", userName);
		map.put("userEmail", userEmail);
		return urepo.selectByNameAndEmail(map);
	}

	// 비번찾기 - id,이름과 Email로 비번 찾기 서비스
	@Override
	public String SearchPass(String userId, String userName, String userEmail) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("userName", userName);
		map.put("userEmail", userEmail);
		map.put("userId", userId);
		return urepo.selectPass(map);
	}

	// 정보변경 - 정보 변경 서비스 .비밀번호, 연락처, 이메일, 닉네임을 변경
	@Override
	public int updateUser(String userId, String userPass, String userPhone, String userEmail, String userNick) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("userPass", userPass);
		map.put("userEmail", userEmail);
		map.put("userId", userId);
		map.put("userPhone", userPhone);
		map.put("userNick", userNick);
		return urepo.updateUser(map);
	}

	@Override
	public int checkId(String userId) {
		return urepo.checkId(userId);
	}

	@Override
	public int checkNick(String userNick) {
		return urepo.checkNick(userNick);
	}

	@Override
	public String searchNickById(String userId) {
		return urepo.searchNickById(userId);
	}

	@Override
	public int insertUsertracking(HashMap<String, Object> hashMap) {
		return urepo.insertUsertracking(hashMap);
	}

	@Override
	public int updatePass(String userId, String userPass) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("userPass", userPass);
		map.put("userEmail", userId);
		return urepo.updatePass(map);
	}

}
