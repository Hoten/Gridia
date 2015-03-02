﻿using Gridia;
using System.Collections.Generic;
using UnityEngine;

public class GridiaGame
{
    public TileMap TileMap;
    public TileMapView View;
    public StateMachine StateMachine = new StateMachine();
    public List<AnimationRenderable> Animations = new List<AnimationRenderable>();

    private Vector3 _selectorDelta = Vector3.zero;
    public Vector3 SelectorDelta
    {
        get
        {
            return _selectorDelta;
        }
        set
        {
            _selectorDelta = value;
            _selectorDelta.x = Mathf.Clamp(_selectorDelta.x, -2, 2);
            _selectorDelta.y = Mathf.Clamp(_selectorDelta.y, -2, 2);
        }
    }

    public bool HideSelector = true;
    public bool IsConnected;
    private GridiaDriver _driver;

    public void Initialize(int size, int depth, int sectorSize) {
        Locator.Provide(StateMachine);
        TileMap = new TileMap(size, depth, sectorSize);
        Locator.Provide(TileMap);
        View = new TileMapView(TileMap, Locator.Get<TextureManager>(), 1.75f);
        Locator.Provide(View);
        StateMachine.SetState(new IdleState());
        _driver = Locator.Get<GridiaDriver>();
    }

    // :(
    public void InitAdminWindowTab()
    {
        var adminWindow = new AdminWindow(Vector2.zero);
        _driver.TabbedGui.Add(10, adminWindow, false);
    }

    public Vector3 GetSelectorCoord()
    {
        return GetSelectorCoord(SelectorDelta);
    }

    // :(
    public Vector3 GetSelectorCoord(Vector3 sDelta)
    {
        return View.FocusPosition + sDelta + new Vector3(View.Width / 2, View.Height / 2);
    }

    public Vector3 GetScreenPosition(Vector3 coord) 
    {
        var tileSize = 32 * View.Scale;
        var relative = coord - View.FocusPosition;
        return new Vector2(relative.x * tileSize, Screen.height - relative.y * tileSize - tileSize);
    }

    public void DropItemAtSelection() 
    {
        if (_driver.SelectedContainer == null)
        {
            DropItemAt(View.Focus.Position + SelectorDelta);
        }
        else
        {
            var destIndex = _driver.SelectedContainer.SlotSelected;
            var slotSelected = _driver.InvGui.SlotSelected;
            Locator.Get<ConnectionToGridiaServerHandler>().MoveItem(_driver.InvGui.ContainerId, _driver.SelectedContainer.ContainerId, slotSelected, destIndex, 1); // :(
        }
    }

    private void DropItemAt(Vector3 dropItemLoc) 
    {
        var destIndex = Locator.Get<TileMap>().ToIndex(dropItemLoc);
        var slotSelected = _driver.InvGui.SlotSelected;
        Locator.Get<ConnectionToGridiaServerHandler>().MoveItem(_driver.InvGui.ContainerId, 0, slotSelected, destIndex, 1); // :(
    }

    public void PickUpItemAtSelection() 
    {
        if (_driver.SelectedContainer == null)
        {
            PickUpItemAt(View.Focus.Position + SelectorDelta);
        }
        else
        {
            var pickupItemIndex = _driver.SelectedContainer.SlotSelected;
            Locator.Get<ConnectionToGridiaServerHandler>().MoveItem(_driver.SelectedContainer.ContainerId, _driver.InvGui.ContainerId, pickupItemIndex, -1); // :(
        }
    }

    private void PickUpItemAt(Vector3 pickupItemLoc)
    {
        pickupItemLoc = TileMap.Wrap(pickupItemLoc);
        var pickupItemIndex = TileMap.ToIndex(pickupItemLoc);
        Locator.Get<ConnectionToGridiaServerHandler>().MoveItem(0, _driver.InvGui.ContainerId, pickupItemIndex, -1); // :(
    }

    public void UseItemAtSelection(int sourceIndex)
    {
        if (_driver.SelectedContainer == null)
        {
            var destIndex = TileMap.ToIndex(GetSelectorCoord());
            UseItemAt(_driver.InvGui.ContainerId, sourceIndex, 0, destIndex);
        }
        else
        {
            UseItemAt(_driver.InvGui.ContainerId, sourceIndex, _driver.SelectedContainer.ContainerId, _driver.SelectedContainer.SlotSelected);
        }
    }

    private void UseItemAt(int source, int sourceIndex, int dest, int destIndex) 
    {
        Locator.Get<ConnectionToGridiaServerHandler>().UseItem(source, dest, sourceIndex, destIndex);
    }
}