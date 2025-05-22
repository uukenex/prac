package my.prac.core.util;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class RoomContextService {
    private final ConcurrentHashMap<String, FixedSizeMessageQueue> roomMap = new ConcurrentHashMap<>();

    public FixedSizeMessageQueue getQueue(String roomName) {
        return roomMap.computeIfAbsent(roomName, k -> new FixedSizeMessageQueue(20));
    }
}