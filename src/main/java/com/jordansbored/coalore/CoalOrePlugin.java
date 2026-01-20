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

public class CoalOrePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    private static final String COAL_ORE_BLOCK = "Ore_Coal_Stone";
    
    private static final int MIN_Y = 10;
    private static final int MAX_Y = 80;
    private static final double GRID_SCALE = 22.0;
    private static final double JITTER = 0.5;
    private static final double SPAWN_WEIGHT = 80.0;
    private static final double MAX_WEIGHT = 100.0;
    private static final long SEED = 12345L;
    
    private final Random random = new Random();
    private JitteredOreGenerator oreGenerator;
    
    private int coalOreId = Integer.MIN_VALUE;
    private BlockType coalOreType = null;

    public CoalOrePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Coal Ore plugin loaded - version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Coal Ore plugin...");
        
        this.oreGenerator = new JitteredOreGenerator(
            MIN_Y, MAX_Y, GRID_SCALE, JITTER,
            SPAWN_WEIGHT, MAX_WEIGHT, SEED
        );
        
        this.getEventRegistry().registerGlobal(
            EventPriority.LATE, 
            ChunkPreLoadProcessEvent.class, 
            this::onChunkGenerated
        );
        
        this.getCommandRegistry().registerCommand(new CoalOreCommand());
        
        LOGGER.atInfo().log("Coal Ore plugin setup complete!");
        LOGGER.atInfo().log("  - Natural generation: ENABLED (Y=" + MIN_Y + " to Y=" + MAX_Y + ")");
        LOGGER.atInfo().log("  - Grid scale: " + GRID_SCALE + ", Jitter: " + JITTER);
        LOGGER.atInfo().log("  - Commands: /coalore spawn|generate|fill");
    }
    
    private void onChunkGenerated(@Nonnull ChunkPreLoadProcessEvent event) {
        if (!event.isNewlyGenerated()) {
            return;
        }
        
        WorldChunk chunk = event.getChunk();
        if (chunk == null) {
            return;
        }
        
        int placed = oreGenerator.generateOre(chunk);
        
        if (placed > 0) {
            LOGGER.atFine().log("Generated %d coal ore blocks in chunk [%d, %d]", 
                placed, chunk.getX(), chunk.getZ());
        }
    }
    
    private boolean initializeBlockIds() {
        if (coalOreId != Integer.MIN_VALUE) {
            return true;
        }
        
        coalOreType = BlockType.getAssetMap().getAsset(COAL_ORE_BLOCK);
        if (coalOreType == null) {
            LOGGER.atWarning().log("Coal ore block type '%s' not found! Natural generation disabled.", COAL_ORE_BLOCK);
            return false;
        }
        
        coalOreId = BlockType.getAssetMap().getIndex(COAL_ORE_BLOCK);
        return true;
    }
    
    private class CoalOreCommand extends AbstractCommandCollection {
        
        public CoalOreCommand() {
            super("coalore", "Spawn coal ore veins in the world");
            this.addAliases("co");
            this.setPermissionGroup(GameMode.Creative);
            this.addSubCommand(new SpawnCommand());
            this.addSubCommand(new GenerateCommand());
            this.addSubCommand(new FillCommand());
            this.addSubCommand(new StatsCommand());
        }
    }
    
    private class StatsCommand extends AbstractPlayerCommand {
        
        public StatsCommand() {
            super("stats", "Show coal ore generation timing stats");
        }
        
        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, 
                             @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String stats = JitteredOreGenerator.getTimingStats();
            context.sendMessage(Message.raw(stats));
        }
    }
    
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
            double radiusSq = clusterRadius * clusterRadius + 0.5;
            
            for (int dx = -clusterRadius; dx <= clusterRadius; dx++) {
                for (int dy = -clusterRadius; dy <= clusterRadius; dy++) {
                    for (int dz = -clusterRadius; dz <= clusterRadius; dz++) {
                        if (dx*dx + dy*dy + dz*dz > radiusSq) continue;
                        
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
        
        return placed;
    }
    
    private boolean placeCoalOre(World world, int x, int y, int z) {
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk chunk = (WorldChunk) world.getNonTickingChunk(chunkIndex);
            
            if (chunk == null) {
                return false;
            }
            
            int currentBlock = chunk.getBlock(x, y, z);
            int stoneId = BlockType.getAssetMap().getIndex("Rock_Stone");
            
            if (currentBlock == stoneId || isReplaceableBlockId(currentBlock)) {
                chunk.setBlock(x, y, z, coalOreId, coalOreType, 0, 0, 4);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isReplaceableBlockId(int blockId) {
        String[] replaceableBlockNames = {
            "Rock_Stone_Cobble", "Rock_Stone_Mossy",
            "Rock_Sandstone", "Rock_Sandstone_Cobble",
            "Rock_Basalt", "Rock_Basalt_Cobble",
            "Rock_Marble", "Rock_Marble_Cobble",
            "Rock_Granite", "Rock_Granite_Cobble",
            "Dirt", "Dirt_Grass", "Dirt_Dry",
            "Gravel", "Clay"
        };
        
        for (String name : replaceableBlockNames) {
            int id = BlockType.getAssetMap().getIndex(name);
            if (id != Integer.MIN_VALUE && id == blockId) {
                return true;
            }
        }
        
        return false;
    }
}
