package com.bingo.hotel.spa.intl.core.poll;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PollThreadManager {

    private final static Map<String, AbstractPollThread> pollThreadMap = new ConcurrentHashMap<String, AbstractPollThread>();

    private final static PollThreadManager instance = new PollThreadManager();


    public static PollThreadManager get() {
        return instance;
    }

    public void register(String threadName, AbstractPollThread pollThread) {
        pollThreadMap.put(threadName, pollThread);
    }

    public AbstractPollThread getPollThread(String threadName) {
        return pollThreadMap.get(threadName);
    }

    public Map<String, AbstractPollThread> getPollThreads() {
        return pollThreadMap;
    }
}
