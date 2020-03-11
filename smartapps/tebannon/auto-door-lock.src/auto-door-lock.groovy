/**
 *  Auto Door Lock
 *
 *  Copyright 2020 Tom Bannon
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Auto Door Lock",
    namespace: "tebannon",
    author: "Tom Bannon",
    description: "Automatically locks door after a specified elapsed time with optional door cotact sensor and motion contact sensor.",
    category: "Safety & Security",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Solution/doors.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Solution/doors@2x.png"
	/*
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
    */
    )

preferences
{

    section("Door lock:")
    {
        input "doorLock", "capability.lock", title: "Select the door lock", required: true
    }

    section("Lock after elapsed time:")
    {
        input "elapsedMinutes", "number", title: "Enter elapsed time (minutes)", description: "Minutes", range:"1..60", required: true   
	}
    
    section("Check interval (optional):")
    {
        input "checkInterval", "enum", title: "Select interval", metadata:[values:["None", "1 Minute", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]], required: false
    }

    section("Keep unlocked if door is open (optional):")
    {
        input "doorContact", "capability.contactSensor", title: "Select the door contact sensor", required: false
    }

    section("Keep unlocked if motion is detected (optional):")
    {
        input "motionDetector", "capability.motionSensor", title: "Select the motion sensor", required: false
    }

	section( "Push notification:" ) 
    {
        input "sendPushMessage", "enum", title: "Send push notification?", metadata:[values:["Yes", "No"]], required: true
    }

    section( "Text message:" ) 
    {
        input "sendText", "enum", title: "Send text message notification?", metadata:[values:["Yes", "No"]], required: true
        input "phoneNumber", "phone", title: "Enter phone number:", required: false
    }
}


def installed()
{
    initialize()
}

def updated()
{
    unsubscribe()
    unschedule()
    initialize()
}

def initialize()
{
    log.debug "Settings: ${settings}"
    
    // Used to track if lockDoor has been scheduled 
    state.lockDoorScheduled = false

	// Used to track how many times the lock state is unknown
	state.lockStateUnknownCount = 0

    // Setup event handlers
    subscribe(doorLock, "lock.unlocked", doorUnlockedHandler)
    subscribe(doorLock, "lock.locked", doorLockedHandler)
    subscribe(doorLock, "lock.unknown", lockStateUnknownHandler)
    subscribe(doorContact, "contact.closed", doorClosedHandler)
	subscribe(doorContact, "contact.open", doorOpenHandler)
    subscribe(motionDetector, "motion.inactive", motionInactiveHandler)
    subscribe(motionDetector, "motion.active", motionActiveHandler)
    subscribe(location, "mode", modeChangeHandler)
     
    // Set Check Interval
    switch (checkInterval) {
    case "1 Minute":
        runEvery1Minute(checkAllDevices)
        break
    case "10 Minutes":
        runEvery10Minutes(checkAllDevices)
        break
    case "15 Minutes":
        runEvery15Minutes(checkAllDevices)
        break
    case "30 Minutes":
        runEvery30Minutes(checkAllDevices)
        break
    case "1 Hour":
        runEvery1Hour(checkAllDevices)
        break
    case "3 Hours":
        runEvery3Hours(checkAllDevices)
        break
    default:
    	break;
	}
}

def doorUnlockedHandler(evt)
{
	log.debug "doorUnlockedHandler called: $evt"
    
    state.lockStateUnknownCount = 0
    
    if (
    	!state.lockDoorScheduled 
    	&& !(doorContact?.latestValue("contact") == "open") 
        && !(motionDetector?.latestValue("motion") == "active")
       )
    {
    	// Door was unlocked, door is closed, and no motion is detected, so schedule door to lock after elapsedMinutes
        log.debug "Schedule lockDoor for $doorLock after $elapsedMinutes..."
 	    runIn (elapsedMinutes * 60, lockDoor)
        state.lockDoorScheduled = true
    }
}

def doorLockedHandler(evt)
{
	log.debug "doorlockedHandler called: $evt"
    
    state.lockStateUnknownCount = 0
	
    If (state.lockDoorScheduled)
    {
    	// Door was locked, so unschedule pending lockDoor
    	log.debug "Unschedule lockDoor for $doorLock..."
    	unschedule (lockDoor)
        state.lockDoorScheduled = false
    }
}

def lockStateUnknownHandler(evt)
{
	log.debug "lockStateUnknownHandler called: $evt"
	
    state.lockStateUnknownCount += 1;
    
    If (state.lockStateUnknownCount <= 3)
    {
    	// Lock State unknown
        // Check if we need to schedule lockDoor
        if (
            !state.lockDoorScheduled
            && !(doorContact?.latestValue("contact") == "open")
            && !(motionDetector?.latestValue("motion") == "active")
           )
        {
            // Lock state is unkown, and door is closed, and motion is inactive, so schedule door to lock after elapsedMinutes
            log.debug "Schedule lockDoor for $doorLock after $elapsedMinutes..."
            runIn (elapsedMinutes * 60, lockDoor)
            state.lockDoorScheduled = true
        } 
    }
}

def doorClosedHandler(evt)
{	
	log.debug "doorClosed called: $evt"
	
    if (
    	!state.lockDoorScheduled
    	&& (doorLock.latestValue("lock") == "unlocked")
        && !(motionDetector?.latestValue("motion") == "active")
       )
    {
    	// Door was closed, lock is unlocked, and no motion is detected, so schedule door to lock after elapsedMinutes
        log.debug "Schedule lockDoor for $doorLock after $elapsedMinutes..."
		runIn (elapsedMinutes * 60, lockDoor)
        state.lockDoorScheduled = true
    }
}

def doorOpenHandler(evt)
{	
	log.debug "doorOpenHandler called: $evt"
    
    if (state.lockDoorScheduled)
    {
    	// Door was opened, so unschedule lockDoor
        log.debug "Unschedule lockDoor for $doorLock..."
        unschedule (lockDoor)
        state.lockDoorScheduled = false
    }
}

def motionInactiveHandler(evt)
{	
	log.debug "motionInactiveHandler called: $evt"
	
    if (
    	!state.lockDoorScheduled
    	&& (doorLock.latestValue("lock") == "unlocked")
        && !(doorContact?.latestValue("contact") == "open")
       )
    {
    	// Motion has stopped, lock is unlocked, and door is closed, so schedule door to lock after elapsedMinutes
        log.debug "Schedule lockDoor for $doorLock after $elapsedMinutes..."
 	    runIn (elapsedMinutes * 60, lockDoor)
       	state.lockDoorScheduled = true
    }
}

def motionActiveHandler(evt)
{	
	log.debug "motionActiveHandler called: $evt"
    
    if (state.lockDoorScheduled)
    {
    	// Motion is detected, so unschedule lockDoor
        log.debug "Unschedule lockDoor for $doorLock..."
        unschedule (lockDoor)
        state.lockDoorScheduled = false
    }
}

def modeChangeHandler(evt)
{
	log.debug "modeChangeHandler called: $evt"
    
    // Location mode has chenged so call checkAll after a 10 second delay
    runIn(10, checkAllDevices)
}

def checkAllDevices()
{
	log.debug "checkAll called"
    
    // Check if we need to schedule lockDoor
    if (
    	!state.lockDoorScheduled
    	&& 
        (
        		doorLock.latestValue("lock") == "unlocked"
                ||
                (
                	doorLock.latestValue("lock") == "unknown"
                    && state.lockStateUnknownCount <= 3
                )
		)
        && !(doorContact?.latestValue("contact") == "open")
        && !(motionDetector?.latestValue("motion") == "active")
       )
    {
    	// Lock is unlocked or unknown, and door is closed, and motion is inactive, so schedule door to lock after elapsedMinutes
        log.debug "Schedule lockDoor for $doorLock after $elapsedMinutes..."
 	    runIn (elapsedMinutes * 60, lockDoor)
       	state.lockDoorScheduled = true
    } 
    else if (
    		state.lockDoorScheduled
        	&&
        	(
    			(doorLock.latestValue("lock") == "locked")
        		|| (doorContact?.latestValue("contact") == "open")
        		|| (motionDetector?.latestValue("motion") == "active")    
    		)
		)
    {
    	// Door is locked, or door is open, or motion is active, so unschedule lockDoor
        log.debug "Unschedule lockDoor for $doorLock..."
        unschedule (lockDoor)
        state.lockDoorScheduled = false
    }
}

def lockDoor()
{
	state.lockDoorScheduled = false
        
    def lockMsg = "$doorLock was automatically locked after $elapsedMinutes minute(s)"
    
	if (doorLock.latestValue("lock") == "unlocked")
    {
    	log.debug "Locking $doorLock..."
    	doorLock.lock()
        
    	if (sendPushMessage != "No")
        {
	        log.debug ("Sending Push Notification...") 
        	sendPush(lockMsg)
        }

 		if ((sendText == "Yes") && (phoneNumber != "0")) 
        {
			log.debug("Sending text message...")
        	sendSms(phoneNumber, lockMsg)
        }
    }
	else if (doorLock.latestValue("lock") == "locked")
    {
        log.debug "$doorLock was already locked..."
    }
}
