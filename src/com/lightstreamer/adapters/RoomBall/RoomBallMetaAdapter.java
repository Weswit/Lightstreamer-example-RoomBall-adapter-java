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
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import ua_parser.Client;
import ua_parser.Parser;

import com.lightstreamer.adapters.metadata.LiteralBasedProvider;
import com.lightstreamer.interfaces.metadata.CreditsException;
import com.lightstreamer.interfaces.metadata.NotificationException;
import com.lightstreamer.interfaces.metadata.TableInfo;

public class RoomBallMetaAdapter extends LiteralBasedProvider {

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
     * map of player's nicknames indexed by session id
     * There can be only one player per session
     */
    private final ConcurrentHashMap<String, String> nicksns =
            new ConcurrentHashMap<String, String>();

    /**
     * map of player's user agent indexed by session id
     */
    private final ConcurrentHashMap<String, String> usrAgnts =
            new ConcurrentHashMap<String, String>();


    private final static ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor();

    private final static ConcurrentHashMap<String, PollsBandwidth> checkBandWidths =
            new ConcurrentHashMap<String, PollsBandwidth>();

    private int jmxPort = 9999;

    private final Room room;

    // Constructor -------------------------------------------------------------

    public RoomBallMetaAdapter() {
        room = Box2DRoom.getInstance();
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

    public static void killBandChecker(String itemName) {
        PollsBandwidth p = checkBandWidths.get(itemName);
        if ( p != null ) {
            p.getTask().cancel(true);
            checkBandWidths.remove(itemName);
        }
    }

    public static double getTotalBandwidthOut() {
        double sum = 0.0;
        Enumeration<PollsBandwidth> e = checkBandWidths.elements();
        PollsBandwidth p;
        while ( e.hasMoreElements() ) {
            p = e.nextElement();
            sum += p.getBandwidth();
        }

        return sum;
    }

    @Override
    public int getDistinctSnapshotLength(String item) {
        return 0;
    }

    @Override
    public void notifySessionClose(String sessionID) throws NotificationException {
        sessions.remove(sessionID);

        String deadmanwalking = nicksns.get(sessionID);
        if (deadmanwalking != null) {
            try {
                room.removePlayer(deadmanwalking);
            } catch (Exception e) {
                logger.warn(e);
            }
        }

        nicksns.remove(sessionID);
    }

    @SuppressWarnings({"rawtypes", "unchecked" })
    @Override
    public void notifyNewSession(String user, String sessionID, Map sessionInfo) throws CreditsException, NotificationException {

        manageUserAgent(sessionID, sessionInfo);

        sessions.put(sessionID, sessionInfo);
    }

    @Override
    public void notifyTablesClose(java.lang.String sessionID, TableInfo[] tables) {
    }

    @Override
    public void notifyNewTables(String user, String sessionID, TableInfo[] tables) throws CreditsException {

        if ( tables[0].getId().startsWith(ITEM_NAME_PREFIX_BAND) ) {
            String usr = tables[0].getId().substring(ITEM_NAME_PREFIX_BAND.length());
            PollsBandwidth p = new PollsBandwidth(sessionID, usr, this.jmxPort);

            ScheduledFuture<?> tsk = executor.scheduleAtFixedRate(p,10,2000,TimeUnit.MILLISECONDS);
            p.setTask(tsk);
            checkBandWidths.put(tables[0].getId(), p);
        }
    }

    @Override
    public void notifyUserMessage(String user, String sessionID, String message)
            throws CreditsException {
        try {
            if (message == null) {
                return ;
            }

            tracer.debug("Received message '"+message+"' from player '" + user + "', sessionId '"+sessionID+"'.");

            if (message.startsWith("n|") ) {
                String res = notifyNickName(sessionID, removeTypeFrom(message));

                if (res.equalsIgnoreCase("nan")) {
                    throw new CreditsException(-6, "I'm not a number! I'm a free man!");
                } else if (res.equalsIgnoreCase("ball")) {
                    throw new CreditsException(-1, "Srsly? A ball?");
                } else if (res.equalsIgnoreCase("overcrowed")) {
                    throw new CreditsException(-2700, "Too many users. Please try again.");
                } else if (res.equalsIgnoreCase(ERROR)) {
                    throw new CreditsException(-2710, "Error logging in.");
                } else if (!res.equalsIgnoreCase("")) {
                    throw new CreditsException(-2720, res);
                }
            } else if ( message.startsWith("m|") ) {
                notifyChatMessage(sessionID, removeTypeFrom(message));
            } else {
                notifyCommand(sessionID, message);
            }
        } catch (CreditsException e) {
            throw e;
        } catch (Exception e) {
            // Skip, message not well formatted
            logger.warn("Unexpected error handling message from user '"+user+"', session '"+sessionID+"'.", e);
        }
    }

    /**
     *
     * @param sessionID
     * @param message
     * @return An empty string if the nickmame has been accepted, or the
     * actual name if it has been changed.
     */
    protected String notifyNickName(String sessionID, String message) {

        try {

            final String nickname = getNickNameFrom(message);
            if (nickname.equals(ERROR)) {
                return ERROR;
            }

            if (!isNaN(nickname)) {
                return "nan";
            }

            if (nickname.indexOf(NAME_PREFIX_BALL) == 0) {
                return "ball";
            }

            String ip = getIp(sessionID);
            if (ip.isEmpty()) {
                logger.warn("New player '" + nickname + "' message received from non-existent session '" + sessionID + "'.");
            } else {
                tracer.debug("New player '" + nickname + "' message from ip " + ip );
            }

            if (nicksns.containsKey(sessionID)) {
                // duplicated message; it should be avoided when possible
                String actualNickName = nicksns.get(sessionID);

                if ( actualNickName.equalsIgnoreCase(nickname)) {
                    actualNickName = "";
                }
                return actualNickName;
                // returning the nickname instead of the empty string brings
                // back to the case where the name has been changed, causing
                // the client to receive a non blocking error.
            }

            String userAgent = ( usrAgnts.get(sessionID) != null ? usrAgnts.get(sessionID) : "undetected");


            String actualNickName = room.addPlayer(nickname, userAgent);

            nicksns.put(sessionID, actualNickName);

            if ( actualNickName.equalsIgnoreCase(nickname)) {
                actualNickName = "";
            }
            return actualNickName;
        } catch (RoomException e) {
            logger.warn("Unable to add player: " + e.getMessage());
            return "overcrowed";
        } catch (Exception e) {
            logger.warn("Message not well formatted, skipped.", e);
            return ERROR;
        }
    }

    protected void notifyChatMessage(String sessionID, String message) throws Exception {
        String player = getPlayerName(sessionID);
        if (player == null) {
            return;
        }

        room.updatePlayerMsg(player, message);
    }

    protected void notifyCommand(String sessionID, String message) throws Exception {
        String player = getPlayerName(sessionID);
        if (player == null) {
            return;
        }

        room.dispatchCommand(player, message);
        logger.debug("Input command from user " + player + ": " + message);
    }

    // Private Methods ---------------------------------------------------------

    @SuppressWarnings("rawtypes")
    private void manageUserAgent(String sessionID, Map sessionInfo) {
        try {
            String ua = (String) sessionInfo.get(FIELD_USER_AGENT);

            logger.info("Usr Agent: " + ua);

            if (ua != null) {
                Parser uaParser = new Parser();
                Client c = uaParser.parse(ua);
                if ( c.userAgent.family.equals("Android") ) {
                    usrAgnts.put(sessionID, c.userAgent.family + " Browser on " + c.os.family);
                } else {
                    usrAgnts.put(sessionID, c.userAgent.family + " on " + c.os.family);
                }
                logger.info("Saved: " + c.userAgent.family + " on " + c.os.family + ", for " + sessionID);
            }

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

    private String getPlayerName(String sessionID) {
        String playerName = nicksns.get(sessionID);
        if (playerName == null) {
            // the message might have come too early; we cannot fulfill it
            tracer.warn("Received message from incomplete player (ip: " + getIp(sessionID) + ").");
            return null;
        }

        tracer.debug("Received message from player '" + playerName + "' (ip: " + getIp(sessionID) + ").");
        return playerName;
    }

    private String getIp(String sessionID) {
        String ip = "";

        Map<String,String> sessionInfo = sessions.get(sessionID);
        if (sessionInfo == null) {
             logger.warn("Message received from non-existent session '" + sessionID + "'");
        } else {
            ip =  sessionInfo.get(FIELD_REMOTE_IP);
        }
        return ip;
    }

    private String getNickNameFrom(String message) {
        final String nickname = message;
        return nickname;
    }

}
