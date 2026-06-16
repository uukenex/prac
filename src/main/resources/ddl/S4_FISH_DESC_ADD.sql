-- TBOT_S4_FISH_INFO 도감 설명 컬럼 추가
ALTER TABLE TBOT_S4_FISH_INFO ADD (
    FISH_EMOJI VARCHAR2(10),
    FISH_DESC  VARCHAR2(300)
);

-- ★1 민물 (101~106)
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='잉어과에 속하며 전국 하천에 서식하는 흔한 민물고기. 맑은 여울을 즐겨 찾는다.' WHERE FISH_ID=101; -- 피라미
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐠', FISH_DESC='잉어과 붕어속. 저수지와 논도랑 등 전국 어디서나 잡히는 대표 민물고기.' WHERE FISH_ID=102; -- 붕어
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐍', FISH_DESC='미꾸리과. 논두렁과 진흙 바닥에 숨어 사는 길쭉한 민물고기. 추어탕의 주인공.' WHERE FISH_ID=103; -- 미꾸라지
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='잉어과 소형 어종. 논과 웅덩이 등 얕고 따뜻한 민물에 떼지어 산다.' WHERE FISH_ID=104; -- 송사리
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐡', FISH_DESC='붕어와 비슷하나 몸이 더 납작하다. 하천 중·하류 수초 지대에 서식.' WHERE FISH_ID=105; -- 참붕어
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='잉어과. 모래와 자갈이 깔린 맑은 하천 바닥을 즐겨 찾는다. 환경부 보호종.' WHERE FISH_ID=106; -- 모래무지

-- ★2 낚시터/갯벌 (201~206)
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='잉어과 대형 민물고기. 연못과 강 하류에 서식하며 장수의 상징으로 여겨진다.' WHERE FISH_ID=201; -- 잉어
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐈', FISH_DESC='메기과. 수염이 특징인 대형 육식 민물고기. 강 하류 깊은 웅덩이에 산다.' WHERE FISH_ID=202; -- 메기
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='❄️',  FISH_DESC='바다빙어과. 겨울 얼음낚시의 주인공. 청정 호수에서만 서식하는 계절 한정 어종.' WHERE FISH_ID=203; -- 빙어
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐊', FISH_DESC='가물치과. 수면 호흡이 가능한 사나운 육식 어종. 습지와 저수지에 서식.' WHERE FISH_ID=204; -- 가물치
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦪', FISH_DESC='백합과 조개. 서해 갯벌에 집단 서식하며 조개구이와 칼국수 재료로 유명하다.' WHERE FISH_ID=205; -- 바지락
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦪', FISH_DESC='홍합과 이매패. 바위에 족사로 붙어 사는 조개. 된장국 재료로 널리 쓰인다.' WHERE FISH_ID=206; -- 홍합

-- ★3 청정계곡/연안 (301~306)
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🌊', FISH_DESC='바다빙어과. 청정 1급수 계곡에만 서식하는 여름철 어종. 수질 지표 생물로 지정.' WHERE FISH_ID=301; -- 은어
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='꺽지과 육식 민물고기. 맑은 계곡 바위틈에 살며 낚시인들이 즐겨 찾는 대상어.' WHERE FISH_ID=302; -- 꺽지
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='연어과. 강에서 태어나 바다에서 성장 후 산란을 위해 고향 강으로 돌아온다.' WHERE FISH_ID=303; -- 연어
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦪', FISH_DESC='재첩과 소형 담수 조개. 섬진강 등 청정 하천 모래바닥에 서식. 해장국 재료.' WHERE FISH_ID=304; -- 재첩
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐚', FISH_DESC='가리비과 이매패. 연안 암초에 서식하며 부채꼴 껍데기가 특징인 고급 조개.' WHERE FISH_ID=305; -- 가리비
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦀', FISH_DESC='참게과. 강에서 태어나 바다로 이동 후 성체가 되어 돌아오는 회귀성 갑각류.' WHERE FISH_ID=306; -- 참게

-- ★4 배낚시 (401~406)
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐙', FISH_DESC='문어과 두족류. 연안 암초 지대에 서식하며 강력한 지능과 위장 능력을 지닌다.' WHERE FISH_ID=401;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦑', FISH_DESC='오징어과 두족류. 동해 연안 회유성 어종. 반건조 오징어로도 유명하다.' WHERE FISH_ID=402;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐚', FISH_DESC='소라과 복족류. 조간대 암초에 붙어 사는 대형 고둥. 제주도 해녀가 즐겨 채집.' WHERE FISH_ID=403;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦪', FISH_DESC='굴과 이매패. 서남해 양식의 대표 조개. 겨울철 영양 가득한 맛으로 유명.' WHERE FISH_ID=404;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='고등어과 회유성 어종. 동·서·남해 전 연안에 떼지어 이동하며 등푸른 생선의 대표.' WHERE FISH_ID=405;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='전갱이과. 연안에서 배낚시로 주로 잡히는 소형 회유성 어종. 구이와 회로 즐긴다.' WHERE FISH_ID=406;

-- ★5 잠수/전문장비 (501~506)
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='도미과 고급 어종. 붉은 빛 외관과 담백한 맛으로 횟감의 최고봉으로 꼽힌다.' WHERE FISH_ID=501;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='넙치과. 납작한 몸통에 두 눈이 한쪽에 몰린 대형 저서성 어종. 양식 횟감 1위.' WHERE FISH_ID=502;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐚', FISH_DESC='전복과 복족류. 제주 해녀가 채취하는 고급 패류. 환경부 해산물 중 최고가.' WHERE FISH_ID=503;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🥒', FISH_DESC='해삼과 극피동물. 연안 암초와 모래바닥에 서식. 동아시아 최고급 수산물 중 하나.' WHERE FISH_ID=504;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦀', FISH_DESC='물맞이게과. 동해 심냉수에 서식하는 대형 게. 겨울 제철에만 맛볼 수 있는 별미.' WHERE FISH_ID=505;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐙', FISH_DESC='문어과 소형 두족류. 서·남해 갯벌과 암초에 서식. 회·볶음·탕 등 다양하게 즐긴다.' WHERE FISH_ID=506;

-- ★6 원양/심해 입구 (601~606)
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='전갱이과 대형 회유성 어종. 겨울 남해에서 잡히며 횟감과 방어구이로 최고급 취급.' WHERE FISH_ID=601;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🌊', FISH_DESC='성게과 극피동물. 암초 조간대에 서식. 고소한 생식소(알)가 일본 요리에서 최고급 식재료.' WHERE FISH_ID=602;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦀', FISH_DESC='왕게과 대형 갑각류. 베링해·오호츠크해 등 극한 냉수에 서식. 최고급 수산물.' WHERE FISH_ID=603;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐍', FISH_DESC='뱀장어과 대형 바다장어. 이빨이 날카로워 위험하며 장어탕과 구이로 고급 식재료.' WHERE FISH_ID=604;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦪', FISH_DESC='굴과. 자연산으로 양식품과 비교 불가한 깊은 풍미. 천연 바위에만 붙어 채취 극히 어렵다.' WHERE FISH_ID=605;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='가자미과. 동해 청정 해역 모래바닥에 서식하는 최고급 횟감 가자미 어종.' WHERE FISH_ID=606;

-- ★7 원양어선급 (701~706)
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐋', FISH_DESC='고등어과 최대 3m. 태평양·인도양 원양 회유. 초밥 참치의 대명사이자 세계 최고가 어종.' WHERE FISH_ID=701;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐡', FISH_DESC='복어과. 독소 테트로도톡신을 가진 맹독 어종. 전문 면허 조리사만 손질 가능.' WHERE FISH_ID=702;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦞', FISH_DESC='바닷가재과 대형 갑각류. 대서양 냉수 심해에 서식. 전 세계 고급 레스토랑의 상징.' WHERE FISH_ID=703;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐚', FISH_DESC='거북손과 만각류. 암초 조간대에 집단 서식하며 유럽에서 초고급 식재료로 취급.' WHERE FISH_ID=704;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦈', FISH_DESC='귀상어과. 망치 모양 머리가 특징. 전 세계 온대·열대 바다 회유. CITES 보호 대상종.' WHERE FISH_ID=705;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐡', FISH_DESC='개복치과. 세계 최대 경골어류. 외양성 어종으로 표층을 떠다니며 일광욕을 즐긴다.' WHERE FISH_ID=706;

-- ★8 현실 채집 거의 불가 (801~806)
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦑', FISH_DESC='대왕오징어과. 최대 13m 이상. 북대서양 심해 1000m에 서식하는 신화 속 크라켄의 실체.' WHERE FISH_ID=801;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='철갑상어과. 2억년 전 고대 어종. 캐비어의 원료. 남획으로 멸종위기 1급 보호종.' WHERE FISH_ID=802;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='돗돔과. 인도양·태평양 초심해에 서식하는 최대 2m 희귀 대형 어종. 낚시 기록급.' WHERE FISH_ID=803;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='아귀과 심해 발광 어종. 수심 200~2000m 암흑 속 발광 기관으로 먹이를 유인한다.' WHERE FISH_ID=804;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🐟', FISH_DESC='나폴레옹피시과. 인도·태평양 산호초 대형 어종. 현란한 무늬로 바다의 귀족이라 불린다.' WHERE FISH_ID=805;
UPDATE TBOT_S4_FISH_INFO SET FISH_EMOJI='🦈', FISH_DESC='쥐가오리과 대형 가오리. 날개폭 최대 7m. 열대 원양을 유영하며 CITES 멸종위기 보호종.' WHERE FISH_ID=806;

COMMIT;
