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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.jbox2d.dynamics.World;

public class Box2DRoom implements Room {
    private static final int PLAYER_SPEED = 200000;
    private static final int MAX_NOW_STATS = 1780;

    // Member Fields -----------------------------------------------------------

    /**
     * Map of Player(s) indexed by name.
     * Is guarded by 'this' and every change must result in a Event to be
     * published, before an other change can occur. In other words, the order of
     * the Event(s) must be consistent with the order of the changes on 'players'.
     * For example an update followed by a delete of the same Player, must result
     * in a sequence of Event(s), where the first Event is an 'Update', followed
     * by the 'Delete'. If it was possible that the 'Delete' precedes the 'Update,
     * the 'Update' will be treated as an 'Add' and, as a result' the Player will
     * be added again.
     */
    private final Map<String, Player> players = new HashMap<String, Player>();

    /**
     * The ball used to play
     * Is guarded by 'this' and every change must result in a Event to be
     * published, before an other change can occur. In other words, the order of
     * the Event(s) must be consistent with the order of the changes on 'ball'.
     */
    private final Ball ball;

    /**
     * The interface used to publish events
     */
    private volatile Publisher publisher;

    /**
     * The physical world where the Player(s) and the Ball move.
     */
    private final World m_world;

    /**
     * The engine that actually keeps the world moving
     */
    private final RoomEngine engine = new RoomEngine();

    /**
     * The Executor used to publish the Even(s) to the DataAdapter and to the
     * LS Server
     */
    private final Executor publishExecutor;

    private final Logger logger;

    private static final int BASE_RATE = 10;

    private double factorWorld;

    private int frameRate;

    private float timeStep;

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

    static private Box2DRoom instance = null;

    synchronized static public Box2DRoom getInstance() {
        if (instance == null) {
            instance = new Box2DRoom(new NullPublisher(), new WorldFactory().createWorld());
        }
        return instance;
    }

    private Box2DRoom(Publisher publisher, World world) {
        super();
        logger = Logger.getLogger(RoomBallMetaAdapter.ROOM_DEMO_LOGGER_NAME);

        this.publisher = publisher;
        this.m_world = world;
        this.ball = new BallFactory().createElement(m_world);

        setFrameRate(DEFAULT_FRAME_RATE);

        publishExecutor = Executors.newSingleThreadExecutor();
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

    class PublishEventTask implements Runnable {

        private final Event event;

        public PublishEventTask(Event event) {
            this.event = event;
        }

        @Override
        public void run() {
            publisher.publish(event);
        }
    }

    class PublishEOSTask implements Runnable {
        @Override
        public void run() {
            publisher.publishEOS();
        }
    }

    // Public Methods ----------------------------------------------------------

    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
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
    synchronized public void dispatchCommand(String name, String msg) {

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
    synchronized public void addPlayer(String name, String usrAgent)
            throws RoomException {

        if (players.containsKey(name)) {
            throw new RoomException("Unable to add player: '"+name+"' already exists!.");
        }

        Player player = new PlayerFactory().createElement(m_world, name, usrAgent);
        players.put(player.getName(), player);

        publishAdd(player);
        logger.info("Added player '" + name + "'");
    }

    @Override
    synchronized public void removePlayer(String name) {

        Player player = players.remove(name);
        if (player == null) {
            logger.warn("Unable to remove '" + name + "': not present.");
            return;
        }

        player.close(m_world);

        publishDelete(player);
        logger.info("Removed player '"+ name +"'");
    }

    @Override
    synchronized public void touchAllElements() {

        for (Player player  : players.values()) {
            player.setAsChanged();
            publishTouch(player);
        }

        ball.setAsChanged();

        publishTouch(ball);
        publishEOS();
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

        publishUpdate(player);
    }

    // Private Methods ---------------------------------------------------------

    private void moveWorld() {
        step();

        if (++stepCounter >= stepsPerFrame) {
            sendUpdates();
            stepCounter = 0;
        }
    }

    synchronized private void step() {

        for (Player player : players.values()) {
            player.applyImpulse();
        }

        m_world.step(timeStep, velocityIterations, positionIterations);

        for (Player player : players.values()) {
            player.synchWithWorld();
        }

        ball.synchWithWorld();
    }


    private void publishAdd(Element element) {
        publish(element, EventComposer.createAddEventComposer());
    }

    private void publishDelete(Element element) {
        publish(element, EventComposer.createDeleteEventComposer());
    }

    private void publishUpdate(Element element) {
        publish(element, EventComposer.createUpdateEventComposer());
    }

    private void publishTouch(Element element) {
        publish(element, EventComposer.createTouchEventComposer());
    }

    private void publish(Element element, EventComposer eventCreator) {
        element.accept(eventCreator);
        final Event event = eventCreator.getEvent();

        publishExecutor.execute(new PublishEventTask(event));
    }

    private void publishEOS() {
        publishExecutor.execute(new PublishEOSTask());
    }

    synchronized private void sendUpdates() {

        for (Player player : players.values()) {
            publishUpdate(player);
        }

        publishUpdate(ball);
    }

    private void tickStatistics() {
        try {
            // Post overall bandwidth utilization.
            if ( ticksCount++ > ((2000/frameRate)* factorWorld) ) {
                publisher.postOverallBandwidth();
                ticksCount = 0;
                if ( nowStats++ > MAX_NOW_STATS ) {
                    publisher.flushStatistics();
                    nowStats = 0;
                }
            }
        } catch (Exception e) {
            logger.warn("Unexpected error in send overall Bandwidth information.", e);
        }
    }
}
