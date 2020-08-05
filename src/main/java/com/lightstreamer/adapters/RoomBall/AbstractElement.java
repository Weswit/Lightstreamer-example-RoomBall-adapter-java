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

package com.lightstreamer.adapters.RoomBall;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.World;


abstract public class AbstractElement implements Element {

    // Nested Classes ----------------------------------------------------------

    static class Impulse {

        public static final Impulse NULL = new Impulse(0, 0);

        private final int intensityX;
        private final int intensityY;

        public Impulse(int intensityX, int intensityY) {
            super();
            this.intensityX = intensityX;
            this.intensityY = intensityY;
        }

        public float getIntensityX() {
            return intensityX;
        }
        public float getIntensityY() {
            return intensityY;
        }

        /**
         * Sum two Impulse.
         * If the two Impulse have the same direction, then the two Impulses
         * are not added.
         *
         * @param that The Impulse to add
         * @return The resulting Impulse
         */
        public Impulse sum(Impulse that) {

            int correctedX;
            if ((this.intensityX > 0 &&  that.intensityX > 0) ||
                    (this.intensityX < 0 &&  that.intensityX < 0) )
            {
                correctedX = 0;
            } else {
                correctedX = that.intensityX;
            }

            int correctedY;
            if ((this.intensityY > 0 &&  that.intensityY > 0) ||
                    (this.intensityY < 0 &&  that.intensityY < 0) )
            {
                correctedY = 0;
            } else {
                correctedY = that.intensityY;
            }

            int newX = this.intensityX + correctedX;
            int newY = this.intensityY + correctedY;
            return new Impulse(newX, newY);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + intensityX;
            result = prime * result + intensityY;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Impulse other = (Impulse) obj;
            if (intensityX != other.intensityX)
                return false;
            if (intensityY != other.intensityY)
                return false;
            return true;
        }
    }

    // Constants ---------------------------------------------------------------

    private static final double EPSILON = 0.1;

    // Member Fields  ----------------------------------------------------------

    private final String name;

    private final Body body;

    private Double lastPosX = Double.NaN;
    private Double lastPosY = Double.NaN;

    private Double currPosX = Double.NaN;
    private Double currPosY = Double.NaN;

    // Constructor -------------------------------------------------------------

    public AbstractElement(String name,Body body) {
        this.name = name;
        this.body = body;
        synchWithWorld();
    }

    // Public Methods ----------------------------------------------------------

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isChanged() {
        return posXIsChanged() || posYIsChanged();
    }

    @Override
    public void setAsChanged() {
        this.lastPosX = Double.NaN;
        this.lastPosY = Double.NaN;
    }

    @Override
    public void setAsNotChanged() {
        this.lastPosX = currPosX;
        this.lastPosY = currPosY;
    }

    @Override
    public void close(World world) {
        // already synchronized by caller
        world.destroyBody(body);
    }

    @Override
    public double getX() {
        return currPosX;
    }

    @Override
    public double getY() {
        return currPosY;
    }

    @Override
    public void synchWithWorld() {
        Vec2 position = body.getPosition();
        this.currPosX = (double) position.x;
        this.currPosY = (double) position.y;
    }

    // Protected Methods -------------------------------------------------------

    protected boolean posXIsChanged() {
        return (lastPosX.isNaN() || Math.abs(lastPosX - currPosX) >= EPSILON );
    }

    protected boolean posYIsChanged() {
        return (lastPosY.isNaN() || Math.abs(lastPosY - currPosY) >= EPSILON );
    }

    protected void applyImpulse(Impulse impulse) {
        if (impulse.equals(Impulse.NULL)) {
            return;
        }
        body.applyLinearImpulse(new Vec2(impulse.getIntensityX(),
                impulse.getIntensityY()),
                body.getWorldCenter());
    }

}
