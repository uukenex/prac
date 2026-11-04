package my.prac.core.util;

import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import my.prac.core.prjbot.dao.BotS5DAO;

/**
 * [2026-11-04] 관리자 폰 푸시 알림 -- "관리자에게 다른 유저 접속 / 보스 처치 / 전설 이상 장비 / 카카오 닉네임 연결 / 게시판 새 글·공유게시판 수정 알림"(웹앱 /mine/ 설치한 폰).
 * 웹 푸시는 내용 없이 "알림 있음"만 보낸다(내용 암호화 라이브러리 없이 -- VAPID 서명만). 폰의 서비스 워커(/mine/sw.js)가 깨면
 * /mine/push-pull?endpoint= 로 그 폰 몫의 알림을 받아 띄운다. 그래서 알림 내용은 서버 메모리에만 잠깐(구독마다 최근 30개, 재시작하면 사라짐).
 *  - 구독: TBOT_S55_PUSH_SUB(관리자만 -- S55MineController.pushSub가 확인), VAPID 키: TBOT_S5_CONFIG S55_VAPID_PUB / S55_VAPID_PRIV(처음 쓸 때 만든다)
 *  - 보내기 실패 404/410 = 구독이 끝남 -> 지운다. 보내는 일은 별도 스레드(요청을 늦추지 않게).
 */
@Component
public class S55AdminPush {
    private static final Logger logger = LoggerFactory.getLogger(S55AdminPush.class);
    private static volatile S55AdminPush inst;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "s55push"); t.setDaemon(true); return t; });
    private static final ConcurrentHashMap<String, Deque<HashMap<String, Object>>> QUEUE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> THROTTLE = new ConcurrentHashMap<>();
    private static final int QUEUE_MAX = 30;

    @Resource(name = "core.prjbot.BotS5DAO")
    BotS5DAO dao;

    private volatile String pubB64; // 0x04 + x + y, base64url
    private volatile PrivateKey priv;

    @PostConstruct
    void init() { inst = this; }

    /** 화면(구독 버튼)이 쓰는 공개키(base64url). */
    public static String publicKey() {
        S55AdminPush p = inst;
        if (p == null) return null;
        try { p.keys(); return p.pubB64; } catch (Exception e) { logger.warn("[푸시] 키 준비 실패: {}", e.getMessage()); return null; }
    }

    public static boolean subscribe(String endpoint, String kakaoId, String userName) {
        S55AdminPush p = inst;
        if (p == null || endpoint == null || !endpoint.startsWith("https://") || endpoint.length() > 1000) return false;
        p.dao.upsertS55PushSub(endpoint, kakaoId, userName);
        return true;
    }

    /** 같은 key는 gapMs 안에 한 번만(예: 같은 유저 접속 알림 30분에 한 번). */
    public static void notifyOnce(String key, long gapMs, String title, String body) {
        long now = System.currentTimeMillis();
        Long last = THROTTLE.get(key);
        if (last != null && now - last < gapMs) return;
        THROTTLE.put(key, now);
        if (THROTTLE.size() > 5000) THROTTLE.clear();
        notify(title, body);
    }

    /** 모든 관리자 구독에 알림(내용은 메모리에 두고, 폰에는 "알림 있음"만). */
    public static void notify(String title, String body) {
        S55AdminPush p = inst;
        if (p == null) return;
        POOL.execute(() -> p.send(title, body));
    }

    /** 서비스 워커가 깨서 가져가는 그 폰 몫의 알림(가져가면 비운다). */
    public static List<HashMap<String, Object>> pull(String endpoint) {
        List<HashMap<String, Object>> out = new ArrayList<>();
        Deque<HashMap<String, Object>> q = endpoint == null ? null : QUEUE.get(endpoint);
        if (q != null) synchronized (q) { out.addAll(q); q.clear(); }
        return out;
    }

    private void send(String title, String body) {
        List<String> subs;
        try { subs = dao.selectS55PushSubs(); keys(); } catch (Exception e) { logger.warn("[푸시] 준비 실패: {}", e.getMessage()); return; }
        if (subs == null || subs.isEmpty()) return;
        HashMap<String, Object> n = new HashMap<>();
        n.put("title", title);
        n.put("body", body);
        n.put("t", System.currentTimeMillis());
        for (String ep : subs) {
            Deque<HashMap<String, Object>> q = QUEUE.computeIfAbsent(ep, k -> new ArrayDeque<>());
            synchronized (q) { q.addLast(n); while (q.size() > QUEUE_MAX) q.removeFirst(); }
            try {
                int code = post(ep);
                if (code == 404 || code == 410) { dao.deleteS55PushSub(ep); QUEUE.remove(ep); }
                else if (code >= 300) logger.warn("[푸시] {} 응답 {}", ep.substring(0, Math.min(60, ep.length())), code);
            } catch (Exception e) {
                logger.warn("[푸시] 보내기 실패: {}", e.getMessage());
            }
        }
    }

    private int post(String endpoint) throws Exception {
        URL u = new URL(endpoint);
        String aud = u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(0);
        c.setRequestProperty("TTL", "86400");
        c.setRequestProperty("Urgency", "high");
        c.setRequestProperty("Authorization", "vapid t=" + jwt(aud) + ", k=" + pubB64);
        c.getOutputStream().close();
        int code = c.getResponseCode();
        c.disconnect();
        return code;
    }

    /** VAPID JWT(ES256) -- 12시간 유효. */
    private String jwt(String aud) throws Exception {
        Base64.Encoder b = Base64.getUrlEncoder().withoutPadding();
        String head = b.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        long exp = System.currentTimeMillis() / 1000 + 12 * 3600;
        String claims = b.encodeToString(("{\"aud\":\"" + aud + "\",\"exp\":" + exp + ",\"sub\":\"https://rgb-tns.dev-apc.com/mine/\"}").getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(priv);
        s.update((head + "." + claims).getBytes(StandardCharsets.UTF_8));
        return head + "." + claims + "." + b.encodeToString(derToRaw(s.sign()));
    }

    /** DER(ASN.1) ECDSA 서명 -> JWT가 쓰는 r||s 64바이트. */
    private static byte[] derToRaw(byte[] der) {
        int i = 3; // 30 len 02
        int rl = der[i++] & 0xff;
        byte[] r = new byte[rl]; System.arraycopy(der, i, r, 0, rl); i += rl;
        i++; // 02
        int sl = der[i++] & 0xff;
        byte[] sv = new byte[sl]; System.arraycopy(der, i, sv, 0, sl);
        byte[] out = new byte[64];
        System.arraycopy(fix32(r), 0, out, 0, 32);
        System.arraycopy(fix32(sv), 0, out, 32, 32);
        return out;
    }

    private static byte[] fix32(byte[] v) {
        byte[] o = new byte[32];
        int n = Math.min(32, v.length);
        System.arraycopy(v, v.length - n, o, 32 - n, n);
        return o;
    }

    /** VAPID 키 -- 설정값에 없으면 만들어 넣는다(개인키는 DB 설정값에만, 리포지토리엔 없음). */
    private synchronized void keys() throws Exception {
        if (priv != null && pubB64 != null) return;
        String pub = dao.selectConfigValue("S55_VAPID_PUB"), pk = dao.selectConfigValue("S55_VAPID_PRIV");
        if (pub == null || pk == null || pub.isEmpty() || pk.isEmpty()) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = g.generateKeyPair();
            ECPublicKey ep = (ECPublicKey) kp.getPublic();
            byte[] raw = new byte[65];
            raw[0] = 4;
            System.arraycopy(fix32(unsigned(ep.getW().getAffineX())), 0, raw, 1, 32);
            System.arraycopy(fix32(unsigned(ep.getW().getAffineY())), 0, raw, 33, 32);
            pub = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            pk = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
            dao.upsertConfigValue("S55_VAPID_PUB", pub, "web mine push VAPID public key (base64url)");
            dao.upsertConfigValue("S55_VAPID_PRIV", pk, "web mine push VAPID private key (PKCS8 base64)");
        }
        priv = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pk)));
        pubB64 = pub;
    }

    private static byte[] unsigned(BigInteger v) {
        byte[] b = v.toByteArray();
        return b.length > 1 && b[0] == 0 ? java.util.Arrays.copyOfRange(b, 1, b.length) : b;
    }
}
