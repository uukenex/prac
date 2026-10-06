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

    /** cutoff(ms)보다 오래된 메시지를 버린다. */
    public synchronized void removeOlderThan(long cutoff) {
        messages.removeIf(m -> m.getTime() < cutoff);
    }

    public synchronized List<Message> getAll() {
        return new ArrayList<>(messages);
    }
}