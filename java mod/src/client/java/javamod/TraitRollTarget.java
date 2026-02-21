package javamod;

enum TraitRollTarget {
	BANKER_I("Banker I", "banker_i", 1),
	BANKER_II("Banker II", "banker_ii", 2),
	BANKER_III("Banker III", "banker_iii", 3),
	BANKER_IV("Banker IV", "banker_iv", 4),
	BANKER_V("Banker V", "banker_v", 5),
	MIDAS_I("Midas I", "midas_i", 6),
	MIDAS_II("Midas II", "midas_ii", 7),
	MIDAS_III("Midas III", "midas_iii", 8),
	MIDAS_IV("Midas IV", "midas_iv", 9),
	MIDAS_V("Midas V", "midas_v", 10),
	WIZARD_I("Wizard I", "wizard_i", 11),
	WIZARD_II("Wizard II", "wizard_ii", 12),
	WIZARD_III("Wizard III", "wizard_iii", 13),
	ACCOUNTANT("Accountant", "accountant", 14),
	KING("King", "king", 15);

	final String label;
	final String configKey;
	final int rankScore;

	TraitRollTarget(String label, String configKey, int rankScore) {
		this.label = label;
		this.configKey = configKey;
		this.rankScore = rankScore;
	}
}

