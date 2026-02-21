package javamod;

import java.util.Locale;

enum SetupSwapArmor {
	OFF("Off", "off", ""),
	DIRT("Dirt", "dirt", "dirt"),
	SPARTAN("Spartan", "spartan", "spartan"),
	WARDEN("Warden", "warden", "warden"),
	ENCHANTED("Enchanted", "enchanted", "enchanted"),
	ZEUS("Zeus", "zeus", "zeus"),
	HERCULES("Hercules", "hercules", "hercules"),
	HELL("Hell", "hell", "hell"),
	HEAVENLY("Heavenly", "heavenly", "heavenly"),
	OVERSEER("Overseer", "overseer", "overseer");

	final String label;
	final String configKey;
	final String matchLower;

	SetupSwapArmor(String label, String configKey, String matchLower) {
		this.label = label;
		this.configKey = configKey;
		this.matchLower = matchLower == null ? "" : matchLower.toLowerCase(Locale.ROOT);
	}
}


