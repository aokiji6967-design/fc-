package org.loveroo.fireclient.mixin.settings;

import org.loveroo.fireclient.data.LivePing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.query.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;

/**
 * Times the vanilla ping exchange so the HUD can show the real round trip time.
 *
 * The player list entry only holds the latency the server reported when joining,
 * which on many SMPs stays at whatever it was during the handshake and never
 * matches the connection the player is actually on.
 *
 * The server sends a CommonPing packet, the client answers it, and the server
 * replies with PingResult. Timing that full cycle gives the real ping.
 */
@Mixin(ClientCommonNetworkHandler.class)
public class MeasurePingMixin {

    /**
     * The server is asking us to answer a ping, so this is the start of a round trip.
     */
    @Inject(method = "onPing", at = @At("HEAD"))
    private void pingSent(CommonPingS2CPacket packet, CallbackInfo info) {
        LivePing.onSend(System.nanoTime());
    }
}

@Mixin(ClientPlayNetworkHandler.class)
public class MeasurePingResponseMixin {

    /**
     * The server answered our ping, so the round trip is complete.
     */
    @Inject(method = "onPingResult", at = @At("HEAD"))
    private void pingReceived(PingResultS2CPacket packet, CallbackInfo info) {
        LivePing.onResponse(System.nanoTime());
    }
}