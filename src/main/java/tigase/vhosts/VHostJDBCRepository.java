/*
 * VHostJDBCRepository.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */



package tigase.vhosts;

//~--- non-JDK imports --------------------------------------------------------

import tigase.db.comp.UserRepoRepository;

import tigase.util.DNSEntry;
import tigase.util.DNSResolver;
import tigase.util.TigaseStringprepException;

import tigase.xmpp.BareJID;

//~--- JDK imports ------------------------------------------------------------

import java.net.UnknownHostException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;

/**
 * This implementation stores virtual domains in the UserRepository database. It
 * loads initial settings and virtual hosts from the configuration file and then
 * loads more vhosts from the database. Virtual domains from the database can
 * overwrite (disable) vhosts loaded from the configuration file.
 *
 * This implementation keeps all virtual hosts and their parameters in a single
 * database field. This might not be very efficient if you want to manager big
 * number of virtual domains. It is sufficient for hundreds of vhosts. If you
 * need thousands of VHosts support I advice to implement this storage in more
 * efficient way using separate database tables instead of UserRepository.
 * Please note there is a limit of about 300 vhosts if you use Derby database.
 *
 *
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @since Nov 29, 2008 2:32:48 PM
 */
public class VHostJDBCRepository
				extends UserRepoRepository<VHostItem> {
	/**
	 * Configuration option allowing specify default IP to which VHost should resolve
	 * vhost-man/dns-def-ip=
	 */
	public static final String DNS_DEF_IP_PROP_KEY = "dns-def-ip";

	/** Field description */
	public static String DNS_DEF_IP_PROP_VAL = null;

	/**
	 * Configuration option allowing specify default hostname to which VHost should resolve
	 * vhost-man/dns-srv-def-addr=
	 */
	public static final String DNS_SRV_DEF_ADDR_PROP_KEY = "dns-srv-def-addr";

	/** Field description */
	public static String DNS_SRV_DEF_ADDR_PROP_VAL = null;

	/**
	 * Configuration option allowing specify default maximum number that user can register
	 * in service
	 *
	 * vhost-man/domains-per-user-limit=
	 */
	public static final String DOMAINS_PER_USER_LIMIT_PROP_KEY = "domains-per-user-limit";

	/** Field description */
	public static final int DOMAINS_PER_USER_LIMIT_PROP_VAL = 25;
	private static final Logger log                         =
		Logger.getLogger(VHostJDBCRepository.class.getName());

	//~--- fields ---------------------------------------------------------------

	// ~--- fields ---------------------------------------------------------------
	private String def_ip_address    = null;
	private String def_srv_address   = null;
	private int max_domains_per_user = DOMAINS_PER_USER_LIMIT_PROP_VAL;

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String getConfigKey() {
		return VHostRepoDefaults.getConfigKey();
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getDefaultPropetyItems() {
		return VHostRepoDefaults.getDefaultPropetyItems();
	}

	/**
	 * Method description
	 *
	 *
	 * @param defs
	 * @param params
	 */
	@Override
	public void getDefaults(Map<String, Object> defs, Map<String, Object> params) {

		// Something to initialize database with, in case it is empty
		// Otherwise the server would not work at all with empty Items database
		super.getDefaults(defs, params);
		DNS_SRV_DEF_ADDR_PROP_VAL = DNSResolver.getDefaultHostname();
		try {
			DNS_DEF_IP_PROP_VAL = DNSResolver.getHostIP(DNSResolver.getDefaultHostname());
		} catch (Exception e) {
			DNS_DEF_IP_PROP_VAL = DNSResolver.getDefaultHostname();
		}
		defs.put(DNS_SRV_DEF_ADDR_PROP_KEY, DNS_SRV_DEF_ADDR_PROP_VAL);
		defs.put(DNS_DEF_IP_PROP_KEY, DNS_DEF_IP_PROP_VAL);
		defs.put(DOMAINS_PER_USER_LIMIT_PROP_KEY, DOMAINS_PER_USER_LIMIT_PROP_VAL);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public VHostItem getItemInstance() {
		return VHostRepoDefaults.getItemInstance();
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String getItemsListPKey() {
		return VHostRepoDefaults.getItemsListPKey();
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String getPropertyKey() {
		return VHostRepoDefaults.getPropertyKey();
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public BareJID getRepoUser() {
		return VHostRepoDefaults.getRepoUser();
	}

	//~--- set methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param properties
	 */
	@Override
	public void setProperties(Map<String, Object> properties) {

		// Let's load items from configuration first. Later we can overwrite
		// them with items settings in the database.
		super.setProperties(properties);
		def_srv_address = (String) properties.get(DNS_SRV_DEF_ADDR_PROP_KEY);
		if ((def_srv_address != null) &&!def_srv_address.endsWith(".")) {
			def_srv_address = def_srv_address + ".";
		}
		def_ip_address       = (String) properties.get(DNS_DEF_IP_PROP_KEY);
		max_domains_per_user = (Integer) properties.get(DOMAINS_PER_USER_LIMIT_PROP_KEY);
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Performs validation of given VHostItem
	 *
	 * @param item VHostItem which should be validated
	 * @return
	 */
	@Override
	public String validateItem(VHostItem item) {
		if ((item.getVhost() == null) || (item.getVhost().getDomain() == null) ||
				item.getVhost().getDomain().isEmpty()) {
			return "Domain name not specified";
		}

		int vhost_count = 0;

		for (VHostItem it : allItems()) {
			if (it.isOwner(item.getOwner())) {
				++vhost_count;
			}
		}
		if (vhost_count >= max_domains_per_user) {
			return "Maximum number of domains exceeded for the user! Current number is: " +
						 vhost_count;
		}
		if (System.getProperty("vhost-disable-dns-check") != null) {
			return null;
		}

		// verify all SRV DNS records
		try {
			DNSEntry[] entries = DNSResolver.getHostSRV_Entries(item.getKey());

			if (entries != null) {
				for (DNSEntry dNSEntry : entries) {
					log.finest("Validating DNS SRV settings ('" + dNSEntry +
										 "') for the given hostname: " + item.getKey() + " (defaults: " +
										 def_ip_address + ", " + def_srv_address);
					if (Arrays.asList(dNSEntry.getIps()).contains(def_ip_address) ||
							def_srv_address.equals(dNSEntry.getDnsResultHost())) {

						// configuration is OK
						return null;
					}
				}

				return "Incorrect DNS SRV settings" + Arrays.asList(entries);
			}
		} catch (UnknownHostException ex) {

			// Ignore, maybe simply IP address is set in DNS
		}

		// verify DNS records
		try {
			String[] ipAddress = DNSResolver.getHostIPs(item.getKey());

			if (ipAddress != null) {
				if (Arrays.asList(ipAddress).contains(def_ip_address)) {
					return null;
				} else {
					return "Incorrect IP address: '" + Arrays.asList(ipAddress) +
								 "' found in DNS for the given host: " + item.getKey();
				}
			} else {
				return "No DNS settings found for given host: " + item.getKey();
			}
		} catch (UnknownHostException ex1) {
			return "There is no DNS settings for given host: " + item.getKey();
		}
	}

	/**
	 * Simple verification of VHost validation
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		Map<String, Object> props  = new HashMap<String, Object>();
		Map<String, Object> params = new HashMap<String, Object>();
		VHostJDBCRepository repo   = new VHostJDBCRepository();

		repo.getDefaults(props, params);
		props.put(DNS_SRV_DEF_ADDR_PROP_KEY, "tigase.me");
		props.put(DOMAINS_PER_USER_LIMIT_PROP_KEY, 50);
		repo.setProperties(props);

		VHostItem domain = null;

		try {
			domain = new VHostItem("tigase.im");
		} catch (TigaseStringprepException ex) {
			Logger.getLogger(VHostJDBCRepository.class.getName()).log(Level.SEVERE, null, ex);
		}
		System.out.println("repo.validateItem( domain ) :: " + repo.validateItem(domain));
	}
}


//~ Formatted in Tigase Code Convention on 13/02/28
