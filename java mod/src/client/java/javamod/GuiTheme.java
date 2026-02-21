package javamod;

enum GuiTheme {
	DARK("Dark", "dark"),
	LIGHT("Light", "light");

	final String label;
	final String configKey;

	GuiTheme(String label, String configKey) {
		this.label = label;
		this.configKey = configKey;
	}
}


