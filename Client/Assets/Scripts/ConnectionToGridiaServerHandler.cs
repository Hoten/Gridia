﻿using Newtonsoft.Json.Linq;
using Serving;
using Serving.FileTransferring;
using System;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Text;
using UnityEngine;

public class ConnectionToGridiaServerHandler : SocketHandler
{
    private GridiaGame _game;
    private HashSet<Vector3> _sectorsRequested = new HashSet<Vector3>();
    private HashSet<int> _creaturesRequested = new HashSet<int>();
    public Action<JObject> GenericEventHandler { get; set; }

    private FileTransferringSocketReciever _socketHandler;

    public ConnectionToGridiaServerHandler(String host, int port, GridiaGame game)
    {
        _socketHandler = new FileTransferringSocketReciever(new SocketHandlerImpl(host, port));
        _game = game;
    }

    public void Start(Action onConnectionSettled, SocketHandler topLevelSocketHandler)
    {
        _socketHandler.Start(onConnectionSettled, topLevelSocketHandler);
    }

    public void Close()
    {
        _socketHandler.Close();
    }

    public void Send(Message message)
    {
        _socketHandler.Send(message);
    }

    public JavaBinaryReader GetInputStream()
    {
        return _socketHandler.GetInputStream();
    }

    public JavaBinaryWriter GetOutputStream()
    {
        return _socketHandler.GetOutputStream();
    }

    // :(
    public long getSystemTime()
    {
        return DateTime.Now.Ticks / TimeSpan.TicksPerMillisecond;
    }

    //outbound

    public void PlayerMove(Vector3 delta, bool onRaft, int timeForMovement)
    {
        Message message = new JsonMessageBuilder()
            .Type("PlayerMove")
            .Set("delta", new { x = (int)delta.x, y = (int)delta.y, z = (int)delta.z }) // :(
            .Set("onRaft", onRaft)
            .Set("timeForMovement", timeForMovement)
            .Build();
        _socketHandler.Send(message);
    }

    public void SectorRequest(int x, int y, int z)
    {
        if (_sectorsRequested.Contains(new Vector3(x, y, z)))
        {
            return;
        }
        _sectorsRequested.Add(new Vector3(x, y, z));

        Message message = new JsonMessageBuilder()
            .Type("SectorRequest")
            .Set("x", x)
            .Set("y", y)
            .Set("z", z)
            .Build();
        _socketHandler.Send(message);
    }

    public void CreatureRequest(int id)
    {
        if (_creaturesRequested.Contains(id))
        {
            return;
        }
        //Debug.LogError("no creature of id: " + id); // :(
        _creaturesRequested.Add(id);

        Message message = new JsonMessageBuilder()
            .Type("CreatureRequest")
            .Set("id", id)
            .Build();
        _socketHandler.Send(message);
    }

    public void MoveItem(String source, String dest, int sourceIndex, int destIndex, int quantity = -1)
    {
        Message message = new JsonMessageBuilder()
            .Type("MoveItem")
            .Set("source", source)
            .Set("dest", dest)
            .Set("si", sourceIndex)
            .Set("di", destIndex)
            .Set("quantity", quantity)
            .Build();
        _socketHandler.Send(message);
    }

    public void UseItem(String source, String dest, int sourceIndex, int destIndex)
    {
        Message message = new JsonMessageBuilder()
            .Type("UseItem")
                .Set("source", source)
                .Set("dest", dest)
                .Set("si", sourceIndex)
                .Set("di", destIndex)
                .Build();
        _socketHandler.Send(message);
    }

    public void PickItemUse(int useIndex)
    {
        Message message = new JsonMessageBuilder()
            .Type("PickItemUse")
                .Set("useIndex", useIndex)
                .Build();
        _socketHandler.Send(message);
    }

    public void Chat(String text)
    {
        Message message = new JsonMessageBuilder()
            .Type("Chat")
                .Set("msg", text)
                .Build();
        _socketHandler.Send(message);
    }

    public void EquipItem(int slotIndex)
    {
        Message message = new JsonMessageBuilder()
            .Type("EquipItem")
                .Set("slotIndex", slotIndex)
                .Build();
        _socketHandler.Send(message);
    }

    public void UnequipItem(int slotIndex)
    {
        Message message = new JsonMessageBuilder()
            .Type("UnequipItem")
                .Set("slotIndex", slotIndex)
                .Build();
        _socketHandler.Send(message);
    }

    public void Hit(Vector3 loc)
    {
        Message message = new JsonMessageBuilder()
            .Type("Hit")
                .Set("loc", new { x = loc.x, y = loc.y, z = loc.z })
                .Build();
        _socketHandler.Send(message);
    }

    public void AdminMakeItem(Vector3 loc, int itemIndex)
    {
        Message message = new JsonMessageBuilder()
            .Type("AdminMakeItem")
                .Set("loc", new { x = loc.x, y = loc.y, z = loc.z })
                .Set("item", itemIndex)
                .Build();
        _socketHandler.Send(message);
    }

    public void AdminMakeFloor(Vector3 loc, int floorIndex)
    {
        Message message = new JsonMessageBuilder()
            .Type("AdminMakeFloor")
                .Set("loc", new { x = loc.x, y = loc.y, z = loc.z })
                .Set("floor", floorIndex)
                .Build();
        _socketHandler.Send(message);
    }

    public void Register(String username, String password)
    {
        var passwordHash = MD5.Create().ComputeHash(Encoding.ASCII.GetBytes(password));
        Message message = new JsonMessageBuilder()
            .Type("Register")
                .Set("username", username)
                .Set("passwordHash", passwordHash)
                .Build();
        _socketHandler.Send(message);
    }

    public void Login(String username, String password)
    {
        var passwordHash = MD5.Create().ComputeHash(Encoding.ASCII.GetBytes(password));
        Message message = new JsonMessageBuilder()
            .Type("Login")
                .Set("username", username)
                .Set("passwordHash", passwordHash)
                .Build();
        _socketHandler.Send(message);
    }

    public GridiaGame GetGame()
    {
        return _game;
    }
}
