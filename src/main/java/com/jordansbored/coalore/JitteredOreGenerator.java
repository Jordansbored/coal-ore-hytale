package com.jordansbored.coalore;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;

public class JitteredOreGenerator {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    private static final long FASTNOISE_CONSTANT = 668265261L;
    
    private static final double[] RAND_VECS = precomputeRandomVectors();
    private static final int HASH_MASK = 0x3FC;
    
    private final int minY;
    private final int maxY;
    private final double gridScale;
    private final double jitter;
    private final double spawnWeight;
    private final double maxWeight;
    private final long seed;
    
    private int coalOreId = Integer.MIN_VALUE;
    private BlockType coalOreType = null;
    private int stoneId = Integer.MIN_VALUE;
    
    public JitteredOreGenerator(int minY, int maxY, double gridScale, double jitter,
                                 double spawnWeight, double maxWeight, long seed) {
        this.minY = minY;
        this.maxY = maxY;
        this.gridScale = gridScale;
        this.jitter = jitter;
        this.spawnWeight = spawnWeight;
        this.maxWeight = maxWeight;
        this.seed = seed;
    }
    
    public int generateOre(@Nonnull WorldChunk chunk) {
        if (!initializeBlockIds()) {
            return 0;
        }
        
        int chunkX = chunk.getX() << 5;
        int chunkZ = chunk.getZ() << 5;
        
        int totalPlaced = 0;
        int gridPointsX = (int) Math.ceil(32.0 / gridScale);
        int gridPointsZ = (int) Math.ceil(32.0 / gridScale);
        
        for (int gx = 0; gx < gridPointsX; gx++) {
            for (int gz = 0; gz < gridPointsZ; gz++) {
                int worldGridX = chunkX / (int) gridScale + gx;
                int worldGridZ = chunkZ / (int) gridScale + gz;
                
                Vector3d point = generatePoint(seed, jitter, gridScale, worldGridX, worldGridZ);
                
                int pointX = (int) Math.floor(point.x);
                int pointZ = (int) Math.floor(point.z);
                
                int worldX = chunkX + ((pointX - chunkX) % 32 + 32) % 32;
                int worldZ = chunkZ + ((pointZ - chunkZ) % 32 + 32) % 32;
                
                double probability = spawnWeight / maxWeight;
                if (fastRandom(seed + pointX * 7919 + pointZ * 104729) > probability) {
                    continue;
                }
                
                int y = minY + (int) (fastRandom(seed + pointX * 7919 + pointZ * 104729 + 1) * (maxY - minY));
                
                try {
                    int currentBlock = chunk.getBlock(worldX, y, worldZ);
                    if (currentBlock == stoneId) {
                        chunk.setBlock(worldX, y, worldZ, coalOreId, coalOreType, 0, 0, 4);
                        totalPlaced++;
                    }
                } catch (Exception e) {
                }
            }
        }
        
        return totalPlaced;
    }
    
    private static Vector3d generatePoint(long seed, double jitter, double scale, int gridX, int gridZ) {
        long hash = fastHash(seed, gridX, gridZ) & HASH_MASK;
        
        double offsetX = RAND_VECS[(int) hash] * jitter;
        double offsetZ = RAND_VECS[(int) (hash + 2)] * jitter;
        
        return new Vector3d(
            gridX * scale + offsetX,
            0,
            gridZ * scale + offsetZ
        );
    }
    
    private static long fastHash(long seed, int x, int z) {
        long xPrimed = x * 374761393L;
        long zPrimed = z * 668265263L;
        return (xPrimed ^ (zPrimed ^ seed)) * FASTNOISE_CONSTANT;
    }
    
    private static double fastRandom(long seed) {
        seed ^= seed >>> 12;
        seed ^= seed >>> 7;
        seed ^= seed >>> 5;
        return (seed & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }
    
    private static double[] precomputeRandomVectors() {
        double[] vecs = new double[1024];
        for (int i = 0; i < 256; i++) {
            double x = Math.random() * 2 - 1;
            double y = Math.random() * 2 - 1;
            double z = Math.random() * 2 - 1;
            double len = Math.sqrt(x * x + y * y + z * z);
            vecs[i * 4] = x / len;
            vecs[i * 4 + 1] = y / len;
            vecs[i * 4 + 2] = z / len;
        }
        return vecs;
    }
    
    private boolean initializeBlockIds() {
        if (coalOreId != Integer.MIN_VALUE) {
            return true;
        }
        
        coalOreType = BlockType.getAssetMap().getAsset("Ore_Coal_Stone");
        if (coalOreType == null) {
            LOGGER.atWarning().log("Coal ore block type not found!");
            return false;
        }
        
        coalOreId = BlockType.getAssetMap().getIndex("Ore_Coal_Stone");
        stoneId = BlockType.getAssetMap().getIndex("Rock_Stone");
        
        return true;
    }
    
    private static class Vector3d {
        double x, y, z;
        Vector3d(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
