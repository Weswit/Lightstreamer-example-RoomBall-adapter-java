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

import java.util.Map;

public class Event {

    public static final String FIELD_KEY = "key";
    public static final String FIELD_COMMAND = "command";
    public static final String FIELD_POS_Y = "posY";
    public static final String FIELD_POS_X = "posX";
    public static final String FIELD_USR_AGNT = "usrAgnt";
    public static final String FIELD_MSG = "msg";

    private final Map<String, String> itemEvent;
    private final boolean isSnapshot;

    public Event(Map<String, String> itemEvent, boolean isSnapshot) {
        super();
        this.itemEvent = itemEvent;
        this.isSnapshot = isSnapshot;
    }

    public Map<String, String> getItemEvent() {
        return itemEvent;
    }

    public boolean isSnapshot() {
        return isSnapshot;
    }

    public String getCommand() {
        return itemEvent.get(FIELD_COMMAND);
    }

    public String getKey() {
        return itemEvent.get(FIELD_KEY);
    }
}
