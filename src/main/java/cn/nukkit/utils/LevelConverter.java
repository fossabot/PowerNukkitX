package cn.nukkit.utils;

import cn.nukkit.api.PowerNukkitOnly;
import cn.nukkit.api.Since;
import cn.nukkit.blockstate.BlockState;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.RegionLoader;
import cn.nukkit.level.format.generic.BaseFullChunk;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author LT_Name
 * @author Superice666
 */
@PowerNukkitOnly
@Since("1.6.0.0-PNX")
@Log4j2
public class LevelConverter {
    private static final Pattern PATTERN = Pattern.compile("-?\\d+");

    private LevelConverter() {
        throw new RuntimeException();
    }

    /**
     * 参考文件 https://github.com/Creeperface01/WorldFixer/blob/master/src/main/java/com/creeperface/nukkitx/worldfixer/LevelConverter.java
     *
     * @param level 要转换的世界
     */
    public static void convert256To384(final Level level) {
        final LevelProvider provider = level.getProvider();
        final File[] regions = new File(level.getServer().getDataPath() + "worlds/" + level.getFolderName() + "/region").listFiles(
                (f) -> f.isFile() && f.getName().endsWith(".mca")
        );

        if (regions != null && regions.length > 0) {
            final AtomicInteger processed = new AtomicInteger();
            long time = System.currentTimeMillis();
            log.info("开始转换世界： '" + level.getName() + "'");

            Arrays.stream(regions).parallel().forEach(region -> {
                final Matcher m = PATTERN.matcher(region.getName());
                int regionX, regionZ;
                try {
                    if (m.find()) {
                        regionX = Integer.parseInt(m.group());
                    } else return;
                    if (m.find()) {
                        regionZ = Integer.parseInt(m.group());
                    } else return;
                } catch (NumberFormatException e) {
                    return;
                }
                try {
                    final RegionLoader loader = new RegionLoader(provider, regionX, regionZ);
                    BaseFullChunk chunk;
                    for (int chunkX = 0; chunkX < 32; chunkX++) {
                        for (int chunkZ = 0; chunkZ < 32; chunkZ++) {
                            chunk = loader.readChunk(chunkX, chunkZ);
                            if (chunk == null) continue;
                            chunk.backwardCompatibilityUpdate(level);
                            chunk.initChunk();
                            for (int dx = 0; dx < 16; dx++) {
                                for (int dz = 0; dz < 16; dz++) {
                                    for (int dy = chunk.getHighestBlockAt(dx, dz, false); dy >= -64; --dy) {
                                        chunk.setBlockState(dx, dy + 64, dz, chunk.getBlockState(dx, dy, dz));
                                        chunk.setBlockStateAtLayer(dx, dy + 64, dz, 1, chunk.getBlockState(dx, dy, dz, 1));
                                        chunk.setBlockState(dx, dy, dz, BlockState.AIR);
                                        chunk.setBlockStateAtLayer(dx, dy, dz, 1, BlockState.AIR);
                                    }
                                }
                            }
                            chunk.getBlockEntities().values().forEach(e -> e.setY(e.getY() + 64));
                            loader.writeChunk(chunk);
                        }
                    }
                    log.info(String.format("转换中... 已完成: %.2f%%", ((double) processed.incrementAndGet() / regions.length) * 100));
                    loader.close();
                } catch (Exception e) {
                    MainLogger.getLogger().logException(e);
                }
            });

            log.info(String.format("世界 %s 成功转换，耗时： %.2f秒。", level.getName(), (System.currentTimeMillis() - time) / 1000f));
        }
    }

}
