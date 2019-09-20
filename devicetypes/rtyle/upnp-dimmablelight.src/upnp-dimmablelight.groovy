// vim: ts=4:sw=4
/**
 *	UPnP DimmableLight
 *
 *	Copyright 2019 Ross Tyler
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
**/
metadata {
	definition (name: "UPnP DimmableLight", namespace: "rtyle", author: "Ross Tyler") {
		capability 'Actuator'	// we have commands
		capability 'Sensor'		// we have attributes
		capability 'Refresh'
		capability 'Switch'
		capability 'SwitchLevel'
	}
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#FFFFFF", nextState:"turningOn", defaultState: true
				attributeState "turningOn", label:'Turning On', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOn"
				attributeState "turningOff", label:'Turning Off', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#FFFFFF", nextState:"turningOff"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action: "setLevel"
			}
			tileAttribute ("brightnessLabel", key: "SECONDARY_CONTROL") {
				attributeState "Brightness", label: '${name}', defaultState: true
			}
		}
		standardTile("explicitOn", "device.switch", width: 2, height: 2, decoration: "flat") {
			state "default", label: "On", action: "switch.on", icon: "st.Home.home30", backgroundColor: "#ffffff"
		}
		standardTile("explicitOff", "device.switch", width: 2, height: 2, decoration: "flat") {
			state "default", label: "Off", action: "switch.off", icon: "st.Home.home30", backgroundColor: "#ffffff"
		}
		controlTile("levelSlider", "device.level", "slider", width: 2, height: 2, inactiveLabel: false, range: "(1..100)") {
			state "physicalLevel", action: "switch level.setLevel"
		}
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		main(["switch"])
		details(["switch", "explicitOn", "explicitOff", "levelSlider", 'refresh'])
	}
}

private void control(String service, String action, Map args = null) {
	log.debug "control: $service, $action, $args"
	String path = getDataValue "controlPath$service"
	String soapArgs = ''
	args?.each {name, value ->
		soapArgs += "<$name>$value</$name>"
	}
	String body = """\
<?xml version='1.0'?>
	<s:Envelope s:encodingStyle='http://schemas.xmlsoap.org/soap/encoding/' xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'>
		<s:Body>
			<u:$action xmlns:u='urn:schemas-upnp-org:service:$service:1'>
				$soapArgs
			</u:$action>
		</s:Body>
</s:Envelope>"""
	sendHubCommand new physicalgraph.device.HubAction([
			method	: 'POST',
			path	: path,
			headers	: [
				Host			: host,
				SOAPAction		: "'urn:schemas-upnp-org:service:$service:1#$action'",
				'Content-Length': "${body.length()}",
			],
			body 	: body
		],
		device.deviceNetworkId,
		[callback: "controlResponse$service$action"]
	)
}

private Map controlResponse(String service, String action, physicalgraph.device.HubResponse hubResponse) {
	def message = parseLanMessage hubResponse.description
	log.debug "controlResponse: $service, $action, $message.headers"
	String response = message.header.split('\r\n')[0]
	// HTTP/1.1 <statusCode> <reason>
	def part = response.split('\\s+')
	Integer statusCode = part[1].toInteger()
	if (200 != statusCode) {
		String reason = part[2]
		log.error "controlResponse: $statusCode $reason"
		null
	} else {
		log.debug "controlResponse: $message.body"
		groovy.util.slurpersupport.GPathResult xml = parseXml message.body
		Map args = [:]
		xml.Body."${action}Response".'*'.each {node ->
			args."${node.name()}" = node.text()
		}
		args
	}
}

void controlResponseSwitchPowerGetStatus(physicalgraph.device.HubResponse hubResponse) {
	Map args = controlResponse 'SwitchPower', 'GetStatus', hubResponse
	log.debug "controlResponseSwitchPowerGetStatus: $args"
	if (args.containsKey('ResultStatus')) {
		String value = '1' == args.ResultStatus ? 'on' : 'off'
		log.info "controlResponseSwitchPowerGetStatus: sendEvent name: 'switch', value: $value"
		sendEvent name: 'switch', value: value
	}
}

void controlResponseDimmingGetLoadLevelStatus(physicalgraph.device.HubResponse hubResponse) {
	Map args = controlResponse 'Dimming', 'GetLoadLevelStatus', hubResponse
	log.debug "controlResponseDimmingGetLoadLevelStatus $args"
	if (args.containsKey('retLoadLevelStatus')) {
		String value = args.retLoadLevelStatus
		log.info "controlResponseDimmingGetLoadLevelStatus: sendEvent name: 'level', value: $value"
		sendEvent name: 'level', value: value
	}
}

void controlResponseSwitchPowerSetTarget(physicalgraph.device.HubResponse hubResponse) {
	controlResponse 'SwitchPower', 'SetTarget', hubResponse
}

void controlResponseDimmingSetLoadLevelTarget(physicalgraph.device.HubResponse hubResponse) {
	controlResponse 'Dimming', 'SetLoadLevelTarget', hubResponse
}

void refresh() {
	log.debug 'refresh'
	control 'SwitchPower', 'GetStatus'
	control 'Dimming', 'GetLoadLevelStatus'
}

void on() {
	log.debug 'on'
	control 'SwitchPower', 'SetTarget', [NewTargetValue: '1']
}

void off() {
	log.debug 'off'
	control 'SwitchPower', 'SetTarget', [NewTargetValue: '0']
}

void setLevel(level) {
	log.debug "setLevel: $level"
	control 'Dimming', 'SetLoadLevelTarget', [NewLoadLevelTarget: level]
	if (level) {
		on()
	} else {
		off()
	}
}

private String getNetworkAddress() {
	getDataValue 'networkAddress'
}
private void setNetworkAddress(String value) {
	updateDataValue 'networkAddress', value
}
private String getDeviceAddress() {
	getDataValue 'deviceAddress'
}
private void setDeviceAddress(String value) {
	updateDataValue 'deviceAddress', value
}
private String getDescription() {
	getDataValue 'description'
}

private Integer decodeHexadecimal(String hexadecimal) {
	Integer.parseInt hexadecimal, 16
}
private String decodeNetworkAddress(String networkAddress) {
	[
		decodeHexadecimal(networkAddress[0..1]),
		decodeHexadecimal(networkAddress[2..3]),
		decodeHexadecimal(networkAddress[4..5]),
		decodeHexadecimal(networkAddress[6..7]),
	].join('.')
}
private Integer decodeDeviceAddress(String deviceAddress) {
	decodeHexadecimal deviceAddress
}

private String getHost() {
	decodeNetworkAddress(networkAddress) + ':' + decodeDeviceAddress(deviceAddress)
}

private String getHub() {
	device.hub.getDataValue('localIP') + ':' + device.hub.getDataValue('localSrvPortTCP')
}

void notifySwitchPower(notification) {
	log.debug "notifySwitchPower: $notification.body"
	groovy.util.slurpersupport.GPathResult xml = parseXml notification.body
	String status = xml.property.Status.text()
	if (status) {
		String value = '1' == status ? 'on' : 'off'
		log.info "notifySwitchPower: sendEvent name: 'switch', value: $value"
		sendEvent name: 'switch', value: value
	}
}

void notifyDimming(notification) {
	log.debug "notifyDimming: $notification.body"
	groovy.util.slurpersupport.GPathResult xml = parseXml notification.body
	String value = xml.property.LoadLevelStatus.text()
	if (value) {
		log.info "notifyDimming: sendEvent name: 'level', value: $value"
		sendEvent name: 'level', value: value
	}
}

void parse(event) {
	log.error "parse: not expected: $event"
}

private void upnpSubscribe(String service) {
	String path = getDataValue "eventPath$service"
	log.debug "upnpSubscribe: $service, $path"
	String udn = device.deviceNetworkId
	sendHubCommand new physicalgraph.device.HubAction([
			method	: 'SUBSCRIBE',
			path	: path,
			headers: [
				Host	: host,
				CALLBACK: "<http://$hub/$udn/notify$service>",
				NT		: 'upnp:event',
				TIMEOUT	: 'Second-480',
			],
		],
		udn,
		[callback: "upnpSubscribeResponse$service"],
	)
	runIn(60, "upnpSubscribe$service")	// unschedule on success
}

private void resubscribe(String service) {
	String sid = getDataValue "sid$service"
	String path = getDataValue "eventPath$service"
	log.debug "resubscribe: $service, $path, $sid"
	String udn = device.deviceNetworkId
	sendHubCommand new physicalgraph.device.HubAction([
			method	: 'SUBSCRIBE',
			path	: path,
			headers: [
				Host	: host,
				SID		: "uuid:$sid",
				TIMEOUT	: 'Second-480',
			],
		],
		udn,
		[callback: "upnpSubscribeResponse$service"],
	)
	runIn(60, "upnpSubscribe$service")	// unschedule on success
}

void upnpSubscribeSwitchPower() {
	upnpSubscribe 'SwitchPower'
}

void upnpSubscribeDimming() {
	upnpSubscribe 'Dimming'
}

void resubscribeSwitchPower() {
	resubscribe 'SwitchPower'
}

void resubscribeDimming() {
	resubscribe 'Dimming'
}

private void upnpSubscribeResponse(String service, physicalgraph.device.HubResponse hubResponse) {
	def message = parseLanMessage hubResponse.description
	log.debug "upnpSubscribeResponse: $service, $message.headers"
	String response = message.header.split('\r\n')[0]
	// HTTP/1.1 <statusCode> <reason>
	def part = response.split('\\s+')
	Integer statusCode = part[1].toInteger()
	if (200 != statusCode) {
		String reason = part[2]
		log.error "upnpSubscribeResponse: $statusCode $reason"
	} else {
		def headers = message.headers
		String sid = headers.sid.split(':')[1]
		updateDataValue "sid$service", sid
		unschedule "upnpSubscribe$service"	// success
		refresh()
		Integer timeout = headers.timeout.split('-')[1].toInteger()
		if (120 > timeout) {
			timeout /= 2
		} else {
			timeout -= 60
		}
		runIn(timeout, "resubscribe$service")
	}
}

void upnpSubscribeResponseSwitchPower(physicalgraph.device.HubResponse hubResponse) {
	upnpSubscribeResponse 'SwitchPower', hubResponse
}

void upnpSubscribeResponseDimming(physicalgraph.device.HubResponse hubResponse) {
	upnpSubscribeResponse 'Dimming', hubResponse
}

private void attach() {
	def message = parseLanMessage description
	String body = message.body
	log.debug "attach: $body"
	groovy.util.slurpersupport.GPathResult xml = parseXml message.body
	groovy.util.slurpersupport.GPathResult serviceList = xml.device.serviceList
	['SwitchPower', 'Dimming'].each {service ->
		groovy.util.slurpersupport.GPathResult action = serviceList.'*'.find {action ->
			"urn:schemas-upnp-org:service:$service:1" == action.serviceType.text()
		}
		updateDataValue "controlPath$service", action.controlURL.text()
		updateDataValue "eventPath$service", action.eventSubURL.text()
		upnpSubscribe service
	}
}

private void upnpUnsubscribe(String service) {
	String path = getDataValue "eventPath$service"
	String sid = getDataValue "sid$service"
	log.debug "upnpUnsubscribe: $service, $path, $sid"
	if (path && sid) {
		sendHubCommand new physicalgraph.device.HubAction([
				method	: 'UNSUBSCRIBE',
				path	: path,
				headers: [
					HOST	: host,
					SID		: "uuid:$sid",
				],
			],
			device.deviceNetworkId,
			[callback: "upnpUnsubscribeResponse$service"],
		)
	}
}

void upnpUnsubscribeResponse(String service, physicalgraph.device.HubResponse hubResponse) {
	def message = parseLanMessage hubResponse.description
	log.debug "upnpUnsubscribeResponse: $service, $message"
	String response = message.header.split('\r\n')[0]
	// HTTP/1.1 <statusCode> <reason>
	def part = response.split('\\s+')
	Integer statusCode = part[1].toInteger()
	if (200 != statusCode) {
		String reason = part[2]
		log.error "upnpUnsubscribeResponse: $statusCode $reason"
	} else {
		updateDataValue "sid$service", ''
	}
}

void upnpUnsubscribeResponseSwitchPower(physicalgraph.device.HubResponse hubResponse) {
	upnpUnsubscribeResponse 'SwitchPower', hubResponse
}

void upnpUnsubscribeResponseDimming(physicalgraph.device.HubResponse hubResponse) {
	upnpUnsubscribeResponse 'Dimming', hubResponse
}

private void detach() {
	log.debug "detach"
	['SwitchPower', 'Dimming'].each {service ->
		upnpUnsubscribe service
	}
}

void install() {
	log.debug("install")
	attach()
}

void update(String networkAddress_, String deviceAddress_) {
	log.debug("update: $networkAddress_, $deviceAddress_")
	if (networkAddress != networkAddress_ || deviceAddress != deviceAddress_) {
		detach()
		networkAddress = networkAddress_
		deviceAddress = deviceAddress_
		attach()
	}
}

void uninstall() {
	log.debug("uninstall")
	detach()
}
