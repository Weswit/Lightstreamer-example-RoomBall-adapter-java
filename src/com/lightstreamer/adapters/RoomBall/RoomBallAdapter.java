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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.lightstreamer.interfaces.data.DataProviderException;
import com.lightstreamer.interfaces.data.FailureException;
import com.lightstreamer.interfaces.data.ItemEventListener;
import com.lightstreamer.interfaces.data.SmartDataProvider;
import com.lightstreamer.interfaces.data.SubscriptionException;

public class RoomBallAdapter implements SmartDataProvider, Publisher {

    private static final String CFG_PARAM_FRAME_RATE = "frameRate";
    private static final String CFG_PARAM_STEPS_PER_FRAME = "stepsPerFrame";

    private static final String ITEM_NAME_PLAYERS_LIST = "Players_list";
    private static final String ITEM_NAME_PREFIX_BAND = "My_Band_";

    private static final String FIELD_CURRENT_BANDWIDTH = "currentBandwidth";

    private Logger logger;

    /**
     * should be supplied by logback configuration.
     */
    private static Logger tracer = null;

    /**
     * The listener of updates set by Lightstreamer Kernel.
     */
    // TODO static?
    private static volatile ItemEventListener listener = null;

    /**
     * Flag indicating whether the item "Players_list" is subscribed or not
     * It is not guarded by any lock to avoid to lock the call to
     * listener.smartUpdate(..). If, due to a race condition, smartUpdate is
     * called after an unsubscribe, the server will ignore the update, and
     * may log a warning.
     */
    volatile private boolean playerListSubscribed = false;

    private static WorldsStatistics stats = null;

    private Room room;

    private volatile Object playerListHandle = null;

    // Public Methods Implementing DataProvider Methods ------------------------

    @Override
    @SuppressWarnings("rawtypes")
    public void init(Map params, File configDir) throws DataProviderException {

        try {
            logger = Logger.getLogger(RoomBallMetaAdapter.ROOM_DEMO_LOGGER_NAME);

            tracer = Logger.getLogger(RoomBallMetaAdapter.TRACER_LOGGER);
            tracer.info("LS_RoomDemo_Logger start.");

        } catch (Exception e) {
            System.out.println("Loggers failed to load: " + e);
        }

        stats = new WorldsStatistics(0);

        room = initRoom(params);

        logger.info("RoomAdapter start!");
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
            RoomBallMetaAdapter.killBandChecker(itemName);
        }
    }

    // Public Methods Implementing Publisher Methods ---------------------------

    @Override
    public void postOverallBandwidth() {
        double totBandwidth = RoomBallMetaAdapter.getTotalBandwidthOut();

        if ( tracer != null && tracer.isDebugEnabled()) {
            tracer.debug("Statistics - Total bandwidth for the demo: " + totBandwidth + ".");
        }

        // update statistics.
        stats.feedBandwidth(totBandwidth);
        return ;
    }

    @Override
    public void flushStatistics() {
        if ( tracer != null ) {
            tracer.debug(stats);
        }
        stats.reset();
    }

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

            if ( tracer != null && tracer.isTraceEnabled()) {
                tracer.trace(event.getCommand() +" '" + event.getKey() + "'.");
            }
            logger.debug("Update list " + ITEM_NAME_PLAYERS_LIST + " " + event.getCommand() + " " + event.getKey());

            listener.smartUpdate(playerListHandle, event.getItemEvent(), event.isSnapshot());

        } catch (Exception e) {
            logger.warn("Exception sending event.", e);
        }
    }

    @Override
    public void publishEOS() {

        //If we have a listener create a new Runnable to be used as a task to pass the
        //new update to the listener
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

    // TODO static?
    public static void postBandwith(String userName, Double d) {
        if (listener == null) {
            return;
        }
        if ( tracer != null && tracer.isDebugEnabled()) {
            tracer.debug("Update current bandwidth for user " + userName + ": " + d);
        }

        final HashMap<String, String> update = new HashMap<String, String>();
        update.put(FIELD_CURRENT_BANDWIDTH, roundToSend(d, 2));
        listener.update(ITEM_NAME_PREFIX_BAND+ userName,update,false);
    }

    // Private Methods ---------------------------------------------------------

    @SuppressWarnings("rawtypes")
    private Box2DRoom initRoom(Map params) {
        Box2DRoom room = Box2DRoom.getInstance();
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

    private static String roundToSend(double value, int pre) {
        String tmp = ""+value;

        int cut = tmp.length() - tmp.indexOf(".") - 1;
        if (pre < cut  ) {
            tmp = tmp.substring(0, tmp.length() - (cut - pre));
        }

        return tmp;
    }

}