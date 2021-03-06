package com.hoten.gridia.serializers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hoten.gridia.CreatureImage;
import com.hoten.gridia.content.ContentManager;
import com.hoten.gridia.content.ItemInstance;
import com.hoten.gridia.map.Tile;
import com.hoten.gridia.scripting.Entity;
import com.hoten.gridia.serving.ServingGridia;

public class GridiaGson {

    private static Gson _gson;

    public static Gson get() {
        return _gson;
    }

    public static void initialize(ContentManager contentManager, ServingGridia servingGridia) {
        _gson = new GsonBuilder()
                .registerTypeAdapter(Entity.class, new EntityGsonAdapter())
                .registerTypeAdapter(ItemInstance.class, new ItemInstanceGsonAdapter(contentManager))
                .registerTypeAdapter(Tile.class, new TileGsonAdapter(servingGridia))
                .registerTypeAdapter(CreatureImage.class, new InterfaceAdapter<>())
                .setPrettyPrinting()
                .create();
    }
}
