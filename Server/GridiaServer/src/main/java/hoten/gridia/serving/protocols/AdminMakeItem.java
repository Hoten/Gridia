package hoten.gridia.serving.protocols;

import com.google.gson.JsonObject;
import hoten.gridia.map.Coord;
import hoten.gridia.serializers.GridiaGson;
import hoten.gridia.serving.ConnectionToGridiaClientHandler;
import hoten.gridia.serving.ServingGridia;
import hoten.serving.message.JsonMessageHandler;

public class AdminMakeItem extends JsonMessageHandler<ConnectionToGridiaClientHandler> {

    @Override
    protected void handle(ConnectionToGridiaClientHandler connection, JsonObject data) {
        ServingGridia server = connection.getServer();
        Coord loc = GridiaGson.get().fromJson(data.get("loc"), Coord.class);
        int itemId = data.get("item").getAsInt();

        server.changeItem(loc, server.contentManager.createItemInstance(itemId));
    }
}
