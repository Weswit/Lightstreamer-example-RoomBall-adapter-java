package com.lightstreamer.adapters.RoomBall;

public interface Publisher {

    public void publishAdd(Element element);

    public void publishDelete(Element element);

    public void publishUpdate(Element element);

    public void publishTouch(Element element);

    public void publishEOS();

    public void postOverallBandwidth();

    public void flushStatistics();

}
