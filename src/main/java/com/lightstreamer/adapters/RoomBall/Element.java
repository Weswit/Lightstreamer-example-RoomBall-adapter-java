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

import org.jbox2d.dynamics.World;

public interface  Element {

    public double  getX ( );

    public double  getY ( );

    public String getName();

    public void close(World world);

    public boolean isChanged();

    public void setAsChanged();

    public void setAsNotChanged();

    public void synchWithWorld();

    public void accept(ElementVisitor visitor);

}