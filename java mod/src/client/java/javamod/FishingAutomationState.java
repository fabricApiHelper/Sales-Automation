package javamod;

final class FishingAutomationState {
	boolean rodIsCast;
	long lastCastTimestamp;
	boolean pausedByEvent;

	void reset() {
		rodIsCast = false;
		lastCastTimestamp = 0L;
		pausedByEvent = false;
	}
}


