package de.btegermany.terraplusminus.data;

import net.buildtheearth.terraminusminus.dataset.builtin.AbstractBuiltinDataset;
import net.buildtheearth.terraminusminus.util.RLEByteArray;
import net.daporkchop.lib.common.reference.cache.Cached;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import static net.daporkchop.lib.common.util.PValidation.checkState;

public class KoppenClimateData extends AbstractBuiltinDataset {
    protected static final int COLUMNS = 43200;
    protected static final int ROWS = 21600;


    public KoppenClimateData() {
        super(COLUMNS, ROWS);
    }

    private static final Cached<RLEByteArray> CACHE = Cached.global((Supplier<RLEByteArray>) () -> {
        RLEByteArray.Builder builder = RLEByteArray.builder();

        try (InputStream compressedStream = KoppenClimateData.class.getResourceAsStream("/koppen_map.gz")) {
            checkState(compressedStream != null, "Missing internal resource for Koppen biome dataset");
            InputStream is = new GZIPInputStream(compressedStream);
            byte[] buffer = new byte[4096];
            int readyBytes;
            while ((readyBytes = is.read(buffer, 0, buffer.length)) != -1) {
                for (int i = 0; i < readyBytes; i++) {
                    builder.append(buffer[i]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load internal resource for Koppen dataset", e);
        }

        return builder.build();
    });


    private final RLEByteArray data = CACHE.get();

    @Override
    protected double get(double xc, double yc) {
        int x = (int) Math.floor(xc);
        int y = (int) Math.floor(yc);

        if (x >= COLUMNS || x < 0 || y >= ROWS || y < 0)
            return 0;

        return this.data.get(y * COLUMNS + x);
    }

}
