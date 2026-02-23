package com.softcore.addon.modules;

import com.softcore.addon.SoftcoreAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class DeathChestDebugger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> trackInventoryCounts = sgGeneral.add(new BoolSetting.Builder()
        .name("track-inventory")
        .description("Track your inventory item count on each tick")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> trackNearbyChests = sgGeneral.add(new BoolSetting.Builder()
        .name("track-chests")
        .description("Track nearby chest item counts")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> logOnDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("log-on-disconnect")
        .description("Log inventory status when disconnecting")
        .defaultValue(true)
        .build()
    );

    private int lastInventoryCount = 0;
    private int tickCounter = 0;

    public DeathChestDebugger() {
        super(SoftcoreAddon.CATEGORY, "death-chest-debugger", 
            "Debugs death-chest dupe by tracking inventory and chest states");
    }

    @Override
    public void onActivate() {
        lastInventoryCount = countInventoryItems();
        tickCounter = 0;
        info("Death-Chest Debugger activated. Starting inventory count: " + lastInventoryCount);
    }

    @Override
    public void onDeactivate() {
        if (logOnDisconnect.get()) {
            int finalCount = countInventoryItems();
            info("§cFinal inventory count: " + finalCount + " (change: " + (finalCount - lastInventoryCount) + ")");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null) return;

        tickCounter++;

        if (trackInventoryCounts.get() && tickCounter % 20 == 0) {
            int currentCount = countInventoryItems();
            if (currentCount != lastInventoryCount) {
                ChatUtils.info("§eInventory changed! Previous: " + lastInventoryCount + " Current: " + currentCount);
                lastInventoryCount = currentCount;
            }
        }

        if (trackNearbyChests.get() && tickCounter % 20 == 0) {
            checkNearbyChests();
        }
    }

    private int countInventoryItems() {
        if (mc.player == null) return 0;
        
        PlayerInventory inv = mc.player.getInventory();
        int total = 0;
        
        for (int i = 0; i < inv.size(); i++) {
            if (!inv.getStack(i).isEmpty()) {
                total += inv.getStack(i).getCount();
            }
        }
        
        return total;
    }

    private void checkNearbyChests() {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int range = 16;
        int foundChests = 0;
        
        try {
            // Check a more manageable 16-block radius (instead of 32)
            for (int x = -range; x <= range; x++) {
                for (int y = -range; y <= range; y++) {
                    for (int z = -range; z <= range; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        try {
                            if (mc.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                                if (mc.world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                                    int itemCount = 0;
                                    for (int i = 0; i < chest.size(); i++) {
                                        if (!chest.getStack(i).isEmpty()) {
                                            itemCount += chest.getStack(i).getCount();
                                        }
                                    }
                                    
                                    ChatUtils.info("§eChest at " + pos.getX() + "," + pos.getY() + "," + pos.getZ() + 
                                                 ": " + itemCount + " items");
                                    foundChests++;
                                }
                            }
                        } catch (Exception e) {
                            // Skip problematic blocks
                        }
                    }
                }
            }
            if (foundChests == 0) {
                ChatUtils.info("§7No nearby chests with items found");
            }
        } catch (Exception e) {
            ChatUtils.info("§cChest scan error: " + e.getMessage());
        }
    }
}
