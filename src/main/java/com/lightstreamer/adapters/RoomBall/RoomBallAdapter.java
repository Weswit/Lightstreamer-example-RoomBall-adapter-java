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

import java.io.File;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lightstreamer.interfaces.data.DataProviderException;
import com.lightstreamer.interfaces.data.FailureException;
import com.lightstreamer.interfaces.data.ItemEventListener;
import com.lightstreamer.interfaces.data.SmartDataProvider;
import com.lightstreamer.interfaces.data.SubscriptionException;

public class RoomBallAdapter implements SmartDataProvider, Publisher {

    static final String ROOM_DEMO_LOGGER_NAME = "LS_demos_Logger.RoomBallDemo";
    
    private static final String CFG_PARAM_FRAME_RATE = "frameRate";
    private static final String CFG_PARAM_STEPS_PER_FRAME = "stepsPerFrame";

    private static final String ITEM_NAME_PLAYERS_LIST = "Players_list";
    private static final String ITEM_NAME_PREFIX_BAND = "My_Band_";

    private Logger logger;

    /**
     * The listener of updates set by Lightstreamer Kernel.
     */
    private volatile ItemEventListener listener = null;

    /**
     * Flag indicating whether the item "Players_list" is subscribed or not
     * It is not guarded by any lock to avoid to lock the call to
     * listener.smartUpdate(..). If, due to a race condition, smartUpdate is
     * called after an unsubscribe, the server will ignore the update, and
     * may log a warning.
     */
    volatile private boolean playerListSubscribed = false;

    /**
     * The room where the players and the ball move
     */
    private Room room;

    /**
     * The handle received by subscription and used to send messages to.
     */
    private volatile Object playerListHandle = null;

    // Public Methods Implementing DataProvider Methods ------------------------

    @Override
    @SuppressWarnings("rawtypes")
    public void init(Map params, File configDir) throws DataProviderException {

        try {
            logger = LogManager.getLogger(ROOM_DEMO_LOGGER_NAME);
        } catch (Exception e) {
            System.out.println("Loggers failed to load: " + e);
        }

        room = initRoom(params);

        if (room == null) {
            throw new DataProviderException("Room not ready!");
        }
        
        logger.debug("RoomAdapter start!");
    }

    @Override
    public void setListener(ItemEventListener lstnr) {
        if (listener == null) {
            listener = lstnr;
        }
    }

    @Override
    public boolean isSnapshotAvailable(String itemName)  throws SubscriptionException {
        if (itemName.startsWith(ITEM_NAME_PLAYERS_LIST)) {
            return true;
        }
        return false;
    }

    @Override
    public void subscribe(String itemName, Object handle, boolean needsIterator) throws SubscriptionException,FailureException {

         if (itemName.startsWith(ITEM_NAME_PLAYERS_LIST)) {
             logger.debug("Subscribe request for '" + ITEM_NAME_PLAYERS_LIST + "'.");
             playerListSubscribed = true;
             playerListHandle = handle;
             room.start();
             room.touchAllElements();

        } else if (itemName.startsWith(ITEM_NAME_PREFIX_BAND)) {
            // Nothing to do.
        }
    }

    @Override
    public void subscribe(String itemName, boolean needsIterator)
            throws SubscriptionException, FailureException {
        // Never Called.
    }

    @Override
    public void unsubscribe(String itemName) throws SubscriptionException, FailureException {

        if (itemName.startsWith(ITEM_NAME_PLAYERS_LIST)) {
            logger.debug("Unsubscribe request for '" + itemName + "'.");
            playerListSubscribed = false;
            room.stop();
        } else if (itemName.startsWith(ITEM_NAME_PREFIX_BAND)) {
            room.killBandChecker(itemName);
        }
    }

    // Public Methods Implementing Publisher Methods ---------------------------

    @Override
    public void publish(Event event) {

        try {

            if ( listener == null ) {
                return ;
            }
            if (playerListHandle == null) {
                return;
            }

            if (!playerListSubscribed) {
                return;
            }

            logger.debug("Update list " + ITEM_NAME_PLAYERS_LIST + " " + event.getCommand() + " " + event.getKey());

            listener.smartUpdate(playerListHandle, event.getItemEvent(), event.isSnapshot());

        } catch (Exception e) {
            logger.warn("Exception sending event.", e);
        }
    }

    @Override
    public void publishEOS() {

        if (listener == null) {
            return;
        }
        if (playerListHandle == null) {
            return;
        }

        // call the update on the listener;
        // in case the listener has just been detached,
        // the listener should detect the case
        listener.smartEndOfSnapshot(playerListHandle);
    }

    @Override
    public void postBandwith(String userName, Map<String, String> itemEvent) {
        if (listener == null) {
            return;
        }

        listener.update(ITEM_NAME_PREFIX_BAND+userName, itemEvent, false);
    }

    // Private Methods ---------------------------------------------------------

    @SuppressWarnings("rawtypes")
    private Box2DRoom initRoom(Map params) {
        
        Box2DRoom room = Box2DRoom.getInstance();
        
        if (room == null) {
            return null;
        }
        
        room.setPublisher(this);

        if (params.containsKey(CFG_PARAM_FRAME_RATE)) {
            logger.debug("frameRate configured: " + params.get(CFG_PARAM_FRAME_RATE));
            room.setFrameRate(new Integer((String)params.get(CFG_PARAM_FRAME_RATE)).intValue());
        }

        if (params.containsKey(CFG_PARAM_STEPS_PER_FRAME)) {
            logger.debug("Steps per Frame configured: " + params.get(CFG_PARAM_STEPS_PER_FRAME));
            room.setStepsPerFrame(new Integer((String)params.get(CFG_PARAM_STEPS_PER_FRAME)).intValue());
        }

        return room;
    }

}