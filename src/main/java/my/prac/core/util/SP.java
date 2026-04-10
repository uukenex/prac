package my.prac.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SP {
	private double value;
	private String unit;

	public double getValue() {
		return value;
	}

	public String getUnit() {
		return unit;
	}

	public SP(double value, String unit) {
		this.value = value;
		this.unit = unit == null ? "" : unit;
	}
	
	public static SP of(double value, String unit){

	    SP sp = new SP(value, unit);

	    return sp.normalize();
	}

	@Override
	public String toString() {
		return String.format("%.2f %s", value, unit);
	}

	public String format() {

		return String.format("%.2f %s", value, unit);
	}

	public SP normalize() {

		BigDecimal v = BigDecimal.valueOf(this.value).setScale(6, RoundingMode.HALF_UP);

		BigDecimal base = BigDecimal.valueOf(10000);

		int idx = unitIndex(this.unit);

		while (v.compareTo(base) >= 0) {

			v = v.divide(base);
			idx++;
		}
		// 하향 (1 미만)
		while (v.compareTo(BigDecimal.ONE) < 0 && idx > 0) {
			v = v.multiply(base);
			idx--;
		}

		String newUnit = (idx == 0) ? "" : String.valueOf((char) ('a' + idx - 1));

		return new SP(v.doubleValue(), newUnit);  
	}

	public SP multiply(double m) {

		BigDecimal v = BigDecimal.valueOf(this.value);

		v = v.multiply(BigDecimal.valueOf(m)).setScale(6, RoundingMode.HALF_UP);;

		return SP.of(v.doubleValue(), this.unit);
	}
	public SP multiplyRate(double rate) {

	    double baseValue = toBaseValue(this);

	    double result = baseValue * rate;

	    return SP.fromSp(result);
	}
	public static long toBaseValue(SP sp) {

	    double v = sp.value;

	    int idx = (sp.unit == null || sp.unit.isEmpty())
	            ? 0
	            : sp.unit.charAt(0) - 'a' + 1;

	    return (long) (v * Math.pow(10000, idx));
	}

	public SP divide(double d) {

		BigDecimal v = BigDecimal.valueOf(this.value);

		v = v.divide(BigDecimal.valueOf(d), 6, RoundingMode.HALF_UP);

		return SP.of(v.doubleValue(), this.unit);
	}

	public static SP fromSp(double sp) {

		BigDecimal v = BigDecimal.valueOf(sp);
		BigDecimal base = BigDecimal.valueOf(10000);

		int unitIndex = 0;

		while (v.compareTo(base) >= 0) {
			v = v.divide(base);
			unitIndex++;
		}

		String unit = unitIndex == 0 ? "" : String.valueOf((char) ('a' + unitIndex - 1));

		return SP.of(v.doubleValue(), unit);
	}

	// -----------------------------
	// unit → index 변환
	// -----------------------------
	private int unitIndex(String u) {

		if (u == null || u.equals(""))
			return 0;

		return u.charAt(0) - 'a' + 1;
	}

	// -----------------------------
	// 비교
	// -----------------------------
	public int compare(SP other) {

		BigDecimal v1 = BigDecimal.valueOf(this.value);
		BigDecimal v2 = BigDecimal.valueOf(other.value);

		int idx1 = unitIndex(this.unit);
		int idx2 = unitIndex(other.unit);

		int diff = idx1 - idx2;

		BigDecimal base = BigDecimal.valueOf(10000);

		if (diff > 0) {
			v2 = v2.divide(base.pow(diff));
		} else if (diff < 0) {
			v1 = v1.divide(base.pow(-diff));
		}

		return v1.compareTo(v2);
	}

	// -----------------------------
	// 구매 가능 여부
	// -----------------------------
	public boolean canAfford(SP price) {
		return compare(price) >= 0;
	}

	// -----------------------------
	// 덧셈
	// -----------------------------
	public SP add(SP other) {

	    int idx1 = unitIndex(this.unit);
	    int idx2 = unitIndex(other.unit);

	    BigDecimal v1 = BigDecimal.valueOf(this.value);
	    BigDecimal v2 = BigDecimal.valueOf(other.value);

	    BigDecimal base = BigDecimal.valueOf(10000);

	    BigDecimal result;
	    int resultIdx;

	    if (idx1 > idx2) {
	        v2 = v2.divide(base.pow(idx1 - idx2));
	        result = v1.add(v2);
	        resultIdx = idx1;
	    } else {
	        v1 = v1.divide(base.pow(idx2 - idx1));
	        result = v1.add(v2);
	        resultIdx = idx2;
	    }

	    result = result.setScale(6, RoundingMode.HALF_UP);

	    // 결과를 현재 객체에 반영
	    this.value = result.doubleValue();
	    this.unit = resultIdx == 0 ? "" : String.valueOf((char) ('a' + resultIdx - 1));

	    return this.normalize();
	}

	// -----------------------------
	// 뺄셈
	// -----------------------------
	public SP subtract(SP other) {

		int idx1 = unitIndex(this.unit);
		int idx2 = unitIndex(other.unit);

		BigDecimal v1 = BigDecimal.valueOf(this.value);
		BigDecimal v2 = BigDecimal.valueOf(other.value);

		BigDecimal base = BigDecimal.valueOf(10000);

		BigDecimal result;
		int resultIdx;

		if (idx1 > idx2) {
			v2 = v2.divide(base.pow(idx1 - idx2));
			result = v1.subtract(v2);
			resultIdx = idx1;
		} else {
			v1 = v1.divide(base.pow(idx2 - idx1));
			result = v1.subtract(v2);
			resultIdx = idx2;
		}

		result = result.setScale(6, RoundingMode.HALF_UP);

		this.value = result.doubleValue();
		this.unit = resultIdx == 0 ? "" : String.valueOf((char) ('a' + resultIdx - 1));

		return this.normalize();
	}

	public static SP parse(String str) {

		if (str == null || str.trim().isEmpty()) {
			return SP.of(0, "");
		}

		String[] p = str.trim().split("\\s+");

		double v = Double.parseDouble(p[0]);
		String u = (p.length > 1) ? p[1] : "";

		return SP.of(v, u);
	}

	public boolean lessThan(SP other) {
		return compare(other) < 0;
	}
}
