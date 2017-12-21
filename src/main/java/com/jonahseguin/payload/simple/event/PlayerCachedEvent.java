package com.jonahseguin.payload.simple.event;

import com.jonahseguin.payload.simple.simple.PlayerCacheable;
import lombok.Getter;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerCachedEvent<X extends PlayerCacheable> extends Event {

    private static final HandlerList handlerList = new HandlerList();

    private final X player;

    public PlayerCachedEvent(X player) {
        this.player = player;
    }

    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

    public static HandlerList getHandlerList() {
        return handlerList;
    }

    public X getPlayer() {
        return player;
    }
}
