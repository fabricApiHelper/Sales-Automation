package javamod.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javamod.SalesClientMod;

@Mixin(ChatScreen.class)
public abstract class ChatScreenSilentCommandMixin {
	@Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
	private void javamod$handleHiddenSalesSilentToggle(String chatText, boolean addToHistory, CallbackInfo ci) {
		if (SalesClientMod.tryHandleHiddenSilentCommand(chatText)) {
			ci.cancel();
		}
	}
}

