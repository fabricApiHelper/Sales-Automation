package javamod.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javamod.ClientBrandingAssets;

@Mixin(Screen.class)
public abstract class ScreenCustomBackgroundMixin {
	private boolean javamod$shouldUseCustomBackground() {
		Screen self = (Screen) (Object) this;
		ClientBrandingAssets.tickBootSoundPlaybackFromScreen(self);
		return ClientBrandingAssets.shouldRenderCustomBackground(self);
	}

	private boolean javamod$maybeRenderCustomBackground(DrawContext context) {
		if (!javamod$shouldUseCustomBackground()) {
			return false;
		}
		return ClientBrandingAssets.renderBackgroundCover(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void javamod$renderCustomBackgroundWhenConfigured(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (javamod$maybeRenderCustomBackground(context)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
	private void javamod$renderCustomPanoramaBackground(DrawContext context, float delta, CallbackInfo ci) {
		if (javamod$maybeRenderCustomBackground(context)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
	private void javamod$renderCustomDarkening(DrawContext context, CallbackInfo ci) {
		if (javamod$shouldUseCustomBackground()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;IIII)V", at = @At("HEAD"), cancellable = true)
	private void javamod$renderCustomDarkeningRegion(DrawContext context, int x, int y, int width, int height, CallbackInfo ci) {
		if (javamod$shouldUseCustomBackground()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
	private void javamod$renderCustomInGameBackground(DrawContext context, CallbackInfo ci) {
		if (javamod$shouldUseCustomBackground()) {
			ci.cancel();
		}
	}
}

