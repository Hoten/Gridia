def isContainerWrapper(wrapper) {
    wrapper.class.name == "ContainerItemWrapper"
}

def getCave(event) {
    if (event.result.products?.getAt(0)?.item?.cave) {
        event.result.products[0].item
    }
}

onValidateItemUse {
    cave = getCave(event)
    if (!cave) return
    if (isContainerWrapper(event.focus)) return "You can't make a $cave.name in a container!"
    
    if (cave.itemClass == ItemClass.Cave_down) {
        if (event.focus.lowestLevel) return "You can't dig any lower"
    } else if (cave.itemClass == ItemClass.Cave_up) {
        if (event.focus.highestLevel) return "You can't go any higher"
    }
}

onCompleteItemUse {
    cave = getCave(event)
    if (!cave) return
    
    focus = event.focus
    if (cave.itemClass == ItemClass.Cave_down) {
        below = focus.itemBelow
        if (below.item.itemClass != ItemClass.Cave_up) {
            if (below.nothing || focus.moveItemBelow()) {
                focus.itemBelow = server.contentManager.createItemInstance(981)
            }
        }
    } else {
        above = focus.itemAbove
        if (above.item.itemClass != ItemClass.Cave_down) {
            if (above.nothing || focus.moveItemAbove()) {
                focus.itemAbove = server.contentManager.createItemInstance(980)
            }
        }
    }
}

onCompleteItemUse {
    if (event.tool.itemInstance.item.name == "Shovel" && event.focus.itemInstance.item.cave) {
        if (event.focus.itemInstance.item.itemClass == ItemClass.Cave_down) {
            if (event.focus.itemBelow.item.itemClass == ItemClass.Cave_up) {
                event.focus.itemBelow = server.contentManager.createItemInstance(0)
            }
        } else {
            if (event.focus.itemAbove.item.itemClass == ItemClass.Cave_down) {
                event.focus.itemAbove = server.contentManager.createItemInstance(0)
            }
        }
    }
}
