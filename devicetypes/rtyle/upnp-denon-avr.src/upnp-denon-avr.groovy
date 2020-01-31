// vim: ts=4:sw=4
/**
 *	UPnP Denon AVR
 *
 *	Copyright 2020 Ross Tyler
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
	definition (name: "UPnP Denon AVR", namespace: "rtyle", author: "Ross Tyler") {
		capability 'Refresh'
	}
	tiles() {
		standardTile('refreshTile', null, decoration: 'flat') {
			state 'default', label:'', action: 'refresh.refresh', icon:'st.secondary.refresh'
		}
		main(['refreshTile'])
		details(['refreshTile'])
	}
	preferences {
		input 'logLevel', 'number', defaultValue: '1', title: 'Log level (-1..4: trace, debug, info, warn, error, none)', range: '-1..4'
	}
}

private int getTrace() {0}
private int getDebug() {1}
private int getInfo	() {2}
private int getWarn	() {3}
private int getError() {4}
private void log(int level, String message) {
	if (level > (null == logLevel ? 1 : logLevel)) {
		log."${['trace', 'debug', 'info', 'warn', 'error'][level]}" message
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
private String getSsdpPath() {
	getDataValue 'ssdpPath'
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

private String getHostHttp() {
	decodeNetworkAddress(networkAddress) + ':80'
}

private String getHost() {
	decodeNetworkAddress(networkAddress) + ':' + decodeDeviceAddress(deviceAddress)
}

private String getHub() {
	device.hub.getDataValue('localIP') + ':' + device.hub.getDataValue('localSrvPortTCP')
}

private List getZones() {[
	'MainZone',
	'Zone2',
	'Zone3',
]}

void parse(event) {
	log error, "parse: not expected: $event"
}

void refresh() {
	log debug, 'refresh'
    getChildDevices().each {zoneChild ->
        zoneChild.refresh()
    }
}

// Denon UPnP services are pretty much useless.
// All actions will be handled through the Denon web interface.
// Only some service (RenderingControl) notifications (mute, volume) are ever sent
// and there are conditions where even these are not sent.
// We subscribe only to take the opportunity to refresh on what notifications we do get.
private List getServices() {[
	"RenderingControl",
    //	"ConnectionManager",
    //	"AVTransport",
]}

void refreshRenderingControl() {
	log debug, 'refreshRenderingControl'
    refresh()
}
void refreshConnectionManager() {
	log debug, 'refreshConnectionManager'
    refresh()
}
void refreshAVTransport() {
	log debug, 'refreshAVTransport'
    refresh()
}

void notifyRenderingControl(notification) {
	log debug, "notifyRenderingControl: $notification.body"
    refresh()
}
void notifyConnectionManager(notification) {
	log debug, "notifyConnectionManager: $notification.body"
    refresh()
}
void notifyAVTransport(notification) {
	log debug, "notifyAVTransport: $notification.body"
    refresh()
}
private void upnpSubscribe(String service) {
	String path = getDataValue "eventPath$service"
	log debug, "upnpSubscribe: $service, $path"
	String udn = device.deviceNetworkId
	sendHubCommand new physicalgraph.device.HubAction([
			method	: 'SUBSCRIBE',
			path	: path,
			headers: [
				Host	: host,
				CALLBACK: "<http://$hub/$udn/notify$service>",
				NT		: 'upnp:event',
				TIMEOUT	: 'Second-300',
			],
		],
		udn,
		[callback: "upnpSubscribeResponse$service"],
	)
}

private void resubscribe(String service) {
	String sid = getDataValue "sid$service"
	String path = getDataValue "eventPath$service"
	log debug, "resubscribe: $service, $path, $sid"
	String udn = device.deviceNetworkId
	sendHubCommand new physicalgraph.device.HubAction([
			method	: 'SUBSCRIBE',
			path	: path,
			headers: [
				Host	: host,
				SID		: "uuid:$sid",
				TIMEOUT	: 'Second-300',
			],
		],
		udn,
		[callback: "upnpSubscribeResponse$service"],
	)
}

void upnpSubscribeRenderingControl() {
	upnpSubscribe 'RenderingControl'
}
void upnpSubscribeConnectionManager() {
	upnpSubscribe 'ConnectionManager'
}
void upnpSubscribeAVTransport() {
	upnpSubscribe 'AVTransport'
}

void resubscribeRenderingControl() {
	resubscribe 'RenderingControl'
}
void resubscribeConnectionManager() {
	resubscribe 'ConnectionManager'
}
void resubscribeAVTransport() {
	resubscribe 'AVTransport'
}

private void upnpSubscribeResponse(String service, physicalgraph.device.HubResponse hubResponse) {
	def message = parseLanMessage hubResponse.description
	log debug, "upnpSubscribeResponse: $service, $message.headers"
	String response = message.header.split('\r\n')[0]
	// HTTP/1.1 <statusCode> <reason>
	def part = response.split('\\s+', 3)
	Integer statusCode = part[1].toInteger()
	if (200 != statusCode) {
		String reason = part[2]
		log error, "upnpSubscribeResponse: $service $statusCode $reason"
		runIn 60, "upnpSubscribe$service"	// unschedule on success
	} else {
		unschedule "upnpSubscribe$service"	// success
		def headers = message.headers
		String sid = headers.sid.split(':')[1]
		if (sid != getDataValue("sid$service")) {
			updateDataValue "sid$service", sid
			"refresh$service"()
		}
	}
}

void upnpSubscribeResponseRenderingControl(physicalgraph.device.HubResponse hubResponse) {
	upnpSubscribeResponse 'RenderingControl', hubResponse
}
void upnpSubscribeResponseConnectionManager(physicalgraph.device.HubResponse hubResponse) {
	upnpSubscribeResponse 'ConnectionManager', hubResponse
}
void upnpSubscribeResponseAVTransport(physicalgraph.device.HubResponse hubResponse) {
	upnpSubscribeResponse 'AVTransport', hubResponse
}

private void attach() {
	def message = parseLanMessage description
	String body = message.body
	log debug, "attach: $body"
	groovy.util.slurpersupport.GPathResult xml = parseXml message.body
	groovy.util.slurpersupport.GPathResult serviceList = xml.device.serviceList
	services.each {service ->
		groovy.util.slurpersupport.GPathResult action = serviceList.'*'.find {action ->
			"urn:schemas-upnp-org:service:$service:1" == action.serviceType.text()
		}
		updateDataValue "controlPath$service", action.controlURL.text()
		updateDataValue "eventPath$service", action.eventSubURL.text()
		upnpSubscribe service

		// Once we receive a good response to our subscription request,
		// we will need to renew it before it expires.
		// Using runIn to do so does not work well because
		// scheduling is, at best, late and, at worst, doesn't happen at all.
		// Instead, we set up a periodic schedule here using a runEvery* method.
		// These work much better.
		// From now, our first period will elapse sometime before it should
		// but subsequent ones will be close to clockwork.
		// We don't have a lot of choices for the period (1, 5, 10, 15, 30, 60, 180 minutes).
		// It seems we get a 5 minute subscription no matter what we ask for so
		// resubscribe every 5 minutes.
		runEvery5Minutes "resubscribe$service"
	}
}

private void upnpUnsubscribe(String service) {
	String path = getDataValue "eventPath$service"
	String sid = getDataValue "sid$service"
	log debug, "upnpUnsubscribe: $service, $path, $sid"
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
	log debug, "upnpUnsubscribeResponse: $service, $message"
	String response = message.header.split('\r\n')[0]
	// HTTP/1.1 <statusCode> <reason>
	def part = response.split('\\s+')
	Integer statusCode = part[1].toInteger()
	if (200 != statusCode) {
		String reason = part[2]
		log error, "upnpUnsubscribeResponse: $service $statusCode $reason"
	} else {
		updateDataValue "sid$service", ''
	}
}

void upnpUnsubscribeResponseRenderingControl(physicalgraph.device.HubResponse hubResponse) {
	upnpUnsubscribeResponse 'RenderingControl', hubResponse
}
void upnpUnsubscribeResponseConnectionManager(physicalgraph.device.HubResponse hubResponse) {
	upnpUnsubscribeResponse 'ConnectionManager', hubResponse
}
void upnpUnsubscribeResponseAVTransport(physicalgraph.device.HubResponse hubResponse) {
	upnpUnsubscribeResponse 'AVTransport', hubResponse
}

private void detach() {
	log debug, "detach"
	services.each {service ->
		upnpUnsubscribe service
	}
	unschedule()
}

void install() {
	log debug, "install"
	attach()
    String namespace = 'rtyle'
    String name = 'UPnP Denon AVR'
    zones.each {zone ->
    	String childName = "$name Zone"
        String dni = "$device.deviceNetworkId\t$zone"
        String label = "$device.label $zone"
        log info, "install: addChildDevice $namespace, $childName, $dni, null, [label: $label, data: [networkAddress: $networkAddress, deviceAddress: $deviceAddress, ssdpPath: $ssdpPath, description: $description]]"
        physicalgraph.app.DeviceWrapper zoneChild = addChildDevice namespace, childName, dni, null, [
            data : [
                networkAddress	: networkAddress,
                deviceAddress	: deviceAddress,
                ssdpPath		: ssdpPath,
                description		: description,
                zone			: zone,
            ],
            label				: label,
            completedSetup		: true,
        ]
        zoneChild.install()
    }
}

void update(String networkAddress_, String deviceAddress_) {
	log debug, "update: $networkAddress_, $deviceAddress_"
	if (networkAddress != networkAddress_ || deviceAddress != deviceAddress_) {
		detach()
		networkAddress = networkAddress_
		deviceAddress = deviceAddress_
		attach()
        getChildDevices().each {zoneChild ->
            zoneChild.update(networkAddress, deviceAddress)
        }
	}
}

void uninstall() {
	log debug, "uninstall"
	detach()
	getChildDevices().each {zoneChild ->
		zoneChild.uninstall()
		log debug, "uninstall: deleteChildDevice $zoneChild.deviceNetworkId"
		deleteChildDevice zoneChild.deviceNetworkId
	}
}
