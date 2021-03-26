/*
 *
 *  Copyright (c) Lightstreamer Srl
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.lightstreamer.examples.roomball_demo.adapters;

import java.util.Random;

import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

class PlayerFactory {

    private static final float PLAYER_RADIUS = 23.0f; //used for initial placement only

    private static final int PLACEMENT_CYCLES_GIVEUP = 100;

    //avatar definition
    private static final int PTM_RATIO = 1;
    private static final Vec2[][] POLYGONS =
    {
        {   new Vec2(5/PTM_RATIO, 46/PTM_RATIO)  ,  new Vec2(5/PTM_RATIO, 26/PTM_RATIO)  ,  new Vec2(15/PTM_RATIO, 26/PTM_RATIO)  ,  new Vec2(15/PTM_RATIO, 46/PTM_RATIO)  } ,
        {   new Vec2(5/PTM_RATIO, 26/PTM_RATIO)  ,  new Vec2(2/PTM_RATIO, 10/PTM_RATIO)  ,  new Vec2(5/PTM_RATIO, 8/PTM_RATIO)  ,  new Vec2(7/PTM_RATIO, 7/PTM_RATIO)  ,  new Vec2(13/PTM_RATIO, 7/PTM_RATIO)  ,  new Vec2(18/PTM_RATIO, 10/PTM_RATIO)  ,  new Vec2(20/PTM_RATIO, 26/PTM_RATIO)  } ,
        {   new Vec2(2/PTM_RATIO, 10/PTM_RATIO)  ,  new Vec2(5/PTM_RATIO, 26/PTM_RATIO)  ,  new Vec2(0/PTM_RATIO, 26/PTM_RATIO)  } ,
        {   new Vec2(7/PTM_RATIO, 1/PTM_RATIO)  ,  new Vec2(9/PTM_RATIO, 0/PTM_RATIO)  ,  new Vec2(11/PTM_RATIO, 0/PTM_RATIO)  ,  new Vec2(14/PTM_RATIO, 5/PTM_RATIO)  ,  new Vec2(13/PTM_RATIO, 7/PTM_RATIO)  ,  new Vec2(7/PTM_RATIO, 7/PTM_RATIO)  ,  new Vec2(6/PTM_RATIO, 5/PTM_RATIO)  ,  new Vec2(6/PTM_RATIO, 2/PTM_RATIO)  } ,
        {   new Vec2(14/PTM_RATIO, 5/PTM_RATIO)  ,  new Vec2(11/PTM_RATIO, 0/PTM_RATIO)  ,  new Vec2(13/PTM_RATIO, 1/PTM_RATIO)  ,  new Vec2(14/PTM_RATIO, 2/PTM_RATIO)  }
    };


    private final Random randomizer = new Random();


    public Player createElement(World world, String name, String usrAgent) throws RoomException {
        Body body = null;

        for (int i= 0; i < PLACEMENT_CYCLES_GIVEUP; i++) {
            float x = randomizer.nextFloat() * WorldFactory.bRangeX;
            float y = randomizer.nextFloat() * WorldFactory.bRangeY;
            Body tmpBody = createBody(world, x, y);
            if (!collide(tmpBody, world)) {
                body = tmpBody;
                break;
            }
            world.destroyBody(tmpBody);
        }
        if (body == null) {
            throw new RoomException("Unable to create body for '" + name +"'");
        }
        return new Player(name, usrAgent, body);
    }

    private Body createBody(World world, float x, float y) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        bodyDef.position.set(x, y);
        bodyDef.angle = (float) (Math.PI);
        bodyDef.allowSleep = false;
        bodyDef.linearDamping = 5.5f;
        bodyDef.angularDamping = 10.2f;
        bodyDef.fixedRotation = true;

        Body body = world.createBody(bodyDef);
        body.setUserData(Player.USERDATA_PLAYER);

        for(int p=0; p<POLYGONS.length; p++)
        {
            PolygonShape polygonShape = new PolygonShape();
            polygonShape.set(POLYGONS[p], POLYGONS[p].length);

            FixtureDef fixtureDef = new FixtureDef();
            fixtureDef.shape=polygonShape;
            fixtureDef.density = 30;
            fixtureDef.friction = 0.3f;

            body.createFixture(fixtureDef);
        }

        return body;
    }

    private boolean collide(Body thisBody, World world) {

        for (Body thatBody = world.getBodyList(); thatBody!=null; thatBody = thatBody.getNext() ) {
            if (collide(thisBody, thatBody)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Collision Detection
     * @param body
     * @param otherBody
     * @return
     */
    private boolean collide(Body body, Body otherBody) {

        if (otherBody == body) {
            return false;
        }
        Vec2 center = body.getPosition();

        Vec2 other = otherBody.getPosition();
        double dist2 = (center.x - other.x) * (center.x - other.x)
                + (center.y - other.y) * (center.y - other.y);
        if (dist2 < PLAYER_RADIUS * 2) {
            return true;
        } else {
            return false;
        }
    }
}