-- ============================================================
-- Season 5 - 동료 이미지 일괄 백필 (2026-09-03, 실 DB에 이미 적용 완료)
--
-- 배경: /이미지갱신(BotS5ServiceImpl.refreshCompanionImages)이 nekos.best
-- API 호출 시 User-Agent 헤더를 안 보내서 전부 403(외부 API 응답 없음/차단
-- 추정)으로 실패, 동료 88마리 전부 IMAGE_URL이 NULL이었음. 원인은 nekos.best가
-- User-Agent 미지정 요청을 차단하는 정책 때문(문서: "APP_NAME (CONTACT_INFO)"
-- 형식 요구) -- BotS5ServiceImpl.fetchRandomNekoImage()에 User-Agent 헤더를
-- 추가해서 코드 자체는 고쳤지만(재배포 필요), 이미 존재하던 88마리는 이 백필
-- 스크립트로 직접 채웠음.
--
-- ⚠️ 참고: nekos.best의 JSON API(/api/v2/neko)는 User-Agent만 맞으면 되지만,
-- 실제 이미지 파일이 있는 CDN 경로(위 UPDATE의 IMAGE_URL 값들)는 Cloudflare
-- JS 챌린지("Just a moment...")가 걸려있어서 curl 등 스크립트성 요청으로는
-- 이미지 자체를 못 받아왔음(User-Agent를 정상 브라우저 값으로 바꿔도 동일).
-- 실제 유저 브라우저에서 tower_view.jsp의 <img> 태그로 정상 렌더링되는지는
-- 이 세션에서 브라우저 도구가 연결되지 않아 직접 확인 못했음 -- 웹에서
-- 이미지가 안 보이면 이 챌린지 때문일 가능성이 높으니 후속 확인 필요.
--
-- 재실행 불필요(이미 적용 완료), 새로 뽑는 동료는 /이미지갱신(수정된 코드
-- 배포 후) 또는 뽑기 시점 자동 호출로 채워짐.
-- ============================================================
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/ed7d26b8-756e-4e41-9a7b-f7c0f3970bb8.png' WHERE COMPANION_ID = 1;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/237da5ad-7597-478e-8e09-eb37cf367bea.png' WHERE COMPANION_ID = 2;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/2c5ae002-3750-41ff-961a-9ea889fbcac6.png' WHERE COMPANION_ID = 3;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/4aacd1fc-a31c-455c-b65e-d9b0dcadb48d.png' WHERE COMPANION_ID = 4;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/cc57fd3e-3d68-4fa3-bedf-685d22abad29.png' WHERE COMPANION_ID = 5;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/e4dc1604-e4b9-43ef-a8d1-25dea3e4a6df.png' WHERE COMPANION_ID = 6;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/bc7b308b-fcf4-453e-aafe-c46bc20f04d9.png' WHERE COMPANION_ID = 7;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/85ac0a7b-01fa-4138-b702-50545aee56c8.png' WHERE COMPANION_ID = 8;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/ee0249e3-c263-4ab4-b32a-138c5262d0de.png' WHERE COMPANION_ID = 9;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/3b3658d7-a91f-4da3-aa96-71150e67dd8c.png' WHERE COMPANION_ID = 10;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/5d292d34-6d1d-43f2-af36-dbe6da3b68c4.png' WHERE COMPANION_ID = 11;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/fdc8f2eb-9596-4ccc-92a3-62118ef08396.png' WHERE COMPANION_ID = 12;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/4d0652dd-d8bc-4c11-be8a-ff56ef8aee49.png' WHERE COMPANION_ID = 13;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/2d5022d3-5293-47fd-b0cd-b04faf8ce072.png' WHERE COMPANION_ID = 14;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/1b095921-b0b0-4702-a585-ac234d2f159c.png' WHERE COMPANION_ID = 15;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/50a3f63f-b0f2-4c3c-a7d2-5d28f0fa4a74.png' WHERE COMPANION_ID = 16;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/b4c47243-4a9d-4d53-8434-b7ebc9972199.png' WHERE COMPANION_ID = 17;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/45454c74-f915-49f8-83c8-182cc175bb07.png' WHERE COMPANION_ID = 18;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/808c040d-0c81-4b07-90c2-99de5b6ee6b3.png' WHERE COMPANION_ID = 19;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/0c5f633f-d185-4f24-be7f-2b3d9e88ca33.png' WHERE COMPANION_ID = 20;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/7e070456-d5ed-4147-a0ee-8171347834a9.png' WHERE COMPANION_ID = 21;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/2a481bd0-373a-46bc-a123-015deb9920b8.png' WHERE COMPANION_ID = 22;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/319b1f93-157f-4236-b885-a6b4026b849a.png' WHERE COMPANION_ID = 23;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/a92bf34a-2674-48b3-a8ab-fb2a8dc7e6b8.png' WHERE COMPANION_ID = 24;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/2f60a77c-6691-4f2a-80ff-59cc35d3c9e5.png' WHERE COMPANION_ID = 25;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/c94cd5e7-7414-43e7-8817-893442a1dff7.png' WHERE COMPANION_ID = 26;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/9c2df50c-ca71-4c82-ac72-65c7be9cf2d5.png' WHERE COMPANION_ID = 27;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/103e0d50-c804-47c8-93f7-7d1f5bf4b90e.png' WHERE COMPANION_ID = 29;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/fafff17d-76ea-42fb-b725-08d573fc9d94.png' WHERE COMPANION_ID = 30;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/390f0b9e-1bba-4e0f-a53e-9e5a3a583ce6.png' WHERE COMPANION_ID = 31;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/9ba62c14-8e72-4518-871c-c72c27a41107.png' WHERE COMPANION_ID = 33;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/5fc155ed-8458-4ead-9457-f70296e7f7b0.png' WHERE COMPANION_ID = 34;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/3b537fc1-f759-4499-870a-93ae9aaff9ea.png' WHERE COMPANION_ID = 35;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/2ecff225-1a8e-4e23-9b12-5ddaaff4b020.png' WHERE COMPANION_ID = 36;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/037379a4-d73e-4aa8-8797-d35034e6c2bc.png' WHERE COMPANION_ID = 37;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/1adcdd41-d2be-436a-9da2-154d40f93f81.png' WHERE COMPANION_ID = 38;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/75e5634e-18ed-42b1-90c9-8fee0bc815a4.png' WHERE COMPANION_ID = 39;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/84b0a000-d8c5-4c3f-a089-840dc0f1ca76.png' WHERE COMPANION_ID = 41;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/14766ba1-e7d9-4b00-a82f-4da9a6fd0d68.png' WHERE COMPANION_ID = 42;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/b648a9f5-35ae-4b6d-8d31-ae6957355de5.png' WHERE COMPANION_ID = 43;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/876a7a67-d3de-405c-943f-11232e99f8c8.png' WHERE COMPANION_ID = 44;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/f11a0506-8c32-443c-8fb7-a746ebb2a627.png' WHERE COMPANION_ID = 45;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/83bc30fe-5b77-4137-a78d-987bfc19a695.png' WHERE COMPANION_ID = 46;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/81e05a5f-31dd-4005-9d4c-8d75f7280704.png' WHERE COMPANION_ID = 47;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/2577fe52-516e-40e6-875e-75dbe319e439.png' WHERE COMPANION_ID = 48;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/777077cd-2e3b-44cd-a5e3-8edb6a8d2465.png' WHERE COMPANION_ID = 50;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/27993de3-d049-408b-8ba3-4487bd3d7da0.png' WHERE COMPANION_ID = 51;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/ea69ac1a-ea27-46c1-8658-05019ea1a2f4.png' WHERE COMPANION_ID = 52;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/1f5d7f3a-cd44-4028-9e43-87fb9dfbc268.png' WHERE COMPANION_ID = 53;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/1aeefdad-eeae-4902-9f5c-5a73449f58fc.png' WHERE COMPANION_ID = 54;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/879ae65c-ce98-4413-9729-abf08897fd5a.png' WHERE COMPANION_ID = 58;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/99e262a5-9e21-4da1-9479-ea8497620b0f.png' WHERE COMPANION_ID = 59;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/a0e1bcde-743c-4b29-9ee7-09d749b22a94.png' WHERE COMPANION_ID = 60;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/429fec52-770b-43b0-a4fc-3b11e3803919.png' WHERE COMPANION_ID = 61;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/87921cb0-9825-42a4-9758-2370817ce410.png' WHERE COMPANION_ID = 62;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/17362488-e60a-4a94-b55e-728f61c4b9eb.png' WHERE COMPANION_ID = 63;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/c495ebd2-648c-4a7d-b682-2785d2ff1154.png' WHERE COMPANION_ID = 64;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/478d294c-dfef-450a-abb7-a1c1d30c6ca2.png' WHERE COMPANION_ID = 65;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/53de6332-5519-4150-8d32-c8370343b2ea.png' WHERE COMPANION_ID = 66;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/c7886c83-0167-40fd-851d-e1b653a018cf.png' WHERE COMPANION_ID = 67;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/6136985d-9d54-48d0-b225-1720ebfd2034.png' WHERE COMPANION_ID = 68;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/1ade9c0a-74bd-42d7-bd1f-ff66f18a3060.png' WHERE COMPANION_ID = 69;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/ed625206-f83c-4c18-837d-09d09061533f.png' WHERE COMPANION_ID = 70;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/d7536822-8aa3-497f-9d5d-3bcf4d64a97e.png' WHERE COMPANION_ID = 71;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/f169748c-6581-4069-99ad-d8de3f061bb9.png' WHERE COMPANION_ID = 72;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/2656de1d-783c-4be0-839b-a983c2975b3a.png' WHERE COMPANION_ID = 73;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/9595c419-a6be-4ce9-80d4-660e0fe759de.png' WHERE COMPANION_ID = 74;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/00a50ba0-6589-4d53-8339-2e41639d37df.png' WHERE COMPANION_ID = 75;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/48364a45-36f0-45b1-806c-8ed3a4955fb1.png' WHERE COMPANION_ID = 76;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/d82de324-a923-4bb2-a5ba-40e0729dfeec.png' WHERE COMPANION_ID = 77;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/719f1c45-d37b-4529-a904-fca8c381d902.png' WHERE COMPANION_ID = 78;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/e0e1dba2-070a-45b4-aa6c-30735288e464.png' WHERE COMPANION_ID = 79;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/33aab0f5-829b-48d4-8ed5-185a204098a5.png' WHERE COMPANION_ID = 80;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/b62fdfc2-892e-4f3f-a9b5-e227443d80e1.png' WHERE COMPANION_ID = 81;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/46a13849-6fb9-4476-b754-5fd6f6ae333b.png' WHERE COMPANION_ID = 82;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/ba541f9f-39c4-4f09-bb79-4e4edfebd0d1.png' WHERE COMPANION_ID = 83;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/7e89c32e-6b7e-499f-9855-2137fcb98e10.png' WHERE COMPANION_ID = 84;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/02e5aa26-94c9-4cda-8c6f-b86c02d55e0f.png' WHERE COMPANION_ID = 85;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/1b0de689-9060-4400-86dc-73200105a6ca.png' WHERE COMPANION_ID = 86;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/2ebf59cf-1196-443f-b15b-1fd3ebdbf83b.png' WHERE COMPANION_ID = 87;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/97fb31d2-25f5-456d-99a8-c04392708b9a.png' WHERE COMPANION_ID = 88;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/f9650bb1-ef43-45c7-b9e3-41c7b7100235.png' WHERE COMPANION_ID = 89;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/1cd6152d-3ccb-4196-b444-ea248b40017e.png' WHERE COMPANION_ID = 90;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/998b0782-6ef4-406e-a1d4-fdfa4b435ae6.png' WHERE COMPANION_ID = 91;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/776ba57b-a2b0-40b6-b69f-3bdb169f0e62.png' WHERE COMPANION_ID = 92;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/bc00bdea-ef4f-4b57-80d4-48b9ec13f0ad.png' WHERE COMPANION_ID = 93;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/e1f21c6a-0610-4861-a005-47bc89b266e9.png' WHERE COMPANION_ID = 94;
UPDATE TBOT_S5_USER_COMPANION SET IMAGE_URL = 'https://nekos.best/api/v2/neko/ba57884e-44f4-4372-bd8c-9d11d7807117.png' WHERE COMPANION_ID = 95;
COMMIT;
EXIT;
