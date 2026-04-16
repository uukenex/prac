<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>람쥐봇 직업 도감</title>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #f0f4f8; font-family: 'Segoe UI', 'Malgun Gothic', sans-serif; color: #333; margin-left: 120px; }

    .wrap { max-width: 1100px; margin: 0 auto; padding: 28px 16px 60px; }

    .page-header { margin-bottom: 22px; }
    .page-title  { font-size: 22px; font-weight: 800; color: #1a2a3a; display: flex; align-items: center; gap: 8px; }
    .page-subtitle { font-size: 13px; color: #999; margin-top: 4px; }

    /* 검색 */
    .top-controls { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
    .search-wrap  { flex: 1; min-width: 180px; position: relative; }
    .search-wrap input { width: 100%; padding: 10px 14px 10px 36px; border: 1.5px solid #d4dde6; border-radius: 24px; background: #fff; font-size: 14px; color: #333; outline: none; transition: border-color .2s; }
    .search-wrap input:focus { border-color: #5b8dd9; }
    .search-wrap .fa { position: absolute; left: 13px; top: 50%; transform: translateY(-50%); color: #bbb; font-size: 13px; }

    /* 상태바 */
    .bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 8px; }
    .count-label { font-size: 13px; color: #aaa; }
    .count-label strong { color: #1a2a3a; }

    /* 그리드 */
    .job-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 5px; }

    /* 카드 */
    .job-card { background: #fff; border-radius: 14px; padding: 16px 15px 14px; box-shadow: 0 2px 8px rgba(0,0,0,.06); border: 1.5px solid transparent; transition: box-shadow .18s, border-color .18s; }
    .job-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.10); border-color: #5b8dd9; }

    .card-name  { font-size: 15px; font-weight: 800; color: #1a2a3a; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
    .card-subtitle { font-size: 12px; color: #888; margin-bottom: 10px; line-height: 1.4; }

    .skill-block { border-top: 1px solid #f0f0f0; padding-top: 9px; margin-top: 2px; }
    .skill-line { font-size: 12px; color: #4a5568; padding: 3px 0; line-height: 1.5; }
    .skill-line:not(:last-child) { border-bottom: 1px dashed #f5f5f5; }

    .skill-name { font-weight: 700; color: #2c5282; }
    .skill-desc { color: #666; }

    .empty   { text-align: center; padding: 60px 0; color: #ccc; }
    .empty .ico { font-size: 34px; margin-bottom: 10px; }
    .loading { text-align: center; padding: 60px; color: #ccc; font-size: 15px; }

    @media (max-width: 360px) {
      .search-wrap   { min-width: 0; }
      .job-grid      { grid-template-columns: 1fr; }
      .job-card      { padding: 12px 12px 10px; }
      .card-name     { font-size: 13px; }
      .card-subtitle { font-size: 11px; margin-bottom: 7px; }
      .skill-line    { font-size: 11px; }
    }
  </style>
</head>
<body>
<%@ include file="_loa_nav.jsp" %>
<div id="app" class="wrap">

  <div class="page-header">
    <div class="page-title">⚔ 람쥐봇 직업 도감</div>
    <div class="page-subtitle">총 {{ jobs.length }}개 직업</div>
  </div>

  <div class="top-controls">
    <div class="search-wrap">
      <i class="fa fa-search"></i>
      <input v-model="keyword" placeholder="직업명 / 스킬 검색...">
    </div>
  </div>

  <div class="bar">
    <div class="count-label">현재 표시 <strong>{{ filteredJobs.length }}</strong> 개</div>
  </div>

  <div class="loading" v-if="loading"><i class="fa fa-spinner fa-spin"></i> 불러오는 중...</div>

  <div class="job-grid" v-else-if="filteredJobs.length > 0">
    <div class="job-card" v-for="job in filteredJobs" :key="job.name">
      <div class="card-name">{{ job.name }}</div>
      <div class="card-subtitle">{{ job.listLine }}</div>
      <div class="skill-block">
        <div class="skill-line" v-for="(line, idx) in job.skills" :key="idx">
          <template v-if="line.name">
            <span class="skill-name">[{{ line.name }}]</span>
            <span class="skill-desc"> {{ line.desc }}</span>
          </template>
          <template v-else>
            <span class="skill-desc">{{ line.raw }}</span>
          </template>
        </div>
      </div>
    </div>
  </div>

  <div class="empty" v-else>
    <div class="ico">🗡️</div>
    <div>검색 결과가 없습니다</div>
  </div>

</div>

<script src="https://cdn.jsdelivr.net/npm/vue@2/dist/vue.min.js"></script>
<script>
new Vue({
  el: '#app',
  data: {
    jobs: [],
    keyword: '',
    loading: true
  },
  computed: {
    filteredJobs() {
      const kw = this.keyword.trim().toLowerCase();
      if (!kw) return this.jobs;
      return this.jobs.filter(j =>
        j.name.toLowerCase().includes(kw) ||
        j.listLine.toLowerCase().includes(kw) ||
        j.attackLine.toLowerCase().includes(kw)
      );
    }
  },
  mounted() {
    fetch('<%=request.getContextPath()%>/loa/api/jobs')
      .then(r => r.json())
      .then(data => {
        this.jobs = data.map(j => Object.assign({}, j, { skills: parseSkills(j.attackLine) }));
        this.loading = false;
      })
      .catch(() => { this.loading = false; });
  }
});

function parseSkills(attackLine) {
  if (!attackLine) return [];
  return attackLine.split('♬')
    .map(s => s.trim())
    .filter(s => s.length > 0)
    .map(s => {
      // ▶[스킬명] 설명 형식 파싱
      const m = s.match(/^[▶►]?\[([^\]]+)\]\s*(.*)/);
      if (m) return { name: m[1], desc: m[2], raw: s };
      // ▶스킬명 형식
      const m2 = s.match(/^[▶►](.*)/);
      if (m2) return { name: '', raw: m2[1].trim() };
      return { name: '', raw: s };
    });
}
</script>
</body>
</html>
