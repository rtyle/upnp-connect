# upnp-connect
**upnp-connect** provides SmartThings components to connect to UPnP devices on your LAN.
The SmartThings hub is used discover and communicate with these devices.

Currently, the only device UPnP URNs that are supported are
* schemas-upnp-org:device:BinaryLight:1
* schemas-upnp-org:device:DimmableLight:1
* schemas-upnp-org:device:MediaRenderer:1

Which correspond to
* [OCF UPnP BinaryLight](http://upnp.org/specs/ha/UPnP-ha-BinaryLight-v1-Device.pdf)
* [OCF UPnP DimmableLight](http://upnp.org/specs/ha/UPnP-ha-DimmableLight-v1-Device.pdf)
* [OCF UPnP MediaRenderer](http://upnp.org/specs/av/UPnP-av-MediaRenderer-v1-Device.pdf)

A companion project is [connect-things](https://www.github.com/rtyle/connect-things) which puts a UPnP face on such things. Together, these projects may be used to replace SmartThings to [Legrand Adorne LC7001 hub](https://www.legrand.us/adorne/products/wireless-whole-house-lighting-controls/lc7001.aspx) integration through Samsung’s ARTIK Cloud. Samsung has abandoned the ARTIK Cloud and Legrand has no plans to provide an alternative. This is an alternative.

OCF UPnP MediaRenderer support is limited to the [Denon AVR X4100W](https://usa.denon.com/us/product/hometheater/receivers/avrx4100w). Here UPnP is only used for discovery. A Device is created for each AVR zone and control is given to turn the zone on/off, set the volume and select the input source.

Using the SmartThings IDE, one can integrate this repository and *Execute Update* with

* smartapps/rtyle/upnp-connect.src/upnp-connect.groovy

for your *SmartApps* and

* devicetypes/rtyle/upnp-dimmablelight.src/upnp-dimmablelight.groovy
* devicetypes/rtyle/upnp-connect.src/upnp-connect.groovy
* devicetypes/rtyle/upnp-binarylight.src/upnp-binarylight.groovy
* devicetypes/rtyle/upnp-denon-avr.src/upnp-denon-avr.groovy
* devicetypes/rtyle/upnp-denon-avr.src/upnp-denon-avr-zone.groovy

as needed, for your *Device Handlers*. Otherwise, one can just copy and paste this code.

One should deploy the **UPnP (Connect)** SmartThings SmartApp and change the settings to select the supported SmartThings device types to search for and begin the search (save these settings). The SmartApp will periodically perform such searches and collect candidates that might have SmartThings devices created for them. Use the SmartApp's settings again to select found candidates that you want this done for. When you save these settings, the SmartThings devices are created for the selected candidates. When you are done adding SmartThings devices you may turn search off or leave it on to continually monitor and adjust to changes on your hub's LAN…

For each hosted service implementing supported UPnP devices on your LAN,
a special SmartThings Device named **UPnP (Connect) MAC** will be created to support events coming from its host
where **MAC** is the MAC address of that host.
If the MAC address, IP address or port of such a service changes,
search again from the **UPnP (Connect)** SmartApp.
Leave search on until all your SmartThings Devices are supported again.

When UPnP devices are added to (or restarted on) your hub's LAN, they may advertise their presence without an explicit discovery search being requested. If their device URNs are supported and were requested, the result is the same: they will be (re)discovered.
  
The **UPnP (Connect)** SmartApp will never remove any of its created SmartThings Devices until the SmartApp is removed.
This includes the special **UPnP (Connect) MAC** SmartThings Devices.
You can remove any of the **UPnP (Connect)** SmartApp's Devices manually if you want.
If they still exist on your LAN and are needed for communication, the MAC devices will be recreated automatically the next time they are discovered.

**UPnP (Connect)** will add a prefix to the *friendlyName* of a UPnP device when creating/labeling a new SmartThings device. By default, this is 'UPnP '. This *Device label prefix* can be changed or even removed in the SmartApp's settings. Of course, the created device's label can later be edited to be anything you want using the SmartThings app or IDE.
