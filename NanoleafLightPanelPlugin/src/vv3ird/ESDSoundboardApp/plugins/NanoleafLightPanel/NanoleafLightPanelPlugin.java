package vv3ird.ESDSoundboardApp.plugins.NanoleafLightPanel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.rowak.Aurora;
import io.github.rowak.Setup;
import io.github.rowak.StatusCodeException;
import io.github.rowak.StatusCodeException.UnauthorizedException;
import vv3ird.ESDSoundboardApp.AudioApp;
import vv3ird.ESDSoundboardApp.config.AppConfiguration;
import vv3ird.ESDSoundboardApp.config.Sound;
import vv3ird.ESDSoundboardApp.ngui.pages.Page;
import vv3ird.ESDSoundboardApp.ngui.plugins.JPluginConfigurationPanel;
import vv3ird.ESDSoundboardApp.plugins.NanoleafLightPanel.pages.JNanoleafOptionsPanel;
import vv3ird.ESDSoundboardApp.plugins.data.Plugin;
import vv3ird.ESDSoundboardApp.plugins.data.SoundPluginMetadata;
import vv3ird.ESDSoundboardApp.plugins.data.SoundPluginMetadataTemplate;
import vv3ird.ESDSoundboardApp.plugins.data.SoundPluginMetadataTemplate.TYPE;
import vv3ird.ESDSoundboardApp.plugins.listener.PlaybackListener;
import vv3ird.ESDSoundboardApp.plugins.listener.PluginListener;

public class NanoleafLightPanelPlugin implements Plugin, PlaybackListener {
	
	private static Logger logger = LogManager.getLogger(NanoleafLightPanelPlugin.class);

	List<PluginListener> listener = new LinkedList<>();
	
	private Map<String, Aurora> auroras = null;
	
	private String instanceMac = null;
	
	private String instanceName = null;
	
	public static final String API_LEVEL = "v1";

	public NanoleafLightPanelPlugin() {
		
	}

	public NanoleafLightPanelPlugin(String mac, Aurora aurora) {
		auroras = new HashMap<String, Aurora>();
		auroras.put(mac, aurora);
		instanceMac = mac;
		this.instanceName = aurora.getName();
	}

	public void init() throws UnauthorizedException, StatusCodeException {
		auroras = new HashMap<String, Aurora>();
		instanceMac = null;
		logger.debug("Initializing " + getDisplayName());
		if (isEnabled() && isConfigured()) {
			logger.debug(getDisplayName() + " is enabled and configured connection to aurora");
			try {
				List<InetSocketAddress> auroraInetAdresses = getAuroras();
				for (InetSocketAddress inetSocketAddress : auroraInetAdresses) {
					logger.debug("Aurora in network: " + inetSocketAddress.toString());
					String mac = getMac(inetSocketAddress.getHostName());
					if(AudioApp.getConfig("nanoleaf.accessToken." + mac) != null) {
						logger.debug("Aurora is configured in the app, connecting...");
						Aurora aurora = new Aurora(inetSocketAddress, API_LEVEL, AudioApp.getConfig("nanoleaf.accessToken." + mac));
						logger.debug("Max Brightness for " + aurora.getName() + ": " + + aurora.state().getMaxBrightness());
						logger.debug("Min Brightness for " + aurora.getName() + ": " + + aurora.state().getMinBrightness());
						auroras.put(mac, aurora);
						if(instanceMac == null)
							instanceMac =  mac;
						logger.debug("Aurora \"" + aurora.getName() + "\"is connected");
					}
				}
			} catch (IOException e) {
				logger.error("Error connection to aurora");
				logger.error(e);
			}
		}
	}

	public List<InetSocketAddress> getAuroras() throws SocketTimeoutException, UnknownHostException, IOException {
		List<InetSocketAddress> auroras = Setup.findAuroras(5_000);
		return auroras;
	}

	@Override
	public String getDisplayName() {
		return "Nanoleaf Plugin " + ( instanceName != null ? "(" + instanceName + ")" : "");
	}

	@Override
	public JPluginConfigurationPanel getConfigurationPanel() {
		return new JNanoleafOptionsPanel(this);
	}

	@Override
	public boolean isEnabled() {
		return "true".equalsIgnoreCase(AudioApp.getConfig("nanoleaf.enabled"));
	}

	@Override
	public void enable() {
		AudioApp.addConfig("nanoleaf.enabled", "true");
	}

	@Override
	public void disable() {
		AudioApp.addConfig("nanoleaf.enabled", "false");
	}

	@Override
	public boolean isConfigured() {
		String[] accessToken = AudioApp.getConfigKeys("nanoleaf.accessToken");
		logger.debug("Access token:");
		for (String string : accessToken) {
			logger.debug(string);
		}
		return accessToken != null && accessToken.length > 0;
	}

	public boolean isConnected() {
		try {
			if (auroras.size() > 0) {
				auroras.get(instanceMac).state().getBrightness();
				return true;
			}
		} catch (StatusCodeException e) {
			logger.error("Error connection to aurora " + auroras.get(instanceMac).getName() + "(" + auroras.get(instanceMac).getHostName() + ":" + auroras.get(instanceMac).getPort() + ")");
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean isPlaybackListener() {
		return true;
	}

	@Override
	public void addPluginListener(PluginListener pl) {
		listener.add(pl);
	}

	@Override
	public void removePluginListener(PluginListener pl) {
		listener.remove(pl);
	}

	@Override
	public boolean hasSoundPluginMetadataTemplates() {
		return true;
	}

	@Override
	public List<SoundPluginMetadataTemplate> getSoundPluginMetadataTemplates() {
		List<String> effectsList = getListForMetadata("Effect", instanceMac, null);
		SoundPluginMetadataTemplate effect = new SoundPluginMetadataTemplate(
				NanoleafLightPanelPlugin.class.getCanonicalName(), instanceMac, "NanoleafLightPanelPlugin", TYPE.LIST,
				"Effect", effectsList, 0, 0, "", 0);

		SoundPluginMetadataTemplate brightness = new SoundPluginMetadataTemplate(
				NanoleafLightPanelPlugin.class.getCanonicalName(), instanceMac, "NanoleafLightPanelPlugin", TYPE.INT,
				"Brightness", null, 0, 100, "", 70);

		SoundPluginMetadataTemplate switchOnOff = new SoundPluginMetadataTemplate(
				NanoleafLightPanelPlugin.class.getCanonicalName(), instanceMac, "NanoleafLightPanelPlugin", TYPE.INT,
				"Switch On/Off", null, 0, 1, "", 1);

		/*
		SoundPluginMetadataTemplate mac = new SoundPluginMetadataTemplate(NanoleafLightPanelPlugin.class.getCanonicalName(), instanceMac,
				"NanoleafLightPanelPlugin", TYPE.LIST, "MAC", macList);*/
		
		List<SoundPluginMetadataTemplate> templates = new LinkedList<>();
		templates.add(effect);
		templates.add(brightness);
		templates.add(switchOnOff);
		//templates.add(mac);
		return templates;
	}

	@Override
	public List<String> getListForMetadata(SoundPluginMetadata metadata) {
		return getListForMetadata(metadata.key, metadata.instanceId, metadata.valueString);
	}
	
	public List<String> getListForMetadata(String metadata, String instanceId, String value) {
		if (isEnabled() && isConfigured() && this.auroras.size() > 0) {
			if ("Effect".equalsIgnoreCase(metadata)) {
				try {
					return Arrays.asList(auroras.get(instanceId).effects().getEffectsList());
				} catch (StatusCodeException e) {
					e.printStackTrace();

				}
			}
			else if ("MAC".equalsIgnoreCase(metadata)) {
				return new ArrayList<>(auroras.keySet());
			}
		}
		// Instance not online return value only
		List<String> list = new ArrayList<>(1);
		list.add(value);
		return list;
	}

	//

	@Override
	public void onStart(Sound s) {
		logger.debug(s);
		if (isEnabled() && isConfigured() && isConnected()) {
			List<SoundPluginMetadata> metadata = s.getMetadataFor(NanoleafLightPanelPlugin.class.getCanonicalName());
			Collections.sort(metadata, new Comparator<SoundPluginMetadata>() {
				@Override
				public int compare(SoundPluginMetadata o1, SoundPluginMetadata o2) {
					return o1.key.compareTo(o2.key);
				}
			});
			if(metadata != null && metadata.size() > 0) {
				for (SoundPluginMetadata metad : metadata) {
					if(metad.key.equals("Switch On/Off")) {
						try {
							auroras.get(metad.instanceId != null && metad.instanceId != null ? metad.instanceId : instanceMac).state().setOn(metad.valueInt != 0);
							logger.debug("Switching " + metad.pluginName + " " + (metad.valueInt != 0 ? "on" : "off"));
						} catch (StatusCodeException e) {
							logger.error("Error switching aurora on/off: " + metad.valueInt);
							logger.error(e);
//							e.printStackTrace();
						}
					}
					else if(metad.key.equals("Effect")) {
						try {
							auroras.get(metad.instanceId != null && metad.instanceId != null ? metad.instanceId : instanceMac).effects().setEffect(metad.valueString);
						} catch (StatusCodeException e) {
							logger.error("Error setting new effect: " + metad.valueString);
							logger.error(e);
//							e.printStackTrace();
						}
					}
					else if(metad.key.equals("Brightness")) {
						try {
							auroras.get(metad.instanceId != null && metad.instanceId != null ? metad.instanceId : instanceMac).state().setBrightness(metad.valueInt);
							logger.debug("Setting brightness of " + metad.pluginName + " to: " + metad.valueInt);
						} catch (StatusCodeException e) {
							logger.error("Error changing brightness: " + metad.valueInt);
							logger.error(e);
//							e.printStackTrace();
						}
					}
				}
			}
		}
	}

	@Override
	public void onStop(Sound s) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isMultiInstance() {
		return true;
	}

	@Override
	public List<Plugin> getInstances() {
		List<Plugin> instances = this.auroras.keySet().stream()
				.map(m -> new NanoleafLightPanelPlugin(m, auroras.get(m)) {
					@Override
					public boolean isMultiInstance() {
						return false;
					}
				}).collect(Collectors.toList());
		return instances;
	}

	public static String getMac(String ip) {
		Pattern macpt = null;
		// Find OS and set command according to OS
		String OS = System.getProperty("os.name").toLowerCase();
		String[] cmd;
		if (OS.contains("win")) {
			// Windows
			macpt = Pattern.compile("[0-9a-f]+-[0-9a-f]+-[0-9a-f]+-[0-9a-f]+-[0-9a-f]+-[0-9a-f]+");
			String[] a = { "arp", "-a", ip };
			cmd = a;
		} else {
			// Mac OS X, Linux
			macpt = Pattern.compile("[0-9a-f]+:[0-9a-f]+:[0-9a-f]+:[0-9a-f]+:[0-9a-f]+:[0-9a-f]+");
			String[] a = { "arp", ip };
			cmd = a;
		}
		try {
			// Run command
			Process p = Runtime.getRuntime().exec(cmd);
			p.waitFor();
			// read output with BufferedReader
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = reader.readLine();
			// Loop trough lines
			while (line != null) {
				Matcher m = macpt.matcher(line);
				// when Matcher finds a Line then return it as result
				if (m.find()) {
					logger.debug("Found");
					logger.debug("MAC: " + m.group(0));
					return m.group(0).replace("-", ":");
				}
				line = reader.readLine();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// Return empty string if no MAC is found
		return "";
	}

}
