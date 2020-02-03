/*
 * This code and all components (c) Copyright 2006 - 2020, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.geoip;

import com.wowza.wms.application.*;

import java.util.ArrayList;
import java.util.List;

import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import com.wowza.util.StringUtils;
import com.wowza.wms.amf.*;
import com.wowza.wms.client.*;
import com.wowza.wms.module.*;
import com.wowza.wms.request.*;
import com.wowza.wms.util.SecurityUtils;
import com.wowza.wms.rtp.model.*;
import com.wowza.wms.rtsp.RTSPRequestMessage;
import com.wowza.wms.rtsp.RTSPResponseMessages;
import com.wowza.wms.server.Server;
import com.wowza.wms.httpstreamer.model.*;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.logging.WMSLoggerIDs;

public class ModuleGeoIp extends ModuleBase
{
	class RTSPActionNotifyListener extends  RTSPActionNotifyBase
	{
		@Override
		public void onDescribe(RTPSession rtpSession, RTSPRequestMessage req, RTSPResponseMessages resp)
		{
			try
			{
				if(debugLog)
					logger.info(String.format("%s::RTSPActionNotifyListener.onDescribe [%s] sessionIp: %s", CLASSNAME, appInstance.getContextStr(), rtpSession.getIp()), category, event);
				if(SecurityUtils.isInIPList(rtpSession.getIp(), allowedIps))
					return;
				if(checkAddress(rtpSession.getIp()))
					return;
				
				rtpSession.rejectSession();
			}
			catch (Exception e)
			{
				logger.error(CLASSNAME + "::RTSPActionNotifyListener.onDescribe [" + appInstance.getContextStr()  +"] error occured.", e);
			}
			catch (Throwable t)
			{
				logger.error(CLASSNAME + "::RTSPActionNotifyListener.onDescribe [" + appInstance.getContextStr()  +"] Throwable error occured.", t);
			}
		}

		@Override
		public void onAnnounce(RTPSession rtpSession, RTSPRequestMessage req, RTSPResponseMessages resp)
		{
			try
			{
				if(debugLog)
					logger.info(String.format("%s::RTSPActionNotifyListener.onAnnounce [%s] sessionIp: %s, userAgent: %s", CLASSNAME, appInstance.getContextStr(), rtpSession.getIp(), rtpSession.getUserAgent()), category, event);
				if(SecurityUtils.isValidFlashVersion(rtpSession.getUserAgent(), allowedEncoders))
					return;
				if(SecurityUtils.isInIPList(rtpSession.getIp(), allowedIps))
					return;
				if(checkAddress(rtpSession.getIp()))
					return;
				
				rtpSession.rejectSession();
			}
			catch (Exception e)
			{
				logger.error(CLASSNAME + "::RTSPActionNotifyListener.onAnnounce [" + appInstance.getContextStr()  +"] error occured.", e);
			}
			catch (Throwable t)
			{
				logger.error(CLASSNAME + "::RTSPActionNotifyListener.onAnnounce [" + appInstance.getContextStr()  +"] Throwable error occured.", t);
			}
		}
	}
	
	public static final String CLASSNAME = "ModuleGeoIP";
	
	private WMSLogger logger;
	private IApplicationInstance appInstance;
	private ServerListenerGeoIp geoIpReader = (ServerListenerGeoIp)Server.getInstance().getProperties().get(ServerListenerGeoIp.PROP_GEOIP_READER);
	private RTSPActionNotifyListener rtspListener = new RTSPActionNotifyListener();
	private String allowedEncodersStr = SecurityUtils.DEFAULT_AUTHFLASHVERSIONS;
	private List<String> allowedEncoders = new ArrayList<String>();
	private String allowedIpsStr = "";
	private List<String> allowedIps;
	private String countriesStr = "*";
	private List<String> countries;
	private boolean matchAllow = true;
	private boolean debugLog = false;
	private String category = WMSLoggerIDs.CAT_application;
	private String event = WMSLoggerIDs.EVT_comment;

	public void onAppStart(IApplicationInstance appInstance)
	{
		logger = WMSLoggerFactory.getLoggerObj(appInstance);
		this.appInstance = appInstance;
		// use ModuleCoreSecurity property if set. 
		allowedEncodersStr = appInstance.getProperties().getPropertyStr("securityPublishValidEncoders", allowedEncodersStr);
		allowedEncodersStr = appInstance.getProperties().getPropertyStr("geoIpAllowedEncoders", allowedEncodersStr);
		allowedEncoders = SecurityUtils.parseValidFlashStrings(allowedEncodersStr);
		allowedIpsStr = appInstance.getProperties().getPropertyStr("geoIpAllowedIps", allowedIpsStr);
		allowedIps = splitPropertyIntoList(allowedIpsStr, true);
		countriesStr = appInstance.getProperties().getPropertyStr("geoIpCountries", countriesStr);
		countries = splitPropertyIntoList(countriesStr, false);
		matchAllow = appInstance.getProperties().getPropertyBoolean("geoIpMatchAllow", matchAllow);
		debugLog = appInstance.getProperties().getPropertyBoolean("geoIpDebugLog", debugLog);
		if(logger.isDebugEnabled())
			debugLog = true;
		
		if(geoIpReader == null)
			logger.warn("ModuleGeoIp.onAppStart [" + appInstance.getContextStr() + "] Build #1 " + ServerListenerGeoIp.PROP_GEOIP_READER + " not set");
		else
			logger.info("ModuleGeoIp.onAppStart [" + appInstance.getContextStr() + "] Build #1 countries: " + countries + ", matchAllow: " + matchAllow + ", allowedIps: " + allowedIps + ", allowedEncoders: " + allowedEncoders, WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
	}

	public void onConnect(IClient client, RequestFunction function, AMFDataList params)
	{
		try
		{
			if(debugLog)
				logger.info(String.format("%s.onConnect [%s] clientIp: %s, flashVer: %s", CLASSNAME, appInstance.getContextStr(), client.getIp(), client.getFlashVer()), category, event);
			if(SecurityUtils.isValidFlashVersion(client.getFlashVer(), allowedEncoders))
				return;
			if(SecurityUtils.isInIPList(client.getIp(), allowedIps))
				return;
			if(checkAddress(client.getIp()))
				return;
			
			client.rejectConnection();
		}
		catch (Exception e)
		{ 
			logger.error(CLASSNAME + ".onConnect [" + appInstance.getContextStr()  +"] error occured.", e);
		}
		catch (Throwable t)
		{
			logger.error(CLASSNAME + ".onConnect [" + appInstance.getContextStr()  +"] Throwable error occured.", t);
		}
	}

	public void onHTTPSessionCreate(IHTTPStreamerSession httpSession)
	{
		try
		{
			if(debugLog)
				logger.info(String.format("%s.onHTTPSessionCreate [%s] sessionIp: %s", CLASSNAME, appInstance.getContextStr(), httpSession.getIpAddress()), category, event);
			if(SecurityUtils.isInIPList(httpSession.getIpAddress(), allowedIps))
				return;
			if(checkAddress(httpSession.getIpAddress()))
				return;
			
			httpSession.rejectSession();
			httpSession.setDeleteSession();
		}
		catch (Exception e)
		{
			logger.error(CLASSNAME + ".onHttpSessionCreate [" + appInstance.getContextStr()  +"] error occured.", e);
		}
		catch (Throwable t)
		{
			logger.error(CLASSNAME + ".onHttpSessionCreate [" + appInstance.getContextStr()  +"] Throwable error occured.", t);
		}
	}

	public void onRTPSessionCreate(RTPSession rtpSession)
	{
		rtpSession.addActionListener(rtspListener);
	}
	
	public boolean checkAddress(String ipAddress)
	{
		boolean valid = !matchAllow;
		CountryResponse response = null;
		try
		{
			response = geoIpReader.getGeoIpCountry(ipAddress);
		}
		catch (GeoIp2Exception e)
		{
			logger.warn(String.format("%s.checkAddress [%s]: %s.", CLASSNAME, appInstance.getContextStr(), e.getMessage()), category, event);
		}
		
		if(response != null)
		{
			Country country = response.getCountry();
			if(country != null)
			{
				if(debugLog)
					logger.info(String.format("%s.checkAddress [%s] ipAddress: %s, country: %s", CLASSNAME, appInstance.getContextStr(), ipAddress, country), category, event);
				String isoCode = country.getIsoCode();
				for(String countryCode : countries)
				{
					if (countryCode.equals("*"))
					{
						if(debugLog)
							logger.info(String.format("%s.checkAddress [%s] ipAddress: %s, isoCode: %s, countryCode: %s is wildcard. returning %b", CLASSNAME, appInstance.getContextStr(), ipAddress, isoCode, countryCode, matchAllow), category, event);
						valid = matchAllow;
						break;
					}
					if (StringUtils.isEmpty(isoCode))
					{
						if(debugLog)
							logger.info(String.format("%s.checkAddress [%s] ipAddress: %s, isoCode: %s is empty. returning %b", CLASSNAME, appInstance.getContextStr(), ipAddress, isoCode, !matchAllow), category, event);
						valid = !matchAllow;
						break;
					}

					if (isoCode.matches(countryCode))
					{
						if(debugLog)
							logger.info(String.format("%s.checkAddress [%s] ipAddress: %s, isoCode: %s, matches countryCode: %s. returning %b", CLASSNAME, appInstance.getContextStr(), ipAddress, isoCode, countryCode, matchAllow), category, event);
						valid = matchAllow;
						break;
					}
				}
			}
		}
		return valid;
	}

	private List<String> splitPropertyIntoList(String listStr, boolean isIpList) {
		ArrayList<String> ret = new ArrayList<String>();
		if (com.wowza.util.StringUtils.isEmpty(listStr))
			return ret;

		String[] items = listStr.split("[,|]");
		for (String item : items) {
			item = item.trim();
			if(isIpList)
			{
				if (SecurityUtils.isValidIPMatchingString(item))
					ret.add(item);
				else
					logger.warn(String.format("ModuleGeoIp.splitPropertyIntoList[%s]: Invalid IP list string '%s'.", appInstance.getContextStr(), item), category, event);
			}
			else
				ret.add(item);
		}
		return ret;
	}
}
