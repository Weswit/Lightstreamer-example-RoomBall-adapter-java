/*
 *
 *  Copyright 2013 Weswit s.r.l.
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

import java.util.HashMap;
import java.util.Map;

public class EventCreator implements Visitor {

    private final String command;
    private final boolean isSnapshot;

    protected static final String CMD_ADD = "ADD";
    protected static final String CMD_UPDATE = "UPDATE";
    protected static final String CMD_DELETE = "DELETE";

    Event event;

    public EventCreator(String command, boolean isSnapshot) {
        this.command = command;
        this.isSnapshot = isSnapshot;
    }

    @Override
    public void visit(Player player) {

        Map<String, String> itemEvent = new HashMap<String, String>();

        itemEvent.put(Event.FIELD_KEY, player.getName());
        itemEvent.put(Event.FIELD_COMMAND, command);
        if (player.posYIsChanged()) {
            itemEvent.put(Event.FIELD_POS_Y, ""+Math.round(player.getY()));
        }
        if (player.posXIsChanged()) {
            itemEvent.put(Event.FIELD_POS_X, ""+Math.round(player.getX()));
        }
        if (player.lastMsgIsChanged()) {
            itemEvent.put(Event.FIELD_MSG, player.getLastMsg());
        }
        if (player.usrAgentIsChanged()) {
            itemEvent.put(Event.FIELD_USR_AGNT, player.getUsrAgent());
        }

        event = new Event(itemEvent, isSnapshot);

    }

    @Override
    public void visit(Ball ball) {

        Map<String, String> itemEvent = new HashMap<String, String>();

        itemEvent.put(Event.FIELD_KEY, ball.getName());
        itemEvent.put(Event.FIELD_COMMAND, command);
        if (ball.posYIsChanged()) {
            itemEvent.put(Event.FIELD_POS_Y, ""+Math.round(ball.getY()));
        }
        if (ball.posXIsChanged()) {
            itemEvent.put(Event.FIELD_POS_X, ""+Math.round(ball.getX()));
        }

        event = new Event(itemEvent, isSnapshot);

    }

    public Event getEvent() {
        return this.event;
    }

}
