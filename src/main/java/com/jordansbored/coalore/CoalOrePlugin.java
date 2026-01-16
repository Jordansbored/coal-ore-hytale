package com.jordansbored.coalore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.util.ChunkUtil;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Coal Ore Plugin - Spawns coal ore veins naturally during world generation
 * 
 * Natural Generation:
 * - Automatically generates coal ore veins when new chunks are created
 * - Ore spawns between Y=10 and Y=80, with higher density at lower levels
 * - Replaces stone-like blocks only
 * 
 * Commands (Creative mode):
 * - /coalore spawn [size] - Spawns a coal ore vein at your location
 * - /coalore generate [radius] [count] - Generates multiple veins in an area
 * - /coalore fill [radius] - Fills underground areas with coal ore veins
 */
public class CoalOrePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    // Coal ore block ID - defined in our pack as Ore_Coal_Stone
    private static final String COAL_ORE_BLOCK = "Ore_Coal_Stone";
    private static final String STONE_BLOCK = "Rock_Stone";
    
    // Generation settings
    private static final int MIN_Y = 10;
    private static final int MAX_Y = 60;
    private static final int VEINS_PER_CHUNK = 2;  // Average veins per chunk (rare)
    private static final int MIN_VEIN_SIZE = 3;
    private static final int MAX_VEIN_SIZE = 7;
    private static final double SPAWN_CHANCE = 0.6; // 60% chance per chunk to spawn any veins
    
    private final Random random = new Random();
    
    // Cached block IDs for performance (initialized on first use)
    private int coalOreId = Integer.MIN_VALUE;
    private BlockType coalOreType = null;
    private int stoneId = Integer.MIN_VALUE;
    private int[] replaceableBlockIds = null;

    public CoalOrePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Coal Ore plugin loaded - version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Coal Ore plugin...");
        
        // Register chunk generation event for natural ore spawning
        // Use LATE priority so terrain is fully generated before we add ores
        this.getEventRegistry().registerGlobal(
            EventPriority.LATE, 
            ChunkPreLoadProcessEvent.class, 
            this::onChunkGenerated
        );
        
        // Register commands for manual ore spawning
        this.getCommandRegistry().registerCommand(new CoalOreCommand());
        
        LOGGER.atInfo().log("Coal Ore plugin setup complete!");
        LOGGER.atInfo().log("  - Natural generation: ENABLED (Y=" + MIN_Y + " to Y=" + MAX_Y + ", ~" + VEINS_PER_CHUNK + " veins/chunk)");
        LOGGER.atInfo().log("  - Commands: /coalore spawn|generate|fill");
    }
    
    /**
     * Called when a chunk is about to be loaded. If it's newly generated,
     * we add coal ore veins to it.
     */
    private void onChunkGenerated(@Nonnull ChunkPreLoadProcessEvent event) {
        // Only process newly generated chunks, not chunks loaded from disk
        if (!event.isNewlyGenerated()) {
            return;
        }
        
        WorldChunk chunk = event.getChunk();
        if (chunk == null) {
            return;
        }
        
        // Initialize block IDs on first use
        if (!initializeBlockIds()) {
            return;
        }
        
        // Get chunk coordinates (block coordinates of chunk corner)
        int chunkX = chunk.getX() << 5;  // Multiply by 32 (chunk size)
        int chunkZ = chunk.getZ() << 5;
        
        // Create a seeded random for this chunk so generation is deterministic
        long chunkSeed = ((long) chunk.getX() * 341873128712L) + ((long) chunk.getZ() * 132897987541L);
        Random chunkRandom = new Random(chunkSeed);
        
        // Chance for this chunk to have any coal ore at all
        if (chunkRandom.nextDouble() > SPAWN_CHANCE) {
            return; // No coal ore in this chunk
        }
        
        int totalPlaced = 0;
        int veinsCreated = 0;
        
        // Generate veins in this chunk (1-3 veins when spawning)
        int numVeins = VEINS_PER_CHUNK + chunkRandom.nextInt(2); // 2-3 veins
        
        for (int i = 0; i < numVeins; i++) {
            // Random position within chunk
            int x = chunkX + chunkRandom.nextInt(32);
            int z = chunkZ + chunkRandom.nextInt(32);
            
            // Y level with bias toward lower depths (coal is more common deeper)
            // Use triangular distribution favoring lower Y values
            int y = MIN_Y + (int) (Math.pow(chunkRandom.nextDouble(), 1.5) * (MAX_Y - MIN_Y));
            
            // Random vein size
            int size = MIN_VEIN_SIZE + chunkRandom.nextInt(MAX_VEIN_SIZE - MIN_VEIN_SIZE + 1);
            
            int placed = generateVeinInChunk(chunk, x, y, z, size, chunkRandom);
            if (placed > 0) {
                totalPlaced += placed;
                veinsCreated++;
            }
        }
        
        if (veinsCreated > 0) {
            LOGGER.atFine().log("Generated %d coal ore veins (%d blocks) in chunk [%d, %d]", 
                veinsCreated, totalPlaced, chunk.getX(), chunk.getZ());
        }
    }
    
    /**
     * Initialize and cache block IDs for better performance.
     * @return true if initialization succeeded
     */
    private boolean initializeBlockIds() {
        if (coalOreId != Integer.MIN_VALUE) {
            return true; // Already initialized
        }
        
        coalOreType = BlockType.getAssetMap().getAsset(COAL_ORE_BLOCK);
        if (coalOreType == null) {
            LOGGER.atWarning().log("Coal ore block type '%s' not found! Natural generation disabled.", COAL_ORE_BLOCK);
            return false;
        }
        
        coalOreId = BlockType.getAssetMap().getIndex(COAL_ORE_BLOCK);
        stoneId = BlockType.getAssetMap().getIndex(STONE_BLOCK);
        
        // Cache all replaceable block IDs
        String[] replaceableBlockNames = {
            "Rock_Stone", "Rock_Stone_Cobble", "Rock_Stone_Mossy",
            "Rock_Sandstone", "Rock_Sandstone_Cobble",
            "Rock_Basalt", "Rock_Basalt_Cobble",
            "Rock_Marble", "Rock_Marble_Cobble",
            "Rock_Granite", "Rock_Granite_Cobble",
            "Dirt", "Dirt_Grass", "Dirt_Dry",
            "Gravel", "Clay"
        };
        
        replaceableBlockIds = new int[replaceableBlockNames.length];
        for (int i = 0; i < replaceableBlockNames.length; i++) {
            replaceableBlockIds[i] = BlockType.getAssetMap().getIndex(replaceableBlockNames[i]);
        }
        
        LOGGER.atInfo().log("Initialized coal ore generation - ore ID: %d, stone ID: %d", coalOreId, stoneId);
        return true;
    }
    
    /**
     * Generate a coal ore vein within a chunk during world generation.
     * This version works directly with the chunk being generated.
     */
    private int generateVeinInChunk(WorldChunk chunk, int centerX, int centerY, int centerZ, int size, Random rand) {
        int placed = 0;
        
        // Generate a blob-like vein using multiple overlapping spheres
        for (int i = 0; i < size; i++) {
            float progress = (float) i / size;
            float angle1 = rand.nextFloat() * (float) Math.PI * 2;
            float angle2 = rand.nextFloat() * (float) Math.PI * 2;
            
            int offsetX = (int) (Math.cos(angle1) * progress * 2);
            int offsetY = (int) (Math.sin(angle1) * Math.cos(angle2) * progress * 2);
            int offsetZ = (int) (Math.sin(angle2) * progress * 2);
            
            int x = centerX + offsetX;
            int y = centerY + offsetY;
            int z = centerZ + offsetZ;
            
            // Place a small cluster at this position
            int clusterRadius = 1 + rand.nextInt(2);
            
            for (int dx = -clusterRadius; dx <= clusterRadius; dx++) {
                for (int dy = -clusterRadius; dy <= clusterRadius; dy++) {
                    for (int dz = -clusterRadius; dz <= clusterRadius; dz++) {
                        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        if (dist <= clusterRadius + rand.nextFloat() * 0.5) {
                            int bx = x + dx;
                            int by = y + dy;
                            int bz = z + dz;
                            
                            // Bounds check
                            if (by < 1 || by > 310) continue;
                            
                            // Check if this block is in the current chunk
                            int blockChunkX = bx >> 5;
                            int blockChunkZ = bz >> 5;
                            if (blockChunkX != chunk.getX() || blockChunkZ != chunk.getZ()) {
                                continue; // Skip blocks outside this chunk
                            }
                            
                            // Try to place coal ore
                            if (placeCoalOreInChunk(chunk, bx, by, bz)) {
                                placed++;
                            }
                        }
                    }
                }
            }
        }
        
        return placed;
    }
    
    /**
     * Place a single coal ore block in a chunk, only replacing stone-like blocks.
     */
    private boolean placeCoalOreInChunk(WorldChunk chunk, int x, int y, int z) {
        try {
            int currentBlock = chunk.getBlock(x, y, z);
            
            // Check if current block is replaceable
            if (isReplaceableBlockId(currentBlock)) {
                // setBlock: x, y, z, blockId, blockType, rotation, filler, settings
                // settings: 4 = no particles, helps with performance during generation
                chunk.setBlock(x, y, z, coalOreId, coalOreType, 0, 0, 4);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Fast check if a block ID is replaceable (uses cached IDs).
     */
    private boolean isReplaceableBlockId(int blockId) {
        if (blockId == stoneId) return true;
        
        if (replaceableBlockIds != null) {
            for (int id : replaceableBlockIds) {
                if (id != Integer.MIN_VALUE && id == blockId) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    // ========== COMMANDS (for manual ore spawning) ==========
    
    /**
     * Main command collection for coal ore operations
     */
    private class CoalOreCommand extends AbstractCommandCollection {
        
        public CoalOreCommand() {
            super("coalore", "Spawn coal ore veins in the world");
            this.addAliases("co");
            this.setPermissionGroup(GameMode.Creative);
            this.addSubCommand(new SpawnCommand());
            this.addSubCommand(new GenerateCommand());
            this.addSubCommand(new FillCommand());
        }
    }
    
    /**
     * Spawns a single coal ore vein at the player's location
     */
    private class SpawnCommand extends AbstractPlayerCommand {
        
        @Nonnull
        private final DefaultArg<Integer> sizeArg = this.withDefaultArg(
            "size", "Size of the vein (1-20)", ArgTypes.INTEGER, 8, "Vein size"
        );
        
        public SpawnCommand() {
            super("spawn", "Spawn a coal ore vein at your location");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, 
                             @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                context.sendMessage(Message.raw("Could not get player position!"));
                return;
            }
            
            Vector3d pos = transform.getPosition();
            int x = (int) pos.x;
            int y = (int) pos.y - 2;
            int z = (int) pos.z;
            
            int size = Math.max(1, Math.min(20, sizeArg.get(context)));
            
            world.execute(() -> {
                int placed = spawnCoalOreVein(world, x, y, z, size);
                context.sendMessage(Message.raw("Spawned coal ore vein with " + placed + " blocks at (" + x + ", " + y + ", " + z + ")"));
            });
        }
    }
    
    /**
     * Generates multiple coal ore veins in an area around the player
     */
    private class GenerateCommand extends AbstractPlayerCommand {
        
        @Nonnull
        private final DefaultArg<Integer> radiusArg = this.withDefaultArg(
            "radius", "Radius to generate in", ArgTypes.INTEGER, 32, "Generation radius"
        );
        
        @Nonnull
        private final DefaultArg<Integer> countArg = this.withDefaultArg(
            "count", "Number of veins to generate", ArgTypes.INTEGER, 10, "Vein count"
        );
        
        public GenerateCommand() {
            super("generate", "Generate multiple coal ore veins in an area");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, 
                             @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                context.sendMessage(Message.raw("Could not get player position!"));
                return;
            }
            
            Vector3d pos = transform.getPosition();
            int centerX = (int) pos.x;
            int centerZ = (int) pos.z;
            
            int radius = Math.max(1, Math.min(128, radiusArg.get(context)));
            int count = Math.max(1, Math.min(100, countArg.get(context)));
            
            context.sendMessage(Message.raw("Generating " + count + " coal ore veins in radius " + radius + "..."));
            
            world.execute(() -> {
                int totalPlaced = 0;
                int veinsCreated = 0;
                
                for (int i = 0; i < count; i++) {
                    int x = centerX + random.nextInt(radius * 2) - radius;
                    int z = centerZ + random.nextInt(radius * 2) - radius;
                    int y = 10 + random.nextInt(50);
                    int size = 4 + random.nextInt(9);
                    
                    int placed = spawnCoalOreVein(world, x, y, z, size);
                    if (placed > 0) {
                        totalPlaced += placed;
                        veinsCreated++;
                    }
                }
                
                context.sendMessage(Message.raw("Generated " + veinsCreated + " veins with " + totalPlaced + " total coal ore blocks!"));
            });
        }
    }
    
    /**
     * Fills an underground area with coal ore veins at regular intervals
     */
    private class FillCommand extends AbstractPlayerCommand {
        
        @Nonnull
        private final DefaultArg<Integer> radiusArg = this.withDefaultArg(
            "radius", "Radius to fill", ArgTypes.INTEGER, 16, "Fill radius"
        );
        
        public FillCommand() {
            super("fill", "Fill underground area with coal ore veins");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, 
                             @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                context.sendMessage(Message.raw("Could not get player position!"));
                return;
            }
            
            Vector3d pos = transform.getPosition();
            int centerX = (int) pos.x;
            int centerZ = (int) pos.z;
            
            int radius = Math.max(1, Math.min(64, radiusArg.get(context)));
            
            context.sendMessage(Message.raw("Filling area with coal ore (radius " + radius + ")..."));
            
            world.execute(() -> {
                int totalPlaced = 0;
                int veinsCreated = 0;
                int spacing = 8;
                
                for (int x = centerX - radius; x <= centerX + radius; x += spacing) {
                    for (int z = centerZ - radius; z <= centerZ + radius; z += spacing) {
                        for (int yBase = 15; yBase <= 55; yBase += 15) {
                            int vx = x + random.nextInt(spacing) - spacing/2;
                            int vz = z + random.nextInt(spacing) - spacing/2;
                            int vy = yBase + random.nextInt(10) - 5;
                            int size = 5 + random.nextInt(6);
                            
                            int placed = spawnCoalOreVein(world, vx, vy, vz, size);
                            if (placed > 0) {
                                totalPlaced += placed;
                                veinsCreated++;
                            }
                        }
                    }
                }
                
                context.sendMessage(Message.raw("Created " + veinsCreated + " veins with " + totalPlaced + " total coal ore blocks!"));
            });
        }
    }
    
    /**
     * Spawns a coal ore vein at the specified position (for commands).
     * Uses a blob-like pattern similar to Minecraft ore generation.
     */
    private int spawnCoalOreVein(World world, int centerX, int centerY, int centerZ, int size) {
        if (!initializeBlockIds()) {
            return 0;
        }
        
        int placed = 0;
        
        for (int i = 0; i < size; i++) {
            float progress = (float) i / size;
            float angle1 = random.nextFloat() * (float) Math.PI * 2;
            float angle2 = random.nextFloat() * (float) Math.PI * 2;
            
            int offsetX = (int) (Math.cos(angle1) * progress * 2);
            int offsetY = (int) (Math.sin(angle1) * Math.cos(angle2) * progress * 2);
            int offsetZ = (int) (Math.sin(angle2) * progress * 2);
            
            int x = centerX + offsetX;
            int y = centerY + offsetY;
            int z = centerZ + offsetZ;
            
            int clusterRadius = 1 + random.nextInt(2);
            
            for (int dx = -clusterRadius; dx <= clusterRadius; dx++) {
                for (int dy = -clusterRadius; dy <= clusterRadius; dy++) {
                    for (int dz = -clusterRadius; dz <= clusterRadius; dz++) {
                        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        if (dist <= clusterRadius + random.nextFloat() * 0.5) {
                            int bx = x + dx;
                            int by = y + dy;
                            int bz = z + dz;
                            
                            if (by < 1 || by > 310) continue;
                            
                            if (placeCoalOre(world, bx, by, bz)) {
                                placed++;
                            }
                        }
                    }
                }
            }
        }
        
        return placed;
    }
    
    /**
     * Places a single coal ore block (for commands), only replacing stone-like blocks.
     */
    private boolean placeCoalOre(World world, int x, int y, int z) {
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk chunk = (WorldChunk) world.getNonTickingChunk(chunkIndex);
            
            if (chunk == null) {
                return false;
            }
            
            int currentBlock = chunk.getBlock(x, y, z);
            
            if (isReplaceableBlockId(currentBlock)) {
                chunk.setBlock(x, y, z, coalOreId, coalOreType, 0, 0, 4);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
