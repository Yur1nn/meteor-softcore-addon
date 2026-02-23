package com.softcore.addon.modules;

import com.softcore.addon.SoftcoreAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ChestReopenHelper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Boolean> autoReopen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-reopen")
        .description("Automatically reopen chest after closing")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> reopenDelayTicks = sgTiming.add(new IntSetting.Builder()
        .name("reopen-delay")
        .description("Ticks to wait before reopening (0 = same tick, 1 = next tick)")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderMax(10)
        .visible(autoReopen::get)
        .build()
    );

    private final Setting<Integer> reopenCount = sgGeneral.add(new IntSetting.Builder()
        .name("reopen-count")
        .description("Number of times to reopen (for testing race conditions)")
        .defaultValue(1)
        .min(1)
        .max(10)
        .sliderMax(5)
        .visible(autoReopen::get)
        .build()
    );

    private final Setting<Boolean> blockClosePacket = sgGeneral.add(new BoolSetting.Builder()
        .name("block-close-packet")
        .description("Block close packet while reopening")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifyActions = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Notify on close/reopen actions")
        .defaultValue(true)
        .build()
    );

    private BlockPos lastChestPos = null;
    private int ticksUntilReopen = 0;
    private int reopensRemaining = 0;
    private boolean shouldReopen = false;

    public ChestReopenHelper() {
        super(SoftcoreAddon.CATEGORY, "chest-reopen-helper", 
            "Quickly reopens chests after closing. Tests race condition with scheduled chest destruction. " +
            "Death Chest plugin destroys empty chests 1 tick after close.");
    }

    @Override
    public void onActivate() {
        lastChestPos = null;
        ticksUntilReopen = 0;
        reopensRemaining = 0;
        shouldReopen = false;
        info("§eChest reopen helper activated!");
        info("§7Take all items and close - will reopen before destruction");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof CloseHandledScreenC2SPacket) {
            // Store the last chest position we were looking at
            if (mc.crosshairTarget instanceof BlockHitResult hit) {
                BlockPos pos = hit.getBlockPos();
                BlockState state = mc.world.getBlockState(pos);
                
                if (state.getBlock() instanceof ChestBlock) {
                    lastChestPos = pos;
                }
            }

            // If we don't have a direct hit, try to find chest in front of player
            if (lastChestPos == null && mc.player != null) {
                Vec3d eyePos = mc.player.getEyePos();
                Vec3d lookVec = mc.player.getRotationVec(1.0f);
                
                for (int i = 1; i <= 5; i++) {
                    BlockPos checkPos = new BlockPos(
                        (int) (eyePos.x + lookVec.x * i),
                        (int) (eyePos.y + lookVec.y * i),
                        (int) (eyePos.z + lookVec.z * i)
                    );
                    
                    if (mc.world.getBlockState(checkPos).getBlock() instanceof ChestBlock) {
                        lastChestPos = checkPos;
                        break;
                    }
                }
            }

            if (autoReopen.get() && lastChestPos != null) {
                shouldReopen = true;
                ticksUntilReopen = reopenDelayTicks.get();
                reopensRemaining = reopenCount.get();
                
                if (notifyActions.get()) {
                    ChatUtils.info("§6🔄 Chest closed! Will reopen in " + 
                        ticksUntilReopen + " ticks (" + reopensRemaining + "x)");
                }

                if (blockClosePacket.get()) {
                    event.cancel();
                    if (notifyActions.get()) {
                        ChatUtils.info("§c❌ Blocked close packet!");
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!shouldReopen || lastChestPos == null) return;

        if (ticksUntilReopen > 0) {
            ticksUntilReopen--;
            return;
        }

        // Attempt to reopen the chest
        if (reopensRemaining > 0 && mc.player != null && mc.interactionManager != null) {
            BlockState state = mc.world.getBlockState(lastChestPos);
            
            if (state.getBlock() instanceof ChestBlock) {
                // Create a block hit result for the chest
                BlockHitResult hitResult = new BlockHitResult(
                    Vec3d.ofCenter(lastChestPos),
                    Direction.UP,
                    lastChestPos,
                    false
                );

                // Interact with the chest
                mc.interactionManager.interactBlock(
                    mc.player,
                    Hand.MAIN_HAND,
                    hitResult
                );

                reopensRemaining--;
                
                if (notifyActions.get()) {
                    ChatUtils.info("§a✓ Reopened chest! (" + reopensRemaining + " remaining)");
                }

                if (reopensRemaining > 0) {
                    ticksUntilReopen = reopenDelayTicks.get();
                } else {
                    shouldReopen = false;
                    lastChestPos = null;
                }
            } else {
                if (notifyActions.get()) {
                    ChatUtils.info("§c❌ Chest no longer exists or was destroyed!");
                }
                shouldReopen = false;
                lastChestPos = null;
            }
        }
    }
}
