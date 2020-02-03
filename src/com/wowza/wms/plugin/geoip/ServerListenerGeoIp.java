/*
 * This code and all components (c) Copyright 2006 - 2020, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.geoip;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.GeoIp2Provider;
import com.maxmind.geoip2.WebServiceClient;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.wowza.util.StringUtils;
import com.wowza.util.SystemUtils;
import com.wowza.wms.bootstrap.Bootstrap;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.server.IServer;
import com.wowza.wms.server.IServerNotify2;

public class ServerListenerGeoIp implements IServerNotify2
{
	class MaxmindResponse implements Comparable<MaxmindResponse>
	{
		long lastAccess = -1;
		CountryResponse countryResponse = null;

		public MaxmindResponse(CountryResponse countryResponse, long lastAccess)
		{
			this.countryResponse = countryResponse;
			this.lastAccess = lastAccess;
		}

		@Override
		public int compareTo(MaxmindResponse other)
		{
			return Long.compare(lastAccess, other.lastAccess);
		}
	}

	public static final String PROP_GEOIP_READER = "ServerListenerGeoIp";
	public static final int CACHE_CAPACITY = 4096;

	private ReadWriteLock lock = new ReentrantReadWriteLock();
	private Lock readLock = lock.readLock();
	private Lock writeLock = lock.writeLock();
	private GeoIp2Provider provider;
	private int accountId = -1;
	private String licenseKey = null;
	private File dbFile = null;
	private long dbLastModified = -1;
	private Timer checkTimer = null;
	private long checkInterval = 1000;
	private long checkTimeout = checkInterval * 30;
	private long lastLookup = -1;
	private boolean shuttingDown = false;
	private ConcurrentMap<String, MaxmindResponse> cache = new ConcurrentHashMap<String, MaxmindResponse>(CACHE_CAPACITY);

	@Override
	public void onServerConfigLoaded(IServer server)
	{
		accountId = server.getProperties().getPropertyInt("geoIpAccountId", accountId);
		licenseKey = server.getProperties().getPropertyStr("geoIpLicenseKey", licenseKey);
		String filePath = server.getProperties().getPropertyStr("geoIpDatabasePath", Bootstrap.getServerHome(Bootstrap.CONFIGHOME) + File.separatorChar + "conf" + File.separatorChar + "GeoIP2-Country.mmdb");

		dbFile = new File(SystemUtils.expandEnvironmentVariables(filePath));
		if (!dbFile.exists() && dbFile.isFile())
		{
			dbFile = null;
		}
	}

	@Override
	public void onServerCreate(IServer server)
	{
		server.getProperties().setProperty(PROP_GEOIP_READER, this);
	}

	@Override
	public void onServerInit(IServer server)
	{
		checkTimer = new Timer();
		checkTimer.schedule(new TimerTask()
		{

			@Override
			public void run()
			{
				writeLock.lock();
				try
				{
					if (provider instanceof WebServiceClient)
					{
						if (lastLookup + checkTimeout > System.currentTimeMillis())
						{
							((WebServiceClient)provider).close();
							provider = null;
						}
					}
					else if (dbFile.exists() && dbFile.lastModified() > dbLastModified)
					{
						if (provider instanceof DatabaseReader)
						{
							((DatabaseReader)provider).close();
							provider = null;
							cache.clear();
						}
						dbLastModified = dbFile.lastModified();
					}

					if (cache.size() >= CACHE_CAPACITY)
					{
						List<MaxmindResponse> cachedResponses = new ArrayList<MaxmindResponse>(cache.values());
						Collections.sort(cachedResponses);
						for(MaxmindResponse cachedResponse : cachedResponses)
						if (cache.size() > CACHE_CAPACITY * 0.75)
						{
							cache.values().remove(cachedResponse);
						}
					}
				}
				catch (IOException e)
				{
					WMSLoggerFactory.getLogger(getClass()).error("ServerListenerGeoIp::CheckTimer.run error occured", e);
				}
				finally
				{
					writeLock.unlock();
				}
			}
		}, 0, checkInterval);
	}

	@Override
	public void onServerShutdownStart(IServer server)
	{
		writeLock.lock();
		try
		{
			checkTimer.cancel();
			if (provider != null)
				((Closeable)provider).close();
		}
		catch (IOException e)
		{
			// ignore
		}
		finally
		{
			shuttingDown = true;
			provider = null;
			writeLock.unlock();
		}
	}

	@Override
	public void onServerShutdownComplete(IServer server)
	{
		// no-op
	}

	public CountryResponse getGeoIpCountry(String ipAddress) throws GeoIp2Exception
	{
		MaxmindResponse response = null;
		readLock.lock();
		try
		{
			response = cache.get(ipAddress);
			if (!shuttingDown)
			{
				if (response == null)
				{
					if (provider == null)
					{
						readLock.unlock();
						writeLock.lock();
						try
						{
							if (provider == null && !shuttingDown)
							{
								try
								{
									if (accountId != -1 && !StringUtils.isEmpty(licenseKey))
									{
										provider = new WebServiceClient.Builder(accountId, licenseKey).build();
									}
									else if (dbFile.exists())
									{
										provider = new DatabaseReader.Builder(dbFile).build();
									}
								}
								catch (Exception e)
								{
									WMSLoggerFactory.getLogger(getClass()).error("ServerListenerGeoIp.checkProvider error occured", e);
								}
							}
						}
						catch (Exception e)
						{
							WMSLoggerFactory.getLogger(getClass()).error("ServerListenerGeoIp.checkProvider2 error occured", e);
						}
						finally
						{
							readLock.lock();
							writeLock.unlock();
						}
					}
					if (provider != null)
					{

						InetAddress inetAddress = InetAddress.getByName(ipAddress);
						CountryResponse countryResponse = provider.country(inetAddress);

						lastLookup = System.currentTimeMillis();
						response = new MaxmindResponse(countryResponse, lastLookup);
						cache.putIfAbsent(ipAddress, response);
					}
				}

			}
		}
		catch (IOException e)
		{
			WMSLoggerFactory.getLogger(getClass()).error("ServerListenerGeoIp.getGeoIpCountry error occured", e);
		}
		finally
		{
			readLock.unlock();
		}

		if (response != null)
		{
			response.lastAccess = System.currentTimeMillis();
			return response.countryResponse;
		}

		return null;
	}
}
