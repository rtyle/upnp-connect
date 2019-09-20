// vim: ts=4:sw=4
/**
 *	UPnP (Connect)
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
definition(
	name		: 'UPnP (Connect)',
	namespace	: 'rtyle',
	author		: 'Ross Tyler',
	description	: 'UPnP connect to hub-local devices',
	category	: 'Convenience',
	iconUrl		: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
	iconX2Url	: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
	iconX3Url	: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
)

preferences {
	section('Title') {
		// TODO: put inputs here
	}
}

// we would have liked to have gotten these from the definition above (or vice-versa)
private String getNamespace() {
	'rtyle'
}
private String getName() {
	'UPnP (Connect)'
}

// map of Uniform Resource Name (URN) to SmartThings Device Handler characteristics
private Map getUrnToDeviceHandler() {[
	'schemas-upnp-org:device:BinaryLight:1'		: [namespace: namespace, name: 'UPnP BinaryLight'],
	'schemas-upnp-org:device:DimmableLight:1'	: [namespace: namespace, name: 'UPnP DimmableLight'],
]}

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

private Map getRememberedDevice() {
	if (!state.rememberedDevice) {
		state.rememberedDevice = [:]
	}
	state.rememberedDevice
}

private def decodeMap(String serialization, String major = '\\s*,\\s*', String minor = '\\s*:\\s*') {
	Map map = [:]
	serialization.split(major).each {
		def a = it.split(minor, 2)
		map << [(a[0]) : a[1]]
	}
	map;
}

void ssdpPathResponse(physicalgraph.device.HubResponse hubResponse) {
	def message = parseLanMessage(hubResponse.description)
	// log.debug "ssdpPathResponse: $message.body"
	def xml = parseXml(message.body)
	def device = xml.device
	String udn = decodeMap(device.UDN.text()).uuid
	log.debug "ssdpPathResponse: udn=$udn"

	if (rememberedDevice."$udn") {
		def remembered = rememberedDevice."$udn"

		// SmartThings delivers events from a UPnP device
		// to the SmartThings device identified by the MAC address of the UPnP device
		// that the hub received the event from.
		// UPnP identifies its devices with a UDN and there may be many UPnP devices supported at a MAC address.
		// We create a child for each MAC address and one for each UDN.
		// The UDN identified devices handle all UPnP communication directly except they cannot handle event reception.
		// Instead, they encode their UDN and notify method in the SUBSCRIBE CALLBACK header.
		// The MAC identified devices' parse method will decode this from the HTTP request,
		// and notify the UDN identified device.

		// create the MAC identified child, if needed
		String mac = remembered.mac
		physicalgraph.app.DeviceWrapper macChild = getChildDevice mac
		if (!macChild) {
			def label = name + ' ' + mac
			log.debug "ssdpPathResponse: addChildDevice $namespace, $name, $mac, $hubResponse.hubId [label: $label, completedSetup: true]"
			macChild = addChildDevice namespace, name, mac, hubResponse.hubId, [label: label, completedSetup: true]
		}

		// create the UDN identified child, if we support its URN and needed
		String urn = remembered.ssdpTerm.urn
		log.debug("ssdpPathResponse: urn=$urn")
		if (urnToDeviceHandler."$urn") {
			def deviceHandler = urnToDeviceHandler."$urn"
			log.debug("ssdpPathResponse: urn=$urn device hander $deviceHandler")
			physicalgraph.app.DeviceWrapper udnChild = getChildDevice udn
			if (!udnChild) {
				log.debug "ssdpPathResponse: addChildDevice $deviceHandler.namespace, $deviceHandler.name, $udn, hubResponse.hubId, [label: device.friendlyName.text(), data: [networkAddress: $remembered.networkAddress, deviceAddress: $remembered.deviceAddress, ssdpPath: $remembered.ssdpPath, description: $hubResponse.description]]"
				udnChild = addChildDevice deviceHandler.namespace, deviceHandler.name, udn, hubResponse.hubId, [
					data			: [
						description		: hubResponse.description,
						networkAddress	: remembered.networkAddress,
						deviceAddress	: remembered.deviceAddress,
						ssdpPath		: remembered.ssdpPath,
					],
					label			: device.friendlyName.text(),
					completedSetup	: true,
				]
				udnChild.install()
			}
		} else {
			log.error("ssdpPathResponse: urn=$urn has no device handler")
		}
	}
}

private void ssdpDiscovered(physicalgraph.app.EventWrapper e) {
	def discovered = parseLanMessage e.description
	discovered.ssdpUSN = decodeMap discovered.ssdpUSN, '\\s*::\\s*'
	discovered.ssdpTerm = decodeMap discovered.ssdpTerm
	log.debug "ssdpDiscovered: $discovered"

	String urn = discovered.ssdpUSN.urn
	if (!urnToDeviceHandler."$urn") {
		// ignore discovered services
		// and discovered devices we can't handle (we shouldn't get these)
		log.debug("ssdpDiscovered: ignore $urn")
		return
	}

	String udn = discovered.ssdpUSN.uuid
	rememberedDevice."$udn" = discovered;

	physicalgraph.app.DeviceWrapper udnChild = getChildDevice udn
	if (udnChild) {
		log.debug "ssdpDiscovered: (getChildDevice $udn).update $discovered.networkAddress $discovered.deviceAddress"
		udnChild.update discovered.networkAddress, discovered.deviceAddress	
	} else {
		String target = decodeNetworkAddress(discovered.networkAddress) + ':' + decodeDeviceAddress(discovered.deviceAddress)
		log.debug "ssdpDiscovered: GET http://$target${discovered.ssdpPath}"
		sendHubCommand new physicalgraph.device.HubAction(
			"GET ${discovered.ssdpPath} HTTP/1.1\r\nHOST: $target\r\n\r\n",
			physicalgraph.device.Protocol.LAN,
			target,
			[callback: ssdpPathResponse],
		)
	}
}

private void ssdpDiscover() {
	List hubActions = [];
	urnToDeviceHandler.each {urn, notUsed ->
		log.debug "ssdpDiscover: hubAction lan discover urn:${urn}"
		hubActions.add new physicalgraph.device.HubAction('lan discovery urn:' + urn, physicalgraph.device.Protocol.LAN)
	}
	sendHubCommand hubActions, 4000	// perform hubActions with a delay between them
}

private void ssdpSubscribe() {
	urnToDeviceHandler.each {urn, deviceHandler ->
		// subscribe to event by name (ssdpTerm) and (dot) value (urn:$urn)
		log.debug "ssdpSubscribe: subscribe ssdpTerm.urn:$urn"
		subscribe location, 'ssdpTerm' + '.urn:' + urn, 'ssdpDiscovered'
	}
}

void updated() {
	log.debug "updated"
	ssdpDiscover()
}

void installed() {
	log.debug "installed"
	ssdpSubscribe()
	ssdpDiscover()
}

void uninstalled() {
	getChildDevices().each({child ->
		log.debug "uninstalled: deleteChildDevice $child.deviceNetworkId"
		child.uninstall()
		deleteChildDevice child.deviceNetworkId
	});
}
