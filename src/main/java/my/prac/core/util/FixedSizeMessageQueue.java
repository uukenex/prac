package my.prac.core.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import my.prac.core.dto.Message;

public class FixedSizeMessageQueue {
    private final int maxSize;
    private final LinkedList<Message> messages;

    public FixedSizeMessageQueue(int maxSize) {
        this.maxSize = maxSize;
        this.messages = new LinkedList<>();
    }

    public synchronized void add(Message message) {
        if (messages.size() >= maxSize) {
            messages.removeFirst(); // 오래된 것 제거
        }
        messages.addLast(message);
    }

    public synchronized List<Message> getAll() {
        return new ArrayList<>(messages);
    }
}