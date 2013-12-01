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
//    private static final String CFG_PARAM_ADAPTER_SET_ID = "adapters_conf.id";

    private static final String ITEM_NAME_PLAYERS_LIST = "Players_list";
    private static final String ITEM_NAME_PREFIX_BAND = "My_Band_";

    private static final String FIELD_KEY = "key";
    private static final String FIELD_COMMAND = "command";
    private static final String FIELD_CURRENT_BANDWIDTH = "currentBandwidth";

    private static final String CMD_ADD = "ADD";
    private static final String CMD_UPDATE = "UPDATE";
    private static final String CMD_DELETE = "DELETE";

    private Logger logger;

    /**
     * should be supplied by logback configuration.
     */
    private static Logger tracer = null;

    /**
     * The listener of updates set by Lightstreamer Kernel.
     */
    private static ItemEventListener listener = null;

    private boolean playerListSubscribed = false;

    private static WorldsStatistics stats = null;

    private Room room;

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
             setSubscribed();
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
            setUnsubscribed();
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
    public void publishAdd(Element box) {
        boolean isSnapshot = false;
        sendUpdate(CMD_ADD, box, isSnapshot);
    }

    @Override
    public void publishDelete(Element box) {
        boolean isSnapshot = false;
        sendUpdate(CMD_DELETE, box, isSnapshot);
    }

    @Override
    public void publishUpdate(Element element) {
        if (!element.isChanged()) {
            return;
        }
        boolean isSnapshot = false;
        sendUpdate(CMD_UPDATE, element, isSnapshot);
        element.setAsNotChanged();
    }

    @Override
    public void publishTouch(Element body) {
        boolean isSnapshot = true;
        sendUpdate(CMD_ADD, body, isSnapshot);
    }

    @Override
    public void publishEOS() {

        //If we have a listener create a new Runnable to be used as a task to pass the
        //new update to the listener
        if (listener == null) {
            return;
        }

        // call the update on the listener;
        // in case the listener has just been detached,
        // the listener should detect the case
        listener.endOfSnapshot(ITEM_NAME_PLAYERS_LIST);
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

        room.start();
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

    // synchronized playerListSubscribed
    synchronized private void sendUpdate(final String command, final Element elemen,
            final boolean isSnapshot) {

        try {

            if ( listener == null ) {
                return ;
            }

            if (!playerListSubscribed) {
                return;
            }

            if ( tracer != null && tracer.isTraceEnabled()) {
                tracer.trace(command +" '" + elemen.getName() + "'.");
            }
            logger.debug("Update list " + ITEM_NAME_PLAYERS_LIST + " " + command + " " + elemen.getName());

            HashMap<String, String> update = new HashMap<String, String>();

            update.put(FIELD_KEY, elemen.getName());
            update.put(FIELD_COMMAND, command);
            elemen.addFieldsTo(update);

            listener.update(ITEM_NAME_PLAYERS_LIST, update, isSnapshot);

        } catch (Exception e) {
            logger.warn("Exception in "+command+" procedure.", e);
        }
    }

    synchronized private void setSubscribed() {
        playerListSubscribed = true;
    }

    synchronized private void setUnsubscribed() {
        playerListSubscribed = false;
    }


}