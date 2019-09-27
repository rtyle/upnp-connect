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
	// we have no need for tiles
	// but unless we do something we'll see 'Tiles Missing' in the Things tab of the phone app
	tiles() {
		standardTile('.', '') {}
	}
}

// Events returned by parse would be handled by the SmartThings runtime.
// These could include events created and returned from the child we notify.
// However, such would not be heard by subscribers of the child.
// Instead, our children should explicitly call sendEvent.
// We ignore anything they return and explicitly return nothing.
void parse(description) {
	// parse the lan message notification and pass it to the appropriate child
	def notification = parseLanMessage description
	// log.debug "parse: $notification"
	String request = notification.header.split('\r\n')[0]
	log.debug "parse: $request"
	// NOTIFY path HTTP/1.1
	def path = request.split('\\s+')[1].split('/')
	String udn = path[1]
	physicalgraph.app.DeviceWrapper child = parent.getChildDevice udn
	if (child) {
		String notify = path[2]
		child."$notify" notification
	} else {
		log.error "parse: $udn sibling not found"
	}
}

void uninstall() {
}
