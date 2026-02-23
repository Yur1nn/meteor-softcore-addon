package com.softcore.addon.modules;

import com.softcore.addon.SoftcoreAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

public class RaceConditionTester extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSequence = settings.createGroup("Sequence");

    private final Setting<ExploitMode> mode = sgGeneral.add(new EnumSetting.Builder<ExploitMode>()
        .name("mode")
        .description("Which race condition exploit to test")
        .defaultValue(ExploitMode.EMPTY_TIMING)
        .build()
    );

    private final Setting<Boolean> autoExecute = sgSequence.add(new BoolSetting.Builder()
        .name("auto-execute")
        .description("Automatically execute the exploit sequence")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> step1Delay = sgSequence.add(new IntSetting.Builder()
        .name("step1-delay")
        .description("Ticks between taking items and putting back")
        .defaultValue(2)
        .min(0)
        .max(20)
        .sliderMax(10)
        .visible(() -> mode.get() == ExploitMode.EMPTY_TIMING && autoExecute.get())
        .build()
    );

    private final Setting<Integer> step2Delay = sgSequence.add(new IntSetting.Builder()
        .name("step2-delay")
        .description("Ticks between putting back and closing")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(10)
        .visible(() -> mode.get() == ExploitMode.EMPTY_TIMING && autoExecute.get())
        .build()
    );

    private final Setting<Boolean> blockClosePacket = sgGeneral.add(new BoolSetting.Builder()
        .name("block-close")
        .description("Block close packet during exploit")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifySteps = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Notify each step of the exploit")
        .defaultValue(true)
        .build()
    );

    public enum ExploitMode {
        EMPTY_TIMING("Empty Timing", "Take items, put back, close during transit"),
        DRAG_DESYNC("Drag Desync", "Drag items and close mid-drag"),
        CURSOR_SWAP("Cursor Swap", "Swap items and close during operation");

        public final String title;
        public final String description;

        ExploitMode(String title, String description) {
            this.title = title;
            this.description = description;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private int sequenceStep = 0;
    private int ticksUntilNextStep = 0;
    private boolean inExploit = false;

    public RaceConditionTester() {
        super(SoftcoreAddon.CATEGORY, "race-condition-tester", 
            "Tests various race condition exploits on Death Chest. " +
            "Attempts to cause desyncs by manipulating timing of inventory operations.");
    }

    @Override
    public void onActivate() {
        sequenceStep = 0;
        ticksUntilNextStep = 0;
        inExploit = false;
        info("§eRace condition tester activated!");
        info("§7Mode: §f" + mode.get().title);
        info("§7" + mode.get().description);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        // Monitor for exploit-relevant packets
        if (event.packet instanceof ClickSlotC2SPacket) {
            ClickSlotC2SPacket packet = (ClickSlotC2SPacket) event.packet;
            
            if (!inExploit && notifySteps.get()) {
                ChatUtils.info("§6🔍 Item interaction detected");
            }
            
            if (inExploit && notifySteps.get()) {
                ChatUtils.info("§6📤 Item interaction during exploit");
            }
        }

        // Handle close packet during exploit
        if (event.packet instanceof CloseHandledScreenC2SPacket && inExploit) {
            if (blockClosePacket.get()) {
                event.cancel();
                if (notifySteps.get()) {
                    ChatUtils.info("§c❌ BLOCKED close during race condition!");
                }
            } else if (notifySteps.get()) {
                ChatUtils.info("§e✓ Close packet sent during race condition!");
            }
            inExploit = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!autoExecute.get() || !(mc.currentScreen instanceof HandledScreen)) {
            return;
        }

        // Auto-execution sequence (simplified for safety)
        if (sequenceStep > 0 && ticksUntilNextStep > 0) {
            ticksUntilNextStep--;
            if (ticksUntilNextStep == 0) {
                executeNextStep();
            }
        }
    }

    private void executeNextStep() {
        if (notifySteps.get()) {
            ChatUtils.info("§eExecuting step " + sequenceStep + " of exploit sequence");
        }

        switch (mode.get()) {
            case EMPTY_TIMING:
                executeEmptyTimingStep();
                break;
            case DRAG_DESYNC:
                executeDragDesyncStep();
                break;
            case CURSOR_SWAP:
                executeCursorSwapStep();
                break;
        }
    }

    private void executeEmptyTimingStep() {
        // This is intentionally simplified - manual execution is safer
        if (notifySteps.get()) {
            ChatUtils.info("§7Manual steps: 1) Take all items 2) Put back 3) Close immediately");
        }
    }

    private void executeDragDesyncStep() {
        if (notifySteps.get()) {
            ChatUtils.info("§7Manual steps: 1) Click & hold items 2) Drag across slots 3) Close mid-drag");
        }
    }

    private void executeCursorSwapStep() {
        if (notifySteps.get()) {
            ChatUtils.info("§7Manual steps: 1) Pick up item 2) Click another to swap 3) Close immediately");
        }
    }

    public void startExploit() {
        inExploit = true;
        sequenceStep = 1;
        ticksUntilNextStep = step1Delay.get();
        
        if (notifySteps.get()) {
            ChatUtils.info("§c⚠ EXPLOIT SEQUENCE STARTED!");
        }
    }
}
