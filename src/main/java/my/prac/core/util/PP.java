package my.prac.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * [시즌5] 탑 등반 시스템 전용 수치 클래스.
 * my.prac.core.util.SP 를 그대로 복제한 것으로, 기존 시즌(SP)과 완전히 분리해서 쓰기 위해 별도 클래스로 둔다.
 * PP(재화) 뿐 아니라 몬스터/동료의 HP·ATK·DEF 등 큰 수치 표기에도 동일하게 사용한다.
 * 1만 = 1a, 1만a = 1b ... 형태로 자동 정규화된다.
 */
public class PP {
	private double value;
	private String unit;

	public double getValue() {
		return value;
	}

	public String getUnit() {
		return unit;
	}

	public PP(double value, String unit) {
		this.value = value;
		this.unit = unit == null ? "" : unit;
	}

	public static PP of(double value, String unit){

	    PP pp = new PP(value, unit);

	    return pp.normalize();
	}

	@Override
	public String toString() {
		PP n = normalize();
		return n.unit.isEmpty()
				? String.format("%.2f", n.value)
				: String.format("%.2f%s", n.value, n.unit);
	}

	public String format() {
		PP n = normalize();
		return n.unit.isEmpty()
				? String.format("%.2f", n.value)
				: String.format("%.2f%s", n.value, n.unit);
	}

	public PP normalize() {

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

		return new PP(v.doubleValue(), newUnit);
	}

	public PP multiply(double m) {

		BigDecimal v = BigDecimal.valueOf(this.value);

		v = v.multiply(BigDecimal.valueOf(m)).setScale(6, RoundingMode.HALF_UP);;

		return PP.of(v.doubleValue(), this.unit);
	}
	public PP multiplyRate(double rate) {

	    double baseValue = toBaseValue(this);

	    double result = baseValue * rate;

	    return PP.fromPP(result);
	}
	public static long toBaseValue(PP pp) {

	    double v = pp.value;

	    int idx = (pp.unit == null || pp.unit.isEmpty())
	            ? 0
	            : pp.unit.charAt(0) - 'a' + 1;

	    return (long) (v * Math.pow(10000, idx));
	}

	public PP divide(double d) {

		BigDecimal v = BigDecimal.valueOf(this.value);

		v = v.divide(BigDecimal.valueOf(d), 6, RoundingMode.HALF_UP);

		return PP.of(v.doubleValue(), this.unit);
	}

	public static PP fromPP(double pp) {

		BigDecimal v = BigDecimal.valueOf(pp);
		BigDecimal base = BigDecimal.valueOf(10000);

		int unitIndex = 0;

		while (v.compareTo(base) >= 0) {
			v = v.divide(base);
			unitIndex++;
		}

		String unit = unitIndex == 0 ? "" : String.valueOf((char) ('a' + unitIndex - 1));

		return PP.of(v.doubleValue(), unit);
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
	public int compare(PP other) {

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
	public boolean canAfford(PP price) {
		return compare(price) >= 0;
	}

	// -----------------------------
	// 덧셈
	// -----------------------------
	public PP add(PP other) {

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
	public PP subtract(PP other) {

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

	public static PP parse(String str) {

		if (str == null || str.trim().isEmpty()) {
			return PP.of(0, "");
		}

		String s = str.trim();
		String[] p = s.split("\\s+");

		if (p.length >= 2) {
			double v = Double.parseDouble(p[0]);
			String u = p[1];
			return PP.of(v, u);
		}

		// "26.22b" 형태 (공백 없이 단위가 붙은 경우)
		int i = p[0].length() - 1;
		while (i >= 0 && Character.isLetter(p[0].charAt(i))) i--;
		double v = Double.parseDouble(p[0].substring(0, i + 1));
		String u = p[0].substring(i + 1);
		return PP.of(v, u);
	}

	public boolean lessThan(PP other) {
		return compare(other) < 0;
	}
}
