package my.prac.core.dto;

public class Message {
    private String role;    // user or assistant
    private String content;
    private long time;      // 메시지 시각(ms) -- 오래된 대화를 잊을 때 사용

    public Message(String role, String content) {
        this(role, content, System.currentTimeMillis());
    }

    public Message(String role, String content, long time) {
        this.role = role;
        this.content = content;
        this.time = time;
    }

    public long getTime() {
        return time;
    }
    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
    public String toJson() {
        return String.format("{\"role\":\"%s\",\"content\":\"%s\"}",
            role, content.replace("\"", "\\\""));
    }
}