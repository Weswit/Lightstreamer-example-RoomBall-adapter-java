package com.lightstreamer.adapters.RoomBall;

import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

class BallFactory {

    public static final float DEFAULT_BALL_RESTITUTION = 1;

    private static final float BALL_RADIUS =   15.0f;
    private static final int BALL_SPEED =    30000;

    public Ball createElement(World world) {
        Body ball = createBody(world);
        ball.setLinearVelocity(new Vec2(0, BALL_SPEED));
        return new Ball(ball);
    }

    private Body createBody(World world) {
        CircleShape shape = new CircleShape();
        shape.setRadius(BALL_RADIUS);

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        bodyDef.position.set(WorldFactory.WORLD_SIZE_X / 2, 0);
        bodyDef.angle = (float) (Math.PI);
        bodyDef.allowSleep = false;
        bodyDef.linearDamping = 0.3f;
        bodyDef.angularDamping = 10.2f;
        Body body = world.createBody(bodyDef);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1f;
        fixtureDef.friction = 0.3f;
        fixtureDef.restitution = DEFAULT_BALL_RESTITUTION;
        body.createFixture(fixtureDef);

        body.setUserData(Ball.USERDATA_BALL);
        return body;
    }
}