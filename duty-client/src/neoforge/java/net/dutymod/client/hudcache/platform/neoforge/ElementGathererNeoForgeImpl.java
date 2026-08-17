package net.dutymod.client.hudcache.platform.neoforge;

//? neoforge {

import net.dutymod.client.hudcache.CachedElement;
import net.dutymod.client.hudcache.Constants;
import net.dutymod.client.hudcache.VersionCompatUtil;
import net.dutymod.client.hudcache.compat.neoforge.EventBusAccessor;
import net.dutymod.client.mixin.hudcache.neoforge.HudAccessor;
import net.dutymod.client.mixin.hudcache.neoforge.GuiLayerManagerAccessor;
import net.dutymod.client.hudcache.platform.ElementGatherer;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public class ElementGathererNeoForgeImpl extends ElementGatherer {

	@Override
	public void gatherImpl(Map<String, CachedElement> map) {
		gatherEvent(RenderGuiEvent.Pre.class, map);

		var mc = Minecraft.getInstance();
		//? >=26.2 {
		/*var hud = mc.gui.hud;
		*///? } else {
		var hud = mc.gui;
		//? }
		var layerManager = ((HudAccessor)hud).getLayerManager();
		var layers = ((GuiLayerManagerAccessor) layerManager).getLayers();
		for (var layer : layers) {
			var name = VersionCompatUtil.stringValueOf(layer.name());
			map.putIfAbsent(name, new CachedElement(name));
		}

		gatherEvent(RenderGuiEvent.Post.class, map);
	}

	private <T extends Event> void gatherEvent(Class<T> event, Map<String, CachedElement> map) {
		var bus = (EventBus) NeoForge.EVENT_BUS;
		var listeners = EventBusAccessor.getListenerList(bus, event).getListeners();
		for (var listener : listeners) {
			var modid = EventListenerHelper.tryGetModId(listener).orElse(Constants.UNKNOWN_ELEMENTS);
			map.putIfAbsent(modid, new CachedElement(modid));
		}
	}
}
//?}
