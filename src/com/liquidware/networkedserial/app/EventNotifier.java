package com.liquidware.networkedserial.app;

import java.util.concurrent.ConcurrentLinkedQueue;

public class EventNotifier {
    private static final String TAG = "EventNotifier";
    private final ConcurrentLinkedQueue<Event> listeners;
    private final ConcurrentLinkedQueue<Event> addListeners;
    private int mIterating = 0;


    public EventNotifier (Event uiListener) {
        addListeners = new ConcurrentLinkedQueue<Event>();
        listeners = new ConcurrentLinkedQueue<Event>();
        listeners.add(uiListener);
    }

    public synchronized void addListener (Event listener) {
        if (mIterating == 0)
            listeners.add(listener);
        else
            addListeners.add(listener);
    }

    public synchronized void removeListener (Event listener) {
        listeners.remove(listener);
    }

    private void enterList() {
        mIterating++;
    }

    private void exitList() {

        mIterating--;

        if (mIterating != 0)
            return;

        /* Update list */
        for (Event listener : addListeners)
            listeners.add(listener);

        addListeners.clear();
    }

    public synchronized void onTimerTick(long millisUpTime) {
        enterList();

        for (Event listener : listeners)
            listener.onTimerTick(millisUpTime);

        exitList();
    }
}
