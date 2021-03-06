package com.hoten.gridia.worldgen;

import com.google.gson.Gson;
import com.hoten.delaunay.examples.TestDriver;
import com.hoten.delaunay.examples.TestGraphImpl.ColorData;
import com.hoten.delaunay.voronoi.VoronoiGraph;
import com.hoten.gridia.content.ContentManager;
import com.hoten.gridia.content.ItemInstance;
import com.hoten.gridia.map.Coord;
import com.hoten.gridia.map.Sector;
import com.hoten.gridia.map.SectorLoader;
import com.hoten.gridia.map.SectorSaver;
import com.hoten.gridia.map.Tile;
import com.hoten.gridia.map.TileMap;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.apache.commons.io.FileUtils;

public class MapGenerator {

    private final ContentManager _contentManager;
    private final int _numPoints, _numLloydRelaxations;
    private final long _seed;

    public MapGenerator(ContentManager contentManager, int numPoints, int numLloydRelaxations, long seed) {
        _contentManager = contentManager;
        _numPoints = numPoints;
        _numLloydRelaxations = numLloydRelaxations;
        _seed = seed;
    }

    public TileMap generate(File map, int size, int depth, int sectorSize) throws IOException {
        HashMap<String, Object> mapMetaData = new HashMap<>();

        mapMetaData.put("name", map.getName());
        mapMetaData.put("size", size);
        mapMetaData.put("depth", depth);
        mapMetaData.put("sectorSize", sectorSize);
        mapMetaData.put("defaultPlayerSpawn", new Coord(size / 2, size / 2, 0));

        String metaDataJson = new Gson().toJson(mapMetaData);
        FileUtils.writeStringToFile(new File(map, "meta.json"), metaDataJson);

        VoronoiGraph graph = TestDriver.createVoronoiGraph(size, _numPoints, _numLloydRelaxations, _seed);
        BufferedImage mapImage = graph.createMap();

        TileMap world = new TileMap(size, depth, sectorSize, createFakeLoader(), new SectorSaver(map));

        HashMap<Integer, ColorData> colorBiomeMap = new HashMap<>();

        Arrays.asList(ColorData.values()).forEach(c -> colorBiomeMap.put(c.color.getRGB(), c));

        Random random = new Random(_seed);

        List<Integer> desertItems = Arrays.asList(1093, 2044);
        Supplier<ItemInstance> desertItemSupplier = () -> {
            int id = desertItems.get(random.nextInt(desertItems.size()));
            return _contentManager.createItemInstance(id);
        };

        List<Integer> defaultItems = Arrays.asList(575, 260, 11, 1402);
        Supplier<ItemInstance> defaultItemSupplier = () -> {
            int id = defaultItems.get(random.nextInt(defaultItems.size()));
            return _contentManager.createItemInstance(id);
        };

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 1; z < depth; z++) {
                    Tile undergroundTile = world.getTile(x, y, z);
                    undergroundTile.item = ItemInstance.NONE;
                    undergroundTile.floor = 0;
                }

                int pixel = mapImage.getRGB(x, y);
                Tile tile = world.getTile(x, y, 0);

                Supplier<ItemInstance> itemSupplier = defaultItemSupplier;
                double itemProbability = 0;

                int index = graph.pixelCenterMap.getRGB(x, y) - 0xff000000;
                if (index < graph.centers.size()) {
                    double e = graph.centers.get(index).elevation;
                    int grassIndex = (int) (e * 10);
                    itemProbability = 0.015 * grassIndex;
                    tile.floor = 100 + grassIndex * 20;
                }

                if (x == 0 || y == 0 || x == size - 1 || y == size - 1) {
                    tile.floor = 1;
                } else {
                    ColorData biome = colorBiomeMap.get(pixel);

                    if (biome != null) {
                        switch (biome) {
                            case OCEAN:
                            case RIVER:
                            case LAKE:
                                tile.floor = 1;
                                itemProbability = 0;
                                break;
                            case BEACH:
                                tile.floor = 41;
                                itemProbability = 0;
                                break;
                            case SUBTROPICAL_DESERT:
                            case TEMPERATE_DESERT:
                            case BARE:
                                tile.floor = 44;
                                itemSupplier = desertItemSupplier;
                                itemProbability = 0.01;
                                break;
                            case TUNDRA:
                            case TAIGA:
                            case SNOW:
                                if (index < graph.centers.size()) {
                                    double m = graph.centers.get(index).moisture;
                                    tile.floor = 200 + (int) (m * 3) * 20;
                                }
                                break;
                            default:
                                //tile.floor = 0;
                                //System.out.println("biome = " + biome);
                                break;
                        }
                    } else {
                        tile.floor = 4;
                    }
                }

                tile.item = random.nextDouble() < itemProbability ? itemSupplier.get() : ItemInstance.NONE;
            }
        }

        new File(map, "scripts/auto").mkdirs();

        return world;
    }

    private SectorLoader createFakeLoader() {
        return (int sectorSize, int sx, int sy, int sz) -> {
            Tile[][] tiles = new Tile[sectorSize][sectorSize];
            for (int x = 0; x < sectorSize; x++) {
                tiles[x] = new Tile[sectorSize];
                for (int y = 0; y < sectorSize; y++) {
                    tiles[x][y] = new Tile();
                }
            }
            return new Sector(tiles, sx, sy, sz);
        };
    }
}
