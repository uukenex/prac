package my.prac.core.games;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

public class Game {

	public static final int CELL_SIZE = 4;// create(cell) -> cell * cell
	public static final int GAME_OVER_COUNT = 9;
	public static final int ITEM_COUNT = 4;
	int cur_item_count = 0;
	int enemyResponTime = 2000;
	int cur_point = 0;

	public static void main(String args[]) {
		Game game = new Game();
	}

	public Game() {
		JButton btnArr[][] = createCell(CELL_SIZE);// create(cell) -> cell *
													// cell 으로 생성
		createEnemy(btnArr, 1);
		createEnemyCountChk(btnArr);
	}

	public JButton[][] createCell(int bxCnt) {
		JFrame f = new JFrame("Game");

		int startX = 20;// x 시작좌표
		int startY = 20;// y 시작좌표
		int x = startX; // x 현재좌표 임시변수
		int y = startY; // y 현재좌표 임시변수
		int boxSize = 30;// 셀 사이즈
		int blankSize = 5;// 셀 사이 공간 사이즈
		int boxCount = bxCnt;// 2x2 인지 3x3인지 정하는 변수

		int frameXsize = bxCnt * (boxSize + blankSize) - blankSize + startX * 3;
		int frameYsize = bxCnt * (boxSize + blankSize) - blankSize + startY * 4;

		JButton[][] btn = new JButton[boxCount][boxCount];

		for (int i = 0; i < boxCount; i++) {
			for (int j = 0; j < boxCount; j++) {

				btn[i][j] = new JButton(); // 버튼 생성
				btn[i][j].setName("btn" + i + j);
				btn[i][j].setBounds(x, y, boxSize, boxSize);
				btn[i][j].setBackground(Color.WHITE);
				btn[i][j].addMouseListener(new BtnRightAction(btn[i][j]));

				f.add(btn[i][j]);
				x += boxSize + blankSize;
			}
			x = startX;
			y += boxSize + blankSize;
		}

		JLabel jl1 = new JLabel("우클릭 사용가능수 : " + ITEM_COUNT);
		jl1.setBounds(x, y + 10, frameXsize, 20);
		JLabel jl2 = new JLabel("적 생성시간 : " + enemyResponTime / 1000);
		jl2.setBounds(x, y + 30, frameXsize, 20);
		f.add(jl1);
		// f.add(jl2);

		f.setSize(frameXsize, frameYsize + 40);
		f.setLayout(null);
		f.setVisible(true);
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		return btn;
	}

	public void createEnemy(JButton[][] jbArr, int level) {

		Timer timer = new Timer();
		int timeAttackCnt = 8;
		TimerTask timerTask = new TimerTask() {
			int createCnt = 0;

			@Override
			public void run() {
				Random rd = new Random();
				JButton[][] btn = jbArr;
				boolean chk = false;
				int rdX = 0;
				int rdY = 0;

				for (int i = 0; i < level; i++) {
					while (!chk) {
						rdX = rd.nextInt(CELL_SIZE);
						rdY = rd.nextInt(CELL_SIZE);
						// 흰색 셀 중에서만 선택하도록
						if (btn[rdX][rdY].getBackground() == Color.WHITE) {
							chk = false;
							break;
						}
					}

					if (!chk) {
						btn[rdX][rdY].setBackground(Color.BLACK);
						createCnt++;
					}
				}

				if (timeAttackCnt == createCnt) {
					enemyResponTime = enemyResponTime / 2;
					// System.out.println("타임어택"+enemyResponTime);
					timer.cancel();
					createEnemy(jbArr, 1);
					return;
				}
			}

		};

		timer.schedule(timerTask, enemyResponTime, enemyResponTime);

	}

	public void createEnemyCountChk(JButton[][] jbArr) {
		Timer timer = new Timer();
		TimerTask timerTask = new TimerTask() {
			@Override
			public void run() {
				JButton[][] btn = jbArr;

				int curCnt = 0;

				// 검정 셀의 갯수를 체크, OVER_COUNT 이상일시 종료
				for (int i = 0; i < CELL_SIZE; i++) {
					for (int j = 0; j < CELL_SIZE; j++) {
						if (btn[i][j].getBackground() == Color.BLACK) {
							curCnt++;
						}
					}
				}

				if (curCnt >= GAME_OVER_COUNT) {
					JOptionPane.showMessageDialog(null, cur_point + "점 게임 오버");
					System.exit(0);
				}

			}
		};

		timer.schedule(timerTask, 0, 1000);
	}

	class BtnRightAction implements MouseListener {
		private JButton jb;

		public BtnRightAction(JButton jbutton) {
			jb = jbutton;
		}

		@Override
		public void mouseClicked(MouseEvent e) {

			if (e.getButton() == MouseEvent.BUTTON1) {
				if (jb.getBackground() == Color.BLACK) {
					jb.setBackground(Color.WHITE);
					cur_point++;
				}
			}
			if (e.getButton() == MouseEvent.BUTTON3) {
				// BUTTON3 우클릭 이벤트
				if (jb.getBackground() == Color.WHITE || jb.getBackground() == Color.BLACK) {
					if (cur_item_count < ITEM_COUNT) {
						jb.setBackground(Color.BLUE);
						cur_item_count++;
					}
				} else if (jb.getBackground() == Color.BLUE) {
					jb.setBackground(Color.WHITE);
					cur_item_count--;
				}

			}

		}

		@Override
		public void mousePressed(MouseEvent e) {
			// 클릭 down
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			// 클릭 up
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			// 마우스가 영역 안으로 들어갈때
		}

		@Override
		public void mouseExited(MouseEvent e) {
			// 마우스가 영역 밖으로 나올때
		}

	}

}
