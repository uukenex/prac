package my.prac.api.board.controller;

import java.util.HashMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.prjuser.dao.UserDAO;

public class DatabaseController{
	static Logger logger = LoggerFactory.getLogger(DatabaseController.class);
	public static boolean dbcheckval = false;
	@Autowired
	UserDAO dao;
	
	@PostConstruct
	void DatabaseController(){
		try{
			HashMap map = new HashMap();
			map.put("userId", "server");
			
			dao.insertUsertracking(map);
			dbcheckval = true;
		}catch(Exception e){
			dbcheckval = false;
		}
		finally{
			logger.info("dbcheckval:::"+dbcheckval);
		}
	}
	
}
