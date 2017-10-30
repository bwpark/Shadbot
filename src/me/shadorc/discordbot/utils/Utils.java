package me.shadorc.discordbot.utils;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.json.JSONArray;

import me.shadorc.discordbot.Shadbot;
import me.shadorc.discordbot.command.AbstractCommand;
import me.shadorc.discordbot.data.Config;
import sx.blah.discord.util.EmbedBuilder;

public class Utils {

	/**
	 * @return double representing process CPU load percentage value, Double.NaN if not available
	 */
	public static double getProcessCpuLoad() {
		double cpuLoad;
		try {
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			ObjectName name = ObjectName.getInstance("java.lang:type=OperatingSystem");
			AttributeList list = mbs.getAttributes(name, new String[] { "ProcessCpuLoad" });

			if(list.isEmpty()) {
				return Double.NaN;
			}

			Attribute att = (Attribute) list.get(0);
			Double value = (Double) att.getValue();

			if(value == -1.0) {
				return Double.NaN;
			}

			cpuLoad = value * 100d;
		} catch (InstanceNotFoundException | ReflectionException | MalformedObjectNameException err) {
			cpuLoad = Double.NaN;
		}

		return cpuLoad;
	}

	/**
	 * @return the default embed builder (with author icon and color)
	 */
	public static EmbedBuilder getDefaultEmbed() {
		return new EmbedBuilder()
				.withAuthorIcon(Shadbot.getClient().getOurUser().getAvatarURL())
				.withColor(Config.BOT_COLOR);
	}

	/**
	 * @param command - the command
	 * @return the default command embed builder (with author name, author icon and color)
	 */
	public static EmbedBuilder getDefaultEmbed(AbstractCommand command) {
		return Utils.getDefaultEmbed()
				.withAuthorName("Help for " + command.getFirstName() + " command");
	}

	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		return map.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(value1, value2) -> value1,
						LinkedHashMap::new));
	}

	/**
	 * @param array - JSONArray to convert
	 * @return List<String> containing array elements
	 */
	public static List<String> convertToStringList(JSONArray array) {
		if(array == null) {
			return null;
		}
		List<String> list = new ArrayList<>();
		for(int i = 0; i < array.length(); i++) {
			list.add(array.getString(i));
		}
		return list;
	}

	/**
	 * @param array - JSONArray to convert
	 * @return List<Long> containing array elements
	 */
	public static List<Long> convertToLongList(JSONArray array) {
		if(array == null) {
			return null;
		}
		List<Long> list = new ArrayList<>();
		for(int i = 0; i < array.length(); i++) {
			list.add(array.getLong(i));
		}
		return list;
	}

	public static boolean allEqual(Object key, Object... objs) {
		for(Object obj : objs) {
			if(!obj.equals(key)) {
				return false;
			}
		}
		return true;
	}

	public static void sleep(long duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException ignored) {
		}
	}
}