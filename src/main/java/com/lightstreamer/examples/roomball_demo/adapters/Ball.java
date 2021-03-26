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

import org.jbox2d.dynamics.Body;

public class Ball extends AbstractElement {

    public static final String NAME_BALL = "Ball-1";

    public static final String USERDATA_BALL = "BALL";

    public Ball(Body body) {
        super(NAME_BALL, body);
    }

    @Override
    public void accept(ElementVisitor visitor) {
        visitor.visit(this);
    }

}
