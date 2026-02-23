package com.softcore.addon.modules;

import com.softcore.addon.SoftcoreAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

import java.util.ArrayList;
import java.util.List;

public class InventoryCloseDelayer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Number of ticks to delay close packets (20 ticks = 1 second)")
        .defaultValue(5)
        .min(0)
        .max(100)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> cancelAfterDelay = sgGeneral.add(new BoolSetting.Builder()
        .name("cancel-after-delay")
        .description("Cancel the packet instead of sending after delay")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifyOnDelay = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-on-delay")
        .description("Notify when close packet is delayed/sent")
        .defaultValue(true)
        .build()
    );

    private static class DelayedPacket {
        CloseHandledScreenC2SPacket packet;
        int ticksRemaining;

        DelayedPacket(CloseHandledScreenC2SPacket packet, int ticks) {
            this.packet = packet;
            this.ticksRemaining = ticks;
        }
    }

    private final List<DelayedPacket> delayedPackets = new ArrayList<>();

    public InventoryCloseDelayer() {
        super(SoftcoreAddon.CATEGORY, "inventory-close-delayer", 
            "Delays inventory close packets by a specified number of ticks. For testing race conditions.");
    }

    @Override
    public void onActivate() {
        delayedPackets.clear();
        info("Close delayer activated. Delaying by " + delayTicks.get() + " ticks.");
    }

    @Override
    public void onDeactivate() {
        // Send all remaining packets
        for (DelayedPacket dp : delayedPackets) {
            if (!cancelAfterDelay.get() && mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(dp.packet);
                if (notifyOnDelay.get()) {
                    ChatUtils.info("§eSent delayed close packet on deactivation");
                }
            }
        }
        delayedPackets.clear();
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof CloseHandledScreenC2SPacket) {
            CloseHandledScreenC2SPacket packet = (CloseHandledScreenC2SPacket) event.packet;
            
            if (delayTicks.get() > 0) {
                event.cancel();
                delayedPackets.add(new DelayedPacket(packet, delayTicks.get()));
                
                if (notifyOnDelay.get()) {
                    ChatUtils.info("§6Delayed close packet! Will " + 
                        (cancelAfterDelay.get() ? "cancel" : "send") + " in " + 
                        delayTicks.get() + " ticks");
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (delayedPackets.isEmpty()) return;

        List<DelayedPacket> toRemove = new ArrayList<>();

        for (DelayedPacket dp : delayedPackets) {
            dp.ticksRemaining--;

            if (dp.ticksRemaining <= 0) {
                if (!cancelAfterDelay.get() && mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(dp.packet);
                    if (notifyOnDelay.get()) {
                        ChatUtils.info("§aSent delayed close packet!");
                    }
                } else if (notifyOnDelay.get()) {
                    ChatUtils.info("§cCanceled close packet after delay!");
                }
                toRemove.add(dp);
            }
        }

        delayedPackets.removeAll(toRemove);
    }
}
