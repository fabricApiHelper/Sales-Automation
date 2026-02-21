package javamod;

enum TriggerActionMode {
	OFF("Off", "off"),
	WEBHOOK("Webhook", "webhook"),
	AUTO("Auto", "auto"),
	BOTH("Both", "both");

	final String label;
	final String configKey;

	TriggerActionMode(String label, String configKey) {
		this.label = label;
		this.configKey = configKey;
	}
}

