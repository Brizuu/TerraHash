package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.tree.TreePopulator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import lombok.Getter;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.transform.OffsetProjectionTransform;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.http.Http;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.min;
import static net.buildtheearth.terraminusminus.substitutes.ChunkPos.blockToCube;
import static net.buildtheearth.terraminusminus.substitutes.ChunkPos.cubeToMinBlock;
import static net.buildtheearth.terraminusminus.substitutes.TerraBukkit.toBukkitBlockData;
import static org.bukkit.Material.*;
import static org.bukkit.block.Biome.*;

public class RealWorldGenerator extends ChunkGenerator {

    @Getter
    private final EarthGeneratorSettings settings;
    @Getter
    private final int yOffset;
    private Location spawnLocation = null;

    private final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;
    private final CustomBiomeProvider customBiomeProvider;

    private final Material surfaceMaterial;
    private final Map<String, Material> materialMapping;

    private static final AtomicInteger activeRequests = new AtomicInteger(0);
    private static final Random randomDelay = new Random();

    private static final Set<Material> GRASS_LIKE_MATERIALS = Set.of(
            GRASS_BLOCK,
            DIRT_PATH,
            FARMLAND,
            MYCELIUM,
            SNOW
    );

    public RealWorldGenerator(int yOffset) {
        Http.configChanged();

        EarthGeneratorSettings settings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

        GeographicProjection projection = new OffsetProjectionTransform(
                settings.projection(),
                Terraplusminus.config.getInt("terrain_offset.x"),
                Terraplusminus.config.getInt("terrain_offset.z")
        );

        if (yOffset == 0) {
            this.yOffset = Terraplusminus.config.getInt("terrain_offset.y");
        } else {
            this.yOffset = yOffset;
        }

        this.settings = settings.withProjection(projection);
        this.customBiomeProvider = new CustomBiomeProvider(projection);

        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(5L, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(this.settings));

        this.surfaceMaterial = ConfigurationHelper.getMaterial(Terraplusminus.config, "surface_material", GRASS_BLOCK);
        this.materialMapping = Map.of(
                "minecraft:bricks", ConfigurationHelper.getMaterial(Terraplusminus.config, "building_outlines_material", BRICKS),
                "minecraft:gray_concrete", ConfigurationHelper.getMaterial(Terraplusminus.config, "road_material", GRAY_CONCRETE_POWDER),
                "minecraft:dirt_path", ConfigurationHelper.getMaterial(Terraplusminus.config, "path_material", MOSS_BLOCK)
        );
    }

    private static long globalApiLockoutUntil = 0;

//    private CachedChunkData getTerraChunkData(int chunkX, int chunkZ) {
//        long currentTime = System.currentTimeMillis();
//
//        if (currentTime < globalApiLockoutUntil) {
//            return null;
//        }
//
//        try {
//            if (activeRequests.get() >= 1) {
//                Thread.sleep(100);
//            }
//
//            activeRequests.incrementAndGet();
//
//            CompletableFuture<CachedChunkData> future = this.cache.getUnchecked(new ChunkPos(chunkX, chunkZ));
//            return future.get(1500, TimeUnit.MILLISECONDS);
//
//        } catch (Exception e) {
//            String fullError = e.toString().toLowerCase();
//            if (e.getCause() != null) {
//                fullError += " " + e.getCause().toString().toLowerCase();
//            }
//
//            if (fullError.contains("reset") ||
//                    fullError.contains("peer") ||
//                    fullError.contains("429") ||
//                    fullError.contains("too many")) {
//
//                globalApiLockoutUntil = currentTime + 30000;
//
//                Terraplusminus.instance.getLogger().severe("--- API BLOCKADE: Connection reset by peer / Rate limit ---");
//                Terraplusminus.instance.getLogger().severe("API connection rejection detected. Queries blocked for 30 seconds.");
//            }
//            else if (fullError.contains("timeout")) {
//                globalApiLockoutUntil = currentTime + 10000;
//            }
//
//            this.cache.invalidate(new ChunkPos(chunkX, chunkZ));
//            ChunkStatusCache.markAsFailed(chunkX, chunkZ);
//            return null;
//        } finally {
//            activeRequests.decrementAndGet();
//        }
//    }

    private final ThreadLocal<Map<ChunkPos, CachedChunkData>> sessionCache = ThreadLocal.withInitial(HashMap::new);

    private CachedChunkData getTerraChunkData(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);

        // 1. SESSION CACHE: Błyskawiczny zwrot danych, jeśli już raz o nie pytaliśmy.
        Map<ChunkPos, CachedChunkData> currentSession = sessionCache.get();
        if (currentSession.containsKey(pos)) {
            return currentSession.get(pos);
        }

        long currentTime = System.currentTimeMillis();

        // 2. Blokada globalna - jeśli API nas odcięło, nie marnujemy ani milisekundy.
        if (currentTime < globalApiLockoutUntil) return null;

        // 3. Limit aktywnych połączeń (zmniejszony do 3 dla odciążenia oświetlenia)
        // Twój serwer jest tak przeciążony, że 3 zapytania naraz to max, co udźwignie bez lagów.
        if (activeRequests.get() >= 3) return null;

        CachedChunkData data = null;
        try {
            activeRequests.incrementAndGet();

            CompletableFuture<CachedChunkData> future = this.cache.getIfPresent(pos);
            if (future == null) {
                future = this.cache.getUnchecked(pos);
            }

            // 4. Timeout 3 sekundy.
            data = future.handle((d, ex) -> {
                if (ex != null) {
                    String msg = ex.toString().toLowerCase();
                    // Jeśli API nas odcina (Connection Refused/Reset/429), blokujemy na 60s.
                    if (msg.contains("reset") || msg.contains("429") || msg.contains("refused")) {
                        globalApiLockoutUntil = System.currentTimeMillis() + 60000;
                    }
                    return null;
                }
                return d;
            }).get(3000, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            if (!(e instanceof TimeoutException)) {
                this.cache.invalidate(pos);
            }
        } finally {
            activeRequests.decrementAndGet();

            // ZAPAMIĘTUJEMY WYNIK (nawet null)
            currentSession.put(pos, data);

            // Czyścimy sesję po 16 wpisach (rozmiar jednego "regionu" zapytań)
            if (currentSession.size() > 16) {
                currentSession.clear();
            }
        }
        return data;
    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        CachedChunkData terraData = this.getTerraChunkData(chunkX, chunkZ);

        if (terraData == null) {
            return;
        }

        int minWorldY = worldInfo.getMinHeight();
        int maxWorldY = worldInfo.getMaxHeight();

        int minSurfaceCubeY = blockToCube(minWorldY - this.yOffset);
        int maxWorldCubeY = blockToCube(maxWorldY - this.yOffset);

        if (terraData.aboveSurface(minSurfaceCubeY)) {
            return;
        }

        while (minSurfaceCubeY < maxWorldCubeY && terraData.belowSurface(minSurfaceCubeY)) {
            minSurfaceCubeY++;
        }

        if (minSurfaceCubeY >= maxWorldCubeY) {
            chunkData.setRegion(0, minWorldY, 0, 16, maxWorldY, 16, STONE);
            return;
        } else {
            chunkData.setRegion(0, minWorldY, 0, 16, cubeToMinBlock(minSurfaceCubeY), 16, STONE);
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int groundHeight = min(terraData.groundHeight(x, z) + this.yOffset, maxWorldY - 1);
                int waterHeight = min(terraData.waterHeight(x, z) + this.yOffset, maxWorldY - 1);
                chunkData.setRegion(x, minWorldY, z, x + 1, groundHeight + 1, z + 1, STONE);
                chunkData.setRegion(x, groundHeight + 1, z, x + 1, waterHeight + 1, z + 1, WATER);
            }
        }
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        CachedChunkData terraData = this.getTerraChunkData(chunkX, chunkZ);
        if (terraData == null) return;

        final int minWorldY = worldInfo.getMinHeight();
        final int maxWorldY = worldInfo.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int groundY = terraData.groundHeight(x, z) + this.yOffset;
                int startMountainHeight = random.nextInt(7500, 7520);

                if (groundY < minWorldY || groundY >= maxWorldY) continue;

                Material material;
                BlockState state = terraData.surfaceBlock(x, z);

                if (state != null) {
                    material = this.materialMapping.get(state.getBlock().toString());
                    if (material == null) {
                        material = toBukkitBlockData(state).getMaterial();
                    }
                } else if (groundY >= startMountainHeight) {
                    material = STONE;
                } else {
                    Biome biome = chunkData.getBiome(x, groundY, z);
                    if (biome == DESERT) material = Material.SAND;
                    else if (biome == SNOWY_SLOPES || biome == SNOWY_PLAINS || biome == FROZEN_PEAKS) material = SNOW_BLOCK;
                    else material = this.surfaceMaterial;
                }

                boolean isUnderWater = groundY + 1 >= maxWorldY || chunkData.getBlockData(x, groundY + 1, z).getMaterial().equals(WATER);
                if (isUnderWater && GRASS_LIKE_MATERIALS.contains(material)) {
                    material = DIRT;
                }
                chunkData.setBlock(x, groundY, z, material);
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return this.customBiomeProvider;
    }

    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData chunkData) {}
    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData chunkData) {}

    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull HeightMap heightMap) {
        int chunkX = blockToCube(x);
        int chunkZ = blockToCube(z);
        x -= cubeToMinBlock(chunkX);
        z -= cubeToMinBlock(chunkZ);
        CachedChunkData terraData = this.getTerraChunkData(chunkX, chunkZ);

        if (terraData == null) return worldInfo.getMinHeight();

        return switch (heightMap) {
            case OCEAN_FLOOR, OCEAN_FLOOR_WG -> terraData.groundHeight(x, z) + this.yOffset;
            default -> terraData.surfaceHeight(x, z) + this.yOffset;
        };
    }

    public boolean canSpawn(@NotNull World world, int x, int z) {
        Block highest = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);
        return switch (world.getEnvironment()) {
            case NETHER -> true;
            case THE_END -> highest.getType() != Material.AIR && highest.getType() != WATER;
            default -> highest.getType() == Material.SAND || highest.getType() == Material.GRAVEL;
        };
    }

    @NotNull
    @Override
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return Collections.emptyList();
    }

    @Nullable
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        if (spawnLocation == null) spawnLocation = new Location(world, 3517417, 58, -5288234);
        return spawnLocation;
    }

    public boolean shouldGenerateNoise() { return false; }
    public boolean shouldGenerateSurface() { return false; }
    public boolean shouldGenerateBedrock() { return false; }
    public boolean shouldGenerateCaves() { return false; }
    public boolean shouldGenerateDecorations() { return false; }
    public boolean shouldGenerateMobs() { return false; }
    public boolean shouldGenerateStructures() { return false; }
}