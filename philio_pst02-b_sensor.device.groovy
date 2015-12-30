/*
 * PST02-B PIR/Motion 3 in 1 Sensor - (1)PIR, (2)temperature, (3)illumination
*/
 
 metadata {


	definition (name: "My Philio PST02-B", namespace: "jscgs350", author: "SmartThings") {
        capability "Motion Sensor"
		capability "Temperature Measurement"
		capability "Configuration"
		capability "Illuminance Measurement"
		capability "Sensor"
		capability "Battery"

		fingerprint deviceId: "0x2001", inClusters: "0x30,0x31,0x80,0x84,0x70,0x85,0x72,0x86"
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
			}
		}
		valueTile("temperature", "device.temperature", inactiveLabel: false, width: 3, height: 2) {
			state "temperature", label:'${currentValue}°',
			backgroundColors:[
				[value: 31, color: "#153591"],
				[value: 44, color: "#1e9cbb"],
				[value: 59, color: "#90d2a7"],
				[value: 74, color: "#44b621"],
				[value: 84, color: "#f1d801"],
				[value: 95, color: "#d04e00"],
				[value: 96, color: "#bc2323"]
			]
		}
		valueTile("illuminance", "device.illuminance", inactiveLabel: false, width: 3, height: 2) {
			state "luminosity", label:'${currentValue} ${unit}', unit:"lux"
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 3, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}
		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 3, height: 2) {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		main(["motion", "temperature", "illuminance"])
		details(["motion", "temperature", "illuminance", "battery", "configure"])
	}
}

preferences {
}

def installed() {
	log.debug "PST02-B: Installed with settings: ${settings}"
	configure()
}

def updated() {
	log.debug "PST02-B: Updated with settings: ${settings}"
    configure()

}

def parse(Map evt){
	log.debug "Parse(Map) called with map ${evt}"
    def result = [];
    if (evt)
    	result << evt;
    log.debug "Parse(Map) returned ${result}"
    return result
}

// Parse incoming device messages to generate events
def parse(String description)
{
    log.debug "Parse called with ${description}"
	def result = []
	def cmd = zwave.parse(description, [0x20: 1, 0x31: 2, 0x30: 2, 0x80: 1, 0x84: 2, 0x85: 2])
	if (cmd) {
		if( cmd.CMD == "8407" ) { result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format()) }
		def evt = zwaveEvent(cmd)
        result << createEvent(evt)
	}
	log.debug "Parse returned ${result}"
	return result
}

// Event Generation
def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	[descriptionText: "${device.displayName} woke up", isStateChange: false]
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd)
{
	def map = [:]
	switch (cmd.sensorType) {
		case 1:
			// temperature
			def cmdScale = cmd.scale == 1 ? "F" : "C"
			map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
			map.unit = getTemperatureScale()
			map.name = "temperature"
			break;
		case 3:
			// luminance
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "lux"
			map.name = "illuminance"
			break;
	}
	map
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
	map.name = "battery"
	map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
	map.unit = "%"
	map.displayed = false
	map
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
    log.debug "PSM02: SensorBinaryReport ${cmd.toString()}}"
    def map = [:]
    switch (cmd.sensorType) {
        case 12: // motion sensor
            map.name = "motion"
            if (cmd.sensorValue) {
                map.value = "active"
                map.descriptionText = "$device.displayName detected motion"
            } else {
                map.value = "inactive"
                map.descriptionText = "$device.displayName motion has stopped"
            }
            map.isStateChange = true
            break;
    }
    map
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "PST02-B: Catchall reached for cmd: ${cmd.toString()}}"
	[:]
}

def configure() {
    log.debug "PST02-B: configure() called"
    
	delayBetween([      
        // Set PIR Re-Detect Interval Time
        zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 3).format(),
        
		// Auto report Battery time 1-127
		zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: 1).format(),

		// Auto report Illumination time 1-127
		zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, scaledConfigurationValue: 1).format(),
        
        // Auto report Temperature time 1-127
        zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: 1).format(),
        
        // Wake up every hour
        zwave.wakeUpV1.wakeUpIntervalSet(seconds: 1 * 3600, nodeid:zwaveHubNodeId).format(),
        
        // Get PIR sensitivity
        zwave.configurationV1.configurationGet(parameterNumber: 3).format()
    ])
}
