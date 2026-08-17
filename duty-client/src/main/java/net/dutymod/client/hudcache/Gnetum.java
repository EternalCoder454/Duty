package net.dutymod.client.hudcache;

import net.dutymod.client.hudcache.platform.Platform;

import net.dutymod.client.hudcache.time.GlfwTimeSource;
import net.dutymod.client.hudcache.time.TimeSource;
import net.dutymod.client.hudcache.util.AnyBooleanValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? >=1.21.10 {
import org.joml.Matrix3x2f;
//? } else {
/*import org.joml.Matrix4f;
*///? }

import java.util.Locale;

//? fabric {
/*import net.dutymod.client.hudcache.platform.fabric.FabricPlatform;
*///?} neoforge {
//?}

public class Gnetum {
	public static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
	public static final FpsCounter FPS_COUNTER = new FpsCounter();

	private static final TimeSource time = new GlfwTimeSource();
	private static Framebuffers framebuffers;
	public static int pass = 1;
	public static boolean rendering;
	public static boolean flushing;
	public static boolean renderingGuiInjection;
	public static GnetumConfig config;
	public static CachedElement currentElement;

	//? >=1.21.10 {
	public static final Matrix3x2f lastGuiMatrix = new Matrix3x2f();
	//?} else {
	/*public static final Matrix4f lastGuiMatrix = new Matrix4f();
	 *///?}

	/**
	 * Set by the loader entry point before {@link #init()}.
	 *
	 * <p>Upstream constructs this inline, picking the implementation with a stonecutter branch --
	 * which puts a loader's class name in a file that otherwise names no loader. Duty splits on
	 * exactly that line ({@code checkMainIsLoaderNeutral}), so the choice moves to the only place
	 * allowed to make it. Nothing reads this before the entry point runs: the HUD cannot render
	 * before mods are constructed.
	 */
	private static Platform PLATFORM;

	public static void setPlatform(Platform platform) {
		PLATFORM = platform;
	}

	public static void init() {
		GnetumConfig.reload();
		config.save();
	}

	public static void nextPass() {
		if (pass == 0 && FPS_COUNTER.belowMax()) {
			pass++;
			finishAllPasses();
		}
		else if (pass > 0) {
			pass++;
		}
		if (pass > config.getNumberOfPasses()) {
			if (FPS_COUNTER.belowMax()) {
				pass = 1;
				finishAllPasses();
			}
			else {
				pass = 0;
			}
		}
	}

	private static void finishAllPasses() {
		Distributor.resolve();
		framebuffers().swapFramebuffers();
		HudDeltaTracker.reset();
	}

	public static void checkForPoseCatchUp(GuiGraphicsExtractor guiGraphics) {
		var pose = guiGraphics.pose();
		//? >= 1.21.10 {
		if (!pose.equals(lastGuiMatrix, 0.01F)) {
			lastGuiMatrix.set(pose);
			Gnetum.framebuffers().markForCatchUp();
		}
		//?} else {
		/*if (!pose.last().pose().equals(lastGuiMatrix, 0.01F)) {
			lastGuiMatrix.set(pose.last().pose());
			Gnetum.framebuffers().markForCatchUp();
		}
        *///?}
	}

	public static String getFpsString() {
		return String.format(Locale.ROOT, "HUD: %d fps T: %s (%d passes)", Gnetum.FPS_COUNTER.getFps(), Gnetum.config.getMaxFps() == Constants.UNLIMITED_FPS ? "inf" : Gnetum.config.getMaxFps(), Gnetum.config.getNumberOfPasses());
	}

	public static boolean isCurrentElementForceCached() {
		if (currentElement == null) {
			return false;
		}
		return currentElement.enabled.value == AnyBooleanValue.ON;
	}

	public static void disableCachingForCurrentElement(String reason) {
		if (currentElement == null) {
			LOGGER.error("No current element to disable");
			return;
		}
		disableCachingForElement(currentElement, reason);
	}

	public static void disableCachingForElement(CachedElement element, String reason) {
		if (element == null) return;
		if (element.enabled.get() && element.enabled.value == AnyBooleanValue.AUTO) {
			LOGGER.info("Disabling caching for element {}. Reason: {}", element.name, reason);
			element.enabled.defaultValue = false;
			framebuffers().dropCurrentFrame();
		}
	}

	public static CachedElement getElement(Identifier name) {
		return getElement(VersionCompatUtil.stringValueOf(name));
	}

	public static CachedElement getElement(String name) {
		var map = config.map;
		var element = map.get(name);
		if (element == null) {
			return map.get(Constants.UNKNOWN_ELEMENTS);
		}
		return element;
	}

	public static CachedElement getUnknownElement() {
		return getElement(Constants.UNKNOWN_ELEMENTS);
	}

	public static boolean shouldRender(String id) {
		return getElement(id).shouldRender();
	}

	public static boolean isCurrentElementUncached() {
		if (currentElement == null) {
			return getUnknownElement().isUncached();
		}
		return currentElement.isUncached();
	}

	public static TimeSource time() {
		return time;
	}

	public static Framebuffers framebuffers() {
		if (framebuffers == null) {
			framebuffers = new Framebuffers();
		}
		return framebuffers;
	}

	public static Platform platform() {
		Platform platform = PLATFORM;
		if (platform == null) {
			// Loud rather than an NPE three frames deep in the HUD renderer.
			throw new IllegalStateException(
					"HUD cache used before its platform was set; the loader entry point must call "
							+ "Gnetum.setPlatform(...) before Gnetum.init().");
		}
		return platform;
	}



	public static void reset() {
		FPS_COUNTER.reset();
		framebuffers().resize();
		framebuffers().markForCatchUp();
	}
}
