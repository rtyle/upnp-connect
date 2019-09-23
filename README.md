# upnp-connect
**upnp-connect** provides SmartThings components to connect to UPnP devices on your LAN.
The SmartThings hub is used discover and communicate with these devices.

Currently, the only device UPnP URNs that are supported are
* schemas-upnp-org:device:BinaryLight:1
* schemas-upnp-org:device:DimmableLight:1

Which correspond to
* [OCF UPnP BinaryLight](http://upnp.org/specs/ha/UPnP-ha-BinaryLight-v1-Device.pdf)
* [OCF UPnP DimmableLight](http://upnp.org/specs/ha/UPnP-ha-DimmableLight-v1-Device.pdf)

A companion project is [upnp-things](https://www.github.com/rtyle/upnp-things) which puts a UPnP face on such things. Together, these projects may be used to replace SmartThings to [Legrand Adorne LC7001 hub](https://www.legrand.us/adorne/products/wireless-whole-house-lighting-controls/lc7001.aspx) integration through Samsung’s ARTIK Cloud. Samsung has abandoned the ARTIK Cloud and Legrand has no plans to provide an alternative. This is an alternative.

Using the SmartThings IDE, one can integrate this repository and *Execute Update* with

* smartapps/rtyle/upnp-connect.src/upnp-connect.groovy

for your *SmartApps* and

* devicetypes/rtyle/upnp-dimmablelight.src/upnp-dimmablelight.groovy
* devicetypes/rtyle/upnp-connect.src/upnp-connect.groovy
* devicetypes/rtyle/upnp-binarylight.src/upnp-binarylight.groovy

for your *Device Handlers*. Otherwise, one can just copy and paste this code.

One should deploy the **UPnP (Connect)** SmartApp to request a discovery search and begin automatic device handler creation.
If the SmartThings cloud is flooded with many discovered devices, creation for some may fail.
Request another discovery search by opening the SmartApp settings, turning on *Request a discovery search* and clicking *Save*.
Repeat until all your devices are supported.
Repeat again when new devices are added.

For each hosted service implementing supported UPnP devices on your LAN,
a special device named **UPnP (Connect) MAC** will be created to support events coming from its host
where **MAC** is the MAC address of that host.
If the IP address or port of such a service changes,
*Request a discovery search* again from the **UPnP (Connect)** SmartApp.
Repeat until all your devices are supported again.

When UPnP devices are added to (or restarted on) your hub's LAN, they may advertise their presence without an explicit discovery search being requested. If their device URNs are supported, the result is the same: they will be (re)discovered.
  
The **UPnP (Connect)** SmartApp will never remove any of its created devices until the SmartApp is removed.
This includes the special **UPnP (Connect) MAC** devices.
You can remove any of these manually if you want
but if they still exist on your LAN they will be recreated the next time they are discovered.
