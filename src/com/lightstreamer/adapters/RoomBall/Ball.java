package com.lightstreamer.adapters.RoomBall;

import org.jbox2d.dynamics.Body;

public class Ball extends AbstractElement {

    public static final String NAME_BALL = "Ball-1";

    public static final String USERDATA_BALL = "BALL";

    public Ball(Body body) {
        super(NAME_BALL, body);
    }



}
