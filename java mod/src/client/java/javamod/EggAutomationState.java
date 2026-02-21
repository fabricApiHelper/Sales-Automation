package javamod;

final class EggAutomationState {
	boolean pausedByEvent;
	long nextEggWatchdogAt;
	long nextEggChatScanAt;
	long eggChatWindowStartAt;
	long eggChatWindowEndAt;
	EggType eggChatWindowEggType;

	void reset() {
		pausedByEvent = false;
		nextEggWatchdogAt = 0L;
		nextEggChatScanAt = 0L;
		eggChatWindowStartAt = 0L;
		eggChatWindowEndAt = 0L;
		eggChatWindowEggType = null;
	}
}


