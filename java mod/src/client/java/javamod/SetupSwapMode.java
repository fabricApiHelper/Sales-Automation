package javamod;

enum SetupSwapMode {
	GEM("Gem", "gem"),
	STARS("Stars", "stars"),
	MONEY("Money", "money");

	final String label;
	final String configKey;

	SetupSwapMode(String label, String configKey) {
		this.label = label;
		this.configKey = configKey;
	}
}


