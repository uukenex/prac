<%@ page pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<title>보스 유물 옵션 | 람쥐봇</title>
<style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
body {
  background: #0a0818;
  color: #c8c0e0;
  font-family: 'Segoe UI', 'Malgun Gothic', sans-serif;
  min-height: 100vh;
}
.wrap { max-width: 960px; margin: 0 auto; padding: 24px 16px 80px; }

.page-title {
  font-size: 22px; font-weight: 900; color: #e8c870; margin-bottom: 6px;
  display: flex; align-items: center; gap: 10px;
}
.page-sub { font-size: 12px; color: #7070a0; margin-bottom: 24px; }

.card { background: #100e1e; border: 1px solid #2a2048; border-radius: 12px; padding: 20px; margin-bottom: 18px; }
.card-title {
  font-size: 14px; font-weight: 800; color: #c9a96e;
  margin-bottom: 14px; border-bottom: 1px solid #2a2048;
  padding-bottom: 8px;
}

table { width: 100%; border-collapse: collapse; font-size: 13px; }
th {
  background: #18152e; color: #a090c0; font-weight: 700;
  padding: 8px 10px; text-align: center; border-bottom: 1px solid #2a2048;
}
th:first-child { text-align: left; }
td { padding: 8px 10px; border-bottom: 1px solid #1a1830; vertical-align: middle; }
td:first-child { font-weight: 700; color: #e0d8f8; white-space: nowrap; }
td.opt-label { color: #90a0c0; font-size: 12px; white-space: nowrap; }
td.val { text-align: center; color: #e8c870; font-weight: 700; }
td.val.highlight { color: #60d0a0; }
tr:hover td { background: #14112a; }

.enhance-lv {
  display: inline-block; padding: 2px 7px; border-radius: 10px;
  font-size: 11px; font-weight: 700; margin-bottom: 4px;
}
.lv0 { background: #2a2048; color: #9090c0; }
.lv1 { background: #1a2a40; color: #60a0e0; }
.lv2 { background: #1a3020; color: #60d060; }
.lv3 { background: #2a2010; color: #d0a020; }
.lv4 { background: #2a1020; color: #e06080; }
.lv5 { background: #3a0820; color: #ff4080; }

#loading { text-align: center; padding: 60px; color: #7070a0; font-size: 14px; }
</style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div class="wrap">
  <div class="page-title">⚔ 보스 유물 강화 옵션</div>
  <div class="page-sub">최대 +5강화까지 각 단계별 수치를 확인하세요. (qty: 0강화=1개, +1=2~3개, +2=4~6개, +3=7~10개, +4=11~15개, +5=16~21개)</div>
  <div id="loading">데이터 불러오는 중...</div>
  <div id="content" style="display:none"></div>
</div>
<script>
const LV_LABELS = ['0강화', '+1강화', '+2강화', '+3강화', '+4강화', '+5강화'];
const LV_CLS    = ['lv0',   'lv1',    'lv2',    'lv3',    'lv4',    'lv5'   ];

fetch('/loa/api/boss-items')
  .then(r => r.json())
  .then(items => {
    const el = document.getElementById('content');
    const rows = items.map(item => {
      const desc = item.enhanceDesc || '';
      // parse "옵션명: 0강화:X단위 / +1강화:Y단위 / ..."
      const colonIdx = desc.indexOf(': ');
      const optLabel = colonIdx >= 0 ? desc.substring(0, colonIdx) : desc;
      const tiers = desc.substring(colonIdx + 2).split(' / ').map(s => {
        const ci = s.indexOf(':');
        return ci >= 0 ? s.substring(ci + 1) : s;
      });

      const cells = tiers.map((v, i) =>
        `<td class="val${i === 5 ? ' highlight' : ''}">${v}</td>`
      ).join('');
      const empties = LV_LABELS.slice(tiers.length).map(() => `<td class="val" style="color:#3a3060">-</td>`).join('');

      return `<tr>
        <td>${item.itemId}. ${item.itemName}</td>
        <td class="opt-label">${optLabel}</td>
        ${cells}${empties}
      </tr>`;
    }).join('');

    el.innerHTML = `
      <div class="card">
        <div class="card-title">유물 목록 (총 ${items.length}종)</div>
        <div style="overflow-x:auto">
          <table>
            <thead><tr>
              <th>유물명</th>
              <th>옵션</th>
              ${LV_LABELS.map((l,i) => `<th><span class="enhance-lv ${LV_CLS[i]}">${l}</span></th>`).join('')}
            </tr></thead>
            <tbody>${rows}</tbody>
          </table>
        </div>
      </div>`;
    document.getElementById('loading').style.display = 'none';
    el.style.display = '';
  })
  .catch(e => {
    document.getElementById('loading').textContent = '데이터 로드 실패: ' + e;
  });
</script>
</body>
</html>
