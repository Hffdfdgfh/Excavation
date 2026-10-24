package dev.excavation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.state.BlockState;

public final class ExcavationTools {
    public static final String PICKAXE = "excavation:pickaxe";
    public static final String SHOVEL = "excavation:shovel";
    public static final String HOE = "excavation:hoe";
    private ExcavationTools() {}

    public static int enchantmentLevel(ItemStack stack) {
        String expected = enchantmentFor(stack);
        if (expected == null) {
            return 0;
        }
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.unwrapKey().map(key -> key.identifier().toString().equals(expected)).orElse(false)) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }

    public static int activeSize(ServerPlayer player) {
        String type = enchantmentFor(player.getMainHandItem());
        if (type == SHOVEL || type == HOE) {
            return 1;
        }

        int level = enchantmentLevel(player.getMainHandItem());
        int mode = ExcavationNetworking.mode(player.getUUID(), player.getInventory().getSelectedSlot());
        if (level == 0 || mode == 0 || mode > level) {
            return 1;
        }
        return mode * 2 + 1;
    }

    public static int maximumMode(ItemStack stack) {
        return Math.min(3, enchantmentLevel(stack));
    }

    public static Direction.Axis planeAxis(Player player) {
        return Math.abs(player.getXRot()) >= 45.0F ? Direction.Axis.Y : player.getDirection().getAxis();
    }

    public static String enchantmentFor(ItemStack stack) {
        if (stack.is(Items.WOODEN_PICKAXE) || stack.is(Items.STONE_PICKAXE) || stack.is(Items.IRON_PICKAXE)
                || stack.is(Items.GOLDEN_PICKAXE) || stack.is(Items.DIAMOND_PICKAXE)) {
            return PICKAXE;
        }
        if (stack.is(Items.WOODEN_SHOVEL) || stack.is(Items.STONE_SHOVEL) || stack.is(Items.IRON_SHOVEL)
                || stack.is(Items.GOLDEN_SHOVEL) || stack.is(Items.DIAMOND_SHOVEL)) {
            return SHOVEL;
        }
        if (stack.is(Items.WOODEN_HOE) || stack.is(Items.STONE_HOE) || stack.is(Items.IRON_HOE)
                || stack.is(Items.GOLDEN_HOE) || stack.is(Items.DIAMOND_HOE)) {
            return HOE;
        }
        return null;
    }

    public static void breakArea(ServerPlayer player, BlockPos center, int size) {
        Direction.Axis normal = planeAxis(player);
        int radius = size / 2;
        for (int first = -radius; first <= radius; first++) {
            for (int second = -radius; second <= radius; second++) {
                if (first == 0 && second == 0) {
                    continue;
                }
                BlockPos target = switch (normal) {
                    case X -> center.offset(0, first, second);
                    case Y -> center.offset(first, 0, second);
                    case Z -> center.offset(first, second, 0);
                };
                BlockState targetState = player.level().getBlockState(target);
                if (!targetState.isAir() && targetState.getDestroySpeed(player.level(), target) >= 0) {
                    player.gameMode.destroyBlock(target);
                }
            }
        }
    }
}