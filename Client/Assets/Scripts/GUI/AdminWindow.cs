﻿using System;
using UnityEngine;

namespace Gridia
{
    public class AdminWindow : GridiaWindow
    {
        private readonly BulkViewer _bulkItems;
        private readonly BulkViewer _bulkFloors;
        private readonly ContentManager _contentManager;

        public AdminWindow(Vector2 pos)
            : base(pos, "Admin Control Panel")
        {
            _contentManager = Locator.Get<ContentManager>();

            _bulkItems = new BulkViewer(
                Vector2.zero,
                () => _contentManager.ItemCount,
                index => new ItemRenderable(Vector2.zero, _contentManager.GetItem(index).GetInstance()),
                index =>
                {
                    var location = Locator.Get<GridiaGame>().GetSelectorCoord();
                    Locator.Get<ConnectionToGridiaServerHandler>().AdminMakeItem(location, index);
                }
            );

            _bulkFloors = new BulkViewer(
                new Vector2(10 * GridiaConstants.SpriteSize, 0),
                () => _contentManager.FloorCount,
                index => new FloorRenderable(Vector2.zero, index),
                index =>
                {
                    var location = Locator.Get<GridiaGame>().GetSelectorCoord();
                    Locator.Get<ConnectionToGridiaServerHandler>().AdminMakeFloor(location, index);
                }
            );

            AddChild(_bulkItems);
            AddChild(_bulkFloors);
        }

        private class BulkViewer : RenderableContainer
        {
            private readonly ExtendibleGrid _bulk = new ExtendibleGrid(new Vector2(0, 50));
            private int _currentPage;
            private const int PerPage = 50;
            private Vector2 _vector2;

            public BulkViewer(
                Vector2 pos,
                Func<int> getNumElements,
                Func<int, Renderable> getElement,
                Action<int> onElementClick
            )
                : base(pos)
            {
                _bulk.TileSelected = -1;

                _getNumElements = getNumElements;
                _getElement = getElement;
                _onElementClick = onElementClick;

                var prevPage = new Button(Vector2.zero, "Previous") {OnClick = () => { ShowPage(_currentPage - 1); }};

                var nextPage = new Button(new Vector2(120, 0), "Next") {OnClick = () => { ShowPage(_currentPage + 1); }};

                ShowPage(0);

                AddChild(prevPage);
                AddChild(nextPage);
                AddChild(_bulk);
            }

            private readonly Func<int> _getNumElements;

            private readonly Func<int, Renderable> _getElement;

            private readonly Action<int> _onElementClick;

            private void ShowPage(int page)
            {
                var maxPage = _getNumElements() / PerPage - 1;
                if (page == -1)
                {
                    page = maxPage;
                }
                else if (page == maxPage + 1)
                {
                    page = 0;
                }
                _currentPage = page;

                _bulk.RemoveAllChildren();
                var firstItemToShow = page * PerPage;
                var lastItemToShow = Math.Min(_getNumElements(), firstItemToShow + PerPage);

                for (var i = firstItemToShow; i < lastItemToShow; i++)
                {
                    var elementIndex = i;
                    var element = _getElement(i);
                    element.OnClick = () =>
                    {
                        _onElementClick(elementIndex);
                    };
                    _bulk.AddChild(element);
                }
            }
        }
    }
}
