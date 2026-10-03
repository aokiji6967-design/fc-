package org.loveroo.fireclient.mixin.settings;

import org.loveroo.fireclient.data.LivePing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import net.minecraft.util.Util;

/**
 * Times the vanilla query ping exchange so the HUD can show the real round trip time.
 *
 * The player list entry only holds the latency the server reported when joining,
 * which on many SMPs stays at whatever it was during the handshake and never
 * matches the connection the player is actually on.
 *
 * Vanilla has a ping exchange for exactly this, but PingMeasurer only fires it
 * while the debug screen is showing its ping chart, so on its own it updates
 * only while the player is holding F3. Here the client drives the same exchange
 * itself once a second instead: it sends a QueryPingC2SPacket stamped with the
 * current time, and the server echoes that stamp back in PingResultS2CPacket.
 * Timing between those two ends measures the live connection.
 *
 * Note this is a different exchange from the server's own CommonPingS2CPacket,
 * which asks the client to acknowledge a ping so the server can measure its
 * latency to us. That one never travels in the direction being measured here.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class MeasurePingMixin {

    /**
     * Ticks between ping requests. Twenty is roughly one per second, which keeps
     * a fresh number on screen without flooding the connection with packets.
     */
    private static final int PING_INTERVAL_TICKS = 20;

    /**
     * Counts down to the next ping, per connection, so the first ping happens one
     * interval after joining rather than immediately on the first tick.
     */
    @Unique
    private int fireclient$pingTicks;

    /**
     * The client is sending a ping request, so this is the start of a round trip.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void sendPing(CallbackInfo info) {
        if(++this.fireclient$pingTicks < PING_INTERVAL_TICKS) {
            return;
        }

        this.fireclient$pingTicks = 0;

        LivePing.onSend(System.nanoTime());

        var packet = new QueryPingC2SPacket(Util.getMeasuringTimeMs());
        ((ClientPlayNetworkHandler)(Object)this).sendPacket(packet);
    }

    @Mixin(ClientPlayNetworkHandler.class)
    public static class MeasurePingResponseMixin {

        /**
         * The server answered our ping, so the round trip is complete.
         */
        @Inject(method = "onPingResult", at = @At("HEAD"))
        private void pingReceived(PingResultS2CPacket packet, CallbackInfo info) {
            LivePing.onResponse(System.nanoTime());
        }
    }
}