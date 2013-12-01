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

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.jbox2d.dynamics.World;

public class Box2DRoom implements Room {

    // Member Fields -----------------------------------------------------------

    private static final int MAX_NUM_OF_PLAYERS = 200;

    private static final int PLAYER_SPEED = 200000;

    private static final int MAX_NOW_STATS = 1780;

    /**
     * Map of Players indexed by name.
     */
    private final ConcurrentHashMap<String, Player> players =
            new ConcurrentHashMap<String, Player>();

    private final Ball ball;

    private volatile Publisher listener;

    // Setup world
    private final World m_world;

    private final RoomEngine engine = new RoomEngine();

    private final Logger logger;


    private static final int BASE_RATE = 10;

    private double factorWorld;

    private int frameRate;

    private float timeStep; // 1.0f/25.0f;

    private static final int DEFAULT_STEPS_PER_FRAME = 1;
    private static final int DEFAULT_FRAME_RATE = 10;
    private static final int velocityIterations = 6;
    private static final int positionIterations = 2;

    private int stepsPerFrame = DEFAULT_STEPS_PER_FRAME;

    private int stepCounter = 0;

    // Counts ticks from last overall bandwidth post
    private int ticksCount = 0;
    // Counts ticks from last statistics flush post
    private int nowStats = 0;

    // Constructor -------------------------------------------------------------

    private static Box2DRoom instance = null;

    synchronized static public Box2DRoom getInstance() {
        if (instance == null) {

            instance = new Box2DRoom(new NullPublisher(), new WorldFactory().createWorld());
        }
        return instance;
    }

    private Box2DRoom(Publisher listener, World world) {
        super();
        logger = Logger.getLogger(RoomBallMetaAdapter.ROOM_DEMO_LOGGER_NAME);

        this.listener = listener;
        this.m_world = world;
        this.ball = new BallFactory().createElement(m_world);

        setFrameRate(DEFAULT_FRAME_RATE);

    }

    // Nested Class ------------------------------------------------------------

    class RoomEngine implements Runnable {

        private volatile boolean keepRunning = false;

        public void start() {
            if (keepRunning) {
                return;
            }
            keepRunning = true;
            Thread worldThread = new Thread(this);
            worldThread.start();
        }

        public void stop() {
            keepRunning = false;
        }

        @Override
        public void run () {

            while (keepRunning) {
                moveWorld();
                tickStatistics();
                try {
                    Thread.sleep(frameRate);
                } catch (InterruptedException ie) {
                    // Skip.
                }
            }
        }

    }

    // Public Methods ----------------------------------------------------------

    public void setPublisher(Publisher publisher) {
        this.listener = publisher;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
        this.factorWorld = this.frameRate / BASE_RATE;
        this.timeStep = (this.frameRate / 1000f);
    }

    public void setStepsPerFrame(int stepsPerFrame) {
        this.stepsPerFrame = stepsPerFrame;
    }


    @Override
    public void start() {
        engine.start();
    }

    @Override
    public void stop() {
        engine.stop();
    }

    @Override
    public void dispatchCommand(String name, String msg) {

        Player player = players.get(name);
        if ( player == null ) {
            logger.warn("'" + name + "' not found!");
            return;
        }

        if ( msg.equals("40") ) {
            player.addImpulse(0, PLAYER_SPEED);
        } else if ( msg.equals("38") ) {
            player.addImpulse(0, -PLAYER_SPEED);
        } else if ( msg.equals("37") ) {
            player.addImpulse(-PLAYER_SPEED, 0);
        } else if ( msg.equals("39") ) {
            player.addImpulse(PLAYER_SPEED, 0);
        } else if ( msg.equals("400") ) {
            player.addImpulse(0, -PLAYER_SPEED);
        } else if ( msg.equals("380") ) {
            player.addImpulse(0, PLAYER_SPEED);
        } else if ( msg.equals("370") ) {
            player.addImpulse(PLAYER_SPEED, 0);
        } else if ( msg.equals("390") ) {
            player.addImpulse(-PLAYER_SPEED, 0);
        } else {
            logger.debug("Unknown command received.");
        }
    }

    @Override
    synchronized public String addPlayer(String proposedName, String usrAgent)
            throws RoomException {

        if (players.size() >= MAX_NUM_OF_PLAYERS) {
            logger.warn("Room is overcrowded; subscription rejected.");
            throw new RoomException("Room is overcrowded.");
        }

        String actualName = computeName(proposedName, players.keySet());
        Player player = new PlayerFactory().createElement(m_world, actualName, usrAgent);
        players.putIfAbsent(player.getName(), player);
        listener.publishAdd(player);

        logger.info("Added player '" + actualName + "'");
        return actualName;
    }

    @Override
    synchronized public void removePlayer(String name) {

        Player player = players.remove(name);
        if (player == null) {
            logger.warn("Unable to remove '" + name + "': not present.");
            return;
        }

        player.close(m_world);
        listener.publishDelete(player);
        logger.info("Removed player '"+ name +"'");
    }

    @Override
    synchronized public void touchAllElements() {

        for (Player player  : players.values()) {
            player.setAsChanged();
            listener.publishTouch(player);
        }

        ball.setAsChanged();
        listener.publishTouch(ball);

        listener.publishEOS();
    }

    @Override
    synchronized public void updatePlayerMsg(String name, String newMsg) {

        Player player = players.get(name);
        if ( player == null ) {
            logger.warn("'" + name + "' not found!");
            return;
        }

        if ( newMsg.length() > 30 ) {
            newMsg = newMsg.substring(0,30);
        }

        logger.debug("New message for " + name + " :" + newMsg);
        player.setLastMsg(newMsg);

        listener.publishUpdate(player);
    }

    // Private Methods ---------------------------------------------------------

    private void moveWorld() {
        step();

        if (++stepCounter >= stepsPerFrame) {
            sendUpdates();
            stepCounter = 0;
        }
    }

    private void step() {

        for (Player player : players.values()) {
            player.applyImpulse();
        }

        m_world.step(timeStep, velocityIterations, positionIterations);

        for (Player player : players.values()) {
            player.synchWithWorld();
        }

        ball.synchWithWorld();
    }

    synchronized private void sendUpdates() {

        for (Player player : players.values()) {
            listener.publishUpdate(player);
        }

        listener.publishUpdate(ball);
    }

    private void tickStatistics() {
        try {
            // Post overall bandwidth utilization.
            if ( ticksCount++ > ((2000/frameRate)* factorWorld) ) {
                listener.postOverallBandwidth();
                ticksCount = 0;
                if ( nowStats++ > MAX_NOW_STATS ) {
                    listener.flushStatistics();
                    nowStats = 0;
                }
            }
        } catch (Exception e) {
            logger.warn("Unexpected error in send overall Bandwidth information.", e);
        }
    }



    /**
     * Compute a name that is not already present in the name list.
     * The returned name is based on the proposed one.
     * @param proposedName The proposed name
     * @param namesNotAllowed The set of names form with the computed name must be
     * different.
     * @return a name not preset in namesNotAllowed
     */
    private String computeName(String proposedName, Collection<String> namesNotAllowed) {
        if (proposedName.startsWith(Ball.NAME_BALL)) {
            proposedName = "NotABall";
        }

        String actualName;
        if (!namesNotAllowed.contains(proposedName) ){
            actualName = proposedName;
        } else {
            int ik = 2;
            while ( namesNotAllowed.contains(proposedName+ik)  ){
                ik++;
            }

            actualName = proposedName+ik;
        }
        return actualName;
    }

}
