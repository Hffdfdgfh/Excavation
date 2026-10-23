package dev.excavation;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExcavationNetworking {
    private static final Map<UUID, Map<Integer, Integer>> MODES = new HashMap<>();

    public record CycleModePayload(int slot, int mode) implements CustomPacketPayload {
        public static final Type<CycleModePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("excavation", "cycle_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CycleModePayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.slot());
                    buffer.writeVarInt(payload.mode());
                },
                buffer -> new CycleModePayload(buffer.readVarInt(), buffer.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    private ExcavationNetworking() {}

    public static void registerServer() {
        PayloadTypeRegistry.serverboundPlay().register(CycleModePayload.ID, CycleModePayload.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MODES.remove(handler.player.getUUID()));
        ServerPlayNetworking.registerGlobalReceiver(CycleModePayload.ID, (payload, context) -> context.server().execute(() -> {
            ServerPlayer player = context.player();
            if (payload.slot() < 0 || payload.slot() >= player.getInventory().getContainerSize()) {
                return;
            }
            ItemStack stack = player.getInventory().getItem(payload.slot());
            int max = ExcavationTools.maximumMode(stack);
            if (payload.mode() < 0 || payload.mode() > max) {
                return;
            }
            MODES.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()).put(payload.slot(), payload.mode());
        }));
    }

    public static int mode(UUID player, int slot) {
        return MODES.getOrDefault(player, Map.of()).getOrDefault(slot, 0);
    }

    public static int cycleLocal(int[] modes, int slot, int max) {
        modes[slot] = (modes[slot] + 1) % (max + 1);
        return modes[slot];
    }
}