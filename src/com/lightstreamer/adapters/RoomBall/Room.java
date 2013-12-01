package com.lightstreamer.adapters.RoomBall;

public interface Room {

    public void start();

    public void stop();

    public String addPlayer(String proposedName, String userAgent) throws RoomException;

    public void removePlayer(String name);

    public void updatePlayerMsg(String name, String message);

    public void dispatchCommand(String name, String command);

    public void touchAllElements();

}
