package org.loveroo.fireclient.data;

/**
 * Measures the real client-to-server round trip time.
 *
 * The vanilla player list entry caches whatever latency the server reported when
 * the player joined, which is why the tab can read 1ms while the connection is
 * actually far slower. That value only changes when the server decides to resend
 * it, so on many SMPs it stays wrong for the whole session.
 *
 * This instead times the protocol's own ping exchange, which the client sends
 * roughly once a second while connected, so the number shown tracks the live
 * connection.
 *
 * Reading is a plain volatile long: the render thread and the network thread both
 * touch this, and a ping a few milliseconds stale is fine.
 */
public final class LivePing {

    /**
     * When the client last sent a ping request, or -1 if never.
     */
    private static volatile long sentAt = -1;

    /**
     * The most recent measured round trip, or -1 if none has completed yet.
     */
    private static volatile int latency = -1;

    /**
     * Ping values above this are treated as nonsense (a world border crossing, a
     * server hiccup, or a paused game) and are ignored so the HUD does not flicker.
     */
    private static final int MAX_PLAUSIBLE_PING = 5000;

    private LivePing() { }

    /**
     * Called when the client sends a ping request.
     */
    public static void onSend(long now) {
        sentAt = now;
    }

    /**
     * Called when the server answers a ping request.
     *
     * Ignores the response if no matching send was recorded, which happens on the
     * first response if the client connected mid exchange.
     */
    public static void onResponse(long now) {
        var start = sentAt;

        if(start < 0) {
            return;
        }

        sentAt = -1;

        var elapsed = (int)Math.round((now - start) / 1_000_000.0);

        // a clock adjustment or a thread scheduling hiccup can produce a nonsense
        // value, so only accept a plausible round trip
        if(elapsed < 0 || elapsed > MAX_PLAUSIBLE_PING) {
            return;
        }

        latency = elapsed;
    }

    /**
     * Last measured round trip in milliseconds, or -1 if none yet.
     */
    public static int get() {
        return latency;
    }

    /**
     * Forgets the measurement, so joining another server does not briefly show the
     * previous server's ping.
     */
    public static void reset() {
        sentAt = -1;
        latency = -1;
    }
}