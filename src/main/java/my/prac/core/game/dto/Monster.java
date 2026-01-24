package my.prac.core.game.dto;

public class Monster {
	public int monNo;
	public int monHp;
	public int monAtk;
	public int monExp;
	public String monDrop;
	public int monPatten;
	public String monName;
	public String monNote;
	public int monLv;
	
	// ✅ 복사 생성자 (필수)
    public Monster(Monster src) {
        this.monNo = src.monNo;
        this.monHp = src.monHp;
        this.monAtk = src.monAtk;
        this.monExp = src.monExp;
        this.monDrop = src.monDrop;
        this.monPatten = src.monPatten;
        this.monName = src.monName;
        this.monNote = src.monNote;
        this.monLv = src.monLv;
    }

    public Monster() {}
}