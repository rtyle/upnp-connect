// vim: ts=4:sw=4
/**
 *	UPnP Denon AVR Zone
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

// *WARNING*
// this code has been developed and tested against the Denon AVR X4100W model only

private List getInputSources() {[
	// these sources may be renamed and have hardware inputs assigned to them
	[id: 'CBLSAT'	, label: 'CBL/SAT'			],
	[id: 'DVD'		, label: 'DVD'				],
	[id: 'BD'		, label: 'Blu-ray'			],
	[id: 'GAME'		, label: 'Game'				],
	[id: 'MPLAY'	, label: 'Media Player'		],
	[id: 'TV'		, label: 'TV Audio'			],
	[id: 'AUX1'		, label: 'AUX1'				],
	[id: 'AUX2'		, label: 'AUX2'				],
	[id: 'CD'		, label: 'CD'				],
	[id: 'PHONO'	, label: 'Phono'			],
	// these sources cannot be renamed and require further configuration
	[id: 'TUNER'	, label: 'Tuner'			],
	[id: 'BT'		, label: 'Bluetooth'		],
	[id: 'IPOD'		, label: 'iPod/USB'			],
	[id: 'NETHOME'	, label: 'Online Music'		],
	[id: 'SERVER'	, label: 'Media Server'		],
	[id: 'IRP'		, label: 'Internet Radio'	],
]}

metadata {
	definition (name: "UPnP Denon AVR Zone", namespace: "rtyle", author: "Ross Tyler") {
		capability 'AudioMute'
		capability 'AudioVolume'
		capability 'MediaInputSource'
		capability 'Refresh'
		capability 'Switch'

		inputSources.each {inputSource ->
			command	"setInputSource$inputSource.id"
		}
	}
	tiles(scale: 2) {
		multiAttributeTile(name: 'mainTile', type: 'generic', width:6, height:4) {
			tileAttribute('device.switch', key: 'PRIMARY_CONTROL') {
				attributeState 'on'			, label: '${name}'		, action: 'switch.off'	, icon: 'st.Electronics.electronics19', backgroundColor: '#00A0DC', nextState: 'turningOff'
				attributeState 'off'		, label: '${name}'		, action: 'switch.on'	, icon: 'st.Electronics.electronics19', backgroundColor: '#FFFFFF', nextState: 'turningOn'	, defaultState: true
				attributeState 'turningOn'	, label: 'Turning On'	, action: 'switch.off'	, icon: 'st.Electronics.electronics19', backgroundColor: '#00A0DC', nextState: 'turningOn'
				attributeState 'turningOff'	, label: 'Turning Off'	, action: 'switch.on'	, icon: 'st.Electronics.electronics19', backgroundColor: '#FFFFFF', nextState: 'turningOff'
			}
			tileAttribute('device.inputSource', key: 'SECONDARY_CONTROL') {
				attributeState '', label: '${currentValue}'
			}
			tileAttribute('device.volume', key: 'SLIDER_CONTROL', range: '(0..100)') {
				attributeState '', action: 'audio volume.setVolume'
			}
		}
		standardTile('switchOnTile', 'device.switch', decoration: 'flat') {
			state '', label: 'On', action: 'switch.on', icon: 'st.switches.switch.on', backgroundColor: '#ffffff'
		}
		standardTile('switchOffTile', 'device.switch', decoration: 'flat') {
			state '', label: 'Off', action: 'switch.off', icon: 'st.switches.switch.off', backgroundColor: '#ffffff'
		}
		standardTile('muteTile', 'device.mute', decoration: 'flat') {
			state 'muted'	, label: '${name}'	, action: 'audio mute.unmute'	, icon: 'st.custom.sonos.muted'		, backgroundColor: '#00A0DC', nextState: 'unmuting'
			state 'unmuted'	, label: '${name}'	, action: 'audio mute.mute'		, icon: 'st.custom.sonos.unmuted'	, backgroundColor: '#FFFFFF', nextState: 'muting'	, defaultState: true
			state 'muting'	, label: 'Muting'	, action: 'audio mute.unmute'	, icon: 'st.custom.sonos.muted'		, backgroundColor: '#00A0DC', nextState: 'muting'
			state 'unmuting', label: 'Unmuting'	, action: 'audio mute.mute'		, icon: 'st.custom.sonos.unmuted'	, backgroundColor: '#FFFFFF', nextState: 'unmuting'
		}
		standardTile('muteMuteTile', 'device.mute', decoration: 'flat') {
			state '', label: 'Mute', action: 'audio mute.mute', icon: 'st.custom.sonos.unmuted', backgroundColor: '#ffffff'
		}
		standardTile('muteUnmuteTile', 'device.mute', decoration: 'flat') {
			state '', label: 'Unmute', action: 'audio mute.unmute', icon: 'st.custom.sonos.muted', backgroundColor: '#ffffff'
		}
		standardTile('refreshTile', null, decoration: 'flat') {
			state '', label: 'Refresh', action: 'refresh.refresh', icon:'st.secondary.refresh'
		}
		inputSources.each {inputSource ->
			standardTile(inputSource.id, null, decoration: 'flat') {
				state '', label: inputSource.label, action: "setInputSource$inputSource.id"
			}
		}
		main(['mainTile'])
		details(['mainTile', 'switchOnTile', 'switchOffTile', 'muteTile', 'muteMuteTile', 'muteUnmuteTile', 'volumeTile', 'refreshTile'] + inputSources.collect{it.id})
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
private String getDescription() {
	getDataValue 'description'
}
private String getZone() {
	getDataValue 'zone'
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

void parse(event) {
	log error, "parse: not expected: $event"
}

void sendEvent_(Map args) {
	log debug, "sendEvent $args"
	sendEvent args
}

void response(physicalgraph.device.HubResponse hubResponse, String command, Closure success) {
	def message = parseLanMessage hubResponse.description
	log debug, "${command}Response: $message.headers $message.body"
	String response = message.header.split('\r\n')[0]
	// HTTP/1.1 <statusCode> <reason>
	def part = response.split('\\s+', 3)
	Integer statusCode = part[1].toInteger()
	if (200 != statusCode) {
		String reason = part[2]
		log error, "${command}Response: $statusCode $reason"
	} else {
		success(message)
	}
}

void refreshResponse(physicalgraph.device.HubResponse hubResponse) {
	response hubResponse, 'refresh', {message ->
		def item = parseXml message.body
		sendEvent_ name: 'switch'		, value: 'ON' == item.ZonePower.value.text()	? 'on'		: 'off'
		sendEvent_ name: 'mute'			, value: 'on' == item.Mute.value.text()			? 'muted'	: 'unmuted'
		sendEvent_ name: 'volume'		, value: decodeVolume(item.MasterVolume.text())
		sendEvent_ name: 'inputSource'	, value: decodeInputSource(item.InputFuncSelect.text(), item.NetFuncSelect.text())
	}
}
private void refreshRequest() {
	log debug, "refreshRequest: curl -X GET http://$hostHttp/goform/formMainZone_MainZoneXml.xml?ZoneName=$zone"
	sendHubCommand new physicalgraph.device.HubAction([
			method: 'GET',
			path: "/goform/formMainZone_MainZoneXml.xml?ZoneName=$zone",
			headers: [
				Host: hostHttp,
			],
		],
		device.deviceNetworkId,
		[callback: "refreshResponse"],
	)
}
void refresh() {
	refreshRequest()
}

private void request(String command, String form) {
	// AVR expects urlencoded form to be in body (instead of appended to path)
	log debug, "${command}Request: curl -d \"ZoneName=$zone&cmd0=Put$form\" -X POST http://$hostHttp/MainZone/index.put.asp"
	sendHubCommand new physicalgraph.device.HubAction([
			method: 'POST',
			path: "/MainZone/index.put.asp",
			headers: [
				Host: hostHttp,
				'Content-Type': 'application/x-www-form-urlencoded'
			],
			body: "ZoneName=$zone&cmd0=Put$form",
		],
		device.deviceNetworkId,
		[callback: "${command}Response"],
	)
}

void onResponse(physicalgraph.device.HubResponse hubResponse) {
	response hubResponse, 'on', {sendEvent_ name: 'switch', value: 'on'}
}
void on() {
	request 'on', 'Zone_OnOff%2fON'
}
void offResponse(physicalgraph.device.HubResponse hubResponse) {
	response hubResponse, 'off', {sendEvent_ name: 'switch', value: 'off'}
}
void off() {
	request 'off', 'Zone_OnOff%2fOFF'
}

private String encodeVolume_(Integer volume) {
	// 0-100 -> -80..18 with .5 precision
	// to match AVR monitor display (not front panel, which is ~2 less)
	if (2 > volume)
		-80
	else
		(2 * (volume - 2 - 80)) / 2.0f
}
private String encodeVolume(Integer volume) {
	String _ = encodeVolume_ volume
	log debug, "encodeVolume: $volume -> $_"
	_
}
private Integer decodeVolume_(Float volume) {
	// -80..18 -> 0-100
	if (-80 >= volume)
		0
	else
		volume + 80 + 2
}
private Integer decodeVolume(Float volume) {
	Integer _ = decodeVolume_ volume
	log debug, "decodeVolume: $volume -> $_"
	_
}
private Integer decodeVolume(String volume) {
	if (volume.isFloat())
		decodeVolume volume.toFloat()
	else
		0
}
void volumeResponse(physicalgraph.device.HubResponse hubResponse) {
	response hubResponse, 'volume', {refreshRequest()}
}
void volumeDown() {
	request 'volume', 'MasterVolumeBtn%2f%3c'
}
void volumeUp() {
	request 'volume', 'MasterVolumeBtn%2f%3e'
}
void setVolume(Integer volume) {
	request 'volume', "MasterVolumeSet%2f${encodeVolume volume}"
}

void muteResponse(physicalgraph.device.HubResponse hubResponse) {
	response hubResponse, 'mute', {sendEvent_ name: 'mute', value: 'muted'}
}
void mute() {
	request 'mute', 'VolumeMute%2fON'
}
void unmuteResponse(physicalgraph.device.HubResponse hubResponse) {
	response hubResponse, 'unmute', {sendEvent_ name: 'mute', value: 'unmuted'}
}
void unmute() {
	request 'unmute', 'VolumeMute%2fOFF'
}
void setMute(String mute) {
	if ('muted' == mute)
		mute()
	else
		unmute()
}

private String decodeInputSource(String a, String b) {
	if ('Online Music' != a) {
		a
	} else {
		switch (b) {
			case 'SERVER': return 'Media Server'
			case 'IRADIO': return 'Internet Radio'
			case 'NET'	 :
			default		 : return 'Online Music'
		}
	}
}
void setInputSourceResponse(physicalgraph.device.HubResponse hubResponse) {
	response hubResponse, 'setInputSource', {refreshRequest()}
}
void setInputSource(String inputSource) {
	log debug, "setInputSource: $source"
	request 'setInputSource', "Zone_InputFunction%2f$inputSource"
}
void setInputSourceCBLSAT	() {setInputSource('SAT%2FCBL'	)}
void setInputSourceDVD		() {setInputSource('DVD'		)}
void setInputSourceBD		() {setInputSource('BD'			)}
void setInputSourceGAME		() {setInputSource('GAME'		)}
void setInputSourceMPLAY	() {setInputSource('MPLAY'		)}
void setInputSourceTV		() {setInputSource('TV'			)}
void setInputSourceAUX1		() {setInputSource('AUX1'		)}
void setInputSourceAUX2		() {setInputSource('AUX2'		)}
void setInputSourceCD		() {setInputSource('CD'			)}
void setInputSourcePHONO	() {setInputSource('PHONO'		)}
void setInputSourceTUNER	() {setInputSource('TUNER'		)}
void setInputSourceBT		() {setInputSource('BT'			)}
void setInputSourceIPOD		() {setInputSource('USB%2FIPOD'	)}
void setInputSourceNETHOME	() {setInputSource('NETHOME'	)}
void setInputSourceSERVER	() {setInputSource('SERVER'		)}
void setInputSourceIRP		() {setInputSource('IRP'		)}

void install() {
	log debug, "install"
}

void update(String networkAddress_, String deviceAddress_) {
	log debug, "update: $networkAddress_, $deviceAddress_"
	if (networkAddress != networkAddress_ || deviceAddress != deviceAddress_) {
		networkAddress = networkAddress_
		deviceAddress = deviceAddress_
	}
}

void uninstall() {
	log debug, "uninstall"
}
