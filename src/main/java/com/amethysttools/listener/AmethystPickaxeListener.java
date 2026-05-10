package com.amethysttools.listener;

import com.amethysttools.AmethystToolService;
import com.amethysttools.AmethystToolsPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

public class AmethystPickaxeListener implements Listener {

    private static final int MAX_TREE_BLOCKS = 128;

    private final AmethystToolsPlugin plugin;
    private final AmethystToolService toolService;

    public AmethystPickaxeListener(AmethystToolsPlugin plugin, AmethystToolService toolService) {
        this.plugin = plugin;
        this.toolService = toolService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!toolService.isAmethystTool(tool)) {
            return;
        }

        if (!player.hasPermission("amethysttools.use")) {
            event.setCancelled(true);
            player.sendMessage(toolService.msg("no-permission"));
            return;
        }

        if (toolService.isExpired(tool)) {
            player.getInventory().setItemInMainHand(null);
            event.setCancelled(true);
            player.sendMessage(toolService.msg("tool-expired"));
            return;
        }

        Block origin = event.getBlock();
        if (toolService.isBlocked(origin.getType())) {
            event.setCancelled(true);
            return;
        }

        if (toolService.isToolItem(tool, "amethyst_axe")) {
            handleAxeBreak(origin, tool);
            return;
        }

        int radius = toolService.getRadius();
        int depth = toolService.getDepth();
        BlockFace face = player.getTargetBlockFace(6);
        if (face == null) {
            face = getFallbackFace(player);
        }

        Set<Block> extraBlocks = collectExtraBlocks(origin, face, radius, depth);
        extraBlocks.remove(origin);

        for (Block block : extraBlocks) {
            if (block.getType() == Material.AIR || toolService.isBlocked(block.getType()) || toolService.isExcludedFromArea(block.getType())) {
                continue;
            }
            block.breakNaturally(tool);
        }
    }

    private void handleAxeBreak(Block origin, ItemStack tool) {
        if (!isTreeLog(origin.getType())) {
            return;
        }

        Set<Block> treeBlocks = collectMainTrunkBlocks(origin);
        treeBlocks.remove(origin);

        for (Block block : treeBlocks) {
            if (block.getType() == Material.AIR) {
                continue;
            }
            block.breakNaturally(tool);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ItemStack[] content = player.getInventory().getContents();
        for (int i = 0; i < content.length; i++) {
            content[i] = toolService.updateOrExpire(content[i], player);
        }
        player.getInventory().setContents(content);
    }

    private Set<Block> collectExtraBlocks(Block origin, BlockFace face, int radius, int depth) {
        Set<Block> result = new HashSet<>();
        int stepX = -face.getModX();
        int stepY = -face.getModY();
        int stepZ = -face.getModZ();

        for (int d = 0; d < depth; d++) {
            int baseX = origin.getX() + (stepX * d);
            int baseY = origin.getY() + (stepY * d);
            int baseZ = origin.getZ() + (stepZ * d);

            if (Math.abs(face.getModY()) == 1) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        result.add(origin.getWorld().getBlockAt(baseX + x, baseY, baseZ + z));
                    }
                }
            } else if (Math.abs(face.getModX()) == 1) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        result.add(origin.getWorld().getBlockAt(baseX, baseY + y, baseZ + z));
                    }
                }
            } else {
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        result.add(origin.getWorld().getBlockAt(baseX + x, baseY + y, baseZ));
                    }
                }
            }
        }

        return result;
    }

    private Set<Block> collectMainTrunkBlocks(Block origin) {
        Set<Block> result = new LinkedHashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        Material trunkType = origin.getType();
        queue.add(origin);

        while (!queue.isEmpty() && result.size() < MAX_TREE_BLOCKS) {
            Block current = queue.poll();
            if (result.contains(current) || current.getType() != trunkType) {
                continue;
            }

            result.add(current);
            addVertical(queue, result, current, trunkType, BlockFace.UP);
            addVertical(queue, result, current, trunkType, BlockFace.DOWN);

            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block side = current.getRelative(face);
                if (result.contains(side) || side.getType() != trunkType) {
                    continue;
                }

                if (!hasVerticalTrunkConnection(side, trunkType)) {
                    continue;
                }

                queue.add(side);
            }
        }

        return result;
    }

    private void addVertical(Queue<Block> queue, Set<Block> visited, Block start, Material type, BlockFace direction) {
        Block next = start.getRelative(direction);
        while (next.getType() == type && visited.size() + queue.size() < MAX_TREE_BLOCKS) {
            if (!visited.contains(next)) {
                queue.add(next);
            }
            next = next.getRelative(direction);
        }
    }

    private boolean hasVerticalTrunkConnection(Block block, Material type) {
        return block.getRelative(BlockFace.UP).getType() == type
                || block.getRelative(BlockFace.DOWN).getType() == type;
    }

    private boolean isTreeLog(Material material) {
        return material.name().endsWith("_LOG");
    }

    private BlockFace getFallbackFace(Player player) {
        org.bukkit.util.Vector dir = player.getLocation().getDirection();
        double ax = Math.abs(dir.getX());
        double ay = Math.abs(dir.getY());
        double az = Math.abs(dir.getZ());

        if (ay >= ax && ay >= az) {
            return dir.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        }
        if (ax >= az) {
            return dir.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return dir.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }
}
