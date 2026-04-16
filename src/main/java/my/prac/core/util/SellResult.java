package my.prac.core.util;

public class SellResult {

	public int sold;
	public SP total;
	public int gpGranted; // 7000번대 보스 아이템 판매 시 획득 GP

    public SellResult(int sold, SP total) {
        this.sold = sold;
        this.total = total;
        this.gpGranted = 0;
    }

    public SellResult(int sold, SP total, int gpGranted) {
        this.sold = sold;
        this.total = total;
        this.gpGranted = gpGranted;
    }

    public static SellResult empty() {
        return new SellResult(0, SP.of(0,""), 0);
    }
}
