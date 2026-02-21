package javamod;

enum CookieRollTarget {
	F_PLUS("F+", "f_plus", 1),
	F("F", "f", 2),
	E("E", "e", 3),
	E_PLUS("E+", "e_plus", 4),
	D("D", "d", 5),
	D_PLUS("D+", "d_plus", 6),
	C("C", "c", 7),
	C_PLUS("C+", "c_plus", 8),
	B("B", "b", 9),
	B_PLUS("B+", "b_plus", 10),
	A("A", "a", 11),
	A_PLUS("A+", "a_plus", 12),
	S("S", "s", 13),
	S_PLUS("S+", "s_plus", 14),
	S_DOUBLE_PLUS("S++", "s_double_plus", 15);

	final String label;
	final String configKey;
	final int rankScore;

	CookieRollTarget(String label, String configKey, int rankScore) {
		this.label = label;
		this.configKey = configKey;
		this.rankScore = rankScore;
	}
}

