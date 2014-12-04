package hoten.gridia.serving;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import hoten.gridia.Container;
import hoten.gridia.Creature;
import hoten.gridia.CustomPlayerImage;
import hoten.gridia.DefaultCreatureImage;
import hoten.gridia.map.Coord;
import hoten.gridia.content.ItemInstance;
import hoten.gridia.Player;
import hoten.gridia.content.ItemUse;
import hoten.gridia.content.Monster;
import hoten.gridia.map.Sector;
import hoten.serving.message.Protocols;
import hoten.serving.SocketHandler;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConnectionToGridiaClientHandler extends SocketHandler {

    private final List<Sector> _loadedSectors = new ArrayList();
    private final GridiaMessageToClientBuilder _messageBuilder;
    private final Gson _gson = new Gson();
    private final ServingGridia _server;
    public Player player;

    public ConnectionToGridiaClientHandler(ServingGridia server, Socket socket) throws IOException {
        super(socket, new GridiaProtocols(), Protocols.BoundDest.CLIENT);
        _server = server;
        _messageBuilder = server.messageBuilder;
    }

    public boolean hasSectorLoaded(Sector sector) {
        return _loadedSectors.contains(sector);
    }

    @Override
    protected void onConnectionSettled() throws IOException {
        player = new Player();
        player.username = "Bill_" + hashCode();
        player.creature = _server.createCreatureForPlayer();
        _server.announceNewPlayer(this, player);
        send(_messageBuilder.initialize(_server.tileMap.size, _server.tileMap.depth, _server.tileMap.sectorSize));
        send(_messageBuilder.setFocus(player.creature.id));

        // fake an inventory
        List<ItemInstance> inv = new ArrayList();
        inv.addAll(Arrays.asList(
                57, 335, 277, 280, 1067, 1068, 826, 1974,
                1974, 1039, 171, 902, 901, 339, 341,
                29, 19, 18, 12, 913, 34, 140
        ).stream()
                .map(i -> {
                    int quantity = _server.contentManager.getItem(i).stackable ? 1000 : 1;
                    return _server.contentManager.createItemInstance(i, quantity);
                })
                .collect(Collectors.toList()));
        while (inv.size() < 40) {
            inv.add(_server.contentManager.createItemInstance(0));
        }
        player.creature.inventory = new Container(inv, Container.ContainerType.Inventory);

        // fake equipment
        List<ItemInstance> equipment = new ArrayList();
        equipment.add(_server.contentManager.createItemInstance(0));
        equipment.add(_server.contentManager.createItemInstance(0));
        equipment.add(_server.contentManager.createItemInstance(0));
        equipment.add(_server.contentManager.createItemInstance(0));
        equipment.add(_server.contentManager.createItemInstance(0));
        player.equipment = new Container(equipment, Container.ContainerType.Equipment);
        if (player.creature.image instanceof CustomPlayerImage) {
            ((CustomPlayerImage) (player.creature.image)).moldToEquipment(player.equipment);
        }
        _server.updateCreatureImage(player.creature);

        send(_messageBuilder.container(player.creature.inventory));
        send(_messageBuilder.container(player.equipment));
    }

    @Override
    protected void handleData(int type, JsonObject data) throws IOException {
        switch (GridiaProtocols.Serverbound.values()[type]) {
            case PlayerMove:
                ProcessPlayerMove(data);
                break;
            case SectorRequest:
                ProcessSectorRequest(data);
                break;
            case CreatureRequest:
                ProcessCreatureRequest(data);
                break;
            case MoveItem:
                ProcessMoveItem(data);
                break;
            case Chat:
                ProcessChat(data);
                break;
            case UseItem:
                ProcessUseItem(data);
                break;
            case PickItemUse:
                ProcessPickItemUse(data);
                break;
            case EquipItem:
                ProcessEquipItem(data);
                break;
            case UnequipItem:
                ProcessUnequipItem(data);
                break;
            case Hit:
                ProcessHit(data);
                break;
            case AdminMakeItem:
                ProcessAdminMakeItem(data);
                break;
            case AdminMakeFloor:
                ProcessAdminMakeFloor(data);
                break;
        }
    }

    @Override
    protected void handleData(int type, DataInputStream data) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected synchronized void close() {
        super.close();
        _server.removeCreature(player.creature);
        _server.sendToAll(_messageBuilder.chat(player.username + " has left the building."));
    }

    private void ProcessPlayerMove(JsonObject data) throws IOException {
        Coord loc = _gson.fromJson(data.get("loc"), Coord.class);
        int timeForMovement = data.get("timeForMovement").getAsInt();
        _server.moveCreatureTo(player.creature, loc, timeForMovement, false);
    }

    private void ProcessSectorRequest(JsonObject data) throws IOException {
        int sx = data.get("x").getAsInt();
        int sy = data.get("y").getAsInt();
        int sz = data.get("z").getAsInt();
        Sector sector = _server.tileMap.getSector(sx, sy, sz);
        _loadedSectors.add(sector);
        send(_messageBuilder.sectorRequest(sector));
    }

    private void ProcessCreatureRequest(JsonObject data) throws IOException {
        int id = data.get("id").getAsInt();
        Creature cre = _server.creatures.get(id);
        if (cre != null) {
            send(_messageBuilder.addCreature(cre));
        }
    }

    private ItemInstance getItemFrom(String from, int index) {
        switch (from) {
            case "world":
                return _server.tileMap.getItem(_server.tileMap.getCoordFromIndex(index));
            case "inv":
                if (index == -1) {
                    return ItemInstance.NONE;
                }
                return player.creature.inventory.get(index);
            default:
                return ItemInstance.NONE;
        }
    }

    private void removeItemAt(String from, int index, int quantity) {
        switch (from) {
            case "world":
                _server.reduceItemQuantity(_server.tileMap.getCoordFromIndex(index), quantity);
                break;
            case "inv":
                player.creature.inventory.reduceQuantityAt(index, quantity);
                break;
        }
    }

    private void ProcessMoveItem(JsonObject data) throws IOException {
        String source = data.get("source").getAsString();
        String dest = data.get("dest").getAsString();
        int sourceIndex = data.get("si").getAsInt();
        int quantityToMove = data.get("quantity").getAsInt();
        int destIndex = data.get("di").getAsInt();

        if (source.equals(dest) && sourceIndex == destIndex) {
            return;
        }

        ItemInstance item = getItemFrom(source, sourceIndex);
        if (item == ItemInstance.NONE || (!_server.devMode && !item.data.moveable)) {
            return;
        }
        if (quantityToMove == -1) {
            quantityToMove = item.quantity;
        }
        item = new ItemInstance(item);
        item.quantity = quantityToMove;

        boolean moveSuccessful = false;
        switch (dest) {
            case "world":
                moveSuccessful = _server.addItem(_server.tileMap.getCoordFromIndex(destIndex), item);
                break;
            case "inv":
                if (destIndex == -1) {
                    moveSuccessful = player.creature.inventory.add(item);
                } else {
                    moveSuccessful = player.creature.inventory.add(item, destIndex);
                }
                break;
        }

        if (!moveSuccessful) {
            return;
        }

        removeItemAt(source, sourceIndex, quantityToMove);
    }

    private void ProcessChat(JsonObject data) throws IOException {
        String msg = data.get("msg").getAsString();

        if ("!clear".equals(msg)) {
            int size = _server.tileMap.size;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    for (int k = 0; k < _server.tileMap.depth; k++) {
                        _server.tileMap.setItem(ItemInstance.NONE, i, j, k);
                    }
                }
            }
        }

        if (msg.startsWith("!image ")) {
            try {
                String[] split = msg.split(" ");
                int image = split.length > 1 ? Integer.parseInt(split[1]) : 0;
                int width = split.length == 4 && split[2].matches("\\d+") ? Integer.parseInt(split[2]) : 1;
                int height = split.length == 4 && split[3].matches("\\d+") ? Integer.parseInt(split[3]) : 1;
                if (image == 0) {
                    player.creature.image = new CustomPlayerImage();
                } else if (image > 0 && image <= 700 && width > 0 && width <= 3 && height > 0 && height <= 3) {
                    player.creature.image = new DefaultCreatureImage(image, width, height);
                }
                updatePlayerImage();
            } catch (NumberFormatException e) {
            }
        }

        if (msg.startsWith("!friendly ")) {
            try {
                String[] split = msg.split(" ", 3);
                if (split.length == 3) {
                    int id = Integer.parseInt(split[1]);
                    Monster monster = _server.contentManager.getMonster(id);
                    if (monster != null) {
                        String friendlyMessage = split[2];
                        Creature creature = _server.createCreature(monster, player.creature.location.add(0, 1, 0));
                        creature.isFriendly = true;
                        creature.friendlyMessage = friendlyMessage;
                    }
                }
            } catch (NumberFormatException e) {
            }
        }

        if (msg.startsWith("!monster ")) {
            try {
                String[] split = msg.split(" ", 3);
                if (split.length == 2) {
                    int id = Integer.parseInt(split[1]);
                    Monster monster = _server.contentManager.getMonster(id);
                    if (monster != null) {
                        _server.createCreature(monster, player.creature.location.add(0, 1, 0));
                    }
                }
            } catch (NumberFormatException e) {
            }
        }

        if (msg.equals("!kill")) {
            Creature cre = _server.tileMap.getCreature(player.creature.location.add(0, 1, 0));
            if (cre != null) {
                _server.removeCreature(cre);
            }
        }

        if (msg.equals("!del")) {
            _server.changeItem(player.creature.location.add(0, 1, 0), ItemInstance.NONE);
        }

        if (msg.equals("!clr")) {
            for (int i = 0; i < 20; i++) {
                for (int j = 0; j < 20; j++) {
                    _server.changeItem(player.creature.location.add(i, j, 0), ItemInstance.NONE);
                }
            }
        }

        if (msg.equals("!loc")) {
            send(_messageBuilder.chat(player.creature.location.toString()));
        }

        if (msg.equals("!die")) {
            _server.hurtCreature(player.creature, 100000);
        }

        if (msg.startsWith("!warp ")) {
            String[] split = msg.split("\\s+");
            if (split.length == 4) {
                try {
                    int x = Integer.parseInt(split[1]);
                    int y = Integer.parseInt(split[2]);
                    int z = Integer.parseInt(split[3]);
                    if (_server.tileMap.inBounds(x, y, z)) {
                        _server.moveCreatureTo(player.creature, new Coord(x, y, z), true);
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
        
        if (msg.equals("!dev")) {
            _server.devMode = !_server.devMode;
        }

        _server.sendToAll(_messageBuilder.chat(player.username + " says: " + msg));
    }

    private void ExecuteItemUse(
            ItemUse use,
            ItemInstance tool,
            ItemInstance focus,
            String source,
            String dest,
            int sourceIndex,
            int destIndex
    ) throws IOException {
        if (use.successTool != -1) {
            if (use.successTool == 0) {
                tool.quantity -= 1;
            } else {
                tool = _server.contentManager.createItemInstance(use.successTool);
            }

            switch (source) {
                case "world":
                    _server.changeItem(sourceIndex, tool);
                    break;
                case "inv":
                    player.creature.inventory.set(sourceIndex, tool);
                    break;
            }
        }

        if (use.focusQuantityConsumed > 0) {
            if (focus != ItemInstance.NONE) {
                focus.quantity -= use.focusQuantityConsumed;
            }
            switch (dest) {
                case "world":
                    _server.updateTile(destIndex);
                    break;
            }
        }

        if ("world".equals(dest)) {
            use.products.stream()
                    .forEach(product -> {
                        ItemInstance productInstance = _server.contentManager.createItemInstance(product);
                        _server.addItemNear(destIndex, productInstance, 3);
                    });
            if (use.animation != 0) {
                _server.sendToClientsWithAreaLoaded(_messageBuilder.animation(use.animation), destIndex);
            }
        }

        if (use.successMessage != null) {
            send(_messageBuilder.chat(use.successMessage));
        }
    }

    private int useSourceIndex, useDestIndex;
    private String useSource, useDest;

    private void ProcessUseItem(JsonObject data) throws IOException {
        String source = data.get("source").getAsString();
        String dest = data.get("dest").getAsString();
        int sourceIndex = data.get("si").getAsInt();
        int destIndex = data.get("di").getAsInt();

        ItemInstance tool = getItemFrom(source, sourceIndex);
        ItemInstance focus = getItemFrom(dest, destIndex);

        List<ItemUse> uses = _server.contentManager.getItemUses(tool.data, focus.data);

        if (uses.isEmpty()) {
            return;
        }

        if (uses.size() == 1) {
            ExecuteItemUse(uses.get(0), tool, focus, source, dest, sourceIndex, destIndex);
        } else {
            send(_messageBuilder.itemUsePick(uses));
            useSource = source;
            useDest = dest;
            useSourceIndex = sourceIndex;
            useDestIndex = destIndex;
        }
    }

    private void ProcessPickItemUse(JsonObject data) throws IOException {
        int useIndex = data.get("useIndex").getAsInt();
        ItemInstance tool = getItemFrom(useSource, useSourceIndex);
        ItemInstance focus = getItemFrom(useDest, useDestIndex);
        List<ItemUse> uses = _server.contentManager.getItemUses(tool.data, focus.data);
        ItemUse use = uses.get(useIndex);
        ExecuteItemUse(use, tool, focus, useSource, useDest, useSourceIndex, useDestIndex);
    }

    private void ProcessEquipItem(JsonObject data) throws IOException {
        int slotIndex = data.get("slotIndex").getAsInt();
        ItemInstance item = player.creature.inventory.get(slotIndex);
        // :(
        if (item.data.isEquipable()) {
            int armorSlotIndex = item.data.armorSpot.ordinal();
            if (player.equipment.isEmpty(armorSlotIndex)) {
                player.creature.inventory.deleteSlot(slotIndex);
                player.equipment.set(armorSlotIndex, item);
            } else {
                player.creature.inventory.set(slotIndex, player.equipment.get(armorSlotIndex));
                player.equipment.set(armorSlotIndex, item);
            }
            updatePlayerImage();
        } else {
            send(_messageBuilder.chat("You cannot equip a " + item.data.name));
        }
    }

    private void ProcessUnequipItem(JsonObject data) throws IOException {
        int slotIndex = data.get("slotIndex").getAsInt();
        ItemInstance itemToUnequip = player.equipment.get(slotIndex);
        if (player.creature.inventory.add(itemToUnequip)) {
            player.equipment.deleteSlot(slotIndex);
            updatePlayerImage();
        } else {
            send(_messageBuilder.chat("Your inventory is full."));
        }
    }

    private void updatePlayerImage() {
        if (player.creature.image instanceof CustomPlayerImage) {
            CustomPlayerImage image = (CustomPlayerImage) player.creature.image;
            image.moldToEquipment(player.equipment);
        }
        _server.updateCreatureImage(player.creature);
    }

    private void ProcessHit(JsonObject data) throws IOException {
        Coord loc = _gson.fromJson(data.get("loc"), Coord.class);
        Creature creature = _server.tileMap.getCreature(loc);
        if (creature != null && !creature.belongsToPlayer) {
            if (creature.isFriendly) {
                send(_messageBuilder.chat(creature.friendlyMessage));
            } else {
                _server.hurtCreature(creature, 1);
            }
        }
    }

    private void ProcessAdminMakeItem(JsonObject data) throws IOException {
        Coord loc = _gson.fromJson(data.get("loc"), Coord.class);
        int itemId = data.get("item").getAsInt();
        _server.changeItem(loc, _server.contentManager.createItemInstance(itemId));
    }

    private void ProcessAdminMakeFloor(JsonObject data) throws IOException {
        Coord loc = _gson.fromJson(data.get("loc"), Coord.class);
        int floor = data.get("floor").getAsInt();
        _server.changeFloor(loc, floor);
    }
}
