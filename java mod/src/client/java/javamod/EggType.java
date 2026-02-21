package javamod;

import java.util.Locale;
import java.util.regex.Pattern;

enum EggType {
	DEFAULT("Default", 1, false),
	DESERT("Desert", 2, false),
	CACTUS("Cactus", 3, false),
	ICE("Ice", 4, false),
	HELL("Hell", 5, false),
	HEAVENLY("Heavenly", 6, false),
	BRAIN_ROT("Brain-Rot", 7, false),
	DINO("Dino", 1, true),
	PUMPKIN("Pumpkin", 2, true),
	WITCH("Witch", 3, true),
	ROBOT("Robot", 4, true),
	VOID("Void", 5, true),
	CORRUPT("Corrupt", 6, true),
	DRAGON("Dragon", 7, true);

	final String displayName;
	final int slot;
	final boolean secondPage;
	final Pattern chatNameMatcher;

	EggType(String displayName, int slot, boolean secondPage) {
		this.displayName = displayName;
		this.slot = slot;
		this.secondPage = secondPage;
		this.chatNameMatcher = buildChatNameMatcher(displayName);
	}

	private static Pattern buildChatNameMatcher(String displayName) {
		String raw = displayName == null ? "" : displayName;
		String[] tokens = raw.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");

		StringBuilder regex = new StringBuilder("(?i)\\b");
		boolean first = true;
		for (String token : tokens) {
			if (token == null || token.isBlank()) {
				continue;
			}
			if (!first) {
				regex.append("[-\\s_]*");
			}
			regex.append(Pattern.quote(token));
			first = false;
		}
		if (first) {
			regex.append(Pattern.quote(raw.toLowerCase(Locale.ROOT)));
		}
		regex.append("\\b");
		return Pattern.compile(regex.toString());
	}
}

