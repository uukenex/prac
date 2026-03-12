package my.prac.core.util;

public class SellResult {

	public int sold;
	public SP total;

    public SellResult(int sold, SP total) {
        this.sold = sold;
        this.total = total;
    }

    public static SellResult empty() {
        return new SellResult(0, SP.of(0,""));
    }
}
