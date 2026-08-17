package net.dutymod.client.hudcache.platform.neoforge;

//? neoforge {

import net.dutymod.client.hudcache.Constants;
import net.dutymod.client.hudcache.platform.ElementGatherer;
import net.dutymod.client.hudcache.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class NeoforgePlatform implements Platform {
	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public String getModName(String modId) {
		return FMLLoader.getCurrent().getLoadingModList().getModFileById(modId).getMods().get(0).getDisplayName();
	}

	@Override
	public String getModId(Class<?> clazz) {
		var mod = clazz.getModule().getName();
		return mod == null ? Constants.UNKNOWN_ELEMENTS : mod;
	}

	@Override
	public ElementGatherer elementGatherer() {
		return new ElementGathererNeoForgeImpl();
	}
}
//?}
