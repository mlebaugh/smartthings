/**
 *  Enhanced GE/Jasco Dimmer Switch
 *	Author: Ben W. (@desertBlade)
 *
 * Based off of the Dimmer Switch under Templates in the IDE 
 * Copyright (C) Ben W.
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

metadata {
	definition (name: "GE Toggle Dimmer Switch 12729", namespace: "mlebaugh", author: "Matt LeBaugh") {
        capability "Actuator"
 		capability "Switch"
        capability "Switch Level"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Health Check"
		capability "Light"

        fingerprint mfr:"0063", prod:"4944", model: "3033", deviceJoinName: "GE Toggle Dimmer Switch"

	}

	preferences {
       //dimmersettings
            input title: "", description: "Dimmer Ramp rate settings\nDefaults: Step Percent: 1, Duration: 3", displayDuringSetup: false, type: "paragraph", element: "paragraph"
            input ( "stepSize", "number", title: "zWave Size of Steps in Percent",
              range: "1..99", required: false)
       		input ( "stepDuration", "number", title: "zWave Steps Intervals each 10 ms",
              range: "1..255", required: false)
       		input ( "manualStepSize", "number", title: "Manual Size of Steps in Percent",
              range: "1..99", required: false)
       		input ( "manualStepDuration", "number", title: "Manual Steps Intervals Each 10 ms",
              range: "1..255", required: false)
            //
    }

	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"
		status "09%": "command: 2003, payload: 09"
		status "10%": "command: 2003, payload: 0A"
		status "33%": "command: 2003, payload: 21"
		status "66%": "command: 2003, payload: 42"
		status "99%": "command: 2003, payload: 63"

		// reply messages
		reply "2001FF,delay 5000,2602": "command: 2603, payload: FF"
		reply "200100,delay 5000,2602": "command: 2603, payload: 00"
		reply "200119,delay 5000,2602": "command: 2603, payload: 19"
		reply "200132,delay 5000,2602": "command: 2603, payload: 32"
		reply "20014B,delay 5000,2602": "command: 2603, payload: 4B"
		reply "200163,delay 5000,2602": "command: 2603, payload: 63"
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
		}


		standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main(["switch"])
		details(["switch", "refresh"])

	}
}

def parse(String description) {
	def result = null
	if (description != "updated") {
		log.debug "parse() >> zwave.parse($description)"
		def cmd = zwave.parse(description, [0x20: 1, 0x26: 1, 0x70: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	if (result?.name == 'hail' && hubFirmwareLessThan("000.011.00602")) {
		result = [result, response(zwave.basicV1.basicGet())]
		log.debug "Was hailed: requesting state update"
	} else {
		log.debug "Parse returned ${result?.descriptionText}"
	}
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	dimmerEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	dimmerEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
    dimmerEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv1.SwitchMultilevelSet cmd) {
	dimmerEvents(cmd)
}

private dimmerEvents(physicalgraph.zwave.Command cmd) {
	def value = (cmd.value ? "on" : "off")
	def result = [createEvent(name: "switch", value: value)]
	if (cmd.value && cmd.value <= 100) {
		result << createEvent(name: "level", value: cmd.value, unit: "%")
	}
	return result
}


def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} sent ${cmd}"
    def config = cmd.scaledConfigurationValue
    def result = []   
    if (cmd.parameterNumber == 7) {
    	result << createEvent([name:"StepSize", value: config, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 8) {
    	result << createEvent([name:"StepDuration", value: config, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 9) {
    	result << createEvent([name:"ManualStepSize", value: config, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 10) {
    	result << createEvent([name:"ManualStepDuration", value: config, displayed:true, isStateChange:true])
    }
    //
   return result
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	createEvent([name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false])
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def on() {
	delayBetween([
			zwave.basicV1.basicSet(value: 0xFF).format(),
			zwave.switchMultilevelV1.switchMultilevelGet().format()
	],500)
       
}

def off() {
	delayBetween([
			zwave.basicV1.basicSet(value: 0x00).format(),
			zwave.switchMultilevelV1.switchMultilevelGet().format()
	],500)
}


def setLevel(value) {
	log.debug "setLevel >> value: $value"
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
	if (level > 0) {
		sendEvent(name: "switch", value: "on")
	} else {
		sendEvent(name: "switch", value: "off")
	}
	sendEvent(name: "level", value: level, unit: "%")
	delayBetween ([zwave.basicV1.basicSet(value: level).format(), zwave.switchMultilevelV1.switchMultilevelGet().format()], 500)
}

def setLevel(value, duration) {
	log.debug "setLevel >> value: $value, duration: $duration"
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
	def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
	def getStatusDelay = duration < 128 ? (duration*1000)+2000 : (Math.round(duration / 60)*60*1000)+2000
	delayBetween ([zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration).format(),
				   zwave.switchMultilevelV1.switchMultilevelGet().format()], getStatusDelay)
}

def poll() {
	zwave.switchMultilevelV1.switchMultilevelGet().format()
}

def ping() {
		refresh()
}

def refresh() {
	log.debug "refresh() is called"
    delayBetween([
        zwave.switchMultilevelV1.switchMultilevelGet().format()
	],100)
}
def installed() {
	// Device-Watch simply pings if no device events received for 32min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def updated() {
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    if (state.lastUpdated && now() <= state.lastUpdated + 3000) return
    state.lastUpdated = now()

	def stepSize = Math.max(Math.min(stepSize, 99), 1)
    def stepDuration = Math.max(Math.min(stepDuration, 255), 1)
    def manualStepSize = Math.max(Math.min(manualStepSize, 99), 1)
    def manualStepDuration = Math.max(Math.min(manualStepDuration, 255), 1)
	
     def cmds = []
        if (settings.stepSize) {cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.stepSize.toInteger()], parameterNumber: 7, size: 1)}    
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 7)
        if (settings.stepDuration) {cmds << zwave.configurationV1.configurationSet(configurationValue: [0,settings.stepDuration.toInteger()], parameterNumber: 8, size: 2)}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 8)
        if (settings.manualStepSize) {cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.manualStepSize.toInteger()], parameterNumber: 9, size: 1)}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 9)
        if (settings.manualStepDuration) {cmds << zwave.configurationV1.configurationSet(configurationValue: [0,settings.manualStepDuration.toInteger()], parameterNumber: 10, size: 2)}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 10) 

    sendHubCommand(cmds.collect{ new physicalgraph.device.HubAction(it.format()) }, 500)
}