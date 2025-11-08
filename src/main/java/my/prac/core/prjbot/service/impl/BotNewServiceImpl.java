package my.prac.core.prjbot.service.impl;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;
import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.dao.BotNewDAO;
import my.prac.core.prjbot.dao.BotSettleDAO;
import my.prac.core.prjbot.service.BotNewService;

@Service("core.prjbot.BotNewService")
public class BotNewServiceImpl implements BotNewService {

	@Resource(name = "core.prjbot.BotDAO")
	BotDAO botDAO;
	@Resource(name = "core.prjbot.BotNewDAO")
	BotNewDAO botNewDAO;

	@Resource(name = "core.prjbot.BotSettleDAO")
	BotSettleDAO botSettleDAO;

	public List<String> selectParam1ToNewUserSearch(HashMap<String,Object> map){
		return botNewDAO.selectParam1ToNewUserSearch(map);
	}
	
	public int insertBotPointNew(HashMap<String, Object> map) {
		return botNewDAO.insertBotPointNew(map);
	}

	@Override
    public User selectUser(String userName, String roomName) {
        return botNewDAO.selectUser(userName, roomName);
    }

    @Override
    public OngoingBattle selectOngoingBattle(String userName, String roomName) {
        return botNewDAO.selectOngoingBattle(userName, roomName);
    }
    
    @Override
    public AttackDeathStat selectAttackDeathStats(String userName, String roomName) {
        return botNewDAO.selectAttackDeathStats(userName, roomName);
    }

    @Override
    public List<KillStat> selectKillStats(String userName, String roomName){
    	return botNewDAO.selectKillStats(userName,roomName);
    }
    
    @Override
    public List<Monster> selectAllMonsters(){
    	return botNewDAO.selectAllMonsters();
    }
    @Override
    public Monster selectMonsterByNo(int monNo) {
        return botNewDAO.selectMonsterByNo(monNo);
    }
    @Override
    public Monster selectMonsterByName(String monName) {
    	return botNewDAO.selectMonsterByName(monName);
    }

    @Override
    public Timestamp selectLastAttackTime(String userName, String roomName) {
        return botNewDAO.selectLastAttackTime(userName, roomName);
    }

    @Override
    public int updateUserAfterBattleTx(String userName, String roomName, int newLv, int newExpCur, int newExpNext,
                                     int newHpCur, int newHpMax, int newAtkMin, int newAtkMax,int critRate,int hpRegen ) {
        return botNewDAO.updateUserAfterBattle(userName, roomName, newLv, newExpCur, newExpNext,
                                            newHpCur, newHpMax, newAtkMin, newAtkMax, critRate,hpRegen );
    }

    @Override
    public int insertBattleLogTx(BattleLog log) {
        return botNewDAO.insertBattleLog(log);
    }

    @Override
    public int closeOngoingBattleTx(String userName, String roomName) {
        return botNewDAO.closeOngoingBattle(userName, roomName);
    }
    @Override
    public int updateUserHpOnlyTx(String userName, String roomName, int newHpCur) {
    	return botNewDAO.updateUserHpOnly(userName, roomName,newHpCur);
    }
    @Override
    public int updateUserTargetMonTx(String userName, String roomName, int newMonNo) {
    	return botNewDAO.updateUserTargetMon(userName, roomName,newMonNo);
    }
    
    @Override
    public int insertUserWithTargetTx(String userName, String roomName, int targetMonNo) {
    	return botNewDAO.insertUserWithTarget(userName, roomName,targetMonNo);
    }
    
    @Override
    public Integer selectLatestLuckyYn(String userName, String roomName) {
        if (userName == null || roomName == null) return null;
        try {
            return botNewDAO.selectLatestLuckyYn(userName, roomName);
        } catch (Exception e) {
            // 안정성: 실패 시 null
            return null;
        }
    }
    
	@Override
	public Integer selectItemIdByCode(String itemCode) {
		return botNewDAO.selectItemIdByCode(itemCode);
	}

	@Override
	public Integer selectItemIdByName(String itemName) {
		return botNewDAO.selectItemIdByName(itemName);
	}

	@Override
	public int insertInventoryLogTx(HashMap<String, Object> p) {
		return botNewDAO.insertInventoryLog(p);
	}

	@Override
	public List<HashMap<String, Object>> selectInventorySummary(String userName, String roomName) {
		return botNewDAO.selectInventorySummary(userName, roomName);
	}
	
	@Override
	public Integer selectCurrentPoint(String userName, String roomName) {
	    return botNewDAO.selectCurrentPoint(userName, roomName);
	}

	@Override
	public int insertPointRank(HashMap<String, Object> p) {
		return botNewDAO.insertPointRank(p);
	}

	@Override
	public Integer selectItemSellPriceById(int itemId) {
		return botNewDAO.selectItemSellPriceById(itemId);
	}

	@Override
	public List<HashMap<String, Object>> selectInventoryRowsForSale(String u, String r, int id) {
		return botNewDAO.selectInventoryRowsForSale(u, r, id);
	}

	@Override
	public int updateInventoryDelByRowId(String rowid) {
		return botNewDAO.updateInventoryDelByRowId(rowid);
	}

	@Override
	public int updateInventoryQtyByRowId(String rowid, int newQty) {
		return botNewDAO.updateInventoryQtyByRowId(rowid, newQty);
	}
	@Override
	public Integer selectInventoryQty(String userName, String roomName, Integer itemId) {
	    return botNewDAO.selectInventoryQty(userName, roomName, itemId);
	}

	
	@Override
	public HashMap<String, Object> selectOwnedItemBuffTotals(String user, String room, String onlyMarket) {
	    return botNewDAO.selectOwnedItemBuffTotals(user, room, onlyMarket);
	}

	@Override
	public List<HashMap<String, Object>> selectInventorySummaryAll(String user, String room) {
	    return botNewDAO.selectInventorySummaryAll(user, room);
	}

	@Override
	public Integer selectItemPriceByName(String itemName) {
	    return botNewDAO.selectItemPriceByName(itemName);
	}
	
	@Override
	public HashMap<String, Number> selectOwnedMarketBuffTotals(String user, String room) {
	    return botNewDAO.selectOwnedMarketBuffTotals(user, room);
	}
	@Override
	public HashMap<String, Number> selectOwnedAllBuffTotals(String user, String room) {
	    return botNewDAO.selectOwnedAllBuffTotals(user, room);
	}
	@Override
    public List<HashMap<String, Object>> selectMarketItemsForSale(String userName, String roomName) {
        return botNewDAO.selectMarketItemsForSale(userName, roomName);
    }
    @Override
    public HashMap<String, Object> selectMarketItemById(Integer itemId) {
        return botNewDAO.selectMarketItemById(itemId);
    }
    @Override
    public HashMap<String, Object> selectMarketItemByNameOrCode(String token) {
        return botNewDAO.selectMarketItemByNameOrCode(token);
    }
    @Override
    public Integer countOwnedMarketItem(String userName, String roomName, Integer itemId) {
        return botNewDAO.countOwnedMarketItem(userName, roomName, itemId);
    }
    
    @Override
    public List<HashMap<String, Object>> selectMarketItems() {
        return botNewDAO.selectMarketItems();
    }

    @Override
    public HashMap<String, Object> selectItemDetailById(int itemId) {
        return botNewDAO.selectItemDetailById(itemId);
    }
    
    // BotNewServiceImpl
    @Override
    public List<HashMap<String,Object>> selectMarketItemsWithOwned(String userName, String roomName) {
        return botNewDAO.selectMarketItemsWithOwned(userName, roomName);
    }
    @Override
    public Integer selectHasOwnedMarketItem(String userName, String roomName, Integer itemId) {
        return botNewDAO.selectHasOwnedMarketItem(userName, roomName, itemId);
    }
    
    @Override
    public Timestamp selectLastDamagedTime(String userName, String roomName) {
        return botNewDAO.selectLastDamagedTime(userName, roomName);
    }

    

    @Override
    public List<HashMap<String, Object>> selectTopLevelUsers() {
        return botNewDAO.selectTopLevelUsers();
    }
    @Override
    public List<HashMap<String, Object>> selectKillLeadersByMonster() {
        return botNewDAO.selectKillLeadersByMonster();
    }
    @Override
    public List<HashMap<String, Object>> selectFirstClearInfo() {
        return botNewDAO.selectFirstClearInfo();
    }
    
    @Override
    public int selectPointRankCountByCmdGlobal(String cmd) {
    	return botNewDAO.selectPointRankCountByCmdGlobal(cmd);
    }

    @Override
    public int selectPointRankCountByCmdUserInRoom(String roomName, String userName, String cmd) {
    	return botNewDAO.selectPointRankCountByCmdUserInRoom(roomName, userName, cmd);
    }
    @Override
    public List<HashMap<String,Object>> selectAchievementsByUser(String userName) {
        return botNewDAO.selectAchievementsByUser(userName);
    }
    
    @Override
    public void updateUserJob(String userName, String roomName, String job) {
    	botNewDAO.updateUserJob(userName,roomName,job);
    }
    
    @Override
    public void updateUserStatsForWarrior(String userName, String roomName,
                                          int hpMax, int atkMin, int atkMax) {
        botNewDAO.updateUserStatsForWarrior(userName, roomName, hpMax, atkMin, atkMax);
    }
}
