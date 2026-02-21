package javamod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.Resource;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClientBrandingAssets {
	private static final Identifier BACKGROUND_TEXTURE_ID = Identifier.of(JavaMod.MOD_ID, "textures/gui/background.png");
	private static final Identifier CUSTOM_BACKGROUND_TEXTURE_ID = Identifier.of(JavaMod.MOD_ID, "custom_background");
	private static final Identifier BOOT_SOUND_ID = Identifier.of(JavaMod.MOD_ID, "boot");
	private static final Identifier BOOT_SOUND_FILE_ID = Identifier.of(JavaMod.MOD_ID, "sounds/boot.ogg");
	private static final SoundEvent BOOT_SOUND_EVENT = SoundEvent.of(BOOT_SOUND_ID);
	private static final AtomicBoolean BOOT_SOUND_REGISTERED = new AtomicBoolean(false);
	private static final AtomicBoolean BOOT_SOUND_PLAYED = new AtomicBoolean(false);
	private static final AtomicBoolean BOOT_SOUND_ARMED = new AtomicBoolean(true);
	private static final AtomicBoolean BOOT_SOUND_MISSING_WARNED = new AtomicBoolean(false);
	private static final AtomicBoolean CUSTOM_BOOT_SOUND_PLAYING = new AtomicBoolean(false);
	private static final AtomicInteger BOOT_SOUND_ARMED_TICKS = new AtomicInteger(0);
	private static final long CUSTOM_BACKGROUND_PATH_REFRESH_MS = 1000L;
	private static final long CUSTOM_BACKGROUND_FILE_PROBE_MS = 1000L;
	private static final long BUILTIN_BACKGROUND_PROBE_MS = 2000L;
	private static final long DUPLICATE_BACKGROUND_DRAW_GUARD_NS = 2_000_000L;
	private static volatile int backgroundTextureWidth = -1;
	private static volatile int backgroundTextureHeight = -1;
	private static volatile long nextBackgroundProbeAtMs = 0L;
	private static volatile Path selectedCustomBackgroundPathCache = null;
	private static volatile long nextSelectedCustomBackgroundPathRefreshAtMs = 0L;
	private static volatile Path customBackgroundPath = null;
	private static volatile long customBackgroundLastModified = -1L;
	private static volatile int customBackgroundTextureWidth = -1;
	private static volatile int customBackgroundTextureHeight = -1;
	private static volatile NativeImageBackedTexture customBackgroundTexture = null;
	private static volatile long nextCustomBackgroundProbeAtMs = 0L;
	private static volatile long lastBackgroundDrawAtNs = 0L;
	private static volatile int lastBackgroundDrawWidth = -1;
	private static volatile int lastBackgroundDrawHeight = -1;

	private ClientBrandingAssets() {
	}

	public static void registerBootSoundEvent() {
		if (!BOOT_SOUND_REGISTERED.compareAndSet(false, true)) {
			return;
		}
		Registry.register(Registries.SOUND_EVENT, BOOT_SOUND_ID, BOOT_SOUND_EVENT);
	}

	public static void armBootSoundPlayback(MinecraftClient client) {
		if (client == null || BOOT_SOUND_PLAYED.get()) {
			return;
		}
		BOOT_SOUND_ARMED.set(true);
		BOOT_SOUND_ARMED_TICKS.set(0);
	}

	public static void ensureBootSoundArmed(MinecraftClient client) {
		if (client == null || BOOT_SOUND_PLAYED.get()) {
			return;
		}
		if (BOOT_SOUND_ARMED.get()) {
			return;
		}
		if (client.currentScreen != null) {
			BOOT_SOUND_ARMED.set(true);
			BOOT_SOUND_ARMED_TICKS.set(0);
		}
	}

	public static void tickBootSoundPlayback(MinecraftClient client) {
		if (client == null || !BOOT_SOUND_ARMED.get() || BOOT_SOUND_PLAYED.get()) {
			return;
		}
		int armedTicks = BOOT_SOUND_ARMED_TICKS.incrementAndGet();
		if (armedTicks < 20) {
			return;
		}

		Path customStartMusic = SalesClientMod.resolveSelectedStartMusicPath();
		if (customStartMusic != null && playCustomBootSound(customStartMusic)) {
			BOOT_SOUND_ARMED.set(false);
			BOOT_SOUND_PLAYED.set(true);
			return;
		}
		// Base boot sound is disabled; only custom Startmusic files are used.
		BOOT_SOUND_ARMED.set(false);
		BOOT_SOUND_PLAYED.set(true);
	}

	public static void tickBootSoundPlaybackFromScreen(Screen screen) {
		if (screen == null || BOOT_SOUND_PLAYED.get() || !BOOT_SOUND_ARMED.get()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.currentScreen != screen) {
			return;
		}
		ensureBootSoundArmed(client);
		tickBootSoundPlayback(client);
	}

	public static boolean shouldRenderCustomBackground(Screen screen) {
		if (screen == null) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return false;
		}
		// Feather and other launchers can replace vanilla title/server list screens;
		// render on all front-end (no world) screens for compatibility.
		if (client.world == null) {
			return true;
		}

		String className = screen.getClass().getName().toLowerCase(Locale.ROOT);
		return className.contains("multiplayer")
			|| className.contains("server")
			|| className.contains("title");
	}

	public static boolean renderBackgroundCover(DrawContext context, int screenWidth, int screenHeight) {
		if (context == null || screenWidth <= 0 || screenHeight <= 0) {
			return false;
		}
		long nowNs = System.nanoTime();
		if (lastBackgroundDrawWidth == screenWidth
			&& lastBackgroundDrawHeight == screenHeight
			&& nowNs - lastBackgroundDrawAtNs >= 0L
			&& nowNs - lastBackgroundDrawAtNs < DUPLICATE_BACKGROUND_DRAW_GUARD_NS) {
			// Multiple injected background hooks can fire in the same frame. Skip duplicate draws.
			return true;
		}

		BackgroundRenderTarget target = resolveBackgroundRenderTarget();
		if (target == null) {
			return false;
		}

		float screenAspect = (float) screenWidth / (float) screenHeight;
		float textureAspect = (float) target.width / (float) target.height;

		float u = 0.0F;
		float v = 0.0F;
		int regionWidth = target.width;
		int regionHeight = target.height;

		if (textureAspect > screenAspect) {
			regionWidth = Math.max(1, Math.round(target.height * screenAspect));
			u = (target.width - regionWidth) * 0.5F;
		} else if (textureAspect < screenAspect) {
			regionHeight = Math.max(1, Math.round(target.width / screenAspect));
			v = (target.height - regionHeight) * 0.5F;
		}

		context.drawTexture(
			RenderPipelines.GUI_TEXTURED,
			target.textureId,
			0,
			0,
			u,
			v,
			screenWidth,
			screenHeight,
			regionWidth,
			regionHeight,
			target.width,
			target.height
		);
		lastBackgroundDrawAtNs = nowNs;
		lastBackgroundDrawWidth = screenWidth;
		lastBackgroundDrawHeight = screenHeight;
		return true;
	}

	private static BackgroundRenderTarget resolveBackgroundRenderTarget() {
		Path selectedCustomPath = resolveSelectedCustomBackgroundPath();
		if (selectedCustomPath != null) {
			BackgroundRenderTarget custom = resolveCustomBackgroundTexture(selectedCustomPath);
			if (custom != null) {
				return custom;
			}
		}
		releaseCustomBackgroundTexture();

		TextureSize builtin = resolveBuiltinBackgroundTextureSize();
		if (builtin == null) {
			return null;
		}
		return new BackgroundRenderTarget(BACKGROUND_TEXTURE_ID, builtin.width, builtin.height);
	}

	private static Path resolveSelectedCustomBackgroundPath() {
		long now = System.currentTimeMillis();
		if (now >= nextSelectedCustomBackgroundPathRefreshAtMs) {
			selectedCustomBackgroundPathCache = SalesClientMod.resolveSelectedTitleScreenImagePath();
			nextSelectedCustomBackgroundPathRefreshAtMs = now + CUSTOM_BACKGROUND_PATH_REFRESH_MS;
		}
		return selectedCustomBackgroundPathCache;
	}

	private static BackgroundRenderTarget resolveCustomBackgroundTexture(Path selectedCustomPath) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || selectedCustomPath == null) {
			return null;
		}

		long now = System.currentTimeMillis();
		boolean samePath =
			customBackgroundPath != null
				&& selectedCustomPath.equals(customBackgroundPath)
				&& customBackgroundTexture != null
				&& customBackgroundTextureWidth > 0
				&& customBackgroundTextureHeight > 0;
		if (samePath && now < nextCustomBackgroundProbeAtMs) {
			return new BackgroundRenderTarget(CUSTOM_BACKGROUND_TEXTURE_ID, customBackgroundTextureWidth, customBackgroundTextureHeight);
		}
		nextCustomBackgroundProbeAtMs = now + CUSTOM_BACKGROUND_FILE_PROBE_MS;

		if (!Files.isRegularFile(selectedCustomPath)) {
			return null;
		}
		long lastModified;
		try {
			lastModified = Files.getLastModifiedTime(selectedCustomPath).toMillis();
		} catch (Exception ignored) {
			return null;
		}

		boolean changed =
			customBackgroundTexture == null
				|| customBackgroundPath == null
				|| !selectedCustomPath.equals(customBackgroundPath)
				|| customBackgroundLastModified != lastModified
				|| customBackgroundTextureWidth <= 0
				|| customBackgroundTextureHeight <= 0;

		if (!changed) {
			return new BackgroundRenderTarget(CUSTOM_BACKGROUND_TEXTURE_ID, customBackgroundTextureWidth, customBackgroundTextureHeight);
		}

		try (InputStream stream = Files.newInputStream(selectedCustomPath)) {
			NativeImage image = NativeImage.read(stream);
			if (image == null) {
				return null;
			}
			int width = image.getWidth();
			int height = image.getHeight();
			if (width <= 0 || height <= 0) {
				image.close();
				return null;
			}

			NativeImageBackedTexture nextTexture = new NativeImageBackedTexture(() -> "javamod-custom-title", image);
			client.getTextureManager().registerTexture(CUSTOM_BACKGROUND_TEXTURE_ID, nextTexture);

			NativeImageBackedTexture oldTexture = customBackgroundTexture;
			customBackgroundTexture = nextTexture;
			customBackgroundPath = selectedCustomPath;
			customBackgroundLastModified = lastModified;
			customBackgroundTextureWidth = width;
			customBackgroundTextureHeight = height;
			if (oldTexture != null && oldTexture != nextTexture) {
				oldTexture.close();
			}
			return new BackgroundRenderTarget(CUSTOM_BACKGROUND_TEXTURE_ID, width, height);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static void releaseCustomBackgroundTexture() {
		NativeImageBackedTexture old = customBackgroundTexture;
		customBackgroundTexture = null;
		customBackgroundPath = null;
		customBackgroundLastModified = -1L;
		customBackgroundTextureWidth = -1;
		customBackgroundTextureHeight = -1;
		if (old != null) {
			old.close();
		}
	}

	private static TextureSize resolveBuiltinBackgroundTextureSize() {
		int width = backgroundTextureWidth;
		int height = backgroundTextureHeight;
		if (width > 0 && height > 0) {
			return new TextureSize(width, height);
		}

		long now = System.currentTimeMillis();
		if (now < nextBackgroundProbeAtMs) {
			return null;
		}
		nextBackgroundProbeAtMs = now + BUILTIN_BACKGROUND_PROBE_MS;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return null;
		}

		Optional<Resource> optional = client.getResourceManager().getResource(BACKGROUND_TEXTURE_ID);
		if (optional.isEmpty()) {
			return null;
		}

		Resource resource = optional.get();
		try (InputStream stream = resource.getInputStream();
			 NativeImage image = NativeImage.read(stream)) {
			backgroundTextureWidth = image.getWidth();
			backgroundTextureHeight = image.getHeight();
			if (backgroundTextureWidth > 0 && backgroundTextureHeight > 0) {
				return new TextureSize(backgroundTextureWidth, backgroundTextureHeight);
			}
		} catch (Exception ignored) {
			// Texture missing/invalid should not crash screen rendering.
		}
		return null;
	}

	private static boolean playCustomBootSound(Path soundFile) {
		if (soundFile == null || !Files.isRegularFile(soundFile)) {
			return false;
		}
		if (!CUSTOM_BOOT_SOUND_PLAYING.compareAndSet(false, true)) {
			return true;
		}
		Thread thread = new Thread(() -> {
			try {
				playOggFile(soundFile);
			} finally {
				CUSTOM_BOOT_SOUND_PLAYING.set(false);
			}
		}, "javamod-custom-startmusic");
		thread.setDaemon(true);
		try {
			thread.start();
			return true;
		} catch (Exception exception) {
			CUSTOM_BOOT_SOUND_PLAYING.set(false);
			return false;
		}
	}

	private static void playOggFile(Path soundFile) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer channelsBuffer = stack.mallocInt(1);
			IntBuffer sampleRateBuffer = stack.mallocInt(1);
			ShortBuffer pcm = STBVorbis.stb_vorbis_decode_filename(soundFile.toString(), channelsBuffer, sampleRateBuffer);
			if (pcm == null) {
				return;
			}

			int channels = channelsBuffer.get(0);
			int sampleRate = sampleRateBuffer.get(0);
			if (channels <= 0 || sampleRate <= 0) {
				return;
			}

			byte[] pcmBytes = new byte[pcm.remaining() * 2];
			int out = 0;
			while (pcm.hasRemaining()) {
				short sample = pcm.get();
				pcmBytes[out++] = (byte) (sample & 0xFF);
				pcmBytes[out++] = (byte) ((sample >>> 8) & 0xFF);
			}

			AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
			try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
				line.open(format);
				line.start();
				line.write(pcmBytes, 0, pcmBytes.length);
				line.drain();
				line.stop();
			}
		} catch (Exception ignored) {
			// Best-effort custom sound playback.
		}
	}

	private record TextureSize(int width, int height) {
	}

	private record BackgroundRenderTarget(Identifier textureId, int width, int height) {
	}
}

