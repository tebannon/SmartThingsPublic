/**
 *  Auto Door Lock
 *
 *
 */
definition(
    name: "Auto Door Lock",
    namespace: "tebannon@att.net",
    author: "Tom Bannon",
    description: "Automatically locks door after a specified elapsed time with optional door cotact sensor and motion contact sensor.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences
{
    section("Door lock:")
    {
        input "doorLock", "capability.lock", title: "Select the door lock", required: true
    }
    
    section("Lock after elapsed time:")
    {
        input "elapsedMinutes", "number", title: "Enter elapsed time (minutes)", description: "Minutes", required: true
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
    
    // Setup event handlers
    subscribe(doorLock, "lock.unlocked", doorUnlockedHandler)
    subscribe(doorLock, "lock.locked", doorLockedHandler)    
    subscribe(doorContact, "contact.closed", doorClosedHandler)
	subscribe(doorContact, "contact.open", doorOpenHandler)
    subscribe(motionDetector, "motion.inactive", motionInactiveHandler)
    subscribe(motionDetector, "motion.active", motionActiveHandler)
}

def doorUnlockedHandler(evt)
{
	log.debug "doorUnlockedHandler called: $evt"
	
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

def doorlockedHandler(evt)
{
	log.debug "doorlockedHandler called: $evt"
	
    If (state.lockDoorScheduled)
    {
    	// Door was locked, so unschedule pending lockDoor
    	log.debug "Unschedule lockDoor for $doorLock..."
    	unschedule (lockDoor)
        state.lockDoorScheduled = false
    }
}

def doorClosed(evt)
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

def lockDoor()
{
	state.lockDoorScheduled = false
        
    def lockMsg = "$doorLock was automatically locked after $elapsedMinutes minute(s)"
    
	if (doorLock.latestValue("lock") == "unlocked")
    {
    	log.debug "Locking $doorLock..."
    	doorLock.lock()
        
        log.debug ("Sending Push Notification...") 
    	if (sendPushMessage != "No")
        {
        	sendPush(lockMsg)
        }

		log.debug("Sending text message...")
 		if ((sendText == "Yes") && (phoneNumber != "0")) 
        {
        	sendSms(phoneNumber, lockMsg)
        }
    }
	else if (doorLock.latestValue("lock") == "locked")
    {
        log.debug "$doorLock was already locked..."
    }
}
