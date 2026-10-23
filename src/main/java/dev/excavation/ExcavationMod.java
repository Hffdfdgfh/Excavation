package dev.excavation;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ExcavationMod implements ModInitializer {
    private static final Set<UUID> AREA_BREAKS = new HashSet<>();

    @Override
    public void onInitialize() {
        ExcavationNetworking.registerServer();
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof Level level) || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            if (AREA_BREAKS.contains(player.getUUID())) {
                return true;
            }

            int size = ExcavationTools.activeSize(serverPlayer);
            if (size <= 1) {
                return true;
            }

            AREA_BREAKS.add(player.getUUID());
            try {
                    ExcavationTools.breakArea(serverPlayer, pos, size);
            } finally {
                AREA_BREAKS.remove(player.getUUID());
            }
            return true;
        });
    }
}