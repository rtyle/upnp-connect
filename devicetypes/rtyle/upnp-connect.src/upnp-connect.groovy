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
metadata {
	definition (name: 'UPnP (Connect)', namespace: 'rtyle', author: 'Ross Tyler') {
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
		// TODO: define your main and details tiles here
	}
}

private physicalgraph.app.DeviceWrapper getChildDevice(String udn) {
	getChildDevices().find {child ->
		udn == child.deviceNetworkId
	}
}

// Events returned parse would be handled by the SmartThings runtime.
// These could include events created and returned from the child we notify.
// However, such would not be heard by subscribers of the child.
// Instead, our children should explicitly call sendEvent.
// We ignore anything they return and explicitly return nothing.
void parse(description) {
	// parse the lan message notification and pass it to the appropriate child
	def notification = parseLanMessage description
	log.debug "parse: $notification"
	String request = notification.header.split('\r\n')[0]
	// NOTIFY path HTTP/1.1
	def path = request.split('\\s+')[1].split('/')
	String udn = path[1]
	physicalgraph.app.DeviceWrapper child = getChildDevice udn
	if (child) {
		String notify = path[2]
		child."$notify" notification
	} else {
		log.error "parse: $udn child not found"
	}
}

boolean hasChild(String udn) {
	getChildDevice udn
}

void add(String namespace, String name, String udn, String hubId, String description, String networkAddress, String deviceAddress, String ssdpPath, String label) {
	physicalgraph.app.DeviceWrapper child = getChildDevice udn
	if (!child) {
		log.debug "add: addChildDevice $namespace, $name, $udn, $hubId, [label: $label, data: [networkAddress: $networkAddress, deviceAddress: $deviceAddress, ssdpPath: $ssdpPath, description: $description]]"
		child = addChildDevice namespace, name, udn, hubId, [
			data			: [
				description		: description,
				networkAddress	: networkAddress,
				deviceAddress	: deviceAddress,
				ssdpPath		: ssdpPath,
			],
			label			: label,
			completedSetup	: true,
		]
		child.install()
	}
}

void update(String udn, String networkAddress, String deviceAddress) {
	log.debug "update: (getChildDevice $udn).update $networkAddress $deviceAddress"
	physicalgraph.app.DeviceWrapper child = getChildDevice udn
	if (child) {
		child.update networkAddress, deviceAddress
	}
}

void uninstall() {
	getChildDevices().each({child ->
		log.debug "uninstall: deleteChildDevice $child.deviceNetworkId"
		child.uninstall()
		deleteChildDevice child.deviceNetworkId
	});
}
