package my.prac.core.util;

import java.util.LinkedList;
import java.util.List;

import my.prac.core.dto.Message;

public class FixedSizeMessageQueue {
    private final int maxSize;
    private final LinkedList<Message> messages = new LinkedList<>();

    public FixedSizeMessageQueue(int maxSize) {
        this.maxSize = maxSize;
    }

    public void add(Message msg) {
        if (messages.size() >= maxSize) messages.removeFirst();
        messages.add(msg);
    }

    public List<Message> getAll() {
        return new LinkedList<>(messages);
    }

    public String toJsonArray() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            sb.append(messages.get(i).toJson());
            if (i < messages.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}