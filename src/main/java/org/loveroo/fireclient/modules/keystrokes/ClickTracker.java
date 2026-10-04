package org.loveroo.fireclient.modules.keystrokes;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Counts mouse clicks per second for the Keystrokes module.
 *
 * Each click is stored as the timestamp at which it falls out of the one second
 * window (press time + 1000ms), so reading the count is a plain compare against
 * the current time and never has to recompute an offset. Only press edges are
 * registered, so holding the button down does not inflate the count.
 */
public final class ClickTracker {

    private static final long WINDOW_MS = 1000L;

    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();

    private ClickTracker() { }

    public static void registerLeftClick() {
        leftClicks.addLast(System.currentTimeMillis() + WINDOW_MS);
    }

    public static void registerRightClick() {
        rightClicks.addLast(System.currentTimeMillis() + WINDOW_MS);
    }

    public static int getLeftCps() {
        return prune(leftClicks);
    }

    public static int getRightCps() {
        return prune(rightClicks);
    }

    private static int prune(Deque<Long> clicks) {
        var now = System.currentTimeMillis();

        while(!clicks.isEmpty() && clicks.peekFirst() <= now) {
            clicks.removeFirst();
        }

        return clicks.size();
    }

    /**
     * Clears both counters, used when the module is reloaded.
     */
    public static void reset() {
        leftClicks.clear();
        rightClicks.clear();
    }
}
