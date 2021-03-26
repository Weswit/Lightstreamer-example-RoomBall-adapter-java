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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.concurrent.ScheduledFuture;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PollsBandwidth implements Runnable {

    private static final String FIELD_CURRENT_BANDWIDTH = "currentBandwidth";

    private MBeanServer server;
    private ObjectName sessionMBeanName = null;
    private String user = "";
    private ScheduledFuture<?> task = null;

    private Publisher publisher;

    /**
     * should be supplied by logback configuration.
     */
    private Logger logger = null;


    public ScheduledFuture<?> getTask() {
        return task;
    }

    public void setTask(ScheduledFuture<?> task) {
        this.task = task;
    }

    public PollsBandwidth(String sessionId, String user, int port, Publisher publisher) {
        try {
            /*
            JMXServiceURL url = new JMXServiceURL("service:jmx:jmxmp://localhost:"+port);
            JMXConnector jmxc = JMXConnectorFactory.connect(url, null);

            this.mbsc = jmxc.getMBeanServerConnection();
            this.mbeanName =  new ObjectName("com.lightstreamer:type=Session,sessionId="+sessionId);
            */

            server = null;
            ObjectName mbeanName = new ObjectName("com.lightstreamer", "type", "Server");
            if (mbeanName != null) {
                ArrayList<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
                MBeanServer found = null;
                for (int i = 0; i < servers.size(); i++) {
                    found = (MBeanServer) servers.get(i);
                    if (found.isRegistered(mbeanName)) {
                        break;
                    } else {
                        found = null;
                    }
                }
                server = found;
            }

            Hashtable<String, String> props = new Hashtable<String, String>();
            props.put("type", "Session");
            props.put("sessionId", sessionId);

            sessionMBeanName = new ObjectName("com.lightstreamer", props);

            this.user = user;
            this.publisher = publisher;
            logger = LogManager.getLogger(RoomBallAdapter.ROOM_DEMO_LOGGER_NAME);

        } catch (Exception e) {
            // Skip. Eventually log here ...
        }
    }

    public void forceMeOut() {
        try {
            server.invoke(sessionMBeanName, "destroySession", null, null);
        } catch (Exception e) {
            // it is still possible that the session has just ended
            return;
        }
    }

    public double getBandwidth() {
        try {
            Double d = (Double)server.getAttribute(sessionMBeanName, "CurrentBandwidthKbps");
            return d;
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public void run () {
        try {
            Double bandwidth = (Double)server.getAttribute(sessionMBeanName, "CurrentBandwidthKbps");

            if ( logger != null && logger.isDebugEnabled()) {
                logger.debug("Update current bandwidth for user " + user + ": " + bandwidth);
            }

            final HashMap<String, String> update = new HashMap<String, String>();
            update.put(FIELD_CURRENT_BANDWIDTH, roundToSend(bandwidth, 2));

            publisher.postBandwith(this.user, update);
        } catch (Exception e) {
            // send update ERR.
            // this.listener.postBandwith(BAND_PREFIX+this.user, new Double(0));
        }
    }

    private String roundToSend(double value, int pre) {
        String tmp = ""+value;

        int cut = tmp.length() - tmp.indexOf(".") - 1;
        if (pre < cut  ) {
            tmp = tmp.substring(0, tmp.length() - (cut - pre));
        }

        return tmp;
    }

}
