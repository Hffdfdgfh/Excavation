package dev.excavation;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.Arrays;

public final class ExcavationClient implements ClientModInitializer {
    private static final int[] MODES = new int[9];
    private static final int REQUIRED_TAPS = 5;
    private static final long TAP_WINDOW_MS = 1200;
    private static int tapCount;
    private static long lastTap;
    private static int lastSelectedSlot = -1;
    private static boolean shiftWasDown;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(ExcavationClient::tick);
        LevelRenderEvents.BEFORE_GIZMOS.register(ExcavationClient::renderOutline);
    }

    private static void tick(Minecraft client) {
        if (client.player == null) {
            lastSelectedSlot = -1;
            tapCount = 0;
            shiftWasDown = false;
            Arrays.fill(MODES, 0);
            return;
        }
        int slot = client.player.getInventory().getSelectedSlot();
        if (slot != lastSelectedSlot) {
            lastSelectedSlot = slot;
            if (ExcavationTools.maximumMode(client.player.getMainHandItem()) > 0) {
                showMode(client, slot);
            }
        }

        long now = System.currentTimeMillis();
        boolean shiftDown = client.options.keyShift.isDown();
        int max = ExcavationTools.maximumMode(client.player.getMainHandItem());
        if (max > 0 && shiftDown && !shiftWasDown) {
            tapCount = now - lastTap <= TAP_WINDOW_MS ? tapCount + 1 : 1;
            lastTap = now;
            if (tapCount == REQUIRED_TAPS) {
                tapCount = 0;
                int mode = ExcavationNetworking.cycleLocal(MODES, slot, max);
                ClientPlayNetworking.send(new ExcavationNetworking.CycleModePayload(slot, mode));
                showMode(client, slot);
                client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.1F + mode * 0.08F);
            }
        } else if (max == 0) {
            tapCount = 0;
        }
        if (now - lastTap > TAP_WINDOW_MS) {
            tapCount = 0;
        }
        shiftWasDown = shiftDown;
    }

    private static void showMode(Minecraft client, int slot) {
        if (client.player == null || ExcavationTools.maximumMode(client.player.getMainHandItem()) == 0) {
            return;
        }
        int mode = MODES[slot];
        Component message = mode == 0
                ? Component.translatable("message.excavation.disabled")
                : Component.translatable("message.excavation.size", (mode * 2 + 1) + "x" + (mode * 2 + 1));
        client.gui.hud.setOverlayMessage(message, false);
    }

    private static void renderOutline(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.isSpectator() || !(client.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        int mode = MODES[client.player.getInventory().getSelectedSlot()];
        int max = ExcavationTools.maximumMode(client.player.getMainHandItem());
        if (mode == 0 || mode > max) {
            return;
        }

        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        int radius = mode;
        Direction.Axis normal = ExcavationTools.planeAxis(client.player);
        var pose = context.poseStack();
        var camera = context.levelState().cameraRenderState.pos;
        BlockPos center = hit.getBlockPos();

        for (int i = -radius; i <= radius; i++) {
            BlockPos edgeA = switch (normal) {
                case X -> center.offset(0, i, -radius);
                case Y -> center.offset(i, 0, -radius);
                case Z -> center.offset(i, -radius, 0);
            };
            BlockPos edgeB = switch (normal) {
                case X -> center.offset(0, i, radius);
                case Y -> center.offset(i, 0, radius);
                case Z -> center.offset(i, radius, 0);
            };
            pose.pushPose();
            pose.translate(edgeA.getX() - camera.x, edgeA.getY() - camera.y, edgeA.getZ() - camera.z);
            context.submitNodeCollector().submitShapeOutline(pose, Shapes.block(), RenderTypes.lines(), 0xFFF5F5F5, 1.2F, false);
            pose.popPose();

            pose.pushPose();
            pose.translate(edgeB.getX() - camera.x, edgeB.getY() - camera.y, edgeB.getZ() - camera.z);
            context.submitNodeCollector().submitShapeOutline(pose, Shapes.block(), RenderTypes.lines(), 0xFFF5F5F5, 1.2F, false);
            pose.popPose();
        }

        for (int i = -radius; i <= radius; i++) {
            BlockPos edgeA = switch (normal) {
                case X -> center.offset(0, -radius, i);
                case Y -> center.offset(-radius, 0, i);
                case Z -> center.offset(-radius, i, 0);
            };
            BlockPos edgeB = switch (normal) {
                case X -> center.offset(0, radius, i);
                case Y -> center.offset(radius, 0, i);
                case Z -> center.offset(radius, i, 0);
            };
            pose.pushPose();
            pose.translate(edgeA.getX() - camera.x, edgeA.getY() - camera.y, edgeA.getZ() - camera.z);
            context.submitNodeCollector().submitShapeOutline(pose, Shapes.block(), RenderTypes.lines(), 0xFFF5F5F5, 1.2F, false);
            pose.popPose();

            pose.pushPose();
            pose.translate(edgeB.getX() - camera.x, edgeB.getY() - camera.y, edgeB.getZ() - camera.z);
            context.submitNodeCollector().submitShapeOutline(pose, Shapes.block(), RenderTypes.lines(), 0xFFF5F5F5, 1.2F, false);
            pose.popPose();
        }
    }
}