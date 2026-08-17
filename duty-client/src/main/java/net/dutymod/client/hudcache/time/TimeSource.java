package net.dutymod.client.hudcache.time;

public interface TimeSource {
	double get();
	long millis();
	long nanos();
}
