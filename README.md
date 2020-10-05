# GeoIP
The **ModuleGeoIp** module for Wowza Streaming Engine™ [media server software](https://www.wowza.com/products/streaming-engine) enables applications to allow or block connections based on the country they originate from.

This repo includes a [compiled version](/lib/wse-plugin-geoip.jar).

## Prerequisites
Wowza Streaming Engine 4.7.5 or later is required.

The following jar files from the [Maxmind GeoIP2-java GitHub Releases page](https://github.com/maxmind/GeoIP2-java/releases) are required.

    geoip2-x.xx.x.jar
    maxmind-db-x.x.x.jar
    
Version 2.12.0 or later is required. Download the `geoip2-x.xx.x-with-dependancies.zip` file, unzip it, and copy the required jar files to your `[install-dir]/lib/` folder. The rest of the dependencies are either already included in the Wowza Streaming Engine core files or are not required for the module to work.

**Note:** The GeoIP AddOn is not currently compatible with the Dynamic Load Balancing AddOn. The Dynamic Load Balancing AddOn package includes older versions of the MaxMind files that are not compatible with this module. You must remove the MaxMind files provided with the Dynamic Load Balancing AddOn package before installing and configuring the GeoIP AddOn.

Either a subscription to the GeoIP2 [Precision web services](https://dev.maxmind.com/geoip/geoip2/web-services) or the [GeoIP2 Country database](https://www.maxmind.com/en/geoip2-country-database) is required. The free [GeoLite2 Country database](https://dev.maxmind.com/geoip/geoip2/geolite2/) can also be used. The ASN and City level databases will not work with this module.

## Configuration
A Server Listener is required to handle the database and web service connections and the caching of lookups. The module is configured for each application that requires it.

Add the following server listener definition to your server configuration.

| Fully Qualified Class Name |
| --- |
| com.wowza.wms.plugin.geoip.ServerListenerGeoIp |

The following properties can be used to configure the server listener.

Path | Name | Value | type | Comment
--- | --- | --- | --- | ---
/Root/Server/ | geoIpAccountId | 12345 | integer | Precision web services account ID. Default: not set.
/Root/Server/ | geoIpLicenseKey | xyz123 | string | Precision web services license key. Default: not set.
/Root/Server/ | geoIpDatabasePath | ${com.wowza.wms.ConfigHome}/conf/GeoLite2-Country.mmdb | string | Path to GeoIP2 country database. Default: ${com.wowza.wms.ConfigHome}/conf/GeoIP2-Country.mmdb

If credentials are set for the web service then this will be used instead of the local database.

To enable the module, add the following module definition to your application configuration.

Name | Description | Fully Qualified Class Name
--- | --- | ---
ModuleGeoIp | Uses GeoIp lookups to control access. | com.wowza.wms.plugin.geoip.ModuleGeoIp

The following properties can be used to configure the module.

Path | Name | Value | Type | Comment
--- | --- | --- | --- | ---
/Root/Application/ | geoIpAllowedEncoders | Wirecast/,FME/,FMLE/,Wowza GoCoder* | string | Encoder IDs that will not require a geoip lookup. Default: same as for Source Security.
/Root/Application/ | geoIpAllowedIps | 127.0.0.1,192.168.1.\*  | string | IP addresses that won't trigger a geoip lookup. A wildcard can be used. Default: not set.
/Root/Application/ | geoIpCountries | US,GB | string | 2-letter ISO country codes to look for a match. Default: not set.
/Root/Application/ | geoIpMatchAllow | true | Boolean | Blocks connections from specific countries. When set to **false**, **matching** country codes in the list will not be allowed to connect. Default: true.
/Root/Application/ | geoIpDebugLog | true | Boolean | Set to true to enable extra debug logging. Default: false.

## Usage
When a client/player makes a request to the server, a geoip lookup will be performed to determine if the connection is allowed. The ISO country code for the connecting IP address will be checked against the list of country codes and the connection will be allowed or blocked, depending on the configuration of the `geoIpMatchAllow` property. The default configuration is to only allow connections if a match can be found. To allow non-matching and block matching connections, set `geoIpMatchAllow` to `false`.

## More resources
[Wowza Streaming Engine Java API reference documentation](https://www.wowza.com/resources/serverapi/)

[Extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/docs/how-to-extend-wowza-streaming-engine-using-the-wowza-ide)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See the [Wowza Developer Portal](https://www.wowza.com/developer) to learn more about our APIs and SDK.

For additional information about this module, see [Enable geographic locking with a Wowza Streaming Engine server listener and Java module](https://www.wowza.com/docs/how-to-enable-geographic-locking-modulegeoiplock).

## Contact
[Wowza Media Systems, LLC](https://www.wowza.com/contact)

## License
This code is distributed under the [Wowza Public License](/LICENSE.txt).
