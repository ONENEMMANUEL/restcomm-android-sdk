/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package org.restcomm.android.sdk.util;

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDeviceListener;
import org.squirrelframework.foundation.fsm.annotation.StateMachineParameters;
import org.squirrelframework.foundation.fsm.impl.AbstractUntypedStateMachine;

/**
 *
 * FSM class to synchronize parallel registration of signaling and push notifications facilities which run in parallel so that we can present a single success/failure point
 * The way this works is that it doesn't matter which finishes first; application is notified only after both signaling and push registrations have finished processing.
 * Notice that finished might mean either successful registration, failure and timeout. Also, specifically for push registration, there might be no registration
 * happening at all if the user has disabled push.
 *
 * The FSM is used in 2 RCDevice actions:
 * - RCDevice.initialize()
 * - RCDevice.reconfigure()
 *
 * The FSM events and states are shared in those actions to make the whole thing simpler. This is possible because initialize() and reconfigure are independent and never intertwine.
 * However, the FSM methods are separate between initialize() and reconfigure() to make things clearer
 *
 * Once FSM finishes, RCDevice (i.e. the listener) is notified via listener callbacks
 *
 * Notice that we are using hekailiang's Squirrel FSM
 *
 *  TODO:
 * - See if we can improve the savedContext
 * - Fix exception 'java.lang.NoClassDefFoundError: Failed resolution of: Ljava/beans/Introspector' on FSM builder creation
 */
@StateMachineParameters(stateType=RegistrationFsm.FSMState.class, eventType=RegistrationFsm.FSMEvent.class, contextType=RegistrationFsmContext.class)
public class RegistrationFsm extends AbstractUntypedStateMachine {

    // Events to notify listener that FSM is done
    public interface RCDeviceFSMListener {
        // RCDevice.initialize() processing is finished
        void onDeviceFSMInitializeDone(RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);
        // RCDevice.reconfigure() processing is finished
        void onDeviceFSMReconfigureDone(RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);
    }

    private static final String TAG = "RegistrationFsm";

    // Define FSM events, notice that each of those events are valid both when initializing RCDevice as well as when reconfiguring it
    public enum FSMEvent {
        signalingRegistrationEvent,  // signaling facilities registration finished
        pushRegistrationEvent,  // push registered finished
        pushRegistrationNotNeededEvent,  // push registration isn't needed (it's already up to date)
        resetStateMachine,  // reset state machine to original state
    }

    // Define FSM states
    public enum FSMState {
        initialState,
        signalingReadyState,
        pushReadyState,
        finishedState,
    }

    RegistrationFsm(RCDeviceFSMListener listener)
    {
        this.listener = listener;
    }

    // keep internal context as we need to keep data around between state changes
    private RegistrationFsmContext savedContext;
    private RCDeviceFSMListener listener;

    // Methods relevant to RCDevice.initialize()
    protected void toPushInitializationReady(FSMState from, FSMState to, FSMEvent event, RegistrationFsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");

        if (from == FSMState.initialState) {
            // we haven't finished signaling registration; let's keep the state around so that we can use it when signaling is finished
            savedContext = context;
        } else if (from == FSMState.signalingReadyState) {
            // we are already registered signaling-wise, which means we a can notify app and then we 're done
            if (savedContext.status == RCClient.ErrorCodes.SUCCESS) {
                listener.onDeviceFSMInitializeDone(context.connectivityStatus, context.status, RCClient.errorText(context.status));
            } else {
                // we have an error/warning previously stored, so we need to convey it to the user
                listener.onDeviceFSMInitializeDone(savedContext.connectivityStatus, savedContext.status, savedContext.text);
            }

            // reset FSM as this was the last state, so that it can be reused
            fire(FSMEvent.resetStateMachine);
        } else {
            // should never happen; let's add a Runtime Exception for as long we 're testing this to fix any isseues
            throw new RuntimeException("RCDevice FSM invalid state: " + to);
        }
    }

    protected void toSignalingInitializationReady(FSMState from, FSMState to, FSMEvent event, RegistrationFsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");

        if (from == FSMState.initialState) {
            // we haven't finished signaling registration; let's keep the state around so that we can use it when signaling is finished
            savedContext = context;
        } else if (from == FSMState.pushReadyState) {
            // we are already registered push-wise, which means we a can notify app and then we 're done
            if (savedContext.status == RCClient.ErrorCodes.SUCCESS) {
                listener.onDeviceFSMInitializeDone(context.connectivityStatus, context.status, RCClient.errorText(context.status));
            } else {
                // we have an error/warning previously stored, so we need to convey it to the user
                listener.onDeviceFSMInitializeDone(savedContext.connectivityStatus, savedContext.status, savedContext.text);
            }

            // reset FSM as this was the last state, so that it can be reused
            fire(FSMEvent.resetStateMachine);
        } else {
            // should never happen; let's add a Runtime Exception for as long we 're testing this to fix any isseues
            throw new RuntimeException("RCDevice FSM invalid state: " + to);
        }
    }

    // Methods relevant to RCDevice.reconfigure()
    protected void toPushReconfigurationReady(FSMState from, FSMState to, FSMEvent event, RegistrationFsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");

        if (from == FSMState.initialState) {
            // we haven't finished signaling registration; let's keep the state around so that we can use it when signaling is finished
            savedContext = context;
        } else if (from == FSMState.signalingReadyState) {
            // we are already registered signaling-wise, which means we a can notify app and then we 're done
            if (savedContext.status == RCClient.ErrorCodes.SUCCESS) {
                listener.onDeviceFSMReconfigureDone(context.connectivityStatus, context.status, RCClient.errorText(context.status));
            } else {
                // we have an error/warning previously stored, so we need to convey it to the user
                listener.onDeviceFSMReconfigureDone(savedContext.connectivityStatus, savedContext.status, savedContext.text);
            }

            // reset FSM as this was the last state, so that it can be reused
            fire(FSMEvent.resetStateMachine);
        } else {
            // should never happen; let's add a Runtime Exception for as long we 're testing this to fix any isseues
            throw new RuntimeException("RCDevice FSM invalid state: " + to);
        }
    }

    protected void toSignalingReconfigurationReady(FSMState from, FSMState to, FSMEvent event, RegistrationFsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");

        if (from == FSMState.initialState) {
            // we haven't finished signaling registration; let's keep the state around so that we can use it when signaling is finished
            savedContext = context;
        } else if (from == FSMState.pushReadyState) {
            // we are already registered push-wise, which means we a can notify app and then we 're done
            if (savedContext.status == RCClient.ErrorCodes.SUCCESS) {
                listener.onDeviceFSMReconfigureDone(context.connectivityStatus, context.status, RCClient.errorText(context.status));
            } else {
                // we have an error/warning previously stored, so we need to convey it to the user
                listener.onDeviceFSMReconfigureDone(savedContext.connectivityStatus, savedContext.status, savedContext.text);
            }

            // reset FSM as this was the last state, so that it can be reused
            fire(FSMEvent.resetStateMachine);
        } else {
            // should never happen; let's add a Runtime Exception for as long we 're testing this to fix any isseues
            throw new RuntimeException("RCDevice FSM invalid state: " + to);
        }
    }

    protected void toInitialState(FSMState from, FSMState to, FSMEvent event, RegistrationFsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");
        savedContext = null;
    }
}