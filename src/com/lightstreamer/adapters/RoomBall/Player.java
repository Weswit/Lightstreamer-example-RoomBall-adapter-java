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

import org.jbox2d.dynamics.Body;

public class Player extends AbstractElement {

    public static final String USERDATA_PLAYER = "PLAYER";

    // Member Variables --------------------------------------------------------

    private Impulse impulse = Impulse.NULL;

    private String lastMsg = "";

    private String usrAgent = "";

    private boolean lastMsgChanged = false;

    private boolean usrAgentChanged = false;


    // Constructor -------------------------------------------------------------

    public Player(String name, String usrAgent, Body body) {
        super(name, body);
        setUsrAgent(usrAgent);
    }

    // Public Method -----------------------------------------------------------

    @Override
    public boolean isChanged() {
        return (super.isChanged() || lastMsgIsChanged() || usrAgentIsChanged()) ;
    }

    @Override
    public void setAsChanged() {
        super.setAsChanged();
        this.lastMsgChanged = true;
        this.usrAgentChanged = true;
    }

    @Override
    public void setAsNotChanged() {
        super.setAsNotChanged();
        this.lastMsgChanged = false;
        this.usrAgentChanged = false;
    }

    @Override
    public void accept(ElementVisitor visitor) {
        visitor.visit(this);
    }

    public String getUsrAgent() {
        return usrAgent;
    }

    final public void  setUsrAgent(String usrAgent) {
        this.usrAgent = usrAgent;
        usrAgentChanged = true;
    }

    public String getLastMsg() {
        return lastMsg;
    }

    public void setLastMsg(String lastMsg) {
        this.lastMsg = lastMsg;
        lastMsgChanged = true;
    }

    public void applyImpulse() {
        applyImpulse(impulse);
    }

    public void addImpulse(int intensityX, int intensityY) {
        impulse = impulse.sum(new Impulse(intensityX, intensityY));
    }

    // Protected Methods -------------------------------------------------------

    protected boolean lastMsgIsChanged() {
        return this.lastMsgChanged;
    }

    protected boolean usrAgentIsChanged() {
        return this.usrAgentChanged;
    }

    // Private Methods ---------------------------------------------------------

}
