<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐탑 등반기</title>
  <style>
    :root{
      --parchment:#FBF3DF; --parchment-deep:#F3E6C4; --ink:#2E2440; --ink-soft:#6B5E82;
      --line:#E5D3A1; --gold:#C68A2E; --gold-soft:#EAD9AC;
      --combat:#D8523F; --combat-soft:#F7DAD2;
      --shop:#6C4E9E; --shop-soft:#E4DAF2;
      --pp:#2F8F5C; --pp-soft:#D6EEDF;
      --trap:#3A3548; --trap-soft:#DAD6E4;
      --special:#D64B87; --special-soft:#F8DCE9;
      --treasure:#C6982E; --treasure-soft:#F2E6C6;
      --elite:#8E1F1F; --elite-soft:#F0D2D2;
      --village:#3C7CB8; --boss:#A31F2B;
      --shadow: 0 3px 0 rgba(46,36,64,.14), 0 10px 22px -12px rgba(46,36,64,.35);
    }
    *,*::before,*::after{box-sizing:border-box;}
    /* 이 페이지는 사이드바(_loa_nav.jsp)를 포함하지 않는 독립 SPA라 그 폭(120px)을
       비워둘 필요가 없다 -- 대신 하단 dock 내비게이션을 쓴다. 그래서 다른 /loa 페이지들과
       달리 margin-left를 두지 않는다(전에 넣었더니 폰/폴더블 펼친 화면처럼 600px보다
       넓은데 아직 데스크톱은 아닌 폭에서 왼쪽에 아무 것도 없는 여백만 남았었음). */
    body{
      margin:0;
      background: radial-gradient(1100px 500px at 50% -8%, #FFFDF6 0%, var(--parchment) 46%, var(--parchment-deep) 100%);
      color:var(--ink); font-family:'Malgun Gothic','Segoe UI',sans-serif;
    }
    .wrap{ max-width:900px; margin:0 auto; padding:20px 16px 215px; display:flex; flex-direction:column; gap:14px; }

    .top-controls{ display:flex; gap:8px; margin-bottom:2px; }
    .top-controls input{ padding:9px 14px; border:1.5px solid var(--line); border-radius:22px; background:#fff; font-size:13px; width:150px; }
    .btn-query{ background:var(--gold); color:#fff; border:none; padding:9px 18px; border-radius:22px; font-size:13px; font-weight:700; cursor:pointer; }
    /* [2026-09-07] "공지 다시 보기" 버튼 -- 항상 보이는 top-controls에 둬서 탭과 무관하게 접근 가능. */
    .btn-notice{ background:#fff; border:1.5px solid var(--line); border-radius:22px; padding:9px 12px;
                 font-size:14px; cursor:pointer; flex-shrink:0; }
    .btn-notice:active{ transform:scale(.95); }

    .card{ background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep)); border:2px solid var(--line);
           border-radius:20px; padding:16px; box-shadow:var(--shadow); }
    .card-title{ font-size:16px; font-weight:800; display:flex; align-items:center; gap:6px; margin-bottom:10px; }

    .statusbar{ display:flex; align-items:center; justify-content:space-between; gap:10px; flex-wrap:wrap; }
    .who-name{ font-size:15px; font-weight:800; }
    .who-floor{ font-size:12px; color:var(--ink-soft); }
    .pp-badge{ display:flex; align-items:baseline; gap:5px; background:var(--pp-soft); border:2px solid #BFE3CD;
               border-radius:999px; padding:7px 14px; }
    .pp-badge .val{ font-size:15px; font-weight:800; color:var(--pp); }

    .panel{ display:none; }
    .panel.active{ display:block; }

    /* 부루마불 스타일: 칸들이 사각형 둘레를 따라 시계방향으로 빙 둘러 배치되는 순환 보드.
       칸 수(15~150)에 따라 정사각형 한 변의 칸수(S)가 달라지므로, 컨테이너 크기와 각 칸의
       left/top은 JS에서 %(퍼센트) 인라인 스타일로 계산해서 넣는다. [2026-09-05] "좌우/상하
       스크롤바가 보인다" 신고로, 칸 수(최대 150)에 따라 트랙 자체가 커지던 방식을 버리고
       뷰포트 크기를 고정(overflow:hidden)한 뒤 칸 크기만 칸 수에 맞춰 줄어들게 했었다.
       [2026-09-13] "칸(200개)이 너무 작아서 글씨가 안 보인다, 세로로 스크롤해도 되니까
       칸 크기를 키우고 안 겹치게 해달라" 요청으로 그 결정을 뒤집는다 -- 가로 스크롤은
       여전히 없지만(가로는 항상 뷰포트 폭에 맞춤), 세로는 이제 overflow-y:auto로 스크롤
       가능. 큰 보드(51층+ 하드코어)는 renderBoard()가 칸 크기를 유지한 채 필요한 만큼
       .tower-track의 실제 높이(px)를 늘려서 스크롤 영역을 만든다(카트라이더 트랙 모양의
       serpentine 경로는 그대로 유지, 세로로만 길어짐). */
    /* [2026-09-14 3차 수정] "스크롤을 조금만 내려도 내 위치/아래로 버튼을 못 누른다"
       신고 -- sticky를 towerTrack 자식으로 넣는 방식은(바로 아래 옛 주석) 실기기에서
       안정적으로 안 붙어있는 문제가 있었다. 대신 .tower-viewport-wrap이라는 새 래퍼로
       #towerViewport(스크롤되는 상자)를 감싸고, 버튼 박스는 그 래퍼의 "형제"로 둬서
       애초에 스크롤 컨텍스트 밖에 있게 한다 -- position:absolute로 래퍼 기준 우측 하단에
       고정되면 #towerViewport 내부를 아무리 스크롤해도(내부 콘텐츠만 움직임) 버튼은 항상
       화면상 같은 자리에 떠 있는다(진짜 "플로팅"). */
    .tower-viewport-wrap{ position:relative; }
    /* [2026-09-18] "스크롤내리면 맵뷰가 길어진다" 신고 -- 모바일에서 스크롤하면 브라우저
       주소창이 접히면서 일반 vh가 순간순간 커지는 게 원인(모바일 브라우저의 vh는 "지금
       보이는" 뷰포트가 아니라 상황에 따라 값이 바뀜). svh(small viewport height)는 주소창이
       펼쳐진 최소 크기 기준으로 고정돼서 스크롤해도 절대 안 늘어난다 -- svh 미지원 브라우저는
       바로 아래 줄이 무효 선언으로 무시되고 그 위 vh 값이 그대로 폴백으로 남는다. */
    .tower-viewport{ position:relative; overflow-y:auto; overflow-x:hidden; width:100%; height:min(480px, 60vh);
                      height:min(480px, 60svh);
                      border-radius:14px; background:var(--parchment-deep);
                      border:1.5px dashed var(--line); padding:10px; box-sizing:border-box; }
    .tower-track{ position:relative; width:100%; height:100%; }
    /* [2026-09-30] 마을층 미니맵(바람의나라식: 위쪽=상향 이동, 아래쪽=하향 이동, 가운데 건물 버튼) + 최근 전투층 바로가기 */
    .tower-viewport-wrap.village-map .board-track-controls{ display:none; }
    .last-battle-bar{ display:none; margin:0 0 8px; }
    .last-battle-btn{ width:100%; border:1.5px solid var(--gold); background:var(--gold-soft); color:#6B4A12; font-weight:800;
                      font-size:13px; padding:9px 12px; border-radius:12px; cursor:pointer; text-align:center; }
    .last-battle-btn.dim{ opacity:.55; }
    .vmap{ position:relative; width:100%; height:100%; display:flex; flex-direction:column; gap:6px; }
    .vm-gate{ flex:0 0 auto; border:2px solid #8A6A32; background:linear-gradient(180deg,#E9D3A0,#D9BE84); color:#4A3712;
              font-weight:800; font-size:12.5px; padding:9px 8px; border-radius:10px; cursor:pointer; text-align:center; }
    .vm-gate small{ display:block; font-weight:600; font-size:10.5px; opacity:.8; }
    .vm-gate.locked{ opacity:.5; }
    .vm-gate.up{ border-bottom-width:4px; } .vm-gate.down{ border-top-width:4px; }
    .vm-field{ position:relative; flex:1 1 auto; min-height:0; border-radius:12px; border:2px solid #6E9B4F; overflow:hidden;
               background:
                 radial-gradient(circle at 20% 30%, rgba(255,255,255,.10) 0 6px, transparent 7px),
                 radial-gradient(circle at 70% 70%, rgba(255,255,255,.10) 0 6px, transparent 7px),
                 repeating-linear-gradient(45deg, #9CCB7B 0 14px, #93C271 14px 28px); }
    .vm-road-v{ position:absolute; left:50%; top:0; bottom:0; width:22%; transform:translateX(-50%); background:#DCC79B; opacity:.9; }
    .vm-road-h{ position:absolute; top:50%; left:0; right:0; height:16%; transform:translateY(-50%); background:#DCC79B; opacity:.9; }
    .vm-plaza{ position:absolute; left:50%; top:50%; width:34%; aspect-ratio:1/1; transform:translate(-50%,-50%); border-radius:50%;
               background:#EAD9AC; border:3px solid #B99B5A; display:flex; align-items:center; justify-content:center; font-size:26px; }
    .vm-title{ position:absolute; left:8px; top:6px; font-size:11px; font-weight:800; color:#2F5A1E; background:rgba(255,255,255,.7);
               padding:2px 8px; border-radius:999px; z-index:3; }
    .vm-you{ position:absolute; left:50%; top:50%; transform:translate(-50%,-50%); font-size:20px; margin-top:26px; z-index:2; }
    .vm-bld{ position:absolute; width:26%; min-width:74px; transform:translate(-50%,-50%); z-index:2; cursor:pointer;
             border:2px solid #6B4A12; border-radius:12px; background:#FFF6DC; padding:7px 4px 6px; text-align:center;
             box-shadow:0 3px 0 rgba(46,36,64,.25); font-family:inherit; color:#2E2440; }
    .vm-bld:active{ transform:translate(-50%,-48%); }
    .vm-bld .vb-ico{ display:block; font-size:26px; line-height:1.1; }
    .vm-bld .vb-name{ display:block; font-size:12px; font-weight:800; margin-top:2px; }
    .vm-bld .vb-sub{ display:block; font-size:10px; color:var(--ink-soft); }
    .vm-bld.dungeon{ background:#3A2E4F; color:#fff; border-color:#1D1530; } .vm-bld.dungeon .vb-sub{ color:#D9CFF0; }
    .vm-bld.dim{ opacity:.6; }
    .dungeon-opt{ display:flex; align-items:center; gap:10px; width:100%; background:#fff; border:1.5px solid var(--line);
                  border-radius:12px; padding:12px; margin-bottom:8px; cursor:pointer; font-family:inherit; text-align:left; color:var(--ink); }
    .dungeon-opt:hover{ border-color:var(--gold); }
    .dungeon-opt.locked{ opacity:.5; }
    .dungeon-opt .do-ico{ font-size:24px; } .dungeon-opt .do-name{ font-size:14px; font-weight:800; } .dungeon-opt .do-sub{ font-size:11px; color:var(--ink-soft); }
    /* [2026-09-14] "내 위치/스크롤 버튼을 맵뷰 안에 넣어달라, 마우스휠 스크롤은 막고 대신
       버튼으로 내리게 해달라" 요청으로 처음엔 towerTrack 자식 + position:sticky로 구현했었음
       (매 렌더마다 다시 그려 넣는 방식). [2026-09-14 3차 수정] 위 .tower-viewport-wrap 주석
       참고 -- 이제 정적 마크업 한 번만 만들고(renderBoard가 다시 그릴 필요 없음)
       position:absolute로 래퍼 우측 하단에 항상 떠 있는다. "맨 위로" 버튼도 추가. */
    .board-track-controls{ position:absolute; right:10px; bottom:10px; display:flex;
                   flex-direction:column; align-items:flex-end; gap:6px; pointer-events:none; z-index:5; }
    .board-track-btn{ pointer-events:auto; white-space:nowrap; border-radius:999px;
                   padding:6px 10px; font-size:11px; font-weight:700; cursor:pointer;
                   background:var(--parchment); border:1.5px solid var(--line); color:var(--ink);
                   box-shadow:var(--shadow); }
    .board-track-btn:active{ transform:scale(.95); }
    .tile{ position:absolute; border-radius:10px; display:flex;
           flex-direction:column; align-items:center; justify-content:center; font-size:9px; font-weight:700;
           color:#fff; box-shadow:0 2px 0 rgba(0,0,0,.12); text-align:center; line-height:1.15; padding:1px; }
    .tile .tno{ position:absolute; top:1px; left:3px; font-size:7px; opacity:.75; }
    .tile.combat{ background:var(--combat); } .tile.treasure{ background:var(--treasure); }
    .tile.pp{ background:var(--pp); } .tile.trap{ background:var(--trap); } .tile.special{ background:var(--special); }
    .tile.stairs-up{ background:var(--gold); } .tile.stairs-down{ background:var(--village); } .tile.elite{ background:var(--elite); }
    /* [2026-09-28] 101층+ 계단 구역 전용 칸 -- 행운(무엇이 나올지 모르는 랜덤칸)은 럭키/함정/특수
       색을 섞은 그라데이션, 중간보스는 강화몬스터보다 한 톤 짙게 테두리까지 둘러 눈에 띄게. */
    .tile.random-lucky{ background:linear-gradient(135deg, var(--pp) 0%, var(--special) 50%, var(--trap) 100%); }
    .tile.midboss{ background:var(--elite); box-shadow:inset 0 0 0 2px var(--ink); font-weight:700; }
    .tile.hidden{ background:#D8CDB4; color:#8a7f68; }
    .tile.done{ opacity:.55; } .tile.here{ outline:2px solid var(--ink); transform:scale(1.15); opacity:1; z-index:2; }
    /* [2026-09-14] "미니맵이 마음에 안 든다, 롤백해달라" 요청으로 09-13에 넣었던
       .board-minimap/.board-minimap-track/.board-minimap-hint/.board-zoom-card 및
       renderMinimap()/openBoardZoom()/closeBoardZoom()를 전부 제거하고 칸그리드를 다시
       카드 안에 직접 표시(PC 너비 제한도 같이 해제). "내 위치" 버튼(scrollToHere)만 카드
       제목 옆으로 옮겨서 유지 -- 칸이 커지고 세로 스크롤이 길어서 여전히 쓸모 있음. */
    /* [2026-09-12] "최소주사위 줄에 가려서 보드 위쪽 칸이 안 보인다" 신고 -- 그동안
       dice-overlay/dice-enhance-overlay/board-roll-btn을 전부 board-card 기준 절대좌표로
       띄워서 칸그리드(.tower-viewport) 좌상단 위에 겹쳐 보이게 했는데(2026-09-05~10),
       보드 폭/칸 수에 따라 겹치는 범위가 달라져 위쪽 1~2줄이 통째로 안 보이는 경우가
       생겼다. 절대좌표 오버레이를 버리고 .dice-controls라는 별도 박스로 분리해서
       card-title 아래 "정상적인 문서 흐름"에 놓는다 -- 이러면 이 박스가 차지하는 높이만큼
       .tower-viewport가 항상 그 아래로 밀려나서, 화면 크기가 어떻든 칸그리드를 절대
       가리지 않는다(스크롤 영역 크기에도 영향 없음 -- .tower-viewport는 여전히 자기
       height:min(440px,55vh)를 그대로 유지, 그 위에 컨트롤 박스 높이가 "추가"될 뿐). */
    /* [2026-09-12] "최대/최소 주사위 줄 크기를 똑같이 맞추고, 주사위 굴리기 버튼은
       오른쪽에서 두 줄 높이를 다 차지하게" 요청 -- 컨트롤 박스를 좌(최대+최소 두 줄, 세로로
       쌓임, 폭 동일)/우(굴리기 버튼, align-items:stretch로 좌측 전체 높이만큼 늘어남) 2단
       레이아웃으로 재구성. */
    /* [2026-09-17] "주사위선택/주사위사용 버튼이 위쪽에 있어서 스크롤내리면 잘 안보여, 화면
       상단에 계속 플로팅 되어있게 해달라" 요청 -- 문서(페이지) 스크롤 기준 position:sticky.
       예전에 칸그리드 안(#towerViewport의 overflow-y:auto 안, 게다가 매번 다시 그려지는
       .tower-track 자식)에서 sticky를 썼다가 실기기에서 안 붙는 문제로 position:absolute로
       바꾼 적이 있는데(board-track-controls 주석 참고), 이번엔 그 두 함정(중첩 스크롤 컨테이너
       안 / 매 렌더마다 파괴-재생성되는 엘리먼트)이 둘 다 해당 없다 -- .dice-controls는 정적
       엘리먼트(자식 내용만 innerHTML로 갱신됨)고, 기준 스크롤도 그냥 문서 자체라 sticky가
       안정적으로 동작한다. */
    .dice-controls{ display:flex; align-items:stretch; gap:8px; margin:8px 0 10px;
                     position:sticky; top:0; z-index:15; background:var(--parchment-deep);
                     padding:8px; border-radius:0 0 14px 14px; box-shadow:0 4px 10px -4px rgba(0,0,0,.25); }
    /* [2026-09-19] "전투시 주사위로 몬스터가 가려져" 신고 -- sticky는 이 박스 바로 아래
       #battleScreen(고정 높이, 자체 스크롤 없음)까지 페이지가 스크롤되면 그 위를 덮어버린다
       (원래 sticky는 세로로 긴 .tower-viewport가 자체 overflow-y:auto라 페이지 자체는
       스크롤될 일이 거의 없다는 전제로 넣은 것 -- .battle-screen엔 그 전제가 안 맞음).
       전투 중엔(updateBattleScreen이 붙이는 .dc-in-battle) 그냥 일반 흐름으로 되돌려서
       몬스터 줄 위에 뜨는 일 자체를 없앤다. */
    .dice-controls.dc-in-battle{ position:static; box-shadow:none; }
    .dice-controls-rows{ display:flex; flex-direction:column; gap:6px; flex:1 1 auto; min-width:0; }
    .move-limit-badge{ font-size:11px; color:var(--ink-soft); font-weight:700; }
    .move-limit-badge.move-limit-full{ color:var(--gold); }
    /* [2026-09-05] 사용중인 주사위는 더 크게/진하게 돋보이도록 표시, 한 줄로만 나열
       (줄바꿈 없음, 넘치면 가로 스크롤). */
    .dice-overlay{ width:100%; box-sizing:border-box;
                   display:flex; gap:4px; flex-wrap:nowrap;
                   overflow-x:auto; background:var(--parchment); border:1px solid var(--line);
                   border-radius:999px; padding:4px 6px; box-shadow:var(--shadow); }
    .dice-overlay button{ flex:0 0 auto; border:none; border-radius:999px; padding:3px 9px;
                   font-size:11px; font-weight:700; cursor:pointer; background:#fff;
                   color:var(--ink); transition:transform .1s; }
    .dice-overlay button.current{ background:var(--gold); color:#fff; font-size:13px;
                   padding:5px 12px; box-shadow:0 2px 0 rgba(0,0,0,.18); transform:scale(1.08); }
    .dice-overlay button.locked{ background:transparent; color:var(--ink-soft); opacity:.5; cursor:default; }
    /* [2026-09-09] 아래 dice-enhance-overlay의 "🎲최소" 라벨과 짝 -- 이 줄이 "최대치(주사위
       등급)"를 고르는 줄이라는 걸 맨 앞에 명시. .dice-enhance-label과 같은 톤이지만 부모가
       달라(.dice-overlay) 그쪽 스코프 규칙을 그대로 못 받아서 여기 따로 정의. */
    .dice-overlay .dice-enhance-label{ flex:0 0 auto; background:transparent; opacity:.6;
                   font-weight:600; font-size:11px; padding-right:1px; }
    /* [2026-09-08] 주사위 강화(+)/마이너스 주사위(-) 스트립 -- dice-overlay 바로 아래, 같은
       스타일의 pill로 -6..0..+6을 한 줄에 나열(넘치면 가로 스크롤). 0은 고정 기준점(항상
       회색 텍스트, 클릭 불가), 오른쪽(+)은 강화, 왼쪽(-)은 마이너스 -- 이미 산 단계는
       각각 금색/청색으로 진하게, 다음 한 단계만 클릭 가능(테두리로 표시), 그 이상은 흐리게
       + 해금층 안내. */
    .dice-enhance-overlay{ display:flex; align-items:center; gap:3px; flex-wrap:nowrap;
                   width:100%; box-sizing:border-box; overflow-x:auto; background:var(--parchment);
                   border:1px solid var(--line); border-radius:999px; padding:4px 6px;
                   box-shadow:var(--shadow); }
    .dice-enhance-overlay button, .dice-enhance-overlay span{ flex:0 0 auto; border:none;
                   border-radius:999px; padding:2px 7px; font-size:10px; font-weight:700;
                   background:#fff; color:var(--ink-soft); }
    /* [2026-09-08] "이게 최소값에 영향 주는 거라고 보이게 해달라" 요청 -- 스트립 맨 앞에
       고정 안내 라벨을 붙이고, 전체 박스에도 title(호버 툴팁)로 풀어서 설명. */
    .dice-enhance-overlay .dice-enhance-label{ background:transparent; opacity:.6;
                   font-weight:600; padding-right:1px; }
    .dice-enhance-overlay .dice-enhance-zero{ background:transparent; opacity:.5; }
    /* [2026-09-08] "선택한 게(현재 적용 중인 단계가) 더 잘 보이게 해달라, 초기값은 0"
       요청 -- 현재 유효 단계 하나만 더 크고 진하게(외곽선까지) 강조. +0/-0(아무것도 안
       샀을 때의 기본값)도 이 스타일을 그대로 받아서 "지금은 0이 적용 중"임이 보인다. */
    .dice-enhance-overlay .dice-enhance-zero.current{ background:var(--ink); color:#fff;
                   opacity:1; font-size:12px; padding:4px 10px; box-shadow:0 2px 0 rgba(0,0,0,.18);
                   transform:scale(1.1); }
    .dice-enhance-overlay button.current{ font-size:12px; padding:4px 10px;
                   box-shadow:0 2px 0 rgba(0,0,0,.18); transform:scale(1.15);
                   outline:2px solid #fff; outline-offset:-3px; }
    /* [2026-09-09] "여러 개가 동시에 선택된 것처럼 보인다, 토글버튼 아니고 최대/최소 각각
       1개만 선택되게 해달라" 요청 -- 예전엔 "구매 완료한 단계 전부"(지나온 낮은 단계까지)를
       색칠해서, 여러 단계가 한꺼번에 "선택됨"처럼 보였다. 지금은 이미 지나온(구매했지만
       현재 적용 중은 아닌) 단계는 무채색으로 눌러서 "지나온 흔적"만 남기고, 실제로 지금
       적용 중인 단 하나(.current)만 색을 채워 강조 -- 라디오버튼처럼 항상 정확히 1개만
       도드라져 보인다(기본값은 +0). */
    .dice-enhance-overlay button.dice-enhance-owned{ background:transparent; color:var(--ink-soft); opacity:.55; }
    .dice-enhance-overlay button.dice-enhance-owned.current.plus{ background:var(--gold); color:#fff; opacity:1; }
    .dice-enhance-overlay button.dice-enhance-owned.current.minus{ background:var(--village); color:#fff; opacity:1; }
    .dice-enhance-overlay button.dice-enhance-buyable{ background:#fff; color:var(--ink);
                   border:1.5px dashed var(--gold); cursor:pointer; }
    .dice-enhance-overlay button.dice-enhance-buyable.minus{ border-color:var(--village); }
    .dice-enhance-overlay button.dice-enhance-locked{ background:transparent; opacity:.4; cursor:default; }
    /* [2026-09-09] "주사위를 보드 안쪽으로 옮기고, 하단 주사위굴리기 탭은 없애자" 요청 --
       기존 하단 도크의 별도 "주사위" 버튼(dbtnDice)을 없애고, 그 자리에 있던 굴리기 동작을
       보드 카드 안(최대/최소 줄 옆)의 이 버튼 하나로 옮긴다.
       [2026-09-12] 절대좌표 오버레이에서 일반 flex 아이템으로 전환(위 .dice-controls 주석
       참고) -- 최대 줄 옆이 아니라 이제 최대+최소 두 줄 전체 옆에서, 부모(.dice-controls)의
       align-items:stretch로 그 두 줄을 합친 높이만큼 늘어난다(고정 height 제거). */
    /* [2026-09-30] 연타할 때 더블탭 확대/스크롤 제스처로 화면이 흔들리지 않게 */
    .board-roll-btn{ touch-action:manipulation; -webkit-tap-highlight-color:transparent; user-select:none; flex:0 0 auto;
                   display:flex; flex-direction:column; align-items:center; justify-content:center;
                   gap:2px; width:56px; border-radius:14px; border:none; cursor:pointer;
                   background:linear-gradient(180deg,#E4633F,var(--combat)); color:#fff; font-weight:800;
                   font-size:11px; box-shadow:0 3px 0 #A93A28; }
    .board-roll-btn:active{ transform:scale(.96); }
    .board-roll-btn:disabled{ background:var(--parchment-deep); color:var(--ink-soft); opacity:.6;
                   box-shadow:none; cursor:not-allowed; }
    .board-roll-btn .broll-icn{ font-size:24px; }
    .legend{ display:flex; flex-wrap:wrap; gap:6px; margin-top:10px; }
    .legend-chip{ display:flex; align-items:center; gap:4px; font-size:10px; color:var(--ink-soft); background:#fff;
                  border:1px solid var(--line); border-radius:999px; padding:3px 9px; }
    .legend-dot{ width:8px; height:8px; border-radius:50%; }

    /* [2026-09-06] 파티 영역 확대 개편 -- 드래그 폐지에 이어, 슬롯을 탭해서 여는 시트 대신
       각 파티원 카드 자체에 동료변경/해제/부위별 장비변경 버튼을 전부 인라인으로 붙인다
       ("보유동료보다 이미지 크게, 동료 한 명씩 영역도 크게, 버튼은 직관적으로" 요청).
       세로로 크게 쌓아서(3열 그리드였던 것을 1열로) 카드 하나에 들어갈 내용(초상화+정보+
       버튼 2개+장비 3부위)이 넉넉히 들어가게 함. */
    .party-slots{ display:flex; flex-direction:column; gap:10px; }
    .party-slot-card{ border:2px solid var(--line); border-radius:16px; padding:14px; background:#fff; }
    .party-slot-card.filled{ border-color:var(--gold); background:var(--gold-soft); }
    .party-slot-card.locked{ opacity:.6; border-style:dashed; }
    .party-slot-card.empty{ display:flex; flex-direction:column; align-items:center; gap:10px;
                             padding:26px 14px; border-style:dashed; color:var(--ink-soft); }
    .ps-slot-label{ font-size:10px; color:var(--ink-soft); opacity:.8; }
    .ps-header{ display:flex; align-items:center; gap:14px; margin-bottom:12px; }
    .ps-avatar{ width:84px; height:84px; border-radius:14px; object-fit:cover; object-position:50% 15%;
                background:#EFE7D2; border:2px solid var(--line); flex-shrink:0; cursor:zoom-in; }
    .ps-avatar.avatar-emoji{ display:flex; align-items:center; justify-content:center; font-size:38px; }
    .ps-info{ flex:1; min-width:0; }
    .ps-name{ font-size:16px; font-weight:800; color:var(--ink); }
    .ps-role{ font-size:12px; color:var(--ink-soft); margin-top:2px; }
    .ps-hp{ font-size:11px; color:var(--ink-soft); margin-top:4px; }
    /* [2026-09-18] "체력이 0이 된 동료는 전투불능 표시를 하고싶어" 요청 -- HP<=0인 동료를
       보여주는 자리마다 공통으로 쓰는 톤(빨강 텍스트 + 살짝 흐려진 초상화). */
    .ps-hp.incapacitated{ color:var(--combat); font-weight:800; }
    .party-slot-card.incapacitated .ps-avatar, .party-card.incapacitated .avatar,
    .bs-companion.incapacitated .bs-avatar, .bs-companion.incapacitated .avatar-emoji{
      filter:grayscale(70%); opacity:.65; }
    .incapacitated-badge{ color:var(--combat); font-weight:800; font-size:10px; }
    .ps-actions{ display:flex; gap:6px; margin-bottom:12px; }
    /* [2026-10-21] 설정 탭 + 동료 이미지 바꾸기 팝업 */
    .set-row{ display:flex; align-items:center; justify-content:space-between; gap:10px; padding:10px 0; border-top:1px dashed var(--line); }
    .set-label{ font-size:13px; font-weight:800; }
    .set-btns{ display:flex; gap:6px; }
    .set-btn{ border:1.5px solid var(--line); background:#fff; border-radius:10px; padding:8px 16px; font-size:13px; font-weight:800; color:var(--ink-soft); cursor:pointer; }
    .set-btn.active{ background:var(--gold); border-color:var(--gold); color:#fff; }
    /* [2026-10-22] 설정 -- 하단 메뉴 순서/숨기기(유저별 저장) */
    .set-block{ padding:10px 0; border-top:1px dashed var(--line); }
    .set-block-head{ display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:8px; }
    .set-hint{ font-size:11px; color:var(--ink-soft); margin:-4px 0 8px; }
    .dock-edit-row{ display:flex; align-items:center; gap:8px; background:#fff; border:1.5px solid var(--line); border-radius:10px; padding:6px 8px; margin-bottom:6px; }
    .dock-edit-row.hidden-tab .de-icn, .dock-edit-row.hidden-tab .de-name{ opacity:.4; text-decoration:line-through; }
    .dock-edit-row .de-no{ width:18px; font-size:11px; font-weight:800; color:var(--ink-soft); text-align:center; }
    .dock-edit-row .de-icn{ font-size:18px; }
    .dock-edit-row .de-name{ flex:1; font-size:13px; font-weight:800; }
    .dock-edit-row button{ border:1.5px solid var(--line); background:#fff; border-radius:8px; font-size:12px; font-weight:800; color:var(--ink-soft); padding:5px 8px; cursor:pointer; min-width:34px; }
    .dock-edit-row button:disabled{ opacity:.35; cursor:default; }
    .dock-edit-row button.de-toggle{ min-width:58px; }
    .dock-edit-row button.de-toggle.off{ background:var(--parchment-deep); }
    .dock-save-state{ font-size:11px; color:var(--ink-soft); }
    .isw-now{ display:flex; align-items:center; gap:12px; background:#fff; border:1.5px solid var(--line); border-radius:12px; padding:8px 10px; margin-bottom:10px; font-size:12px; line-height:1.5; }
    .isw-now .avatar{ width:56px; height:56px; flex:none; border-radius:10px; object-fit:cover; object-position:50% 15%; background:#EFE7D2; border:2px solid var(--line); }
    .isw-now .avatar-emoji{ display:flex; align-items:center; justify-content:center; font-size:26px; }
    .party-card.swappable{ cursor:pointer; }
    .party-card.swappable:active{ transform:scale(.97); }
    .ps-btn{ flex:1; background:#fff; border:1.5px solid var(--line); border-radius:10px; font-size:12px;
             font-weight:700; padding:9px; cursor:pointer; color:var(--ink); }
    .ps-btn:active{ transform:scale(.97); }
    .ps-btn.danger{ color:var(--combat); border-color:var(--combat); }
    .ps-btn.small{ font-size:10px; padding:6px; width:100%; }
    .ps-equip-row{ display:grid; grid-template-columns:repeat(3,1fr); gap:8px; border-top:1.5px dashed var(--line); padding-top:12px; }
    .ps-equip-slot{ display:flex; flex-direction:column; align-items:center; gap:5px; background:#fff;
                    border:1.5px solid var(--line); border-radius:12px; padding:9px 6px; }
    .ps-equip-icon{ font-size:22px; }
    .ps-equip-grade{ font-size:11px; font-weight:700; color:var(--ink); }
    /* [2026-10-20] 장비 카드: 전설 이름/강화 수치와 장비가 올려주는 능력치(강화로 늘어난 몫은 따로) */
    .ps-equip-name{ font-size:10px; color:var(--ink-soft); max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .ps-equip-stat{ font-size:10px; line-height:1.45; text-align:center; color:var(--ink-soft); min-height:14px; }
    .ps-equip-stat b{ color:var(--ink); font-weight:700; }
    /* [2026-10-20] 파티 카드 상단 상세: 총합 = 원래 + 스탯구매 + 한계돌파 + 장비(+강화) 색 구분 */
    .ps-detail{ font-size:11px; line-height:1.65; margin-bottom:10px; background:#fff; border:1.5px solid var(--line); border-radius:10px; padding:8px 10px; }
    .ps-detail:empty{ display:none; }
    .psd-k{ display:inline-block; min-width:28px; font-weight:800; color:var(--ink); }
    .psd-total{ font-size:13px; color:var(--ink); }
    .c-base{ color:var(--ink-soft); }
    .c-buy{ color:#1F8A8A; font-weight:700; }
    .c-lb{ color:#C2661A; font-weight:700; }
    .c-eq{ color:#2D6CB5; font-weight:700; }
    .c-enh{ color:#2F7D32; font-weight:700; }
    .psd-mult{ margin-top:4px; padding-top:4px; border-top:1px dashed var(--line); color:var(--ink-soft); font-size:10.5px; }
    .psd-mult b{ color:#C2661A; }
    .ps-equip-enh{ font-size:10px; line-height:1.4; text-align:center; color:#9A6B12; font-weight:700; }
    /* 카드마다 내용 길이가 달라도(능력치/강화 줄 수) 변경 버튼이 늘어나지 않고 카드 맨 아래에 같은 높이로 붙게 */
    .ps-equip-slot .ps-btn.small{ flex:none; margin-top:auto; }
    .ps-empty-msg{ font-size:13px; }

    /* "보유동료/파티장비현황/미착용장비는 없애고 버튼으로 이관" 요청 -- 파티 탭 하단의
       "더 보기" 버튼 2개. 눌린 기능 자체(renderPartyGrid/renderEquipList 등)는 그대로 두고
       담는 그릇만 인라인 카드에서 팝업(.wide-card)으로 옮겼다. */
    .browse-btn{ flex:1; background:var(--parchment); border:1.5px solid var(--line); border-radius:12px;
                 font-size:13px; font-weight:700; padding:12px; cursor:pointer; color:var(--ink); }
    .browse-btn:active{ transform:scale(.98); }
    /* [2026-09-07] "필터링에 따라 창 크기가 바뀐다, 고정 크기로" 요청 -- 높이를 고정해서
       필터를 눌러 목록이 줄어들거나 늘어나도 팝업 자체는 안 흔들리고 카드 내부만 스크롤됨. */
    .wide-card{ max-width:520px; height:640px; max-height:85vh; }

    .card-title-row{ display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:10px; }
    .card-title-row .card-title{ margin-bottom:0; }
    .btn-unassign-all{ background:#fff; border:1.5px solid var(--line); border-radius:10px;
                         font-size:11px; padding:5px 11px; cursor:pointer; color:var(--ink-soft); white-space:nowrap; }
    .btn-unassign-all:hover{ border-color:var(--combat); color:var(--combat); }

    .party-grid{ display:grid; grid-template-columns:repeat(auto-fill,minmax(120px,1fr)); gap:8px; }
    .party-card{ background:#fff; border:2px solid var(--line); border-radius:14px; padding:9px; text-align:center; }
    .party-card.inparty{ border-color:var(--gold); background:var(--gold-soft); }
    /* [2026-09-07] 전체동료보기를 배치 팝업으로도 재사용할 때(companionPickerSlot) 탭
       가능함을 알리는 커서/피드백. */
    .party-card.pickable{ cursor:pointer; }
    .party-card.pickable:active{ transform:scale(.97); }
    /* 얼굴이 잘 안 보인다는 신고로 원형 → 사각형으로 변경 + 인물 사진은 보통 위쪽에 얼굴이
       있어서 object-position을 top으로 둬서 얼굴이 잘리지 않게 함. 클릭하면 확대(zoom-in). */
    .party-card .avatar{ width:64px; height:64px; border-radius:10px; object-fit:cover; object-position:50% 15%;
                          background:#EFE7D2; display:block; margin:0 auto 6px; border:2px solid var(--line);
                          cursor:zoom-in; }
    .party-card .avatar-emoji{ display:flex; align-items:center; justify-content:center; font-size:28px; cursor:default; }

    /* 캐릭터 확대 + 상세(장비/스탯) 모달 */
    .detail-overlay{ position:fixed; inset:0; background:rgba(20,14,32,.72); z-index:200;
                      display:none; align-items:center; justify-content:center; padding:20px; }
    .detail-overlay.open{ display:flex; }
    /* [2026-09-15 버그 수정] "전체동료 리스트에서 동료 누르면 상세보기가 뒤로 깔린다"
       신고 -- 6개 오버레이(detailOverlay/confirmOverlay/noticeOverlay/pickerOverlay/
       allCompanionsOverlay/allEquipOverlay) 전부 같은 .detail-overlay 클래스를 그대로
       써서 z-index가 200으로 동일했다. 전체동료보기(allCompanionsOverlay)가 열린 채로
       그 안의 동료를 눌러 상세보기(detailOverlay)를 "위에" 띄우면, z-index가 같을 땐
       DOM 순서로 승패가 갈리는데 detailOverlay가 allCompanionsOverlay보다 앞(위)쪽
       마크업이라 오히려 뒤에 깔려 보였다. 목록류(피커/전체동료/전체장비)는 200 그대로
       두고, 그 안에서 "더 파고드는" 상세보기는 210으로, 언제 어디서 열려도 항상 최상단
       이어야 하는 확인/알림 다이얼로그는 220으로 개별 지정해서 항상 올바른 순서로 쌓이게 함. */
    #detailOverlay{ z-index:210; }
    #confirmOverlay, #noticeOverlay, #gateOverlay{ z-index:220; }
    /* [2026-10-22] 주사위 전 패치 확인 팝업 / 장비 수량 / 강화권 */
    .gate-body{ font-size:12.5px; line-height:1.6; white-space:pre-line; background:#fff; border:1.5px solid var(--line); border-radius:12px; padding:10px 12px; margin:6px 0 12px; max-height:52vh; overflow:auto; }
    .gate-btns{ display:flex; gap:8px; }
    .gate-btns button{ flex:1; border-radius:12px; padding:11px 8px; font-size:13px; font-weight:800; cursor:pointer; border:1.5px solid var(--line); background:#fff; color:var(--ink-soft); }
    .gate-btns button.primary{ background:var(--gold); border-color:var(--gold); color:#fff; }
    .dm-qty-row{ display:flex; align-items:center; justify-content:center; gap:6px; margin:12px 0 8px; }
    .dm-qty-row input{ width:80px; text-align:center; font-size:16px; font-weight:800; padding:6px; border:1px solid var(--line, #ccc); border-radius:8px; }
    .dm-qty-row button, .dm-quick button{ min-width:40px; padding:6px 8px; }
    .dm-quick{ display:flex; justify-content:center; gap:6px; margin-bottom:8px; }
    .dm-info{ font-size:12px; text-align:center; opacity:.85; line-height:1.5; }
    .eq-qty{ display:inline-block; margin-left:4px; padding:0 6px; border-radius:999px; background:var(--gold); color:#fff; font-size:10px; font-weight:800; }
    .ticket-row{ display:flex; flex-wrap:wrap; gap:6px; margin:0 0 10px; font-size:11px; }
    .ticket-chip{ background:#fff; border:1.5px solid var(--gold); color:var(--gold); border-radius:999px; padding:3px 9px; font-weight:800; }
    .legend-ticket-btn{ margin-left:6px; }
    .detail-card{ background:linear-gradient(180deg,#FFFCF3,var(--parchment-deep)); border:2px solid var(--line);
                   border-radius:20px; padding:16px; box-shadow:var(--shadow); max-width:360px; width:100%;
                   max-height:88vh; overflow:auto; position:relative; }
    .detail-card .detail-close{ position:absolute; top:10px; right:12px; background:none; border:none;
                                  font-size:20px; cursor:pointer; color:var(--ink-soft); line-height:1; }
    .detail-card .detail-img{ width:100%; aspect-ratio:1/1; object-fit:cover; object-position:50% 15%;
                                border-radius:14px; border:2px solid var(--line); background:#EFE7D2; display:block; }
    .detail-card .detail-name{ font-size:17px; font-weight:800; margin-top:10px; }
    .detail-card .detail-role{ font-size:12px; color:var(--ink-soft); margin-bottom:8px; }
    .detail-stats{ display:grid; grid-template-columns:repeat(3,1fr); gap:6px; margin:8px 0 10px; }
    .detail-stats .stat-box{ background:#fff; border:1.5px solid var(--line); border-radius:10px; padding:8px 4px; text-align:center; }
    .detail-stats .stat-box .stat-label{ font-size:9px; color:var(--ink-soft); }
    .detail-stats .stat-box .stat-val{ font-size:14px; font-weight:800; }
    /* [2026-10-20] 동료 상세: 총합 아래에 "캐릭터 기본(+한계돌파) / 장비 합계"를 두 줄로 */
    .detail-stats .stat-box .stat-sub{ font-size:9px; line-height:1.45; color:var(--ink-soft); margin-top:3px; }
    .detail-stats .stat-box .stat-sub b{ color:var(--ink); font-weight:700; }
    .detail-stats .stat-box .stat-sub .lb{ color:#9A6B12; }
    .der{ background:#fff; border:1.5px solid var(--line); border-radius:10px; padding:8px 10px; margin-bottom:6px; }
    .der-head{ display:flex; justify-content:space-between; gap:8px; font-weight:700; font-size:12px; }
    .der-line{ font-size:10.5px; line-height:1.5; color:var(--ink-soft); margin-top:2px; }
    .der-line b{ color:var(--ink); }
    .der-line.up b{ color:#2F7D32; }
    .sheet-row.has-stat{ flex-wrap:wrap; }
    .sheet-row .sr-stat{ flex-basis:100%; font-size:10.5px; line-height:1.5; color:var(--ink-soft); }
    .sheet-row .sr-stat b{ color:var(--ink); }
    .sheet-row .sr-stat .up{ color:#2F7D32; font-weight:700; }
    .sheet-row .sr-stat .down{ color:#B23A2A; font-weight:700; }
    .detail-equip-row{ display:flex; justify-content:space-between; background:#fff; border:1.5px solid var(--line);
                        border-radius:10px; padding:7px 10px; margin-bottom:5px; font-size:11px; }
    .party-card{ position:relative; }
    .party-card.hidden{ opacity:.4; }
    .party-card .hide-btn{ position:absolute; top:4px; right:6px; font-size:13px; cursor:pointer; background:none; border:none; padding:2px; }
    .party-card .cname{ font-size:12px; font-weight:800; }
    .party-card .role{ font-size:10px; color:var(--ink-soft); }
    .hpbar-track{ height:6px; border-radius:4px; background:#EFE7D2; overflow:hidden; margin-top:4px; }
    .hpbar-fill{ height:100%; background:linear-gradient(90deg,#5FBE85,#2F8F5C); transition:width .4s ease; }
    .hp-num{ font-size:9px; color:var(--ink-soft); margin-top:2px; }

    /* [2026-09-17] "전투 화면을 포켓몬 배틀 화면처럼" 요청 -- 전투 중엔 칸그리드(#towerViewport)
       대신 이 화면을 보여준다. 서버는 한 번의 /주사위 요청에 전투 전체를 계산해서 텍스트
       메시지 하나로만 돌려주므로(턴별 구조화 데이터 없음), 진짜 턴 단위 타격 모션 대신
       "때리기 전 HP" -> "때린 후 HP"를 간단한 스냅샷 연출로 보여준다: 주사위 버튼을 누르면
       동료 전원이 짧게 앞으로 튀고(파티가 실제로 매턴 다같이 공격하는 것과 동일), 몬스터가
       흔들리면서 HP바가 새 값으로 줄어들고, 반격을 맞은 동료도 흔들리며 자기 HP바가 준다.
       HP바 자체의 부드러운 감소는 위 .hpbar-fill의 transition:width만으로 공짜로 얻는다 --
       DOM 엘리먼트를 갱신할 때마다 새로 만들지 않고 width만 바꾸는 게 핵심(updateBattleParty/
       updateBattleScreen 참고). 몬스터는 이미지 에셋이 전혀 없어(DB에 스프라이트 컬럼 자체가
       없음) monsterEmoji()로 몬스터ID 기반 이모지를 고정 배정해 자리만 채운다. */
    /* [2026-09-17 2차] "전투시 UI창 크기가 맵뷰 UI창과 동일사이즈면 좋겠어" 요청 -- .tower-viewport
       와 똑같이 height:min(480px, 60vh)로 고정해서 맵뷰↔전투화면 전환 시 카드 높이가 안 흔들린다.
       내용은 flex column + space-between으로 위(몬스터)/아래(파티) 끝에 붙이고 남는 세로공간은
       가운데 여백으로 자연스럽게 흡수한다. */
    .battle-screen{ border:1.5px solid var(--line);
                     border-radius:14px; padding:16px; margin-top:2px; box-sizing:border-box;
                     height:min(480px, 60vh); height:min(480px, 60svh); /* [2026-09-18] .tower-viewport와 동일 이유(svh 폴백) */
                     display:flex; flex-direction:column; justify-content:space-between;
                     opacity:1; transition:opacity .28s ease, background-position 0s;
                     position:relative; overflow:hidden; }
    /* [2026-09-17 2차] "전투시 뒤 배경도 애니메이션 틱한 배경, 랜덤배경 생기면 좋겠어" 요청 --
       몬스터 이미지 에셋이 없는 것처럼 배경 그림도 없어서(DB에 그런 자원 자체가 없음),
       천천히 흐르는 그라데이션 6종을 만들어 전투 진입(새 몬스터 조우)마다 하나를 무작위로
       배정한다(updateBattleScreen의 "새 몬스터 조우" 분기 참고). 텍스트 대비를 해치지
       않도록 전부 --parchment 톤과 비슷한 밝기의 파스텔로만 구성(색상만 다름).
       .bs-particle 2개는 배경 위에 천천히 떠다니는 흐릿한 빛망울로 "애니메이션 틱"을 더한다. */
    @keyframes bsBgShift{ 0%{ background-position:0% 50%; } 50%{ background-position:100% 50%; } 100%{ background-position:0% 50%; } }
    .bs-bg-0{ background:linear-gradient(135deg,#E9F1E1,#F8F4E4,#E9F1E1); background-size:220% 220%; animation:bsBgShift 12s ease infinite; }
    .bs-bg-1{ background:linear-gradient(135deg,#F6ECD9,#FBE3C0,#F6ECD9); background-size:220% 220%; animation:bsBgShift 10s ease infinite; }
    .bs-bg-2{ background:linear-gradient(135deg,#E3EEF6,#D3E6F5,#E3EEF6); background-size:220% 220%; animation:bsBgShift 13s ease infinite; }
    .bs-bg-3{ background:linear-gradient(135deg,#EDE6F6,#E1D3F0,#EDE6F6); background-size:220% 220%; animation:bsBgShift 11s ease infinite; }
    .bs-bg-4{ background:linear-gradient(135deg,#F8E5E8,#F5D3DA,#F8E5E8); background-size:220% 220%; animation:bsBgShift 9s ease infinite; }
    .bs-bg-5{ background:linear-gradient(135deg,#DFF0E9,#C9E8DB,#DFF0E9); background-size:220% 220%; animation:bsBgShift 14s ease infinite; }
    .bs-particle{ position:absolute; border-radius:50%; filter:blur(10px); opacity:.5; pointer-events:none; z-index:0; }
    .bs-particle-a{ width:70px; height:70px; background:#fff; top:10%; left:15%; animation:bsFloatA 16s ease-in-out infinite; }
    .bs-particle-b{ width:50px; height:50px; background:#fff; bottom:12%; right:20%; animation:bsFloatB 19s ease-in-out infinite; }
    @keyframes bsFloatA{ 0%,100%{ transform:translate(0,0); } 50%{ transform:translate(24px,-16px); } }
    @keyframes bsFloatB{ 0%,100%{ transform:translate(0,0); } 50%{ transform:translate(-20px,14px); } }
    .bs-top, .bs-party-row{ position:relative; z-index:1; }
    /* [2026-09-17 2차] "전투진입시 포켓몬처럼 스르르르 페이드아웃/페이드인" 요청 -- 맵뷰<->전투화면
       전환(진입/이탈)에만 opacity 크로스페이드를 건다(crossfadeBoardView 참고). 전투 중 HP
       갱신처럼 화면을 안 바꾸는 경우는 이 transition과 무관. */
    .tower-viewport-wrap{ transition:opacity .28s ease; }

    /* [2026-09-21] V1/V2 버전 전환 -- #battleScreen(바깥, 배경/파티클/크기 담당)은 그대로 두고
       안쪽 두 래퍼(#battleScreenV2가 기본 표시, #battleScreenV1은 구버전) 중 하나만 보인다.
       V1은 옛 구조(위 몬스터줄/아래 파티줄을 space-between으로 위아래 끝에 붙임) 그대로
       재현해야 해서 flex:1로 카드 전체 높이를 채우게 한다. V2는 콘텐츠가 이미 자체 완결형
       (세력비교바+대치장면+스탯표 한 묶음)이라 그럴 필요 없음. */
    #battleScreenV1{ position:relative; z-index:1; display:flex; flex-direction:column; justify-content:space-between; flex:1; min-height:0; }
    #battleScreenV2{ position:relative; z-index:1; }
    .bs-version-btns{ position:absolute; top:8px; right:8px; z-index:2; display:flex; gap:4px; }
    .bs-version-btn{ border:1.5px solid var(--line); background:rgba(255,255,255,.75); border-radius:8px;
                       padding:5px 11px; font-size:11px; font-weight:800; color:var(--ink-soft); cursor:pointer;
                       min-width:30px; }
    .bs-version-btn.active{ background:var(--gold); border-color:var(--gold); color:#fff; }

    /* [2026-09-21] 삼국지 대전화면 재설계 -- ①세력비교바 ②중앙 대치 장면 ③좌우 스탯표.
       사용자 첨부 레퍼런스(관우 vs 조조군 1:1 대전 화면)의 구성요소를 파티vs몬스터 구조에
       맞게 절충 적용(파티 쪽은 파티슬롯1번/최고성급 생존자를 "대표"로 매핑). */
    .bs-top{ display:flex; flex-direction:column; gap:8px; }
    .bs-power-bar{ display:flex; align-items:center; gap:8px; }
    .bs-power-num{ font-size:20px; font-weight:900; min-width:44px; text-align:center; }
    .bs-power-num-party{ color:#2F6FA8; }
    .bs-power-num-monster{ color:#B8412F; }
    .bs-power-track{ flex:1; height:14px; border-radius:7px; overflow:hidden; display:flex;
                       position:relative; border:1.5px solid var(--line); background:#EFE7D2; }
    /* [2026-09-21] 좌/우 절반을 고정폭(50%)으로 나눠서 각자 안에서 채워지게 한다("상단
       체력바를 절반씩 영역을 차지하면 좋겠어" 요청) -- 파티(왼쪽)는 왼쪽 끝에 붙어 안쪽으로
       차오르고, 몬스터(오른쪽)는 오른쪽 끝에 붙어 안쪽으로 차오른다(justify-content로 제어,
       fill 자체는 항상 width:100%을 목표로 하되 setHpBarFill이 실제 폭을 pct%로 줄임).
       색은 fill 요소에 공용 setHpBarFill()이 HP_GRADIENT(초록→노랑→빨강)를 입힌다. */
    .bs-power-half{ width:50%; height:100%; overflow:hidden; display:flex; }
    .bs-power-half-party{ justify-content:flex-start; }
    .bs-power-half-monster{ justify-content:flex-end; }
    .bs-power-fill{ height:100%; transition:width .4s ease; }
    .bs-power-vs{ position:absolute; left:50%; top:50%; transform:translate(-50%,-50%);
                   font-size:9px; font-weight:800; color:#fff; text-shadow:0 1px 2px rgba(0,0,0,.4); letter-spacing:.5px; }
    .bs-duel-row{ display:flex; align-items:center; justify-content:space-around; }
    .bs-duel-side{ display:flex; flex-direction:column; align-items:center; gap:4px; width:88px; }
    .bs-duel-avatar-slot .bs-duel-avatar{ width:56px; height:56px; border-radius:50%; object-fit:cover; object-position:50% 15%;
                                            border:2.5px solid #fff; box-shadow:0 2px 6px rgba(0,0,0,.18); }
    .bs-duel-avatar-slot .avatar-emoji{ width:56px; height:56px; border-radius:50%; display:flex; align-items:center;
                                          justify-content:center; font-size:26px; background:#fff; border:2.5px solid #fff;
                                          box-shadow:0 2px 6px rgba(0,0,0,.18); }
    /* [2026-10-20] "전투화면이 어색해, 왼쪽에 메인캐릭터 한 마리가 덩그러니 가운데 있는데 캐릭터 여러 마리가 동시에 서 있도록" 요청 --
       V2 대치 장면 왼쪽을 대표 1명 대신 파티 전원이 나란히 서 있는 대열(.bs-duel-party)로 바꿨다. 1번 슬롯이 몬스터에 가장 가깝게(row-reverse) 서고,
       가운데 동료는 한 칸 뒤로 물러서서 V자 대열처럼 보이게 한다. 각 동료가 자기 HP바/상태(전투불능)/피격 흔들림/공격 펄스를 가진다. */
    .bs-duel-party{ flex:1; min-width:0; display:flex; flex-direction:row-reverse; align-items:flex-end; justify-content:center; gap:4px; padding:0 4px; }
    .bs-duel-member{ display:flex; flex-direction:column; align-items:center; gap:3px; flex:1 1 0; min-width:0; max-width:68px; }
    .bs-duel-member:nth-child(2){ margin-bottom:14px; }
    .bs-duel-member .bs-duel-avatar-slot{ width:100%; display:flex; justify-content:center; }
    /* 화면 폭이 좁으면(모바일) 3명이 한 줄에 들어가도록 아바타 크기를 vw 기준으로 줄인다(데스크톱은 52px). */
    .bs-duel-member .bs-duel-avatar-slot .bs-duel-avatar, .bs-duel-member .bs-duel-avatar-slot .avatar-emoji{ width:min(52px, 11vw); height:min(52px, 11vw); font-size:min(24px, 5.2vw); }
    .bs-duel-member .bs-duel-name{ font-size:10px; max-width:100%; line-height:1.15; white-space:normal; word-break:keep-all; overflow-wrap:anywhere; }
    .bs-duel-member .bs-duel-hpbar{ width:82%; }
    .bs-duel-member.incapacitated .bs-duel-avatar, .bs-duel-member.incapacitated .avatar-emoji{ filter:grayscale(1); opacity:.5; }
    .bs-duel-member .bs-duel-down{ font-size:9px; color:var(--combat); font-weight:800; }
    .bs-duel-member.shake{ animation:bsShake .35s; }
    .bs-duel-empty{ flex:1; text-align:center; font-size:11px; color:var(--ink-soft); }
    @media (max-width:420px){ .bs-duel-row > .bs-duel-side{ width:70px; } .bs-duel-vs-badge{ padding:2px 5px; font-size:13px; } }
    .bs-monster-sprite{ font-size:52px; line-height:1; }
    .bs-monster-sprite.hit{ animation:bsShake .35s; }
    .bs-duel-name{ font-size:11px; font-weight:800; text-align:center; }
    /* [2026-09-21] "왼쪽아래 아이콘(구 파티 목록)은 없어도 될거같아, 위쪽 캐릭터에 체력바를
       넣어주면 될거같아" 요청 -- V2에서 하단 bs-party-row를 없애는 대신 파티 대표 캐릭터
       본인의 HP바를 duel-side 안에 작게 넣는다(전체 합산 세력비교바와는 별개로, "이 캐릭터"
       개인 HP). */
    .bs-duel-hpbar{ width:70px; height:5px; margin-top:0; }
    .bs-duel-side .hp-num{ font-size:9px; color:var(--ink-soft); }

    /* ===== V1(구버전, 포켓몬 스타일) -- 2026-09-17 원본 CSS 복원 ===== */
    .bs-monster-row{ display:flex; align-items:center; justify-content:flex-end; gap:12px; }
    .bs-monster-sprite-v1{ font-size:48px; line-height:1; }
    .bs-monster-sprite-v1.hit{ animation:bsShake .35s; }
    .bs-monster-info{ min-width:150px; max-width:220px; text-align:right; }
    .bs-monster-info .bs-name{ font-size:13px; font-weight:800; margin-bottom:3px; }
    .bs-monster-info .hpbar-track{ height:9px; }
    .bs-duel-vs-badge{ font-size:15px; font-weight:900; color:var(--gold); border:2px solid var(--gold);
                         border-radius:8px; padding:2px 8px; background:var(--gold-soft); flex-shrink:0; }
    .bs-stat-table{ display:flex; align-items:stretch; justify-content:space-between; gap:6px;
                      background:rgba(255,255,255,.55); border:1px solid var(--line); border-radius:10px; padding:6px 10px; }
    .bs-stat-col{ display:grid; grid-template-columns:auto auto; gap:1px 6px; align-content:center; font-size:10px; }
    .bs-stat-col-right{ text-align:right; grid-template-columns:auto auto; direction:rtl; }
    .bs-stat-col-right > *{ direction:ltr; }
    .bs-stat-hdr{ color:var(--ink-soft); font-weight:700; }
    .bs-stat-val{ font-weight:800; }
    .bs-stat-hit{ grid-column:1 / span 2; font-size:9px; color:var(--ink-soft); margin-top:2px; font-weight:700; }
    .bs-stat-mid{ flex-shrink:0; font-size:9px; font-weight:800; color:var(--ink-soft); text-align:center;
                   align-self:center; line-height:1.3; border-left:1px dashed var(--line); border-right:1px dashed var(--line); padding:0 8px; }

    .bs-party-row{ display:flex; gap:12px; justify-content:flex-start; flex-wrap:wrap; }
    .bs-companion{ width:66px; text-align:center; }
    .bs-companion.shake{ animation:bsShake .35s; }
    .bs-companion .bs-avatar{ width:46px; height:46px; border-radius:50%; object-fit:cover; object-position:50% 15%;
                                border:2px solid #fff; box-shadow:0 1px 3px rgba(0,0,0,.15); }
    .bs-companion .avatar-emoji{ width:46px; height:46px; border-radius:50%; display:flex; align-items:center;
                                   justify-content:center; font-size:22px; background:#fff; border:2px solid #fff;
                                   box-shadow:0 1px 3px rgba(0,0,0,.15); }
    .bs-companion .bs-cname{ font-size:9px; margin-top:3px; color:var(--ink-soft); }
    .bs-companion .hpbar-track{ height:5px; margin-top:2px; }
    .bs-atk-pulse .bs-avatar, .bs-atk-pulse .avatar-emoji, .bs-atk-pulse .bs-duel-avatar{ animation:bsLunge .28s; }
    @keyframes bsShake{ 0%,100%{ transform:translateX(0); } 25%{ transform:translateX(-5px); } 75%{ transform:translateX(5px); } }
    @keyframes bsLunge{ 0%{ transform:translateY(0); } 40%{ transform:translateY(-9px); } 100%{ transform:translateY(0); } }

    /* [2026-09-17 4차] "위아래 높이가 작으면 왼쪽/오른쪽 이렇게만 해줘, 잘리는게 보기싫어"
       요청 -- 세로 공간이 좁을 때(fitBattleScreenHeight가 판단, JS에서 .compact 토글) 기존
       세로 배치(위 몬스터/아래 파티, column+space-between)를 유지하면 둘 다 밀려 잘려
       보이므로, row-reverse로 가로 배치로 전환한다. DOM 순서가 몬스터→파티라 row-reverse를
       쓰면 몬스터가 오른쪽/파티가 왼쪽에 오는 기존 배치(왼쪽아래 플레이어/오른쪽위 몬스터)가
       그대로 유지된다(뒤집히지 않음). 각 행 내부(.bs-monster-row/.bs-party-row) 구조는
       그대로 두고 세로로 쌓아서 좁은 가로폭 안에서도 한눈에 들어오게 한다. */
    .battle-screen.compact{ align-items:center; gap:10px; overflow-y:auto; }
    .battle-screen.compact .bs-top{ width:100%; }
    .battle-screen.compact .bs-power-num{ font-size:14px; min-width:32px; }
    .battle-screen.compact .bs-duel-side{ width:64px; }
    .battle-screen.compact .bs-duel-member{ max-width:58px; }
    .battle-screen.compact .bs-duel-member .bs-duel-avatar-slot .bs-duel-avatar, .battle-screen.compact .bs-duel-member .bs-duel-avatar-slot .avatar-emoji{ width:min(44px, 10vw); height:min(44px, 10vw); }
    /* V1(구버전) 전용 -- row-reverse로 가로 배치(몬스터 오른쪽 위/파티 왼쪽 아래 유지) */
    .battle-screen.compact #battleScreenV1{ flex-direction:row-reverse; justify-content:space-between; }
    .battle-screen.compact .bs-monster-row{ flex-direction:column; align-items:flex-end; gap:6px; }
    .battle-screen.compact .bs-party-row{ flex-direction:column; flex-wrap:nowrap; align-items:flex-start; gap:8px; }

    /* [2026-09-17 2차] "승리시 승리했다고 화면 띄워주면 좋을것같아" 요청 -- 전투화면/보드
       어느 쪽이든 상관없이 위에 살짝 떴다 사라지는 뱃지(showVictoryFlash 참고). 뷰포트 기준
       고정 위치라 카드 안 레이아웃(position:relative 필요 여부)과 무관하게 항상 동작한다. */
    .victory-flash{ position:fixed; top:38%; left:50%; transform:translate(-50%,-50%); z-index:9999;
                      pointer-events:none; opacity:0; transition:opacity .3s ease; }
    .victory-flash.show{ opacity:1; }
    .victory-flash .vf-badge{ background:linear-gradient(135deg,#FFF3D6,#FFE7A8); border:2px solid var(--gold);
                                border-radius:16px; padding:16px 32px; font-size:20px; font-weight:800;
                                color:#6B4A12; box-shadow:0 8px 28px rgba(0,0,0,.2); white-space:nowrap;
                                animation:vfPop .45s cubic-bezier(.34,1.56,.64,1); }
    @keyframes vfPop{ 0%{ transform:scale(.5); opacity:0; } 60%{ transform:scale(1.12); opacity:1; } 100%{ transform:scale(1); } }
    /* [2026-09-14] "전체보기 카드에도 공격력/방어력을 HP처럼 표기해달라" 요청 -- hp-num과
       동일한 스타일 공유. */
    .atk-num, .def-num{ font-size:9px; color:var(--ink-soft); margin-top:1px; }

    .shop-header{ display:flex; align-items:center; justify-content:space-between; gap:8px; flex-wrap:wrap; }
    .shop-header .pp-badge{ margin:0; }
    .free-chip{ font-size:11px; font-weight:700; color:var(--gold); background:var(--gold-soft);
                border-radius:999px; padding:4px 10px; }
    .shop-row .btn-group{ display:flex; gap:5px; flex-shrink:0; }
    .shop-row button.ten{ background:var(--shop); }
    .shop-row button.free{ background:var(--pp); }

    .shop-row{ display:flex; justify-content:space-between; align-items:center; background:#fff; border:1.5px solid var(--line);
               border-radius:12px; padding:9px 12px; margin-bottom:6px; font-size:12px; }
    .shop-row button{ background:var(--gold); color:#fff; border:none; border-radius:10px; padding:6px 12px; font-size:11px; cursor:pointer; }
    .shop-row button:disabled{ opacity:.4; cursor:not-allowed; }
    .shop-row.dice-locked{ opacity:.45; }
    .shop-row.dice-current{ border-color:var(--gold); background:var(--gold-soft); }

    /* [2026-09-21] 전설제작/전설해체 카드 -- 상점 탭. 각 카드는 버튼 하나뿐이고(제작하기/
       해체하기), 실제 선택+실행은 전부 팝업(전체동료보기와 동일한 .detail-overlay 패턴)
       안에서 끝난다("버튼을누르면 팝업에서 눌러서 처리할수있게해줘" 요청). 제작 팝업은
       항목을 누르는 순간 그 자리에서 "제작 중..." 연출 후 성공/실패 결과까지 보여준다. */
    .legend-frag-badge{ font-size:12px; font-weight:800; color:var(--gold); background:var(--gold-soft);
                          border-radius:999px; padding:4px 10px; }
    .legend-craft-btn{ width:100%; background:var(--gold); color:#fff; border:none; border-radius:10px;
                         padding:11px; font-size:13px; font-weight:800; cursor:pointer; margin-top:2px; }
    .legend-craft-btn:disabled{ opacity:.4; cursor:not-allowed; }
    .legend-roster-item{ display:flex; align-items:center; gap:10px; background:#fff; border:1.5px solid var(--line);
                           border-radius:12px; padding:10px 12px; margin-bottom:6px; cursor:pointer; transition:border-color .15s ease, background .15s ease; }
    .legend-roster-item:hover{ border-color:var(--gold); }
    .lri-icon{ font-size:22px; flex-shrink:0; }
    .lri-body{ flex:1; min-width:0; }
    .lri-name{ font-size:13px; font-weight:800; }
    .lri-meta{ font-size:10px; color:var(--ink-soft); margin-top:1px; }
    .lri-flavor{ font-size:10px; color:var(--ink-soft); margin-top:3px; line-height:1.4; }
    .lri-arrow{ font-size:20px; color:var(--ink-soft); flex-shrink:0; }
    .legend-disenchant-item-btn{ flex-shrink:0; background:var(--gold); color:#fff; border:none; border-radius:10px;
                                   padding:8px 12px; font-size:11px; font-weight:800; cursor:pointer; }
    .legend-craft-anim{ text-align:center; font-size:13px; font-weight:700; color:var(--gold); padding:14px 0; }
    .legend-craft-spinner{ display:inline-block; animation:legendSpin 1s linear infinite; margin-right:4px; }
    @keyframes legendSpin{ 0%{ transform:rotate(0deg) scale(1); } 50%{ transform:rotate(180deg) scale(1.3); } 100%{ transform:rotate(360deg) scale(1); } }
    .legend-craft-result{ text-align:center; font-size:13px; font-weight:700; padding:14px 10px; border-radius:12px; margin-top:4px; }
    .legend-craft-result.success{ background:var(--gold-soft); color:#6B4A12; }
    .legend-craft-result.fail{ background:#EFE7D2; color:var(--ink-soft); }
    /* [2026-10-20] 성장형 동료 팝업 */
    .gr-head{ display:flex; gap:12px; align-items:center; margin-bottom:10px; }
    .gr-face{ width:84px; height:84px; border-radius:14px; object-fit:cover; object-position:50% 15%; background:#fff; border:1.5px solid var(--line); }
    .gr-face.avatar-emoji{ display:flex; align-items:center; justify-content:center; font-size:38px; }
    .gr-head-info{ flex:1; min-width:0; }
    .gr-role{ font-size:12px; color:var(--ink-soft); margin-bottom:4px; }
    .gr-name-row{ display:flex; gap:6px; }
    .gr-input{ flex:1; min-width:0; border:1.5px solid var(--line); border-radius:10px; padding:8px 10px; font-size:14px; font-weight:700; background:#fff; color:var(--ink); }
    .gr-save{ width:auto; padding:8px 12px; margin:0; font-size:12px; }
    .gr-sub{ font-size:10.5px; color:var(--ink-soft); margin-top:4px; }
    .gr-prog{ background:#fff; border:1.5px solid var(--line); border-radius:12px; padding:10px 12px; margin-bottom:10px; }
    .gr-prog-top{ display:flex; justify-content:space-between; font-size:12px; margin-bottom:6px; }
    .gr-bar{ height:10px; background:#EFE7D2; border-radius:6px; overflow:hidden; }
    .gr-bar > i{ display:block; height:100%; background:var(--gold); }
    .gr-line{ font-size:12px; margin-top:6px; color:var(--ink-soft); }
    .gr-line b{ color:var(--ink); }
    .gr-title{ font-weight:800; font-size:13px; margin:4px 0 6px; }
    .gr-title small{ font-weight:400; color:var(--ink-soft); font-size:10px; }
    .gr-jobs{ display:grid; grid-template-columns:repeat(3, 1fr); gap:6px; }
    .gr-job{ display:flex; flex-direction:column; align-items:center; gap:2px; padding:8px 4px; border-radius:12px; border:2px solid transparent; background:#F4EDD9; color:var(--ink); font-size:13px; cursor:pointer; }
    .gr-job small{ font-size:9.5px; color:var(--ink-soft); }
    .gr-job .gr-job-em{ font-size:22px; }
    .gr-job.sel{ border-color:var(--gold); background:var(--gold-soft); cursor:default; }
    .gr-job:disabled{ opacity:.85; }
    /* [2026-10-20] ★7 동료 소환/방출 팝업 */
    .s7-intro{ font-size:12px; color:var(--ink-soft); line-height:1.5; margin-bottom:8px; }
    .s7-req{ font-size:13px; padding:6px 10px; border-radius:10px; margin-bottom:4px; background:#F4EDD9; }
    .s7-req.no{ color:#9A4A2E; }
    .s7-req small{ color:var(--ink-soft); }
    .s7-jobs{ display:grid; grid-template-columns:repeat(3, 1fr); gap:6px; margin:10px 0; }
    .s7-job{ display:flex; flex-direction:column; align-items:center; gap:2px; padding:8px 4px; border-radius:12px;
             border:2px solid transparent; background:#F4EDD9; color:var(--ink); font-size:13px; cursor:pointer; }
    .s7-job small{ font-size:10px; color:var(--ink-soft); }
    .s7-job .s7-job-em{ font-size:22px; }
    .s7-job .s7-face{ width:56px; height:56px; border-radius:10px; object-fit:cover; object-position:50% 15%; }
    .s7-job.sel{ border-color:var(--gold); background:var(--gold-soft); }

    /* [2026-10-08] 도감 탭 */
    .cx-chips{ display:flex; flex-wrap:wrap; gap:6px; margin-bottom:8px; }
    .cx-chip{ background:#fff; border:1.5px solid var(--line); border-radius:999px; padding:5px 11px; font-size:11px; font-weight:700;
              color:var(--ink-soft); cursor:pointer; }
    .cx-chip.on{ background:var(--gold); border-color:var(--gold); color:#fff; }
    .cx-tbl-wrap{ overflow-x:auto; margin-top:8px; }
    .cx-tbl{ width:100%; border-collapse:collapse; font-size:11px; }
    .cx-tbl th{ background:var(--parchment-deep); color:var(--ink-soft); font-weight:800; padding:6px 6px; text-align:right; white-space:nowrap; position:sticky; top:0; }
    .cx-tbl th.l, .cx-tbl td.l{ text-align:left; }
    .cx-tbl th.c, .cx-tbl td.c{ text-align:center; }
    .cx-tbl td{ padding:5px 6px; text-align:right; border-bottom:1px solid var(--line); white-space:nowrap; }
    .cx-tbl tr.boss td{ background:#FBE9E4; }
    .cx-note{ font-size:10px; color:var(--ink-soft); line-height:1.5; margin-top:8px; }
    .cx-item{ background:#fff; border:1.5px solid var(--line); border-radius:12px; padding:10px 12px; margin-bottom:8px; }
    .cx-item-head{ display:flex; align-items:center; gap:8px; }
    .cx-item-name{ font-size:14px; font-weight:800; }
    .cx-item-kind{ font-size:10px; color:var(--ink-soft); }
    .cx-item-effect{ font-size:12px; margin-top:6px; }
    .cx-item-flavor{ font-size:10px; color:var(--ink-soft); margin-top:3px; line-height:1.4; }
    .cx-item-stats{ display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
    .cx-stat{ font-size:11px; font-weight:800; background:var(--gold-soft); color:#6B4A12; border-radius:999px; padding:3px 9px; }
    .cx-stat.none{ background:#EFE7D2; color:var(--ink-soft); font-weight:700; }

    /* 미착용 장비 목록(합성/자동장착용 버튼은 그대로, 드래그는 폐지). 직업별로 묶고 그 안에서
       성급 내림차순 정렬, 카드 한 칸이 화면 폭을 다 먹던 걸 party-grid처럼 여러 칸으로
       배치되는 작은 카드로 줄임(개수 많아지면 스캔하기 쉽게). .equip-group-title은 선택
       팝업(#pickerOverlay) 안의 직업별 동료 그룹 헤더에도 재사용한다. */
    .equip-group-title{ font-size:11px; font-weight:800; color:var(--ink-soft); margin:10px 0 6px; }
    .equip-group-title:first-child{ margin-top:0; }
    .equip-grid{ display:grid; grid-template-columns:repeat(auto-fill,minmax(108px,1fr)); gap:8px; }
    .equip-card{ background:#fff; border:1.5px solid var(--line); border-radius:12px; padding:8px;
                 font-size:11px; text-align:center; }
    .equip-card .eq-part{ font-size:18px; }
    .equip-card .eq-grade{ font-weight:800; margin:2px 0 6px; }
    .equip-card .btn-group{ display:flex; gap:4px; justify-content:center; }
    .equip-card button{ background:var(--gold); color:#fff; border:none; border-radius:8px;
                         padding:4px 6px; font-size:10px; cursor:pointer; }
    .equip-card button.ten{ background:var(--shop); }
    /* [2026-09-17 2차] 착용중인 장비 카드 -- 미착용과 구분되게 살짝 다른 배경 + 착용자 표시. */
    .equip-card.worn{ background:var(--gold-soft); border-color:var(--gold); }
    /* [2026-10-22] 인벤토리 탭 -- 장비 카드에 옵션(능력치 보너스/강화/전설 효과), 기타 탭은 아이콘 타일 */
    #panel-inven .equip-grid{ grid-template-columns:repeat(auto-fill,minmax(150px,1fr)); }
    .equip-card .eq-opt{ font-size:10px; line-height:1.45; color:var(--ink-soft); background:var(--parchment); border-radius:8px; padding:4px 6px; margin:0 0 6px; text-align:left; word-break:keep-all; }
    .equip-card .eq-opt .eq-leg{ color:#8a5a00; font-weight:700; }
    .inv-tabs{ display:flex; gap:6px; margin-bottom:10px; }
    /* [2026-10-23] 웹 광산(2D 도트) */
    .mine-overlay{ position:fixed; inset:0; z-index:400; background:#0b0b10; display:none; touch-action:none; user-select:none; -webkit-user-select:none; overscroll-behavior:none; }
    .mine-overlay.open{ display:block; }
    #mineCanvas{ position:absolute; inset:0; width:100%; height:100%; image-rendering:pixelated; image-rendering:crisp-edges; touch-action:none; }
    .mpx{ background:rgba(20,16,30,.82); border:2px solid #f4f0e6; box-shadow:3px 3px 0 rgba(0,0,0,.65); color:#fff; font-weight:800; border-radius:0; }
    .mine-hud-tl{ position:absolute; left:10px; top:10px; pointer-events:none; }
    .mine-coord{ display:inline-block; font:800 18px/1.1 ui-monospace, Consolas, monospace; padding:6px 10px; letter-spacing:1px; } /* 아래 지형 설명이 길어도 좌표 칸은 글자 폭만 */
    .mine-terr{ margin-top:6px; max-width:calc(100vw - 200px); font-size:12px; color:#ffe9a8; text-shadow:1px 1px 0 #000, -1px -1px 0 #000; }
    .mine-hud-tr{ position:absolute; right:10px; top:10px; display:flex; flex-direction:column; align-items:flex-end; gap:6px; }
    .mine-sta{ font:800 15px/1.1 ui-monospace, Consolas, monospace; padding:6px 10px; color:#ffe14d; }
    .mine-regen{ max-width:150px; font-size:11px; line-height:1.35; color:#eee; text-shadow:1px 1px 0 #000, -1px -1px 0 #000; text-align:right; }
    #mineMini{ width:108px; height:108px; image-rendering:pixelated; border:2px solid #f4f0e6; box-shadow:3px 3px 0 rgba(0,0,0,.65); background:#000; cursor:pointer; }
    #mineMini.big{ width:min(78vw,360px); height:min(78vw,360px); }
    .mine-dig{ position:absolute; right:18px; bottom:calc(22px + env(safe-area-inset-bottom)); width:84px; height:84px; border-radius:50%; font-size:40px; line-height:1; background:#ffcf4a; border:3px solid #140d0a; box-shadow:0 5px 0 #8a5a00; cursor:pointer; }
    .mine-dig:active{ transform:translateY(3px); box-shadow:0 2px 0 #8a5a00; }
    .mine-dig:disabled{ filter:grayscale(1); opacity:.6; }
    .mine-btn{ position:absolute; left:10px; padding:8px 12px; font-size:13px; cursor:pointer; }
    .mine-exit{ bottom:calc(22px + env(safe-area-inset-bottom)); }
    .mine-log-btn{ bottom:calc(66px + env(safe-area-inset-bottom)); }
    .mine-home-btn{ bottom:calc(110px + env(safe-area-inset-bottom)); }
    .mine-log{ position:absolute; left:10px; bottom:calc(156px + env(safe-area-inset-bottom)); width:min(270px,70vw); max-height:42vh; overflow:auto; padding:8px; font-size:12px; font-weight:600; display:none; }
    .mine-log-head{ display:flex; justify-content:space-between; align-items:center; margin-bottom:6px; font-weight:800; }
    .mine-log-head button{ font-size:11px; padding:3px 6px; border:2px solid #f4f0e6; background:#3a2f55; color:#fff; cursor:pointer; }
    .mine-log-row{ display:flex; align-items:center; gap:4px; padding:2px 0; }
    .mine-log-row img{ width:16px; height:16px; image-rendering:pixelated; }
    .mine-log-xy{ font-family:ui-monospace, Consolas, monospace; color:#ffe9a8; }
    .mine-msg{ position:absolute; left:50%; top:calc(50% - 140px); transform:translateX(-50%); padding:7px 12px; font-size:13px; display:none; max-width:84vw; text-align:center; pointer-events:none; }
    .mine-loading{ position:absolute; inset:0; display:flex; align-items:center; justify-content:center; color:#fff; font-weight:800; }
    /* [2026-10-23] 대장간(곡괭이 강화) 창 */
    .mine-smith{ position:absolute; left:50%; top:50%; transform:translate(-50%,-50%); width:min(340px,92vw); max-height:80vh; overflow:auto; padding:12px; font-size:13px; display:none; z-index:2; }
    .mine-smith-head{ display:flex; justify-content:space-between; align-items:center; font-size:15px; margin-bottom:6px; }
    .mine-smith-head button, .mine-smith-row button{ font-size:12px; padding:4px 8px; border:2px solid #f4f0e6; background:#3a2f55; color:#fff; cursor:pointer; font-weight:800; }
    .mine-smith-row button:disabled{ opacity:.4; cursor:default; }
    .mine-smith-say{ font-size:12px; font-weight:600; color:#ffe9a8; margin-bottom:8px; }
    .mine-smith-dur{ margin-bottom:8px; }
    .mine-smith-menu{ display:flex; gap:8px; }
    .mine-smith-menu button{ flex:1; display:flex; flex-direction:column; align-items:center; gap:2px; padding:10px 6px; font-size:15px; border:2px solid #f4f0e6; background:#3a2f55; color:#fff; cursor:pointer; font-weight:800; }
    .mine-smith-menu button small{ font-size:11px; font-weight:600; color:#cfc8e8; }
    .mine-smith-menu button:hover, .mine-smith-menu button:focus-visible{ background:#4d3f72; }
    .mine-cost{ display:flex; flex-wrap:wrap; gap:2px 8px; margin-top:3px; font-size:11px; font-weight:700; }
    .mine-cost span{ display:inline-flex; align-items:center; gap:2px; }
    .mine-smith-row .mine-cost img{ width:16px; height:16px; }
    .mine-cost .ok{ color:#a8f0c4; }
    .mine-cost .no{ color:#ffa08f; }
    .mine-worn{ font-size:11px; color:#a8f0c4; }
    .mine-mini-btn{ font-size:11px; padding:2px 6px; border:2px solid #f4f0e6; background:#3a2f55; color:#fff; cursor:pointer; font-weight:800; }
    .mine-smith-sub{ margin-top:12px; padding-top:8px; border-top:2px solid rgba(244,240,230,.45); }
    .mine-smith-sub b{ color:#ffe14d; }
    .mine-smith-dur b{ color:#ffe14d; font-family:ui-monospace, Consolas, monospace; }
    .mine-smith-row{ display:flex; align-items:center; gap:6px; padding:5px 0; border-top:1px solid rgba(244,240,230,.25); }
    .mine-smith-row img{ width:24px; height:24px; image-rendering:pixelated; }
    .mine-smith-row .nm{ flex:1; display:flex; flex-direction:column; line-height:1.25; }
    .mine-smith-row .nm small{ font-size:11px; font-weight:600; color:#cfc8e8; }
    .ore-ico{ width:32px; height:32px; image-rendering:pixelated; }
    .mine-recent{ margin-top:10px; border:1.5px solid var(--line); border-radius:12px; overflow:hidden; }
    .mine-recent-row{ display:flex; align-items:center; gap:6px; padding:6px 10px; font-size:12px; border-top:1px solid var(--line); }
    .mine-recent-row:first-child{ border-top:none; }
    .mine-recent-row img{ width:20px; height:20px; image-rendering:pixelated; }
    .mine-recent-row .xy{ font-family:ui-monospace, Consolas, monospace; color:var(--ink-soft); }
    .mine-recent-row .when{ margin-left:auto; font-size:11px; color:var(--ink-soft); white-space:nowrap; }
    .mine-log-when{ margin-left:auto; padding-left:6px; font-size:11px; color:#cfc6b0; white-space:nowrap; }
    .mshop-bonus{ padding:12px; border-radius:12px; background:#fffaf0; border:1.5px solid var(--gold); margin-bottom:12px; }
    .mshop-bonus b{ font-size:18px; color:var(--gold); }
    .mshop-bonus .sub{ font-size:11px; color:var(--ink-soft); margin-top:4px; line-height:1.5; }
    .mshop-item{ display:flex; align-items:center; gap:10px; padding:10px; border:1.5px solid var(--line); border-radius:12px; margin-bottom:8px; background:#fff; }
    .mshop-item .body{ flex:1; min-width:0; }
    .mshop-item .ttl{ font-weight:800; font-size:13px; }
    .mshop-item .sub{ font-size:11px; color:var(--ink-soft); margin-top:2px; }
    .mshop-item .btns{ display:flex; flex-direction:column; gap:4px; }
    .mshop-item button{ border:none; border-radius:9px; padding:7px 10px; font-weight:800; font-size:12px; background:var(--gold); color:#fff; cursor:pointer; white-space:nowrap; }
    .mshop-item button.max{ background:#8a6a2a; }
    .mshop-item button:disabled{ background:#c9c3b6; cursor:not-allowed; }
    .ore-mine-card{ display:flex; align-items:center; justify-content:space-between; gap:10px; padding:12px; margin-bottom:12px; border:1.5px solid var(--line); border-radius:12px; background:#fffaf0; }
    .ore-mine-card .ore-sta{ font-weight:800; }
    .ore-mine-card .ore-sub{ font-size:11px; color:var(--ink-soft); margin-top:2px; }
    .ore-mine-card button{ border:none; border-radius:10px; padding:10px 14px; font-weight:800; background:var(--gold); color:#fff; cursor:pointer; white-space:nowrap; }
    .inv-tabs button{ flex:1; border:1.5px solid var(--line); background:#fff; border-radius:10px; padding:7px 4px; font-size:12px; font-weight:800; color:var(--ink-soft); cursor:pointer; }
    .inv-tabs button.on{ background:var(--gold); border-color:var(--gold); color:#fff; }
    .inv-etc-title{ font-size:12px; font-weight:800; color:var(--ink-soft); margin:12px 0 6px; }
    .inv-etc-title:first-child{ margin-top:0; }
    .inv-etc-grid{ display:grid; grid-template-columns:repeat(auto-fill,minmax(150px,1fr)); gap:8px; }
    .inv-item{ display:flex; gap:8px; align-items:center; background:#fff; border:1.5px solid var(--line); border-radius:12px; padding:8px; }
    .inv-ico{ flex:none; width:44px; height:44px; border-radius:10px; display:flex; align-items:center; justify-content:center; font-size:24px; background:var(--parchment); border:2px solid var(--line); position:relative; }
    .inv-ico.t1{ border-color:#B9B3A6; } .inv-ico.t2{ border-color:#5FAE6E; background:#EEF7EF; } .inv-ico.t3{ border-color:#4C86C8; background:#EDF3FB; }
    .inv-ico.t4{ border-color:#9B5FD1; background:#F4ECFB; } .inv-ico.legend{ border-color:var(--gold); background:#FFF4D6; }
    .inv-ico .inv-qty{ position:absolute; right:-6px; bottom:-6px; background:var(--ink); color:#fff; border-radius:999px; font-size:10px; font-weight:800; padding:0 5px; line-height:16px; }
    .inv-body{ min-width:0; flex:1; }
    .inv-name{ font-size:12px; font-weight:800; }
    .inv-sub{ font-size:10px; color:var(--ink-soft); margin:1px 0 4px; }
    .inv-body button{ border:none; border-radius:8px; background:var(--gold); color:#fff; font-size:10px; font-weight:700; padding:4px 8px; cursor:pointer; }
    .equip-card .eq-worn-by{ font-size:9.5px; color:var(--ink-soft); margin-bottom:6px; font-weight:600; }

    /* [2026-09-06] 동료변경/장비변경 선택 팝업(#pickerOverlay), "전체 동료보기"/"전체
       아이템보기" 팝업 공용 -- detail-overlay/detail-card를 그대로 재사용하고 내부 콘텐츠만
       아래 클래스로 새로 구성. [2026-09-07] "필터링에 따라 창 크기가 바뀐다, 고정 크기로
       해달라" 요청으로 내용 양과 무관하게 항상 같은 높이(넘치면 카드 안에서만 스크롤, 이미
       있던 detail-card의 overflow:auto가 그대로 처리)로 고정. */
    .sheet-card{ max-width:340px; height:480px; max-height:85vh; }
    .sheet-title{ font-size:15px; font-weight:800; margin:2px 0 12px; padding-right:22px; }
    /* [2026-09-12] 일괄합성 버튼 -- 시트 타이틀과 한 줄에 나란히(타이틀 자체 마진은 없애고
       이 줄에 몰아줌), ✕ 닫기 버튼과는 안 겹치게 우측 여백 확보. */
    .sheet-title-row{ display:flex; align-items:center; justify-content:space-between; gap:8px;
                       margin:2px 22px 12px 0; }
    .sheet-title-row .sheet-title{ margin:0; padding-right:0; }
    .synth-all-btn{ flex:0 0 auto; border:none; border-radius:999px; padding:6px 12px;
                     font-size:11px; font-weight:800; cursor:pointer; color:#fff;
                     background:linear-gradient(180deg,#8E6FE0,#6B4FC4); box-shadow:0 2px 0 #503A9B; }
    .synth-all-btn:active{ transform:scale(.96); }
    .sheet-row{ display:flex; align-items:center; justify-content:space-between; gap:8px; background:#fff;
                border:1.5px solid var(--line); border-radius:12px; padding:10px 12px; margin-bottom:6px;
                font-size:12px; cursor:pointer; text-align:left; }
    .sheet-row:active{ transform:scale(.98); }
    .sheet-row.current{ border-color:var(--gold); background:var(--gold-soft); }
    .sheet-row.readonly{ cursor:default; }
    .sheet-row.danger .sr-main{ color:var(--combat); }
    .sheet-row .sr-main{ font-weight:800; color:var(--ink); }
    .sheet-row .sr-sub{ font-size:10px; color:var(--ink-soft); white-space:nowrap; }
    .sheet-empty{ font-size:11px; color:var(--ink-soft); text-align:center; padding:10px 0; }

    /* [2026-09-06] 필터 칩 -- 보유동료/미착용장비/선택 팝업 후보목록 공용(직업·성급·부위). */
    .filter-row{ display:flex; flex-wrap:wrap; gap:5px; margin-bottom:8px; }
    .filter-chip{ background:#fff; border:1.5px solid var(--line); border-radius:999px; font-size:10px;
                  padding:4px 10px; cursor:pointer; color:var(--ink-soft); white-space:nowrap; }
    .filter-chip.on{ background:var(--gold); border-color:var(--gold); color:#fff; font-weight:800; }
    .mini-btn{ background:#fff; border:1.5px solid var(--line); border-radius:8px; font-size:10px;
               padding:3px 10px; cursor:pointer; color:var(--combat); white-space:nowrap; flex-shrink:0; }
    .mini-btn:hover{ border-color:var(--combat); }

    .ach-row{ display:flex; gap:8px; align-items:flex-start; background:#fff; border:1.5px solid var(--line);
              border-radius:12px; padding:8px 12px; margin-bottom:6px; font-size:12px; }
    .ach-row.done{ background:#fffbe8; border-color:var(--gold); }

    .msg-row{ background:#fff; border:1.5px solid var(--line); border-radius:12px; padding:8px 12px;
               margin-bottom:6px; font-size:12px; }
    .msg-row-head{ display:flex; align-items:baseline; justify-content:space-between; gap:8px; margin-bottom:4px; }
    .msg-row-req{ font-weight:800; color:var(--combat); }
    .msg-row-time{ font-size:10px; color:var(--ink-soft); white-space:nowrap; }
    .msg-row-res{ white-space:pre-line; color:var(--ink-soft); line-height:1.5; }

    /* [2026-09-17] "핸드폰으로 할때 토스트되는 전투메시지가 화면을 너무 가려, 구석에 나오면
       좋겠다" 요청 -- 처음엔 모바일(480px 이하)에서만 우상단 구석으로 옮겼는데, [후속]
       "데스크탑도 구석에 뜨도록해줘" 요청으로 화면 크기 무관하게 항상 구석에 작게 뜨도록
       통일(중앙 하단에 넓게 깔리던 기존 방식은 완전히 제거). [2026-09-17 3차] "우상단이
       주사위 버튼(sticky .dice-controls)을 가려서 못 누른다" 신고로 좌측 하단으로 재배치
       (하단 고정 네비 .dock 위로 여유를 둠).*/
    /* [2026-09-19] "전투시 텍스트로 동료쪽이 가려져(모바일은 더 크게)" 신고 -- 이 토스트가
       매 DICE 응답의 전체 텍스트를 그대로 보여주는데(다중타격/보호막 등 줄이 많으면 10줄
       넘게 늘어남) 높이 제한이 전혀 없어서, 길어질수록 .battle-screen 하단의 파티 줄을
       그만큼 더 많이 덮었다. max-height+내부 스크롤로 상한을 둬서 아무리 길어도 화면을
       덮는 범위 자체는 고정폭 이상으로 안 커지게 함(중요한 앞부분 줄은 그대로 보이고,
       나머지는 안에서 스크롤). */
    .msg-toast{ position:fixed; left:10px; right:auto; bottom:205px; top:auto; transform:none;
                background:var(--ink); color:#fff; padding:9px 12px; border-radius:12px;
                font-size:11px; line-height:1.6; max-width:min(62vw,300px);
                max-height:150px; overflow-y:auto;
                text-align:left; z-index:50; box-shadow:0 6px 18px rgba(0,0,0,.3); display:none;
                white-space:pre-line; opacity:.96; }

    /* [2026-10-23] 하단 메뉴가 탑 마을 그림의 건물/라벨(z-index 2~3)과 주사위 영역(15)에 덮이던 것 -- 그보다 위, 팝업(200+)보다는 아래 */
    .dock{ position:fixed; left:0; right:0; bottom:0; z-index:100; display:flex; justify-content:center;
           padding:10px 14px calc(10px + env(safe-area-inset-bottom));
           background:linear-gradient(180deg,rgba(251,243,223,0),var(--parchment) 22%); }
    /* [2026-10-21] "옆으로 쫙 있는 메뉴를 두 줄, 한 줄에 4칸씩" 요청 -- 탑/편성/상점/빈칸, 업적/도감/메시지/설정. */
    /* [2026-10-22] "8개의 탭메뉴가 영역분리가 되면 좋겠어(테두리)" 요청 -- 칸마다 테두리를 두르고, 선택된 칸은 금색 테두리+배경. 빈칸 자리는 인벤토리 탭. */
    .dock-inner{ max-width:900px; width:100%; display:grid; grid-template-columns:repeat(4,1fr); gap:6px; background:#fff;
                 border:2px solid var(--line); border-radius:20px; padding:7px; box-shadow:0 10px 26px -10px rgba(46,36,64,.4); }
    .dbtn{ display:flex; flex-direction:column; align-items:center; gap:2px; background:#FFFCF3; border:1.5px solid var(--line);
           border-radius:12px; padding:5px 4px 4px; color:var(--ink-soft); font-size:10px; font-weight:700; cursor:pointer; }
    .dbtn-empty{ visibility:hidden; pointer-events:none; }
    .dbtn .d-icn{ font-size:18px; }
    .dbtn:hover{ background:var(--parchment-deep); }
    .dbtn.active{ color:var(--gold); border-color:var(--gold); background:var(--gold-soft); }
    .dbtn.primary{ background:linear-gradient(180deg,#E4633F,var(--combat)); color:#fff; box-shadow:0 3px 0 #A93A28; }
    .dbtn.primary .d-icn{ font-size:22px; }
    .dbtn:disabled{ opacity:.35; cursor:not-allowed; }
    .dbtn:disabled:hover{ background:transparent; }

    /* 층이동: 부루마불 보드 옆에 세우는 "탑" 모양 -- 9층(보스)이 맨 위, 0층(마을)이 맨 아래에
       오도록 DOM을 9->0 순서로 채워서 실제 탑처럼 보이게 한다. 칸 하나가 그 층 하나. */
    .tower-nav{ display:flex; flex-direction:column; gap:5px; max-width:320px; margin:0 auto; }
    .tower-floor{ display:flex; align-items:center; justify-content:space-between; gap:8px;
                  padding:9px 14px; border:2px solid var(--line); border-radius:10px; background:#fff;
                  cursor:pointer; font-size:12px; font-weight:700; color:var(--ink); }
    .tower-floor:active{ transform:scale(.97); }
    .tower-floor .tf-left{ display:flex; align-items:center; gap:8px; }
    .tower-floor .tf-num{ width:22px; height:22px; border-radius:50%; background:var(--parchment-deep);
                           display:flex; align-items:center; justify-content:center; font-size:11px;
                           flex-shrink:0; }
    .tower-floor .tf-kind{ font-size:10px; color:var(--ink-soft); font-weight:600; }
    .tower-floor .tf-explored{ font-size:10px; color:var(--pp); font-weight:800; }
    .tower-floor.here{ border-color:var(--gold); background:var(--gold-soft); }
    .tower-floor.here .tf-num{ background:var(--gold); color:#fff; }
    .tower-floor.village{ border-left:5px solid var(--village); }
    .tower-floor.boss{ border-left:5px solid var(--boss); }
    .tower-floor.locked{ opacity:.4; cursor:not-allowed; }

    .confirm-card{ max-width:280px; text-align:center; }
    .confirm-msg{ font-size:14px; margin:6px 0 18px; line-height:1.6; }
    .confirm-btns{ display:flex; gap:10px; }
    .confirm-btns button{ flex:1; padding:11px; border-radius:12px; border:none; font-size:13px;
                            font-weight:800; cursor:pointer; }
    .confirm-btns .btn-yes{ background:var(--gold); color:#fff; }
    .confirm-btns .btn-no{ background:#fff; border:1.5px solid var(--line); color:var(--ink-soft); }

    /* 보드 + 층이동 탑: 모바일에선 위아래로 쌓고(기존과 동일), PC처럼 넓은 화면에선 부루마불판
       옆에 나란히 두는 게 낫다는 요청으로 폭 700px 이상에서만 가로 배치로 전환. */
    .board-row{ display:flex; flex-direction:column; gap:10px; }
    .board-card{ flex:1 1 auto; min-width:0; position:relative; }
    .tower-nav-card{ flex:0 0 auto; }
    @media (min-width:700px){
      .board-row{ flex-direction:row; align-items:flex-start; }
      .tower-nav-card{ width:260px; flex:0 0 260px; }
      /* [2026-09-07] "PC 버전에선 가로 사이즈를 좀 많이 늘려줘" 요청 -- 편성 탭 팝업들이
         모바일 폭(340/520px)에 맞춰져 있어 PC에서는 유독 좁아 보였다. */
      .sheet-card{ max-width:460px; }
      .wide-card{ max-width:820px; }
    }

    @media (max-width:600px){
      body{ padding-top:8px; }
    }
  </style>
</head>
<body>
<div class="wrap">

  <div class="top-controls">
    <input type="text" id="userNameInput" placeholder="유저명 입력">
    <button class="btn-query" onclick="TW.load()">조회</button>
    <!-- [2026-09-07] "공지를 다시 볼 수 있는 버튼" 요청 -- 언제든 눌러서 최신 공지를 다시 볼 수 있음. -->
    <button class="btn-notice" onclick="TW.reopenNotice()" title="공지 다시 보기">📢</button>
  </div>

  <div class="card statusbar" id="statusCard">
    <div>
      <div class="who-name" id="whoName">-</div>
      <div class="who-floor" id="whoFloor">유저명을 입력하고 조회를 눌러주세요</div>
    </div>
    <div class="pp-badge">💰 <span class="val" id="ppVal">0</span></div>
  </div>

  <div class="panel active" id="panel-board">
    <div class="board-row">
      <div class="card board-card">
        <!-- [2026-09-14] "미니맵+탭 확대가 마음에 안 든다, 미니맵 생기기 전으로 롤백" 요청 --
             09-13에 넣었던 미니맵/확대모달을 걷어내고 다시 칸그리드를 카드 안에 직접
             보여준다(PC 폭에서는 board-card가 차지하는 실제 너비만큼 자연스럽게 넓어짐 --
             모달의 wide-card 최대폭 제한이 없어짐). [2026-09-14 3차 수정] 맵뷰 스크롤/이동
             버튼(맨 위로/내 위치/맨 아래로)은 아래 .tower-viewport-wrap 안에 정적 마크업으로
             둔다(위 CSS .board-track-controls 주석 참고 -- 스크롤해도 항상 같은 자리에 뜸). -->
        <div class="card-title-row">
          <div class="card-title">보드</div>
        </div>
        <!-- [2026-09-12] "최소주사위 줄에 칸그리드 위쪽이 가려서 안 보인다" 신고로,
             기존 절대좌표 오버레이 3개(dice-overlay/dice-enhance-overlay/board-roll-btn)를
             칸그리드 위에 겹쳐 띄우던 방식을 버리고, 이 .dice-controls 박스 하나로 묶어서
             card-title 아래 정상 흐름에 둔다(위 .dice-controls CSS 주석 참고) --
             .tower-viewport가 항상 이 박스 아래로 밀려나서 칸그리드를 절대 가리지 않는다. -->
        <div class="dice-controls">
          <div class="dice-controls-rows">
            <!-- [2026-09-21] "이동한도도 맵이동하는곳에 표기하면 좋을거같아" 요청 -- 오늘 이동
                 사용량/한도(전투 턴은 이 한도와 무관하므로 여기 안 포함). renderMoveLimit 참고. -->
            <div class="move-limit-badge" id="moveLimitBadge" style="display:none;"></div>
            <!-- [2026-09-05] 주사위 교체를 상점탭에서 여기로 옮김 -- 해금된 것만 버튼으로
                 눌러 즉시 교체(자동구매형태, 별도 확인 없음). renderDiceOverlay 참고. -->
            <div class="dice-overlay" id="diceOverlay"></div>
            <!-- [2026-09-08] 30/50/60/70/80/90층 마을 도착 보상: 주사위 강화(+, 최소눈금 상승)/
                 마이너스 주사위(-, 최소눈금 하강) 상점 -- 한 줄짜리 숫자 스트립(-6..0..+6)으로
                 표시. 0이 고정 기준점, 오른쪽은 강화(+)/왼쪽은 마이너스(-) 단계, 각각
                 "다음 한 단계"만 눌러서 PP로 구매(순차 구매, 건너뛰기 불가).
                 renderDiceEnhanceRow 참고. -->
            <div class="dice-enhance-overlay" id="diceEnhanceOverlay"
                 title="주사위 눈금의 '최소값'을 조정합니다 (최대값은 그대로) -- 오른쪽(+)은 낮은 눈을 걸러내는 강화, 왼쪽(-)은 반대로 더 낮은 눈까지 나오게 하는 마이너스 주사위. 기본값은 +0."></div>
          </div>
          <!-- [2026-09-09] 하단 도크의 "주사위" 버튼을 없애고 굴리기 동작을 여기로 옮김 --
               updateDiceButtonState() 참고. [2026-09-12] 최대/최소 두 줄 옆, 그 두 줄 높이를
               합친 만큼 늘어나도록 이동. -->
          <button type="button" class="board-roll-btn" id="boardRollBtn" onclick="TW.action('DICE','')">
            <span class="broll-icn">🎲</span>주사위
          </button>
        </div>
        <!-- [2026-09-17] 전투 중엔 이 화면이 뜨고 아래 .tower-viewport-wrap(칸그리드)는
             숨겨진다(updateBattleScreen 참고).
             [2026-09-21] "전투화면을 삼국지 대전화면 스타일로" 요청으로 재설계했다가, 같은 날
             "이전버전은 v1, 지금은v2로 해서 유저가 선택한걸 db에 저장/표시해달라" 요청으로
             구버전(포켓몬 스타일)을 #battleScreenV1로 되살려 나란히 두고 버전 전환 버튼으로
             고른다(state.battleScreenVersion, TBOT_S5_USER_PROGRESS.BATTLE_SCREEN_VERSION).
             배경/파티클/크기(bg 사이클링, fitBattleScreenHeight, crossfadeBoardView)는 바깥
             #battleScreen 공용, 안쪽 V1/V2 콘텐츠만 토글. updateBattleScreen() 참고. -->
        <div id="battleScreen" class="battle-screen bs-bg-0" style="display:none;">
          <div class="bs-particle bs-particle-a"></div>
          <div class="bs-particle bs-particle-b"></div>
          <!-- [2026-09-21] "v1,v2 버전 클릭이 잘안되, 두개다 버튼만들어서 눌려있는걸 표기해줘"
               요청 -- 토글 버튼 하나 대신 V1/V2 각각 버튼을 두고 현재 선택된 쪽에 .active. -->
          <div class="bs-version-btns">
            <button type="button" class="bs-version-btn" id="bsVersionBtnV1" onclick="TW.setBattleScreenVersion('V1')">V1</button>
            <button type="button" class="bs-version-btn" id="bsVersionBtnV2" onclick="TW.setBattleScreenVersion('V2')">V2</button>
          </div>

          <!-- ===== V2(삼국지 대전화면 스타일, 기본값) ===== -->
          <div id="battleScreenV2">
            <!-- [2026-09-21] "동료들이 위쪽에 나와야하는데 아래쪽에 나온다" 요청 -- 파티
                 로스터 줄(.bs-party-row)이 .bs-top(VS 대전 영역) 뒤(화면 맨 아래)에 있어서
                 몬스터보다도 한참 밑에 나왔다. 순서만 앞으로 옮겨서 화면 상단에 파티가
                 먼저 보이게 한다(내용/로직은 그대로, DOM 순서만 이동). -->
            <!-- [2026-10-20] 위쪽 동료 줄(bsPartyRowV2)은 대치 장면의 파티 대열(bsDuelParty)과 같은 정보라 중복이어서 제거했다. -->
            <div class="bs-top">
              <!-- [2026-09-21] "상단 체력바를 절반씩 영역을 차지하면 좋겠어" 요청 -- 예전엔
                   두 fill의 width가 서로 다른 값(각자 %)이라 트랙 안에서 비율이 안 맞았는데,
                   이제 좌/우 절반(.bs-power-half)을 고정폭으로 나누고 그 안에서 각자 채워지게
                   바꿨다(파티는 왼쪽에서, 몬스터는 오른쪽에서 안쪽/바깥쪽으로 자연스럽게). -->
              <div class="bs-power-bar">
                <div class="bs-power-num bs-power-num-party" id="bsPartyPowerNum">0</div>
                <div class="bs-power-track">
                  <div class="bs-power-half bs-power-half-party"><div class="bs-power-fill" id="bsPowerFillParty" style="width:100%"></div></div>
                  <div class="bs-power-half bs-power-half-monster"><div class="bs-power-fill" id="bsPowerFillMonster" style="width:100%"></div></div>
                  <div class="bs-power-vs">VS</div>
                </div>
                <div class="bs-power-num bs-power-num-monster" id="bsMonsterPowerNum">0</div>
              </div>
              <div class="bs-duel-row">
                <div class="bs-duel-party" id="bsDuelParty"></div>
                <div class="bs-duel-vs-badge">對</div>
                <div class="bs-duel-side">
                  <div class="bs-monster-sprite" id="bsMonsterSprite">👹</div>
                  <div class="bs-duel-name" id="bsMonsterName">몬스터</div>
                </div>
              </div>
              <div class="bs-stat-table">
                <div class="bs-stat-col bs-stat-col-left">
                  <div class="bs-stat-hdr">PARTY</div><div class="bs-stat-val" id="bsLeadLevel">-</div>
                  <div class="bs-stat-hdr">POWER</div><div class="bs-stat-val" id="bsLeadPower">-</div>
                  <div class="bs-stat-hdr">GUARD</div><div class="bs-stat-val" id="bsLeadGuard">-</div>
                  <div class="bs-stat-hit">공격 합계 · 방어 평균</div>
                </div>
                <div class="bs-stat-mid">반격<br>방어</div>
                <div class="bs-stat-col bs-stat-col-right">
                  <div class="bs-stat-hdr">LEVEL</div><div class="bs-stat-val" id="bsMonsterLevel">-</div>
                  <div class="bs-stat-hdr">POWER</div><div class="bs-stat-val" id="bsMonsterPower">-</div>
                  <div class="bs-stat-hdr">GUARD</div><div class="bs-stat-val" id="bsMonsterGuard">-</div>
                  <div class="bs-stat-hit">HIT 100%</div>
                </div>
              </div>
            </div>
          </div>

          <!-- ===== V1(구버전, 포켓몬 스타일) ===== -->
          <div id="battleScreenV1" style="display:none;">
            <div class="bs-monster-row">
              <div class="bs-monster-sprite-v1" id="bsMonsterSpriteV1">👹</div>
              <div class="bs-monster-info">
                <div class="bs-name" id="bsMonsterNameV1">몬스터</div>
                <div class="hpbar-track"><div class="hpbar-fill" id="bsMonsterHpFillV1" style="width:100%"></div></div>
                <div class="hp-num" id="bsMonsterHpNumV1"></div>
              </div>
            </div>
            <div class="bs-party-row" id="bsPartyRowV1"></div>
          </div>
        </div>
        <div class="last-battle-bar" id="lastBattleBar"></div>
        <div class="tower-viewport-wrap" id="towerViewportWrap">
          <div class="tower-viewport" id="towerViewport">
            <div class="tower-track" id="towerTrack">
              <div style="color:var(--ink-soft);font-size:12px;">데이터를 불러오는 중...</div>
            </div>
          </div>
          <!-- [2026-09-14 3차 수정] "스크롤을 조금만 내려도 버튼을 못 누른다, 플로팅시켜서
               항상 누를 수 있게 해달라, 맨 위로도 추가해달라" 요청 -- #towerViewport의
               "형제"로 둬서(내부 스크롤과 무관) position:absolute로 항상 같은 자리에 뜸. -->
          <div class="board-track-controls">
            <button type="button" class="board-track-btn" onclick="TW.scrollToTop()">⬆️ 맨 위로</button>
            <button type="button" class="board-track-btn" onclick="TW.scrollToHere()">📍 내 위치</button>
            <button type="button" class="board-track-btn" onclick="TW.scrollToBottom()">⬇️ 맨 아래로</button>
          </div>
        </div>
        <div class="legend">
          <span class="legend-chip"><span class="legend-dot" style="background:var(--combat)"></span>전투</span>
          <span class="legend-chip"><span class="legend-dot" style="background:var(--treasure)"></span>💎 보물상자</span>
          <span class="legend-chip"><span class="legend-dot" style="background:var(--pp)"></span>🍀 럭키</span>
          <span class="legend-chip"><span class="legend-dot" style="background:var(--trap)"></span>함정</span>
          <span class="legend-chip"><span class="legend-dot" style="background:var(--special)"></span>특수</span>
          <span class="legend-chip"><span class="legend-dot" style="background:var(--elite)"></span>💪 강화몬스터</span>
          <span class="legend-chip"><span class="legend-dot" style="background:var(--gold)"></span>계단↑(위)</span>
          <span class="legend-chip"><span class="legend-dot" style="background:var(--village)"></span>계단↓(아래)</span>
          <span class="legend-chip"><span class="legend-dot" style="background:#D8CDB4"></span>미발견</span>
        </div>
      </div>
      <div class="card tower-nav-card">
        <div class="card-title">🗼 층 이동 (눌러서 이동, 탐사완료 층엔 ✅ 표시)</div>
        <div class="tower-nav" id="towerNav"></div>
      </div>
    </div>
    <div class="card" id="autoHuntCard" style="margin-top:10px; display:none;">
      <div class="card-title">🔥 자동사냥</div>
      <div id="autoHuntBox" style="font-size:12px; color:var(--ink-soft); line-height:1.7;"></div>
    </div>
  </div>

  <!-- 파티(동료 3명, 80층 도달 시 4명)와 그 동료들에게 장착하는 장비는 한 화면에서 같이 관리 -->
  <div class="panel" id="panel-party">
    <div class="card">
      <div class="card-title-row">
        <div class="card-title">파티</div>
        <button type="button" class="btn-unassign-all" onclick="TW.action('PARTY_UNASSIGN_ALL')">일괄해제</button>
      </div>
      <div class="party-slots" id="partySlots"></div>
    </div>
    <!-- [2026-09-06] "보유동료/파티장비현황/미착용장비는 없애고 버튼으로 이관" 요청 -- 안의
         기능(renderPartyGrid/renderEquipList)은 그대로 두고, 항상 보이던 카드들을 이 버튼
         2개 뒤 팝업(#allCompanionsOverlay/#allEquipOverlay)으로 옮김. 파티 장비 현황은
         슬롯 카드에 이미 다 보이는 중복 정보라 [2026-09-07]에 완전히 제거. -->
    <div class="card" style="margin-top:10px; display:flex; gap:8px;">
      <button type="button" class="browse-btn" onclick="TW.openAllCompanions()">👥 전체 동료보기</button>
    </div>
  </div>

  <!-- [2026-10-22] "편성 탭의 전체 아이템보기를 없애고 인벤토리 탭을 만들어줘" 요청 -- 예전 #allEquipOverlay 팝업의 장비 목록을 탭으로 옮기고
       카드에 옵션(능력치 보너스/강화/전설 효과)을 보여준다. 기타 탭에는 강화권/전설의 조각/뽑기권/선택권. -->
  <div class="panel" id="panel-inven">
    <div class="card">
      <div class="card-title-row">
        <div class="card-title">🧳 인벤토리</div>
        <button class="synth-all-btn" id="invSynthAllBtn" onclick="TW.action('EQUIP_SYNTH_ALL','')">⚗️ 일괄합성</button>
      </div>
      <div class="inv-tabs">
        <button type="button" class="on" data-inv="equip" onclick="TW.setInvTab('equip')">장비 · 악세서리</button>
        <button type="button" data-inv="etc" onclick="TW.setInvTab('etc')">기타</button>
        <button type="button" data-inv="ore" style="display:none;" onclick="TW.setInvTab('ore')">광석</button>
      </div>
      <div id="invEquipPane">
        <div id="equipListFilters"></div>
        <div id="equipListBox"></div>
      </div>
      <div id="invEtcPane" style="display:none;"><div id="invEtcBox"></div></div>
      <div id="invOrePane" style="display:none;"><div id="invOreBox"></div></div>
    </div>
  </div>

  <div class="panel" id="panel-shop">
    <div class="card">
      <div class="shop-header">
        <div class="pp-badge">💰 <span class="val" id="shopPpVal">0</span></div>
        <span class="free-chip" id="shopFreeChip" style="display:none;"></span>
      </div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">동료 계약서</div>
      <div id="companionGachaList"></div>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">장비 보물상자</div>
      <div id="equipGachaList"></div>
    </div>
    <!-- [2026-09-21] "전설제작 ui도 상점내에 만들어주고" 요청 -> "전설제작 리스트로 쭉 나오는게
         아니고, 전체동료리스트처럼 팝업을띄워서 거기서 선택" 요청 -> "전설제작 아래에 전설해체를
         따로 만들어줘. 전설제작/전설해체 모두 제작하기/해체하기 버튼 하나만 넣어주고 버튼을
         누르면 팝업에서 눌러서 처리할수있게해줘(팝업은전체동료보기와 동일한 ui로)" 요청으로
         최종 정리 -- 카드 각각 버튼 하나만, 실제 선택+실행(제작/해체)은 전부 팝업
         (#legendRosterOverlay/#legendDisenchantOverlay, allCompanionsOverlay와 동일한
         .detail-overlay 패턴) 안에서 끝난다. 정식 오픈 전까지는 서버(craftLegendary)가
         NO_COOLDOWN_YN 계정만 허용하고 나머지는 안내 메시지만 반환하므로, 카드 자체는 누구
         에게나 보이되 실제 동작은 서버가 게이트한다. -->
    <div class="card" style="margin-top:10px;">
      <div class="card-title-row">
        <div class="card-title">✨ 전설제작</div>
        <div class="legend-frag-badge">🧩 <span id="legendCraftFragVal">0</span>/10</div>
      </div>
      <!-- [2026-09-21] "10개가 있어야 누를수있게 해줘" 요청 -- 조각 10개 미만이면 버튼 자체를
           비활성화(renderLegendaryCraftCard가 매번 상태조회 후 토글). -->
      <button type="button" class="legend-craft-btn" id="legendCraftBtn" disabled onclick="TW.openLegendRoster()">제작하기</button>
    </div>
    <div class="card" style="margin-top:10px;">
      <div class="card-title">🔨 전설해체</div>
      <button type="button" class="legend-craft-btn" id="legendDisenchantBtn" disabled onclick="TW.openLegendDisenchant()">해체하기</button>
    </div>
    <!-- [2026-10-08] 전설장비 강화 -- 전설의조각을 넣어 +1씩(수치 제한 없음), 실패해도 수치는 유지. -->
    <div class="card" style="margin-top:10px;">
      <div class="card-title">⬆️ 전설강화</div>
      <button type="button" class="legend-craft-btn" id="legendEnhanceBtn" disabled onclick="TW.openLegendEnhance()">강화하기</button>
    </div>
    <!-- [2026-10-20] ★7 동료 소환 -- 업적 "전설의 시작"(한계돌파 +6 ★5 동료 2체)을 달성하면 원하는 직업의 ★7 동료 1명을 소환(재료/조각 소모 없음). 방출 후 재소환 가능. -->
    <div class="card" style="margin-top:10px;">
      <div class="card-title">🌟 7성 동료 소환</div>
      <button type="button" class="legend-craft-btn" id="star7Btn" onclick="TW.openStar7()">소환 / 방출</button>
    </div>
    <!-- [2026-10-20] 성장형 동료(☆1) -- 모두에게 무료, 1~100층 완전탐사로 영구 성장, 직업 자유 변경, 이름 변경, 장비 직업 제한 없음. -->
    <div class="card" style="margin-top:10px;">
      <div class="card-title">🌱 성장형 동료 (☆1)</div>
      <button type="button" class="legend-craft-btn" id="growthBtn" onclick="TW.openGrowth()">확인 / 직업·이름 변경</button>
    </div>
    <!-- [2026-09-15] "장비뽑기 말고 악세뽑기를 추가해서 목걸이/반지/팔찌" 요청 -- 장비 상자와
         같은 4단계(초급/중급/상급/최상급) 구조지만 해금층(50/60/70/80)과 가격이 달라 별도
         카드/목록으로 분리(renderShop의 accessoryGachaList 참고). -->
    <div class="card" style="margin-top:10px;">
      <div class="card-title">💍 악세서리 상자 (목걸이·반지·팔찌)</div>
      <div id="accessoryGachaList"></div>
    </div>
    <!-- "편성 탭에 있던 완전탐사 선택권을 상점에서 쓸 수 있게 해달라" 요청으로 이동. -->
    <div class="card" id="ticketCard" style="margin-top:10px; display:none;">
      <div class="card-title">🎁 완전탐사 선택권 (10층 구간 앞/뒤 4개층 전부 완전탐사 시 지급, 직업을 골라 확정 획득)</div>
      <div id="ticketBox"></div>
    </div>
    <!-- [2026-09-05] 주사위 교체는 여기(상점탭)가 아니라 '탑' 탭 보드 칸그리드 위 오버레이로
         옮겼다(더 유동적으로 바로 굴릴 수 있게 해달라는 요청) -- diceListBox/renderDiceShop은
         이제 그 오버레이(#diceOverlay)를 채운다. -->
    <div class="card" style="margin-top:10px;">
      <div class="card-title">📊 스탯 강화</div>
      <div id="statShopBox"></div>
    </div>
  </div>

  <div class="panel" id="panel-ach">
    <div class="card">
      <div class="card-title" id="achTitle">업적</div>
      <div id="achList"></div>
    </div>
  </div>

  <!-- [2026-10-08] 도감 탭 -- 몬스터도감(예전 /로그의 몬스터 정보 표) / 아이템도감(★7 전설장비). 앞으로 도감 기능을 이 안에 더 추가한다. -->
  <div class="panel" id="panel-codex">
    <div class="card">
      <div class="card-title">📖 도감</div>
      <div class="cx-chips" id="cxMain">
        <button type="button" class="cx-chip on" data-cx="mon">👹 몬스터도감</button>
        <button type="button" class="cx-chip" data-cx="item">🗡️ 아이템도감</button>
      </div>

      <div id="cxMonster">
        <div class="cx-chips" id="cxBands"></div>
        <div class="cx-chips" style="align-items:center;">
          <input type="text" id="cxFloor" placeholder="층 번호 (예: 87)" style="width:120px; padding:5px 8px; border:1.5px solid var(--line); border-radius:8px; font-size:12px;">
          <span class="cx-note" id="cxCount" style="margin:0;"></span>
        </div>
        <div class="cx-tbl-wrap">
          <table class="cx-tbl">
            <thead><tr><th class="c">층</th><th class="c">종류</th><th class="l">몬스터</th><th>체력</th><th>공격력</th><th>투자반영 공격력</th><th>방어력</th><th>처치 PP</th></tr></thead>
            <tbody id="cxBody"><tr><td colspan="8" class="c">불러오는 중...</td></tr></tbody>
          </table>
        </div>
        <div class="cx-note">
          · 100층 아래는 10층, 100층 이후는 100층 단위로 볼 수 있습니다. 마을(10/20/.../100/200...층)은 몬스터가 없어서 표에 나오지 않습니다.<br>
          · <b>공격력</b>은 보스층의 추가 배율(×1.6)까지 반영한 값입니다. 중간보스(×3)/강화몹(×2)은 이 표의 스펙에 배율만 곱해집니다.<br>
          · <b>투자반영 공격력</b> = 공격력 × 투자계수(<span id="cxInvest">-</span>배). 투자계수는 맞는 동료의 체력 스탯 레벨과 한계돌파가 클수록 커집니다(표는 최대 투자 기준).<br>
          · 처치 PP는 층 배율까지 반영한 1마리 기본 보상입니다(자동사냥 추가 배율/몬스터 2마리 조우 미반영).
        </div>
      </div>

      <div id="cxItem" style="display:none;">
        <div class="cx-note" style="margin-top:0;">★7 전설장비 목록입니다. 보유 인원은 이 서버에서 그 장비를 가진 유저 수, 최고 강화는 가장 높게 강화된 개체의 수치입니다.</div>
        <div id="cxItemList" style="margin-top:8px;"></div>
      </div>
    </div>
  </div>

  <div class="panel" id="panel-msg">
    <div class="card">
      <div class="card-title">💬 지난 메시지 (최근 10개)</div>
      <div id="msgList"></div>
    </div>
  </div>

  <!-- [2026-10-23] 광산: 들어가기 + 곡괭이 내구도/위치 + 최근 캔 곳(좌표 공유) -->
  <div class="panel" id="panel-mine">
    <div class="card">
      <div class="card-title">⛏️ 광산</div>
      <div id="mineBox"></div>
    </div>
  </div>

  <!-- [2026-10-23] 광산상점: 광석을 내고 자동사냥 처치당 PP를 영구히 올린다 -->
  <div class="panel" id="panel-mineshop">
    <div class="card">
      <div class="card-title">🏪 광산상점</div>
      <div id="mineShopBox"></div>
    </div>
  </div>

  <!-- [2026-10-21] 하단 메뉴 "설정" 칸 -- 지금은 전투화면 버전 선택과 공지 다시 보기만 모아 둔다(기존 버튼은 그대로 있다). -->
  <div class="panel" id="panel-settings">
    <div class="card">
      <div class="card-title">⚙️ 설정</div>
      <div class="set-row">
        <div class="set-label">전투화면 버전</div>
        <div class="set-btns">
          <button type="button" class="set-btn set-ver" data-v="V1" onclick="TW.setBattleScreenVersion('V1')">V1</button>
          <button type="button" class="set-btn set-ver" data-v="V2" onclick="TW.setBattleScreenVersion('V2')">V2</button>
        </div>
      </div>
      <div class="set-row">
        <div class="set-label">업데이트 공지</div>
        <button type="button" class="set-btn" onclick="TW.reopenNotice()">📢 다시 보기</button>
      </div>
      <!-- [2026-10-22] 하단 메뉴 순서/숨기기 -- 유저별로 서버(DOCK_LAYOUT)에 저장. 탑/설정은 숨길 수 없다. -->
      <div class="set-block">
        <div class="set-block-head">
          <div class="set-label">하단 메뉴 순서 · 숨기기</div>
          <button type="button" class="set-btn" onclick="TW.resetDockLayout()">기본값으로</button>
        </div>
        <div class="set-hint">▲▼로 순서를 바꾸고, 안 쓰는 메뉴는 숨길 수 있어요. 바로 적용·저장됩니다. (탑·설정은 숨길 수 없어요) <span class="dock-save-state" id="dockSaveState"></span></div>
        <div id="dockEditList"></div>
      </div>
    </div>
  </div>

</div>

<div class="msg-toast" id="msgToast"></div>
<div class="victory-flash" id="victoryFlash"><div class="vf-badge">🎉 전투 승리!</div></div>

<div class="detail-overlay" id="detailOverlay" onclick="if(event.target===this) TW.closeDetail();">
  <div class="detail-card">
    <button class="detail-close" onclick="TW.closeDetail()">✕</button>
    <img class="detail-img" id="detailImg" src="" alt="">
    <div class="detail-name" id="detailName">-</div>
    <div class="detail-role" id="detailRole">-</div>
    <div class="detail-stats">
      <div class="stat-box"><div class="stat-label">HP</div><div class="stat-val" id="detailHp">-</div><div class="stat-sub" id="detailHpSub"></div></div>
      <div class="stat-box"><div class="stat-label">공격력</div><div class="stat-val" id="detailAtk">-</div><div class="stat-sub" id="detailAtkSub"></div></div>
      <div class="stat-box"><div class="stat-label">방어력</div><div class="stat-val" id="detailDef">-</div><div class="stat-sub" id="detailDefSub"></div></div>
    </div>
    <div class="card-title" style="font-size:13px;">착용 장비</div>
    <div id="detailEquipBox"></div>
  </div>
</div>

<div class="detail-overlay" id="confirmOverlay" onclick="if(event.target===this) TW.closeConfirm();">
  <div class="detail-card confirm-card">
    <div class="confirm-msg" id="confirmMsg">정말 이동하시겠습니까?</div>
    <div class="confirm-btns">
      <button type="button" class="btn-no" onclick="TW.closeConfirm()">취소</button>
      <button type="button" class="btn-yes" id="confirmYesBtn">이동</button>
    </div>
  </div>
</div>

<!-- [2026-09-07] 업데이트 공지/새로고침 유도 팝업 -- 새 버전 감지 시 자동으로 뜨거나,
     top-controls의 "📢" 버튼으로 언제든 다시 볼 수 있다. checkAppVersion/reopenNotice 참고. -->
<div class="detail-overlay" id="noticeOverlay" onclick="if(event.target===this) TW.closeNotice();">
  <div class="detail-card confirm-card">
    <div class="sheet-title" id="noticeTitle">📢 공지</div>
    <div class="confirm-msg" id="noticeBody" style="text-align:left; white-space:pre-line;">-</div>
    <div class="confirm-btns" style="margin-bottom:8px;">
      <button type="button" class="btn-no" onclick="TW.dismissNotice()" style="font-size:12px; padding:8px;">🙈 이 공지 다시 보지 않기</button>
    </div>
    <div class="confirm-btns">
      <button type="button" class="btn-no" onclick="TW.closeNotice()">닫기</button>
      <button type="button" class="btn-yes" onclick="TW.refreshForUpdate()">🔄 새로고침</button>
    </div>
  </div>
</div>

<!-- [2026-09-06] 동료변경/무기·투구·갑옷변경 버튼에서 여는 선택 팝업. renderPicker()가
     pickerBody 안을 그때그때 채운다(동료 후보 목록 또는 장비 후보 목록, 고르면 바로 닫힘). -->
<div class="detail-overlay" id="pickerOverlay" onclick="if(event.target===this) TW.closePicker();">
  <div class="detail-card sheet-card">
    <button class="detail-close" onclick="TW.closePicker()">✕</button>
    <div id="pickerBody"></div>
  </div>
</div>

<!-- "보유동료/파티장비현황/미착용장비 카드는 없애고 버튼으로 이관" 요청 -- 내용물
     (partyGridFilters/partyGrid/equipListFilters/equipListBox)은 그대로, 항상 보이던
     인라인 카드 대신 이 팝업 2개 뒤로 옮겼다. [2026-09-07] "파티 장비 현황"은 파티 슬롯
     카드 자체에 이미 부위별로 다 보이는 중복 정보라는 요청으로 이 팝업에서 완전히 제거. -->
<div class="detail-overlay" id="allCompanionsOverlay" onclick="if(event.target===this) TW.closeAllCompanions();">
  <div class="detail-card sheet-card wide-card">
    <button class="detail-close" onclick="TW.closeAllCompanions()">✕</button>
    <div class="sheet-title" id="allCompanionsTitle">보유 동료</div>
    <div id="partyGridFilters"></div>
    <div class="party-grid" id="partyGrid"></div>
  </div>
</div>

<!-- [2026-10-21] 동료 이미지 바꾸기 -- 편성된 동료의 이미지를 내가 가진 다른 동료(전부)의 이미지와 서로 맞바꾼다(★7/성장형 포함). -->
<div class="detail-overlay" id="imageSwapOverlay" onclick="if(event.target===this) TW.closeImageSwap();">
  <div class="detail-card sheet-card wide-card">
    <button class="detail-close" onclick="TW.closeImageSwap()">✕</button>
    <div class="sheet-title">🖼️ 이미지 바꾸기</div>
    <div class="isw-now" id="imageSwapNow"></div>
    <div id="imageSwapFilters"></div>
    <div class="party-grid" id="imageSwapGrid"></div>
  </div>
</div>

<!-- [2026-10-22] 예전 장비 목록 팝업(#allEquipOverlay)은 인벤토리 탭(#panel-inven)으로 옮겨졌다(일괄합성 버튼 포함). -->

<!-- [2026-09-21] "전설제작 리스트로 쭉 나오는게 아니고, 전체동료리스트처럼 팝업을띄워서
     거기서 선택" 요청 -- allCompanionsOverlay/allEquipOverlay와 동일한 .detail-overlay 패턴. -->
<div class="detail-overlay" id="legendRosterOverlay" onclick="if(event.target===this) TW.closeLegendRoster();">
  <div class="detail-card sheet-card wide-card">
    <button class="detail-close" onclick="TW.closeLegendRoster()">✕</button>
    <div class="sheet-title" id="legendRosterTitle">제작할 전설장비 선택 (성공률 30%)</div>
    <!-- [2026-09-21] "버튼을 누르면 팝업에서 눌러서 처리할수있게해줘" 요청 -- 항목을 누르면
         그 자리에서 바로 제작을 시도한다(목록 -> 제작중 연출 -> 성공/실패 결과 전부 이 팝업
         안에서 끝남, 팝업을 닫아야 메인 카드로 돌아옴). -->
    <div id="legendRosterOverlayList"></div>
    <div class="legend-craft-anim" id="legendCraftAnim" style="display:none;">
      <span class="legend-craft-spinner">✨</span> 제작 중...
    </div>
    <div class="legend-craft-result" id="legendCraftResult" style="display:none;"></div>
  </div>
</div>

<!-- [2026-09-21] "장비제작소 안에 장비해체 버튼을 누르면 어떤장비해체할지 띄우는방식으로
     하자" 요청. -->
<div class="detail-overlay" id="legendDisenchantOverlay" onclick="if(event.target===this) TW.closeLegendDisenchant();">
  <div class="detail-card sheet-card wide-card">
    <button class="detail-close" onclick="TW.closeLegendDisenchant()">✕</button>
    <div class="sheet-title">해체할 전설장비 선택 (조각 15개 환급)</div>
    <div id="legendDisenchantList"></div>
  </div>
</div>

<!-- [2026-10-08] 전설장비 강화 팝업 -- 착용/미착용 ★7 전설장비 전부. 실패해도 강화 수치는 유지되고 조각만 소모된다. -->
<!-- [2026-10-22] 전투에 영향이 가는 패치 -- 주사위를 굴리기 전에 패치 내용을 보여주고, 확인을 눌러야 공격할 수 있다(전설 규칙 위반이면 해제 안내). -->
<div class="detail-overlay" id="gateOverlay">
  <div class="detail-card">
    <div class="sheet-title" id="gateTitle">📢 패치 안내</div>
    <div class="gate-body" id="gateBody"></div>
    <div class="gate-btns">
      <button type="button" onclick="TW.closeGate()">닫기</button>
      <button type="button" class="primary" id="gateOkBtn">확인</button>
    </div>
  </div>
</div>

<!-- [2026-10-23] 일반 장비/악세 분해(★1~★6) -- 수량이 여러 개면 몇 개 분해할지 고른다. 개당 확률로 전설의조각. -->
<div class="detail-overlay" id="dismantleOverlay" onclick="if(event.target===this) TW.closeDismantle();">
  <div class="detail-card">
    <div class="sheet-title" id="dmTitle">🔨 장비 분해</div>
    <div class="dm-info" id="dmHave"></div>
    <div class="dm-qty-row" id="dmQtyRow">
      <button type="button" onclick="TW.dmStep(-10)">-10</button>
      <button type="button" onclick="TW.dmStep(-1)">-1</button>
      <input type="number" id="dmQty" min="1" value="1" oninput="TW.dmStep(0)">
      <button type="button" onclick="TW.dmStep(1)">+1</button>
      <button type="button" onclick="TW.dmStep(10)">+10</button>
    </div>
    <div class="dm-quick" id="dmQuick">
      <button type="button" onclick="TW.dmSet(1)">1개</button>
      <button type="button" onclick="TW.dmSet('half')">절반</button>
      <button type="button" onclick="TW.dmSet('all')">전부</button>
    </div>
    <div class="dm-info" id="dmExpect"></div>
    <div class="gate-btns">
      <button type="button" onclick="TW.closeDismantle()">취소</button>
      <button type="button" class="primary" id="dmOkBtn" onclick="TW.doDismantle()">분해</button>
    </div>
  </div>
</div>

<div class="detail-overlay" id="legendEnhanceOverlay" onclick="if(event.target===this) TW.closeLegendEnhance();">
  <div class="detail-card sheet-card wide-card">
    <button class="detail-close" onclick="TW.closeLegendEnhance()">✕</button>
    <div class="sheet-title">⬆️ 전설강화 <span class="legend-frag-badge">🧩 <span id="legendEnhanceFrag">0</span></span></div>
    <div class="legend-craft-result" id="legendEnhanceResult" style="display:none;"></div>
    <div id="legendEnhanceList"></div>
  </div>
</div>

<!-- [2026-10-20] 성장형 동료 팝업 -- 탐사 진행/성장 배율, 직업 변경(무료), 이름 변경. 조건 판정은 서버가 한다. -->
<div class="detail-overlay" id="growthOverlay" onclick="if(event.target===this) TW.closeGrowth();">
  <div class="detail-card sheet-card wide-card">
    <button class="detail-close" onclick="TW.closeGrowth()">✕</button>
    <div class="sheet-title">🌱 성장형 동료</div>
    <div class="legend-craft-result" id="growthResult" style="display:none;"></div>
    <div id="growthBody"></div>
  </div>
</div>

<!-- [2026-10-20] ★7 동료 소환/방출 팝업 -- 직업을 고르면 그 직업의 ★7 동료가 생긴다(1인 1명, 소모 없음). 조건 판정은 서버가 한다. -->
<div class="detail-overlay" id="star7Overlay" onclick="if(event.target===this) TW.closeStar7();">
  <div class="detail-card sheet-card wide-card">
    <button class="detail-close" onclick="TW.closeStar7()">✕</button>
    <div class="sheet-title">🌟 7성 동료 소환</div>
    <div class="legend-craft-result" id="star7Result" style="display:none;"></div>
    <div id="star7Body"></div>
  </div>
</div>

<!-- [2026-09-30] 마을 미니맵 '던전' 팝업 -- 이 마을 첫 사냥터층 또는 최근 전투층으로 이동(확인창은 기존 confirmOverlay 재사용). -->
<div class="detail-overlay" id="dungeonOverlay" onclick="if(event.target===this) TW.closeDungeon();">
  <div class="detail-card sheet-card wide-card">
    <button class="detail-close" onclick="TW.closeDungeon()">✕</button>
    <div class="sheet-title">🕳️ 던전 — 어디로 갈까요?</div>
    <div id="dungeonOptions"></div>
  </div>
</div>

<nav class="dock">
  <div class="dock-inner">
    <button class="dbtn active" data-tab="board" onclick="TW.switchTab('board')"><span class="d-icn">🗼</span>탑</button>
    <button class="dbtn" data-tab="party" id="dbtnParty" onclick="TW.switchTab('party')"><span class="d-icn">🎒</span>편성</button>
    <!-- [2026-09-09] "주사위를 보드 안쪽으로 옮겼으니 하단 탭은 없애자" 요청 -- 여기 있던
         굴리기 버튼(dbtnDice)은 보드 카드 안의 #boardRollBtn으로 이동(updateDiceButtonState 참고). -->
    <button class="dbtn" data-tab="shop" id="dbtnShop" onclick="TW.switchTab('shop')"><span class="d-icn">🛍️</span>상점</button>
    <button class="dbtn" data-tab="inven" onclick="TW.switchTab('inven')"><span class="d-icn">🧳</span>인벤토리</button>
    <button class="dbtn" data-tab="ach" onclick="TW.switchTab('ach')"><span class="d-icn">🏆</span>업적</button>
    <button class="dbtn" data-tab="codex" onclick="TW.switchTab('codex')"><span class="d-icn">📖</span>도감</button>
    <button class="dbtn" data-tab="msg" onclick="TW.switchTab('msg')"><span class="d-icn">💬</span>메시지</button>
    <button class="dbtn" data-tab="settings" onclick="TW.switchTab('settings')"><span class="d-icn">⚙️</span>설정</button>
    <!-- [2026-10-23] 세 번째 줄: 광산(2D 광석 캐기), 광산상점(광석 -> 자동사냥 처치당 PP) -->
    <button class="dbtn" data-tab="mine" style="display:none;" onclick="TW.switchTab('mine')"><span class="d-icn">⛏️</span>광산</button>
    <button class="dbtn" data-tab="mineshop" style="display:none;" onclick="TW.switchTab('mineshop')"><span class="d-icn">🏪</span>광산상점</button>
  </div>
</nav>

<!-- [2026-10-23] 웹 광산(255x255 공용 맵, 2D 도트) -- TowerMine 스크립트(맨 아래)가 그린다. -->
<div class="mine-overlay" id="mineOverlay">
  <canvas id="mineCanvas"></canvas>
  <div class="mine-hud-tl"><div class="mine-coord mpx" id="mineCoord">📍 0, 0</div><div class="mine-terr" id="mineTerr"></div></div>
  <div class="mine-hud-tr">
    <div class="mine-sta mpx" id="mineSta">⛏️ 0 / 200</div>
    <div class="mine-regen" id="mineRegen"></div>
    <canvas id="mineMini" width="255" height="255" onclick="TowerMine.toggleMini()"></canvas>
  </div>
  <div class="mine-log mpx" id="mineLog"></div>
  <button type="button" class="mine-btn mine-home-btn mpx" onclick="TowerMine.goVillage()">🏠 마을로</button>
  <button type="button" class="mine-btn mine-log-btn mpx" onclick="TowerMine.toggleLog()">📜 최근 캔 곳</button>
  <button type="button" class="mine-btn mine-exit mpx" onclick="TowerMine.close()">🚪 나가기</button>
  <button type="button" class="mine-dig" id="mineDigBtn" onclick="TowerMine.dig()" aria-label="캐기">⛏️</button>
  <div class="mine-msg mpx" id="mineMsg"></div>
  <div class="mine-smith mpx" id="mineSmith"></div>
  <div class="mine-loading" id="mineLoading">광산으로 가는 중...</div>
</div>

<script>
var TW = (function () {
  var base = '<%=request.getContextPath()%>/loa';

  function userName() {
    var v = document.getElementById('userNameInput').value.trim();
    if (v) sessionStorage.setItem('loaUserName', v);
    return v || sessionStorage.getItem('loaUserName') || '';
  }

  // 서버 메시지는 ♬(NL)를 줄바꿈 구분자로 씀(카톡봇 등 채팅 환경 공용) -- 웹에서는 실제 개행으로 변환
  function formatMsg(msg) {
    return (msg == null ? '' : String(msg)).split('♬').join('\n');
  }

  // PP_EXT/COST_EXT 등 단위 컬럼은 ''가 Oracle에서 NULL로 저장되므로 항상 null-safe 처리
  function fmtPP(value, ext) {
    var v = (value == null) ? 0 : value;
    var e = (ext == null) ? '' : ext;
    return v + e;
  }

  // [2026-09-18] "동료 체력이 1a 넘으면 표기가 이상해진다" 버그 수정용 -- PP.java의
  // toBaseValue()와 동일하게 단위(''=0, 'a'=1, 'b'=2, ...)를 10000의 거듭제곱으로 환산해
  // 실제 크기를 비교 가능한 숫자로 되돌린다. HP바 % 계산처럼 "단위 없는 숫자"(EFF_HP 등)와
  // 직접 비교/연산해야 할 때는 fmtPP(표시용) 대신 이 함수를 써야 한다.
  function ppToBase(value, ext) {
    var v = (value == null) ? 0 : Number(value);
    var e = (ext == null || ext === '') ? '' : String(ext);
    var idx = e === '' ? 0 : (e.charCodeAt(0) - 'a'.charCodeAt(0) + 1);
    return v * Math.pow(10000, idx);
  }

  // [2026-09-18] "체력이 0이 된 동료는 전투불능 표시를 하고싶어" 요청 -- HP 단위(EXT)가
  // 붙어도(a/b 등) 0 자체는 항상 EXT 없는 순수 0으로 저장되므로(PP.toBaseValue와 동일 전제)
  // value만 보면 충분하다.
  function isIncapacitated(hpValue) {
    return (Number(hpValue) || 0) <= 0;
  }

  // [2026-09-21] "60%이하체력이되면 노랑색, 30%이하가되면 빨간색으로 체력바 색을 바꿔주고,
  // 중간은 그라데이션효과가 있음 좋겠어" 요청 -- 현재 pct 기준으로 빨강(0~30%)/노랑(60%)/
  // 초록(100%) 사이를 선형보간한 "단일 색"을 구하고(hpColor), 그 색과 살짝 어두운 톤 두
  // 스톱으로 입체감 있는 그라데이션을 만든다(기존 기본 .hpbar-fill의 연두->진초록 톤과
  // 같은 방식). 막대 폭 전체를 0~100% 구간으로 늘려 자르는 방식(가장자리 정렬에 따라 안쪽/
  // 바깥쪽 색이 뒤바뀌는 문제가 있었음) 대신, "지금 이 순간의 색 하나"를 계산해서 그대로
  // 칠하는 방식이라 좌/우 어느 쪽에서 채워지든 항상 같은 결과가 나온다. pct가 60%/30%
  // 경계를 지날 때도 hpColor가 연속함수라 뚝뚝 끊기지 않고 매끄럽게 변한다. 모든 HP바
  // (전투화면 몬스터/파티대표/V1 파티목록 등)가 공용으로 쓴다.
  function lerpColor(a, b, t) {
    return [ a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t ];
  }
  function hpColor(pct) {
    var RED = [226, 75, 74], YELLOW = [239, 159, 39], GREEN = [95, 190, 133];
    var c = pct <= 30 ? RED
        : pct <= 60 ? lerpColor(RED, YELLOW, (pct - 30) / 30)
        : lerpColor(YELLOW, GREEN, Math.min(1, (pct - 60) / 40));
    return c.map(function (v) { return Math.round(v); });
  }
  function setHpBarFill(el, pct) {
    if (!el) return;
    pct = Math.max(0, Math.min(100, pct));
    el.style.width = pct + '%';
    var c = hpColor(pct);
    var dark = c.map(function (v) { return Math.round(v * 0.72); });
    el.style.background = 'linear-gradient(90deg, rgb(' + c.join(',') + '), rgb(' + dark.join(',') + '))';
    el.style.transition = 'width .4s ease, background .4s ease';
  }

  // ===== [2026-09-17] 전투화면(포켓몬 배틀 스타일) =====
  // 몬스터 이미지 에셋이 전혀 없어서(DB에 스프라이트 컬럼 자체가 없음) 몬스터ID를 해시해
  // 고정된 이모지 하나를 배정한다 -- 같은 몬스터는 항상 같은 이모지로 보이되, 실제 그림은 아님.
  var MONSTER_EMOJI_POOL = ['👹', '👺', '🐉', '🦂', '🕷️', '🦇', '🐺', '🧟', '🦑', '🐍', '🦖', '👻', '💀', '🦁', '🐲'];
  function monsterEmoji(monsterId) {
    var n = Number(monsterId) || 0;
    return MONSTER_EMOJI_POOL[n % MONSTER_EMOJI_POOL.length];
  }

  // battle.monsterBaseHp: 지금 몬스터를 "처음 본 순간"의 HP를 100%로 삼는 기준선(서버가 몬스터
  // 최대HP를 안 내려주므로 -- 하드코어 스케일링 등으로 몬스터마다 최대치가 달라 고정 상수도 못
  // 씀). battle.monsterHp/companionHp는 "지난 갱신에서 봤던 HP"로, 이번에 그보다 줄었으면 그
  // 대상만 흔들림 애니메이션을 준다(누가 맞았는지 서버가 알려주지 않으니 HP 감소로 추론).
  var battle = { monsterId: null, monsterBaseHp: null, monsterHp: null, companionHp: {}, wasInCombat: false };

  // [2026-09-17 2차] "전투진입시 포켓몬처럼 스르르르 페이드아웃/페이드인" 요청 -- 맵뷰<->전투화면
  // 전환(진입/이탈) "그 순간"에만 크로스페이드를 건다. 전투 중 매 HP 갱신마다 다시 부르면
  // 화면이 계속 깜빡이므로, updateBattleScreen()이 실제 전환(inCombat 값이 지난번과 달라짐)일
  // 때만 이 함수를 부른다.
  // [2026-09-17 3차 버그수정] "모바일화면에서 동료3명이 아래에 둥둥떠있어, 캔버스안쪽으로
  // 나오도록" 신고 -- 실기기 좌표를 직접 재보니 원인이 명확했다: .battle-screen의 높이를
  // min(480px, 60vh)로만 정해서, "화면 위쪽에 이미 쌓인 헤더/탭 콘텐츠 때문에 이 박스가
  // 화면 어디서 시작하는지"와 "하단 고정 네비(.dock, ~95px)가 뷰포트 맨 아래를 차지하고
  // 있다는 것"을 전혀 반영하지 못했다. 그 결과 박스 자체가 뷰포트 아래로 삐져나가고, 맨
  // 아래에 붙는 파티 줄(justify-content:space-between)이 정확히 dock와 겹치는 위치에
  // 놓여서 "떠있는" 것처럼 보였다. CSS만으로는 "이 박스가 페이지에서 실제로 어디서
  // 시작하는지"를 알 방법이 없어서(헤더 길이가 공지 배너 유무 등으로 가변적), 보여줄 때마다
  // JS로 실측해서 남은 세로공간에 맞춰 높이를 직접 계산한다.
  // [2026-09-17 4차] "위아래 높이가 작으면 왼쪽/오른쪽 이렇게만 해줘, 잘리는게 보기싫어"
  // 요청 -- 세로 공간이 좁을 때(available이 작을 때) 몬스터/파티를 위아래로 쌓으면(기존
  // column+space-between) 둘 다 밀려서 잘려 보일 수 있다. 대신 가로(row)로 나란히 배치하면
  // 각자 한 줄 높이(~50~70px)만 있으면 되므로 세로 여유가 적어도 안 잘린다 -- .compact
  // 클래스를 켜면 row-reverse로 전환해서 기존과 동일하게 "왼쪽 파티/오른쪽 몬스터" 배치를
  // 유지한 채 방향만 세로->가로로 바꾼다(CSS .battle-screen.compact 규칙 참고).
  var BATTLE_COMPACT_THRESHOLD = 300;
  function fitBattleScreenHeight() {
    var el = document.getElementById('battleScreen');
    var dock = document.querySelector('.dock');
    if (!el || el.style.display === 'none') return;
    // [2026-09-30] "전투 중 주사위를 계속 누르는데 화면이 위아래로 멋대로 움직인다" 수정 -- 예전엔 화면(뷰포트) 기준 top으로
    // 높이를 260~480px 사이에서 그때그때 정해서, (1) 스크롤 위치에 따라 높이가 달라지고 (2) 전투 시작/종료 때 보드(고정
    // min(480px,60svh))와 전투화면의 높이가 달라져 페이지 전체 높이가 출렁이며 스크롤이 튀었다. 이제 스크롤과 무관하게
    // 문서 기준 top으로 여유 공간(compact 판단)만 계산하고, 높이는 보드 뷰포트와 똑같은 값으로 고정해서 전환해도 높이가 안 변한다.
    var docTop = el.getBoundingClientRect().top + (window.pageYOffset || 0);
    var dockH = dock ? dock.getBoundingClientRect().height : 0;
    var available = window.innerHeight - docTop - dockH - 12; // 12px 여유
    el.classList.toggle('compact', available < BATTLE_COMPACT_THRESHOLD);
    el.style.height = Math.min(480, Math.round(window.innerHeight * 0.6)) + 'px';
  }
  window.addEventListener('resize', fitBattleScreenHeight);

  function crossfadeBoardView(showBattle) {
    var screen = document.getElementById('battleScreen');
    var boardWrap = document.querySelector('.tower-viewport-wrap');
    var trackControls = document.querySelector('.board-track-controls');
    var showEl = showBattle ? screen : boardWrap;
    var hideEl = showBattle ? boardWrap : screen;
    if (trackControls) trackControls.style.display = showBattle ? 'none' : '';
    if (hideEl.style.display === 'none') {
      // 이미 숨겨져 있었으면(최초 페이지 로드 등) 페이드 없이 바로 보여준다.
      showEl.style.display = '';
      showEl.style.opacity = '1';
      if (showBattle) fitBattleScreenHeight();
      return;
    }
    hideEl.style.opacity = '0';
    setTimeout(function () {
      hideEl.style.display = 'none';
      showEl.style.display = '';
      showEl.style.opacity = '0';
      void showEl.offsetWidth; // 강제 리플로우 -- 이게 없으면 opacity 0->1 전환이 안 먹힘
      showEl.style.opacity = '1';
      if (showBattle) fitBattleScreenHeight();
    }, 260);
  }

  // [2026-09-21] "이전버전은 v1, 지금은v2로, 유저가 선택한걸 db에 저장/표시" 요청 -- 두
  // 마크업(#battleScreenV1/#battleScreenV2) 중 state.battleScreenVersion에 맞는 쪽만 보여주고
  // 그 안을 채운다. 공용(배경 사이클링/새 몬스터 조우 판정/크로스페이드)은 여기서 한 번만
  // 처리하고, 버전별 렌더링은 renderBattleV1/renderBattleV2로 분리.
  function updateBattleScreen(p) {
    var inCombat = p.STATUS === 'IN_COMBAT';
    // [2026-09-19] 전투 중엔 위 .dice-controls의 sticky를 꺼서 #battleScreen 상단(몬스터 줄)을
    // 덮지 않게 한다(CSS .dice-controls.dc-in-battle 주석 참고). 매 갱신마다 불러도
    // classList.toggle은 상태가 같으면 사실상 no-op이라 부담 없음.
    var diceControlsEl = document.querySelector('.dice-controls');
    if (diceControlsEl) diceControlsEl.classList.toggle('dc-in-battle', inCombat);
    if (inCombat !== battle.wasInCombat) {
      crossfadeBoardView(inCombat);
      battle.wasInCombat = inCombat;
    }
    if (!inCombat) { battle.monsterId = null; return; }

    state.battleScreenVersion = (p.BATTLE_SCREEN_VERSION === 'V1') ? 'V1' : 'V2';
    applyBattleScreenVersionUI(state.battleScreenVersion);
    var isV1 = state.battleScreenVersion === 'V1';

    if (battle.monsterId !== p.CUR_MONSTER_ID) {
      // 새 몬스터와 조우(직전까지 다른 몬스터였거나, 전투에 막 진입) -- 기준선/스프라이트 리셋.
      battle.monsterId = p.CUR_MONSTER_ID;
      // [버그 수정] CUR_MONSTER_HP_VALUE도 PP 단위 표기라 그대로 쓰면 전투 중 단위(EXT)가
      // 바뀔 때(예: 22.50a -> 7370, 단위가 a에서 없음으로 내려감) 기준선과 안 맞아 HP바가
      // 깨진다 -- ppToBase로 실제 값으로 환산해서 기준선을 잡는다.
      battle.monsterBaseHp = ppToBase(p.CUR_MONSTER_HP_VALUE, p.CUR_MONSTER_HP_EXT) || 1;
      battle.monsterHp = null;
      // [2026-09-17 2차] "랜덤배경 생기면 좋겠어" -- 새 몬스터를 만날 때마다 6종 중 하나를
      // 무작위로 배정(직전과 같은 배경이 연달아 나오지 않게 가볍게 피함).
      var screenEl = document.getElementById('battleScreen');
      var prevBg = (screenEl.className.match(/bs-bg-\d/) || [])[0];
      var nextBg;
      do { nextBg = 'bs-bg-' + Math.floor(Math.random() * 6); } while (nextBg === prevBg);
      screenEl.className = screenEl.className.replace(/\bbs-bg-\d\b/, '').trim() + ' ' + nextBg;
    }

    var curMonsterHpBase = ppToBase(p.CUR_MONSTER_HP_VALUE, p.CUR_MONSTER_HP_EXT);
    var monsterPct = battle.monsterBaseHp > 0
        ? Math.max(0, Math.min(100, curMonsterHpBase / battle.monsterBaseHp * 100)) : 100;
    var monsterHit = battle.monsterHp != null && curMonsterHpBase < battle.monsterHp;
    battle.monsterHp = curMonsterHpBase;

    // [버그 수정, 2026-09-21] updateBattlePartyV1()이 V1/V2 두 파티로스터 줄을 함께 채우도록
    // 바뀌었는데, 정작 이 함수는 renderBattleV1() 안에서만 호출되고 있었다 -- V2로 렌더링될
    // 땐 renderBattleV1이 아예 안 불리니 bsPartyRowV2가 한 번도 채워지지 않아 "동료가 대표
    // 1명만 보인다" 신고가 고쳐지지 않았음. 버전과 무관하게 항상 한 번 호출하도록 여기로 이동.
    updateBattlePartyV1();
    if (isV1) renderBattleV1(p, curMonsterHpBase, monsterPct, monsterHit);
    else renderBattleV2(p, curMonsterHpBase, monsterPct, monsterHit);
  }

  function renderBattleV1(p, curMonsterHpBase, monsterPct, monsterHit) {
    document.getElementById('bsMonsterSpriteV1').textContent = monsterEmoji(p.CUR_MONSTER_ID);
    document.getElementById('bsMonsterNameV1').textContent =
        (p.CUR_MONSTER_ELITE_YN === 'Y' ? '💪 ' : '') + (monsterNameCache || '몬스터');
    setHpBarFill(document.getElementById('bsMonsterHpFillV1'), monsterPct);
    document.getElementById('bsMonsterHpNumV1').textContent = fmtPP(p.CUR_MONSTER_HP_VALUE, p.CUR_MONSTER_HP_EXT);
    var sprite = document.getElementById('bsMonsterSpriteV1');
    if (monsterHit) { sprite.classList.remove('hit'); void sprite.offsetWidth; sprite.classList.add('hit'); }
  }

  // [2026-10-20] 대치 장면의 파티 대열 -- 편성(구성/그림)이 바뀐 때만 다시 만들고(HP바 transition 유지), 매 갱신마다 HP/전투불능/피격 흔들림만 갱신한다.
  function renderDuelParty(members) {
    var box = document.getElementById('bsDuelParty');
    if (!box) return;
    if (!members.length) {
      if (box.dataset.ids !== 'none') { box.dataset.ids = 'none'; box.innerHTML = '<div class="bs-duel-empty">파티 없음</div>'; }
      return;
    }
    members = members.slice().sort(function (a, b) { return (a.PARTY_SLOT || 9) - (b.PARTY_SLOT || 9); });
    var ids = members.map(function (c) { return c.COMPANION_ID + ':' + (c.IMAGE_URL || '') + ':' + c.CLASS + ':' + (c.NAME || ''); }).join(',');
    if (box.dataset.ids !== ids) {
      box.dataset.ids = ids;
      box.innerHTML = '';
      members.forEach(function (c) {
        var m = document.createElement('div');
        m.className = 'bs-duel-member';
        m.dataset.cid = c.COMPANION_ID;
        var slot = document.createElement('div');
        slot.className = 'bs-duel-avatar-slot';
        slot.appendChild(buildAvatarEl(c, 'bs-duel-avatar', false));
        m.appendChild(slot);
        var nm = document.createElement('div');
        nm.className = 'bs-duel-name';
        nm.textContent = (c.GRADE === 8 ? '🌱' : (JOB_EMOJI[c.CLASS] || '')) + ' ' + (c.NAME || JOB_KR[c.CLASS] || c.CLASS);
        m.appendChild(nm);
        var bar = document.createElement('div');
        bar.className = 'hpbar-track bs-duel-hpbar';
        bar.innerHTML = '<div class="hpbar-fill" style="width:100%"></div>';
        m.appendChild(bar);
        var down = document.createElement('div');
        down.className = 'bs-duel-down';
        down.style.display = 'none';
        down.textContent = '💀 전투불능';
        m.appendChild(down);
        box.appendChild(m);
      });
    }
    var dropped = battle.dropped || {};
    members.forEach(function (c) {
      var m = box.querySelector('.bs-duel-member[data-cid="' + c.COMPANION_ID + '"]');
      if (!m) return;
      var maxHp = c.EFF_HP || 1;
      var curHp = ppToBase(c.CUR_HP_VALUE, c.CUR_HP_EXT);
      setHpBarFill(m.querySelector('.hpbar-fill'), Math.max(0, Math.min(100, curHp / maxHp * 100)));
      var downNow = isIncapacitated(curHp);
      m.classList.toggle('incapacitated', downNow);
      var d = m.querySelector('.bs-duel-down');
      if (d) d.style.display = downNow ? '' : 'none';
      if (dropped[c.COMPANION_ID]) { m.classList.remove('shake'); void m.offsetWidth; m.classList.add('shake'); }
    });
  }

  function renderBattleV2(p, curMonsterHpBase, monsterPct, monsterHit) {
    document.getElementById('bsMonsterSprite').textContent = monsterEmoji(p.CUR_MONSTER_ID);
    document.getElementById('bsMonsterName').textContent =
        (p.CUR_MONSTER_ELITE_YN === 'Y' ? '💪 ' : '') + (monsterNameCache || '몬스터');
    setHpBarFill(document.getElementById('bsPowerFillMonster'), monsterPct);
    document.getElementById('bsMonsterPowerNum').textContent = fmtPP(p.CUR_MONSTER_HP_VALUE, p.CUR_MONSTER_HP_EXT);
    var sprite = document.getElementById('bsMonsterSprite');
    if (monsterHit) { sprite.classList.remove('hit'); void sprite.offsetWidth; sprite.classList.add('hit'); }

    // [2026-09-21] 세력비교바 왼쪽(파티) -- 편성된(PARTY_SLOT 있는) 동료 전원의 현재/최대
    // HP를 ppToBase로 환산해 합산(단위 다른 동료가 섞여도 base 단위로는 정확히 비교 가능).
    var partyMembers = (lastParty.companions || []).filter(function (c) { return c.PARTY_SLOT; });
    var partyCurSum = 0, partyMaxSum = 0;
    partyMembers.forEach(function (c) {
      partyCurSum += ppToBase(c.CUR_HP_VALUE, c.CUR_HP_EXT);
      partyMaxSum += (c.EFF_HP || 0);
    });
    var partyPct = partyMaxSum > 0 ? Math.max(0, Math.min(100, partyCurSum / partyMaxSum * 100)) : 100;
    setHpBarFill(document.getElementById('bsPowerFillParty'), partyPct);
    document.getElementById('bsPartyPowerNum').textContent = Math.round(partyCurSum).toLocaleString();

    // [2026-10-20] 중앙 대치 장면 왼쪽 -- 대표 1명이 아니라 편성된 동료 전원이 나란히 선 대열(renderDuelParty). 좌우 스탯표 왼쪽은 파티 전체
    // (PARTY=인원, POWER=공격력 합계, GUARD=방어력 평균)이고, 오른쪽(몬스터)은 apiTowerStatus가 함께 내려준 monsterAtk/monsterDef 캐시를 쓴다.
    renderDuelParty(partyMembers);
    var atkSum = 0, defSum = 0;
    partyMembers.forEach(function (c) { atkSum += (c.EFF_ATK || 0); defSum += (c.EFF_DEF || 0); });
    document.getElementById('bsLeadLevel').textContent = partyMembers.length ? partyMembers.length + '명' : '-';
    document.getElementById('bsLeadPower').textContent = partyMembers.length ? fmtN(atkSum) : '-';
    document.getElementById('bsLeadGuard').textContent = partyMembers.length ? fmtN(Math.round(defSum / partyMembers.length)) : '-';
    document.getElementById('bsMonsterLevel').textContent = p.CUR_FLOOR || '-';
    document.getElementById('bsMonsterPower').textContent = (monsterAtkCache != null) ? monsterAtkCache : '-';
    document.getElementById('bsMonsterGuard').textContent = (monsterDefCache != null) ? monsterDefCache : '-';
  }

  // V1/V2 콘텐츠 표시 전환 + 두 버튼의 active 상태 반영(공용 헬퍼).
  function applyBattleScreenVersionUI(v) {
    var isV1 = v === 'V1';
    document.getElementById('battleScreenV1').style.display = isV1 ? '' : 'none';
    document.getElementById('battleScreenV2').style.display = isV1 ? 'none' : '';
    var btnV1 = document.getElementById('bsVersionBtnV1');
    var btnV2 = document.getElementById('bsVersionBtnV2');
    if (btnV1) btnV1.classList.toggle('active', isV1);
    if (btnV2) btnV2.classList.toggle('active', !isV1);
    document.querySelectorAll('.set-ver').forEach(function (b) { b.classList.toggle('active', b.dataset.v === v); });
  }

  // [2026-09-21] "전투화면 v1,v2는 db에저장해서 선택한걸 저장하도록 해줘" 요청, 이어서
  // "토글 버튼 클릭이 잘 안된다, 두개다 버튼 만들어서 눌려있는걸 표기해줘" 요청 -- 버튼 하나로
  // 토글하던 걸 V1/V2 각각 버튼으로 바꾸고(터치 타겟도 키움), 고른 버전을 서버(
  // setBattleScreenVersion)에 저장 후 다음 조회부터 그 버전으로 뜸(낙관적으로 즉시 화면도
  // 바꿔서 반응성 확보).
  function setBattleScreenVersion(v) {
    if (state.battleScreenVersion === v) return; // 이미 선택된 버전이면 아무 것도 안 함
    state.battleScreenVersion = v;
    applyBattleScreenVersionUI(v);
    action('SET_BATTLE_SCREEN_VERSION', v);
  }

  // 파티(동료) 쪽 배틀 화면(V1) 갱신 -- loadStatus(몬스터 HP)와 loadPartyAndEquip(동료 HP) 둘
  // 중 나중에 끝나는 쪽에서 최신 데이터로 다시 그려지도록 양쪽 다 이 함수를 부른다(요청 2개가
  // 서로 다른 fetch라 어느 쪽이 먼저 끝날지 보장이 없음). 전투 중이 아니거나 V2 표시 중이면
  // 조용히 무시.
  // [2026-09-21] V1/V2 둘 다 같은 파티 로스터 줄(.bs-party-row)을 쓴다(V2는 원래 이 줄이
  // 없어서 "동료가 한명만 보인다" 신고를 받았다) -- 화면 전환과 무관하게 두 줄 다 채워두고
  // 실제 표시 여부는 각 #battleScreenV1/V2의 display 토글에 맡긴다.
  function updateBattlePartyV1() {
    var companions = (lastParty.companions || []).filter(function (c) { return c.PARTY_SLOT; });
    // [버그 방지] 두 줄(V1/V2)을 순서대로 갱신하면서 매번 battle.companionHp를 바로 덮어쓰면,
    // 두 번째 줄을 처리할 때는 이미 prevHp===curHp가 되어 있어 "맞았을 때 흔들림" 연출이 V1
    // 쪽 줄에서만 뜨고 V2 쪽은 영영 안 뜬다 -- 그래서 감소 여부만 먼저 한 번에 계산해두고,
    // battle.companionHp 갱신도 두 줄을 모두 그린 다음 마지막에 한 번만 한다.
    var hpDropped = {};
    var newHp = {};
    companions.forEach(function (c) {
      var curHp = ppToBase(c.CUR_HP_VALUE, c.CUR_HP_EXT);
      var prevHp = battle.companionHp[c.COMPANION_ID];
      hpDropped[c.COMPANION_ID] = prevHp != null && curHp < prevHp;
      newHp[c.COMPANION_ID] = curHp;
    });
    ['bsPartyRowV1', 'bsPartyRowV2'].forEach(function (rowId) {
      var row = document.getElementById(rowId);
      if (!row) return;
      var ids = companions.map(function (c) { return c.COMPANION_ID; }).join(',');
      if (row.dataset.ids !== ids) {
        // 파티 구성 자체가 바뀐 경우(전투 진입 직후 최초 렌더 포함)만 다시 그린다 -- 매 갱신마다
        // innerHTML로 새로 만들면 엘리먼트가 새 걸로 바뀌어서 width transition이 끊겨 보인다.
        row.dataset.ids = ids;
        row.innerHTML = '';
        companions.forEach(function (c) {
          var box = document.createElement('div');
          box.className = 'bs-companion';
          box.dataset.cid = c.COMPANION_ID;
          box.appendChild(buildAvatarEl(c, 'bs-avatar', false));
          var meta = document.createElement('div');
          // [2026-09-17] "전투ui창에 캐릭터이름 옆에 직업아이콘도 만들고싶어" 요청.
          // [2026-09-18] "체력이 0이된 동료는 전투불능 표시를 하고싶어" 요청 -- 이름 줄
          // 아래에 상태 배지 자리를 하나 만들어두고(bs-status), 갱신 루프에서 채운다.
          meta.innerHTML = '<div class="bs-cname">' + (JOB_EMOJI[c.CLASS] || '') + ' ' + (c.NAME || JOB_KR[c.CLASS] || c.CLASS) + '</div>'
              + '<div class="bs-status incapacitated-badge" style="display:none">💀 전투불능</div>'
              + '<div class="hpbar-track"><div class="hpbar-fill" style="width:100%"></div></div>';
          box.appendChild(meta);
          row.appendChild(box);
        });
      }
      companions.forEach(function (c) {
        var box = row.querySelector('.bs-companion[data-cid="' + c.COMPANION_ID + '"]');
        if (!box) return;
        var maxHp = c.EFF_HP || 1;
        var curHp = newHp[c.COMPANION_ID];
        var pct = Math.max(0, Math.min(100, curHp / maxHp * 100));
        setHpBarFill(box.querySelector('.hpbar-fill'), pct);
        var down = isIncapacitated(curHp);
        box.classList.toggle('incapacitated', down);
        var statusEl = box.querySelector('.bs-status');
        if (statusEl) statusEl.style.display = down ? '' : 'none';
        if (hpDropped[c.COMPANION_ID]) {
          box.classList.remove('shake'); void box.offsetWidth; box.classList.add('shake');
        }
      });
    });
    battle.dropped = hpDropped; // 대치 장면 대열(renderDuelParty)이 같은 피격 정보를 쓴다
    companions.forEach(function (c) { battle.companionHp[c.COMPANION_ID] = newHp[c.COMPANION_ID]; });
  }

  // 주사위(공격) 버튼을 눌렀을 때 파티 전원이 짧게 앞으로 튀는 연출 -- 실제로 몇 명이 몇 번
  // 공격했는지는 서버 텍스트 메시지 안에만 있고 구조화되어 있지 않아, "파티가 매턴 전원 동시
  // 공격한다"는 실제 전투 규칙 그대로 전원 동시 연출로 단순화했다.
  // [2026-09-21] V2도 이제 파티 로스터 줄(bsPartyRowV2)이 있으므로 V1과 동일하게 펄스시키고,
  // V2 대표 아바타(bsLeadAvatarSlot)도 함께 펄스한다.
  function playBattleAttackMotion() {
    var rowId = state.battleScreenVersion === 'V1' ? 'bsPartyRowV1' : 'bsPartyRowV2';
    var row = document.getElementById(rowId);
    if (row) {
      row.querySelectorAll('.bs-companion').forEach(function (box) {
        box.classList.remove('bs-atk-pulse'); void box.offsetWidth; box.classList.add('bs-atk-pulse');
      });
    }
    if (state.battleScreenVersion !== 'V1') {
      document.querySelectorAll('#bsDuelParty .bs-duel-member').forEach(function (m) {
        m.classList.remove('bs-atk-pulse'); void m.offsetWidth; m.classList.add('bs-atk-pulse');
      });
    }
  }

  var monsterNameCache = ''; // updateBattleScreen()이 loadStatus() 밖에서도 이름을 쓸 수 있게 캐시
  var monsterAtkCache = null, monsterDefCache = null; // [2026-09-21] 전투화면 스탯표(POWER/GUARD)용 캐시

  // [2026-09-17 2차] "승리시 승리했다고 화면 띄워주면 좋을것같아" 요청 -- 서버는 전투 승리
  // 텍스트에 항상 " 처치! 🎉"를 포함한다(BotS5ServiceImpl.resolveCombatTurn의 monsterDead
  // 처리부, 단일/dual 전부 이 문구를 거침 -- 파티 전멸 감지에 "파티 전멸" 문자열을 쓰는 것과
  // 동일한 패턴). 승리 직후 STATUS가 바로 NORMAL로 바뀌어 전투화면이 곧 사라지므로, 이 뱃지는
  // 뷰포트 기준 고정 위치(.victory-flash가 position:fixed)라 전투화면이 사라져도 독립적으로
  // 잠깐 떴다 사라진다.
  function showVictoryFlash() {
    var el = document.getElementById('victoryFlash');
    if (!el) return;
    el.classList.remove('show'); void el.offsetWidth; el.classList.add('show');
    clearTimeout(showVictoryFlash._t);
    showVictoryFlash._t = setTimeout(function () { el.classList.remove('show'); }, 1500);
  }

  function toast(msg) {
    var el = document.getElementById('msgToast');
    var text = formatMsg(msg);
    el.textContent = text;
    el.style.display = 'block';
    clearTimeout(toast._t);
    var duration = Math.min(8000, Math.max(3200, text.length * 60));
    toast._t = setTimeout(function () { el.style.display = 'none'; }, duration);
  }

  var state = { inVillage: false, battleScreenVersion: 'V2' };

  // 주사위는 마을이 아니면서, 동시에 '탑' 화면을 보고 있을 때만 누를 수 있다.
  // [2026-09-09] 하단 도크 버튼(dbtnDice)을 없애고 보드 카드 안의 #boardRollBtn으로 옮김.
  function updateDiceButtonState() {
    var onBoardTab = document.getElementById('panel-board').classList.contains('active');
    document.getElementById('boardRollBtn').disabled = state.inVillage || !onBoardTab;
  }

  // ---- [2026-10-22] 하단 메뉴 순서/숨기기(유저별, 서버 DOCK_LAYOUT) -- 규칙은 서버 setDockLayout과 같다 ----
  var DOCK_DEFAULT = ['board', 'party', 'shop', 'inven', 'ach', 'codex', 'msg', 'settings', 'mine', 'mineshop'];
  var DOCK_LOCKED = { board: true, settings: true };
  var dockLayout = { order: DOCK_DEFAULT.slice(), hidden: {} };
  var lastDockStr = '';
  var dockSaveTimer = null;
  function parseDockLayout(str) {
    var order = [], hidden = {};
    String(str || '').split(',').forEach(function (t) {
      t = t.trim();
      var h = t.charAt(0) === '!';
      var k = h ? t.slice(1) : t;
      if (DOCK_DEFAULT.indexOf(k) === -1 || order.indexOf(k) !== -1) return;
      order.push(k);
      if (h && !DOCK_LOCKED[k]) hidden[k] = true;
    });
    DOCK_DEFAULT.forEach(function (k) { if (order.indexOf(k) === -1) order.push(k); });
    return { order: order, hidden: hidden };
  }
  function dockLayoutString(l) {
    var v = l.order.map(function (k) { return (l.hidden[k] ? '!' : '') + k; }).join(',');
    return v === DOCK_DEFAULT.join(',') ? '' : v;
  }
  // [2026-10-23] 광산/광산상점은 서버가 허용한 유저(관리자 또는 전체 오픈)에게만 보인다(tower-status mineOpen).
  var mineOpen = false;
  var MINE_TABS = { mine: true, mineshop: true };
  function applyDockLayout(str) {
    lastDockStr = str || '';
    dockLayout = parseDockLayout(str);
    var inner = document.querySelector('.dock-inner');
    if (!inner) return;
    dockLayout.order.forEach(function (k) {
      var b = inner.querySelector('.dbtn[data-tab="' + k + '"]');
      if (!b) return;
      inner.appendChild(b); // 순서대로 다시 붙이면 그 순서가 된다
      b.style.display = dockLayout.hidden[k] || (MINE_TABS[k] && !mineOpen) ? 'none' : '';
    });
    if (document.getElementById('panel-settings').classList.contains('active')) renderDockEdit();
  }
  function renderDockEdit() {
    var box = document.getElementById('dockEditList');
    if (!box) return;
    box.innerHTML = '';
    var inner = document.querySelector('.dock-inner');
    dockLayout.order.forEach(function (k, i) {
      if (MINE_TABS[k] && !mineOpen) return; // 광산 미오픈이면 설정 목록에서도 숨김
      var b = inner && inner.querySelector('.dbtn[data-tab="' + k + '"]');
      var icn = b ? (b.querySelector('.d-icn') || {}).textContent : '';
      var name = b ? b.textContent.replace(icn, '').trim() : k;
      var hidden = !!dockLayout.hidden[k];
      var row = document.createElement('div');
      row.className = 'dock-edit-row' + (hidden ? ' hidden-tab' : '');
      row.innerHTML = '<span class="de-no">' + (i + 1) + '</span><span class="de-icn">' + icn + '</span><span class="de-name">' + name + '</span>';
      var up = document.createElement('button'); up.type = 'button'; up.textContent = '▲'; up.disabled = i === 0;
      up.onclick = function () { moveDockTab(i, -1); };
      var down = document.createElement('button'); down.type = 'button'; down.textContent = '▼'; down.disabled = i === dockLayout.order.length - 1;
      down.onclick = function () { moveDockTab(i, 1); };
      var tg = document.createElement('button'); tg.type = 'button';
      tg.className = 'de-toggle' + (hidden ? ' off' : '');
      tg.textContent = DOCK_LOCKED[k] ? '고정' : (hidden ? '보이기' : '숨기기');
      tg.disabled = !!DOCK_LOCKED[k];
      tg.onclick = function () { toggleDockTab(k); };
      row.appendChild(up); row.appendChild(down); row.appendChild(tg);
      box.appendChild(row);
    });
  }
  function moveDockTab(i, d) {
    var j = i + d;
    if (j < 0 || j >= dockLayout.order.length) return;
    var o = dockLayout.order;
    var t = o[i]; o[i] = o[j]; o[j] = t;
    commitDockLayout();
  }
  function toggleDockTab(k) {
    if (DOCK_LOCKED[k]) return;
    if (dockLayout.hidden[k]) delete dockLayout.hidden[k]; else dockLayout.hidden[k] = true;
    commitDockLayout();
  }
  function resetDockLayout() {
    dockLayout = { order: DOCK_DEFAULT.slice(), hidden: {} };
    commitDockLayout();
  }
  // 화면은 바로 바꾸고, 서버 저장은 연속 클릭을 모아 0.6초 뒤 한 번만.
  function commitDockLayout() {
    var str = dockLayoutString(dockLayout);
    applyDockLayout(str);
    var st = document.getElementById('dockSaveState');
    if (st) st.textContent = '저장 중...';
    clearTimeout(dockSaveTimer);
    dockSaveTimer = setTimeout(function () {
      var u = userName();
      if (!u) { if (st) st.textContent = '(유저명이 있어야 저장돼요)'; return; }
      fetch(base + '/api/tower-action?userName=' + encodeURIComponent(u) + '&type=SET_DOCK_LAYOUT&param1=' + encodeURIComponent(str) + '&param2=')
        .then(function (r) { return r.json(); })
        .then(function () { if (st) st.textContent = '✓ 저장됨'; if (state.progress) state.progress.DOCK_LAYOUT = str; })
        .catch(function () { if (st) st.textContent = '저장 실패 -- 다시 시도해주세요'; });
    }, 600);
  }

  function switchTab(name) {
    document.querySelectorAll('.dbtn[data-tab]').forEach(function (b) { b.classList.toggle('active', b.dataset.tab === name); });
    document.querySelectorAll('.panel').forEach(function (p) { p.classList.toggle('active', p.id === 'panel-' + name); });
    if (name === 'shop') loadShop();
    if (name === 'ach') loadAchievements();
    if (name === 'party' || name === 'inven') loadPartyAndEquip();
    if (name === 'inven' && invTab === 'etc') renderInvEtc();
    if (name === 'inven' && invTab === 'ore') renderInvOre();
    if (name === 'mine') renderMinePanel();
    if (name === 'mineshop') renderMineShop();
    if (name === 'settings') renderDockEdit();
    if (name === 'msg') loadMessages();
    if (name === 'codex') loadCodex();
    updateDiceButtonState();
  }

  // [2026-09-28] RANDOM_LUCKY/MIDBOSS는 101층+ 계단 구역 전용 칸(BotS5ServiceImpl.STAIR_ZONE_PATTERN).
  var TILE_CLASS = { COMBAT: 'combat', TREASURE: 'treasure', PP: 'pp', TRAP: 'trap', SPECIAL: 'special',
                      STAIRS_UP: 'stairs-up', STAIRS_DOWN: 'stairs-down', ELITE: 'elite',
                      RANDOM_LUCKY: 'random-lucky', MIDBOSS: 'midboss' };
  var TILE_KR    = { COMBAT: '전투', TREASURE: '보물상자', PP: '럭키', TRAP: '함정', SPECIAL: '무너진사원',
                      STAIRS_UP: '계단↑', STAIRS_DOWN: '계단↓', ELITE: '강화몬스터',
                      RANDOM_LUCKY: '행운', MIDBOSS: '중간보스' };
  // BotS5ServiceImpl.STAIR_ZONE_START와 같은 값 -- 이 층부터 X0/X9층 규칙 없는 9칸 고정 계단 구역.
  var STAIR_ZONE_START = 101;
  var STAIR_ZONE_BASE_CAMP = 100;
  // [2026-09-29] 100의 배수(100/200/...)는 마을 -- BotS5ServiceImpl.isStairZone과 동일 규칙.
  function isStairZone(floor) { return floor >= STAIR_ZONE_START && floor % 100 !== 0; }
  function tileClass(type) { return TILE_CLASS[type] || 'combat'; }

  function loadStatus() {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    return fetch(base + '/api/tower-status?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (data.error) { toast(data.error); return; }
        checkAppVersionIfNewUser(); // 유저명이 방금 확정됐으면(로그인 직후 등) 그 유저 기준으로 공지 재확인
        var p = data.progress;
        // [버그 수정] 부분 입력("타락고냥이")이 서버에서 다른 실제 계정("타락고냥이/바드")으로
        // 해석됐을 수 있으니, 화면 표시와 입력창·세션 저장값을 전부 실제로 조회된 이름으로
        // 맞춰서 이후 다른 탭 호출도 같은(맞는) 계정을 보게 한다.
        u = p.USER_NAME || u;
        document.getElementById('userNameInput').value = u;
        sessionStorage.setItem('loaUserName', u);
        document.getElementById('whoName').textContent = u;
        document.getElementById('whoFloor').textContent = p.CUR_FLOOR + '층 · 상태 ' + p.STATUS
            + (p.AUTO_HUNT_YN === 'Y' ? ' · 자동사냥ON' : '');
        document.getElementById('ppVal').textContent = (p.PP_VALUE || 0).toFixed ? p.PP_VALUE.toFixed(2) + (p.PP_EXT || '') : p.PP_VALUE;
        state.progress = p; // [2026-09-18] 전설의조각 보유량 등, 다른 렌더 함수(renderEquipList)에서도 참조
        if ((p.DOCK_LAYOUT || '') !== lastDockStr || !!data.mineOpen !== mineOpen) { // [2026-10-22] 유저별 하단 메뉴 배치(바뀐 때만)
          mineOpen = !!data.mineOpen;
          var oreTabBtn = document.querySelector('.inv-tabs button[data-inv="ore"]');
          if (oreTabBtn) oreTabBtn.style.display = mineOpen ? '' : 'none';
          if (!mineOpen && invTab === 'ore') setInvTab('equip');
          applyDockLayout(p.DOCK_LAYOUT || '');
        }
        var legendFragmentEl = document.getElementById('legendFragmentVal');
        if (legendFragmentEl) legendFragmentEl.textContent = p.LEGEND_FRAGMENT || 0;

        // '편성' 탭(파티 슬롯 토글/장비 장착·합성)과 '상점' 탭(뽑기) 전부 전투 중만 아니면
        // 어디서든 가능하므로 탭 자체를 막지 않는다. inVillage는 아래 주사위 버튼(보드가
        // 없는 마을에서는 이동/전투가 의미 없음)에만 쓰인다.
        state.inVillage = (p.CUR_FLOOR % 10 === 0) && !isStairZone(p.CUR_FLOOR); // 110/120층 등은 계단 구역 일반 층
        updateDiceButtonState();
        renderTickets(p);

        var huntCard = document.getElementById('autoHuntCard');
        if (data.autoHunt) {
          huntCard.style.display = '';
          var h = data.autoHunt;
          var mins = h.elapsedMinutes || 0;
          var hh = Math.floor(mins / 60), mm = mins % 60;
          document.getElementById('autoHuntBox').innerHTML =
              h.floor + '층 (' + (h.monsterName || '?') + ') 기준으로 정산됩니다.<br>'
              + '시간당 약 ' + (h.ppPerHourFormatted || '?') + ' PP<br>'
              + '누적 경과: ' + hh + '시간 ' + mm + '분 (최대 8시간까지 인정, 다음 /주사위 때 일괄 정산)';
        } else {
          huntCard.style.display = 'none';
        }

        monsterNameCache = data.monsterName || '';
        monsterAtkCache = (data.monsterAtk != null) ? data.monsterAtk : null;
        monsterDefCache = (data.monsterDef != null) ? data.monsterDef : null;
        updateBattleScreen(p);
        if (data.stairZoneMax) STAIR_ZONE_MAX_FLOOR = data.stairZoneMax; // 서버가 정한 계단 구역 오픈 상한
        state.lastBattleFloor = (data.lastBattleFloor != null) ? data.lastBattleFloor : null;
        renderBoard(data.tiles, data.myTile ? data.myTile.CUR_TILE : 0, p.CUR_FLOOR);
        renderLastBattleBar(p);
        renderDiceOverlay(data.dice || []);
        renderDiceEnhanceRow(data.diceEnhance);
        renderMoveLimit(data.moveLimit);
        renderTowerNav(p, data.floorBest);
        // [버그 수정] "조회"는 상단 상태/보드만 다시 불러오고 파티·상점·업적 탭은 그대로 둬서,
        // 파티 탭 등을 보고 있는 채로 다른 닉네임을 검색하면 방금 조회한 유저 이름이 위에는
        // 보이는데 그 아래 탭 내용(동료 목록 등)은 이전 유저 것이 그대로 남아있는 문제가 있었다
        // ("닉네임 검색 시 기존 검색정보가 남아있다" 신고). 지금 열려있는 탭도 같이 새로고침.
        refreshActivePanel();
      })
      .catch(function () { toast('조회 실패'); });
  }

  // 지금 활성화된 탭(파티/상점/업적)이 있으면 그 데이터도 같이 새로고침 -- loadStatus()(닉네임
  // 재조회)와 action()(액션 후 갱신) 둘 다에서 공유해서 쓴다.
  function refreshActivePanel() {
    if (document.getElementById('panel-party').classList.contains('active') || document.getElementById('panel-inven').classList.contains('active')) loadPartyAndEquip();
    if (document.getElementById('panel-shop').classList.contains('active')) loadShop();
    if (document.getElementById('panel-ach').classList.contains('active')) loadAchievements();
    if (document.getElementById('panel-msg').classList.contains('active')) loadMessages();
  }

  // 층이동을 셀렉트박스+버튼 대신 부루마불 옆에 세우는 "탑" 그림으로 -- 9층(보스)이 맨 위,
  // 0층(마을)이 맨 아래에 오도록 n=9부터 역순으로 그린다. 탐사완료(FULLY_EXPLORED_YN)된 사냥터
  // 층엔 ✅ 배지를 붙이고, 아직 한 번도 계단으로 못 간 층(alwaysFree 아니고 MAX_FLOOR_REACHED
  // 미만)은 흐리게 표시 + 눌러도 이동 대신 안내만 뜨게 한다(실제 검증은 서버 changeFloor()가
  // 최종적으로 하므로 여기 reachable 판정은 UX용 힌트일 뿐).
  function renderTowerNav(p, floorBest) {
    var nav = document.getElementById('towerNav');
    nav.innerHTML = '';
    var curFloor = p.CUR_FLOOR;
    // [2026-09-28] 100층 마을/101층+ 계단 구역은 "X9=보스, X0=마을, 탑올라가기" 블록 그림이 안 맞아 별도로 그린다.
    if (curFloor >= STAIR_ZONE_BASE_CAMP) { renderStairZoneNav(nav, p); return; }
    var blockBase = curFloor - (curFloor % 10);
    var maxReached = p.MAX_FLOOR_REACHED || 0;
    var bestByFloor = {};
    (floorBest || []).forEach(function (b) { bestByFloor[b.FLOOR] = b; });

    // "보스를 처치한 구간은 위 마을로 바로 이동하게 해달라(탑올라가기)" 요청 -- 탑다운과
    // 대칭으로 목록 맨 위에 전용 행. 이 구간 보스를 이미 처치해서(UNLOCKED_BLOCK) 위 구간이
    // 열려있고 지금 이 구간 마을에 있을 때만(서버와 동일 조건) 이동 가능, 아니면 잠금 표시.
    var unlockedBlock = p.UNLOCKED_BLOCK || 0;
    var canTowerUp = (curFloor % 10 === 0) && (unlockedBlock >= blockBase + 10);
    var upRow = document.createElement('div');
    upRow.className = 'tower-floor boss' + (canTowerUp ? '' : ' locked');
    upRow.innerHTML = '<span class="tf-left"><span class="tf-num">⬆️</span>'
        + '<span>' + (blockBase + 10) + '층 마을</span><span class="tf-kind">탑올라가기</span></span>';
    upRow.onclick = canTowerUp
        ? function () { confirmTowerUp(blockBase + 10); }
        : function () { toast('탑올라가기는 이 구간 보스를 처치한 뒤, 이 구간 마을에서만 사용할 수 있습니다.'); };
    nav.appendChild(upRow);

    for (var n = 9; n >= 0; n--) {
      var floor = blockBase + n;
      var isHere = (floor === curFloor);
      var alwaysFree = (n === 0) || (n === 1); // 마을<->그 구간 첫 사냥터층은 항상 자유 이동(서버 changeFloor와 동일 규칙)
      var reachable = alwaysFree || floor <= maxReached;
      var kind = n === 0 ? '마을' : (n === 9 ? '보스' : '사냥터');
      var best = bestByFloor[floor];
      var explored = best && best.FULLY_EXPLORED_YN === 'Y';

      var row = document.createElement('div');
      row.className = 'tower-floor'
          + (isHere ? ' here' : '')
          + (n === 0 ? ' village' : '') + (n === 9 ? ' boss' : '')
          + (reachable ? '' : ' locked');
      row.innerHTML = '<span class="tf-left"><span class="tf-num">' + n + '</span>'
          + '<span>' + floor + '층</span><span class="tf-kind">' + kind + '</span></span>'
          + (isHere ? '<span class="tf-explored" style="color:var(--gold);">📍 현재 위치</span>'
                    : (explored ? '<span class="tf-explored">✅ 탐사완료</span>' : ''));
      if (reachable && !isHere) {
        row.onclick = (function (targetFloor, targetN, targetKind) {
          return function () { confirmMove(targetFloor, targetN, targetKind); };
        })(floor, n, kind);
      } else if (!reachable) {
        row.onclick = function () { toast('아직 가본 적 없는 층입니다. 계단으로 먼저 올라가야 해요.'); };
      }
      nav.appendChild(row);
    }

    // "웹에서도 탑내려가기(탑다운) 되게 해달라, 층이동 그림 맨 아래에 두자" 요청 -- 0층
    // 마을보다 한 줄 더 아래, 목록 맨 끝에 전용 행 추가. 0블록(1~9층)엔 더 내려갈 구간이
    // 없으므로 표시하지 않는다. 서버와 동일하게 "지금 이 구간 마을에 있을 때만" 가능.
    if (blockBase > 0) {
      var canTowerDown = (curFloor % 10 === 0);
      var downRow = document.createElement('div');
      downRow.className = 'tower-floor village' + (canTowerDown ? '' : ' locked');
      downRow.innerHTML = '<span class="tf-left"><span class="tf-num">⬇️</span>'
          + '<span>' + (blockBase - 10) + '층 마을</span><span class="tf-kind">계단내려가기</span></span>';
      downRow.onclick = canTowerDown
          ? function () { confirmTowerDown(blockBase - 10); }
          : function () { toast('계단내려가기는 지금 있는 구간의 마을에서만 사용할 수 있습니다.'); };
      nav.appendChild(downRow);
    }
  }

  /** [2026-09-28] 100층 마을 + 101층+ 계단 구역 전용 층이동 그림. 층마다 마을이 없어서
   *  목록은 "지금 10층 단위의 계단층들 + 맨 아래 100층 마을(부활)" 구성이고, 이동은 층 번호를
   *  그대로 보낸다(서버 changeFloor가 101 이상 숫자를 "그 층으로 바로 이동"으로 처리 --
   *  이미 가본 층까지만). 가본 최고층이 목록 밖이면 맨 위에 바로가기 행을 둔다. */
  var STAIR_ZONE_MAX_FLOOR = 200;
  function renderStairZoneNav(nav, p) {
    var curFloor = p.CUR_FLOOR;
    var maxReached = Math.min(p.MAX_FLOOR_REACHED || 0, STAIR_ZONE_MAX_FLOOR);
    var blockBase = curFloor - (curFloor % 10);
    var campFloor = Math.max(STAIR_ZONE_BASE_CAMP, Math.floor(curFloor / 100) * 100); // 돌아갈 마을(100의 배수)
    if (maxReached > STAIR_ZONE_START && maxReached !== curFloor
        && (maxReached < blockBase || maxReached > blockBase + 9)) {
      var resumeRow = document.createElement('div');
      resumeRow.className = 'tower-floor boss';
      resumeRow.innerHTML = '<span class="tf-left"><span class="tf-num">⏩</span><span>' + maxReached
          + '층</span><span class="tf-kind">가본 최고층으로</span></span>';
      resumeRow.onclick = function () { confirmMoveAbs(maxReached, isStairZone(maxReached) ? '계단층' : '마을'); };
      nav.appendChild(resumeRow);
    }
    for (var n = 9; n >= 0; n--) {
      var floor = blockBase + n;
      if (!isStairZone(floor) || floor > STAIR_ZONE_MAX_FLOOR) continue; // 100의 배수(마을)는 아래 마을 행이 담당
      var isHere = (floor === curFloor);
      var reachable = (floor === STAIR_ZONE_START) || floor <= maxReached;
      var row = document.createElement('div');
      row.className = 'tower-floor' + (isHere ? ' here' : '') + (reachable ? '' : ' locked');
      row.innerHTML = '<span class="tf-left"><span class="tf-num">🪜</span>'
          + '<span>' + floor + '층</span><span class="tf-kind">계단층</span></span>'
          + (isHere ? '<span class="tf-explored" style="color:var(--gold);">📍 현재 위치</span>' : '');
      if (reachable && !isHere) {
        row.onclick = (function (targetFloor) {
          return function () { confirmMoveAbs(targetFloor, '계단층'); };
        })(floor);
      } else if (!reachable) {
        row.onclick = function () { toast('아직 가본 적 없는 층입니다. 계단 구역은 한 층씩 직접 올라가야 해요.'); };
      }
      nav.appendChild(row);
    }
    var atCamp = (curFloor === campFloor);
    var campRow = document.createElement('div');
    campRow.className = 'tower-floor village' + (atCamp ? ' here' : '');
    campRow.innerHTML = '<span class="tf-left"><span class="tf-num">0</span>'
        + '<span>' + campFloor + '층</span><span class="tf-kind">마을(부활)</span></span>'
        + (atCamp ? '<span class="tf-explored" style="color:var(--gold);">📍 현재 위치</span>' : '');
    if (!atCamp) campRow.onclick = function () { confirmMove(campFloor, 0, '마을'); };
    nav.appendChild(campRow);
    var downRow = document.createElement('div');
    // [2026-09-29] 계단내려가기: 마을에선 아래 마을로, 계단 구역(101층+)에선 이 구간 100층 마을로.
    var downTarget = atCamp ? (campFloor > STAIR_ZONE_BASE_CAMP ? campFloor - 100 : campFloor - 10) : campFloor;
    downRow.className = 'tower-floor village';
    downRow.innerHTML = '<span class="tf-left"><span class="tf-num">⬇️</span>'
        + '<span>' + downTarget + '층 마을</span><span class="tf-kind">계단내려가기</span></span>';
    downRow.onclick = function () { confirmTowerDown(downTarget); };
    nav.appendChild(downRow);
  }

  // ===== [2026-09-30] 최근 전투층 바로가기 + 마을 미니맵 + 던전 팝업 =====
  function floorKindLabel(f) {
    if (isStairZone(f)) return '계단층';
    return f % 10 === 9 ? '보스' : '사냥터';
  }

  /** 지금 위치(cur)에서 last층으로 "한 번에" 갈 수 있는지 서버 changeFloor 규칙대로 판정.
   *  반환: { ok, param, abs, why }. cur>=100(100층 마을/계단 구역)은 101~200 사이 가본 층 번호로 직접 이동,
   *  그 아래는 같은 10층 구간(마을 기준 n=1~9) 안에서만 이동 가능(다른 구간이면 마을을 거쳐야 함). */
  function lastBattleRoute(cur, last, maxReached) {
    if (last == null || last === cur) return null;
    if (cur >= STAIR_ZONE_BASE_CAMP) {
      if (last >= STAIR_ZONE_START && last <= STAIR_ZONE_MAX_FLOOR && (last === STAIR_ZONE_START || last <= maxReached)) {
        return { ok: true, param: String(last), abs: true };
      }
      return { ok: false, why: last + '층은 계단 구역 밖(아래 구간)이라 마을에서 탑내려가기로 이동해야 해요.' };
    }
    var base = cur - (cur % 10);
    if (last > base && last < base + 10) {
      var n = last - base;
      if (n === 1 || last <= maxReached) return { ok: true, param: String(n), abs: false };
      return { ok: false, why: last + '층은 아직 가본 적이 없어요.' };
    }
    return { ok: false, why: last + '층은 다른 구간이라 지금 위치에서 바로 갈 수 없어요. 마을에서 탑올라가기/내려가기로 이동하세요.' };
  }

  function goLastBattle(route, last) {
    if (route.abs) confirmMoveAbs(last, floorKindLabel(last));
    else confirmMove(last, parseInt(route.param, 10), floorKindLabel(last));
  }

  function renderLastBattleBar(p) {
    var bar = document.getElementById('lastBattleBar');
    if (!bar) return;
    var last = state.lastBattleFloor;
    // [2026-09-30] 표시/숨김으로 높이가 바뀌면 그 아래 전투화면/주사위 버튼이 위아래로 밀려 스크롤이 튀므로, 최근 전투층
    // 기록이 있는 유저는 자리를 항상 유지하고(visibility만 전환) 전투 중에도 숨긴다.
    if (last == null) { bar.style.display = 'none'; bar.innerHTML = ''; return; }
    bar.style.display = 'block';
    bar.style.minHeight = '42px';
    if (last === p.CUR_FLOOR || p.STATUS === 'IN_COMBAT') { bar.style.visibility = 'hidden'; bar.innerHTML = ''; return; }
    bar.style.visibility = 'visible';
    var route = lastBattleRoute(p.CUR_FLOOR, last, p.MAX_FLOOR_REACHED || 0);
    bar.innerHTML = '';
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'last-battle-btn' + (route && route.ok ? '' : ' dim');
    btn.textContent = '⚔️ 최근 전투층 ' + last + '층으로 바로가기';
    btn.onclick = function () {
      if (route && route.ok) goLastBattle(route, last); else toast(route ? route.why : '이동할 수 없습니다.');
    };
    bar.appendChild(btn);
  }

  function renderVillageMap(track, floor) {
    var p = state.progress || {};
    var maxReached = p.MAX_FLOOR_REACHED || 0;
    var unlockedBlock = p.UNLOCKED_BLOCK || 0;
    var deep = floor >= STAIR_ZONE_BASE_CAMP; // 100/200층 마을: 위로 가면 101층+ 계단 구역

    // 위쪽 출구 = 상향 이동
    var upLabel, upSub, upOk, upFn;
    if (deep) {
      var firstUp = floor + 1;
      upLabel = '⬆ ' + firstUp + '층 (계단 구역)'; upSub = '위층으로 올라가기';
      upOk = firstUp <= STAIR_ZONE_MAX_FLOOR;
      upFn = upOk ? function () { confirmMove(firstUp, 1, '계단층'); }
                  : function () { toast('🌑 ' + firstUp + '층 이상은 추후 오픈 예정입니다.'); };
    } else {
      var upTo = floor + 10;
      upLabel = '⬆ ' + upTo + '층 마을'; upSub = '탑올라가기 (이 구간 보스 처치 필요)';
      upOk = unlockedBlock >= upTo;
      upFn = upOk ? function () { confirmTowerUp(upTo); }
                  : function () { toast('탑올라가기는 이 구간 보스를 처치해야 열립니다.'); };
    }
    // 아래쪽 출구 = 하향 이동
    var downTo = floor > STAIR_ZONE_BASE_CAMP ? floor - 100 : floor - 10;
    var canDown = floor > 0;

    var html = '<div class="vmap">'
        + '<button type="button" class="vm-gate up' + (upOk ? '' : ' locked') + '" id="vmUp">' + upLabel + '<small>' + upSub + '</small></button>'
        + '<div class="vm-field">'
        + '<div class="vm-road-v"></div><div class="vm-road-h"></div><div class="vm-plaza">⛲</div>'
        + '<div class="vm-title">🏘️ ' + floor + '층 마을</div>'
        + '<div class="vm-you" title="현재 위치">🧍</div>'
        + '<button type="button" class="vm-bld" id="vmShop" style="left:22%;top:26%"><span class="vb-ico">🛍️</span><span class="vb-name">상점</span><span class="vb-sub">뽑기·강화</span></button>'
        + '<button type="button" class="vm-bld dungeon" id="vmDungeon" style="left:78%;top:26%"><span class="vb-ico">🕳️</span><span class="vb-name">던전</span><span class="vb-sub">사냥터 입구</span></button>'
        + '<button type="button" class="vm-bld" id="vmCraft" style="left:22%;top:76%"><span class="vb-ico">⚒️</span><span class="vb-name">전설제작</span><span class="vb-sub">조각 10개</span></button>'
        + '<button type="button" class="vm-bld" id="vmDisenchant" style="left:78%;top:76%"><span class="vb-ico">💎</span><span class="vb-name">전설해체</span><span class="vb-sub">조각 환급</span></button>'
        + '</div>'
        + (canDown ? '<button type="button" class="vm-gate down" id="vmDown">⬇ ' + downTo + '층 마을<small>계단내려가기</small></button>' : '')
        + '</div>';
    track.innerHTML = html;

    document.getElementById('vmUp').onclick = upFn;
    if (canDown) document.getElementById('vmDown').onclick = function () { confirmTowerDown(downTo); };
    document.getElementById('vmShop').onclick = function () { switchTab('shop'); };
    document.getElementById('vmDungeon').onclick = function () { openDungeon(floor); };
    document.getElementById('vmCraft').onclick = function () {
      var frag = p.LEGEND_FRAGMENT || 0;
      if (frag < LEGEND_CRAFT_COST_UI) { toast('🧩 전설의 조각이 부족합니다. (' + frag + '/' + LEGEND_CRAFT_COST_UI + ')'); return; }
      openLegendRoster();
    };
    document.getElementById('vmDisenchant').onclick = function () { openLegendDisenchant(); };
  }

  function openDungeon(village) {
    var p = state.progress || {};
    var box = document.getElementById('dungeonOptions');
    box.innerHTML = '';
    function addOpt(ico, name, sub, locked, fn) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'dungeon-opt' + (locked ? ' locked' : '');
      b.innerHTML = '<span class="do-ico">' + ico + '</span><span><span class="do-name">' + name + '</span><br><span class="do-sub">' + sub + '</span></span>';
      b.onclick = fn;
      box.appendChild(b);
    }
    var first = village + 1;
    var firstBlocked = first > STAIR_ZONE_MAX_FLOOR;
    addOpt('🚪', first + '층으로 이동', (first > STAIR_ZONE_BASE_CAMP ? '계단 구역' : '이 마을의 첫 사냥터') + '층',
        firstBlocked, function () {
          if (firstBlocked) { toast('🌑 ' + first + '층 이상은 추후 오픈 예정입니다.'); return; }
          closeDungeon(); confirmMove(first, 1, floorKindLabel(first));
        });
    var last = state.lastBattleFloor;
    if (last != null && last !== village && last !== first) {
      var route = lastBattleRoute(village, last, p.MAX_FLOOR_REACHED || 0);
      addOpt('⚔️', '이전 전투층 ' + last + '층으로 이동', route && route.ok ? '가장 최근에 싸운 층' : (route ? route.why : ''),
          !(route && route.ok), function () {
            if (route && route.ok) { closeDungeon(); goLastBattle(route, last); } else toast(route ? route.why : '이동할 수 없습니다.');
          });
    }
    document.getElementById('dungeonOverlay').classList.add('open');
  }
  function closeDungeon() { document.getElementById('dungeonOverlay').classList.remove('open'); }

  function confirmMoveAbs(floor, kind) {
    document.getElementById('confirmMsg').textContent = floor + '층(' + kind + ')으로 정말 이동하시겠습니까?';
    document.getElementById('confirmYesBtn').onclick = function () {
      closeConfirm();
      action('CHANGE_FLOOR', String(floor));
    };
    document.getElementById('confirmOverlay').classList.add('open');
  }

  function confirmMove(floor, n, kind) {
    document.getElementById('confirmMsg').textContent = floor + '층(' + kind + ')으로 정말 이동하시겠습니까?';
    document.getElementById('confirmYesBtn').onclick = function () {
      closeConfirm();
      action('CHANGE_FLOOR', String(n));
    };
    document.getElementById('confirmOverlay').classList.add('open');
  }

  function confirmTowerDown(targetFloor) {
    document.getElementById('confirmMsg').textContent = '계단내려가기: ' + targetFloor + '층 마을로 내려가시겠습니까?';
    document.getElementById('confirmYesBtn').onclick = function () {
      closeConfirm();
      action('TOWER_DOWN', '');
    };
    document.getElementById('confirmOverlay').classList.add('open');
  }

  function confirmTowerUp(targetFloor) {
    document.getElementById('confirmMsg').textContent = '탑올라가기: ' + targetFloor + '층 마을로 올라가시겠습니까?';
    document.getElementById('confirmYesBtn').onclick = function () {
      closeConfirm();
      action('TOWER_UP', '');
    };
    document.getElementById('confirmOverlay').classList.add('open');
  }

  // [2026-10-22] 주사위 게이트 팝업 -- PATCH: 패치 내용 + "확인했습니다"(PATCH_ACK 후 다시 굴릴 수 있음), LEGEND: 전설 규칙 위반 안내 + 편성 탭으로.
  function showDiceGate(type, msg) {
    document.getElementById('gateTitle').textContent = type === 'PATCH' ? '📢 전투 밸런스 패치 안내' : '⚠️ 전설장비 착용 정리 필요';
    document.getElementById('gateBody').textContent = formatMsg(msg);
    var ok = document.getElementById('gateOkBtn');
    if (type === 'PATCH') {
      ok.textContent = '확인했습니다';
      ok.onclick = function () { closeGate(); action('PATCH_ACK', ''); };
    } else {
      ok.textContent = '편성 탭으로';
      ok.onclick = function () { closeGate(); switchTab('party'); };
    }
    document.getElementById('gateOverlay').classList.add('open');
  }
  function closeGate() { document.getElementById('gateOverlay').classList.remove('open'); }

  // [2026-10-23] 일반 장비 분해 팝업 -- 서버 dismantleEquip과 같은 확률표(개당, %).
  var DISMANTLE_FRAGMENT_PCT = { 1: 0.1, 2: 0.1, 3: 0.1, 4: 0.3, 5: 1, 6: 10 };
  var dismantleItems = {};
  var dismantleCur = null;
  function openDismantle(equipId) {
    var it = dismantleItems[equipId];
    if (!it) return;
    dismantleCur = it;
    document.getElementById('dmTitle').textContent = '🔨 ' + it.label + ' ★' + it.grade + ' 분해';
    document.getElementById('dmHave').textContent = '보유 ' + it.qty + '개 · 개당 전설의조각 확률 ' + DISMANTLE_FRAGMENT_PCT[it.grade] + '%';
    var multi = it.qty > 1;
    document.getElementById('dmQtyRow').style.display = multi ? '' : 'none';
    document.getElementById('dmQuick').style.display = multi ? '' : 'none';
    document.getElementById('dmQty').max = it.qty;
    dmSet(1);
    document.getElementById('dismantleOverlay').classList.add('open');
  }
  function closeDismantle() { document.getElementById('dismantleOverlay').classList.remove('open'); dismantleCur = null; }
  function dmQty() {
    var n = parseInt(document.getElementById('dmQty').value, 10);
    return isNaN(n) ? 1 : n;
  }
  function dmSet(v) {
    if (!dismantleCur) return;
    var n = v === 'all' ? dismantleCur.qty : v === 'half' ? Math.max(1, Math.floor(dismantleCur.qty / 2)) : v;
    document.getElementById('dmQty').value = n;
    dmStep(0);
  }
  function dmStep(d) {
    if (!dismantleCur) return;
    var n = Math.max(1, Math.min(dismantleCur.qty, dmQty() + d));
    if (d !== 0 || String(n) !== document.getElementById('dmQty').value) document.getElementById('dmQty').value = n;
    var pct = DISMANTLE_FRAGMENT_PCT[dismantleCur.grade] || 0;
    var exp = n * pct / 100;
    document.getElementById('dmExpect').textContent = n + '개 분해 · 기대 전설의조각 약 ' + (exp >= 1 ? exp.toFixed(1) : exp.toFixed(2)) + '개 (확률이라 0개일 수도 있어요)';
    document.getElementById('dmOkBtn').textContent = n + '개 분해';
  }
  function doDismantle() {
    if (!dismantleCur) return;
    var n = Math.max(1, Math.min(dismantleCur.qty, dmQty()));
    var id = dismantleCur.id;
    closeDismantle();
    action('EQUIP_DISMANTLE', String(id), String(n));
  }

  function closeConfirm() {
    document.getElementById('confirmOverlay').classList.remove('open');
    document.getElementById('confirmYesBtn').textContent = '이동'; // 이미지 바꾸기 확인창이 라벨을 '바꾸기'로 바꿔 쓴다
  }

  // [2026-09-05] "던전앤파이터 보스맵/카트라이더 느낌으로 구불구불하게, 시작과 끝이 이어지면
  // 좋겠다" 요청으로 정사각형 둘레 배치를 곡선 트랙으로 교체. [2026-09-06] "Z자만 하지 말고
  // 여러 형태를 알아서 만들어줘, 50층 아래도 다 다르게" 요청(마리오카트 코스 아이콘 스샷 참고)
  // -- 고정 4종을 floor%4로 돌려쓰던 방식은 4층마다 모양이 겹쳤다(11층=51층 신고). 손으로
  // 그린 곡선 대신, 층 번호를 시드로 한 의사난수로 매번 다른 닫힌 루프를 "생성"한다 --
  // 꼭짓점 개수/각도 흔들림/반지름/가로세로 비율/각진 정도(각진 Z·S 느낌 vs 부드러운 블롭)를
  // 전부 시드로 결정해서 같은 층은 항상 같은 모양, 다른 층은 웬만하면 다른 모양이 나온다.
  var LARGE_BOARD_TILE_THRESHOLD = 60;

  /** 시드 기반 의사난수(선형합동법) -- 같은 시드면 항상 같은 순서의 숫자를 뽑는다. */
  function seededRandom(seed) {
    var s = seed % 2147483647;
    if (s <= 0) s += 2147483646;
    return function () {
      s = (s * 16807) % 2147483647;
      return (s - 1) / 2147483646;
    };
  }

  /** 층 번호(+칸 수)를 시드로 매번 다른 닫힌 루프를 만든다(작은~중간 보드용). 중심에서
   * 각도를 고르게 나눠 뽑은 뒤(각도가 항상 증가하는 순서라 스스로 안 꼬임) 반지름/가로세로
   * 비율을 흔들어 별 모양 정사각형·타원·지그재그 등 다양한 실루엣을 낸다. angular(직선 연결,
   * "Z자/S자" 느낌)와 부드러운 곡선을 절반씩 섞는다. */
  function buildLoopPath(floor, n) {
    var rnd = seededRandom((floor || 0) * 9301 + 49297 + (n || 0));
    var margin = 12, cx = 50, cy = 50, maxR = 38;
    var verts = 6 + Math.floor(rnd() * 6); // 6~11각형
    var angular = rnd() < 0.5;
    var rx = 0.7 + rnd() * 0.6, ry = 0.7 + rnd() * 0.6; // 가로/세로를 다르게 눌러 타원·직사각 느낌도 나오게
    var pts = [];
    for (var i = 0; i < verts; i++) {
      var angle = (i / verts) * Math.PI * 2;
      var jitter = (rnd() - 0.5) * (Math.PI / verts) * 0.7; // 각도를 살짝만 흔들어서(별모양 자기교차 방지) 각지게
      var r = maxR * (0.55 + rnd() * 0.45);
      var x = cx + Math.cos(angle + jitter) * r * rx;
      var y = cy + Math.sin(angle + jitter) * r * ry;
      pts.push([Math.max(margin, Math.min(100 - margin, x)), Math.max(margin, Math.min(100 - margin, y))]);
    }
    var d = 'M' + pts[0][0].toFixed(1) + ',' + pts[0][1].toFixed(1) + ' ';
    if (angular) {
      for (var j = 1; j < pts.length; j++) d += 'L' + pts[j][0].toFixed(1) + ',' + pts[j][1].toFixed(1) + ' ';
      d += 'Z';
    } else {
      // 부드러운 닫힌 곡선 -- 인접한 두 점의 중점을 향해 quadratic curve로 이어서(각 꼭짓점을
      // control point 삼아) 뾰족하지 않게 둥글린다.
      for (var k = 0; k < pts.length; k++) {
        var p1 = pts[k], p2 = pts[(k + 1) % pts.length];
        var mid = [(p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2];
        d += 'Q' + p1[0].toFixed(1) + ',' + p1[1].toFixed(1) + ' ' + mid[0].toFixed(1) + ',' + mid[1].toFixed(1) + ' ';
      }
      d += 'Z';
    }
    return d;
  }

  /** [2026-09-14] "카트라이더 맵처럼 랜덤성이 있었으면 좋겠다, 대신 간격이 있어야 겹치지
   * 않을 것 같다" 요청 -- 완전 자유형 랜덤 곡선(작은 보드의 buildLoopPath처럼)은 칸이
   * 100~200개나 되는 큰 보드에 그대로 쓰면 정사각형 좌표계를 세로로 길게 늘여 그리는 과정에서
   * 칸 간격이 불균등해지고(가로적인 구간은 늘어나고 세로적인 구간은 눌림), 자기교차/겹침
   * 위험도 커진다. 대신 기존의 "줄(행 또는 열) 단위 왕복" 뼈대는 그대로 두고(줄 사이 간격이
   * 곧 안전 마진이 되어 겹침을 구조적으로 방지) 각 줄에 완만한 사인파를 얹어서 층마다 다른
   * 곡선 모양이 나오게 한다. 각 줄의 시작/끝(t=0,1)에서는 sin(t*PI) 포락선으로 진폭이 0으로
   * 수렴하게 해서, 줄이 꺾이는 모서리는 항상 격자에 정확히 물리게 한다(기존 닫힘 구간
   * 로직이 모서리 좌표를 그대로 신뢰하므로 이게 깨지면 안 됨).
   * [2026-09-14 재수정] "곡선이 그다지 안 보인다, 곡선이 좀 더 있으면 좋겠다"는 재요청에
   * "이미 스크롤 만든 김에 아래로 길게 늘리면 되지 않겠냐"는 제안을 받아, 가로 왕복(줄=행)
   * 보드는 세로 칸 간격(spacingY)을 가로 칸 간격(spacingX)보다 STRETCH_K배 더 크게 벌려서
   * (renderBoard 참고, 세로 스크롤 길이만 늘어남 -- 이미 지원 중) 줄 사이 여유가 커진 만큼
   * 진폭 상한도 같이 키운다(아래 ampMin/ampMax 파라미터, renderBoard에서 실측 간격 기준으로
   * 동적 계산). 세로 왕복(줄=열) 보드는 가로 스크롤이 없어 간격을 못 늘리므로 예전 7~13%
   * 그대로 유지(안전함이 이미 검증됨) -- 대신 방향 선택 확률 자체를 낮춰서(gridVertical
   * 30%만) 더 곡선이 두드러지는 가로 왕복 쪽이 더 자주 나오게 했다.
   * [2026-09-14 3차 수정] "한 개의 길도 구불구불했으면 좋겠다"는 요청 -- 줄 하나에 봉우리가
   * 1~2개(freq 1~2)뿐이면 완만한 "C"자 하나로만 보인다. freq를 2~4로 올려서 줄 하나 안에서도
   * 최소 2번, 많으면 4번 방향을 바꾸며 꿈틀거리게 했다(진폭 상한은 그대로라 안전함엔 영향
   * 없음 -- waveOffset의 진폭 상한은 주파수와 무관하게 항상 ampFrac*gap로 고정). */
  function buildRowWaves(rnd, bandCount, ampMin, ampMax) {
    var waves = [];
    for (var i = 0; i < bandCount; i++) {
      waves.push({ ampFrac: ampMin + rnd() * (ampMax - ampMin), phase: rnd() * Math.PI * 2, freq: 2 + Math.floor(rnd() * 3) });
    }
    return waves;
  }
  function waveOffset(wave, gap, t) {
    if (!wave) return 0;
    var tt = Math.min(1, Math.max(0, t));
    var envelope = Math.sin(tt * Math.PI); // 줄 시작/끝(t=0,1)에서 0으로 수렴
    return wave.ampFrac * gap * Math.sin(wave.phase + tt * Math.PI * 2 * wave.freq) * envelope;
  }

  /** [2026-09-14] "곡선이 좀 더 있으면 좋겠다" 요청 -- 배경 트랙 선을 짧은 직선(L) 여러 개로
   * 잇는 대신, Catmull-Rom 스플라인을 3차 베지어로 변환해서 그린다(표준 변환식, tension=0 상당
   * -- 각 구간 제어점을 이웃 두 점의 차이/6으로 계산). 점 배열(pts)만 그대로 넣으면 되고, 줄이
   * 바뀌는 모서리(직각으로 꺾이던 지점)도 이웃 점들의 접선 방향을 따라 자동으로 둥글게
   * 이어진다(꺾이는 지점=격자 진입/이탈 지점이라 실제 칸은 여전히 격자 좌표 그대로 -- 이 선은
   * 순전히 장식용 배경이라 살짝 둥글게 지나가도 문제 없음). */
  function catmullRomPath(pts) {
    if (pts.length < 3) {
      var s = 'M' + pts[0][0].toFixed(2) + ',' + pts[0][1].toFixed(2);
      for (var i = 1; i < pts.length; i++) s += ' L' + pts[i][0].toFixed(2) + ',' + pts[i][1].toFixed(2);
      return s;
    }
    var d = 'M' + pts[0][0].toFixed(2) + ',' + pts[0][1].toFixed(2);
    for (var i = 0; i < pts.length - 1; i++) {
      var p0 = pts[i - 1] || pts[i];
      var p1 = pts[i];
      var p2 = pts[i + 1];
      var p3 = pts[i + 2] || p2;
      var c1x = p1[0] + (p2[0] - p0[0]) / 6, c1y = p1[1] + (p2[1] - p0[1]) / 6;
      var c2x = p2[0] - (p3[0] - p1[0]) / 6, c2y = p2[1] - (p3[1] - p1[1]) / 6;
      d += ' C' + c1x.toFixed(2) + ',' + c1y.toFixed(2) + ' ' + c2x.toFixed(2) + ',' + c2y.toFixed(2) + ' ' + p2[0].toFixed(2) + ',' + p2[1].toFixed(2);
    }
    return d;
  }

  /** 지그재그(뱀) 경로 생성 -- cols×rows 격자를 한 줄씩 좌우(또는 위아래)로 왕복하며 잇는다
   * (waves가 있으면 직선 대신 완만한 곡선, 위 buildRowWaves/waveOffset 참고). 칸 수(n)가
   * 많은 51층+ 하드코어 보드 전용 -- 행(rows)이 칸 수에 비례해 늘어나 총 경로 길이도 같이
   * 늘어나므로, 원의 둘레 위에 점을 찍는 방식과 달리 칸이 많아져도 칸 사이 간격이 좁아지지
   * 않는다. vertical이면 방향을 90도 돌려서(세로로 왕복) 큰 보드끼리도 층마다 다른 모양이
   * 나오게 한다.
   * [2026-09-14] "한 칸에 줄이 여러 개 연결된 것처럼 보인다" 신고로 09-14 초반엔 닫힘
   * 세그먼트를 아예 없앴었는데, "처음과 끝이 이어져야지"라는 재요청으로 다시 되돌리되
   * 이번엔 방식을 바꿨다 -- 마지막 칸에서 첫 칸으로 곧장 대각선을 긋는 대신(그게 바로
   * "겹쳐 보인다"는 원인이었음, 세로로 긴 트랙일수록 화면을 가로지르는 눈에 띄는 대각선이
   * 됨), 카트 트랙의 마지막 직선 코스처럼 격자 바깥의 빈 여백(margin=12보다 안쪽인
   * edge=4 지점)을 둘러서 돌아온다. 첫 칸은 항상 좌상단(margin,margin)에서 시작하므로,
   * 마지막 칸이 있는 가장자리에 따라 인접한 한 변만 두르거나(짧게), 대각선 반대편
   * 모서리면 두 변을 둘러서(길게) 돌아온다 -- 전체 경로는 여전히 하나의 선으로 이어지고,
   * 격자 칸 영역은 절대 가로지르지 않는다. */
  function buildSerpentinePath(cols, rows, vertical, waves) {
    var margin = 12, span = 100 - margin * 2;
    var pts = [];
    // [2026-09-14 3차 수정] freq를 2~4로 올린 만큼(구불구불 요청) 표본도 24로 늘려서
    // Catmull-Rom이 봉우리/골짜기를 놓치지 않고 매끄럽게 따라가게 한다(16으로는 freq=4일 때
    // 한 주기당 표본이 4개뿐이라 곡선이 각져 보일 수 있음).
    var SAMPLES = 24;
    if (!vertical) {
      var colGap = cols > 1 ? span / (cols - 1) : 0;
      var rowGap = rows > 1 ? span / (rows - 1) : 0;
      for (var r = 0; r < rows; r++) {
        var baseY = margin + rowGap * r;
        for (var s = 0; s <= SAMPLES; s++) {
          var t = s / SAMPLES;
          var colF = (r % 2 === 0) ? t * (cols - 1) : (cols - 1) - t * (cols - 1);
          pts.push([margin + colGap * colF, baseY + waveOffset(waves && waves[r], rowGap, t)]);
        }
      }
    } else {
      var rowGap2 = rows > 1 ? span / (rows - 1) : 0;
      var colGap2 = cols > 1 ? span / (cols - 1) : 0;
      for (var c2 = 0; c2 < cols; c2++) {
        var baseX = margin + colGap2 * c2;
        for (var s2 = 0; s2 <= SAMPLES; s2++) {
          var t2 = s2 / SAMPLES;
          var rowF = (c2 % 2 === 0) ? t2 * (rows - 1) : (rows - 1) - t2 * (rows - 1);
          pts.push([baseX + waveOffset(waves && waves[c2], colGap2, t2), margin + rowGap2 * rowF]);
        }
      }
    }
    var d = catmullRomPath(pts); // [2026-09-14] 직선 대신 부드러운 곡선으로(위 catmullRomPath 참고)

    // 닫힘 구간: 마지막 칸 -> (바깥 여백을 둘러서) -> 첫 칸(margin,margin).
    // 격자 구성상 첫 칸은 항상 좌상단(margin,margin)이고, 마지막 칸은 항상 아래쪽 변
    // (수평 왕복) 또는 오른쪽 변(수직 왕복)의 두 모서리 중 하나(margin 또는 100-margin)에
    // 정확히 위치한다 -- 그래서 "어느 가장자리인지"만 보고 인접/대각 여부를 판정할 수 있다.
    var edge = 4; // margin(12)보다 안쪽 = 칸이 전혀 없는 진짜 빈 공간
    var last = pts[pts.length - 1];
    var lastAtRight = Math.abs(last[0] - (100 - margin)) < 0.01;
    var lastAtBottom = Math.abs(last[1] - (100 - margin)) < 0.01;
    d += ' L' + last[0].toFixed(2) + ',' + (lastAtBottom ? (100 - edge) : edge).toFixed(2);
    if (lastAtRight && lastAtBottom) {
      // 대각선 반대 모서리(오른쪽 아래) -- 아래쪽 변을 먼저 두른 뒤 왼쪽 변을 타고 올라간다.
      d += ' L' + edge.toFixed(2) + ',' + (100 - edge).toFixed(2);
    }
    d += ' L' + edge.toFixed(2) + ',' + edge.toFixed(2);
    d += ' L' + margin.toFixed(2) + ',' + margin.toFixed(2);
    return d;
  }

  /** [2026-09-14] "확대 보드 위쪽에 칸들이 몰려서 겹친다" 신고로 확인된 원인 수정 --
   * buildSerpentinePath가 만드는 경로는 격자 칸 사이 짧은 구간(rowGap/colGap)과 열이
   * 바뀔 때의 긴 구간, 그리고 맨 끝에 시작점으로 돌아가는 대각선(길이가 훨씬 김)이 전부
   * 섞여있어서, getPointAtLength로 "호 길이 기준 균등 샘플링"을 하면 실제 격자 위치와
   * 어긋나 일부 구간(특히 마지막 대각선 근처로 밀리는 앞쪽 칸들)이 뭉쳐 보였다. 칸 위치는
   * buildSerpentinePath와 완전히 같은 규칙으로 격자 인덱스에서 직접 계산해서 이 문제를
   * 원천 차단한다(배경 선은 여전히 buildSerpentinePath로 그림 -- 장식이라 무관). */
  function serpentineGridPoint(idx, cols, rows, vertical, waves) {
    var margin = 12, span = 100 - margin * 2;
    var colGap = cols > 1 ? span / (cols - 1) : 0;
    var rowGap = rows > 1 ? span / (rows - 1) : 0;
    var row, col;
    if (!vertical) {
      row = Math.floor(idx / cols);
      var colInRow = idx % cols;
      col = (row % 2 === 0) ? colInRow : (cols - 1 - colInRow);
      var t = cols > 1 ? colInRow / (cols - 1) : 0;
      return { x: margin + colGap * col, y: margin + rowGap * row + waveOffset(waves && waves[row], rowGap, t) };
    } else {
      col = Math.floor(idx / rows);
      var rowInCol = idx % rows;
      row = (col % 2 === 0) ? rowInCol : (rows - 1 - rowInCol);
      var t2 = rows > 1 ? rowInCol / (rows - 1) : 0;
      return { x: margin + colGap * col + waveOffset(waves && waves[col], colGap, t2), y: margin + rowGap * row };
    }
  }

  // 실제로 sampling(getTotalLength/getPointAtLength)에 쓸 <path>. 일부 브라우저(사파리 계열)는
  // 문서에 붙어있지 않은 path에서 geometry 메서드가 제대로 안 되는 경우가 있어, 화면엔 안
  // 보이지만 문서에는 붙어있는 숨은 SVG 안에 넣어서 재사용한다.
  var trackSvgNS = 'http://www.w3.org/2000/svg';
  var trackSampleSvg = document.createElementNS(trackSvgNS, 'svg');
  trackSampleSvg.setAttribute('viewBox', '0 0 100 100');
  trackSampleSvg.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden;';
  var trackSamplePath = document.createElementNS(trackSvgNS, 'path');
  trackSampleSvg.appendChild(trackSamplePath);
  document.body.appendChild(trackSampleSvg);

  // "보스 이미지를 이름에 맞게 그려달라" 요청 -- 10블록 보스마다 크레용 낙서 느낌의 SVG
  // 일러스트(굵은 검은 선, 흰 배경, 단순한 표정). 블록 번호(1~10)로 찾는다.
  var BOSS_ART = {
    1: { name: '하수구의 지배자 라텔', svg:
      '<path d="M60,120 C55,80 75,45 100,45 C125,45 145,80 140,120" fill="none" stroke="#000" stroke-width="6" stroke-linecap="round"/>'
      + '<path d="M70,60 C60,40 55,15 65,10 C72,25 75,45 78,58" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<path d="M130,60 C140,40 145,15 135,10 C128,25 125,45 122,58" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<ellipse cx="100" cy="150" rx="55" ry="45" fill="#fff" stroke="#000" stroke-width="6"/>'
      + '<circle cx="80" cy="90" r="6"/><circle cx="120" cy="90" r="6"/>'
      + '<path d="M85,110 Q100,118 115,110" fill="none" stroke="#000" stroke-width="4" stroke-linecap="round"/>'
      + '<path d="M88,113 L84,122 M112,113 L116,122" stroke="#000" stroke-width="4" stroke-linecap="round"/>'
      + '<circle cx="65" cy="150" r="18" fill="#fff" stroke="#000" stroke-width="5"/>'
      + '<circle cx="135" cy="150" r="18" fill="#fff" stroke="#000" stroke-width="5"/>' },
    2: { name: '폐광의 검은 갱도왕', svg:
      '<rect x="55" y="55" width="90" height="95" rx="18" fill="#fff" stroke="#000" stroke-width="6"/>'
      + '<path d="M60,55 L100,25 L140,55" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<rect x="90" y="15" width="20" height="18" fill="#fff" stroke="#000" stroke-width="5"/>'
      + '<circle cx="82" cy="95" r="7"/><circle cx="118" cy="95" r="7"/>'
      + '<path d="M78,120 L122,120" stroke="#000" stroke-width="5" stroke-linecap="round"/>'
      + '<rect x="30" y="150" width="22" height="45" rx="8" fill="#fff" stroke="#000" stroke-width="5"/>'
      + '<rect x="148" y="150" width="22" height="45" rx="8" fill="#fff" stroke="#000" stroke-width="5"/>'
      + '<path d="M20,190 L60,150 M150,150 L190,190" stroke="#000" stroke-width="6" stroke-linecap="round"/>' },
    3: { name: '늪지 여왕 히드라', svg:
      '<path d="M100,180 C60,180 40,140 55,110 C65,90 90,90 95,110" fill="#fff" stroke="#000" stroke-width="6" stroke-linecap="round"/>'
      + '<path d="M100,180 C140,180 160,140 145,110 C135,90 110,90 105,110" fill="#fff" stroke="#000" stroke-width="6" stroke-linecap="round"/>'
      + '<path d="M100,180 C100,150 100,120 100,95" fill="#fff" stroke="#000" stroke-width="6" stroke-linecap="round"/>'
      + '<ellipse cx="55" cy="100" rx="20" ry="16" fill="#fff" stroke="#000" stroke-width="5"/>'
      + '<ellipse cx="145" cy="100" rx="20" ry="16" fill="#fff" stroke="#000" stroke-width="5"/>'
      + '<ellipse cx="100" cy="85" rx="20" ry="16" fill="#fff" stroke="#000" stroke-width="5"/>'
      + '<circle cx="50" cy="98" r="4"/><circle cx="60" cy="98" r="4"/>'
      + '<circle cx="140" cy="98" r="4"/><circle cx="150" cy="98" r="4"/>'
      + '<circle cx="95" cy="82" r="4"/><circle cx="105" cy="82" r="4"/>'
      + '<ellipse cx="100" cy="160" rx="55" ry="30" fill="#fff" stroke="#000" stroke-width="6"/>' },
    4: { name: '화산의 심장 이프리트', svg:
      '<path d="M100,20 C90,45 70,55 75,75 C80,60 95,55 100,65 C105,55 120,60 125,75 C130,55 110,45 100,20Z" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<path d="M55,80 C45,60 55,40 65,45 C60,60 65,75 70,85" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<path d="M145,80 C155,60 145,40 135,45 C140,60 135,75 130,85" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<ellipse cx="100" cy="130" rx="58" ry="55" fill="#fff" stroke="#000" stroke-width="6"/>'
      + '<circle cx="80" cy="115" r="6"/><circle cx="120" cy="115" r="6"/>'
      + '<path d="M82,145 Q100,155 118,145" fill="none" stroke="#000" stroke-width="4" stroke-linecap="round"/>'
      + '<path d="M86,148 L82,157 M114,148 L118,157" stroke="#000" stroke-width="4" stroke-linecap="round"/>' },
    5: { name: '빙하의 폭군 프로스트자이언트', svg:
      '<ellipse cx="100" cy="130" rx="65" ry="58" fill="#fff" stroke="#000" stroke-width="6"/>'
      + '<path d="M55,40 L65,75 M70,25 L75,70 M90,15 L90,68 M110,15 L110,68 M130,25 L125,70 M145,40 L135,75" stroke="#000" stroke-width="5" stroke-linecap="round"/>'
      + '<circle cx="78" cy="120" r="7"/><circle cx="122" cy="120" r="7"/>'
      + '<path d="M82,150 Q100,160 118,150" fill="none" stroke="#000" stroke-width="4" stroke-linecap="round"/>'
      + '<path d="M40,150 C25,145 20,120 30,105" fill="none" stroke="#000" stroke-width="6" stroke-linecap="round"/>'
      + '<path d="M160,150 C175,145 180,120 170,105" fill="none" stroke="#000" stroke-width="6" stroke-linecap="round"/>' },
    6: { name: '심연의 대공 모르드레드', svg:
      '<path d="M60,65 C40,35 48,5 68,12 C70,32 75,52 80,66" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<path d="M140,65 C160,35 152,5 132,12 C130,32 125,52 120,66" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<ellipse cx="100" cy="100" rx="45" ry="42" fill="#fff" stroke="#000" stroke-width="6"/>'
      + '<path d="M40,135 L60,120 M160,135 L140,120" stroke="#000" stroke-width="5" stroke-linecap="round"/>'
      + '<path d="M50,135 C30,150 25,175 45,192 L155,192 C175,175 170,150 150,135" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<circle cx="82" cy="95" r="6"/><circle cx="118" cy="95" r="6"/>'
      + '<path d="M85,118 Q100,124 115,118" fill="none" stroke="#000" stroke-width="4" stroke-linecap="round"/>'
      + '<path d="M88,120 L84,130 M112,120 L116,130" stroke="#000" stroke-width="4" stroke-linecap="round"/>' },
    7: { name: '차원의 파괴자 아자토스', svg:
      '<path d="M100,30 C60,30 30,65 35,105 C40,150 65,180 100,180 C135,180 160,150 165,105 C170,65 140,30 100,30Z" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<circle cx="70" cy="80" r="10" fill="#fff" stroke="#000" stroke-width="5"/><circle cx="70" cy="80" r="4"/>'
      + '<circle cx="130" cy="80" r="10" fill="#fff" stroke="#000" stroke-width="5"/><circle cx="130" cy="80" r="4"/>'
      + '<circle cx="55" cy="120" r="8" fill="#fff" stroke="#000" stroke-width="5"/><circle cx="55" cy="120" r="3"/>'
      + '<circle cx="145" cy="120" r="8" fill="#fff" stroke="#000" stroke-width="5"/><circle cx="145" cy="120" r="3"/>'
      + '<circle cx="100" cy="60" r="7" fill="#fff" stroke="#000" stroke-width="5"/><circle cx="100" cy="60" r="3"/>'
      + '<path d="M75,140 Q100,155 125,140" fill="none" stroke="#000" stroke-width="4" stroke-linecap="round"/>' },
    8: { name: '천공의 대천사 세라핌', svg:
      '<circle cx="100" cy="45" r="18" fill="none" stroke="#000" stroke-width="4"/>'
      + '<path d="M30,120 C10,100 15,60 45,55 C55,75 55,100 60,120" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<path d="M170,120 C190,100 185,60 155,55 C145,75 145,100 140,120" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<ellipse cx="100" cy="105" rx="35" ry="38" fill="#fff" stroke="#000" stroke-width="6"/>'
      + '<path d="M65,140 C60,165 70,190 100,190 C130,190 140,165 135,140" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<circle cx="88" cy="98" r="5"/><circle cx="112" cy="98" r="5"/>'
      + '<path d="M90,118 Q100,123 110,118" fill="none" stroke="#000" stroke-width="4" stroke-linecap="round"/>' },
    9: { name: '고룡 바하무트', svg:
      '<path d="M100,15 C85,30 80,45 90,55 C95,45 105,45 110,55 C120,45 115,30 100,15Z" fill="#fff" stroke="#000" stroke-width="5" stroke-linejoin="round"/>'
      + '<path d="M40,90 C15,75 15,45 35,40 C40,60 50,75 65,88" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<path d="M160,90 C185,75 185,45 165,40 C160,60 150,75 135,88" fill="#fff" stroke="#000" stroke-width="6" stroke-linejoin="round"/>'
      + '<ellipse cx="100" cy="110" rx="42" ry="40" fill="#fff" stroke="#000" stroke-width="6"/>'
      + '<path d="M100,148 C95,165 100,185 100,195" stroke="#000" stroke-width="6" stroke-linecap="round" fill="none"/>'
      + '<circle cx="82" cy="102" r="6"/><circle cx="118" cy="102" r="6"/>'
      + '<path d="M78,128 L122,128" stroke="#000" stroke-width="5" stroke-linecap="round"/>'
      + '<path d="M82,131 L78,140 M92,131 L90,141 M108,131 L110,141 M118,131 L122,140" stroke="#000" stroke-width="3" stroke-linecap="round"/>' },
    10: { name: '종말의 마룡왕 니드호그', svg:
      '<path d="M100,190 C70,190 50,170 55,150 C40,145 35,120 50,110 C40,95 50,75 65,78 C60,60 75,45 90,50 C90,35 105,25 120,35 C135,30 150,45 140,60" fill="none" stroke="#000" stroke-width="6" stroke-linecap="round"/>'
      + '<ellipse cx="145" cy="65" rx="30" ry="26" fill="#fff" stroke="#000" stroke-width="6"/>'
      + '<path d="M120,45 C110,30 115,15 125,18 C122,28 125,38 130,48" fill="#fff" stroke="#000" stroke-width="5" stroke-linejoin="round"/>'
      + '<path d="M160,45 C170,30 165,15 155,18 C158,28 155,38 150,48" fill="#fff" stroke="#000" stroke-width="5" stroke-linejoin="round"/>'
      + '<circle cx="135" cy="62" r="5"/><circle cx="158" cy="62" r="5"/>'
      + '<path d="M130,78 Q145,85 165,78" fill="none" stroke="#000" stroke-width="4" stroke-linecap="round"/>'
      + '<path d="M135,80 L131,88 M155,80 L159,88" stroke="#000" stroke-width="3" stroke-linecap="round"/>' }
  };

  /** [2026-09-13 신설, 2026-09-14 미니맵 롤백 후에도 유지] "내 위치" 버튼 -- 칸이 커지고
   * 세로 스크롤이 길어진 뒤에도(overflow-y:auto) 항상 현재 칸(.here)이 뷰포트 세로 중앙
   * 근처에 오도록 스크롤한다. */
  function scrollToHere() {
    var vp = document.getElementById('towerViewport');
    var here = document.getElementById('towerTrack').querySelector('.tile.here');
    if (vp && here) {
      vp.scrollTop = Math.max(0, here.offsetTop - vp.clientHeight / 2 + here.offsetHeight / 2);
    }
  }

  /** [2026-09-14] "마우스휠 스크롤은 막고 대신 버튼을 만들어달라" 요청으로 처음엔 뷰포트
   *  높이의 85%만큼만 내려가는 scrollDown()이었는데, [2026-09-14 3차 수정] "맨아래로/맨위로
   *  버튼을 플로팅시켜달라"는 요청에 맞춰 이름 그대로 맨 끝까지 한 번에 이동하도록 바꿨다.
   *  스크롤 자체(스크롤바 드래그/터치 스와이프)는 여전히 가능, 아래 wheel 리스너로 휠만 막음. */
  function scrollToTop() {
    var vp = document.getElementById('towerViewport');
    if (vp) vp.scrollTo({ top: 0, behavior: 'smooth' });
  }
  function scrollToBottom() {
    var vp = document.getElementById('towerViewport');
    if (vp) vp.scrollTo({ top: vp.scrollHeight, behavior: 'smooth' });
  }

  function renderBoard(tiles, curTile, floor) {
    var track = document.getElementById('towerTrack');
    track.innerHTML = '';
    var vwrap = document.getElementById('towerViewportWrap');
    if (vwrap) vwrap.classList.remove('village-map');
    if (!tiles || !tiles.length) {
      // "보스룸은 지도가 없는데 영역만 있어서 뭔지 모르겠다" 요청 -- 마을/보스층을 뭉뚱그리지
      // 않고, 보스층이면 전용 표시(+보스 일러스트)를 보여준다.
      var fm = floor % 10;
      if (fm === 9) {
        var art = BOSS_ART[Math.floor(floor / 10) + 1];
        track.innerHTML = '<div style="text-align:center;padding:14px 0;color:var(--ink-soft);font-size:13px;">'
            + (art ? '<svg viewBox="0 0 200 200" style="width:110px;height:110px;">' + art.svg + '</svg><br>' : '')
            + '👑 보스룸' + (art ? ' — ' + art.name : '') + '<br>'
            + '<span style="font-size:11px;">지도 없이 곧바로 전투가 진행됩니다. /주사위로 도전하세요!</span></div>';
      } else if (floor % 10 === 0) {
        renderVillageMap(track, floor);
        if (vwrap) vwrap.classList.add('village-map');
      } else {
        track.innerHTML = '<div style="color:var(--ink-soft);font-size:12px;">이 층은 보드가 없습니다</div>';
      }
      return;
    }
    if (isStairZone(floor)) { renderStairBoard(track, tiles, curTile, floor); return; }
    var n = tiles.length;
    // [2026-09-05] 칸 수(최대 150~200)에 맞춰 트랙을 키우던 방식을 버리고 뷰포트를 고정
    // 크기(overflow:hidden)로 두는 대신 칸 크기(cell)를 칸 수에 맞춰 줄이게 했었다 -- 좌표는
    // %(트랙 기준 퍼센트)로 둬서 어떤 칸 수에서도 트랙을 벗어나지 않는다.
    // [2026-09-06] 칸 수가 많은 보드는 실제 뷰포트 픽셀 크기를 재서 격자(cols×rows)로 칸
    // 크기를 역산(칸 사이 간격의 72%만 써서 눈에 보이는 틈을 남김)하게 두 번 손봤었다.
    // [2026-09-13] "칸(200개)이 너무 작아서 글씨가 안 보인다, 세로 스크롤은 괜찮으니 칸을
    // 키우고 안 겹치게 해달라" 요청으로 세로 방향의 "뷰포트 높이에 맞춰 칸을 눌러 압축"하는
    // 부분을 제거했다. 가로(cols)는 그대로 뷰포트 폭 기준으로 정하지만(가로 스크롤은 여전히
    // 없음), 세로 칸 간격(spacingY)은 이제 spacingX와 동일하게(정사각형에 가깝게) 두고,
    // 그만큼 필요한 트랙 전체 높이(px)를 역산해서 .tower-track 자체를 늘린다 -- 뷰포트는
    // overflow-y:auto라 늘어난 만큼 세로 스크롤로 보여준다(카트라이더 트랙 모양의 serpentine
    // 경로는 그대로, 세로로만 길어짐). idealSpacing/칸 크기 상하한도 같이 키웠다.
    var cell, d;
    var gridCols = 0, gridRows = 0, gridVertical = false, gridWaves = null, isLarge = n > LARGE_BOARD_TILE_THRESHOLD;
    if (isLarge) {
      var vw = Math.max(200, track.clientWidth - 8);
      var marginFrac = (100 - 12 * 2) / 100; // buildSerpentinePath의 margin=12와 맞춤(0.76)
      var idealSpacing = 34; // 칸 "중심 간" 목표 거리 -- 예전(26)보다 키워서 칸 자체를 더 크게
      var cols = Math.max(5, Math.round(1 + (marginFrac * vw) / idealSpacing));
      var rows = Math.max(3, Math.ceil(n / cols));
      var spacingX = cols > 1 ? (marginFrac * vw) / (cols - 1) : vw;
      // [2026-09-14 재수정] "곡선이 그다지 안 보인다, 좀 더 있으면 좋겠다, 이미 스크롤
      // 만든 김에 아래로 길게 늘리면 되지 않겠냐"는 요청 -- 세로 칸 간격을 가로보다
      // STRETCH_K배 더 넓게 벌려서(트랙이 그만큼 더 세로로 길어짐, 세로 스크롤은 이미
      // 지원 중이라 문제 없음) 줄 사이 여유를 늘리고, 그만큼 사인파 진폭도 아래에서
      // 동적으로 키운다(같은 "칸이 안 겹친다"는 안전 조건을 유지한 채로).
      // [2026-09-14 재재수정] "곡선 기울기를 좀 더 심하게 해달라" 요청으로 1.4 -> 1.9.
      // ampMin/ampMax는 STRETCH_K에서 자동으로 비례 계산되므로(아래 ampScale 참고) 기존과
      // 같은 상대 안전 마진(이론상 한계의 약 93%)을 그대로 유지한 채 더 커진다.
      var STRETCH_K = 1.9;
      var spacingY = spacingX * STRETCH_K;
      cell = Math.max(20, Math.min(36, spacingX * 0.72));
      var trackHeightPx = rows > 1 ? Math.round((spacingY * (rows - 1)) / marginFrac) : track.clientHeight;
      track.style.height = trackHeightPx + 'px';
      // [2026-09-14 버그 수정] seededRandom()은 선형합동법이라 작은 시드(그냥 floor값,
      // 51~100 정도)를 그대로 넣으면 "seed*16807"이 모듈러 나머지 연산에 아직 한 번도
      // 안 걸려서(모듈러보다 한참 작음) 첫 뽑기 값이 사실상 floor에 선형 비례하는 작은
      // 값(대부분 0.5 미만)으로 편향된다 -- 실측 결과 51~100층 전부 예외 없이 가로 왕복만
      // 나왔음(세로 왕복이 사실상 죽어있던 버그). buildLoopPath가 이미 쓰던 것과 같은
      // "floor*큰소수+오프셋"으로 시드를 충분히 흩뜨려서 해결(다른 소수를 써서 buildLoopPath
      // 와 시퀀스가 겹치지 않게 함).
      // [2026-09-14 재수정] 가로 왕복(줄=행, gridVertical=false)은 방금 늘린 spacingY
      // 덕에 곡선을 크게 키울 수 있지만, 세로 왕복(줄=열)은 가로 스크롤이 없어 spacingX를
      // 못 늘리므로 예전처럼 좁은 진폭에 머문다 -- 그래서 더 화려한 가로 왕복 쪽이 자주
      // 나오게 확률을 30%로 낮췄다(예전엔 50%).
      var rndOrient = seededRandom((floor || 0) * 7919 + 31543);
      gridVertical = rndOrient() < 0.3;
      gridCols = cols; gridRows = rows;
      // [2026-09-14] 파동이 실제로 걸리는 축의 실측 간격(px)을 기준으로 안전한 진폭 상한을
      // 역산한다 -- 칸 반지름은 항상 spacingX 기준(cell=spacingX*0.72 -> 반지름=0.36*spacingX)
      // 이고, 인접한 두 줄이 최악의 경우(반대 위상 최대 진폭)로 어긋나도 겹치지 않으려면
      // ampFracMax <= 0.5 - 반지름/줄간격 이어야 한다(증명은 buildRowWaves 주석 참고). 이
      // 값을 "이전에 검증된 7~13%(줄간격=spacingX일 때 기준)" 대비 배율로 환산해서
      // 그대로 스케일업하면, 줄간격이 넓어진 만큼(가로 왕복일 땐 spacingY=spacingX*1.4)
      // 자동으로 더 큰 진폭이 허용되고, 못 늘리는 세로 왕복은 배율이 1.0 그대로 유지된다.
      var bandGapPx = gridVertical ? spacingX : spacingY;
      var radiusFrac = (cell / 2) / bandGapPx;
      var ampScale = (0.5 - radiusFrac) / (0.5 - 0.36); // 0.36 = 기존 spacingX 기준 반지름 비율
      var ampMin = 0.07 * ampScale, ampMax = 0.13 * ampScale;
      // 층마다(그리고 같은 층 안에서도 줄마다) 다른 곡선이 나오도록, 방향을 고른 바로 그
      // 시드 시퀀스를 이어서 줄 개수(가로 왕복이면 rows개, 세로 왕복이면 cols개)만큼
      // 파형을 뽑는다 -- buildRowWaves/waveOffset 참고.
      gridWaves = buildRowWaves(rndOrient, gridVertical ? cols : rows, ampMin, ampMax);
      d = buildSerpentinePath(cols, rows, gridVertical, gridWaves);
    } else {
      cell = Math.max(16, Math.min(40, 900 / n));
      d = buildLoopPath(floor, n);
      track.style.height = ''; // 작은 보드는 예전처럼 뷰포트에 꽉 채움(CSS height:100%로 복귀)
    }

    // 트랙 모양(0~100 정규화 좌표계, 항상 닫힌 루프)의 <path> 하나를 길이 측정용으로 세팅
    trackSamplePath.setAttribute('d', d);
    var normLen = trackSamplePath.getTotalLength();

    // 트랙 라인을 배경으로 깔아서 "길"처럼 보이게(카트라이더 느낌) -- 칸 아이콘들보다 아래(z-index).
    // preserveAspectRatio="none"으로 0~100 좌표계를 뷰포트 실제 가로/세로 비율에 맞게 그대로
    // 늘려서, 아래 칸 아이콘의 %(pt.x/pt.y) 위치와 정확히 겹치게 한다.
    var svg = document.createElementNS(trackSvgNS, 'svg');
    svg.setAttribute('viewBox', '0 0 100 100');
    svg.setAttribute('preserveAspectRatio', 'none');
    svg.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;';
    var lineEl = document.createElementNS(trackSvgNS, 'path');
    lineEl.setAttribute('d', d);
    lineEl.setAttribute('fill', 'none');
    lineEl.setAttribute('stroke', 'var(--line)');
    lineEl.setAttribute('stroke-width', '3');
    lineEl.setAttribute('vector-effect', 'non-scaling-stroke');
    svg.appendChild(lineEl);
    track.appendChild(svg);

    // [2026-09-06] "안에 글씨가 보였으면 좋겠다" 요청 -- 기존엔 cell/4.5라 칸이 커져도
    // 글씨가 거의 안 자랐다(칸 30px에서도 7px 근처). 본문 글씨는 cell/3로, 칸번호(.tno)도
    // CSS 고정 7px 대신 칸 크기에 비례하도록 인라인으로 덮어쓴다.
    var fontSize = Math.max(8, Math.min(13, Math.round(cell / 3)));
    var tnoSize = Math.max(6, Math.round(cell / 5));
    tiles.forEach(function (t, idx) {
      // [2026-09-14] 큰 보드(격자)는 getPointAtLength 대신 격자 인덱스로 직접 위치 계산
      // (serpentineGridPoint 주석 참고) -- 작은 보드(닫힌 루프, buildLoopPath)는 기존처럼
      // 호 길이 균등 샘플링 그대로 사용(문제 없었음).
      var pt = isLarge ? serpentineGridPoint(idx, gridCols, gridRows, gridVertical, gridWaves)
                       : trackSamplePath.getPointAtLength((normLen * idx) / n);
      var div = document.createElement('div');
      var isHere = (t.TILE_NO === curTile);
      div.style.left = 'calc(' + pt.x + '% - ' + (cell / 2) + 'px)';
      div.style.top = 'calc(' + pt.y + '% - ' + (cell / 2) + 'px)';
      div.style.width = cell + 'px';
      div.style.height = cell + 'px';
      div.style.fontSize = fontSize + 'px';
      var tnoHtml = '<span class="tno" style="font-size:' + tnoSize + 'px">' + t.TILE_NO + '</span>';
      if (t.DISCOVERED) {
        div.className = 'tile ' + tileClass(t.TILE_TYPE) + (isHere ? ' here' : ' done');
        div.innerHTML = tnoHtml + (TILE_KR[t.TILE_TYPE] || t.TILE_TYPE);
      } else {
        // 방문한 적 없는 칸은 종류를 감추고 물음표만 표시(fog of war)
        div.className = 'tile hidden' + (isHere ? ' here' : '');
        div.innerHTML = tnoHtml + '?';
      }
      track.appendChild(div);
    });
    // [2026-09-14 3차 수정] 맨 위로/내 위치/맨 아래로 버튼은 이제 .tower-viewport-wrap에
    // 정적 마크업으로 한 번만 있고(HTML 참고) track.innerHTML=''로도 안 지워지므로, 여기서
    // 매 렌더마다 다시 그려 넣던 코드는 삭제(위 CSS .board-track-controls 주석 참고).

    // [2026-09-21] "pc,모바일 둘다 내위치근처에서부터 보여주면좋을거같아(지금은 맵상단부터
    // 보여주고 시작하고있어)" 요청 -- 이 층 보드를 처음 보여줄 때(층이 바뀌었을 때)만
    // scrollToHere()를 자동 호출한다. 상태조회는 몇 초마다 계속 도는데 그때마다 부르면
    // 유저가 다른 곳을 보고 있어도 계속 내 위치로 스크롤이 튕겨서, "층이 바뀐 시점"으로만
    // 한정(같은 층에서의 반복 갱신은 스크롤 위치를 건드리지 않음).
    if (lastAutoScrollFloor !== floor) {
      lastAutoScrollFloor = floor;
      scrollToHere();
    }
  }
  var lastAutoScrollFloor = null;

  /** [2026-09-28] "맵은 계단식으로 해줘" 요청 -- 101층+ 계단 구역 9칸을 왼쪽 아래(1번 계단)에서
   *  오른쪽 위(9번 중간보스)로 한 칸씩 올라가는 계단 모양으로 배치한다. 칸 사이 선도 수평(디딤판)
   *  -> 수직(챌판)으로 꺾어서 실제 계단처럼 보이게 하고, 이미 지나온 칸은 흐리게(.done), 앞으로
   *  밟을 칸은 또렷하게 둬서 "다음에 뭐가 나오는지" 한눈에 보이게 한다. 좌표는 트랙 기준 %라
   *  PC/모바일 폭과 무관하게 같은 모양이 나온다. 이동은 서버에서 항상 1칸씩이다. */
  function renderStairBoard(track, tiles, curTile, floor) {
    track.style.height = '';
    var n = tiles.length;
    var x0 = 10, x1 = 90, y0 = 86, y1 = 16; // 1번 칸(좌하) ~ 마지막 칸(우상), 트랙 기준 %
    var pts = [];
    for (var i = 0; i < n; i++) {
      var t = n > 1 ? i / (n - 1) : 0;
      pts.push({ x: x0 + (x1 - x0) * t, y: y0 + (y1 - y0) * t });
    }
    var d = 'M' + pts[0].x + ',' + pts[0].y;
    for (var k = 1; k < n; k++) {
      d += ' L' + pts[k].x + ',' + pts[k - 1].y + ' L' + pts[k].x + ',' + pts[k].y;
    }
    var svg = document.createElementNS(trackSvgNS, 'svg');
    svg.setAttribute('viewBox', '0 0 100 100');
    svg.setAttribute('preserveAspectRatio', 'none');
    svg.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;';
    var lineEl = document.createElementNS(trackSvgNS, 'path');
    lineEl.setAttribute('d', d);
    lineEl.setAttribute('fill', 'none');
    lineEl.setAttribute('stroke', 'var(--line)');
    lineEl.setAttribute('stroke-width', '3');
    lineEl.setAttribute('vector-effect', 'non-scaling-stroke');
    svg.appendChild(lineEl);
    track.appendChild(svg);

    var vw = Math.max(200, track.clientWidth);
    var cell = Math.max(30, Math.min(56, Math.round(vw / (n + 2))));
    var fontSize = Math.max(9, Math.min(13, Math.round(cell / 3.2)));
    var tnoSize = Math.max(7, Math.round(cell / 5));
    tiles.forEach(function (tile, idx) {
      var pt = pts[idx];
      var div = document.createElement('div');
      var isHere = (tile.TILE_NO === curTile);
      var isPassed = tile.TILE_NO < curTile;
      div.style.left = 'calc(' + pt.x + '% - ' + (cell / 2) + 'px)';
      div.style.top = 'calc(' + pt.y + '% - ' + (cell / 2) + 'px)';
      div.style.width = cell + 'px';
      div.style.height = cell + 'px';
      div.style.fontSize = fontSize + 'px';
      div.className = 'tile ' + tileClass(tile.TILE_TYPE) + (isHere ? ' here' : (isPassed ? ' done' : ''));
      div.innerHTML = '<span class="tno" style="font-size:' + tnoSize + 'px">' + tile.TILE_NO + '</span>'
          + (TILE_KR[tile.TILE_TYPE] || tile.TILE_TYPE || '?');
      track.appendChild(div);
    });
    var label = document.createElement('div');
    label.style.cssText = 'position:absolute;right:8px;top:4px;font-size:11px;color:var(--ink-soft);';
    label.textContent = '⬆️ ' + (floor + 1) + '층으로';
    track.appendChild(label);
    if (lastAutoScrollFloor !== floor) {
      lastAutoScrollFloor = floor;
      scrollToHere();
    }
  }

  // [2026-09-05] 주사위 교체 오버레이 -- 상점탭에 있던 걸 보드 칸그리드 위로 옮겨서, 해금된
  // 등급끼리는 버튼 한 번으로(자동구매형태, 별도 확인창 없음) 바로 교체하고 굴릴 수 있게 함.
  // 해금 조건(unlockFloor)은 서버 로직 그대로, 여기선 표시/클릭만 담당.
  function renderDiceOverlay(dice) {
    var box = document.getElementById('diceOverlay');
    box.innerHTML = '';
    // [2026-09-09] "최대주사위(DICE_6/8/12...)와 최소치보정주사위(강화/마이너스)가 구분이
    // 안 된다" 요청 -- 이 줄은 "최대치(주사위 등급)"를 고르는 줄이라는 걸 맨 앞 라벨로 명시.
    // 아래 dice-enhance-overlay 줄의 "🎲최소" 라벨과 짝을 이룬다.
    var maxLabel = document.createElement('span');
    maxLabel.className = 'dice-enhance-label';
    maxLabel.textContent = '🎲최대';
    box.appendChild(maxLabel);
    dice.forEach(function (d) {
      var btn = document.createElement('button');
      var label = d.name.replace('DICE_', '');
      btn.textContent = '🎲' + label;
      if (d.current) {
        btn.className = 'current';
        btn.title = '사용중';
      } else if (!d.unlocked) {
        btn.className = 'locked';
        btn.title = d.unlockFloor + '층부터 해금';
        // [2026-09-06] "미해금 주사위 눌렀을 때 언제 풀리는지 보여달라" 요청 -- title(호버)은
        // 모바일에서 잘 안 보이므로 탭해도 토스트로 뜨게 함.
        btn.onclick = function () { toast('🔒 ' + d.unlockFloor + '층부터 해금됩니다'); };
      } else {
        btn.title = '눌러서 바로 교체';
        btn.onclick = function () { TW.action('DICE_BUY', String(d.idx)); };
      }
      box.appendChild(btn);
    });
  }

  // [2026-09-08] 주사위 강화(+)/마이너스 주사위(-) 스트립 -- dice-overlay 바로 아래.
  // 0(고정 기준점, 초기값)을 가운데 두고 오른쪽에 강화(+1..+6), 왼쪽에 마이너스(-1)를
  // 나열. "다음 한 단계"만 구매 가능(순차 구매, 건너뛰기 불가) -- 그 이상은 흐리게
  // 표시하고 눌러도 해금층 안내 토스트만 띄운다(diceListInfo의 locked 버튼과 동일한 UX).
  // [2026-09-09 재설계] "최대주사위 줄처럼 딱 1개만 선택되게 해달라(토글 아님), 0은 구매
  // 없이도 항상 누를 수 있어야 한다" 요청 -- 서버가 내려주는 단일 값 de.adjust(현재 실제
  // 적용 중인 값, -1..+6)를 유일한 기준으로 "선택됨" 표시를 하고, 이미 구매(owned)해뒀지만
  // 지금 선택은 아닌 단계 + 0(항상 무료)은 눌러서 DICE_MIN_SELECT로 즉시 무료 전환한다
  // (신규 구매가 아니라 "이미 산 것들 중 뭘 켤지" 전환).
  // [2026-09-21] "이동한도도 맵이동하는곳에 표기하면 좋을거같아" 요청 -- 오늘 이동
  // 사용량/한도를 dice-controls 상단에 작은 줄로 표시. 웹 한도 다 쓰면 카톡보너스 포함 총
  // 한도까지 안내(카톡 없이 웹만 쓰는 유저 입장에선 "얼마 안 남았다"는 것만 알면 충분).
  function renderMoveLimit(ml) {
    var el = document.getElementById('moveLimitBadge');
    if (!el) return;
    if (!ml || ml.webLimit == null) { el.style.display = 'none'; return; }
    el.style.display = '';
    var used = ml.used || 0;
    var full = used >= ml.webLimit;
    el.textContent = '🚶 오늘 이동 ' + used + '/' + ml.webLimit + (full && ml.totalLimit > ml.webLimit ? ' (카톡보너스 포함 ' + ml.totalLimit + ')' : '');
    el.classList.toggle('move-limit-full', full);
  }

  function renderDiceEnhanceRow(de) {
    var box = document.getElementById('diceEnhanceOverlay');
    box.innerHTML = '';
    if (!de) return;
    var adjust = de.adjust || 0;
    var label = document.createElement('span');
    label.className = 'dice-enhance-label';
    label.textContent = '🎲최소';
    box.appendChild(label);
    var malusTiers = de.malusTiers || [];
    var bonusTiers = de.bonusTiers || [];
    for (var i = malusTiers.length - 1; i >= 0; i--) {
      box.appendChild(buildDiceEnhancePip(-(i + 1), malusTiers[i], false, adjust));
    }
    var zero = document.createElement('span');
    var atZero = (adjust === 0);
    zero.className = 'dice-enhance-zero' + (atZero ? ' current' : '');
    zero.textContent = '+0';
    if (atZero) {
      zero.title = '✅ 현재 적용 중 (기본값, 최소 눈금 조정 없음)';
    } else {
      zero.title = '눌러서 기본값(+0)으로 전환 (항상 무료)';
      zero.style.cursor = 'pointer';
      zero.onclick = function () { TW.action('DICE_MIN_SELECT', '0'); };
    }
    box.appendChild(zero);
    for (var j = 0; j < bonusTiers.length; j++) {
      box.appendChild(buildDiceEnhancePip(j + 1, bonusTiers[j], true, adjust));
    }
  }

  function buildDiceEnhancePip(labelNum, tier, isBonus, adjust) {
    var btn = document.createElement('button');
    btn.textContent = (labelNum > 0 ? '+' : '') + labelNum;
    var dir = isBonus ? 'plus' : 'minus';
    var isSelected = tier.owned && labelNum === adjust;
    if (tier.owned) {
      btn.className = 'dice-enhance-owned ' + dir + (isSelected ? ' current' : '');
      if (isSelected) {
        btn.title = '✅ 현재 적용 중 -- 주사위 최소 눈금 ' + (labelNum > 0 ? '+' : '') + labelNum;
      } else {
        // 이미 구매해뒀지만 지금 선택은 아닌 단계 -- 눌러서 무료로 전환.
        btn.title = '눌러서 이 단계로 전환 (구매완료, 무료)';
        btn.onclick = function () { TW.action('DICE_MIN_SELECT', String(labelNum)); };
      }
    } else if (tier.buyable) {
      btn.className = 'dice-enhance-buyable ' + dir;
      btn.title = '눌러서 구매 (' + tier.cost + ' PP, 최소 눈금 ' + (labelNum > 0 ? '+' : '') + labelNum + '로 변경)';
      btn.onclick = function () { TW.action(isBonus ? 'DICE_BONUS_BUY' : 'DICE_MALUS_BUY', ''); };
    } else {
      btn.className = 'dice-enhance-locked';
      btn.title = tier.unlockFloor + '층 마을 도착 후 구매 가능';
      btn.onclick = function () { toast('🔒 ' + tier.unlockFloor + '층 마을 도착 후 구매 가능'); };
    }
    return btn;
  }

  // [2026-09-15] 악세서리(목걸이/반지/팔찌) 신설 -- 장비전체보기 필터/상세카드 장비요약/
  // 목록정렬이 전부 PART_ORDER 하나를 공유해서 여기 3개만 추가하면 자동 반영된다.
  var PART_KR   = { HELMET: '투구', WEAPON: '무기', ARMOR: '갑옷', NECKLACE: '목걸이', RING: '반지', BRACELET: '팔찌' };
  var PART_EMOJI = { HELMET: '⛑️', WEAPON: '⚔️', ARMOR: '🛡️', NECKLACE: '📿', RING: '💍', BRACELET: '⛓️' };
  var PART_ORDER = ['WEAPON', 'HELMET', 'ARMOR', 'NECKLACE', 'RING', 'BRACELET']; // 장비 부위 표시 순서
  // [2026-10-20] 성장형 동료(GRADE 8)는 ☆1로 표기(다른 동료는 ★등급)
  function gradeMark(g) { return g === 8 ? '☆1' : '★' + g; }
  var JOB_KR    = { WARRIOR: '전사', MAGE: '마법사', ROGUE: '도적', ARCHER: '궁수', PRIEST: '도사', SHAMAN: '무녀' };
  var JOB_ORDER = ['WARRIOR', 'MAGE', 'ROGUE', 'ARCHER', 'PRIEST', 'SHAMAN']; // 장비 목록 직업별 그룹핑 순서
  // 초상화(IMAGE_URL)는 외부 API(nekos.best) 실패/차단 시 비어있을 수 있어 직업별 이모지로 항상 얼굴이 보이게 폴백
  var JOB_EMOJI = { WARRIOR: '⚔️', MAGE: '🧙', ROGUE: '🗡️', ARCHER: '🏹', PRIEST: '💫', SHAMAN: '🔮' };

  // [2026-09-17] 장비 그룹 통합(BotS5ServiceImpl의 WEAPON_CLASS_JOBS/JOB_TO_WEAPON_CLASS/
  // JOB_TO_ARMOR_CLASS/JOB_TO_HELMET_CLASS/WEAPON_CLASS_NAME/weaponAllowedJobs/
  // equipClassLabel과 반드시 동일하게 유지) -- 무기(검/지팡이/활), 갑옷(갑주/로브/재킷),
  // 투구(투구/머리띠/깃장식) 각 3종(전사+도적/마법사+도사/궁수), 악세서리(목걸이/반지/팔찌)는
  // 전 직업 공용(COMMON). 마이그레이션 전 구 데이터(CLASS가 아직 직업명 그대로인 경우)도
  // 함께 지원해야 해서 weaponAllowedJobs는 신규값이 아니면 그 값 자신 하나짜리 집합으로
  // 취급한다(서버 로직과 동일).
  var WEAPON_CLASS_JOBS = {
    SWORD: ['WARRIOR', 'ROGUE'], STAFF: ['MAGE', 'PRIEST', 'SHAMAN'], BOW: ['ARCHER'],
    PLATE: ['WARRIOR', 'ROGUE'], ROBE: ['MAGE', 'PRIEST', 'SHAMAN'], JACKET: ['ARCHER'],
    HELM: ['WARRIOR', 'ROGUE'], HEADBAND: ['MAGE', 'PRIEST', 'SHAMAN'], PLUME: ['ARCHER'],
    COMMON: JOB_ORDER
  };
  var WEAPON_CLASS_NAME = {
    SWORD: '검', STAFF: '지팡이', BOW: '활',
    PLATE: '갑주', ROBE: '로브', JACKET: '재킷',
    HELM: '투구', HEADBAND: '머리띠', PLUME: '깃장식'
    // COMMON은 부위마다 라벨이 다르므로("공용목걸이" 등) equipClassLabel에서 따로 처리.
  };
  function weaponAllowedJobs(equipClass) {
    return WEAPON_CLASS_JOBS[equipClass] || [equipClass];
  }
  function equipClassLabel(equipClass, part) {
    if (equipClass === 'COMMON') return '공용' + (PART_KR[part] || part);
    if (WEAPON_CLASS_NAME[equipClass]) return WEAPON_CLASS_NAME[equipClass];
    return JOB_KR[equipClass] || equipClass;
  }
  // [2026-09-21] "★7송곳을 만들었는데 검으로만 나와" 신고 -- selectUserEquip이
  // LEGENDARY_ID를 아예 안 내려주고 있어서(버그 수정, BotS5Mapper.xml) 프론트가 부위 기반
  // 일반 이름(검/지팡이 등)만 표시할 수밖에 없었다. 이제 LEGENDARY_ITEM_NAME이 내려오므로
  // ★7 장비는 이 고유이름을 우선 표시한다.
  function legendaryTag(e) {
    return e && e.LEGENDARY_ITEM_NAME ? (' ✨' + e.LEGENDARY_ITEM_NAME + (e.ENHANCE_LEVEL > 0 ? ' +' + e.ENHANCE_LEVEL : '')) : '';
  }
  // [2026-09-17] "무기 아이콘을 지팡이는 지팡이로, 활은 활로, 갑옷도 적당한 거 있으면"
  // 요청 -- 지금까진 PART 하나로만 아이콘을 정해서(무기는 전부 ⚔️) 검/지팡이/활이 다
  // 똑같은 검 아이콘으로 보였다. 그룹(검/지팡이/활/갑주/로브/재킷)별로 실제 어울리는
  // 이모지를 따로 두고, 매핑에 없으면(투구/악세서리, 또는 마이그레이션 전 구 데이터)
  // 기존 PART_EMOJI로 그대로 폴백한다.
  // [2026-10-22] "투구 종류가 여러 개인데 이미지가 하나" 요청 -- 투구/머리띠/깃장식을 각각 다른 아이콘으로.
  var GROUP_EMOJI = { SWORD: '⚔️', STAFF: '🪄', BOW: '🏹', PLATE: '🛡️', ROBE: '👘', JACKET: '🧥', HELM: '🪖', HEADBAND: '🎀', PLUME: '🪶' };
  function equipIcon(equipClass, part) {
    return GROUP_EMOJI[equipClass] || PART_EMOJI[part] || '🎽';
  }
  // [2026-09-17] "갑주(전사&도적용) 이런식으로 타직업들도 표기해줘" 요청 -- 그룹 헤더에
  // "이 그룹을 누가 쓸 수 있는지"를 직업명으로 풀어서 보여준다. WEAPON_CLASS_JOBS를 그대로
  // 재사용해서 서버 쪽 착용 판정과 항상 같은 직업 목록을 보여주도록(따로 하드코딩 안 함).
  // COMMON(악세서리 공용)은 5직업 전부라 나열하면 너무 길어지고 "공용"이란 라벨 자체가
  // 이미 뜻을 담고 있어서 접미사를 안 붙인다.
  function equipGroupJobSuffix(equipClass) {
    var jobs = WEAPON_CLASS_JOBS[equipClass];
    if (!jobs || equipClass === 'COMMON') return '';
    return '(' + jobs.map(function (j) { return JOB_KR[j] || j; }).join('&') + '용)';
  }

  // [2026-09-06] 동료 초상화 엘리먼트 -- party-card/party-slot-card 헤더 공용.
  // 이미지 로드 실패(외부 API 차단 등) 시 직업 이모지로 폴백하는 로직을 한 곳에 모음.
  // clickable이면 탭 시 상세(showCompanionDetail) 카드가 뜬다(그 외 클릭은 부모에게 위임).
  // [2026-09-14] "핸드폰으로 접속시 이미지가 잘 노출 안된다" 신고 조사 -- 실브라우저(모바일
  // 뷰포트/UA 에뮬레이션)로 직접 확인한 결과 이미지 자체는 로드는 되지만(65/65 성공), 원본
  // nekos.best 초상화 하나가 실측 1.7~3.2MB(가로 1000~2900px)인데 48~64px 썸네일로 그냥
  // 줄여서만 쓰고 있었다 -- 동료 60마리 전체보기 한 번에 열면 합쳐서 100MB+를 한꺼번에
  // 받으려 시도하는 셈이라, 데스크톱/이 테스트 환경(빠른 연결)에선 버티지만 실제 모바일
  // 데이터망(느리고 불안정, 화면 밖 이미지까지 전부 즉시 요청)에서는 타임아웃/메모리
  // 압박으로 일부가 못 뜨는 게 유력한 원인으로 보인다. 1차로 브라우저 네이티브
  // 지연로딩(loading="lazy")을 켜서 화면에 실제로 보이는 이미지만 요청하게 했고, [2026-09-14
  // 근본 대응] "캐싱해줘" 요청으로 원본 URL을 직접 쓰는 대신 서버가 160x160 JPEG로
  // 축소/캐싱해둔 걸 내려주는 /api/tower-avatar 프록시를 거치게 바꿨다(BotS5ServiceImpl.
  // getCompanionAvatarThumbnail 참고) -- 원본 대비 수십~수백 배 작은 파일이라 모바일에서도
  // 훨씬 안정적으로 뜨고, 브라우저 캐시도 7일 걸려있어 재접속 시 재다운로드도 없다.
  // [2026-09-17 버그 수정] "은용 유저가 세라 IMAGE_URL을 DB로 직접 바꿨는데 동료편성
  // 화면(원본 IMAGE_URL을 그대로 쓰는 상세카드)에는 바로 반영되는데 다른 화면(이 프록시를
  // 쓰는 카드들)은 옛날 이미지가 계속 나온다" 신고 -- 원인은 이 프록시 응답에 걸어둔
  // 7일 브라우저 캐시(Season5ViewController.apiTowerAvatar)가 companionId만으로 캐시
  // 키를 삼아서, 서버 쪽 IMAGE_URL이 바뀌어도 브라우저는 예전에 캐시해둔 companionId=N
  // 응답을 계속 재사용했기 때문(서버 자체 썸네일 캐시는 URL별로 따로 관리돼서 문제 없었음
  // -- 순수 브라우저 캐시 문제). c.IMAGE_URL을 캐시버스터로 같이 붙여서, IMAGE_URL이 바뀌면
  // 요청 URL 자체가 달라져 브라우저가 자동으로 새로 받아오게 한다(서버는 이 파라미터를 아예
  // 안 읽으므로 별도 서버 수정 불필요).
  function buildAvatarEl(c, sizeClass, clickable) {
    var img = c.IMAGE_URL ? (base + '/api/tower-avatar?companionId=' + c.COMPANION_ID + '&u=' + encodeURIComponent(c.IMAGE_URL)) : '';
    var emoji = c.GRADE === 8 ? '🌱' : (JOB_EMOJI[c.CLASS] || '👤');
    var onClick = clickable ? function (ev) { ev.stopPropagation(); showCompanionDetail(c.COMPANION_ID); } : null;
    var el = document.createElement(img ? 'img' : 'div');
    el.className = sizeClass + (img ? '' : ' avatar-emoji');
    if (onClick) el.onclick = onClick;
    if (img) {
      el.loading = 'lazy';
      el.decoding = 'async';
      el.src = img;
      el.alt = '';
      el.onerror = function () {
        var fallback = document.createElement('div');
        fallback.className = sizeClass + ' avatar-emoji';
        fallback.textContent = emoji;
        if (onClick) fallback.onclick = onClick;
        el.replaceWith(fallback);
      };
    } else {
      el.textContent = emoji;
    }
    return el;
  }

  // [2026-09-06] 필터 칩 행 3종(직업/성급/장비부위) -- 보유동료 목록, 미착용 장비 목록,
  // 선택 팝업의 동료 후보 목록에서 공용으로 쓴다. current가 null이면 "전체" 칩이 켜진다.
  function buildJobFilterRow(current, onSelect) {
    var row = document.createElement('div');
    row.className = 'filter-row';
    [null].concat(JOB_ORDER).forEach(function (job) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'filter-chip' + (current === job ? ' on' : '');
      btn.textContent = job ? ((JOB_EMOJI[job] || '') + ' ' + (JOB_KR[job] || job)) : '전체';
      btn.onclick = function () { onSelect(job); };
      row.appendChild(btn);
    });
    return row;
  }

  function buildGradeFilterRow(current, onSelect) {
    var row = document.createElement('div');
    row.className = 'filter-row';
    // [2026-09-07] "필터도 성급 desc로 정렬순서 바꿔줘" 요청 -- 전체 다음 ★6부터 내림차순.
    [null, 6, 5, 4, 3, 2, 1, 8].forEach(function (g) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'filter-chip' + (current === g ? ' on' : '');
      btn.textContent = g ? gradeMark(g) : '전체';
      btn.onclick = function () { onSelect(g); };
      row.appendChild(btn);
    });
    return row;
  }

  function buildPartFilterRow(current, onSelect) {
    var row = document.createElement('div');
    row.className = 'filter-row';
    [null].concat(PART_ORDER).forEach(function (part) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'filter-chip' + (current === part ? ' on' : '');
      btn.textContent = part ? ((PART_EMOJI[part] || '') + ' ' + PART_KR[part]) : '전체';
      btn.onclick = function () { onSelect(part); };
      row.appendChild(btn);
    });
    return row;
  }

  // [2026-09-06] "파티 카드 자체에 동료변경/해제, 부위별 장비변경 버튼을 직관적으로" 요청으로
  // 슬롯 관리 UI를 재편 -- 파티 카드(renderPartySlots)는 이제 항상 펼쳐진 상태로 모든 정보를
  // 보여주고, 이 팝업은 그 카드의 부위별 "OO변경" 버튼에서 "무엇으로 바꿀지" 딱 한 번만
  // 고르는 용도로 좁혔다(고르면 바로 닫힘). [2026-09-07] 동료 배치/변경은 "전체동료보기와
  // 같은 창을 띄워달라" 요청으로 이 팝업에서 빠지고 openCompanionPicker(아래, allCompanions
  // Overlay 재사용)로 옮겨갔다 -- 이제 이 팝업은 장비 부위 선택 전용.
  var pickerState = null;

  function openEquipPicker(slot, part) {
    pickerState = { slot: slot, part: part };
    document.getElementById('pickerOverlay').classList.add('open');
    renderPicker();
  }

  function closePicker() {
    pickerState = null;
    document.getElementById('pickerOverlay').classList.remove('open');
  }

  // 팝업 내용 렌더링 -- 고르면 즉시 액션을 쏘고(action) 팝업을 닫는다. 카드 자체가 항상
  // 펼쳐져 있어 "고른 뒤 계속 이 화면에 머무를" 필요가 없어졌다(예전 슬롯 시트와 다른 점).
  function renderPicker() {
    var body = document.getElementById('pickerBody');
    body.innerHTML = '';
    if (!pickerState) return;
    var companions = lastParty.companions || [];
    var byCompanion = lastParty.byCompanion || {};
    var unequipped = lastParty.unequipped || [];

    // 부위+그 동료 직업에 맞는 장비만 고를 수 있다(요청대로 서로 다른 직업 장비는 애초에
    // 후보에 나오지 않음, equipWear의 직업 제약과 동일 조건). [2026-09-17] 무기 통합 후엔
    // e.CLASS가 SWORD 등 무기군일 수 있어 단순 동등비교 대신 weaponAllowedJobs로 판정한다.
    var occupant = companions.filter(function (c) { return c.PARTY_SLOT === pickerState.slot; })[0];
    if (!occupant) { closePicker(); return; }
    var part = pickerState.part;
    var title2 = document.createElement('div');
    title2.className = 'sheet-title';
    title2.textContent = (occupant.NAME || JOB_KR[occupant.CLASS]) + ' · ' + PART_KR[part];
    body.appendChild(title2);

    var mine = byCompanion[occupant.COMPANION_ID] || [];
    var cur = mine.filter(function (e) { return e.PART === part; })[0];
    if (cur) {
      var curRow = document.createElement('div');
      curRow.className = 'sheet-row current readonly';
      curRow.className += ' has-stat';
      curRow.innerHTML = '<span class="sr-main">' + equipIcon(cur.CLASS, part) + ' ★' + cur.GRADE + legendaryTag(cur) + ' 장착중</span>'
          + '<div class="sr-stat" data-eq="' + cur.EQUIP_ID + '"></div>';
      var unwearBtn = document.createElement('button');
      unwearBtn.type = 'button';
      unwearBtn.className = 'mini-btn';
      unwearBtn.textContent = '해제';
      unwearBtn.onclick = function () { action('EQUIP_UNWEAR_ONE', String(cur.EQUIP_ID)); closePicker(); };
      curRow.appendChild(unwearBtn);
      body.appendChild(curRow);
    }

    var label = document.createElement('div');
    label.className = 'equip-group-title';
    label.textContent = '장착 가능한 미착용 장비';
    body.appendChild(label);

    var candidates2 = unequipped.filter(function (e) { return e.PART === part && (occupant.GRADE === 8 || weaponAllowedJobs(e.CLASS).indexOf(occupant.CLASS) !== -1); })
        .sort(function (a, b) { return b.GRADE - a.GRADE; });
    if (candidates2.length === 0) {
      var noEquip = document.createElement('div');
      noEquip.className = 'sheet-empty';
      noEquip.textContent = '후보 장비가 없습니다.';
      body.appendChild(noEquip);
    } else {
      candidates2.forEach(function (e) {
        var row = document.createElement('div');
        row.className = 'sheet-row has-stat';
        row.innerHTML = '<span class="sr-main">' + equipIcon(e.CLASS, part) + ' ★' + e.GRADE + legendaryTag(e) + (e.QTY > 1 ? '<span class="eq-qty">x' + e.QTY + '</span>' : '') + '</span><span class="sr-sub">탭해서 장착</span>'
            + '<div class="sr-stat" data-eq="' + e.EQUIP_ID + '"></div>';
        row.onclick = function () { action('EQUIP_WEAR', String(e.__idx), String(pickerState.slot)); closePicker(); };
        body.appendChild(row);
      });
    }
    loadPickerPreview(occupant.COMPANION_ID, part, pickerState.slot);
  }

  // [2026-10-20] 장비변경 팝업에도 같은 수치 표기 -- 후보마다 "장비 능력치(+강화), 이 동료에게 올려주는 수치, 현재 장비 대비 증감".
  function loadPickerPreview(companionId, part, slot) {
    var u = userName();
    if (!u) return;
    fetch(base + '/api/tower-equip-preview?userName=' + encodeURIComponent(u) + '&companionId=' + companionId + '&part=' + encodeURIComponent(part))
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (!d || d.error || !pickerState || pickerState.slot !== slot || pickerState.part !== part) return;
        var byId = {};
        (d.rows || []).forEach(function (r) { byId[r.EQUIP_ID] = r; });
        var cur = byId[d.currentEquipId];
        Array.prototype.forEach.call(document.querySelectorAll('#pickerBody .sr-stat[data-eq]'), function (el) {
          var r = byId[el.getAttribute('data-eq')];
          if (!r) return;
          var isCur = String(d.currentEquipId) === el.getAttribute('data-eq');
          var enhInfo = r.ENHANCE_LEVEL > 0 ? ' <small>(강화 +' + r.ENHANCE_LEVEL + ', 성능 +' + (r.ENHANCE_LEVEL * 6) + '%)</small>' : '';
          var html = '<b>' + (eqStatText(r, 'raw') || '-') + '</b>' + enhInfo
              + '<br>캐릭터 상승 <b>' + (eqFinalText(r) || '-') + '</b>';
          if (!isCur) html += '<br>현재 장비 대비 ' + (eqDeltaText(r, cur) || '변화 없음');
          el.innerHTML = html;
        });
      })
      .catch(function () { /* 수치 표시는 부가 정보 */ });
  }

  // "보유동료/파티장비현황/미착용장비는 없애고 전체동료보기/전체아이템보기 버튼으로 이관"
  // 요청 -- 내용(renderPartyGrid/renderEquipList)은 loadPartyAndEquip()이 매번 백그라운드로
  // 계속 그려두고, 이 열기/닫기 함수는 그 결과가 담긴 팝업을 보이기만 한다.
  //
  // [2026-09-07] "빈 슬롯 배치/동료 변경 시 전체동료보기와 같은 창을 띄워달라" 요청 -- 별도
  // 팝업을 새로 만들지 않고 이 allCompanionsOverlay를 그대로 재사용한다. companionPickerSlot이
  // 있으면(openCompanionPicker로 열렸으면) renderPartyGrid()가 "배치 모드"로 그린다(미편성
  // 동료만, 탭하면 그 슬롯에 바로 배치하고 닫힘) -- 없으면(openAllCompanions로 열렸으면)
  // 기존처럼 전체 동료 브라우징 모드.
  var companionPickerSlot = null;

  function openCompanionPicker(slot) {
    companionPickerSlot = slot;
    gridFilter = { job: null, grade: null };
    document.getElementById('allCompanionsTitle').textContent = '파티 ' + slot + '번에 배치할 동료';
    document.getElementById('allCompanionsOverlay').classList.add('open');
    renderPartyGrid();
  }

  function openAllCompanions() {
    companionPickerSlot = null;
    gridFilter = { job: null, grade: null };
    document.getElementById('allCompanionsTitle').textContent = '보유 동료';
    document.getElementById('allCompanionsOverlay').classList.add('open');
    renderPartyGrid();
  }
  function closeAllCompanions() {
    companionPickerSlot = null;
    document.getElementById('allCompanionsOverlay').classList.remove('open');
  }
  // [2026-10-21] 동료 이미지 바꾸기 -- 편성된 동료(src)의 이미지를 내가 가진 다른 동료(전부, 숨김/편성 여부 무관)와 서로 맞바꾼다.
  var imageSwapSrc = null;
  var imageSwapFilter = { job: null, grade: null };
  function openImageSwap(companionId) {
    imageSwapSrc = companionId;
    imageSwapFilter = { job: null, grade: null };
    renderImageSwap();
    document.getElementById('imageSwapOverlay').classList.add('open');
  }
  function closeImageSwap() {
    imageSwapSrc = null;
    document.getElementById('imageSwapOverlay').classList.remove('open');
  }
  function companionNameOf(c) { return c.NAME || (JOB_KR[c.CLASS] || c.CLASS); }
  function renderImageSwap() {
    var companions = lastParty.companions || [];
    var src = companions.filter(function (x) { return x.COMPANION_ID === imageSwapSrc; })[0];
    if (!src) { closeImageSwap(); return; }
    var now = document.getElementById('imageSwapNow');
    now.innerHTML = '';
    now.appendChild(buildAvatarEl(src, 'avatar', false));
    var txt = document.createElement('div');
    txt.innerHTML = '<b>' + companionNameOf(src) + '</b> ' + gradeMark(src.GRADE)
        + '<br>아래에서 고른 동료와 <b>이미지를 서로 바꿉니다</b>. (능력치는 그대로)';
    now.appendChild(txt);

    var filterBox = document.getElementById('imageSwapFilters');
    filterBox.innerHTML = '';
    filterBox.appendChild(buildJobFilterRow(imageSwapFilter.job, function (job) { imageSwapFilter.job = job; renderImageSwap(); }));
    filterBox.appendChild(buildGradeFilterRow(imageSwapFilter.grade, function (g) { imageSwapFilter.grade = g; renderImageSwap(); }));

    var list = companions.filter(function (c) {
      if (c.COMPANION_ID === imageSwapSrc) return false;
      if (imageSwapFilter.job && c.CLASS !== imageSwapFilter.job) return false;
      if (imageSwapFilter.grade && c.GRADE !== imageSwapFilter.grade) return false;
      return true;
    }).sort(function (a, b) { return (b.GRADE || 0) - (a.GRADE || 0); });
    var grid = document.getElementById('imageSwapGrid');
    grid.innerHTML = '';
    if (!list.length) { grid.innerHTML = '<div class="sheet-empty">바꿀 수 있는 동료가 없습니다.</div>'; return; }
    list.forEach(function (c) {
      var div = document.createElement('div');
      div.className = 'party-card swappable' + (c.HIDDEN_YN === 'Y' ? ' hidden' : '');
      var lb = c.LIMIT_BREAK ? parseInt(c.LIMIT_BREAK, 10) : 0;
      div.innerHTML = '<div class="cname">' + companionNameOf(c) + '</div>'
          + '<div class="role">' + (JOB_KR[c.CLASS] || c.CLASS) + ' ' + gradeMark(c.GRADE) + (lb > 0 ? ' (+' + lb + ')' : '')
          + (c.PARTY_SLOT ? ' [파티' + c.PARTY_SLOT + ']' : '') + '</div>';
      div.insertBefore(buildAvatarEl(c, 'avatar', false), div.firstChild);
      div.onclick = function () { confirmImageSwap(src, c); };
      grid.appendChild(div);
    });
  }
  function confirmImageSwap(src, target) {
    document.getElementById('confirmMsg').textContent = '[' + companionNameOf(src) + ']와(과) [' + companionNameOf(target) + ']의 이미지를 서로 바꿀까요?';
    var yes = document.getElementById('confirmYesBtn');
    yes.textContent = '바꾸기';
    yes.onclick = function () {
      closeConfirm();
      closeImageSwap();
      action('COMPANION_IMAGE_SWAP', String(src.COMPANION_ID), String(target.COMPANION_ID));
    };
    document.getElementById('confirmOverlay').classList.add('open');
  }

  // [2026-10-22] 장비 목록은 인벤토리 탭으로 옮겨졌다 -- 예전 진입점은 탭으로 보낸다.
  function openAllEquip() { switchTab('inven'); }
  function closeAllEquip() {}

  // ---- 인벤토리 탭 ----
  var invTab = 'equip';
  function setInvTab(t) {
    invTab = t;
    document.querySelectorAll('.inv-tabs button').forEach(function (b) { b.classList.toggle('on', b.dataset.inv === t); });
    document.getElementById('invEquipPane').style.display = t === 'equip' ? '' : 'none';
    document.getElementById('invEtcPane').style.display = t === 'etc' ? '' : 'none';
    document.getElementById('invOrePane').style.display = t === 'ore' ? '' : 'none';
    document.getElementById('invSynthAllBtn').style.display = t === 'equip' ? '' : 'none';
    if (t === 'equip') { renderEquipList(); equipDirty = false; } else if (t === 'ore') renderInvOre(); else renderInvEtc();
  }

  // [2026-10-23] 광석 탭 -- 광산 입장 + 광석 보유(아이콘은 광산 도트와 같은 그림).
  function renderInvOre() {
    var box = document.getElementById('invOreBox');
    var u = userName();
    if (!u) { box.innerHTML = '<div class="sheet-empty">유저명을 입력하세요.</div>'; return; }
    fetch(base + '/api/tower-mine?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }).then(function (m) {
      if (m.error) { box.innerHTML = '<div class="sheet-empty">' + m.error + '</div>'; return; }
      var html = '<div class="ore-mine-card"><div><div class="ore-sta">⛏️ 광석은 광산에서 캐고, 광산상점에서 자동사냥 처치당 PP로 바꿔요.</div></div>'
          + '<button type="button" onclick="TW.switchTab(\'mineshop\')">🏪 광산상점</button></div>';
      html += '<div class="inv-etc-title">⛏️ 광석</div><div class="inv-etc-grid">';
      (m.ores || []).forEach(function (o) {
        html += '<div class="inv-item"><div class="inv-ico ' + (['t1', 't1', 't1', 't2', 't3', 't4', 'legend'][o.grade] || 't3') + '"><img class="ore-ico" src="' + TowerMine.oreIcon(o.grade) + '" alt="">'
            + (o.qty > 0 ? '<span class="inv-qty">x' + o.qty + '</span>' : '') + '</div>'
            + '<div class="inv-body"><div class="inv-name">' + o.name + '</div><div class="inv-sub">' + (o.grade >= 21 ? '보석' : o.grade === 18 ? '요일석 7종 합성' : o.grade >= 11 ? '요일 던전의 돌' : o.grade + '등급') + ' · 보유 ' + o.qty + '개</div></div></div>';
      });
      box.innerHTML = html + '</div>';
    }).catch(function () { box.innerHTML = '<div class="sheet-empty">불러오기에 실패했습니다.</div>'; });
  }
  function openMine() {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    TowerMine.open({ base: base, user: u, onClose: function () {
      if (document.getElementById('panel-mine').classList.contains('active')) renderMinePanel();
      if (invTab === 'ore') renderInvOre();
    } });
  }

  // [2026-10-23] 광산 탭 -- 곡괭이 내구도/현재 맵·위치 + 들어가기 + 최근 캔 곳(언제 캤는지 포함)
  function renderMinePanel() {
    var box = document.getElementById('mineBox');
    var u = userName();
    if (!u) { box.innerHTML = '<div class="sheet-empty">유저명을 입력하세요.</div>'; return; }
    fetch(base + '/api/tower-mine?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }).then(function (m) {
      if (m.error) { box.innerHTML = '<div class="sheet-empty">' + m.error + '</div>'; return; }
      var nr = m.nextResetAt ? new Date(m.nextResetAt) : null;
      var resetTxt = nr ? (nr.getMonth() + 1) + '/' + nr.getDate() + ' ' + (nr.getHours() < 10 ? '0' : '') + nr.getHours() + ':00' : '';
      var html = '<div class="ore-mine-card"><div><div class="ore-sta">⛏️ 곡괭이 내구도 ' + m.durability + ' / ' + m.durabilityMax + '</div>'
          + '<div class="ore-sub">캘 때마다 1씩 닳고, 중앙 평원 마을의 대장장이에게 광석을 주면 곡괭이가 강화돼요(최대·현재 내구도 함께 증가) · 맵은 2일마다 새로 섞여요' + (resetTxt ? '(다음 ' + resetTxt + ')' : '') + ' · 한 번 캔 칸은 리셋 전까지 다시 못 캐요</div>'
          + '<div class="ore-sub">📍 ' + m.mapName + ' (' + m.x + ', ' + m.y + ') · 누적 ' + (m.totalDig || 0) + '회 · ⛑️ 광부 투구 ' + (m.helmet || 0) + '단계</div></div>'
          + '<button type="button" onclick="TW.openMine()">⛏️ 들어가기</button></div>';
      var rec = m.recent || [];
      html += '<div class="inv-etc-title">최근 캔 곳</div>';
      if (!rec.length) html += '<div class="sheet-empty">아직 캔 곳이 없어요. 들어가서 캐 보세요!</div>';
      else {
        html += '<div class="mine-recent">';
        rec.forEach(function (d) {
          html += '<div class="mine-recent-row"><img src="' + TowerMine.oreIcon(d.grade) + '" alt=""><b>' + d.name + '</b><span class="xy">' + d.mapName + ' (' + d.x + ', ' + d.y + ')</span>'
              + '<span class="when">' + TowerMine.digTime(d.at) + '</span></div>';
        });
        html += '</div>';
      }
      box.innerHTML = html;
    }).catch(function () { box.innerHTML = '<div class="sheet-empty">불러오기에 실패했습니다.</div>'; });
  }

  // [2026-10-23] 광산상점 -- 광석을 내고 자동사냥 처치당 PP를 영구히 올린다(탑에서 자동사냥 정산 때 처치 수만큼).
  function renderMineShop() {
    var box = document.getElementById('mineShopBox');
    var u = userName();
    if (!u) { box.innerHTML = '<div class="sheet-empty">유저명을 입력하세요.</div>'; return; }
    fetch(base + '/api/tower-mine-shop?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }).then(function (d) {
      if (d.error) { box.innerHTML = '<div class="sheet-empty">' + d.error + '</div>'; return; }
      var bonus = Math.round((d.ppBonus || 0) * 10) / 10;
      // [2026-10-24] 광부 투구 업그레이드는 광산 안 대장장이 NPC에게서(이 탭에서는 뺐다)
      var html = '<div class="mshop-bonus">광산 수익 <b>처치당 +' + bonus + ' PP</b>'
          + '<div class="sub">탑 <b>자동사냥</b>으로 몬스터를 처치할 때마다 받아요(자동사냥 정산 때 처치 수만큼, 자동사냥이 꺼져 있으면 없음). 한 번 올린 수익은 영구히 유지돼요.</div></div>';
      (d.items || []).forEach(function (it) {
        html += '<div class="mshop-item"><div class="inv-ico ' + ['t1', 't1', 't1', 't2', 't3', 't4', 'legend'][it.grade] + '"><img class="ore-ico" src="' + TowerMine.oreIcon(it.grade) + '" alt=""></div>'
            + '<div class="body"><div class="ttl">' + it.oreName + ' ' + it.need + '개 → 처치당 +' + it.add + ' PP</div>'
            + '<div class="sub">보유 ' + it.have + '개' + (it.maxTimes > 0 ? ' · 최대 ' + it.maxTimes + '번 (+' + (it.maxTimes * it.add) + ' PP)' : '') + '</div></div>'
            + '<div class="btns"><button type="button" ' + (it.maxTimes < 1 ? 'disabled' : '') + ' onclick="TW.buyMineShop(' + it.no + ',1)">1번</button>'
            + (it.maxTimes > 1 ? '<button type="button" class="max" onclick="TW.buyMineShop(' + it.no + ',' + it.maxTimes + ')">전부(' + it.maxTimes + ')</button>' : '') + '</div></div>';
      });
      box.innerHTML = html;
    }).catch(function () { box.innerHTML = '<div class="sheet-empty">불러오기에 실패했습니다.</div>'; });
  }
  function buyMineShop(no, times) {
    var u = userName();
    if (!u) return;
    if (times > 1 && !confirm(times + '번 한꺼번에 바꿀까요? (되돌릴 수 없어요)')) return;
    fetch(base + '/api/tower-mine-shop-buy?userName=' + encodeURIComponent(u) + '&no=' + no + '&times=' + times).then(function (r) { return r.json(); }).then(function (d) {
      toast(d.message || d.error || '완료');
      renderMineShop();
    }).catch(function () { toast('요청 실패'); });
  }

  // 장비 카드 옵션 -- 능력치 보너스(서버 OPTION_TEXT, 실제 계산과 같은 표), 전설이면 강화와 고유 효과.
  function equipOptionHtml(e) {
    var lines = [];
    if (e.OPTION_TEXT) lines.push(String(e.OPTION_TEXT).split(' · ').join('<br>'));
    if (e.LEGENDARY_ID != null) {
      if (e.ENHANCE_LEVEL > 0) lines.push('강화 +' + e.ENHANCE_LEVEL + ' (능력치 +' + (e.ENHANCE_LEVEL * 6) + '%)');
      var fn = LEGEND_EFFECT_LABEL[e.LEGENDARY_EFFECT_TYPE];
      if (fn) lines.push('<span class="eq-leg">✨ ' + fn(e.LEGENDARY_EFFECT_PARAM1, e.LEGENDARY_EFFECT_PARAM2) + '</span>');
    }
    return lines.length ? '<div class="eq-opt">' + lines.join('<br>') + '</div>' : '';
  }

  // 기타 탭 -- 강화권, 전설의 조각, 등급별 뽑기권(상점 API), 선택권(진행 정보). 이미지는 등급 색 테두리를 두른 아이콘 타일.
  function renderInvEtc() {
    var box = document.getElementById('invEtcBox');
    var u = userName();
    if (!u) { box.innerHTML = '<div class="sheet-empty">유저명을 입력하세요.</div>'; return; }
    var p = state.progress || {};
    var tickets = (lastParty.tickets || []).slice().sort(function (a, b) { return b.ENHANCE_LEVEL - a.ENHANCE_LEVEL; });
    fetch(base + '/api/tower-shop?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }).then(function (shop) {
      var sections = [];
      var enh = [];
      tickets.forEach(function (t) {
        enh.push({ icon: '🎫', cls: 'legend', name: '+' + t.ENHANCE_LEVEL + ' 강화권', qty: 1,
                   sub: (t.SOURCE_ITEM_NAME ? t.SOURCE_ITEM_NAME + ' 해체 · ' : '') + '더 낮은 전설장비를 +' + t.ENHANCE_LEVEL + '로', btn: '전설강화로', act: openLegendEnhance });
      });
      if ((p.LEGEND_FRAGMENT || 0) > 0) {
        enh.push({ icon: '🧩', cls: 'legend', name: '전설의 조각', qty: p.LEGEND_FRAGMENT, sub: '전설 제작·강화 재료', btn: '상점으로', act: function () { switchTab('shop'); } });
      }
      if (enh.length) sections.push(['🎫 강화 재료', enh]);
      var tierNames = ['하급', '중급', '상급', '최상급'];
      var pulls = [];
      if ((shop.freeCompanionPullsLeft || 0) > 0) pulls.push({ icon: '🎁', cls: 't1', name: '튜토리얼 무료 동료뽑기', qty: shop.freeCompanionPullsLeft, sub: '상점 동료뽑기에 사용', btn: '상점으로', act: function () { switchTab('shop'); } });
      [['companionVoucherByTier', '📜', '동료뽑기권'], ['equipVoucherByTier', '🧰', '장비뽑기권'], ['accessoryVoucherByTier', '💎', '악세뽑기권']].forEach(function (def) {
        (shop[def[0]] || []).forEach(function (n, i) {
          if (n > 0) pulls.push({ icon: def[1], cls: 't' + (i + 1), name: tierNames[i] + ' ' + def[2], qty: n, sub: tierNames[i] + ' 상자 뽑기에 사용', btn: '상점으로', act: function () { switchTab('shop'); } });
        });
      });
      if (pulls.length) sections.push(['🎟️ 뽑기권', pulls]);
      var choices = [];
      [[3, ''], [4, '_G4'], [5, '_G5']].forEach(function (g) {
        var c = p['COMPANION_CHOICE_TICKET' + g[1]] || 0, w = p['WEAPON_CHOICE_TICKET' + g[1]] || 0;
        if (c > 0) choices.push({ icon: '🎟️', cls: 't' + (g[0] - 1), name: '★' + g[0] + ' 동료 선택권', qty: c, sub: '직업을 골라 ★' + g[0] + ' 동료 획득', btn: '상점으로', act: function () { switchTab('shop'); } });
        if (w > 0) choices.push({ icon: '🗡️', cls: 't' + (g[0] - 1), name: '★' + g[0] + ' 무기 선택권', qty: w, sub: '직업을 골라 ★' + g[0] + ' 무기 획득', btn: '상점으로', act: function () { switchTab('shop'); } });
      });
      if (choices.length) sections.push(['🎯 선택권', choices]);

      box.innerHTML = '';
      if (!sections.length) { box.innerHTML = '<div class="sheet-empty">보유한 기타 아이템이 없습니다.</div>'; return; }
      sections.forEach(function (sec) {
        var t = document.createElement('div');
        t.className = 'inv-etc-title';
        t.textContent = sec[0];
        box.appendChild(t);
        var grid = document.createElement('div');
        grid.className = 'inv-etc-grid';
        sec[1].forEach(function (it) {
          var el = document.createElement('div');
          el.className = 'inv-item';
          el.innerHTML = '<div class="inv-ico ' + it.cls + '">' + it.icon + (it.qty > 1 ? '<span class="inv-qty">x' + it.qty + '</span>' : '') + '</div>'
              + '<div class="inv-body"><div class="inv-name">' + it.name + '</div><div class="inv-sub">' + it.sub + '</div><button type="button">' + it.btn + '</button></div>';
          el.querySelector('button').onclick = it.act;
          grid.appendChild(el);
        });
        box.appendChild(grid);
      });
    }).catch(function () { box.innerHTML = '<div class="sheet-empty">불러오기에 실패했습니다.</div>'; });
  }

  // 캐릭터 확대/상세 카드에서 쓰려고 마지막으로 불러온 파티·장비 데이터를 기억해둔다
  // (모달을 열 때마다 다시 fetch하지 않고, 스탯만 별도로 조회). 선택 팝업(renderPicker)도
  // 같은 캐시를 공유한다.
  var lastParty = { companions: [], byCompanion: {}, unequipped: [] };

  /** [2026-09-14] "공방체 스탯을 합산치 말고 한계돌파0성스탯(+돌파로인한스탯)처럼 표기해달라"
   * 요청 -- final(장비/스탯구매/한계돌파 전부 반영)에서 base(한계돌파만 뺀 값)를 빼서 그
   * 차이를 "+보너스"로 보여준다. 한계돌파 0단계라 차이가 없으면 그냥 숫자만(불필요한 "+0"
   * 생략). base가 없는(구버전 응답 등) 경우를 대비해 null/undefined 방어. */
  function fmtStatWithBonus(finalVal, baseVal) {
    if (finalVal == null) return '-';
    if (baseVal == null) return String(finalVal);
    var bonus = finalVal - baseVal;
    return bonus > 0 ? (baseVal + '(+' + bonus + ')') : String(finalVal);
  }

  function showCompanionDetail(companionId) {
    var c = lastParty.companions.filter(function (x) { return x.COMPANION_ID === companionId; })[0];
    if (!c) return;
    var name = c.NAME || (JOB_KR[c.CLASS] || c.CLASS);
    var detailImg = document.getElementById('detailImg');
    detailImg.onerror = function () { detailImg.style.display = 'none'; };
    // 성장형(☆1) 이미지는 앱에 포함된 파일(classpath:)일 수 있어 브라우저가 직접 못 읽으므로 아바타 프록시로 받는다.
    detailImg.src = (c.GRADE === 8 || /^classpath:/.test(c.IMAGE_URL || '')) && c.IMAGE_URL ? (base + '/api/tower-avatar?companionId=' + c.COMPANION_ID + '&u=' + encodeURIComponent(c.IMAGE_URL)) : (c.IMAGE_URL || '');
    detailImg.style.display = c.IMAGE_URL ? '' : 'none';
    document.getElementById('detailName').textContent = name;
    // [2026-09-14] "동료편성 화면에 한계돌파 (+N) 표기해달라" 요청 -- 단계가 있을 때만 붙인다.
    var lb = c.LIMIT_BREAK ? parseInt(c.LIMIT_BREAK, 10) : 0;
    document.getElementById('detailRole').textContent = (JOB_KR[c.CLASS] || c.CLASS) + ' ' + gradeMark(c.GRADE)
        + (lb > 0 ? ' (+' + lb + ')' : '')
        + (c.PARTY_SLOT ? ' · 파티 ' + c.PARTY_SLOT + '번' : ' · 대기중');
    document.getElementById('detailHp').textContent = '-';
    document.getElementById('detailAtk').textContent = '-';
    document.getElementById('detailDef').textContent = '-';
    ['Hp', 'Atk', 'Def'].forEach(function (k) { document.getElementById('detail' + k + 'Sub').innerHTML = ''; });

    var mine = lastParty.byCompanion[companionId] || [];
    var eqBox = document.getElementById('detailEquipBox');
    eqBox.innerHTML = '';
    PART_ORDER.forEach(function (part) {
      var found = mine.filter(function (e) { return e.PART === part; })[0];
      var row = document.createElement('div');
      row.className = 'detail-equip-row';
      row.innerHTML = '<span>' + PART_KR[part] + '</span><span>' + (found ? ('★' + found.GRADE + legendaryTag(found)) : '미착용') + '</span>';
      eqBox.appendChild(row);
    });

    document.getElementById('detailOverlay').classList.add('open');

    var u = userName();
    // [2026-10-20] "상단에 캐릭터 기본스탯(+한계돌파로 인한 스탯) + 장비 총합, 장비는 기본스탯(+강화로 인한 스탯)과 각 장비로 인한 상승량" 요청 --
    // 서버(companionDetail)가 최종 능력치를 기본/한계돌파(★7은 7성 보정)/장비 합계로 분해하고 장비별 기여를 내려준다. 기본 값에는 스탯구매가 포함돼 있다.
    fetch(base + '/api/tower-companion-detail?userName=' + encodeURIComponent(u) + '&companionId=' + companionId)
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (d.error) return;
        [['Hp', 'hp'], ['Atk', 'atk'], ['Def', 'def']].forEach(function (k) {
          var key = k[1];
          document.getElementById('detail' + k[0]).textContent = fmtN(d.total[key]);
          document.getElementById('detail' + k[0] + 'Sub').innerHTML =
              '<div>기본 <b>' + fmtN(d.base[key]) + '</b></div>' + (d.lbBonus[key] ? '<div class="lb">(+' + d.lbLabel + ' ' + fmtN(d.lbBonus[key]) + ')</div>' : '')
              + '<div>장비 <b>+' + fmtN(d.equipTotal[key]) + '</b>' + (d.equipEffectPct < 100 ? ' <small>(효과 ' + d.equipEffectPct + '%)</small>' : '') + '</div>';
        });
        var byPart = {};
        (d.equips || []).forEach(function (r) { byPart[r.PART] = r; });
        eqBox.innerHTML = '';
        PART_ORDER.forEach(function (part) {
          var found = mine.filter(function (e) { return e.PART === part; })[0];
          var r = byPart[part];
          var el = document.createElement('div');
          el.className = 'der';
          var head = '<div class="der-head"><span>' + PART_KR[part] + '</span><span>' + (found ? ('★' + found.GRADE + legendaryTag(found)) : '미착용') + '</span></div>';
          if (!found || !r) { el.innerHTML = head; eqBox.appendChild(el); return; }
          el.innerHTML = head
              + '<div class="der-line"><b>' + (eqStatText(r, 'raw') || '-') + '</b></div>'
              + '<div class="der-line up">캐릭터 상승 <b>' + (eqFinalText(r) || '-') + '</b>' + (r.ENHANCE_LEVEL > 0 ? ' <small>(강화 +' + r.ENHANCE_LEVEL + ', 성능 +' + (r.ENHANCE_LEVEL * 6) + '%)</small>' : '') + '</div>';
          eqBox.appendChild(el);
        });
      })
      .catch(function () {});
  }

  function closeDetail() {
    document.getElementById('detailOverlay').classList.remove('open');
  }

  // 10층 구간 완전탐사 보상(★3 선택권)은 채팅 명령어 없이 이 웹 화면에서만 쓸 수 있다.
  // /api/tower-status가 내려주는 progress(p)에 COMPANION_CHOICE_TICKET/WEAPON_CHOICE_TICKET
  // 이 이미 포함돼 있어서(BotS5Mapper selectUserProgress) 별도 API 없이 바로 씀.
  // 선택권 등급(3/4/5)은 그 선택권을 지급한 10층 구간(블록)에 따라 이미 정해져 있어서
  // (블록1~3=★3, 4~5=★4, 6~10=★5) 여러 등급을 동시에 들고 있을 수 있다 -- 등급별로 줄을 나눠 표시.
  function renderTickets(p) {
    var compByGrade = { 3: p.COMPANION_CHOICE_TICKET || 0, 4: p.COMPANION_CHOICE_TICKET_G4 || 0, 5: p.COMPANION_CHOICE_TICKET_G5 || 0 };
    var weapByGrade = { 3: p.WEAPON_CHOICE_TICKET || 0, 4: p.WEAPON_CHOICE_TICKET_G4 || 0, 5: p.WEAPON_CHOICE_TICKET_G5 || 0 };
    var totalTix = compByGrade[3] + compByGrade[4] + compByGrade[5] + weapByGrade[3] + weapByGrade[4] + weapByGrade[5];
    var card = document.getElementById('ticketCard');
    var box = document.getElementById('ticketBox');
    if (totalTix <= 0) { card.style.display = 'none'; return; }
    card.style.display = '';
    box.innerHTML = '';

    function jobPickerRow(label, count, actionType, grade) {
      if (count <= 0) return;
      var row = document.createElement('div');
      row.className = 'shop-row';
      row.style.flexWrap = 'wrap';
      var head = document.createElement('span');
      head.textContent = label + ' ' + count + '장';
      row.appendChild(head);
      var picker = document.createElement('span');
      picker.className = 'btn-group';
      picker.style.flexWrap = 'wrap';
      JOB_ORDER.forEach(function (job) {
        var btn = document.createElement('button');
        btn.textContent = JOB_KR[job];
        btn.onclick = function () { action(actionType, job, String(grade)); };
        picker.appendChild(btn);
      });
      row.appendChild(picker);
      box.appendChild(row);
    }

    [3, 4, 5].forEach(function (g) { jobPickerRow('★' + g + ' 동료 선택권', compByGrade[g], 'REDEEM_COMPANION_TICKET', g); });
    [3, 4, 5].forEach(function (g) { jobPickerRow('★' + g + ' 무기 선택권', weapByGrade[g], 'REDEEM_WEAPON_TICKET', g); });
  }

  // [2026-09-06] "보유동료 성급별/직업별 필터링" 요청 -- partyGrid 전용 필터 상태(null=전체).
  var gridFilter = { job: null, grade: null };
  // "장비도 필터링(직업탭 + 그 안에서 부위탭)" 요청 -- equipListBox 전용 필터 상태.
  var equipFilter = { job: null, part: null };

  // [2026-09-06] "파티 영역을 더 크게, 동료 한 명씩 영역도 크게, 동료변경/해제 버튼과 부위별
  // 장비변경 버튼을 직관적으로" 요청으로 슬롯을 3열 그리드가 아니라 1열로 크게 펼친 카드로
  // 다시 그린다. 카드 자체에 모든 정보(초상화/이름/등급/HP/장비 3부위)와 버튼(동료변경/해제,
  // 부위별 OO변경)이 다 보여서 더 이상 "탭해서 열기"가 필요 없다 -- 각 버튼이 바로 해당
  // 동작(즉시 실행 또는 선택 팝업)을 한다.
  var PARTY4_UNLOCK_FLOOR = 80;
  function renderPartySlots() {
    var companions = lastParty.companions || [];
    var byCompanion = lastParty.byCompanion || {};
    var box = document.getElementById('partySlots');
    box.innerHTML = '';
    var bySlot = {};
    companions.forEach(function (c) { if (c.PARTY_SLOT) bySlot[c.PARTY_SLOT] = c; });
    // [2026-10-23] 파티 4번 슬롯 -- 최고 도달층 80층 이상이면 열림(서버 partyMax와 같은 규칙), 아니면 잠긴 칸으로 보여준다.
    var maxReached = (state.progress && state.progress.MAX_FLOOR_REACHED) || 0;
    var slotCount = maxReached >= PARTY4_UNLOCK_FLOOR ? 4 : 3;
    for (var s = 1; s <= 4; s++) {
      if (s > slotCount && !bySlot[s]) {
        var lockCard = document.createElement('div');
        lockCard.className = 'party-slot-card empty locked';
        lockCard.innerHTML = '<div class="ps-slot-label">파티 ' + s + '번</div><div class="ps-empty-msg">🔒 ' + PARTY4_UNLOCK_FLOOR + '층에 도달하면 열려요 (최고 ' + maxReached + '층)</div>';
        box.appendChild(lockCard);
        continue;
      }
      var occ = bySlot[s];
      var occDown = occ && isIncapacitated(occ.CUR_HP_VALUE);
      var card = document.createElement('div');
      card.className = 'party-slot-card' + (occ ? ' filled' : ' empty') + (occDown ? ' incapacitated' : '');

      if (!occ) {
        card.innerHTML = '<div class="ps-slot-label">파티 ' + s + '번</div><div class="ps-empty-msg">빈 슬롯</div>';
        var placeBtn = document.createElement('button');
        placeBtn.type = 'button';
        placeBtn.className = 'ps-btn';
        placeBtn.textContent = '동료 배치';
        (function (slotNo) { placeBtn.onclick = function () { openCompanionPicker(slotNo); }; })(s);
        card.appendChild(placeBtn);
        box.appendChild(card);
        continue;
      }

      var mine = byCompanion[occ.COMPANION_ID] || [];
      var occIdx = companions.indexOf(occ) + 1; // PARTY_TOGGLE(해제)이 참조하는 "N번째 동료" 번호

      var header = document.createElement('div');
      header.className = 'ps-header';
      header.appendChild(buildAvatarEl(occ, 'ps-avatar', true));
      var info = document.createElement('div');
      info.className = 'ps-info';
      // [2026-09-14] "동료편성 화면 및 텍스트에 (+3)이런식으로 표기해줘" 요청 -- 파티 슬롯
      // 카드(항상 보이는 편성 화면 본체)에도 한계돌파 단계를 등급 뒤에 붙인다.
      var occLb = occ.LIMIT_BREAK ? parseInt(occ.LIMIT_BREAK, 10) : 0;
      info.innerHTML = '<div class="ps-slot-label">파티 ' + s + '번</div>'
          + '<div class="ps-name">' + (occ.NAME || JOB_KR[occ.CLASS] || occ.CLASS) + '</div>'
          + '<div class="ps-role">' + (JOB_KR[occ.CLASS] || occ.CLASS) + ' ' + gradeMark(occ.GRADE)
          + (occLb > 0 ? ' (+' + occLb + ')' : '') + '</div>'
          + (occDown ? '<div class="ps-hp incapacitated">💀 전투불능 (HP 0)</div>'
                     : '<div class="ps-hp">HP ' + fmtPP(occ.CUR_HP_VALUE, occ.CUR_HP_EXT) + '</div>')
          + (occ.WEAK_PCT > 0 ? '<div class="ps-hp" style="color:var(--combat);">⚠️ 4번째 동료 약화 -' + occ.WEAK_PCT + '% (1층 오를 때마다 0.1%씩 풀림)</div>' : '');
      header.appendChild(info);
      card.appendChild(header);
      var detailEl = document.createElement('div');
      detailEl.className = 'ps-detail';
      detailEl.setAttribute('data-cid', occ.COMPANION_ID);
      card.appendChild(detailEl);

      var actions = document.createElement('div');
      actions.className = 'ps-actions';
      var changeBtn = document.createElement('button');
      changeBtn.type = 'button';
      changeBtn.className = 'ps-btn';
      changeBtn.textContent = '동료변경';
      (function (slotNo) { changeBtn.onclick = function () { openCompanionPicker(slotNo); }; })(s);
      var unassignBtn = document.createElement('button');
      unassignBtn.type = 'button';
      unassignBtn.className = 'ps-btn danger';
      unassignBtn.textContent = '해제';
      // [2026-09-07 버그 수정] "누른 동료가 아니라 다른 동료가 해제된다" 신고 -- occIdx가
      // for문 안에서 var로 선언돼 함수 스코프를 타서, 세 슬롯의 onclick 클로저가 전부 같은
      // occIdx 변수를 공유하고 있었다(루프가 다 돈 뒤 클릭 시점엔 항상 마지막 슬롯 값).
      // 다른 버튼들처럼 즉시실행함수로 캡처해서 슬롯마다 자기 값을 갖게 한다.
      (function (idx) { unassignBtn.onclick = function () { action('PARTY_TOGGLE', String(idx)); }; })(occIdx);
      // [2026-09-18] "동료 1명에 대해 장착할수있는 장비 일괄 장착 기능" 요청 -- 6부위 각각
      // 미착용 후보 중 최고 등급을 한 번에 장착(서버 equipBestAll이 이미 착용보다 등급 낮으면
      // 스킵). 다른 버튼들과 같은 즉시실행함수 캡처 패턴으로 occIdx 클로저 버그 재발 방지.
      var bestAllBtn = document.createElement('button');
      bestAllBtn.type = 'button';
      bestAllBtn.className = 'ps-btn';
      bestAllBtn.textContent = '일괄장착';
      (function (idx) { bestAllBtn.onclick = function () { action('EQUIP_BEST_ALL', String(idx)); }; })(occIdx);
      var imageBtn = document.createElement('button');
      imageBtn.type = 'button';
      imageBtn.className = 'ps-btn';
      imageBtn.textContent = '이미지';
      imageBtn.title = '이미지 변경 -- 내 다른 동료와 이미지를 서로 바꿉니다';
      (function (cid) { imageBtn.onclick = function () { openImageSwap(cid); }; })(occ.COMPANION_ID);
      actions.appendChild(changeBtn);
      actions.appendChild(bestAllBtn);
      actions.appendChild(imageBtn);
      actions.appendChild(unassignBtn);
      card.appendChild(actions);

      var equipRow = document.createElement('div');
      equipRow.className = 'ps-equip-row';
      PART_ORDER.forEach(function (part) {
        var found = mine.filter(function (e) { return e.PART === part; })[0];
        var equipSlotEl = document.createElement('div');
        equipSlotEl.className = 'ps-equip-slot';
        var foundEnh = found && found.LEGENDARY_ID != null ? (found.ENHANCE_LEVEL || 0) : 0;
        equipSlotEl.innerHTML = '<div class="ps-equip-icon">' + (found ? equipIcon(found.CLASS, part) : (PART_EMOJI[part] || '🎽')) + '</div>'
            + (found && found.LEGENDARY_ID != null && found.LEGENDARY_ITEM_NAME ? '<div class="ps-equip-name" title="' + found.LEGENDARY_ITEM_NAME + '">✨' + found.LEGENDARY_ITEM_NAME + '</div>' : '')
            + '<div class="ps-equip-grade">' + (found ? '★' + found.GRADE + (foundEnh > 0 ? ' +' + foundEnh + '강' : '') : '비어있음') + '</div>'
            + (found ? '<div class="ps-equip-stat" data-eq="' + found.EQUIP_ID + '"></div>' : '');
        if (found) equipSlotEl.setAttribute('data-cid', occ.COMPANION_ID);
        var changeEqBtn = document.createElement('button');
        changeEqBtn.type = 'button';
        changeEqBtn.className = 'ps-btn small';
        changeEqBtn.textContent = PART_KR[part] + '변경';
        (function (slotNo, p) { changeEqBtn.onclick = function () { openEquipPicker(slotNo, p); }; })(s, part);
        equipSlotEl.appendChild(changeEqBtn);
        equipRow.appendChild(equipSlotEl);
      });
      card.appendChild(equipRow);
      box.appendChild(card);
    }
    applyPartyEquipStat();
  }

  // [2026-10-20] "동료편성 화면에 장비/악세 능력치와 강화수치(강화로 늘어난 성능 포함)를 보여달라" 요청 -- 서버(partyEquipContribution)가
  // 장비 1개가 올려주는 최종 체력/공격/방어(스탯구매/한계돌파 반영)와 그중 강화(+N, 1강당 +6%)로 늘어난 몫을 계산해서 내려준다.
  var partyEquipStat = {};
  var partyDetailData = {};
  // [2026-10-20] 파티 카드 상단 상세 -- 총합 = 원래 + 스탯구매 + 한계돌파(★7은 7성 보정) + 장비 + 장비강화(색 구분), 아래에 총 증가율(배율) 계산 근거.
  function psDetailHtml(d) {
    if (!d || d.error || !d.total) return '';
    var html = '';
    [['hp', 'HP'], ['atk', '공격'], ['def', '방어']].forEach(function (r) {
      var k = r[0];
      var enh = (d.enhTotal && d.enhTotal[k]) || 0;
      var eq = d.equipTotal[k] || 0;
      html += '<div><span class="psd-k">' + r[1] + '</span> <b class="psd-total">' + fmtN(d.total[k]) + '</b> = '
          + '<span class="c-base">원래 ' + fmtN(d.baseRaw[k]) + '</span>'
          + (d.statBuy[k] ? ' <span class="c-buy">+스탯구매 ' + fmtN(d.statBuy[k]) + '</span>' : '')
          + (d.lbBonus[k] ? ' <span class="c-lb">+' + d.lbLabel + ' ' + fmtN(d.lbBonus[k]) + '</span>' : '')
          + (eq ? ' <span class="c-eq">+장비 ' + fmtN(eq - enh) + (d.equipEffectPct < 100 ? '(효과 ' + d.equipEffectPct + '%)' : '') + '</span>' : '')
          + (enh ? ' <span class="c-enh">+장비강화 ' + fmtN(enh) + '</span>' : '')
          + '</div>';
    });
    var p = d.pct || {};
    function why(total, buy) {
      if (!(total > 0)) return null;
      var parts = [];
      if (buy > 0) parts.push('스탯구매 +' + fmtPct(buy) + '%');
      if (p.lb > 0) parts.push(d.lbLabel + ' +' + fmtPct(p.lb) + '%');
      return '+' + fmtPct(total) + '%' + (parts.length > 1 ? ' (' + parts.join(' × ') + ')' : (parts.length ? ' (' + parts[0] + ')' : ''));
    }
    var lines = [];
    var w1 = why(p.totalHp, p.buyHp), w2 = why(p.totalAtk, p.buyAtk), w3 = why(p.totalDef, 0);
    if (w1) lines.push('체력 <b>' + w1 + '</b>');
    if (w2) lines.push('공격 <b>' + w2 + '</b>');
    if (w3) lines.push('방어 <b>' + w3 + '</b>');
    if (lines.length) html += '<div class="psd-mult">총 증가율(배율) — ' + lines.join(' · ') + '<br>※ 이 배율은 (원래 + 장비)에 곱해져서 장비 수치에도 이미 반영돼 있습니다.</div>';
    return html;
  }
  function fmtN(n) { return Number(n).toLocaleString('ko-KR'); }
  function applyPartyEquipStat() {
    Array.prototype.forEach.call(document.querySelectorAll('#partySlots .ps-detail[data-cid]'), function (el) {
      el.innerHTML = psDetailHtml(partyDetailData[el.getAttribute('data-cid')]);
    });
    Array.prototype.forEach.call(document.querySelectorAll('#partySlots .ps-equip-slot[data-cid]'), function (slot) {
      var rows = partyEquipStat[slot.getAttribute('data-cid')] || [];
      var el = slot.querySelector('.ps-equip-stat');
      if (!el) return;
      var row = rows.filter(function (r) { return String(r.EQUIP_ID) === el.getAttribute('data-eq'); })[0];
      if (!row) { el.innerHTML = ''; return; }
      var lines = [];
      if (row.HP) lines.push('체력 <b>+' + fmtN(row.HP) + '</b>');
      if (row.ATK) lines.push('공격 <b>+' + fmtN(row.ATK) + '</b>');
      if (row.DEF) lines.push('방어 <b>+' + fmtN(row.DEF) + '</b>');
      var html = lines.join('<br>');
      if (row.ENHANCE_LEVEL > 0) {
        var enh = [];
        if (row.ENH_HP) enh.push('체력+' + fmtN(row.ENH_HP));
        if (row.ENH_ATK) enh.push('공격+' + fmtN(row.ENH_ATK));
        if (row.ENH_DEF) enh.push('방어+' + fmtN(row.ENH_DEF));
        html += '<div class="ps-equip-enh">⬆️ 강화 +' + row.ENHANCE_LEVEL + ' (성능 +' + (row.ENHANCE_LEVEL * 6) + '%)' + (enh.length ? '<br>' + enh.join(' ') : '') + '</div>';
      }
      el.innerHTML = html;
    });
  }
  // [2026-10-20] 장비/동료 상세 수치 표기 -- 서버(equipContribRow)가 준 행에서 "기본 +강화분" 형태로 만든다.
  // kind 'raw' = 장비 자체 수치, 'final' = 동료에게 실제로 올려주는 수치(스탯구매/한계돌파 반영). 강화분(ENH)은 전체에서 따로 떼어 (+강화 N)로 붙인다.
  function eqStatText(row, kind) {
    var pre = kind === 'raw' ? 'RAW_' : '';
    var out = [];
    [['HP', '체력'], ['ATK', '공격'], ['DEF', '방어']].forEach(function (p) {
      var total = row[pre + p[0]] || 0;
      if (!total) return;
      var enh = row[pre + 'ENH_' + p[0]] || 0;
      out.push(p[1] + ' +' + fmtN(total - enh) + (enh > 0 ? '(+강화 ' + fmtN(enh) + ')' : ''));
    });
    return out.join(' · ');
  }
  function fmtPct(x) { return (Math.round(x * 10) / 10).toString(); }
  // "캐릭터 상승" = 이 장비가 캐릭터 능력치를 올려주는 양과, 그 장비를 뺀 능력치 대비 몇 %인지
  function eqFinalText(row) {
    var out = [];
    [['HP', '체력'], ['ATK', '공격'], ['DEF', '방어']].forEach(function (p) {
      if (!row[p[0]]) return;
      var pct = row['PCT_' + p[0]];
      out.push(p[1] + ' +' + fmtN(row[p[0]]) + (pct > 0 ? '(+' + fmtPct(pct) + '%)' : ''));
    });
    return out.join(' · ');
  }
  // 현재 장비 대비 증감(장비변경 후보 표시용)
  function eqDeltaText(row, cur) {
    var out = [];
    [['HP', '체력'], ['ATK', '공격'], ['DEF', '방어']].forEach(function (p) {
      var d = (row[p[0]] || 0) - (cur ? (cur[p[0]] || 0) : 0);
      if (d !== 0) out.push('<span class="' + (d > 0 ? 'up' : 'down') + '">' + p[1] + ' ' + (d > 0 ? '+' : '-') + fmtN(Math.abs(d)) + '</span>');
    });
    return out.join(' ');
  }

  function loadPartyEquipStat() {
    var u = userName();
    if (!u) return;
    fetch(base + '/api/tower-party-equip-stat?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }).then(function (d) {
      if (d && d.byCompanion) { partyEquipStat = d.byCompanion; partyDetailData = d.detail || {}; applyPartyEquipStat(); }
    }).catch(function () { /* 수치 표시는 부가 정보라 실패해도 조용히 둔다 */ });
  }

  // 보유 동료 목록 -- 편성 여부 1순위(편성된 동료 먼저) + 성급 내림차순으로 정렬(기존과 동일),
  // 그 위에 직업/성급 필터 칩을 얹는다("필터링 기능 있으면 좋겠다" 요청). idx는 /파티편성·
  // PARTY_TOGGLE 등이 참조하는 "N번째 동료" 번호라 필터와 무관하게 항상 원본 순서 기준.
  // [2026-09-07] companionPickerSlot이 있으면(openCompanionPicker로 열림) 미편성 동료만
  // 후보로 보여주고 탭하면 그 슬롯에 배치, 없으면(openAllCompanions) 기존처럼 전체 브라우징
  // (숨기기/상세보기만). "정렬순서를 성급 desc로" 요청으로 편성여부 우선순위를 없애고 항상
  // 성급 내림차순 하나로 통일.
  function renderPartyGrid() {
    var companions = lastParty.companions || [];
    var picking = !!companionPickerSlot;
    var filterBox = document.getElementById('partyGridFilters');
    filterBox.innerHTML = '';
    filterBox.appendChild(buildJobFilterRow(gridFilter.job, function (job) { gridFilter.job = job; renderPartyGrid(); }));
    filterBox.appendChild(buildGradeFilterRow(gridFilter.grade, function (g) { gridFilter.grade = g; renderPartyGrid(); }));

    var displayOrder = companions.map(function (c, i) { return { c: c, idx: i + 1 }; })
        .filter(function (entry) {
          if (picking && entry.c.PARTY_SLOT) return false; // 배치 모드: 이미 편성된 동료는 후보 제외
          if (gridFilter.job && entry.c.CLASS !== gridFilter.job) return false;
          if (gridFilter.grade && entry.c.GRADE !== gridFilter.grade) return false;
          return true;
        })
        .sort(function (a, b) { return (b.c.GRADE || 0) - (a.c.GRADE || 0); });
    var grid = document.getElementById('partyGrid');
    grid.innerHTML = '';
    if (displayOrder.length === 0) {
      grid.innerHTML = '<div class="sheet-empty">' + (picking ? '배치할 수 있는 동료가 없습니다.' : '조건에 맞는 동료가 없습니다.') + '</div>';
      return;
    }
    displayOrder.forEach(function (entry) {
      var c = entry.c, idx = entry.idx;
      var hidden = c.HIDDEN_YN === 'Y';
      var inParty = !!c.PARTY_SLOT;
      var cDown = isIncapacitated(c.CUR_HP_VALUE);
      var div = document.createElement('div');
      div.className = 'party-card' + (inParty ? ' inparty' : '') + (hidden ? ' hidden' : '') + (picking ? ' pickable' : '') + (cDown ? ' incapacitated' : '');
      var name = c.NAME || (JOB_KR[c.CLASS] || c.CLASS);
      // [2026-09-14] 동료 전체 목록/선택 카드에도 한계돌파 단계 표기.
      var cLb = c.LIMIT_BREAK ? parseInt(c.LIMIT_BREAK, 10) : 0;
      // [2026-09-14] "밑에 HP 바에도 한계돌파 수치를 (+몇)으로, 공격력/방어력도 비슷한
      // 방식으로 표기해주자" 요청 -- 현재 HP(CUR_HP_VALUE) 대신 유효 최대치(EFF_HP, 장비/
      // 스탯구매/한계돌파 전부 반영)를 base(+한계돌파 보너스) 형태로 보여주고, 같은 방식으로
      // 공격력/방어력 줄을 새로 추가한다(companionsWithEffectiveStats()가 내려주는 EFF_*
      // 필드, apiTowerParty 참고). fmtStatWithBonus는 위 showCompanionDetail 쪽에서 이미
      // 정의된 공용 헬퍼.
      div.innerHTML = '<div class="cname">' + name + '</div>'
          + '<div class="role">' + (JOB_KR[c.CLASS] || c.CLASS) + ' ' + gradeMark(c.GRADE)
          + (cLb > 0 ? ' (+' + cLb + ')' : '') + '</div>'
          + (cDown ? '<div class="incapacitated-badge">💀 전투불능</div>' : '')
          + '<div class="hpbar-track"><div class="hpbar-fill" style="width:100%"></div></div>'
          + '<div class="hp-num">💗' + fmtStatWithBonus(c.EFF_HP, c.EFF_HP_BASE) + (c.PARTY_SLOT ? ' [파티' + c.PARTY_SLOT + ']' : '') + '</div>'
          + '<div class="atk-num">⚔️' + fmtStatWithBonus(c.EFF_ATK, c.EFF_ATK_BASE) + '</div>'
          + '<div class="def-num">🛡️' + fmtStatWithBonus(c.EFF_DEF, c.EFF_DEF_BASE) + '</div>';
      div.insertBefore(buildAvatarEl(c, 'avatar', true), div.firstChild);
      if (picking) {
        // 배치 모드 -- 카드를 탭하면 바로 배치(아바타는 buildAvatarEl이 이미 stopPropagation
        // 처리해서 상세보기가 먼저 뜬다). 목록 관리용 숨기기 버튼은 이 모드에선 불필요.
        var slotNo = companionPickerSlot;
        div.onclick = function () {
          action('PARTY_SWAP', String(idx), String(slotNo));
          closeAllCompanions();
        };
      } else {
        var hideBtn = document.createElement('button');
        hideBtn.className = 'hide-btn';
        hideBtn.type = 'button';
        hideBtn.textContent = hidden ? '👀' : '🙈';
        hideBtn.title = hidden ? '목록에 다시 표시' : '텍스트 목록(/파티편성)에서 숨기기';
        hideBtn.onclick = function (ev) { ev.stopPropagation(); action('COMPANION_HIDE', String(idx)); };
        div.appendChild(hideBtn);
      }
      grid.appendChild(div);
    });
  }

  // 미착용 장비 목록 -- 정밀한 부위별 장착은 파티 슬롯 카드의 "OO변경" 버튼에서, 여기 버튼(장착/합성)은
  // 자동배정 장착과 합성 전용. "필터링 넣어줘(전체/전사/도적..., 그 안에서 또 전체/무기/투구/
  // 갑옷)" 요청으로 직업 탭 + 부위 탭 2단 필터를 얹는다(그룹 헤더는 필터링 후에도 유지).
  function renderEquipList() {
    var unequipped = lastParty.unequipped || [];
    var companions = lastParty.companions || [];
    var filterBox = document.getElementById('equipListFilters');
    filterBox.innerHTML = '';
    filterBox.appendChild(buildJobFilterRow(equipFilter.job, function (job) { equipFilter.job = job; renderEquipList(); }));
    filterBox.appendChild(buildPartFilterRow(equipFilter.part, function (part) { equipFilter.part = part; renderEquipList(); }));

    // [2026-09-17 2차] "장착되어있는장비도 표기해주고 누구 장착중 이렇게 목록에 나왔으면
    // 좋겠어" 요청 -- 기존엔 미착용(unequipped)만 보여줬는데, byCompanion(동료ID -> 그
    // 동료가 착용 중인 장비 목록)을 펼쳐서 착용중인 장비도 같이 섞어 보여준다. 각 항목에
    // __wearerName을 붙여서(없으면 미착용) 카드 렌더링 시 분기한다.
    var companionById = {};
    companions.forEach(function (c) { companionById[c.COMPANION_ID] = c; });
    var equippedFlat = [];
    Object.keys(lastParty.byCompanion || {}).forEach(function (cid) {
      var c = companionById[cid];
      var wearerName = c ? (c.NAME || JOB_KR[c.CLASS] || c.CLASS) : '?';
      (lastParty.byCompanion[cid] || []).forEach(function (e) {
        var copy = Object.assign({}, e);
        copy.__wearerName = wearerName;
        equippedFlat.push(copy);
      });
    });
    var allItems = unequipped.concat(equippedFlat);

    // [2026-09-17] 무기 통합 후 e.CLASS가 SWORD 등 무기군일 수 있어, 필터 칩(전사/도적/...)을
    // 눌렀을 때 그 직업이 쓸 수 있는 무기(예: 전사 -> 검)도 같이 걸리도록 단순 동등비교 대신
    // weaponAllowedJobs로 판정한다(HELMET/ARMOR 등은 CLASS가 그대로 직업명이라 결과 동일).
    var filtered = allItems.filter(function (e) {
      if (equipFilter.job && weaponAllowedJobs(e.CLASS).indexOf(equipFilter.job) === -1) return false;
      if (equipFilter.part && e.PART !== equipFilter.part) return false;
      return true;
    });
    // [2026-09-12] "합성 가능한 장비만 합성 버튼을 띄우도록" 요청 -- 동일 클래스/부위/등급
    // 미착용 장비가 3개 이상(서버 equipSynthesis와 동일 조건) 있어야 합성이 되므로, 현재
    // 필터와 무관하게 unequipped 전체를 기준으로 (클래스|부위|등급) 조합별 개수를 세어둔다.
    var synthCountByKey = {};
    unequipped.forEach(function (e) {
      var key = e.CLASS + '|' + e.PART + '|' + e.GRADE;
      synthCountByKey[key] = (synthCountByKey[key] || 0) + (e.QTY || 1); // [2026-10-22] 수량(QTY) 기준
    });
    function canSynth(e) {
      return e.GRADE < 6 && synthCountByKey[e.CLASS + '|' + e.PART + '|' + e.GRADE] >= 3;
    }
    // idx(N번)는 서버(BotS5Service.equipWear/equipSynthesis)가 계산하는 "미착용 장비 번호"와
    // 반드시 같은 순서여야 하므로, 필터링/그룹핑은 화면 표시용일 뿐 idx 자체(e.__idx, 이미
    // loadPartyAndEquip에서 필터 전 원본 순서로 매겨둠)는 그대로 쓴다.
    // [2026-09-17] 무기 통합 후 그룹 키(e.CLASS)가 직업명(WARRIOR 등)일 수도, 무기군(SWORD 등)
    // 일 수도 있다 -- JOB_ORDER만 순회하면 무기 그룹이 어디에도 안 잡혀서 목록에서 통째로
    // 사라지므로, 무기군 3종을 순서 뒤에 이어붙인다.
    var grouped = {};
    filtered.forEach(function (e) { (grouped[e.CLASS] = grouped[e.CLASS] || []).push(e); });
    var GROUP_ORDER = JOB_ORDER.concat(['SWORD', 'STAFF', 'BOW', 'PLATE', 'ROBE', 'JACKET', 'HELM', 'HEADBAND', 'PLUME', 'COMMON']);
    var jobsInOrder = GROUP_ORDER.filter(function (job) { return grouped[job]; });

    var box = document.getElementById('equipListBox');
    box.innerHTML = '';
    dismantleItems = {};
    if (filtered.length === 0) {
      box.innerHTML = '<div class="sheet-empty">' + (allItems.length === 0 ? '보유한 장비가 없습니다.' : '조건에 맞는 장비가 없습니다.') + '</div>';
      return;
    }
    jobsInOrder.forEach(function (job) {
      // [2026-09-07] "성급별 무기별 정렬해줘, 지금은 섞여있다" 요청 -- 부위(무기→투구→갑옷)로
      // 먼저 묶고 그 안에서 성급 내림차순. 예전엔 성급만으로 정렬해서 같은 직업 안에서
      // 무기/투구/갑옷이 서로 섞여 나왔다.
      var list = grouped[job].slice().sort(function (a, b) {
        var pa = PART_ORDER.indexOf(a.PART), pb = PART_ORDER.indexOf(b.PART);
        if (pa !== pb) return pa - pb;
        return b.GRADE - a.GRADE;
      });
      var title = document.createElement('div');
      title.className = 'equip-group-title';
      var groupLabel = job === 'COMMON' ? '공용 악세서리' : (WEAPON_CLASS_NAME[job] || JOB_KR[job] || job);
      title.textContent = groupLabel + equipGroupJobSuffix(job) + ' (' + list.reduce(function (n, e) { return n + (e.QTY || 1); }, 0) + ')';
      box.appendChild(title);

      var grid = document.createElement('div');
      grid.className = 'equip-grid';
      list.forEach(function (e) {
        var idx = e.__idx;
        var card = document.createElement('div');
        card.className = 'equip-card';
        // [2026-09-17] "★6무기 라고만 되어있는데 ★6검/지팡이/활로 각각 표시해달라" 요청(갑옷/
        // 투구/악세서리 통합까지 확장) -- 그룹 헤더는 이미 구분되지만 카드 자체는 부위명만
        // 찍혀서 카드 하나만 보고는 어느 그룹인지 알 수 없었다. 그룹 통합된 CLASS(검/갑주/
        // 공용 등)일 때만 그 이름을, 아직 마이그레이션 전(구 데이터, class=직업명)이면 기존
        // 처럼 부위명 그대로.
        var isGroupedClass = !!WEAPON_CLASS_NAME[e.CLASS] || e.CLASS === 'COMMON';
        // [2026-09-21] ★7 전설장비는 부위 일반명 대신 고유이름을 보여준다.
        var eqLabel = e.LEGENDARY_ITEM_NAME ? ('✨ ' + e.LEGENDARY_ITEM_NAME + (e.ENHANCE_LEVEL > 0 ? ' +' + e.ENHANCE_LEVEL : ''))
            : (isGroupedClass ? equipClassLabel(e.CLASS, e.PART) : (PART_KR[e.PART] || e.PART));
        var actionsHtml;
        if (e.__wearerName) {
          // [2026-09-17 2차] "장착되어있는장비도 표기해주고 누구 장착중 이렇게 나왔으면
          // 좋겠어" 요청 -- 착용중인 장비는 장착/합성 버튼 대신 착용자 이름 + 해제 버튼.
          card.classList.add('worn');
          actionsHtml = '<div class="eq-worn-by">👤 ' + e.__wearerName + ' 장착중</div>'
              + '<div class="btn-group"><button onclick="TW.action(\'EQUIP_UNWEAR_ONE\',\'' + e.EQUIP_ID + '\')">해제</button></div>';
        } else {
          // [2026-09-12] 합성 재료(동일 클래스/부위/등급 3개 이상)가 안 되는 장비는 눌러도
          // 항상 실패 응답만 오던 "합성" 버튼 자체를 아예 숨긴다.
          var synthBtn = canSynth(e)
              ? '<button class="ten" onclick="TW.action(\'EQUIP_SYNTH\',\'' + idx + '\')">합성</button>'
              : '';
          // [2026-09-21] "장비목록에서 말고, 장비제작소 안에 장비해체 버튼을 누르면 어떤장비
          // 해체할지 띄우는방식으로하자" 요청으로 여기(장비 카드)의 개별 분해 버튼은 제거하고
          // 상점 탭 전설제작 카드의 "장비해체" 버튼 -> 선택 팝업으로 옮김(openLegendDisenchant).
          // [2026-10-23] ★6 이하 일반 장비/악세는 인벤토리에서 바로 분해(수량 선택 팝업, 확률로 전설의조각).
          var dmBtn = '';
          if (!e.LEGENDARY_ID && e.GRADE <= 6) {
            dismantleItems[e.EQUIP_ID] = { id: e.EQUIP_ID, grade: e.GRADE, qty: e.QTY || 1, label: eqLabel };
            dmBtn = '<button class="ghost" onclick="TW.openDismantle(\'' + e.EQUIP_ID + '\')">분해</button>';
          }
          actionsHtml = '<div class="btn-group"><button onclick="TW.action(\'EQUIP_WEAR\',\'' + idx + '\')">장착</button>' + synthBtn + dmBtn + '</div>';
        }
        card.innerHTML = '<div class="eq-part">' + equipIcon(e.CLASS, e.PART) + '</div>'
            + '<div class="eq-grade">' + eqLabel + ' ★' + e.GRADE + (e.QTY > 1 ? '<span class="eq-qty">x' + e.QTY + '</span>' : '') + '</div>'
            + equipOptionHtml(e)
            + actionsHtml;
        grid.appendChild(card);
      });
      box.appendChild(grid);
    });
  }

  // 파티(동료 최대 3명)와 그 동료들에게 장착하는 장비는 한 화면에서 같이 관리한다.
  /** 전투화면(동료 HP/대표 캐릭터)을 lastParty 기준으로 다시 그린다. loadPartyAndEquip/loadPartyLight 공용. */
  function refreshBattleFromParty() {
    updateBattlePartyV1();
    if (state.battleScreenVersion !== 'V1' && state.progress && state.progress.STATUS === 'IN_COMBAT') {
      var pr = state.progress;
      var curBase = ppToBase(pr.CUR_MONSTER_HP_VALUE, pr.CUR_MONSTER_HP_EXT);
      var pct = battle.monsterBaseHp > 0 ? Math.max(0, Math.min(100, curBase / battle.monsterBaseHp * 100)) : 100;
      renderBattleV2(pr, curBase, pct, false);
    }
  }

  /** [2026-09-30] "100층 이상 유저가 공격하면 주사위 클릭 후 1초쯤 뒤에야 체력바/캐릭터가 움직인다" 개선 -- 전투 턴마다
   *  /api/tower-party + /api/tower-equip 를 전부 다시 받고 파티 슬롯/파티 격자/장비 목록(고레벨 유저는 수백 개)을 화면에
   *  안 보여도 통째로 다시 그리던 것이 병목이었다. 전투화면에 필요한 건 동료 HP뿐이라 /api/tower-party 하나만 받아서
   *  전투화면만 갱신한다(장비/파티 탭은 탭을 열 때 switchTab이 어차피 전체를 다시 불러온다). */
  function loadPartyLight() {
    var u = userName();
    if (!u) return;
    fetch(base + '/api/tower-party?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }).then(function (d) {
      lastParty.companions = d.companions || [];
      refreshBattleFromParty();
    });
  }

  // [2026-10-21 성능] 편성 탭을 열 때마다 보유 동료 전체 카드(수십 장)와 장비 목록 전체(많은 유저는 천 개 이상, DOM 약 9천 노드)를 보이지 않는 팝업에도 매번 그려서 느렸다.
  // 팝업(전체동료보기/전체아이템보기)이 열려 있을 때만 그리고, 닫혀 있으면 더럽힘 표시만 해 두었다가 열 때 그린다. 장비 기여/상세 조회(tower-party-equip-stat)도
  // 동료/장비 조회가 끝나길 기다리지 않고 동시에 보낸다.
  var gridDirty = true, equipDirty = true;
  function renderHeavyListsIfOpen() {
    if (document.getElementById('allCompanionsOverlay').classList.contains('open')) { renderPartyGrid(); gridDirty = false; } else { gridDirty = true; }
    if (document.getElementById('panel-inven').classList.contains('active')) {
      renderEquipList(); equipDirty = false;
      if (invTab === 'etc') renderInvEtc();
    } else { equipDirty = true; }
  }

  function loadPartyAndEquip() {
    var u = userName();
    if (!u) return;
    loadPartyEquipStat(); // 병렬 -- 결과는 도착하는 대로 applyPartyEquipStat()이 카드에 채운다(카드가 아직 없으면 renderPartySlots 끝에서 채움)
    return Promise.all([
      fetch(base + '/api/tower-party?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }),
      fetch(base + '/api/tower-equip?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); })
    ]).then(function (results) {
      var companions = results[0].companions || [];
      var equips = results[1].equips || [];

      // 장착된 장비를 동료별로 묶기 -- 파티 슬롯에서 "낀 장비"를 같이 보여주려면 미리 계산해둬야 한다.
      var byCompanion = {};
      var unequipped = [];
      equips.forEach(function (e) {
        if (e.EQUIPPED_COMPANION_ID != null) {
          (byCompanion[e.EQUIPPED_COMPANION_ID] = byCompanion[e.EQUIPPED_COMPANION_ID] || []).push(e);
        } else {
          unequipped.push(e);
        }
      });
      // idx(N번)는 필터링 전 원본 순서 기준으로 딱 한 번만 매긴다(renderEquipList 참고).
      unequipped.forEach(function (e, i) { e.__idx = i + 1; });
      // 캐릭터 상세 카드(showCompanionDetail)와 선택 팝업(renderPicker) 공용 캐시.
      lastParty = { companions: companions, byCompanion: byCompanion, unequipped: unequipped, tickets: results[1].tickets || [] };
      updateLegendDisenchantBtn();

      renderPartySlots();
      renderHeavyListsIfOpen();
      // 전투화면이 떠 있으면(다른 fetch보다 이게 늦게 끝난 경우) 최신 HP로 갱신. V2는 대표
      // 캐릭터 HP바가 파티 데이터에 걸려있어 state.progress(가장 최근 상태 캐시)로 재렌더.
      // [버그 수정, 2026-09-21] 파티로스터(updateBattlePartyV1)는 V1/V2 공용이라 버전과 무관하게
      // 항상 갱신해야 한다 -- V1일 때만 불렀더니 V2에서 "동료가 대표 1명만 보인다" 신고가
      // 여기서도 재발했다(updateBattleScreen 쪽 동일 버그와 같은 원인, 별도 호출 경로).
      refreshBattleFromParty();

      // 선택 팝업이 열려있으면(드물게 액션 응답 전에 다시 열렸을 경우 대비) 최신 데이터로
      // 다시 그려준다. 보통은 고르자마자 닫히므로(closePicker) 실행되지 않는다.
      if (pickerState) renderPicker();
    });
  }

  function loadShop() {
    var u = userName();
    fetch(base + '/api/tower-shop?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        document.getElementById('shopPpVal').textContent = fmtPP(data.ppValue, data.ppExt);
        var starterFree = data.freeCompanionPullsLeft || 0;
        var unlockedBlock = data.unlockedBlock || 0;
        // [정책 변경] 등급 무관 범용 뽑기권(아무 등급에나 쓸 수 있어서 하급 권으로 최상급까지
        // 뚫리던 문제)은 없앴고 항상 등급별 티어락 권만 존재한다.
        var voucherByTier = data.companionVoucherByTier || [0, 0, 0, 0]; // [티어1(하급)..티어4(최상급)]
        var equipVoucherByTier = data.equipVoucherByTier || [0, 0, 0, 0];
        // [2026-09-15] 악세서리도 동료/장비와 동일한 등급별 티어락 권.
        var accessoryVoucherByTier = data.accessoryVoucherByTier || [0, 0, 0, 0];

        var chipText = [];
        if (starterFree > 0) chipText.push('튜토리얼 무료 ' + starterFree + '회');
        var tierNames = ['하급', '중급', '상급', '최상급'];
        voucherByTier.forEach(function (n, i) {
          if (n > 0) chipText.push(tierNames[i] + ' 전용 동료뽑기권 ' + n + '장');
        });
        equipVoucherByTier.forEach(function (n, i) {
          if (n > 0) chipText.push(tierNames[i] + ' 전용 장비뽑기권 ' + n + '장');
        });
        accessoryVoucherByTier.forEach(function (n, i) {
          if (n > 0) chipText.push(tierNames[i] + ' 전용 악세뽑기권 ' + n + '장');
        });
        var freeChip = document.getElementById('shopFreeChip');
        if (chipText.length > 0) {
          freeChip.style.display = '';
          freeChip.textContent = '🎁 ' + chipText.join(' · ');
        } else {
          freeChip.style.display = 'none';
        }

        // companionGacha/equipGacha는 이제 항상 4개 전체(등급 순)가 내려온다("무료뽑기권 있으면
        // 해금 전에도 뽑을 수 있게 해달라" 요청) -- gi(0부터)가 곧 표시번호-1이자 서버가 쓰는
        // 등급(tier)이므로 절대 잘라내지 않는다. 아직 해금 안 됐고(UNLOCK_FLOOR > 내 진행도)
        // 쓸 수 있는 무료뽑기권도 없는 항목만 화면에서 숨긴다.
        var cBox = document.getElementById('companionGachaList');
        cBox.innerHTML = '';
        (data.companionGacha || []).forEach(function (g, gi) {
          // 스타터(1번) 계약서는 튜토리얼 무료도 적용, 나머지는 뽑기권만 적용
          var isFree = (gi === 0 && starterFree > 0) || (voucherByTier[gi] || 0) > 0;
          var locked = unlockedBlock < (g.UNLOCK_FLOOR || 0);
          if (locked && !isFree) return; // 해금도 안 됐고 무료뽑기권도 없으면 목록에서 숨김
          var row = document.createElement('div');
          row.className = 'shop-row';
          var singleLabel = isFree ? '무료뽑기' : '뽑기';
          // 서버(BotS5Service.gachaCompanion 등)는 이제 GACHA_ID(DB PK)가 아니라 표시번호
          // (1부터, UNLOCK_FLOOR 순 = 이 목록 순서)를 받는다 -- 채팅 명령어 /동료뽑기 N 과
          // 번호 체계를 통일하기 위함(장비뽑기는 GACHA_ID가 5~8부터 시작해서 혼란스러웠음).
          var displayNo = gi + 1;
          row.innerHTML = '<span>' + g.GACHA_NAME + ' (' + fmtPP(g.COST_VALUE, g.COST_EXT) + ' PP)'
              + (locked ? ' 🎁 해금 전, 무료뽑기권으로만 가능' : '') + '</span>'
              + '<span class="btn-group">'
              + '<button class="' + (isFree ? 'free' : '') + '" onclick="TW.action(\'GACHA_COMPANION\',\'' + displayNo + '\')">' + singleLabel + '</button>'
              + '<button class="ten" onclick="TW.action(\'GACHA_COMPANION_10\',\'' + displayNo + '\')">10연속</button>'
              + '</span>';
          cBox.appendChild(row);
        });
        var eBox = document.getElementById('equipGachaList');
        eBox.innerHTML = '';
        (data.equipGacha || []).forEach(function (g, gi) {
          var isFree = (equipVoucherByTier[gi] || 0) > 0;
          var locked = unlockedBlock < (g.UNLOCK_FLOOR || 0);
          if (locked && !isFree) return;
          var row = document.createElement('div');
          row.className = 'shop-row';
          var displayNo = gi + 1;
          row.innerHTML = '<span>' + g.GACHA_NAME + ' (' + fmtPP(g.COST_VALUE, g.COST_EXT) + ' PP)'
              + (locked ? ' 🎁 해금 전, 무료뽑기권으로만 가능' : '') + '</span>'
              + '<span class="btn-group">'
              + '<button class="' + (isFree ? 'free' : '') + '" onclick="TW.action(\'GACHA_EQUIP\',\'' + displayNo + '\')">' + (isFree ? '무료뽑기' : '뽑기') + '</button>'
              + '<button class="ten" onclick="TW.action(\'GACHA_EQUIP_10\',\'' + displayNo + '\')">10연속</button>'
              + '</span>';
          eBox.appendChild(row);
        });

        // [2026-09-15][후속] "/이벤트지급으로 악세뽑기권도 지급 가능하게" 요청으로, 장비뽑기와
        // 동일한 isFree/무료뽑기 표시 패턴으로 교체(더는 그냥 비활성화 버튼만이 아님).
        var aBox = document.getElementById('accessoryGachaList');
        aBox.innerHTML = '';
        (data.accessoryGacha || []).forEach(function (g, gi) {
          var isFree = (accessoryVoucherByTier[gi] || 0) > 0;
          var locked = unlockedBlock < (g.UNLOCK_FLOOR || 0);
          if (locked && !isFree) {
            // 잠겨 있고 쓸 수 있는 무료뽑기권도 없으면, 해금층만 안내하고 버튼은 비활성화.
            var lockedRow = document.createElement('div');
            lockedRow.className = 'shop-row';
            lockedRow.innerHTML = '<span>' + g.GACHA_NAME + ' (' + fmtPP(g.COST_VALUE, g.COST_EXT) + ' PP)'
                + ' 🔒 ' + g.UNLOCK_FLOOR + '층 마을부터</span>'
                + '<span class="btn-group"><button disabled>뽑기</button><button class="ten" disabled>10연속</button></span>';
            aBox.appendChild(lockedRow);
            return;
          }
          var row = document.createElement('div');
          row.className = 'shop-row';
          var displayNo = gi + 1;
          row.innerHTML = '<span>' + g.GACHA_NAME + ' (' + fmtPP(g.COST_VALUE, g.COST_EXT) + ' PP)'
              + (locked ? ' 🎁 해금 전, 무료뽑기권으로만 가능' : '') + '</span>'
              + '<span class="btn-group">'
              + '<button class="' + (isFree ? 'free' : '') + '" onclick="TW.action(\'GACHA_ACCESSORY\',\'' + displayNo + '\')">' + (isFree ? '무료뽑기' : '뽑기') + '</button>'
              + '<button class="ten" onclick="TW.action(\'GACHA_ACCESSORY_10\',\'' + displayNo + '\')">10연속</button>'
              + '</span>';
          aBox.appendChild(row);
        });

        // [2026-09-05] 주사위 교체는 상점탭에서 뺐다 -- '탑' 탭 보드 위 오버레이(renderDiceOverlay,
        // loadStatus에서 매번 호출)로 이동. 여기(상점탭)는 더 이상 렌더링 안 함.

        // 스탯 강화: 현재 Lv/상한 + 다음 비용, 상한 도달 시 버튼 비활성화. 다음 상한이 몇층에서
        // 열리는지도 하단에 안내("어떤 마을 가면 몇까지 올릴 수 있는지" 요청).
        var st = data.stat || {};
        var statBox = document.getElementById('statShopBox');
        statBox.innerHTML = '';
        // 레벨당 실제 증가량도 같이 보여준다("스탯구매로 인해 증가량도 표기해달라" 요청) --
        // 퍼센트 스탯(공격력최대/체력)은 "현재 +N% (다음 +M%p)", 최소공격력은 고정 데미지라
        // "현재 최소데미지 +N (다음 +M)" 형태.
        var statDefs = [
          { key: 'atkMaxLv', label: '공격력(%)', cost: st.nextCostAtkMax, type: '공격력',
            gain: '현재 +' + (st.atkPctCur || 0) + '% (다음 레벨 +' + (st.atkPctPerLv || 0) + '%p)' },
          { key: 'atkMinLv', label: '공격력(+)', cost: st.nextCostAtkMin, type: '최소공격력',
            gain: '현재 최소데미지 +' + (st.minDmgCur || 0) + ' (다음 레벨 +' + (st.minDmgPerLv || 0) + ')' },
          { key: 'hpLv',     label: '체력',         cost: st.nextCostHp,     type: '체력',
            gain: '현재 +' + (st.hpPctCur || 0) + '% (다음 레벨 +' + (st.hpPctPerLv || 0) + '%p)' }
        ];
        statDefs.forEach(function (def) {
          var lv = st[def.key] || 0;
          var cap = st.cap || 0;
          var maxed = lv >= cap;
          var row = document.createElement('div');
          row.className = 'shop-row';
          row.innerHTML = '<span><div>' + def.label + ' Lv' + lv + ' / ' + cap
              + (maxed ? ' (상한 도달)' : ' (' + def.cost + 'PP)') + '</div>'
              + '<div style="font-size:10px;color:var(--ink-soft);margin-top:2px;">' + def.gain + '</div></span>'
              + '<span class="btn-group"><button' + (maxed ? ' disabled' : '')
              + ' onclick="TW.action(\'STAT_BUY\',\'' + def.type + '\')">강화</button></span>';
          statBox.appendChild(row);
        });
        if (st.nextVillageFloor != null) {
          var note = document.createElement('div');
          note.style.cssText = 'font-size:11px;color:var(--ink-soft);margin-top:6px;';
          note.textContent = '📈 다음 상한 ' + st.nextCap + '은 ' + st.nextVillageFloor + '층 마을 도달 시 열립니다(그 앞 보스 처치 필요).';
          statBox.appendChild(note);
        }
        renderLegendaryCraftCard();
      });
  }

  // [2026-09-21] "전설제작 ui도 상점내에 만들어주고... 전설제작창에서 여러아이템 중
  // 선택하여 제작버튼을 누르면 성공&실패 가 몇초뒤 만들어지는 ui액션이 잠깐나왔다가
  // 결과가 나오게 해줘" -> "전설제작 리스트로 쭉 나오는게 아니고, 전체동료리스트처럼
  // 팝업을띄워서 거기서 선택" -> "전설제작 아래에 전설해체를 따로 만들어줘. 전설제작/전설해체
  // 모두 제작하기/해체하기 버튼 하나만 넣어주고 버튼을누르면 팝업에서 눌러서 처리할수있게
  // 해줘(팝업은전체동료보기와 동일한 ui로)" 순서로 요청이 다듬어짐 -- 최종 형태: 상점 카드는
  // 버튼 하나("제작하기"/"해체하기")만 있고, 누르면 팝업이 뜨고, 그 팝업 **안에서** 항목을
  // 누르는 즉시 실행(제작은 연출+결과까지 팝업 안에서 끝남, 해체는 즉시 실행). 로스터
  // (/api/tower-legendary-roster)는 유저 무관 고정 목록이라 한 번만 fetch해서 캐시.
  var legendaryRosterCache = null;
  // [2026-10-23] 전설 특수효과 50% 조정 -- 서버 LEGEND_EFFECT_SCALE과 같은 값으로 "실제 적용되는 수치"를 보여준다(성장형은 여기서 한 번 더 50%).
  var LEGEND_EFFECT_SCALE = 0.5;
  function lfx(v) { var r = Math.round(v * 100) / 100; return String(r); }
  function lscale(p) { return lfx((p || 0) * LEGEND_EFFECT_SCALE); }               // 확률/퍼센트
  function lmult(p, base) { return lfx(base + ((p || 0) / 100 - base) * LEGEND_EFFECT_SCALE); } // 배율의 증가분만
  var LEGEND_EFFECT_LABEL = {
    DEF_STEAL: function (p1) { return '적 방어력 무시 + 방어력의 ' + lscale(p1) + '%만큼 데미지 가산 (효과 50%)'; },
    DOUBLE_SHOT: function (p1) { return '공격 시 ' + lscale(p1) + '% 확률로 같은 눈금으로 한 번 더 연사'; },
    START_BARRIER: function () { return '전투 시작 시 ' + lscale(100) + '% 확률로 무적 보호막(다음 피해 1회 면역)을 두르고 시작'; },
    STAT_MULT: function (p1) { return '이 장비의 능력치 보너스 ' + lmult(p1, 1) + '배'; },
    CHATTER: function (p1, p2) { return '공격할 때 ' + lscale(p1) + '% 확률로 몬스터 스턴, ' + lscale(p2) + '% 확률로 내 데미지 두 배 (각각 따로 판정)'; },
    LAST_STAND: function () { return '전투당 1번, 치명적인 피해를 받으면 ' + lscale(100) + '% 확률로 HP 1로 생존 (불굴)'; },
    DICE_MIN_PLUS: function (p1) { return '전투 중 공격/보호막 주사위의 최소 눈 +' + p1 + ' (' + lscale(100) + '% 확률로 적용)'; },
    CRIT_DAMAGE: function (p1) { return '크리티컬 피해 배율 x1.5 -> x' + lmult(p1, 1.5); },
    DAMAGE_REDUCTION: function (p1) { return '착용한 동료가 받는 피해 ' + lscale(p1) + '% 감소'; },
    EVADE_CHANCE: function (p1) { return '착용한 동료가 맞을 때 ' + lscale(p1) + '% 확률로 피해 무효'; },
    KILL_PP_BONUS: function (p1) { return '파티에 착용한 동료가 있으면 몬스터 처치 PP +' + lscale(p1) + '%'; },
    SKILL_RATE_MULT: function (p1) { var m = 1 + ((p1 || 100) / 100 - 1) * LEGEND_EFFECT_SCALE; return '장착한 캐릭터의 스킬 발동 확률 ' + lfx(m) + '배 (예: ★6 도사 부활 15% -> ' + Math.round(15 * m) + '%). ★7 동료 보너스(x1.2)와는 곱하지 않고 증가분을 합산하며, 증가한 확률은 최대 90%까지'; }
  };

  function legendItemSummaryHtml(item) {
    var effectFn = LEGEND_EFFECT_LABEL[item.EFFECT_TYPE];
    var effectLabel = effectFn ? effectFn(item.EFFECT_PARAM1, item.EFFECT_PARAM2) : (item.EFFECT_TYPE || '');
    var groupLabel = WEAPON_CLASS_NAME[item.CLASS] || (item.CLASS === 'COMMON' ? '공용' : item.CLASS);
    return '<div class="lri-icon">' + equipIcon(item.CLASS, item.PART) + '</div>'
        + '<div class="lri-body">'
        + '<div class="lri-name">★7 ' + groupLabel + ' — ' + item.ITEM_NAME + '</div>'
        + '<div class="lri-meta">' + effectLabel + '</div>'
        + (item.FLAVOR_TEXT ? '<div class="lri-flavor">' + item.FLAVOR_TEXT + '</div>' : '')
        + '</div>';
  }

  function ensureLegendaryRosterLoaded(cb) {
    if (legendaryRosterCache) { cb(legendaryRosterCache); return; }
    fetch(base + '/api/tower-legendary-roster')
      .then(function (r) { return r.json(); })
      .then(function (data) {
        legendaryRosterCache = data.roster || [];
        cb(legendaryRosterCache);
      })
      .catch(function () { cb([]); });
  }

  // 상점 탭이 처음 그려질 때(또는 새로고침될 때) 호출 -- 조각 보유량 배지 갱신 + 10개
  // 미만이면 "제작하기" 버튼 자체를 비활성화("10개가 있어야 누를수있게 해줘" 요청). 로스터는
  // 미리 캐시해둔다(팝업을 처음 열 때 기다리지 않도록).
  var LEGEND_CRAFT_COST_UI = 10;
  // [2026-09-29] "전설해체는 해체가능한 전설이 있을때만 버튼이 활성화되게(미착용상태 전설보유)"
  // 요청 -- 해체 팝업(openLegendDisenchant)과 같은 조건(미착용 ★7)으로 버튼 활성/비활성.
  // 파티/장비 데이터(lastParty)가 로드되거나 상점 카드가 갱신될 때마다 호출.
  function updateLegendDisenchantBtn() {
    var btn = document.getElementById('legendDisenchantBtn');
    if (!btn) return;
    btn.disabled = !(lastParty.unequipped || []).some(function (e) { return e.GRADE === 7; });
    var eb = document.getElementById('legendEnhanceBtn');
    if (eb) eb.disabled = allLegendaryEquips().length === 0;
  }

  function renderLegendaryCraftCard() {
    updateLegendDisenchantBtn();
    var frag = (state.progress && state.progress.LEGEND_FRAGMENT) || 0;
    var fragVal = document.getElementById('legendCraftFragVal');
    if (fragVal) fragVal.textContent = frag;
    var btn = document.getElementById('legendCraftBtn');
    if (btn) btn.disabled = frag < LEGEND_CRAFT_COST_UI;
    ensureLegendaryRosterLoaded(function () {});
  }

  function openLegendRoster() {
    // 성공률 = 기본 30% + 연속 실패 1회당 10%p(서버 LEGEND_PITY, 100% 상한).
    var pity = (state.progress && state.progress.LEGEND_PITY) || 0;
    var chance = Math.min(100, 30 + pity * 10);
    document.getElementById('legendRosterTitle').textContent = '제작할 전설장비 선택 (성공률 ' + chance + '%'
        + (pity > 0 ? ', 연속 실패 보너스 +' + (pity * 10) + '%p' : '') + ')';
    document.getElementById('legendRosterOverlay').classList.add('open');
    document.getElementById('legendCraftAnim').style.display = 'none';
    document.getElementById('legendCraftResult').style.display = 'none';
    ensureLegendaryRosterLoaded(function (roster) { renderLegendRosterList(roster); });
  }
  function renderLegendRosterList(roster) {
    var list = document.getElementById('legendRosterOverlayList');
    list.style.display = '';
    if (!roster.length) {
      list.innerHTML = '<div class="sheet-empty">아직 등록된 전설장비가 없습니다.</div>';
      return;
    }
    list.innerHTML = '';
    roster.forEach(function (item) {
      var el = document.createElement('div');
      el.className = 'legend-roster-item';
      el.innerHTML = legendItemSummaryHtml(item) + '<div class="lri-arrow">›</div>';
      el.onclick = function () { attemptCraft(item); };
      list.appendChild(el);
    });
  }
  function closeLegendRoster() {
    document.getElementById('legendRosterOverlay').classList.remove('open');
  }

  // 팝업 안에서 항목을 누른 즉시 실행 -- 목록을 감추고 "제작 중..." 연출(최소
  // LEGEND_CRAFT_ANIM_MS 유지) 후 성공/실패 결과를 같은 팝업 안에 보여준다.
  var LEGEND_CRAFT_ANIM_MS = 1800;
  function attemptCraft(item) {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    var list = document.getElementById('legendRosterOverlayList');
    var anim = document.getElementById('legendCraftAnim');
    var result = document.getElementById('legendCraftResult');
    list.style.display = 'none';
    result.style.display = 'none';
    anim.style.display = '';

    var startedAt = Date.now();
    var url = base + '/api/tower-action?userName=' + encodeURIComponent(u) + '&type=CRAFT_LEGENDARY&param1='
        + encodeURIComponent(item.LEGENDARY_ID) + '&param2=';
    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      var elapsed = Date.now() - startedAt;
      var wait = Math.max(0, LEGEND_CRAFT_ANIM_MS - elapsed);
      setTimeout(function () {
        anim.style.display = 'none';
        var msg = data.message || data.error || '완료';
        var success = msg.indexOf('제작 성공') !== -1;
        var fail = msg.indexOf('제작 실패') !== -1;
        result.className = 'legend-craft-result' + (success ? ' success' : fail ? ' fail' : '');
        result.textContent = msg;
        result.style.display = '';
        loadStatus(); // 조각 보유량(LEGEND_FRAGMENT) 갱신
      }, wait);
    }).catch(function () {
      anim.style.display = 'none';
      list.style.display = '';
      toast('요청 실패');
    });
  }

  // [2026-09-21] "장비제작소 안에 장비해체 버튼을 누르면 어떤장비해체할지 띄우는방식으로
  // 하자" 요청 -- 보유(미착용) ★7 전설장비만 골라서 팝업 목록으로 보여주고, 각 항목의
  // "해체" 버튼을 누르면 바로 실행(조각 9개 환급).
  // [2026-10-22] 해체가 첫 번째만 되고 나머지는 "★7만 분해 가능"으로 거절되던 문제 -- 팝업이 상점 탭에 있어서 해체 후 목록이 갱신되지 않은 채
  // "미착용 N번"을 보내 다른 장비를 가리켰다. 열 때마다 목록을 새로 받아오고, 해체는 EQUIP_ID로 보낸다(DISENCHANT_LEGENDARY_ID).
  function openLegendDisenchant() {
    var overlay = document.getElementById('legendDisenchantOverlay');
    var list = document.getElementById('legendDisenchantList');
    list.innerHTML = '<div class="sheet-empty">불러오는 중...</div>';
    overlay.classList.add('open');
    Promise.resolve(loadPartyAndEquip()).then(renderLegendDisenchant, renderLegendDisenchant);
  }
  function renderLegendDisenchant() {
    var overlay = document.getElementById('legendDisenchantOverlay');
    var list = document.getElementById('legendDisenchantList');
    var legendItems = (lastParty.unequipped || []).filter(function (e) { return e.GRADE === 7 && e.LEGENDARY_ID != null; });
    if (!legendItems.length) {
      list.innerHTML = '<div class="sheet-empty">해체할 수 있는 ★7 전설장비(미착용)가 없습니다.</div>';
    } else {
      list.innerHTML = '';
      legendItems.forEach(function (e) {
        var el = document.createElement('div');
        el.className = 'legend-roster-item';
        var groupLabel = WEAPON_CLASS_NAME[e.CLASS] || (e.CLASS === 'COMMON' ? '공용' : e.CLASS);
        el.innerHTML = '<div class="lri-icon">' + equipIcon(e.CLASS, e.PART) + '</div>'
            + '<div class="lri-body"><div class="lri-name">✨ ' + (e.LEGENDARY_ITEM_NAME || ('★7 ' + groupLabel)) + (e.ENHANCE_LEVEL > 0 ? ' +' + e.ENHANCE_LEVEL : '') + '</div></div>'
            + '<button class="legend-disenchant-item-btn" type="button">🧩 해체(+15' + (e.ENHANCE_LEVEL > 0 ? ' · 🎫+' + e.ENHANCE_LEVEL + '권' : '') + ')</button>';
        el.querySelector('.legend-disenchant-item-btn').onclick = function (ev) {
          ev.stopPropagation();
          action('DISENCHANT_LEGENDARY_ID', String(e.EQUIP_ID));
          closeLegendDisenchant();
        };
        list.appendChild(el);
      });
    }
  }
  function closeLegendDisenchant() {
    document.getElementById('legendDisenchantOverlay').classList.remove('open');
  }

  // [2026-10-08] 전설장비 강화 -- 규칙은 서버(BotS5ServiceImpl)와 같아야 한다: 비용 3 + n/2, 성공률 max(30, 95 - 3n)% + 연속 실패 1회당 5%p(최대 +50), 단계당 장비 보너스 +6%.
  function enhCost(lv) { return 3 + Math.floor(lv / 2); }
  function enhRate(lv, pity) { return Math.min(100, Math.max(30, 95 - 3 * lv) + Math.min(pity || 0, 10) * 5); }
  // 내 ★7 전설장비(미착용 + 착용 중) 전부. 착용 중인 것은 __wearerName을 붙여 반환.
  function allLegendaryEquips() {
    var out = (lastParty.unequipped || []).filter(function (e) { return e.GRADE === 7 && e.LEGENDARY_ID != null; });
    var companionById = {};
    (lastParty.companions || []).forEach(function (c) { companionById[c.COMPANION_ID] = c; });
    Object.keys(lastParty.byCompanion || {}).forEach(function (cid) {
      var c = companionById[cid];
      (lastParty.byCompanion[cid] || []).forEach(function (e) {
        if (e.GRADE === 7 && e.LEGENDARY_ID != null) {
          var copy = Object.assign({}, e);
          copy.__wearerName = c ? (c.NAME || JOB_KR[c.CLASS] || c.CLASS) : '?';
          out.push(copy);
        }
      });
    });
    out.sort(function (a, b) { return a.EQUIP_ID - b.EQUIP_ID; });
    return out;
  }
  function renderLegendEnhanceList() {
    var list = document.getElementById('legendEnhanceList');
    var frag = (state.progress && state.progress.LEGEND_FRAGMENT) || 0;
    document.getElementById('legendEnhanceFrag').textContent = frag;
    var items = allLegendaryEquips();
    if (!items.length) { list.innerHTML = '<div class="sheet-empty">강화할 ★7 전설장비가 없습니다.</div>'; return; }
    list.innerHTML = '';
    // [2026-10-22] 강화권(강화된 전설을 해체하면 받음) -- 강화 수치가 권보다 낮은 장비에 쓰면 그 수치로 맞춰진다.
    var tickets = (lastParty.tickets || []).slice().sort(function (a, b) { return b.ENHANCE_LEVEL - a.ENHANCE_LEVEL; });
    if (tickets.length) {
      var tr = document.createElement('div');
      tr.className = 'ticket-row';
      tr.innerHTML = '<span>보유 강화권</span>' + tickets.map(function (t) { return '<span class="ticket-chip">🎫 +' + t.ENHANCE_LEVEL + '</span>'; }).join('');
      list.appendChild(tr);
    }
    items.forEach(function (e) {
      var lv = e.ENHANCE_LEVEL || 0;
      var cost = enhCost(lv);
      var el = document.createElement('div');
      el.className = 'legend-roster-item';
      el.innerHTML = '<div class="lri-icon">' + equipIcon(e.CLASS, e.PART) + '</div>'
          + '<div class="lri-body"><div class="lri-name">✨ ' + e.LEGENDARY_ITEM_NAME + ' <b>+' + lv + '</b></div>'
          + '<div class="lri-meta">' + (e.__wearerName ? ('👤 ' + e.__wearerName + ' 장착중 · ') : '') + '성능 +' + (lv * 6) + '% · 성공률 ' + enhRate(lv, e.ENHANCE_PITY) + '%</div></div>'
          + '<button class="legend-disenchant-item-btn" type="button"' + (frag < cost ? ' disabled' : '') + '>🧩' + cost + ' 강화</button>';
      el.querySelector('button').onclick = function (ev) { ev.stopPropagation(); doLegendEnhance(e, el.querySelector('button')); };
      var usable = tickets.filter(function (t) { return t.ENHANCE_LEVEL > lv; })[0]; // 쓸 수 있는 것 중 가장 높은 권
      if (usable) {
        var tb = document.createElement('button');
        tb.type = 'button';
        tb.className = 'legend-disenchant-item-btn legend-ticket-btn';
        tb.textContent = '🎫 +' + usable.ENHANCE_LEVEL + ' 적용';
        tb.onclick = function (ev) { ev.stopPropagation(); doUseTicket(usable, e, tb); };
        el.appendChild(tb);
      }
      list.appendChild(el);
    });
  }
  function doLegendEnhance(e, btn) {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    btn.disabled = true;
    var url = base + '/api/tower-action?userName=' + encodeURIComponent(u) + '&type=ENHANCE_LEGENDARY&param1=' + encodeURIComponent(e.EQUIP_ID) + '&param2=';
    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      var msg = data.message || data.error || '완료';
      var result = document.getElementById('legendEnhanceResult');
      result.className = 'legend-craft-result' + (msg.indexOf('강화 성공') !== -1 ? ' success' : msg.indexOf('강화 실패') !== -1 ? ' fail' : '');
      result.textContent = formatMsg(msg); // 서버 메시지의 ♬(NL)를 줄바꿈으로
      result.style.whiteSpace = 'pre-line';
      result.style.display = '';
      return Promise.all([Promise.resolve(loadStatus()), Promise.resolve(loadPartyAndEquip())]);
    }).then(function () { renderLegendEnhanceList(); })
      .catch(function () { toast('요청 실패'); renderLegendEnhanceList(); });
  }
  function doUseTicket(t, e, btn) {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    btn.disabled = true;
    var url = base + '/api/tower-action?userName=' + encodeURIComponent(u) + '&type=USE_ENHANCE_TICKET&param1=' + encodeURIComponent(t.TICKET_ID) + '&param2=' + encodeURIComponent(e.EQUIP_ID);
    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      var result = document.getElementById('legendEnhanceResult');
      result.className = 'legend-craft-result' + ((data.message || '').indexOf('강화권 사용') !== -1 ? ' success' : '');
      result.textContent = formatMsg(data.message || data.error || '완료');
      result.style.whiteSpace = 'pre-line';
      result.style.display = '';
      return loadPartyAndEquip();
    }).then(function () { renderLegendEnhanceList(); })
      .catch(function () { toast('요청 실패'); renderLegendEnhanceList(); });
  }
  function openLegendEnhance() {
    document.getElementById('legendEnhanceResult').style.display = 'none';
    renderLegendEnhanceList();
    document.getElementById('legendEnhanceOverlay').classList.add('open');
  }
  function closeLegendEnhance() {
    document.getElementById('legendEnhanceOverlay').classList.remove('open');
  }

  // ==================== ★7 동료 소환/방출 (2026-10-20) ====================
  // 규칙은 서버(BotS5ServiceImpl.star7Status/summonStar7/releaseStar7)가 판단하고, 여기선 현황 표시와 직업 선택만 한다.
  var s7Data = null, s7Sel = null, s7ReleaseArmed = false;
  function openStar7() {
    document.getElementById('star7Result').style.display = 'none';
    s7Sel = null; s7ReleaseArmed = false;
    renderStar7();
    document.getElementById('star7Overlay').classList.add('open');
  }
  function closeStar7() { document.getElementById('star7Overlay').classList.remove('open'); }
  function renderStar7() {
    var box = document.getElementById('star7Body');
    var u = userName();
    if (!u) { box.innerHTML = '<div class="sheet-empty">유저명을 입력하세요.</div>'; return; }
    box.innerHTML = '<div class="sheet-empty">불러오는 중...</div>';
    fetch(base + '/api/tower-star7?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }).then(function (d) {
      s7Data = d;
      if (d.error) { box.innerHTML = '<div class="sheet-empty">' + d.error + '</div>'; return; }
      var html = '';
      if (d.owned) {
        html += '<div class="s7-intro">이미 ★7 동료를 보유 중입니다: <b>' + d.ownedName + '</b> (' + d.ownedJob + '). ★7은 1명만 가질 수 있어요. 방출하면 장비가 모두 벗겨지고(장비는 사라지지 않음), 직업을 다시 골라 소환할 수 있습니다.</div>'
            + '<button type="button" class="legend-craft-btn" id="star7Release" ' + (d.canRelease ? '' : 'disabled ') + 'onclick="TW.star7Release()">'
            + (s7ReleaseArmed ? '⚠️ 정말 방출 (한 번 더 누르면 실행)' : '👋 ★7 동료 방출') + '</button>';
        box.innerHTML = html;
        return;
      }
      function req(ok, text) { return '<div class="s7-req ' + (ok ? 'ok' : 'no') + '">' + (ok ? '✅' : '❌') + ' ' + text + '</div>'; }
      html += '<div class="s7-intro">재료·전설의조각 소모 없이, 원하는 직업의 ★7 동료를 소환합니다. 능력치는 ★6 한계돌파 최대의 1.2배, 스킬 발현율은 ★6의 1.2배, 한계돌파는 없고 1인 1명입니다. 초상화는 직업마다 고정입니다.</div>';
      html += req(d.hasAchievement, '업적 <b>전설의 시작</b> <small>(한계돌파 +6 ★5 동료 ' + d.need5 + '체 보유 · 현재 ' + d.have5 + '체)</small>');
      html += '<div class="s7-jobs">';
      (d.jobs || []).forEach(function (j) {
        var face = j.portrait ? '<img class="s7-face" src="' + j.portrait + '" alt="" onerror="this.style.display=\'none\'">' : '<span class="s7-job-em">' + (JOB_EMOJI[j.key] || '') + '</span>';
        html += '<button type="button" class="s7-job" data-job="' + j.key + '" onclick="TW.star7Pick(\'' + j.key + '\')">'
            + face + '<b>' + j.name + '</b>'
            + '<small>❤️' + j.hp + ' ⚔️' + j.atk + ' 🛡️' + j.def + '</small></button>';
      });
      html += '</div><button type="button" class="legend-craft-btn" id="star7Go" disabled onclick="TW.star7Do()">직업을 선택하세요</button>';
      box.innerHTML = html;
      if (s7Sel) star7Pick(s7Sel);
    }).catch(function () { box.innerHTML = '<div class="sheet-empty">불러오기에 실패했습니다.</div>'; });
  }
  function star7Pick(job) {
    s7Sel = job;
    Array.prototype.forEach.call(document.querySelectorAll('#star7Body .s7-job'), function (b) { b.classList.toggle('sel', b.getAttribute('data-job') === job); });
    var go = document.getElementById('star7Go');
    if (!go) return;
    var ok = s7Data && s7Data.canSummon;
    go.disabled = !ok;
    go.textContent = ok ? ('🌟 ' + (JOB_KR[job] || job) + ' ★7 소환') : (s7Data && s7Data.inCombat ? '전투 중에는 소환할 수 없습니다' : '업적 [전설의 시작]이 필요합니다');
  }
  function star7Call(type, param1) {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    var url = base + '/api/tower-action?userName=' + encodeURIComponent(u) + '&type=' + type + '&param1=' + encodeURIComponent(param1 || '') + '&param2=';
    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      var msg = data.message || data.error || '완료';
      var result = document.getElementById('star7Result');
      result.className = 'legend-craft-result' + (msg.indexOf('★7 동료 소환') !== -1 || msg.indexOf('방출했습니다') !== -1 ? ' success' : ' fail');
      result.textContent = formatMsg(msg);
      result.style.whiteSpace = 'pre-line';
      result.style.display = '';
      return Promise.all([Promise.resolve(loadStatus()), Promise.resolve(loadPartyAndEquip())]);
    }).then(function () { s7ReleaseArmed = false; renderStar7(); })
      .catch(function () { toast('요청 실패'); renderStar7(); });
  }
  function star7Do() {
    if (!s7Sel) { toast('직업을 선택하세요'); return; }
    var go = document.getElementById('star7Go');
    if (go) go.disabled = true;
    star7Call('SUMMON_STAR7', s7Sel);
  }
  // 방출은 되돌릴 수 없으므로 같은 버튼을 두 번 눌러야 실행된다(첫 클릭은 경고 문구로 바뀜).
  function star7Release() {
    if (!s7ReleaseArmed) {
      s7ReleaseArmed = true;
      var b = document.getElementById('star7Release');
      if (b) b.textContent = '⚠️ 정말 방출 (한 번 더 누르면 실행)';
      return;
    }
    var b2 = document.getElementById('star7Release');
    if (b2) b2.disabled = true;
    star7Call('RELEASE_STAR7', '');
  }
  // ==================== 성장형 동료 (2026-10-20) ====================
  // 규칙(성장 배율/탐사 진행/직업 변경 가능 여부)은 서버(BotS5ServiceImpl.growthStatus)가 계산하고, 여기선 표시와 입력만 한다.
  var grData = null;
  function openGrowth() {
    document.getElementById('growthResult').style.display = 'none';
    renderGrowth();
    document.getElementById('growthOverlay').classList.add('open');
  }
  function closeGrowth() { document.getElementById('growthOverlay').classList.remove('open'); }
  function renderGrowth() {
    var box = document.getElementById('growthBody');
    var u = userName();
    if (!u) { box.innerHTML = '<div class="sheet-empty">유저명을 입력하세요.</div>'; return; }
    box.innerHTML = '<div class="sheet-empty">불러오는 중...</div>';
    fetch(base + '/api/tower-growth?userName=' + encodeURIComponent(u)).then(function (r) { return r.json(); }).then(function (d) {
      grData = d;
      if (d.error || !d.exists) { box.innerHTML = '<div class="sheet-empty">' + (d.error || '성장형 동료를 만들지 못했습니다.') + '</div>'; return; }
      var pct = Math.round(d.exploredFloors * 100 / d.exploreMax);
      var html = '<div class="gr-head"><span id="grFace"></span><div class="gr-head-info">'
          + '<div class="gr-role">☆1 · 현재 직업 <b>' + d.jobName + '</b></div>'
          + '<div class="gr-name-row"><input id="grName" class="gr-input" type="text" maxlength="10" value="' + String(d.name).replace(/"/g, '') + '">'
          + '<button type="button" class="legend-craft-btn gr-save" onclick="TW.growthSaveName()">이름 저장</button></div>'
          + '<div class="gr-sub">' + (d.partySlot ? '파티 ' + d.partySlot + '번에 편성됨' : '편성 탭에서 파티에 넣을 수 있어요') + ' · 장비는 직업과 상관없이 착용</div></div></div>';
      html += '<div class="gr-prog"><div class="gr-prog-top"><span>1~100층 완전탐사 (보스·마을 제외)</span><b>' + d.exploredFloors + ' / ' + d.exploreMax + '</b></div>'
          + '<div class="gr-bar"><i style="width:' + pct + '%"></i></div>'
          + '<div class="gr-line">성장 배율 <b>x' + d.mult + '</b> · ★6 한돌+6의 <b>' + d.ratioVsStar6Max + '배</b> <small>(풀 성장 x' + d.fullMult + ' = ★6 한돌+6의 2배)</small></div>'
          + '<div class="gr-line"><small>※ 장비 효과(전설 특수효과 포함)는 ' + (d.equipEffectPct || 50) + '%만 적용됩니다.</small></div>'
          + '<div class="gr-line">101층 이상: 계단층 ' + d.stairFloors + '개 도달 · ' + (d.stairApplied ? '<b>반영 중</b>' : '1~100층 완전탐사를 모두 마치면 일괄 반영') + '</div></div>';
      html += '<div class="gr-title">직업 선택 <small>(무료, 전투 중에는 변경 불가 · 아래는 장비 없을 때 예상 능력치)</small></div><div class="gr-jobs">';
      (d.jobs || []).forEach(function (j) {
        var cur = j.key === d.job;
        html += '<button type="button" class="gr-job' + (cur ? ' sel' : '') + '" onclick="TW.growthPickJob(\'' + j.key + '\')"' + (cur || d.inCombat ? ' disabled' : '') + '>'
            + '<span class="gr-job-em">' + (JOB_EMOJI[j.key] || '') + '</span><b>' + j.name + (cur ? ' (현재)' : '') + '</b>'
            + '<small>❤️' + fmtN(j.hp) + ' ⚔️' + fmtN(j.atk) + ' 🛡️' + fmtN(j.def) + '</small></button>';
      });
      html += '</div>';
      box.innerHTML = html;
      var face = buildAvatarEl({ COMPANION_ID: d.companionId, IMAGE_URL: d.image, CLASS: d.job, GRADE: 8 }, 'gr-face', false);
      document.getElementById('grFace').appendChild(face);
    }).catch(function () { box.innerHTML = '<div class="sheet-empty">불러오기에 실패했습니다.</div>'; });
  }
  function growthCall(type, param1) {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    var url = base + '/api/tower-action?userName=' + encodeURIComponent(u) + '&type=' + type + '&param1=' + encodeURIComponent(param1 || '') + '&param2=';
    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      var msg = data.message || data.error || '완료';
      var result = document.getElementById('growthResult');
      result.className = 'legend-craft-result' + (msg.indexOf('바꿨습니다') !== -1 ? ' success' : ' fail');
      result.textContent = formatMsg(msg);
      result.style.whiteSpace = 'pre-line';
      result.style.display = '';
      return Promise.all([Promise.resolve(loadStatus()), Promise.resolve(loadPartyAndEquip())]);
    }).then(function () { renderGrowth(); })
      .catch(function () { toast('요청 실패'); renderGrowth(); });
  }
  function growthSaveName() {
    var v = (document.getElementById('grName').value || '').trim();
    if (!v) { toast('이름을 입력하세요'); return; }
    growthCall('GROWTH_RENAME', v);
  }
  function growthPickJob(job) { growthCall('GROWTH_JOB', job); }

  // ==================== 도감 탭 (2026-10-08) ====================
  var cxMonRows = null, cxItems = null, cxBand = 'all', cxSub = 'mon';
  function cxEsc(t) { return String(t == null ? '' : t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
  function cxNum(n) { return Number(n).toLocaleString('ko-KR'); }

  // 100층 아래는 10층 단위, 100층 이후는 100층 단위 구간
  function cxBandDefs(maxFloor) {
    var defs = [], f;
    for (f = 1; f <= Math.min(100, maxFloor); f += 10) defs.push({ lo: f, hi: Math.min(f + 9, 100) });
    for (f = 101; f <= maxFloor; f += 100) defs.push({ lo: f, hi: f + 99 }); // 마지막 구간도 100층 단위(예: 401~500)로 표기
    return defs;
  }

  function loadCodex() {
    if (!cxMonRows) {
      fetch(base + '/api/tower-codex-monsters').then(function (r) { return r.json(); }).then(function (d) {
        cxMonRows = d.rows || [];
        cxBuildBands();
        cxRenderMonsters();
      }).catch(function () { document.getElementById('cxBody').innerHTML = '<tr><td colspan="8" class="c">불러오지 못했습니다.</td></tr>'; });
    }
    if (!cxItems) {
      fetch(base + '/api/tower-codex-items').then(function (r) { return r.json(); }).then(function (d) {
        cxItems = d.items || [];
        cxRenderItems();
      }).catch(function () { document.getElementById('cxItemList').innerHTML = '<div class="cx-note">불러오지 못했습니다.</div>'; });
    }
  }

  function cxBuildBands() {
    var maxFloor = cxMonRows.reduce(function (m, r) { return Math.max(m, r.floor); }, 0);
    var box = document.getElementById('cxBands');
    var html = '<button type="button" class="cx-chip on" data-b="all">전체</button>';
    cxBandDefs(maxFloor).forEach(function (b) { html += '<button type="button" class="cx-chip" data-b="' + b.lo + '-' + b.hi + '">' + b.lo + '~' + b.hi + '</button>'; });
    box.innerHTML = html;
    Array.prototype.forEach.call(box.children, function (btn) {
      btn.onclick = function () {
        cxBand = btn.getAttribute('data-b');
        Array.prototype.forEach.call(box.children, function (x) { x.classList.toggle('on', x === btn); });
        cxRenderMonsters();
      };
    });
    var inv = cxMonRows.length ? cxMonRows[0].investScale : null;
    if (inv != null) document.getElementById('cxInvest').textContent = inv;
    document.getElementById('cxFloor').oninput = cxRenderMonsters;
  }

  function cxRenderMonsters() {
    if (!cxMonRows) return;
    var q = (document.getElementById('cxFloor').value || '').trim();
    var lo = 0, hi = 99999;
    if (cxBand !== 'all') { var p = cxBand.split('-'); lo = parseInt(p[0], 10); hi = parseInt(p[1], 10); }
    var rows = cxMonRows.filter(function (r) {
      if (r.floor < lo || r.floor > hi) return false;
      return q === '' || String(r.floor) === q;
    });
    rows.sort(function (a, b) { return a.floor - b.floor; });
    document.getElementById('cxCount').textContent = rows.length + '개 층';
    var body = document.getElementById('cxBody');
    if (!rows.length) { body.innerHTML = '<tr><td colspan="8" class="c">해당하는 층이 없습니다.</td></tr>'; return; }
    body.innerHTML = rows.map(function (r) {
      return '<tr class="' + (r.kind === '보스' ? 'boss' : '') + '"><td class="c">' + r.floor + '</td><td class="c">' + cxEsc(r.kind) + '</td><td class="l">' + cxEsc(r.name) + '</td>'
          + '<td>' + cxNum(r.hp) + '</td><td>' + cxNum(r.atk) + '</td><td>' + cxNum(r.atkInvest) + '</td><td>' + cxNum(r.def) + '</td><td>' + cxEsc(r.pp) + '</td></tr>';
    }).join('');
  }

  function cxRenderItems() {
    var box = document.getElementById('cxItemList');
    if (!cxItems || !cxItems.length) { box.innerHTML = '<div class="cx-note">등록된 전설장비가 없습니다.</div>'; return; }
    box.innerHTML = cxItems.map(function (it) {
      var effFn = LEGEND_EFFECT_LABEL[it.EFFECT_TYPE];
      var effect = effFn ? effFn(it.EFFECT_PARAM1, it.EFFECT_PARAM2) : (it.EFFECT_TYPE || '');
      var group = WEAPON_CLASS_NAME[it.CLASS] || (it.CLASS === 'COMMON' ? '공용' : (JOB_KR[it.CLASS] || it.CLASS));
      var owners = it.OWNERS || 0;
      var stats = owners > 0
          ? '<span class="cx-stat">👥 보유 ' + owners + '명 (총 ' + (it.COPIES || 0) + '개)</span><span class="cx-stat">⬆️ 최고 +' + (it.MAX_ENHANCE || 0) + '강</span>'
          : '<span class="cx-stat none">👥 아직 아무도 없음</span>';
      return '<div class="cx-item"><div class="cx-item-head"><div class="lri-icon">' + equipIcon(it.CLASS, it.PART) + '</div>'
          + '<div><div class="cx-item-name">✨ ' + cxEsc(it.ITEM_NAME) + '</div><div class="cx-item-kind">★7 ' + cxEsc(group) + ' · ' + cxEsc(PART_KR[it.PART] || it.PART) + ' · No.' + it.LEGENDARY_ID + '</div></div></div>'
          + '<div class="cx-item-effect">' + cxEsc(effect) + '</div>'
          + (it.FLAVOR_TEXT ? '<div class="cx-item-flavor">' + cxEsc(it.FLAVOR_TEXT) + '</div>' : '')
          + '<div class="cx-item-stats">' + stats + '</div></div>';
    }).join('');
  }

  function cxSwitch(sub) {
    cxSub = sub;
    document.getElementById('cxMonster').style.display = sub === 'mon' ? '' : 'none';
    document.getElementById('cxItem').style.display = sub === 'item' ? '' : 'none';
    Array.prototype.forEach.call(document.getElementById('cxMain').children, function (b) { b.classList.toggle('on', b.getAttribute('data-cx') === sub); });
  }
  (function () {
    var main = document.getElementById('cxMain');
    if (main) Array.prototype.forEach.call(main.children, function (b) { b.onclick = function () { cxSwitch(b.getAttribute('data-cx')); }; });
  })();

  function loadAchievements() {
    var u = userName();
    fetch(base + '/api/tower-achievements?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        document.getElementById('achTitle').textContent = '업적 (' + data.clearedCount + '/' + data.total + ')';
        var box = document.getElementById('achList');
        box.innerHTML = '';
        var list = data.achievements || [];
        if (list.length === 0) {
          box.innerHTML = '<div class="ach-row">아직 달성한 업적이 없습니다</div>';
          return;
        }
        list.forEach(function (a) {
          var row = document.createElement('div');
          row.className = 'ach-row done';
          row.innerHTML = '<span>✅</span><span>' + a.ACH_NAME + ' - ' + a.ACH_DESC + '</span>';
          box.appendChild(row);
        });
      });
  }

  // "지나간 이전 메시지도 유저별로 볼 수 있게" 요청 -- 채팅 명령어 요청/응답 이력을 최신순 10개까지.
  function loadMessages() {
    var u = userName();
    fetch(base + '/api/tower-messages?userName=' + encodeURIComponent(u))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        var box = document.getElementById('msgList');
        box.innerHTML = '';
        var list = data.messages || [];
        if (list.length === 0) {
          box.innerHTML = '<div class="msg-row">아직 기록된 메시지가 없습니다</div>';
          return;
        }
        list.forEach(function (m) {
          // REQ/RES 둘 다 유저가 채팅으로 직접 입력/생성한 텍스트가 섞일 수 있어 innerHTML이
          // 아닌 textContent로 안전하게 채운다(다른 유저 메시지 이력을 조회할 때의 XSS 방지).
          var row = document.createElement('div');
          row.className = 'msg-row';
          var head = document.createElement('div');
          head.className = 'msg-row-head';
          var req = document.createElement('span');
          req.className = 'msg-row-req';
          req.textContent = m.REQ || '';
          var time = document.createElement('span');
          time.className = 'msg-row-time';
          time.textContent = m.INSERT_DATE || '';
          head.appendChild(req);
          head.appendChild(time);
          var res = document.createElement('div');
          res.className = 'msg-row-res';
          res.textContent = formatMsg(m.RES);
          row.appendChild(head);
          row.appendChild(res);
          box.appendChild(row);
        });
      })
      .catch(function () { document.getElementById('msgList').innerHTML = '<div class="msg-row">불러오기 실패</div>'; });
  }

  function action(type, param1, param2) {
    var u = userName();
    if (!u) { toast('유저명을 입력하세요'); return; }
    var url = base + '/api/tower-action?userName=' + encodeURIComponent(u) + '&type=' + type
        + '&param1=' + encodeURIComponent(param1 || '') + '&param2=' + encodeURIComponent(param2 || '');
    // 전투 중 공격이면 응답을 기다리지 않고 누르는 즉시 공격 모션 재생(연출일 뿐이라 판정과 정확히 동기화될 필요 없음).
    var motionPlayed = false;
    if (type === 'DICE' && state.progress && state.progress.STATUS === 'IN_COMBAT') { playBattleAttackMotion(); motionPlayed = true; }
    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      if (type === 'DICE' && data.gate) { showDiceGate(data.gate, data.message || ''); return; } // [2026-10-22] 패치 확인/전설 규칙 게이트
      toast(data.message || data.error || '완료');
      if (type === 'DICE') {
        // [2026-09-17] 전투화면이 동료 HP를 보여주려면 최신 파티 데이터가 필요한데, '편성' 탭이
        // 열려있지 않으면 refreshActivePanel()이 파티를 안 불러온다 -- 공격(DICE)만은 탭
        // 상태와 무관하게 항상 파티도 같이 새로고침. 공격 모션은 응답을 기다릴 것 없이 버튼을
        // 누른 즉시 재생(연출일 뿐이라 실제 판정 결과와 정확히 동기화될 필요는 없음).
        if (!motionPlayed) playBattleAttackMotion();
        // 전투가 끝났거나(처치/전멸) 파티/장비 탭이 열려 있거나 아직 한 번도 파티를 못 불렀으면 전체를 새로 불러오고(드랍/HP 반영),
        // 그 외 전투 턴은 전투화면에 필요한 동료 HP만 가볍게 갱신한다.
        var msgTxt = data.message || '';
        var needFull = document.getElementById('panel-party').classList.contains('active')
            || !lastParty.companions.length
            || msgTxt.indexOf('처치!') !== -1 || msgTxt.indexOf('파티 전멸') !== -1
            || msgTxt.indexOf('획득') !== -1 || msgTxt.indexOf('도망') !== -1;
        if (needFull) loadPartyAndEquip(); else loadPartyLight();
        if (msgTxt.indexOf('처치! 🎉') !== -1) showVictoryFlash();
      }
      if (type === 'DISENCHANT_LEGENDARY_ID' || type === 'DISENCHANT_LEGENDARY' || type === 'EQUIP_DISMANTLE') loadPartyAndEquip(); // [2026-10-22] 상점 탭에서도 장비 목록 갱신
      loadStatus(); // loadStatus()가 끝나면 refreshActivePanel()도 같이 불러서 중복 호출 없이 처리됨
    }).catch(function () { toast('요청 실패'); });
  }

  // [2026-09-07] 업데이트 공지/새로고침 유도 -- "새로고침 잘 안 하는 유저가 있다, 업데이트
  // 시 강제로 새로고침 유도하고 공지도 보여주고 싶다, 다시 볼 수 있는 버튼도" 요청. 서버
  // (APP_VERSION, /공지등록으로 관리자가 갱신)와 비교해서 다르면(신규 배포 감지) 새로고침
  // 안내 팝업을 띄운다. "닫기"만 눌러선 완전히 사라지진 않고 일정 시간 뒤 다시 뜬다
  // (noticeSnoozeUntil) -- 계속 미룰 순 있어도 완전히 사라지진 않게 해서 "강제" 취지를 살렸다.
  //
  // [2026-09-08] "공지가 페이지 들어갈 때마다 나온다, 유저별로 한 번씩 다시 보지 않기를
  // 누르면 이전 공지는 안 나오게 해달라" 요청 -- 기존엔 "마지막으로 본 버전"을 이 브라우저의
  // localStorage에만 저장했는데, 카톡봇 링크로 열리는 웹뷰 등 방문마다 저장소가 초기화되는
  // 환경에서는 그 기록이 전혀 남지 않아 매번 떴다. 그래서 서버(TBOT_S5_USER_PROGRESS.
  // NOTICE_SEEN_VERSION, /api/tower-notice-dismiss)에 유저명 기준으로 "다시 보지 않기"
  // 상태를 남기도록 변경 -- 이러면 기기/브라우저를 바꿔도 유지된다. 유저명을 아직 모르는
  // 시점(페이지를 막 열어서 로그인 전)에는 서버에 물어볼 기준이 없으니 기존 localStorage
  // 방식으로 폴백한다. "닫기"는 여전히 임시로만 미루고(dismiss와 다름), "다시 보지 않기"만
  // 서버에 영구 기록한다.
  var lastNotice = { version: '', notice: '', dismissed: false };
  var noticeSnoozeUntil = 0;
  var noticeCheckedUser = null; // 이 유저명으로는 이미 서버에 확인했음(loadStatus마다 재조회하지 않기 위한 가드)
  var NOTICE_STORAGE_KEY = 'loaAppVersionSeen';
  var lastNoticeCheckAt = 0;
  var NOTICE_RECHECK_MS = 10 * 60 * 1000;

  function checkAppVersion() {
    var u = userName();
    noticeCheckedUser = u;
    lastNoticeCheckAt = Date.now();
    fetch(base + '/api/tower-notice' + (u ? '?userName=' + encodeURIComponent(u) : ''))
      .then(function (r) { return r.json(); }).then(function (data) {
        lastNotice = { version: String(data.version || ''), notice: data.notice || '', dismissed: !!data.dismissed };
        if (lastNotice.dismissed) return; // 이 유저가 이 버전은 이미 "다시 보지 않기"로 닫음

        if (!u) {
          // 유저명을 아직 몰라 서버 판단 근거가 없을 때만 기존 localStorage 방식으로 폴백.
          var seen = null;
          try { seen = localStorage.getItem(NOTICE_STORAGE_KEY); } catch (e) {}
          if (seen === null) {
            // 이 브라우저에서 첫 방문 -- "업데이트됐다"고 놀라게 할 필요 없이 기준점만 조용히 저장.
            try { localStorage.setItem(NOTICE_STORAGE_KEY, lastNotice.version); } catch (e) {}
            return;
          }
          if (seen === lastNotice.version) return;
        }
        if (Date.now() > noticeSnoozeUntil) showNoticeModal();
      }).catch(function () {});
  }

  // loadStatus()는 액션마다 자주 불리므로, 유저명이 바뀌었을 때만(로그인 직후 등) 공지를
  // 다시 확인 -- 매 액션마다 /api/tower-notice를 부르지 않기 위한 가드.
  function checkAppVersionIfNewUser() {
    var u = userName();
    if (u && u !== noticeCheckedUser) checkAppVersion();
  }

  function showNoticeModal() {
    document.getElementById('noticeBody').textContent = lastNotice.notice || '새 업데이트가 있습니다. 새로고침해주세요.';
    document.getElementById('noticeOverlay').classList.add('open');
  }

  // "공지를 다시 볼 수 있는 버튼" 요청 -- 다시 보지 않기 여부와 무관하게 최신 공지를 그냥 보여줌.
  function reopenNotice() {
    var u = userName();
    fetch(base + '/api/tower-notice' + (u ? '?userName=' + encodeURIComponent(u) : ''))
      .then(function (r) { return r.json(); }).then(function (data) {
        lastNotice = { version: String(data.version || ''), notice: data.notice || '', dismissed: !!data.dismissed };
        document.getElementById('noticeBody').textContent = lastNotice.notice || '등록된 공지가 없습니다.';
        document.getElementById('noticeOverlay').classList.add('open');
      }).catch(function () { toast('공지 조회 실패'); });
  }

  function closeNotice() {
    document.getElementById('noticeOverlay').classList.remove('open');
    noticeSnoozeUntil = Date.now() + 10 * 60 * 1000; // 10분 동안은 다시 안 뜸(그 뒤엔 재확인) -- "다시 보지 않기"와 달리 임시 미루기일 뿐
  }

  // "다시 보지 않기" 버튼(2026-09-08) -- 서버에 유저별로 영구 기록, 관리자가 새 공지를
  // 등록(APP_VERSION 갱신)하기 전까지는 이 유저에게 다시 뜨지 않는다.
  function dismissNotice() {
    var u = userName();
    document.getElementById('noticeOverlay').classList.remove('open');
    noticeSnoozeUntil = Date.now() + 10 * 60 * 1000;
    try { localStorage.setItem(NOTICE_STORAGE_KEY, lastNotice.version); } catch (e) {}
    if (!u) { toast('유저명을 입력한 상태여야 "다시 보지 않기"가 저장됩니다'); return; }
    lastNotice.dismissed = true;
    fetch(base + '/api/tower-notice-dismiss?userName=' + encodeURIComponent(u)).catch(function () {});
  }

  function refreshForUpdate() {
    try { localStorage.setItem(NOTICE_STORAGE_KEY, lastNotice.version); } catch (e) {}
    location.reload();
  }

  document.addEventListener('DOMContentLoaded', function () {
    // 채팅에서 "/탑현황"·"/탑도움말"이 붙여주는 링크(?userName=닉네임)로 들어오면 그 유저로
    // 바로 조회되게 한다. 파라미터 이름이 userName이 아니어도(user_name, username 등) 웬만하면
    // 알아서 찾도록 흔한 변형을 다 확인 -- 못 찾으면 예전처럼 sessionStorage에 저장된 값으로 폴백.
    var qs = new URLSearchParams(window.location.search);
    var fromUrl = qs.get('userName') || qs.get('user_name') || qs.get('username') || qs.get('name');
    var saved = fromUrl || sessionStorage.getItem('loaUserName');
    if (saved) document.getElementById('userNameInput').value = saved;
    if (saved) loadStatus();

    checkAppVersion();
    // [2026-10-22] "이거 왜 계속 호출해? 안 호출하게" 요청 -- 3분마다 무조건 부르던 타이머(setInterval)를 없앴다.
    // 대신 다른 앱/탭에 갔다가 이 화면으로 돌아올 때만, 마지막 확인 뒤 10분이 지났으면 한 번 확인한다.
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState === 'visible' && Date.now() - lastNoticeCheckAt > NOTICE_RECHECK_MS) checkAppVersion();
    });

    // [2026-09-14] "마우스휠로 스크롤 못하게 하자" 요청으로 휠 이벤트를 막았었는데,
    // [2026-09-21 철회] "pc버전에선 스크롤이 마우스로 잘 안되고" 요청으로 다시 허용(마우스휠
    // 스크롤 차단 코드 제거, 스크롤바 드래그/터치 스와이프/맨위-내위치-맨아래 버튼은 원래도
    // 계속 됐음).
  });

  return { load: loadStatus, action: action, switchTab: switchTab, closeImageSwap: closeImageSwap, closeGate: closeGate, setInvTab: setInvTab, openMine: openMine, buyMineShop: buyMineShop,
           openDismantle: openDismantle, closeDismantle: closeDismantle, dmStep: dmStep, dmSet: dmSet, doDismantle: doDismantle, resetDockLayout: resetDockLayout, closeDetail: closeDetail, closeConfirm: closeConfirm,
           closePicker: closePicker,
           openAllCompanions: openAllCompanions, closeAllCompanions: closeAllCompanions,
           openAllEquip: openAllEquip, closeAllEquip: closeAllEquip,
           scrollToHere: scrollToHere, scrollToTop: scrollToTop, scrollToBottom: scrollToBottom,
           reopenNotice: reopenNotice, closeNotice: closeNotice, dismissNotice: dismissNotice, refreshForUpdate: refreshForUpdate,
           setBattleScreenVersion: setBattleScreenVersion,
           openLegendRoster: openLegendRoster, closeLegendRoster: closeLegendRoster,
           openLegendDisenchant: openLegendDisenchant, closeLegendDisenchant: closeLegendDisenchant,
           openLegendEnhance: openLegendEnhance, closeLegendEnhance: closeLegendEnhance,
           openGrowth: openGrowth, closeGrowth: closeGrowth, growthSaveName: growthSaveName, growthPickJob: growthPickJob,
           openStar7: openStar7, closeStar7: closeStar7, star7Pick: star7Pick, star7Do: star7Do, star7Release: star7Release,
           closeDungeon: closeDungeon };
})();
</script>
<script>
/* [2026-10-23] 웹 광산(공용 맵 여러 개, 2D 도트) -- 서버 BotS5ServiceImpl.mine*, 맵 파일 s5/*.txt(S5MineMap).
 * 지형/포탈은 서버가 준 문자열(모든 유저 같은 맵, 맵별 version으로 브라우저에 저장)을 16x16 도트 타일로 그린다. 광석은 캐 봐야 안다(서버만 앎).
 * 이동: 방향키/WASD 또는 화면 끌기(누른 곳 기준 방향으로 계속 이동). 캐기: ⛏️ 버튼/스페이스 -- 지금 서 있는 칸(한 번 캔 칸은 다시 못 캠).
 * 포탈 칸(지역 통로/동굴 입구/나가는 길/지하 문)으로 걸어 들어가면 서버가 대상 맵으로 옮겨 준다(잠긴 곳은 안내만).
 * 캐릭터 위치는 이동 중 몇 초마다, 캘 때, 포탈을 탈 때, 나갈 때(sendBeacon) 저장한다. 서버가 위치를 거절하면 서버 위치로 되돌린다. */
var TowerMine = (function () {
  var T = 16, STEP_MS = 140, SAVE_MS = 2500; // [2026-10-24] 위치 저장 4초 -> 2.5초(걷는 중에도 저장)
  // [2026-10-23] 사막(d 모래, R 사암, C 선인장, 모래바람), 바닷가(b 모래사장, P 부두, q 배, B 배에 오르는 판자), NPC(N: 대장장이/선장)
  var WALK = '.,:VHcrdbPT', PORTALS = 'GMUSB'; // [2026-10-24] 나무(T)도 지나갈 수 있다(광석은 없음)
  var WIND_MS = 900; // 모래바람에 한 칸 밀리는 간격(가만히 있을 때)
  var ORE_NAME = ['', '구리 광석', '철광석', '은광석', '금광석', '미스릴 원석', '별빛석'];
  var ORE_COL = [null, ['#b8652e', '#f0a066'], ['#7d8790', '#c9d1d8'], ['#cfd9e3', '#ffffff'], ['#f2b705', '#fff1a1'], ['#20c9b8', '#b9fff6'], ['#f23fd0', '#ffffff']];
  // [2026-10-23] 요일 던전의 돌(등급 값 11~17 = 월~일): 월 은빛 달, 화 붉은 불, 수 푸른 물, 목 초록 나무, 금 금빛, 토 흙빛, 일 주황 해
  ORE_NAME[11] = '월요석'; ORE_NAME[12] = '화요석'; ORE_NAME[13] = '수요석'; ORE_NAME[14] = '목요석'; ORE_NAME[15] = '금요석'; ORE_NAME[16] = '토요석'; ORE_NAME[17] = '일요석';
  ORE_COL[11] = ['#b9c6ff', '#ffffff']; ORE_COL[12] = ['#ff4b2e', '#ffc0a8']; ORE_COL[13] = ['#2f8cff', '#b8dcff']; ORE_COL[14] = ['#33b34a', '#c4f5c8'];
  ORE_COL[15] = ['#ffcf26', '#fff6b8']; ORE_COL[16] = ['#a8693a', '#e8c09a']; ORE_COL[17] = ['#ff8c12', '#ffe0a0'];
  ORE_NAME[18] = '칠요석'; ORE_COL[18] = ['#9b5cff', '#ffffff']; // [2026-10-24] 요일석 7종 합성
  // [2026-10-24] 보석(등급 값 21~29): 재스퍼, 루비, 스타 사파이어, 아쿠아마린, 토파즈, 가넷, 스피넬, 에메랄드, 다이아몬드
  ['재스퍼', '루비', '스타 사파이어', '아쿠아마린', '토파즈', '가넷', '스피넬', '에메랄드', '다이아몬드'].forEach(function (n, i) { ORE_NAME[21 + i] = n; });
  [['#a8452f', '#e8907a'], ['#d4104f', '#ff9ab8'], ['#2448b8', '#bcd0ff'], ['#4fc8d8', '#d4fbff'], ['#f0a530', '#ffe2a8'],
   ['#7d1426', '#e0697c'], ['#e0508e', '#ffc4dc'], ['#159a4c', '#a8f0c4'], ['#cfefff', '#ffffff']].forEach(function (c, i) { ORE_COL[21 + i] = c; });
  var RARE_GEM = { 22: 1, 23: 1, 26: 1, 28: 1, 29: 1 }; // 루비, 스타 사파이어, 가넷, 에메랄드, 다이아몬드(나오면 번쩍)
  var TERR_NAME = { '.': '풀밭', ',': '흙길', ':': '바위 지대', 'V': '마을', 'H': '다리', '~': '물', 'T': '숲', '^': '바위산',
                    'c': '동굴 바닥', 'r': '자갈 바닥', '#': '동굴 벽', 'h': '초가집', 'j': '초가집', 'k': '초가집', 'l': '초가집', 'G': '지역 통로', 'M': '동굴 입구', 'U': '나가는 길', 'S': '지하로 가는 문',
                    'd': '모래', 'R': '사암', 'C': '선인장', 'b': '모래사장', 'P': '부두', 'q': '배', 'B': '배에 오르는 판자', 'N': 'NPC' };
  var MINI_COL = { '.': [95, 174, 69], ',': [201, 164, 106], ':': [143, 133, 120], 'V': [232, 215, 168], 'H': [139, 90, 43], '~': [58, 120, 212],
                   'T': [47, 125, 50], '^': [90, 78, 68], 'c': [107, 91, 75], 'r': [125, 109, 92], '#': [30, 24, 20],
                   'h': [190, 140, 60], 'j': [190, 140, 60], 'k': [190, 140, 60], 'l': [190, 140, 60],
                   'd': [227, 196, 122], 'R': [184, 135, 90], 'C': [63, 143, 58], 'b': [236, 217, 160], 'P': [139, 90, 43], 'q': [107, 66, 32], 'N': [64, 120, 255] };

  var cfg = null, map = null, st = null, portalAt = {}, npcAt = {}, ship = null, lastWindT = 0, talkBubble = null, smithOpen = false;
  var el = {}, ctx = null, miniCtx = null, miniBase = null, scale = 3, dpr = 1;
  var tiles = null, sprites = null, hole = null, oreIcons = {};
  var P = { x: 0, y: 0, fx: 0, fy: 0, tx: 0, ty: 0, t0: 0, moving: false, dir: 'down', step: 0, digT0: 0 };
  var keys = [], tapDir = null, drag = null, dug = {}, recent = [], floats = [], flashT0 = 0, lastBump = '', lastBumpT = 0, exitSent = false;
  // [2026-10-23] 맵 이동/마을로 이동 화면 전환 -- 페이드 아웃(검게) -> 서버 이동 -> 새 맵으로 페이드 인 + 가운데 맵 이름.
  var FADE_OUT_MS = 380, FADE_IN_MS = 650, BANNER_MS = 1900;
  var fade = null, banner = null; // fade = { mode: 'out'|'in', t0 }, banner = { text, t0 }
  var raf = 0, opened = false, busy = false, lastSaved = '', saveTimer = 0, msgTimer = 0, bound = false;

  // ---------------------------------------------------------------- 도트 그리기
  function mk(w, h) { var c = document.createElement('canvas'); c.width = w; c.height = h; return c; }
  function rng(seed) {
    var s = (seed * 2654435761) >>> 0 || 1;
    return function () { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return (s % 100000) / 100000; };
  }
  function tile(base, specks, seed, extra) {
    var c = mk(T, T), g = c.getContext('2d'), r = rng(seed);
    g.fillStyle = base; g.fillRect(0, 0, T, T);
    (specks || []).forEach(function (sp) {
      g.fillStyle = sp[0];
      for (var i = 0; i < sp[1]; i++) g.fillRect(Math.floor(r() * T), Math.floor(r() * T), sp[2] || 1, sp[3] || 1);
    });
    if (extra) extra(g, r);
    return c;
  }
  function px(g, col, x, y, w, h) { g.fillStyle = col; g.fillRect(x, y, w || 1, h || 1); }
  function over(base, fn) { var c = mk(T, T), g = c.getContext('2d'); g.drawImage(base, 0, 0); fn(g); return c; }
  function grassTile(seed) { return tile('#5fae45', [['#4c9638', 16], ['#79c95a', 10], ['#3f8a2f', 5, 1, 2]], seed); }
  function cliffTile(seed) {
    return tile('#6d635a', [['#5a5149', 10]], 20 + seed, function (g, r) {
      for (var i = 0; i < 4; i++) {
        var x = Math.floor(r() * 10), y = Math.floor(r() * 10), w = 4 + Math.floor(r() * 4), h = 3 + Math.floor(r() * 3);
        px(g, '#8a7f74', x, y, w, h); px(g, '#a3998d', x, y, w, 1); px(g, '#4a423b', x, y + h, w, 1); px(g, '#4a423b', x + w, y, 1, h + 1);
      }
    });
  }
  function caveWall(seed) {
    return tile('#2a221c', [['#1d1713', 12]], 40 + seed, function (g, r) {
      for (var i = 0; i < 3; i++) {
        var x = Math.floor(r() * 11), y = Math.floor(r() * 11), w = 3 + Math.floor(r() * 4), h = 2 + Math.floor(r() * 3);
        px(g, '#3d3229', x, y, w, h); px(g, '#54453a', x, y, w, 1); px(g, '#151110', x, y + h, w, 1);
      }
    });
  }
  function outline(c, col) {
    var g = c.getContext('2d'), w = c.width, h = c.height, d = g.getImageData(0, 0, w, h), a = d.data, add = [];
    for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) {
      if (a[(y * w + x) * 4 + 3]) continue;
      var n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(function (v) {
        var nx = x + v[0], ny = y + v[1];
        return nx >= 0 && ny >= 0 && nx < w && ny < h && a[(ny * w + nx) * 4 + 3] > 0;
      });
      if (n) add.push([x, y]);
    }
    g.fillStyle = col;
    add.forEach(function (p) { g.fillRect(p[0], p[1], 1, 1); });
    return c;
  }
  function buildTiles() {
    var cliff = [cliffTile(1), cliffTile(2)], wall = [caveWall(1), caveWall(2)];
    tiles = {
      grass: [grassTile(1), grassTile(2), grassTile(3)],
      path: [tile('#c9a46a', [['#b48c55', 14], ['#dcbb82', 9], ['#9e7a48', 3]], 4), tile('#c9a46a', [['#b48c55', 14], ['#dcbb82', 9], ['#9e7a48', 3]], 5)],
      rock: [tile('#8f8578', [['#7a7064', 12], ['#a59b8d', 9], ['#5f574e', 4, 2, 1]], 6), tile('#8f8578', [['#7a7064', 12], ['#a59b8d', 9], ['#5f574e', 4, 2, 1]], 7)],
      village: tile('#d9c9a0', null, 8, function (g) {
        for (var y = 0; y < T; y += 4) for (var x = (y / 4) % 2 ? 2 : 0; x < T; x += 4) { px(g, '#c8b68a', x, y, 3, 3); px(g, '#b29f72', x + 2, y + 2, 1, 1); }
      }),
      bridge: tile('#8b5a2b', null, 9, function (g) {
        for (var y = 0; y < T; y += 4) { px(g, '#6b4220', 0, y + 3, T, 1); px(g, '#a8733d', 0, y, T, 1); px(g, '#3b2412', 3, y + 1); px(g, '#3b2412', 12, y + 1); }
      }),
      water: [0, 1].map(function (f) {
        return tile('#3a78d4', [['#2f63b3', 10]], 10 + f, function (g) {
          for (var y = 2 + f * 2; y < T; y += 5) { px(g, '#6aa4ef', (y * 3 + f * 5) % 12, y, 4, 1); px(g, '#6aa4ef', (y * 7 + 8 + f * 3) % 13, y + 2, 3, 1); }
        });
      }),
      tree: [1, 2].map(function (s) {
        var c = grassTile(s), g = c.getContext('2d');
        px(g, 'rgba(0,0,0,.25)', 3, 13, 10, 2);
        px(g, '#6b4220', 7, 10, 2, 5); px(g, '#4e2f16', 8, 10, 1, 5);
        for (var y = 1; y < 12; y++) for (var x = 2; x < 14; x++) {
          var dx = x - 7.5, dy = y - 6.2, d = dx * dx + dy * dy * 1.15;
          if (d < 30) px(g, d > 22 ? '#1b4d1f' : (dx < -1 && dy < -1 ? '#4fae52' : '#2f7d32'), x, y);
        }
        px(g, '#6fc86f', 5, 3, 2, 1); px(g, '#6fc86f', 4, 4, 1, 1);
        return c;
      }),
      cliff: cliff,
      cave: [tile('#5b4d40', [['#4f4236', 14], ['#6a5a4b', 8]], 30), tile('#5b4d40', [['#4f4236', 14], ['#6a5a4b', 8]], 31)],
      gravel: tile('#665646', [['#7d6b58', 10, 2, 1], ['#4a3e33', 8], ['#8c7a66', 5]], 32),
      wall: wall,
      // 지역 통로(돌 아치) -- 잠김: 나무 바리케이드
      gate: [false, true].map(function (locked) {
        return over(cliff[0], function (g) {
          px(g, '#9a8f84', 1, 2, 3, 14); px(g, '#9a8f84', 12, 2, 3, 14); px(g, '#b5aa9e', 1, 1, 14, 3); px(g, '#7a7066', 1, 4, 14, 1);
          px(g, '#14100d', 4, 5, 8, 11);
          if (locked) { px(g, '#8b5a2b', 4, 7, 8, 2); px(g, '#8b5a2b', 4, 11, 8, 2); px(g, '#5a3a1c', 5, 7, 1, 6); px(g, '#5a3a1c', 10, 7, 1, 6); }
        });
      }),
      // 동굴 입구(바위산에 뚫린 굴 + 나무 버팀목)
      mouth: over(cliff[1], function (g) {
        for (var y = 3; y < 16; y++) for (var x = 2; x < 14; x++) {
          var dx = x - 7.5, dy = y - 13;
          if (dx * dx / 36 + dy * dy / 100 < 1) px(g, y > 12 ? '#1a1410' : '#0b0807', x, y);
        }
        px(g, '#7a4a1f', 2, 4, 2, 12); px(g, '#7a4a1f', 12, 4, 2, 12); px(g, '#8b5a2b', 1, 3, 14, 2); px(g, '#5a3a1c', 1, 5, 14, 1);
      }),
      // 나가는 길(위로 난 빛 + 사다리)
      exit: over(wall[0], function (g) {
        px(g, '#e8d9a0', 4, 0, 8, 16); px(g, '#fff4c8', 6, 0, 4, 16);
        px(g, '#7a4a1f', 4, 0, 1, 16); px(g, '#7a4a1f', 11, 0, 1, 16);
        for (var y = 1; y < 16; y += 3) px(g, '#9c6a35', 4, y, 8, 1);
      }),
      // 지하로 가는 문(잠김: 쇠띠 + 자물쇠)
      door: [false, true].map(function (locked) {
        return over(wall[1], function (g) {
          px(g, '#3a2a1c', 2, 1, 12, 15); px(g, '#7a4a1f', 3, 2, 10, 14); px(g, '#8b5a2b', 3, 2, 10, 1);
          px(g, '#5a3a1c', 7, 2, 1, 14); px(g, '#6b6f75', 3, 5, 10, 1); px(g, '#6b6f75', 3, 11, 10, 1);
          if (locked) { px(g, '#d9b44a', 6, 8, 4, 3); px(g, '#d9b44a', 7, 6, 2, 2); px(g, '#7a5c10', 7, 9, 2, 1); }
          else px(g, '#d9b44a', 11, 9, 1, 2);
        });
      })
    };
    tiles.house = thatchedHouse(tiles.village);
    // [2026-10-23] 사막/바닷가 타일
    tiles.sand = [1, 2].map(function (s) {
      return tile('#e3c47a', [['#d2ad5c', 12], ['#f0d896', 8]], 50 + s, function (g) {
        px(g, '#cfa857', 2 + s * 3, 5, 5, 1); px(g, '#cfa857', 8 - s, 11, 6, 1); // 바람 무늬
      });
    });
    tiles.sandstone = [1, 2].map(function (s) {
      return tile('#b8875a', [['#a3744a', 10]], 60 + s, function (g, r) {
        for (var i = 0; i < 4; i++) {
          var x = Math.floor(r() * 10), y = Math.floor(r() * 10), w = 4 + Math.floor(r() * 4), h = 3 + Math.floor(r() * 3);
          px(g, '#cf9d6a', x, y, w, h); px(g, '#e2b682', x, y, w, 1); px(g, '#8a5f39', x, y + h, w, 1);
        }
      });
    });
    tiles.cactus = over(tiles.sand[0], function (g) {
      px(g, 'rgba(0,0,0,.18)', 4, 14, 9, 2);
      px(g, '#2f7d32', 7, 2, 3, 13); px(g, '#4fae52', 7, 2, 1, 13);
      px(g, '#2f7d32', 3, 6, 2, 5); px(g, '#2f7d32', 3, 10, 4, 2);
      px(g, '#2f7d32', 12, 4, 2, 5); px(g, '#2f7d32', 10, 8, 4, 2);
      px(g, '#c8f0a0', 8, 4); px(g, '#c8f0a0', 8, 9); px(g, '#ff7fa8', 8, 1);
    });
    tiles.beach = [1, 2].map(function (s) { return tile('#ecd9a0', [['#dcc488', 12], ['#f8ecc4', 8], ['#c9b07a', 2]], 70 + s); });
    tiles.plank = [false, true].map(function (locked) {
      return over(tiles.water[0], function (g) {
        px(g, '#9c6a35', 0, 5, T, 6); px(g, '#7a4a1f', 0, 5, T, 1); px(g, '#7a4a1f', 0, 10, T, 1);
        px(g, '#5a3a1c', 4, 5, 1, 6); px(g, '#5a3a1c', 11, 5, 1, 6);
        if (locked) { px(g, '#d9d0b8', 0, 3, T, 1); px(g, '#d9d0b8', 0, 12, T, 1); px(g, '#b5251f', 6, 6, 4, 4); } // 밧줄 + 빨간 표시(못 오름)
      });
    });
    tiles.npc = { smith: npcSprite('smith'), captain: npcSprite('captain') };
    hole = mk(T, T);
    var hg = hole.getContext('2d');
    for (var y = 0; y < T; y++) for (var x = 0; x < T; x++) {
      var dx = x - 7.5, dy = (y - 8) * 1.3, d = dx * dx + dy * dy;
      if (d < 34) px(hg, d > 22 ? '#6b4a30' : (d > 14 ? '#3a281b' : '#1d140d'), x, y);
    }
    px(hg, '#8a6a4a', 2, 4, 2, 1); px(hg, '#8a6a4a', 12, 11, 2, 1);
  }
  // [2026-10-23] 마을 초가집(2x2 칸) -- 32x32로 한 채를 그려 16x16 네 조각(h 왼위, j 오위, k 왼아래, l 오아래)으로 나눈다. 바닥은 마을 타일.
  function thatchedHouse(floor) {
    var S = T * 2, c = mk(S, S), g = c.getContext('2d');
    px(g, 'rgba(0,0,0,.22)', 4, 28, 25, 3);                       // 그림자
    px(g, '#9a8f80', 4, 27, 24, 2);                               // 주춧돌
    px(g, '#e8dcc0', 6, 15, 20, 12);                              // 흙벽
    px(g, '#d6c8a8', 6, 15, 20, 1);
    px(g, '#6b4a2a', 6, 15, 2, 12); px(g, '#6b4a2a', 24, 15, 2, 12); px(g, '#6b4a2a', 6, 20, 20, 1); // 기둥/인방
    px(g, '#4a2f18', 13, 19, 6, 8); px(g, '#7a5230', 14, 20, 4, 7); px(g, '#4a2f18', 16, 20, 1, 7); px(g, '#d9b44a', 17, 23, 1, 1); // 문
    px(g, '#3b2a1a', 20, 21, 3, 3); px(g, '#cdbf9e', 21, 21, 1, 3); px(g, '#cdbf9e', 20, 22, 3, 1); // 창
    for (var y = 1; y <= 16; y++) {                               // 둥근 초가지붕
      var t = (16 - y) / 15, half = Math.round(15 * Math.sqrt(Math.max(0, 1 - t * t)));
      for (var x = 16 - half; x < 16 + half; x++) {
        var col = (y % 4 === 0) ? '#a07c34' : ((x + y) % 5 === 0 ? '#c49a45' : (x < 12 && y < 10 ? '#ecd08a' : '#d9b25a'));
        px(g, col, x, y);
      }
    }
    px(g, '#8c6a2a', 1, 16, 30, 1);                               // 처마
    px(g, '#7a5a20', 15, 0, 2, 2);                                // 용마루 끝
    outline(c, '#2a1d10');
    var parts = [[0, 0], [T, 0], [0, T], [T, T]].map(function (o) {
      var p = mk(T, T), pg = p.getContext('2d');
      pg.drawImage(floor, 0, 0);
      pg.drawImage(c, o[0], o[1], T, T, 0, 0, T, T);
      return p;
    });
    return { h: parts[0], j: parts[1], k: parts[2], l: parts[3] };
  }
  // [2026-10-23] NPC 그림 -- 대장장이(가죽 앞치마 + 망치), 선장(흰 제복 + 모자 + 수염). 칸 위에 서 있는 모습(바닥은 칸 타일).
  function npcSprite(kind) {
    var c = mk(T, T), g = c.getContext('2d'), skin = '#f2c194';
    var smith = kind === 'smith';
    var body = smith ? '#7a4a1f' : '#f4f4f0', bodyD = smith ? '#5a3a1c' : '#c9ccd6', pants = smith ? '#3a2b22' : '#22305e';
    px(g, pants, 5, 12, 2, 3); px(g, pants, 9, 12, 2, 3); px(g, '#2a1d10', 5, 15, 2, 1); px(g, '#2a1d10', 9, 15, 2, 1);
    px(g, body, 4, 7, 8, 5); px(g, bodyD, 4, 7, 1, 5); px(g, bodyD, 11, 7, 1, 5);
    if (smith) { px(g, '#4a2f18', 5, 8, 6, 4); px(g, '#c49a45', 7, 9, 2, 1); } // 앞치마
    else { px(g, '#d9b44a', 7, 8, 1, 1); px(g, '#d9b44a', 7, 10, 1, 1); px(g, '#22305e', 4, 11, 8, 1); }
    px(g, skin, 3, 8, 1, 3); px(g, skin, 12, 8, 1, 3);
    px(g, skin, 4, 1, 8, 6);
    px(g, '#1a1a1a', 6, 4); px(g, '#1a1a1a', 9, 4);
    if (smith) {
      px(g, '#3b2a1a', 4, 0, 8, 2); px(g, '#3b2a1a', 4, 6, 8, 1); // 머리/수염
      px(g, '#8a8f98', 12, 4, 3, 2); px(g, '#6b4220', 13, 6, 1, 5);  // 망치
    } else {
      px(g, '#22305e', 3, 0, 10, 2); px(g, '#1a234a', 4, 2, 8, 1); px(g, '#d9b44a', 7, 0, 2, 1); // 선장 모자
      px(g, '#e8e8e8', 4, 5, 8, 2); // 흰 수염
    }
    return outline(c, '#140d0a');
  }
  // [2026-10-23] 정박한 배 -- 배 칸(q) 묶음 크기로 한 장을 그려 칸별로 잘라 쓴다(물 위에).
  function buildShip(sx, sy, w, h) {
    var c = mk(w * T, h * T), g = c.getContext('2d'), W = w * T, H = h * T;
    px(g, 'rgba(0,0,0,.25)', 4, 6, W - 4, H - 4);
    for (var y = 2; y < H - 2; y++) { // 선체(앞뒤가 둥근 길쭉한 배, 위에서 본 모습)
      var t = Math.abs((y - H / 2) / (H / 2)), half = Math.round((W / 2 - 2) * Math.sqrt(Math.max(0, 1 - Math.pow(t, 4))));
      for (var x = W / 2 - half; x < W / 2 + half; x++) {
        var edge = x < W / 2 - half + 2 || x >= W / 2 + half - 2;
        px(g, edge ? '#4a2c12' : ((y % 6 === 0) ? '#8b5a2b' : '#a8733d'), x, y);
      }
    }
    px(g, '#5a3a1c', W / 2 - 1, 6, 2, H - 12);                          // 돛대
    px(g, '#f4efe0', W / 2 - 14, H * 0.3, 28, 10); px(g, '#d9d0b8', W / 2 - 14, H * 0.3 + 9, 28, 1); // 가로 돛
    px(g, '#f4efe0', W / 2 - 10, H * 0.62, 20, 8); px(g, '#d9d0b8', W / 2 - 10, H * 0.62 + 7, 20, 1);
    px(g, '#b5251f', W / 2 - 3, 4, 6, 3);                                // 깃발
    outline(c, '#140d0a');
    return { x: sx, y: sy, w: w, h: h, img: c };
  }
  function hashXY(x, y) { var h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return h; }
  function tileFor(c, x, y, wf) {
    var v = hashXY(x, y), p;
    switch (c) {
      case '.': return tiles.grass[v % 3];
      case ',': return tiles.path[v % 2];
      case ':': return tiles.rock[v % 2];
      case 'V': return tiles.village;
      case 'H': return tiles.bridge;
      case '~': return tiles.water[(wf + (x + y) % 2) % 2];
      case 'T': return tiles.tree[v % 2];
      case 'c': return tiles.cave[v % 2];
      case 'r': return tiles.gravel;
      case '#': return tiles.wall[v % 2];
      case 'h': case 'j': case 'k': case 'l': return tiles.house[c];
      case 'G': p = portalAt[x + ',' + y]; return tiles.gate[p && !p.locked ? 0 : 1];
      case 'M': return tiles.mouth;
      case 'U': return tiles.exit;
      case 'S': p = portalAt[x + ',' + y]; return tiles.door[p && !p.locked ? 0 : 1];
      case 'd': return tiles.sand[v % 2];
      case 'R': return tiles.sandstone[v % 2];
      case 'C': return tiles.cactus;
      case 'b': return tiles.beach[v % 2];
      case 'P': return tiles.bridge;
      case 'q': return tiles.water[(wf + (x + y) % 2) % 2]; // 배 그림은 draw()에서 위에 덮는다
      case 'B': p = portalAt[x + ',' + y]; return tiles.plank[p && !p.locked ? 0 : 1];
      case 'N': // NPC 바닥: 마을이면 마을, 부두 위면 부두, 그 외 모래(NPC 그림은 draw()에서 위에)
        p = npcAt[x + ',' + y];
        return map && map.id === 'field' ? tiles.village : (p && p.kind === 'captain' ? tiles.bridge : tiles.sand[0]);
      default: return tiles.cliff[v % 2];
    }
  }

  // 캐릭터: 방향(down/up/right/left) x 걸음(0/1/2) x 캐기(0 없음, 1 들기, 2 내려치기). 왼쪽은 오른쪽을 뒤집어서.
  function charSprite(dir, step, dig) {
    if (dir === 'left') {
      var src = charSprite('right', step, dig), c2 = mk(T, T), g2 = c2.getContext('2d');
      g2.translate(T, 0); g2.scale(-1, 1); g2.drawImage(src, 0, 0);
      return c2;
    }
    var c = mk(T, T), g = c.getContext('2d');
    var skin = '#f6c9a0', hair = '#2b1d16', robe = '#3f5fd1', robeD = '#2c45a3', belt = '#7a4a1f', pants = '#3a2b22', boot = '#5b3a1e';
    var side = dir === 'right';
    var lx = side ? 6 : 5, rx = 9;
    var lh = step === 1 ? 2 : 3, rh = step === 2 ? 2 : 3;
    px(g, pants, lx, 12, 2, lh); px(g, boot, lx, 12 + lh, 2, 1);
    px(g, pants, rx, 12, 2, rh); px(g, boot, rx, 12 + rh, 2, 1);
    px(g, robe, 4, 7, 8, 5); px(g, robeD, 4, 7, 1, 5); px(g, robeD, 11, 7, 1, 5); px(g, belt, 4, 10, 8, 1);
    if (side) { px(g, robeD, 7, 8, 2, 3); px(g, skin, 8, 11); }
    else { px(g, robe, 3, 8, 1, 3); px(g, robe, 12, 8, 1, 3); px(g, skin, 3, 11); px(g, skin, 12, 11); }
    px(g, skin, 4, 1, 8, 6);
    if (dir === 'up') { px(g, hair, 4, 0, 8, 6); px(g, '#3d2a20', 6, 1, 4, 1); }
    else if (side) { px(g, hair, 4, 0, 8, 3); px(g, hair, 4, 3, 3, 3); px(g, '#1a1a1a', 10, 4); px(g, '#f29b8a', 10, 5); }
    else { px(g, hair, 4, 0, 8, 3); px(g, hair, 4, 3, 1, 2); px(g, hair, 11, 3, 1, 2); px(g, '#1a1a1a', 6, 4); px(g, '#1a1a1a', 9, 4); px(g, '#f29b8a', 5, 5); px(g, '#f29b8a', 10, 5); }
    var wood = '#8a5a2b', iron = '#c3cad1';
    if (dig === 1) { // 들어 올림
      if (side) { px(g, wood, 9, 1, 1, 7); px(g, iron, 7, 0, 5, 1); px(g, iron, 7, 1); px(g, iron, 11, 1); }
      else { px(g, wood, 12, 1, 1, 8); px(g, iron, 10, 0, 5, 1); px(g, iron, 10, 1); px(g, iron, 14, 1); }
    } else if (dig === 2) { // 내려치기
      if (side) { px(g, wood, 10, 10, 5, 1); px(g, iron, 15, 8, 1, 5); }
      else if (dir === 'up') { px(g, wood, 12, 2, 1, 6); px(g, iron, 10, 1, 5, 1); }
      else { px(g, wood, 12, 11, 1, 4); px(g, iron, 10, 15, 5, 1); }
    }
    return outline(c, '#140d0a');
  }
  function buildSprites() {
    sprites = {};
    ['down', 'up', 'right', 'left'].forEach(function (d) {
      for (var s = 0; s < 3; s++) for (var k = 0; k < 3; k++) sprites[d + s + k] = charSprite(d, s, k);
    });
  }
  // 광석 아이콘(광석 탭/광산상점에서도 씀)
  function oreIcon(grade) {
    if (oreIcons[grade]) return oreIcons[grade];
    var c = mk(T, T), g = c.getContext('2d'), col = ORE_COL[grade] || ORE_COL[1];
    if (grade >= 21) { // [2026-10-24] 보석: 깎은 보석 모양(위 테이블 + 아래 뾰족)
      for (var gy = 3; gy < 14; gy++) {
        var half = gy < 6 ? 3 + (gy - 3) : Math.max(0, 6 - Math.round((gy - 6) * 0.8));
        for (var gx = 8 - half; gx < 8 + half; gx++) px(g, gy < 6 ? col[1] : ((gx + gy) % 3 === 0 ? col[1] : col[0]), gx, gy);
      }
      px(g, '#ffffff', 6, 4, 2, 1); px(g, '#ffffff', 5, 5);
      if (grade === 23) { px(g, '#ffffff', 8, 8); px(g, '#ffffff', 7, 9, 3, 1); px(g, '#ffffff', 8, 10); } // 스타 사파이어의 별
      if (RARE_GEM[grade]) { px(g, '#ffffff', 13, 1); px(g, '#ffffff', 12, 2, 3, 1); px(g, '#ffffff', 13, 3); }
      outline(c, '#140d0a');
      oreIcons[grade] = c.toDataURL();
      return oreIcons[grade];
    }
    for (var y = 3; y < 15; y++) for (var x = 1; x < 15; x++) {
      var dx = x - 7.5, dy = (y - 9) * 1.2, d = dx * dx + dy * dy;
      if (d < 40) px(g, d > 28 ? '#5a524b' : (dy < -2 ? '#9c938a' : '#7b736b'), x, y);
    }
    var gems = [[4, 7], [9, 5], [7, 10], [11, 9], [5, 11]];
    gems.slice(0, 3 + Math.min(2, Math.floor(grade / 2))).forEach(function (p) {
      px(g, col[0], p[0], p[1], 2, 2); px(g, col[1], p[0], p[1]);
    });
    if (grade >= 5) { px(g, '#ffffff', 12, 2); px(g, '#ffffff', 11, 3, 3, 1); px(g, '#ffffff', 12, 4); }
    outline(c, '#140d0a');
    oreIcons[grade] = c.toDataURL();
    return oreIcons[grade];
  }

  // ---------------------------------------------------------------- 화면
  function $(id) { return document.getElementById(id); }
  function bindDom() {
    if (bound) return;
    bound = true;
    el.ov = $('mineOverlay'); el.cv = $('mineCanvas'); el.coord = $('mineCoord'); el.terr = $('mineTerr');
    el.sta = $('mineSta'); el.regen = $('mineRegen'); el.mini = $('mineMini'); el.dig = $('mineDigBtn');
    el.msg = $('mineMsg'); el.load = $('mineLoading'); el.log = $('mineLog'); el.smith = $('mineSmith');
    ctx = el.cv.getContext('2d'); miniCtx = el.mini.getContext('2d');
    el.cv.addEventListener('pointerdown', function (e) {
      el.cv.setPointerCapture && el.cv.setPointerCapture(e.pointerId);
      drag = { ox: e.clientX, oy: e.clientY, cx: e.clientX, cy: e.clientY };
    });
    el.cv.addEventListener('pointermove', function (e) { if (drag) { drag.cx = e.clientX; drag.cy = e.clientY; } });
    ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(function (t) { el.cv.addEventListener(t, function () { drag = null; }); });
    window.addEventListener('resize', function () { if (opened) fit(); });
    window.addEventListener('pagehide', function () { if (opened) saveBeacon(true); });
    document.addEventListener('visibilitychange', function () {
      if (!opened) return;
      if (document.visibilityState === 'hidden') saveBeacon(true);
      else if (exitSent && map) { exitSent = false; api('/api/tower-mine?enter=1').catch(function () { }); } // 다른 앱/탭에서 돌아옴 = 다시 입장
    });
    document.addEventListener('keydown', function (e) {
      if (!opened) return;
      var d = keyDir(e.key);
      if (d) { e.preventDefault(); if (keys.indexOf(d) < 0) keys.push(d); tapDir = d; return; } // 짧게 눌렀다 떼도 한 칸은 움직이게
      if (smithOpen) { if (e.key === 'Escape') closeSmith(); return; } // 대장간 창이 열려 있으면 이동/캐기 대신 창만
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); dig(); }
      else if (e.key === 'Escape') close();
    });
    document.addEventListener('keyup', function (e) {
      var d = keyDir(e.key);
      if (d) keys = keys.filter(function (k) { return k !== d; });
    });
  }
  function keyDir(k) {
    switch (k) {
      case 'ArrowUp': case 'w': case 'W': return 'up';
      case 'ArrowDown': case 's': case 'S': return 'down';
      case 'ArrowLeft': case 'a': case 'A': return 'left';
      case 'ArrowRight': case 'd': case 'D': return 'right';
    }
    return null;
  }
  function fit() {
    dpr = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    el.cv.width = Math.floor(el.cv.clientWidth * dpr);
    el.cv.height = Math.floor(el.cv.clientHeight * dpr);
    // 화면 짧은 변 기준 약 9칸이 보이도록 정수배(도트가 뭉개지지 않게)
    scale = Math.max(2, Math.round(Math.min(el.cv.width, el.cv.height) / (T * 9)));
    ctx.imageSmoothingEnabled = false;
  }
  function msg(text, ms) {
    el.msg.textContent = text;
    el.msg.style.display = 'block';
    clearTimeout(msgTimer);
    msgTimer = setTimeout(function () { el.msg.style.display = 'none'; }, ms || 2600);
  }
  function cellAt(x, y) { return map && x >= 0 && y >= 0 && x < map.w && y < map.h ? map.rows[y].charAt(x) : '^'; }
  function walk(x, y) {
    var c = cellAt(x, y);
    return WALK.indexOf(c) >= 0 || (c === '~' && !!(st && st.waterWalk)); // [2026-10-24] 용왕의 반지 = 물 위 걷기
  }
  function pad2(n) { return (n < 10 ? '0' : '') + n; }
  // [2026-10-23] 스태미너 -> 곡괭이 내구도(캘 때마다 1, 시간 회복 없음, 마을 대장장이에게 광석으로 강화 = 최대/현재 함께 증가)
  function resetText(ms) {
    if (!ms) return '';
    var d = new Date(ms);
    return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
  }
  function showStamina() {
    el.sta.textContent = '⛏️ ' + st.durability + ' / ' + st.durabilityMax;
    // [2026-10-24] 오른쪽 위 안내는 맵 리셋 시각만(대장장이 안내/보석 목록은 뺐다)
    el.regen.textContent = st.nextResetAt ? '🔄 맵 리셋 ' + resetText(st.nextResetAt) : '';
    el.dig.disabled = busy || st.durability < 1;
  }
  function showCoord() {
    el.coord.textContent = '📍 ' + P.x + ', ' + P.y;
    var c = cellAt(P.x, P.y), g = dug[P.x + ',' + P.y];
    el.terr.textContent = map.name + (st && st.dayStone ? ' (오늘 ' + st.dayLabel + ': ' + st.dayStone + ')' : '') + ' · ' + (TERR_NAME[c] || '')
        + (g ? ' · 캔 자리(' + ORE_NAME[g] + ')' : (c === 'V' ? ' · 여기선 못 캐요' : ''));
  }
  // [2026-10-23] 광부 투구 시야 -- 어두운 맵(동굴)에서 투구 단계 - 필요 단계: 0 이하 아주 어둡게 / 1 잘 보임 / 2 이상 전체(서버 mineMapBlock과 같은 기준)
  function sight() {
    if (!map || !map.dark) return 9;
    return (st && st.helmet || 0) - (map.helmetNeed || 0);
  }
  function helmetBlocked() {
    return map && (st && st.helmet || 0) < (map.helmetNeed || 0);
  }
  function buildMiniBase() {
    el.mini.width = map.w; el.mini.height = map.h;
    miniBase = mk(map.w, map.h);
    var g = miniBase.getContext('2d'), img = g.createImageData(map.w, map.h);
    var blind = sight() <= 0; // 투구 없이 어두운 동굴 -- 미니맵에도 지형이 안 보인다
    for (var y = 0; y < map.h; y++) for (var x = 0; x < map.w; x++) {
      var c = blind ? [10, 8, 6] : (MINI_COL[map.rows[y].charAt(x)] || [255, 214, 64]), i = (y * map.w + x) * 4;
      img.data[i] = c[0]; img.data[i + 1] = c[1]; img.data[i + 2] = c[2]; img.data[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
  }
  function drawMini() {
    var k = Math.max(1, Math.round(map.w / 128)); // 큰 맵은 점을 크게
    miniCtx.drawImage(miniBase, 0, 0);
    Object.keys(dug).forEach(function (key) {
      var p = key.split(','), g = dug[key];
      if (g >= 4) { miniCtx.fillStyle = ORE_COL[g][0]; miniCtx.fillRect(+p[0] - k, +p[1] - k, 2 * k + 1, 2 * k + 1); }
    });
    (map.portals || []).forEach(function (p) {
      miniCtx.fillStyle = p.locked ? '#9a9a9a' : '#ffd23f';
      miniCtx.fillRect(p.x - 2 * k, p.y - 2 * k, 4 * k + 1, 4 * k + 1);
    });
    miniCtx.fillStyle = '#ff2b2b'; miniCtx.fillRect(P.x - 3 * k, P.y - 3 * k, 6 * k + 1, 6 * k + 1);
    miniCtx.fillStyle = '#ffffff'; miniCtx.fillRect(P.x - k, P.y - k, 2 * k + 1, 2 * k + 1);
  }
  function renderLog() {
    if (!recent.length) { el.log.innerHTML = '<div class="mine-log-empty">아직 캔 곳이 없어요.</div>'; return; }
    var rows = recent.slice(0, 30).map(function (d) {
      return '<div class="mine-log-row"><span class="mine-log-xy">' + d.mapName + ' (' + d.x + ', ' + d.y + ')</span> <img src="' + oreIcon(d.grade) + '" alt=""> ' + ORE_NAME[d.grade]
          + '<span class="mine-log-when">' + digTime(d.at) + '</span></div>';
    });
    el.log.innerHTML = '<div class="mine-log-head">최근 캔 곳</div>' + rows.join('');
  }

  // ---------------------------------------------------------------- 진행
  function curPos(now) {
    if (!P.moving) return { x: P.x, y: P.y };
    var k = Math.min(1, (now - P.t0) / STEP_MS);
    return { x: P.fx + (P.tx - P.fx) * k, y: P.fy + (P.ty - P.fy) * k };
  }
  function heldDir() {
    if (keys.length) return keys[keys.length - 1];
    if (!drag) return null;
    var dx = drag.cx - drag.ox, dy = drag.cy - drag.oy;
    if (dx * dx + dy * dy < 16 * 16) return null;
    return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up');
  }
  function update(now) {
    if (P.moving && now - P.t0 >= STEP_MS) {
      P.moving = false; P.x = P.tx; P.y = P.ty;
      showCoord(); drawMini();
    }
    if (!P.moving && !busy && now - P.digT0 > 380) {
      var d = heldDir() || tapDir;
      tapDir = null;
      if (smithOpen) d = null; // 대장간 창이 열려 있는 동안은 움직이지 않는다
      if (d && helmetBlocked()) { // 광산 레벨에 비해 투구 단계가 낮으면 움직일 수 없다(마을로 버튼으로 나갈 수 있음)
        if (now - lastBumpT > 2500 || lastBump !== 'helmet') msg('⛑️ 광부 투구 ' + map.helmetNeed + '단계 이상이 있어야 움직일 수 있어요. (🏠 마을로 나갈 수 있어요)');
        lastBump = 'helmet'; lastBumpT = now;
        d = null;
      }
      if (d) {
        P.dir = d;
        var nx = P.x + (d === 'left' ? -1 : d === 'right' ? 1 : 0), ny = P.y + (d === 'up' ? -1 : d === 'down' ? 1 : 0);
        if (walk(nx, ny)) {
          P.fx = P.x; P.fy = P.y; P.tx = nx; P.ty = ny; P.t0 = now; P.moving = true; P.step = P.step === 1 ? 2 : 1;
        } else if (PORTALS.indexOf(cellAt(nx, ny)) >= 0) {
          enterPortal(nx, ny, now);
        } else if (cellAt(nx, ny) === 'N') {
          talk(npcAt[nx + ',' + ny], now);
        }
      } else if (map.wind && !smithOpen && !fade && now - lastWindT >= WIND_MS) {
        // [2026-10-23] 모래바람 -- 가만히 있으면 바람 방향(좌/우)으로 한 칸씩 밀린다(막힌 칸이면 그대로). 방향은 시각으로 정해 모두 같다.
        lastWindT = now;
        var wd = windDir(), wx = P.x + wd;
        if (walk(wx, P.y)) { P.fx = P.x; P.fy = P.y; P.tx = wx; P.ty = P.y; P.t0 = now; P.moving = true; }
      }
    }
  }
  /** 바람 방향(1 = 오른쪽, -1 = 왼쪽) -- 7초마다 바뀌는 돌풍, 기기 시각 기준이라 모든 유저가 같은 방향을 본다. */
  function windDir() {
    var ph = Math.floor(Date.now() / 7000);
    return (hashXY(ph, 7) % 2) ? 1 : -1;
  }
  /** NPC에게 말 걸기 -- 머리 위 말풍선 + 대장장이면 강화 창. */
  function talk(n, now) {
    if (!n) return;
    var key = 'npc' + n.x + ',' + n.y;
    if (lastBump === key && now - lastBumpT < 1200) return;
    lastBump = key; lastBumpT = now;
    var line = n.line, dg = st && st.dragon || {};
    if (n.kind === 'captain' && dg.has) line = '물 위를 걷는 기분은 어떤가?';
    else if (n.kind === 'captain' && dg.quest >= 1) line = '재료는 다 모았나?';
    talkBubble = { x: n.x, y: n.y, text: n.name + ': ' + line, t0: now };
    if (n.kind === 'smith') openSmith(n, 'menu');
    else if (n.kind === 'captain') openSmith(n, 'captain'); // [2026-10-24] 선장 퀘스트 창
  }
  // ---------------------------------------------------------------- 대장간(곡괭이 강화: 최대/현재 내구도 함께 증가)
  function oreHave(g) {
    var q = 0;
    (st.ores || []).forEach(function (o) { if (o.grade === g) q = o.qty; });
    return q;
  }
  // [2026-10-24] NPC 창 -- 대장장이: 메뉴 -> [구매](투구/장신구/장화) / [강화](곡괭이) / [합성](칠요석), 선장: 용왕의 반지 퀘스트.
  //  smithView = 'menu' | 'buy' | 'upgrade' | 'synth' | 'captain'
  var smithView = 'menu', captainSay = '';
  var GEAR_ICON = { ring: '💍', necklace: '📿', boots: '👢' };
  function npcHead(t, back) {
    return '<div class="mine-smith-head">' + t + ' <span>' + (back ? '<button type="button" onclick="TowerMine.smithGo(\'menu\')">뒤로</button> ' : '')
        + '<button type="button" onclick="TowerMine.closeSmith()">닫기</button></span></div>';
  }
  /** 재료 목록(아이콘 + 이름 보유/필요, 모자라면 붉게). */
  function costHtml(costs) {
    return '<span class="mine-cost">' + (costs || []).map(function (c) {
      return '<span class="' + (c.have >= c.need ? 'ok' : 'no') + '"><img src="' + oreIcon(c.grade) + '" alt="">' + c.name + ' ' + c.have + '/' + c.need + '</span>';
    }).join('') + '</span>';
  }
  function gearHtml() {
    return (st.gear || []).map(function (g) {
      var h = '<div class="mine-smith-sub">' + (GEAR_ICON[g.kind] || '💍') + ' ' + g.name + ' <b>' + (g.tier > 0 ? g.tier + '단계' : '없음') + '</b>'
          + (g.kind === 'ring' && g.tier > 0 ? (g.worn ? ' <span class="mine-worn">착용 중</span>'
              : ' <button type="button" class="mine-mini-btn" onclick="TowerMine.equipRing(\'' + g.code + '\')">끼기</button>') : '') + '</div>';
      if (g.effect) h += '<div class="mine-smith-say">지금: ' + g.effect + '</div>';
      if (!g.nextTier) return h + '<div class="mine-smith-say">최고 단계예요.</div>';
      return h + '<div class="mine-smith-row"><span class="nm">' + g.nextTier + '단계: ' + g.nextEffect + costHtml(g.costs) + '</span>'
          + '<button type="button" ' + (g.can ? '' : 'disabled ') + 'onclick="TowerMine.craft(\'' + g.code + '\')">' + (g.tier > 0 ? '올리기' : '만들기') + '</button></div>';
    }).join('') + '<div class="mine-smith-say">반지는 하나만 낄 수 있어요. 목걸이와 장화는 가지고 있으면 바로 적용돼요.</div>';
  }
  function synthHtml() {
    var stones = [];
    for (var g = 11; g <= 17; g++) stones.push({ grade: g, name: ORE_NAME[g], have: oreHave(g), need: 1 });
    var can = (st.sevenMax || 0) > 0 ? '' : 'disabled ';
    return '<div class="mine-smith-say">요일석 7종(월요석~일요석)을 하나씩 가져오면 칠요석 하나로 합쳐 주지.</div>'
        + '<div class="mine-smith-row"><img src="' + oreIcon(18) + '" alt=""><span class="nm">칠요석<small>보유 ' + oreHave(18) + '개 · 지금 ' + (st.sevenMax || 0) + '개 만들 수 있어요</small>'
        + costHtml(stones) + '</span>'
        + '<button type="button" ' + can + 'onclick="TowerMine.synth(1)">1개</button>'
        + '<button type="button" ' + can + 'onclick="TowerMine.synth(9999)">최대</button></div>';
  }
  function captainHtml(n) {
    var d = st.dragon || {}, h = npcHead('⚓ ' + (n ? n.name : '늙은 선장'), false);
    if (d.has) return h + '<div class="mine-smith-say">' + (captainSay || '허허, 물 위를 걷는 기분은 어떤가? 🌊') + '</div>';
    if (d.quest >= 1) {
      return h + '<div class="mine-smith-say">' + (captainSay || d.story || '') + '</div>'
          + '<div class="mine-smith-row"><span class="nm">🌊 용왕의 반지<small>가지고 있으면 물 위를 걸을 수 있어요</small>' + costHtml(d.costs) + '</span>'
          + '<button type="button" ' + (d.can ? '' : 'disabled ') + 'onclick="TowerMine.captain(\'craft\')">만들어 주세요</button></div>';
    }
    return h + '<div class="mine-smith-say">' + (captainSay || (n ? n.line : '')) + '</div>'
        + '<div class="mine-smith-row"><img src="' + oreIcon(4) + '" alt=""><span class="nm">금광석 ' + d.goldNeed + '개 주기<small>보유 ' + (d.gold || 0) + '개</small></span>'
        + '<button type="button" ' + ((d.gold || 0) >= d.goldNeed ? '' : 'disabled ') + 'onclick="TowerMine.captain(\'give\')">주기</button></div>';
  }
  function renderSmith(n) {
    if (smithView === 'captain') {
      el.smith.innerHTML = captainHtml(n);
      return;
    }
    if (smithView === 'menu') {
      el.smith.innerHTML = npcHead('🔨 대장간', false)
          + '<div class="mine-smith-say">' + (n ? n.line : '') + '</div>'
          + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.smithGo(\'buy\')">⛑️ 구매<small>투구·장신구·장화</small></button>'
          + '<button type="button" onclick="TowerMine.smithGo(\'upgrade\')">⛏️ 강화<small>곡괭이 내구도</small></button>'
          + '<button type="button" onclick="TowerMine.smithGo(\'synth\')">🌈 합성<small>칠요석</small></button></div>';
      return;
    }
    if (smithView === 'buy') {
      el.smith.innerHTML = npcHead('⛑️ 대장간 · 구매', true) + helmetHtml() + gearHtml();
      return;
    }
    if (smithView === 'synth') {
      el.smith.innerHTML = npcHead('🌈 대장간 · 합성', true) + synthHtml();
      return;
    }
    var rows = (st.pickUpgrades || []).map(function (r) {
      var have = oreHave(r.grade), can = have >= r.need;
      return '<div class="mine-smith-row"><img src="' + oreIcon(r.grade) + '" alt=""><span class="nm">' + r.oreName + ' ' + r.need + '개 → 최대 +' + r.gain
          + '<small>보유 ' + have + '개</small></span>'
          + '<button type="button" ' + (can ? '' : 'disabled ') + 'onclick="TowerMine.pickUpgrade(' + r.grade + ',1)">1회</button>'
          + '<button type="button" ' + (can ? '' : 'disabled ') + 'onclick="TowerMine.pickUpgrade(' + r.grade + ',9999)">최대</button></div>';
    });
    el.smith.innerHTML = npcHead('⛏️ 대장간 · 곡괭이 강화', true)
        + '<div class="mine-smith-dur">⛏️ 곡괭이 내구도 <b>' + st.durability + ' / ' + st.durabilityMax + '</b></div>'
        + rows.join('');
  }
  // [2026-10-24] 광부 투구 업그레이드(광산상점 탭에서 대장장이 창으로 옮김)
  function helmetHtml() {
    var hm = st.helmetInfo || {};
    var h = '<div class="mine-smith-dur">⛑️ 광부 투구 <b>' + (hm.tier > 0 ? hm.tier + '단계' : '없음') + '</b></div>'
        + '<div class="mine-smith-say">어두운 동굴의 시야가 넓어져요(1단계 훨씬 잘 보임, 2단계 동굴 1층 전체). 더 깊은 광산은 더 높은 단계가 있어야 움직일 수 있어요.</div>';
    if (!hm.nextTier) return h + '<div class="mine-smith-say">지금 열린 최고 단계예요.</div>';
    return h + '<div class="mine-smith-row"><img src="' + oreIcon(hm.oreGrade) + '" alt=""><span class="nm">' + hm.oreName + ' ' + hm.need + '개 → ' + hm.nextTier + '단계'
        + '<small>보유 ' + hm.have + '개</small></span><button type="button" ' + (hm.have < hm.need ? 'disabled ' : '') + 'onclick="TowerMine.helmetUpgrade()">'
        + (hm.tier > 0 ? '업그레이드' : '만들기') + '</button></div>';
  }
  function helmetUpgrade() {
    if (busy) return;
    busy = true;
    api('/api/tower-mine-helmet').then(function (d) {
      msg(d.message || d.error || '완료', 3000);
      return api('/api/tower-mine');
    }).then(function (s) {
      busy = false;
      if (s && !s.error) { st.helmet = s.helmet; st.helmetInfo = s.helmetInfo; applyGear(s); buildMiniBase(); drawMini(); }
      if (smithOpen) renderSmith(smithNpc);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  // [2026-10-24] 장비/합성/선장 응답 -- 서버가 장비 목록·광석·이동 속도·물 위 걷기를 함께 돌려준다.
  function applyGear(d) {
    if (!d) return;
    ['gear', 'dragon', 'sevenMax', 'ores', 'waterWalk', 'stepMs'].forEach(function (k) { if (d[k] != null) st[k] = d[k]; });
    STEP_MS = st.stepMs || 140;
  }
  function gearAct(path) {
    if (busy) return;
    busy = true;
    api(path).then(function (d) {
      busy = false;
      applyGear(d);
      var text = d.message || d.error || '완료';
      if (smithView === 'captain') captainSay = text; // 선장 대답은 창 안에(먹튀 대사 포함)
      else msg(text, 3200);
      if (d.craftedRing || d.questStarted) flashT0 = performance.now();
      if (smithOpen) renderSmith(smithNpc);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function craft(code) { gearAct('/api/tower-mine-craft?code=' + encodeURIComponent(code)); }
  function equipRing(code) { gearAct('/api/tower-mine-ring?code=' + encodeURIComponent(code)); }
  function synth(times) { gearAct('/api/tower-mine-synth?times=' + times); }
  function captain(action) { gearAct('/api/tower-mine-captain?action=' + action); }
  var smithNpc = null;
  function openSmith(n, view) {
    smithNpc = n;
    smithOpen = true;
    smithView = view || 'menu';
    captainSay = '';
    keys = []; drag = null;
    renderSmith(n);
    el.smith.style.display = 'block';
  }
  function smithGo(v) { smithView = v; renderSmith(smithNpc); }
  function closeSmith() {
    smithOpen = false;
    el.smith.style.display = 'none';
  }
  function pickUpgrade(grade, times) {
    if (busy) return;
    busy = true;
    api('/api/tower-mine-pick-upgrade?grade=' + grade + '&times=' + times).then(function (d) {
      busy = false;
      if (d.durability != null) { st.durability = d.durability; st.durabilityMax = d.durabilityMax; }
      if (d.ores) st.ores = d.ores;
      showStamina();
      if (smithOpen) renderSmith(smithNpc);
      msg(d.message || d.error || '강화하지 못했어요.', 2800);
    }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  /** 화면을 검게 덮으면서(FADE_OUT_MS) 서버 요청 work()를 함께 보내고, 둘 다 끝나면 응답을 넘긴다. */
  function fadeOutThen(work) {
    fade = { mode: 'out', t0: performance.now() };
    var wait = new Promise(function (r) { setTimeout(r, FADE_OUT_MS); });
    return Promise.all([work(), wait]).then(function (res) { return res[0]; });
  }
  function fadeIn(label) {
    fade = { mode: 'in', t0: performance.now() };
    if (label) banner = { text: label, t0: performance.now() };
  }
  /** 이동 요청 공통 -- 성공하면 새 상태로 바꾼 뒤 페이드 인, 실패하면 원래 화면으로 페이드 인. */
  function travel(path, failText) {
    busy = true;
    fadeOutThen(function () { return api(path); }).then(function (d) {
      if (d.ok && d.state) {
        return enterState(d.state).then(function () { fadeIn('🗺️ ' + map.name); msg(map.wind ? '🌪️ 모래바람이 불어요! 가만히 있어도 옆으로 밀려나요.' : (d.message || ''), 2600); });
      }
      busy = false;
      if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
      if (d.reload) return reload();
      fadeIn(null);
      msg(d.message || d.error || failText);
    }).catch(function () { busy = false; fadeIn(null); msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function enterPortal(px2, py2, now) {
    var p = portalAt[px2 + ',' + py2];
    if (!p) return;
    if (p.locked) {
      var key = px2 + ',' + py2;
      if (lastBump !== key || now - lastBumpT > 2500) msg('🔒 ' + p.label + ' -- 아직 갈 수 없어요. (준비 중)');
      lastBump = key; lastBumpT = now;
      return;
    }
    travel('/api/tower-mine-warp?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&px=' + px2 + '&py=' + py2 + verQ(), '들어갈 수 없어요.');
  }
  function draw(now) {
    var W = el.cv.width, H = el.cv.height, TS = T * scale, pos = curPos(now);
    var camX = Math.round(pos.x * TS + TS / 2 - W / 2), camY = Math.round(pos.y * TS + TS / 2 - H / 2);
    ctx.fillStyle = '#0b0b10';
    ctx.fillRect(0, 0, W, H);
    var x0 = Math.floor(camX / TS), y0 = Math.floor(camY / TS), x1 = Math.ceil((camX + W) / TS), y1 = Math.ceil((camY + H) / TS);
    var wf = Math.floor(now / 520) % 2;
    for (var y = y0; y <= y1; y++) {
      if (y < 0 || y >= map.h) continue;
      var row = map.rows[y];
      for (var x = x0; x <= x1; x++) {
        if (x < 0 || x >= map.w) continue;
        var sx = x * TS - camX, sy = y * TS - camY;
        var ch = row.charAt(x);
        ctx.drawImage(tileFor(ch, x, y, wf), sx, sy, TS, TS);
        if (dug[x + ',' + y]) ctx.drawImage(hole, sx, sy, TS, TS);
        if (ch === 'q' && ship) ctx.drawImage(ship.img, (x - ship.x) * T, (y - ship.y) * T, T, T, sx, sy, TS, TS);
        else if (ch === 'N') {
          var np = npcAt[x + ',' + y];
          if (np && tiles.npc[np.kind]) ctx.drawImage(tiles.npc[np.kind], sx, sy - 3 * scale, TS, TS);
        }
      }
    }
    // 캐릭터
    var cx = Math.round(pos.x * TS - camX), cy = Math.round(pos.y * TS - camY);
    ctx.fillStyle = 'rgba(0,0,0,.28)';
    ctx.fillRect(cx + 3 * scale, cy + 13 * scale, 10 * scale, 3 * scale);
    var digging = now - P.digT0 < 380, dk = digging ? ((now - P.digT0) < 190 ? 1 : 2) : 0;
    ctx.drawImage(sprites[P.dir + (P.moving ? P.step : 0) + dk], cx, cy - 3 * scale, TS, TS);
    // 어두운 맵(동굴): 캐릭터 주변만 밝게
    var sv = sight();
    if (sv < 2) { // 2단계 이상이면 어둠 없음(전체가 보임)
      var lx = cx + TS / 2, ly = cy + TS / 2;
      var dim = sv <= 0 ? { r0: 0.45, r1: 2.3, mid: 0.8, edge: 0.99 } : { r0: 1.6, r1: 6.0, mid: 0.5, edge: 0.9 };
      var grd = ctx.createRadialGradient(lx, ly, TS * dim.r0, lx, ly, TS * dim.r1);
      grd.addColorStop(0, 'rgba(6,4,2,0)');
      grd.addColorStop(0.55, 'rgba(6,4,2,' + dim.mid + ')');
      grd.addColorStop(1, 'rgba(6,4,2,' + dim.edge + ')');
      ctx.fillStyle = grd;
      ctx.fillRect(0, 0, W, H);
    }
    // [2026-10-23] 모래바람 -- 화면이 누렇게 흐려지고 바람 방향으로 모래가 날린다.
    if (map.wind) {
      var wdir = windDir();
      ctx.fillStyle = 'rgba(214,170,96,0.16)';
      ctx.fillRect(0, 0, W, H);
      ctx.strokeStyle = 'rgba(240,214,150,0.55)';
      ctx.lineWidth = Math.max(1, dpr);
      ctx.beginPath();
      for (var si = 0; si < 46; si++) {
        var spd = 0.35 + (si % 5) * 0.08, len = (10 + (si % 4) * 6) * dpr;
        var lxs = ((si * 197 + now * spd * wdir) % (W + 200) + (W + 200)) % (W + 200) - 100;
        var lys = (si * 131 % 1000) / 1000 * H + Math.sin(now / 400 + si) * 6 * dpr;
        ctx.moveTo(lxs, lys); ctx.lineTo(lxs - len * wdir, lys + 1.5 * dpr);
      }
      ctx.stroke();
    }
    // [2026-10-23] 가까운 NPC 이름 + 말풍선
    (map.npcs || []).forEach(function (n) {
      if (Math.abs(n.x - P.x) + Math.abs(n.y - P.y) > 5) return;
      var nxp = n.x * TS - camX + TS / 2, nyp = n.y * TS - camY - 6 * dpr;
      ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif';
      ctx.textAlign = 'center';
      ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText('💬 ' + n.name, nxp, nyp);
      ctx.fillStyle = '#9fe0ff'; ctx.fillText('💬 ' + n.name, nxp, nyp);
    });
    if (talkBubble && now - talkBubble.t0 < 4500) {
      var bx = talkBubble.x * TS - camX + TS / 2, by = talkBubble.y * TS - camY - 22 * dpr;
      ctx.font = 'bold ' + Math.round(13 * dpr) + 'px sans-serif';
      var words = talkBubble.text, lines = [], cur = '';
      for (var wi = 0; wi < words.length; wi++) { cur += words.charAt(wi); if ((cur.length >= 18 && words.charAt(wi) === ' ') || cur.length >= 26) { lines.push(cur); cur = ''; } }
      if (cur) lines.push(cur);
      var lh = 17 * dpr, bw = 0;
      lines.forEach(function (l) { bw = Math.max(bw, ctx.measureText(l).width); });
      bw += 16 * dpr;
      var bh = lines.length * lh + 10 * dpr, bx0 = Math.max(4, Math.min(W - bw - 4, bx - bw / 2)), by0 = Math.max(4, by - bh);
      ctx.fillStyle = 'rgba(255,253,240,0.96)'; ctx.fillRect(bx0, by0, bw, bh);
      ctx.strokeStyle = '#140d0a'; ctx.lineWidth = 2 * dpr; ctx.strokeRect(bx0, by0, bw, bh);
      ctx.fillStyle = '#2a1d10'; ctx.textAlign = 'left';
      lines.forEach(function (l, i) { ctx.fillText(l, bx0 + 8 * dpr, by0 + 5 * dpr + (i + 0.8) * lh); });
    } else if (talkBubble) talkBubble = null;
    // 가까운 포탈 이름
    (map.portals || []).forEach(function (p) {
      if (Math.abs(p.x - P.x) + Math.abs(p.y - P.y) > 4) return;
      var tx = p.x * TS - camX + TS / 2, ty = p.y * TS - camY - 4 * dpr;
      var label = (p.locked ? '🔒 ' : '🚪 ') + p.label;
      ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif';
      ctx.textAlign = 'center';
      ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText(label, tx, ty);
      ctx.fillStyle = p.locked ? '#d0d0d0' : '#ffe14d'; ctx.fillText(label, tx, ty);
    });
    // 캔 광석 글자
    floats = floats.filter(function (f) { return now - f.t0 < 1400; });
    floats.forEach(function (f) {
      var k = (now - f.t0) / 1400, fx = f.x * TS - camX + TS / 2, fy = f.y * TS - camY - k * 36 * dpr - 6 * dpr;
      ctx.globalAlpha = 1 - k * k;
      ctx.font = 'bold ' + Math.round((f.big ? 17 : 14) * dpr) + 'px monospace';
      ctx.textAlign = 'center';
      ctx.drawImage(f.icon, fx - 9 * dpr - ctx.measureText(f.text).width / 2 - 8 * dpr, fy - 12 * dpr, 16 * dpr, 16 * dpr);
      ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText(f.text, fx + 9 * dpr, fy);
      ctx.fillStyle = f.color; ctx.fillText(f.text, fx + 9 * dpr, fy);
      ctx.globalAlpha = 1;
    });
    if (now - flashT0 < 420) { ctx.fillStyle = 'rgba(255,255,255,' + (0.55 * (1 - (now - flashT0) / 420)) + ')'; ctx.fillRect(0, 0, W, H); }
    if (fade) {
      var fe = now - fade.t0, fa;
      if (fade.mode === 'out') fa = Math.min(1, fe / FADE_OUT_MS);
      else { fa = 1 - fe / FADE_IN_MS; if (fa <= 0) { fade = null; fa = 0; } }
      if (fa > 0) { ctx.fillStyle = 'rgba(0,0,0,' + fa + ')'; ctx.fillRect(0, 0, W, H); }
    }
    if (banner) { // 새 맵 이름(가운데, 잠깐 보였다 사라짐)
      var be = now - banner.t0;
      if (be > BANNER_MS) banner = null;
      else {
        ctx.globalAlpha = be < 250 ? be / 250 : Math.max(0, 1 - (be - BANNER_MS * 0.6) / (BANNER_MS * 0.4));
        ctx.font = 'bold ' + Math.round(22 * dpr) + 'px sans-serif';
        ctx.textAlign = 'center';
        ctx.lineWidth = 6 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText(banner.text, W / 2, H * 0.32);
        ctx.fillStyle = '#ffe9a8'; ctx.fillText(banner.text, W / 2, H * 0.32);
        ctx.globalAlpha = 1;
      }
    }
    // 끌기 조이스틱
    if (drag) {
      var rc = el.cv.getBoundingClientRect(), ox = (drag.ox - rc.left) * dpr, oy = (drag.oy - rc.top) * dpr;
      ctx.strokeStyle = 'rgba(255,255,255,.55)'; ctx.lineWidth = 3 * dpr;
      ctx.beginPath(); ctx.arc(ox, oy, 34 * dpr, 0, Math.PI * 2); ctx.stroke();
      var ddx = (drag.cx - drag.ox) * dpr, ddy = (drag.cy - drag.oy) * dpr, len = Math.sqrt(ddx * ddx + ddy * ddy), lim = 34 * dpr;
      if (len > lim) { ddx = ddx / len * lim; ddy = ddy / len * lim; }
      ctx.fillStyle = 'rgba(255,255,255,.7)'; ctx.beginPath(); ctx.arc(ox + ddx, oy + ddy, 13 * dpr, 0, Math.PI * 2); ctx.fill();
    }
  }
  function loop(now) {
    if (!opened) return;
    raf = requestAnimationFrame(loop);
    if (!map) return;
    update(now);
    draw(now);
  }

  // ---------------------------------------------------------------- 서버
  function api(path) {
    return fetch(cfg.base + path + (path.indexOf('?') < 0 ? '?' : '&') + 'userName=' + encodeURIComponent(cfg.user)).then(function (r) { return r.json(); })
      .then(function (d) {
        // [2026-10-23] 2일 주기 맵 리셋 중 -- 안내 후 광산에서 나간다(리셋이 끝나면 다시 들어오면 새 맵)
        if (d && d.resetting && opened && map) { msg(d.message || d.error, 4000); setTimeout(close, 2500); }
        return d;
      });
  }
  /** 서버에 보내는 맵 version -- 리셋 등으로 맵이 바뀌었으면 서버가 reload로 답한다. */
  function verQ() { return map && map.version ? '&v=' + encodeURIComponent(map.version) : ''; }
  function ensureMap(id, ver) {
    var key = 'tw-mine-map-' + id;
    try {
      var c = JSON.parse(localStorage.getItem(key) || 'null');
      if (c && c.version === ver && c.rows) return Promise.resolve(c);
    } catch (e) { }
    return fetch(cfg.base + '/api/tower-mine-map?mapId=' + encodeURIComponent(id)).then(function (r) { return r.json(); }).then(function (m) {
      if (m.error || !m.rows) throw new Error(m.error || '맵을 불러오지 못했어요.');
      try { localStorage.removeItem('tw-mine-map'); localStorage.setItem(key, JSON.stringify(m)); } catch (e) { }
      return m;
    });
  }
  /** 서버 상태(맵/위치/캔 칸)로 화면을 다시 맞춘다 -- 처음 들어올 때, 포탈을 탄 뒤, 다른 곳에서 위치가 바뀌었을 때. */
  function enterState(s) {
    st = s;
    STEP_MS = s.stepMs || 140; // [2026-10-24] 신속의 장화
    return ensureMap(s.mapId, s.mapVersion).then(function (m) {
      map = m;
      portalAt = {};
      (m.portals || []).forEach(function (p) { portalAt[p.x + ',' + p.y] = p; });
      npcAt = {};
      (m.npcs || []).forEach(function (n) { npcAt[n.x + ',' + n.y] = n; });
      // 배(q) 칸 묶음 -> 한 장 그림
      ship = null;
      var qx0 = 1e9, qy0 = 1e9, qx1 = -1, qy1 = -1;
      for (var yy = 0; yy < m.h; yy++) {
        var rr = m.rows[yy], qi = rr.indexOf('q');
        while (qi >= 0) { qx0 = Math.min(qx0, qi); qx1 = Math.max(qx1, qi); qy0 = Math.min(qy0, yy); qy1 = Math.max(qy1, yy); qi = rr.indexOf('q', qi + 1); }
      }
      if (qx1 >= 0) ship = buildShip(qx0, qy0, qx1 - qx0 + 1, qy1 - qy0 + 1);
      closeSmith(); talkBubble = null; lastWindT = performance.now();
      dug = {};
      var a = s.dug || [];
      for (var i = 0; i + 2 < a.length; i += 3) dug[a[i] + ',' + a[i + 1]] = a[i + 2];
      recent = (s.recent || []).slice();
      buildMiniBase();
      P.dir = 'down'; keys = []; drag = null; floats = []; tapDir = null;
      setPos(s.x, s.y);
      busy = false; // showStamina보다 먼저 -- 안 그러면 ⛏️ 버튼이 비활성처럼 보인 채로 남는다
      showStamina(); renderLog();
    });
  }
  function reload() {
    busy = true;
    return api('/api/tower-mine').then(enterState).then(function () { fadeIn('🗺️ ' + map.name); })
      .catch(function () { busy = false; fadeIn(null); msg('불러오기 실패 -- 다시 들어와 주세요.'); });
  }
  function setPos(x, y) {
    P.x = P.tx = P.fx = x; P.y = P.ty = P.fy = y; P.moving = false;
    lastSaved = map.id + ':' + x + ',' + y;
    showCoord(); drawMini();
  }
  function savePos() {
    // [2026-10-24] 걷는 중에도 저장(P.x/P.y = 마지막으로 다 걸은 칸) -- 예전엔 움직이는 동안 건너뛰어서, 오래 계속 걸으면 멈출 때 한꺼번에 먼 거리를 보내
    //  서버 순간이동 검사(최근 15초 이동 가능 거리)에 걸려 되돌아갔다.
    if (!opened || busy || !map) return;
    var cur = map.id + ':' + P.x + ',' + P.y;
    if (cur === lastSaved) return;
    lastSaved = cur;
    api('/api/tower-mine-pos?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + verQ()).then(function (d) {
      if (d && d.reload) { reload(); return; }
      if (d && d.ok === false && d.x != null) { setPos(d.x, d.y); msg('이동 기록이 맞지 않아 (' + d.x + ', ' + d.y + ')로 돌아갔어요.'); }
    }).catch(function () { lastSaved = ''; });
  }
  /** exit=true: 광산을 나가거나 화면을 벗어날 때 -- 위치가 그대로여도 보내서 서버가 퇴장 로그를 남긴다(한 번만). */
  function saveBeacon(exit) {
    if (!map) return;
    var x = P.moving ? P.fx : P.x, y = P.moving ? P.fy : P.y;
    if (exit && exitSent) return;
    if (!exit && map.id + ':' + x + ',' + y === lastSaved) return;
    lastSaved = map.id + ':' + x + ',' + y;
    if (exit) exitSent = true;
    var body = new URLSearchParams();
    body.append('userName', cfg.user); body.append('mapId', map.id); body.append('x', x); body.append('y', y); body.append('v', map.version || '');
    if (exit) body.append('exit', '1');
    try {
      if (!(navigator.sendBeacon && navigator.sendBeacon(cfg.base + '/api/tower-mine-pos', body))) {
        fetch(cfg.base + '/api/tower-mine-pos', { method: 'POST', body: body, keepalive: true });
      }
    } catch (e) { }
  }
  function dig() {
    if (!opened || busy || !map) return;
    if (P.moving) return;
    var x = P.x, y = P.y, c = cellAt(x, y);
    if (c === 'V') { msg('마을에서는 캘 수 없어요. 마을 밖으로 나가 보세요.'); return; }
    if (c === 'T') { msg('나무 밑은 캘 수 없어요. 옆 칸에서 캐 보세요.'); return; }
    if (dug[x + ',' + y]) { msg('이미 캔 자리예요. 다른 곳을 찾아보세요.'); return; }
    if (st.durability < 1) { msg('⛏️ 곡괭이 내구도가 다 닳았어요. 중앙 평원 마을의 대장장이에게 광석을 주고 곡괭이를 강화하세요.'); return; }
    busy = true;
    el.dig.disabled = true;
    P.digT0 = performance.now();
    api('/api/tower-mine-dig?mapId=' + encodeURIComponent(map.id) + '&x=' + x + '&y=' + y + verQ()).then(function (d) {
      busy = false;
      if (d.durability != null) { st.durability = d.durability; st.durabilityMax = d.durabilityMax; }
      if (d.ok) {
        lastSaved = map.id + ':' + x + ',' + y;
        dug[x + ',' + y] = d.grade;
        (st.ores || []).forEach(function (o) { if (o.grade === d.grade) o.qty = d.qty; }); // 대장간 창의 보유 수량도 맞춘다
        recent.unshift({ mapName: map.name, x: x, y: y, grade: d.grade, at: Date.now() });
        var img = new Image(); img.src = oreIcon(d.grade);
        floats.push({ x: x, y: y, text: '+' + (d.gain || 1) + ' ' + d.oreName, color: (ORE_COL[d.grade] || ORE_COL[1])[1], big: d.grade >= 5, icon: img, t0: performance.now() });
        if (d.grade >= 5) { flashT0 = performance.now(); msg('✨ ' + d.oreName + '! ' + map.name + ' (' + x + ', ' + y + ')', 3200); }
        if (d.gemGrade) { // [2026-10-24] 보석이 함께 나옴
          (st.ores || []).forEach(function (o) { if (o.grade === d.gemGrade) o.qty = d.gemQty; });
          var gimg = new Image(); gimg.src = oreIcon(d.gemGrade);
          floats.push({ x: x, y: y - 0.6, text: '+1 ' + d.gemName, color: (ORE_COL[d.gemGrade] || ORE_COL[1])[1], big: !!RARE_GEM[d.gemGrade], icon: gimg, t0: performance.now() + 250 });
          if (RARE_GEM[d.gemGrade]) { flashT0 = performance.now(); msg('💎 ' + d.gemName + '! ' + map.name + ' (' + x + ', ' + y + ')', 3600); }
          else msg('💎 ' + d.gemName + '도 함께 나왔어요!', 2200);
        }
        showCoord(); drawMini(); renderLog();
      } else {
        if (d.reload) { reload(); return; }
        if (d.resyncX != null) setPos(d.resyncX, d.resyncY);
        msg(d.message || d.error || '캐지 못했어요.');
      }
      showStamina();
    }).catch(function () { busy = false; showStamina(); msg('요청 실패 -- 다시 시도해주세요.'); });
  }

  function open(o) {
    cfg = o;
    bindDom();
    if (!tiles) { buildTiles(); buildSprites(); }
    el.ov.classList.add('open');
    el.load.style.display = 'flex';
    el.log.style.display = 'none';
    document.body.style.overflow = 'hidden';
    opened = true;
    map = null;
    busy = true;
    fade = null; banner = null;
    fit();
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(loop);
    exitSent = false;
    api('/api/tower-mine?enter=1').then(function (s) { // enter=1: 서버가 입장 로그를 남긴다
      if (s.error) throw new Error(s.error);
      return enterState(s);
    }).then(function () {
      if (!opened) return;
      el.load.style.display = 'none';
      fadeIn('🗺️ ' + map.name);
      msg(map.wind ? '🌪️ 모래바람이 불어요! 가만히 있어도 옆으로 밀려나요.' : '방향키·화면 끌기로 이동, ⛏️(스페이스)로 지금 서 있는 칸을 캐요. NPC에게 걸어가면 말을 걸어요.', 3500);
      clearInterval(saveTimer);
      saveTimer = setInterval(function () {
        savePos();
      }, SAVE_MS);
    }).catch(function (e) {
      el.load.textContent = '광산을 불러오지 못했어요. (' + (e && e.message ? e.message : '오류') + ')';
    });
  }
  function close() {
    if (!opened) return;
    if (map) saveBeacon(true);
    closeSmith();
    opened = false;
    cancelAnimationFrame(raf);
    clearInterval(saveTimer);
    keys = []; drag = null;
    el.ov.classList.remove('open');
    el.load.textContent = '광산으로 가는 중...';
    document.body.style.overflow = '';
    if (cfg && cfg.onClose) cfg.onClose();
  }
  // [2026-10-23] 마을로 이동 -- 어디에 있든 중앙 평원 마을(시작점)으로
  function goVillage() {
    if (!opened || busy || !map) return;
    if (map.id === 'field' && Math.abs(P.x - map.spawnX) + Math.abs(P.y - map.spawnY) <= 6) { msg('이미 마을이에요.'); return; }
    if (!confirm('마을(중앙 평원 시작점)로 이동할까요?')) return;
    travel('/api/tower-mine-village', '이동하지 못했어요.');
  }
  function toggleMini() { el.mini.classList.toggle('big'); }
  function toggleLog() { el.log.style.display = el.log.style.display === 'block' ? 'none' : 'block'; }
  // 캔 시각: 오늘이면 "15:42", 아니면 "10/22 15:42"
  function digTime(ms) {
    if (!ms) return '';
    var d = new Date(ms), now = new Date();
    var hm = pad2(d.getHours()) + ':' + pad2(d.getMinutes());
    return d.toDateString() === now.toDateString() ? hm : (d.getMonth() + 1) + '/' + d.getDate() + ' ' + hm;
  }

  return { open: open, close: close, dig: dig, goVillage: goVillage, toggleMini: toggleMini, toggleLog: toggleLog, oreIcon: oreIcon, pickUpgrade: pickUpgrade, helmetUpgrade: helmetUpgrade, closeSmith: closeSmith, smithGo: smithGo, craft: craft, equipRing: equipRing, synth: synth, captain: captain,
           ORE_NAME: ORE_NAME, digTime: digTime };
})();
</script>
</body>
</html>
