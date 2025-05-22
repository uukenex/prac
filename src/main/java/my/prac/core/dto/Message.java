package my.prac.core.dto;

public class Message {
    private String role;    // user or assistant
    private String content;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
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