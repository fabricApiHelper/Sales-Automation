package javamod;

enum AutomationMode {
	FISHING("Fishing"),
	MUSEUM("Museum"),
	EGG("Egg"),
	RING_SCRAPPER("Ring Scrapper");

	final String label;

	AutomationMode(String label) {
		this.label = label;
	}
}

