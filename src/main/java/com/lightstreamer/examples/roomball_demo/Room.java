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

package com.lightstreamer.examples.roomball_demo;

public interface Room {

    public void start();

    public void stop();

    public void addPlayer(String proposedName, String userAgent) throws RoomException;

    public void removePlayer(String name);

    public void updatePlayerMsg(String name, String message);

    public void dispatchCommand(String name, String command);

    public void touchAllElements();

    public void addPollsBandwidth(String sessionID, String itemName, String usr, int jmxPort);

    public void killBandChecker(String itemName);

}
