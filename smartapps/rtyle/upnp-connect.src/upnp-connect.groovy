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
	def udn = decodeMap(device.UDN.text()).uuid
	log.debug "ssdpPathResponse: udn=$udn"
	if (rememberedDevice."$udn") {
		def remembered = rememberedDevice."$udn"

		// We would have liked to create a child SmartThings device to handle each UPnP device
		// but SmartThings delivers events from a UPnP device
		// to the SmartThings device identified by the MAC address of the UPnP device that the hub received the event from.
		// UPnP identifies its devices with a UDN and there may be many UPnP devices supported at a MAC address.
		// So, our child SmartThings devices are identified by MAC addresses and their children are identified by UDNs.
		// The UDN identified devices handle all UPnP communication directly except they cannot handle event reception.
		// Instead, they encode their UDN and notify method in the SUBSCRIBE CALLBACK header.
		// Their parent's parse method will decode this from the HTTP request and dispatch the notification to the child.

		// create the MAC identified child, if needed
		def mac = remembered.mac
		def child = getChildDevice mac
		if (!child) {
			def label = name + ' ' + mac
			log.debug "ssdpPathResponse: addChildDevice $namespace, $name, $mac, $hubResponse.hubId [label: $label, completedSetup: true]"
			child = addChildDevice namespace, name, mac, hubResponse.hubId, [label: label, completedSetup: true]
		}

		// tell the MAC identified child to create a UDN identified child, if able/needed
		String urn = remembered.ssdpTerm.urn
		log.debug("ssdpPathResponse: urn=$urn")
		if (urnToDeviceHandler."$urn") {
			def deviceHandler = urnToDeviceHandler."$urn"
			log.debug("ssdpPathResponse: urn=$urn device hander $deviceHandler")
			child.add deviceHandler.namespace, deviceHandler.name, udn, hubResponse.hubId, hubResponse.description,
				remembered.networkAddress, remembered.deviceAddress, remembered.ssdpPath, device.friendlyName.text()
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
	String udn = discovered.ssdpUSN.uuid	// udn
	if (rememberedDevice."$udn") {
		def remembered = rememberedDevice."$udn"
		if (false
				|| remembered.networkAddress	!= discovered.networkAddress
				|| remembered.deviceAddress		!= discovered.deviceAddress) {
			String mac = remembered.mac
			log.debug "ssdpDiscovered: (getChildDevice $mac).update $udn $discovered.networkAddress $discovered.deviceAddress"
			def child = getChildDevice mac
			child?.update udn, discovered.networkAddress, discovered.deviceAddress
			remembered.networkAddress	= discovered.networkAddress
			remembered.deviceAddress	= discovered.deviceAddress
		}
	} else {
		rememberedDevice."$udn" = discovered;
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
	log.debug "updated: ${settings}"
	ssdpDiscover()
}

void installed() {
	log.debug "installed: ${settings}"
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
