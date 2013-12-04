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
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import ua_parser.Client;
import ua_parser.Parser;

import com.lightstreamer.adapters.metadata.LiteralBasedProvider;
import com.lightstreamer.interfaces.metadata.CreditsException;
import com.lightstreamer.interfaces.metadata.NotificationException;
import com.lightstreamer.interfaces.metadata.TableInfo;

public class RoomBallMetaAdapter extends LiteralBasedProvider {

    private static final int MAX_NUM_OF_PLAYERS = 200;

    public static final String ROOM_DEMO_LOGGER_NAME = "LS_demos_Logger.RoomDemo";
    public static final String TRACER_LOGGER = "LS_RoomDemo_Logger.tracer";

    private static final String FIELD_USER_AGENT = "USER_AGENT";
    private static final String FIELD_REMOTE_IP = "REMOTE_IP";

    private static final String CFG_PARAM_JMX_PORT = "jmxPort";

    private static final String ITEM_NAME_PREFIX_BAND = "My_Band_";

    private static final String ERROR = "Error";

    private static final String NAME_PREFIX_BALL = "Ball-";

    private static Logger logger;

    /**
     * Private logger; a specific "LS_RoomDemo_Logger" category
     * should be supplied by logback configuration.
     */
    public static Logger tracer = null;

    /**
     * Keeps the client context informations supplied by Lightstreamer on the
     * new session notifications.
     * Session information is needed to pass the IP to logging purpose.
     * Map of sessionInfo indexed by sessionID.
     */
    private final ConcurrentHashMap<String,Map<String,String>> sessions =
            new ConcurrentHashMap<String,Map<String,String>>();

    /**
     * map of player's names indexed by session id.
     * There can be only one player per session.
     */
    private final ConcurrentHashMap<String, String> nicksns =
            new ConcurrentHashMap<String, String>();

    /**
     * map of player's user agent indexed by session id
     */
    private final ConcurrentHashMap<String, String> usrAgnts =
            new ConcurrentHashMap<String, String>();

    /**
     * The room where the players live
     */
    private final Room room;

    /**
     * The Executor used to deliver the messages to the Room
     */
    private final Executor messageDeliveryExecutor;

    private int jmxPort = 9999;

    // Inner Classes -----------------------------------------------------------

    class NotifyNickNameTask implements Runnable {

        private final String sessionID;
        private final String name;

        public NotifyNickNameTask(String sessionID, String name) {
            super();
            this.sessionID = sessionID;
            this.name = name;
        }

        @Override
        public void run() {
            notifyNickName(sessionID, name);
        }
    }

    class NotifyChatMessageTask implements Runnable {

        private final String sessionID;
        private final String message;

        public NotifyChatMessageTask(String sessionID, String message) {
            super();
            this.sessionID = sessionID;
            this.message = message;
        }

        @Override
        public void run() {
            notifyChatMessage(sessionID, message);
        }
    }

    class NotifyCommandTask implements Runnable {

        private final String sessionID;
        private final String message;

        public NotifyCommandTask(String sessionID, String message) {
            super();
            this.sessionID = sessionID;
            this.message = message;
        }

        @Override
        public void run() {
            notifyCommand(sessionID, message);
        }
    }

    // Constructor -------------------------------------------------------------

    public RoomBallMetaAdapter() {
        room = Box2DRoom.getInstance();
        messageDeliveryExecutor = Executors.newSingleThreadExecutor();
    }

    // Public Methods ----------------------------------------------------------

    @SuppressWarnings("rawtypes")
    @Override
    public void init(Map params, File configDir) {

        logger = Logger.getLogger(ROOM_DEMO_LOGGER_NAME);

        try{
            tracer = Logger.getLogger(TRACER_LOGGER);
        } catch (Exception e) {
            logger.warn("Error on tracer initialization.",  e);
        }

        if (params.containsKey(CFG_PARAM_JMX_PORT)) {
            this.jmxPort = new Integer((String)params.get(CFG_PARAM_JMX_PORT)).intValue();
        }
        logger.info("JMX Port:" + this.jmxPort);
    }

    @Override
    public boolean wantsTablesNotification(java.lang.String user) {
        return true;
    }

    @Override
    public int getDistinctSnapshotLength(String item) {
        return 0;
    }

    @Override
    synchronized public void notifySessionClose(String sessionID) throws NotificationException {

        sessions.remove(sessionID);
        usrAgnts.remove(sessionID);

        String deadmanwalking = nicksns.remove(sessionID);
        if (deadmanwalking != null) {
            try {
                room.removePlayer(deadmanwalking);
            } catch (Exception e) {
                logger.warn(e);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked" })
    @Override
    synchronized public void notifyNewSession(String user, String sessionID, Map sessionInfo) throws CreditsException, NotificationException {

        sessions.put(sessionID, sessionInfo);
        addUserAgent(sessionID, sessionInfo, usrAgnts);
    }

    @Override
    public void notifyTablesClose(java.lang.String sessionID, TableInfo[] tables) {
    }

    @Override
    public void notifyNewTables(String user, String sessionID, TableInfo[] tables) throws CreditsException {

        String itemName = tables[0].getId();
        if ( itemName.startsWith(ITEM_NAME_PREFIX_BAND) ) {
            String usr = itemName.substring(ITEM_NAME_PREFIX_BAND.length());
            room.addPollsBandwidth(sessionID, itemName, usr, jmxPort);
        }
    }

    /**
     * The method should perform fast and the message processing is done asynchronously.
     */
    @Override
    public void notifyUserMessage(String user, String sessionID, String message)
            throws CreditsException {
        try {
            if (message == null) {
                return ;
            }

            if (sessions.get(sessionID) == null) {
                logger.warn("Message received from not-existent session '" + sessionID + "'.");
                return;
            }

            tracer.debug("Received message '"+message+"' from player '" + user + "', sessionId '"+sessionID+"'.");

            if (message.startsWith("n|") ) {

                message = removeTypeFrom(message);

                if (nicksns.size() >= MAX_NUM_OF_PLAYERS) {
                    logger.warn("Unable to add player: Room is overcrowded.");
                    throw new CreditsException(-2700, "Too many users. Please try again.");
                }

                final String proposedName = getNickNameFrom(message);
                if (proposedName.equals(ERROR)) {
                    throw new CreditsException(-2710, "Error logging in.");
                }

                if (!isNaN(proposedName)) {
                    throw new CreditsException(-6, "I'm not a number! I'm a free man!");
                }

                if (proposedName.indexOf(NAME_PREFIX_BALL) == 0) {
                    throw new CreditsException(-1, "Srsly? A ball?");
                }

                if (nicksns.containsKey(sessionID)) {
                    // duplicated message; it should be avoided when possible
                    String actualNickName = nicksns.get(sessionID);

                    if (!actualNickName.equalsIgnoreCase(proposedName)) {
                        throw new CreditsException(-2720, actualNickName);
                        // brings back to the case where the name has been
                        // changed, causing the client to receive a non
                        // blocking error.
                    }
                }

                String recommendedName = getRecommendedName(proposedName, nicksns.values());

                messageDeliveryExecutor.execute(new NotifyNickNameTask(sessionID, recommendedName));

                if (!recommendedName.equalsIgnoreCase(proposedName)) {
                    throw new CreditsException(-2720, recommendedName);
                    // brings back to the case where the name has been
                    // changed, causing the client to receive a non
                    // blocking error.
                }
            } else if ( message.startsWith("m|") ) {
                message = removeTypeFrom(message);
                messageDeliveryExecutor.execute(new NotifyChatMessageTask(sessionID, message));
            } else {
                messageDeliveryExecutor.execute(new NotifyCommandTask(sessionID, message));
            }
        } catch (CreditsException e) {
            throw e;
        } catch (Exception e) {
            // Skip, message not well formatted
            logger.warn("Unexpected error handling message from user '"+user+"', session '"+sessionID+"'.", e);
        }
    }

    // Private Methods ---------------------------------------------------------

    /**
     *
     * @param sessionID
     * @param message
     * @return An empty string if the nickmame has been accepted, or the
     * actual name if it has been changed.
     */
    synchronized private void notifyNickName(String sessionID, String nickname) {
        try {
            tracer.debug("New player '" + nickname + "' message from ip " + getIp(sessionID, sessions) );
            String userAgent = ( usrAgnts.get(sessionID) != null ? usrAgnts.get(sessionID) : "undetected");
            room.addPlayer(nickname, userAgent);
            nicksns.put(sessionID, nickname);
        } catch (RoomException e) {
            logger.warn("Unable to add player: " + e.getMessage());
        } catch (Exception e) {
            logger.warn("Message not well formatted, skipped.", e);
        }
    }

    synchronized private void notifyChatMessage(String sessionID, String message) {
        String playerName = nicksns.get(sessionID);
        if (playerName == null) {
            // the message might have come too early; we cannot fulfill it
            tracer.warn("Received chat message from incomplete player (ip: " + getIp(sessionID, sessions) + ").");
            return;
        }
        tracer.debug("Received chat message from player '" + playerName + "' (ip: " + getIp(sessionID, sessions) + ").");
        room.updatePlayerMsg(playerName, message);
    }

    synchronized private void notifyCommand(String sessionID, String message) {
        String playerName = nicksns.get(sessionID);
        if (playerName == null) {
            // the message might have come too early; we cannot fulfill it
            tracer.warn("Received command message from incomplete player (ip: " + getIp(sessionID, sessions) + ").");
            return;
        }
        tracer.debug("Received command message from player '" + playerName + "' (ip: " + getIp(sessionID, sessions) + ").");
        room.dispatchCommand(playerName, message);
        logger.debug("Input command from user " + playerName + ": " + message);
    }

    @SuppressWarnings("rawtypes")
    private void addUserAgent(String sessionID, Map sessionInfo, Map<String, String> usrAgnts) {
        try {
            String ua = (String) sessionInfo.get(FIELD_USER_AGENT);
            if (ua == null) {
                logger.info("User Agent not present for session " + sessionID);
                return;
            }

            logger.info("User Agent: " + ua);
            Parser uaParser = new Parser();
            Client c = uaParser.parse(ua);
            String userAgent = ( c.userAgent.family.equals("Android") ?
                c.userAgent.family + " Browser on " + c.os.family :
                c.userAgent.family + " on " + c.os.family);

            usrAgnts.put(sessionID, userAgent);
            logger.info("Saved: " + userAgent + ", for " + sessionID);

        } catch (IOException ioe) {
            logger.warn("Unable to retrieve user agent for sesion '" + sessionID + "'");
        }
    }

    private String removeTypeFrom(String message) {
        String newMsg;
        try {
            newMsg = message.split("\\|")[1];
        } catch (ArrayIndexOutOfBoundsException aiobe) {
            // let's assume that the message ends with | to carry an empty value
            newMsg = "";
        }
        return newMsg;
    }

    private boolean isNaN(String str) {
        if (str.charAt(0) == '-') {
          str = str.substring(1);
        }

        int i=0;
        while (i<str.length() && Character.isDigit(str.charAt(i))) {
          i++;
        }
        return i != str.length();
    }

    private String getIp(String sessionID, Map<String,Map<String,String>> sessions) {
        String ip = "";

        Map<String,String> sessionInfo = sessions.get(sessionID);
        if (sessionInfo == null) {
             logger.warn("Unable to retrieve IP: session '" + sessionID + "' does not exist!");
        } else {
            ip =  sessionInfo.get(FIELD_REMOTE_IP);
        }
        return ip;
    }

    private String getNickNameFrom(String message) {
        final String nickname = message;
        return nickname;
    }

    /**
     * Compute a name that is not already present in the name list.
     * The returned name is based on the proposed one.
     * @param proposedName The proposed name
     * @param namesNotAllowed The set of names form with the computed name must be
     * different.
     * @return a name not preset in namesNotAllowed
     */
    private String getRecommendedName(String proposedName, Collection<String> namesNotAllowed) {
        if (proposedName.startsWith(Ball.NAME_BALL)) {
            proposedName = "NotABall";
        }

        String recommendedName;
        if (!namesNotAllowed.contains(proposedName) ){
            recommendedName = proposedName;
        } else {
            int ik = 2;
            while ( namesNotAllowed.contains(proposedName+ik)  ){
                ik++;
            }

            recommendedName = proposedName+ik;
        }
        return recommendedName;
    }

}
