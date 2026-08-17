package net.dutymod.client.hudcache.platform;

public interface Platform {
	boolean isModLoaded(String modId);
	String getModName(String modId);
	String getModId(Class<?> clazz);
	ElementGatherer elementGatherer();
}
