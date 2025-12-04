package my.prac.core.ext.dto;

public class MerchantReportVO {

    private int serverId;          // 예: 5 (카단)
    private java.util.Date startTimeKst;
    private java.util.Date endTimeKst;
    private int regionId;
    private int itemId;
    private String timeVal;  

    public java.util.Date getEndTimeKst() {
		return endTimeKst;
	}
	public void setEndTimeKst(java.util.Date endTimeKst) {
		this.endTimeKst = endTimeKst;
	}
	public String getTimeVal() {
		return timeVal;
	}
	public void setTimeVal(String timeVal) {
		this.timeVal = timeVal;
	}
	// getter/setter
    public int getServerId() {
        return serverId;
    }
    public void setServerId(int serverId) {
        this.serverId = serverId;
    }
    public java.util.Date getStartTimeKst() {
        return startTimeKst;
    }
    public void setStartTimeKst(java.util.Date startTimeKst) {
        this.startTimeKst = startTimeKst;
    }
    public int getRegionId() {
        return regionId;
    }
    public void setRegionId(int regionId) {
        this.regionId = regionId;
    }
    public int getItemId() {
        return itemId;
    }
    public void setItemId(int itemId) {
        this.itemId = itemId;
    }
}