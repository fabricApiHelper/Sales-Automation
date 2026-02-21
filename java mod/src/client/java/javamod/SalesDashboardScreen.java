package javamod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static javamod.SalesClientMod.*;

final class SalesDashboardScreen extends Screen {
		private static final int SIDEBAR_PADDING = 10;
		private static final int CONTENT_PADDING = 14;
			private static final int TAB_HEIGHT = 20;
			private static final int TAB_GAP = 3;
			private static final int ROW_HEIGHT = 28;
			private static final int CONTROL_HEIGHT = 20;
			private static final int FOOTER_GAP = 10;
			private static final int BUTTON_HEIGHT = 20;
			private static final int PANEL_RADIUS = 10;
			private static final int TAB_RADIUS = 6;
			private static final int CONTROL_RADIUS = 6;
		private static final int BUTTON_RADIUS = 6;

		private final Screen parent;

		private DashboardTab activeTab = DashboardTab.GENERAL;
		private final List<RowLabel> rowLabels = new ArrayList<>();
		private final List<InfoLine> infoLines = new ArrayList<>();
		private final List<TabButtonWidget> tabButtons = new ArrayList<>();
		private final IdentityHashMap<TabButtonWidget, Integer> sidebarTabBaseY = new IdentityHashMap<>();
		private final IdentityHashMap<ClickableWidget, Integer> contentWidgetBaseY = new IdentityHashMap<>();
		private int sidebarScrollPx;
		private int sidebarScrollMaxPx;
		private int contentScrollPx;
		private int contentScrollMaxPx;
		private DropdownWidget<?> openDropdown;

			private KeyBinding keybindCaptureTarget;
			private String keybindCaptureLabel;

			private int panelX;
			private int panelY;
			private int panelW;
			private int panelH;
		private int sidebarW;
		private int contentX;
		private int contentY;
		private int contentW;
		private int contentH;
		private int labelX;
		private int controlX;
		private int controlW;
		private int rowStartY;

		private ActionButtonWidget applyButton;
		private ActionButtonWidget closeButton;

		// Widgets (built per active tab; fields are null when not present).
		private DropdownWidget<GuiTheme> themeDropdown;
		private DropdownWidget<AutomationMode> automationModeDropdown;
		private ToggleSwitchWidget autoReconnectToggle;
		private ToggleSwitchWidget autoConnectStartupToggle;
		private ToggleSwitchWidget lobbyReconnectToggle;
		private ToggleSwitchWidget serverOnlyTriggersToggle;
		private ActionButtonWidget automationKeyButton;
		private ActionButtonWidget guiKeyButton;
		private ActionButtonWidget cancelRoutinesKeyButton;
		private ActionButtonWidget allOnButton;
		private ActionButtonWidget allOffButton;
		private StyledTextFieldWidget museumClickDelayField;
		private StyledTextFieldWidget museumPvField;
		private StyledTextFieldWidget lobbyClickDelayField;

		private DropdownWidget<TriggerActionMode> blackmarketModeDropdown;
		private ToggleSwitchWidget blackmarketWebhookPingToggle;
		private StyledTextFieldWidget blackmarketTriggerField;
		private StyledTextFieldWidget blackmarketBuyNamesField;
		private StyledTextFieldWidget blackmarketStoreCommandField;
		private StyledTextFieldWidget blackmarketClickDelayField;
		private StyledTextFieldWidget blackmarketClickCountField;

		private DropdownWidget<TriggerActionMode> gemshopModeDropdown;
		private ToggleSwitchWidget gemshopWebhookPingToggle;
		private StyledTextFieldWidget gemshopTriggerField;
		private StyledTextFieldWidget gemshopBuyNamesField;
		private StyledTextFieldWidget gemshopStoreCommandField;
		private StyledTextFieldWidget gemshopClickDelayField;
		private StyledTextFieldWidget gemshopClickCountField;

		private DropdownWidget<TriggerActionMode> merchantModeDropdown;
		private ToggleSwitchWidget merchantWebhookPingToggle;
		private ToggleSwitchWidget merchantWebhookNotifyPingToggle;
		private StyledTextFieldWidget merchantTriggerField;
		private StyledTextFieldWidget merchantStoreCommandField;
		private StyledTextFieldWidget merchantProtectedNamesField;
		private StyledTextFieldWidget merchantBlacklistNamesField;
		private StyledTextFieldWidget merchantWebhookNotifyNamesField;
		private StyledTextFieldWidget merchantWebhookNotifyPingCountField;
		private StyledTextFieldWidget merchantClickDelayField;
		private StyledTextFieldWidget merchantRepeatDelayField;
		private StyledTextFieldWidget merchantRepeatCountField;
		private StyledTextFieldWidget merchantGapField;
		private StyledTextFieldWidget merchantBarrierScanDelayField;
		private StyledTextFieldWidget merchantSalvageGapField;
		private DropdownWidget<TraitRollTarget> traitTargetDropdown;
		private StyledTextFieldWidget traitRollDelayField;
		private ToggleSwitchWidget traitRollButtonToggle;
		private DropdownWidget<CookieRollTarget> cookieTargetDropdown;
		private StyledTextFieldWidget cookieRollDelayField;
		private ToggleSwitchWidget cookieRollButtonToggle;
		private DropdownWidget<String> titleScreenImageDropdown;
		private DropdownWidget<String> startMusicDropdown;

		private DropdownWidget<SetupSwapMode> setupSwapModeDropdown;
		private DropdownWidget<SetupSwapArmor> setupSwapArmorDropdown;
		private ToggleSwitchWidget setupSwapBossRelicsToggle;
		private StyledTextFieldWidget setupSwapStoreCommandField;
		private StyledTextFieldWidget setupSwapGetCommandField;
		private DropdownWidget<Integer> setupSwapRingCountDropdown;
		private DropdownWidget<Integer> setupSwapAttachmentCountDropdown;
		private DropdownWidget<Integer> setupSwapBossRelicCountDropdown;
		private StyledTextFieldWidget setupSwapClickDelayField;
		private StyledTextFieldWidget setupSwapAttachmentDelayField;
		private ActionButtonWidget setupSwapRunButton;

		private DropdownWidget<EggType> eggTypeDropdown;
		private DropdownWidget<Integer> eggOpenAmountDropdown;
		private StyledTextFieldWidget eggPostOpenDelayField;
		private StyledTextFieldWidget eggClickDelayField;
		private StyledTextFieldWidget ringScrapperClickDelayField;

		private DropdownWidget<TriggerActionMode> buffsModeDropdown;
		private ToggleSwitchWidget buffsWebhookPingToggle;
		private StyledTextFieldWidget buffsTriggerField;
		private StyledTextFieldWidget buffsClickDelayField;
		private DropdownWidget<TriggerActionMode> storeModeDropdown;
		private ToggleSwitchWidget storeWebhookPingToggle;
		private StyledTextFieldWidget storeTriggerField;
		private StyledTextFieldWidget autoStoreDelayField;

		private ToggleSwitchWidget webhookToggle;
		private StyledTextFieldWidget webhookUrlField;
		private StyledTextFieldWidget webhookDelayField;
		private StyledTextFieldWidget webhookPingCountField;
		private ToggleSwitchWidget bossWebhookPingToggle;
		private StyledTextFieldWidget bossTriggerField;
		private StyledTextFieldWidget bossNotifyDelayField;
		private ActionButtonWidget testWebhookButton;

		private ToggleSwitchWidget autoDailyToggle;
		private ToggleSwitchWidget autoDailyPerksToggle;
		private ToggleSwitchWidget autoDailyFreecreditsToggle;
		private ToggleSwitchWidget autoDailyKeyallToggle;

    SalesDashboardScreen(Screen parent) {
			super(Text.literal("Sales Dashboard"));
			this.parent = parent;
		}

		@Override
			protected void init() {
				this.keybindCaptureTarget = null;
				this.keybindCaptureLabel = "";
				this.openDropdown = null;
				this.rowLabels.clear();
				this.infoLines.clear();
				this.tabButtons.clear();
				this.sidebarTabBaseY.clear();
				this.contentWidgetBaseY.clear();
				this.sidebarScrollPx = 0;
				this.contentScrollPx = 0;
				clearWidgetRefs();

			buildLayout();
			buildSidebarTabs();
			buildFooter();
			buildActiveTabContent();
			recalculateScrollBounds();
		}

		private void clearWidgetRefs() {
			this.themeDropdown = null;
			this.automationModeDropdown = null;
			this.autoReconnectToggle = null;
			this.autoConnectStartupToggle = null;
			this.lobbyReconnectToggle = null;
			this.automationKeyButton = null;
			this.guiKeyButton = null;
			this.cancelRoutinesKeyButton = null;
			this.allOnButton = null;
			this.allOffButton = null;
			this.museumClickDelayField = null;
			this.museumPvField = null;
			this.lobbyClickDelayField = null;

			this.blackmarketModeDropdown = null;
			this.blackmarketWebhookPingToggle = null;
			this.blackmarketTriggerField = null;
			this.blackmarketBuyNamesField = null;
			this.blackmarketStoreCommandField = null;
			this.blackmarketClickDelayField = null;
			this.blackmarketClickCountField = null;

			this.gemshopModeDropdown = null;
			this.gemshopWebhookPingToggle = null;
			this.gemshopTriggerField = null;
			this.gemshopBuyNamesField = null;
			this.gemshopStoreCommandField = null;
			this.gemshopClickDelayField = null;
			this.gemshopClickCountField = null;

			this.merchantModeDropdown = null;
			this.merchantWebhookPingToggle = null;
			this.merchantWebhookNotifyPingToggle = null;
			this.merchantTriggerField = null;
			this.merchantStoreCommandField = null;
			this.merchantProtectedNamesField = null;
			this.merchantBlacklistNamesField = null;
			this.merchantWebhookNotifyNamesField = null;
			this.merchantWebhookNotifyPingCountField = null;
			this.merchantClickDelayField = null;
			this.merchantRepeatDelayField = null;
			this.merchantRepeatCountField = null;
			this.merchantGapField = null;
			this.merchantBarrierScanDelayField = null;
			this.merchantSalvageGapField = null;
			this.traitTargetDropdown = null;
			this.traitRollDelayField = null;
			this.traitRollButtonToggle = null;
			this.cookieTargetDropdown = null;
			this.cookieRollDelayField = null;
			this.cookieRollButtonToggle = null;
			this.titleScreenImageDropdown = null;
			this.startMusicDropdown = null;

			this.setupSwapModeDropdown = null;
			this.setupSwapArmorDropdown = null;
			this.setupSwapBossRelicsToggle = null;
			this.setupSwapStoreCommandField = null;
			this.setupSwapGetCommandField = null;
			this.setupSwapRingCountDropdown = null;
			this.setupSwapAttachmentCountDropdown = null;
			this.setupSwapBossRelicCountDropdown = null;
			this.setupSwapClickDelayField = null;
			this.setupSwapAttachmentDelayField = null;
			this.setupSwapRunButton = null;

			this.eggTypeDropdown = null;
			this.eggOpenAmountDropdown = null;
			this.eggPostOpenDelayField = null;
			this.eggClickDelayField = null;
			this.ringScrapperClickDelayField = null;

			this.buffsModeDropdown = null;
			this.buffsWebhookPingToggle = null;
			this.buffsTriggerField = null;
			this.buffsClickDelayField = null;
			this.storeModeDropdown = null;
			this.storeWebhookPingToggle = null;
			this.storeTriggerField = null;
			this.autoStoreDelayField = null;

			this.webhookToggle = null;
			this.webhookUrlField = null;
			this.webhookDelayField = null;
			this.webhookPingCountField = null;
			this.bossWebhookPingToggle = null;
			this.bossTriggerField = null;
			this.bossNotifyDelayField = null;
			this.testWebhookButton = null;

			this.autoDailyToggle = null;
			this.autoDailyPerksToggle = null;
			this.autoDailyFreecreditsToggle = null;
			this.autoDailyKeyallToggle = null;
		}

		@Override
		public void close() {
			if (this.client != null) {
				this.client.setScreen(this.parent);
			}
		}

		private UiPalette palette() {
			return GUI_THEME == GuiTheme.LIGHT ? UiPalette.LIGHT : UiPalette.DARK;
		}

		private void buildLayout() {
			int maxW = 760;
			int maxH = 480;
			int desiredW = Math.min(this.width - 24, maxW);
			int desiredH = Math.min(this.height - 24, maxH);
			int minW = Math.min(this.width - 8, 360);
			int minH = Math.min(this.height - 8, 260);

			this.panelW = Math.max(desiredW, minW);
			this.panelH = Math.max(desiredH, minH);
			this.panelX = (this.width - this.panelW) / 2;
			this.panelY = (this.height - this.panelH) / 2;

			this.sidebarW = Math.min(180, Math.max(120, this.panelW / 4));
			this.contentX = this.panelX + this.sidebarW;
			this.contentY = this.panelY;
			this.contentW = this.panelW - this.sidebarW;
			this.contentH = this.panelH;

			int innerW = Math.max(1, this.contentW - CONTENT_PADDING * 2);
			this.labelX = this.contentX + CONTENT_PADDING;
			this.controlW = Math.min(440, Math.max(160, innerW - 150));
			this.controlX = this.contentX + this.contentW - CONTENT_PADDING - this.controlW;
			this.rowStartY = this.panelY + 42;
		}

		private void buildSidebarTabs() {
			int x = this.panelX + SIDEBAR_PADDING;
			int y = this.panelY + 42;
			int w = this.sidebarW - SIDEBAR_PADDING * 2;
			for (DashboardTab tab : DashboardTab.values()) {
				TabButtonWidget button = registerSidebarTab(this.addDrawableChild(new TabButtonWidget(this, x, y, w, TAB_HEIGHT, tab)));
				button.setSelected(tab == this.activeTab);
				this.tabButtons.add(button);
				y += TAB_HEIGHT + TAB_GAP;
			}
		}

		private void buildFooter() {
			int buttonW = 116;
			int y = this.panelY + this.panelH - FOOTER_GAP - BUTTON_HEIGHT;
			int closeX = this.contentX + this.contentW - CONTENT_PADDING - buttonW;
			int applyX = closeX - 10 - buttonW;

			this.applyButton = this.addDrawableChild(new ActionButtonWidget(
				this,
				applyX,
				y,
				buttonW,
				BUTTON_HEIGHT,
				Text.literal("Apply"),
				true,
				this::applyFields
			));
			this.closeButton = this.addDrawableChild(new ActionButtonWidget(
				this,
				closeX,
				y,
				buttonW,
				BUTTON_HEIGHT,
				Text.literal("Close"),
				false,
				this::close
			));
		}

		private int rowY(int index) {
			return this.rowStartY + index * ROW_HEIGHT;
		}

		private void buildActiveTabContent() {
			switch (this.activeTab) {
				case GENERAL -> buildGeneralTab();
				case BLACKMARKET -> buildBlackmarketTab();
				case GEMSHOP -> buildGemshopTab();
				case MERCHANT -> buildMerchantTab();
				case TRAITS -> buildTraitsTab();
				case COOKIES -> buildCookiesTab();
				case MEDIA -> buildMediaTab();
				case SETUP_SWAP -> buildSetupSwapTab();
				case EGG -> buildEggTab();
				case RING_SCRAPPER -> buildRingScrapperTab();
				case WEBHOOK -> buildWebhookTab();
				case BOSS -> buildBossTab();
				case MUSEUM -> buildMuseumTab();
				case BUFFS -> buildBuffsTab();
				case STORE -> buildStoreTab();
				case DAILIES -> buildDailiesTab();
			}
		}

		private <T extends TabButtonWidget> T registerSidebarTab(T widget) {
			this.sidebarTabBaseY.put(widget, widget.getY());
			return widget;
		}

		private <T extends ClickableWidget> T registerContentWidget(T widget) {
			this.contentWidgetBaseY.put(widget, widget.getY());
			return widget;
		}

		private int sidebarViewportTop() {
			return this.panelY + 42;
		}

		private int sidebarViewportBottom() {
			return this.panelY + this.panelH - SIDEBAR_PADDING;
		}

		private int contentViewportTop() {
			return this.rowStartY;
		}

		private int contentViewportBottom() {
			return this.panelY + this.panelH - FOOTER_GAP - BUTTON_HEIGHT - 6;
		}

		private boolean isInSidebarViewport(double mouseX, double mouseY) {
			return mouseX >= this.panelX + 1
				&& mouseX < this.contentX - 1
				&& mouseY >= sidebarViewportTop()
				&& mouseY < sidebarViewportBottom();
		}

		private boolean isInContentViewport(double mouseX, double mouseY) {
			return mouseX >= this.contentX + 1
				&& mouseX < this.panelX + this.panelW - 1
				&& mouseY >= contentViewportTop()
				&& mouseY < contentViewportBottom();
		}

		private void recalculateScrollBounds() {
			int sidebarTop = sidebarViewportTop();
			int sidebarBottom = sidebarViewportBottom();
			int sidebarViewportHeight = Math.max(1, sidebarBottom - sidebarTop);
			int sidebarContentBottom = sidebarTop;
			for (TabButtonWidget tabButton : this.tabButtons) {
				Integer baseY = this.sidebarTabBaseY.get(tabButton);
				if (baseY == null) {
					continue;
				}
				sidebarContentBottom = Math.max(sidebarContentBottom, baseY + tabButton.getHeight());
			}
			this.sidebarScrollMaxPx = Math.max(0, sidebarContentBottom - sidebarTop - sidebarViewportHeight);
			this.sidebarScrollPx = MathHelper.clamp(this.sidebarScrollPx, 0, this.sidebarScrollMaxPx);

			int contentTop = contentViewportTop();
			int contentBottom = contentViewportBottom();
			int contentViewportHeight = Math.max(1, contentBottom - contentTop);
			int contentAreaBottom = contentTop;
			for (java.util.Map.Entry<ClickableWidget, Integer> entry : this.contentWidgetBaseY.entrySet()) {
				ClickableWidget widget = entry.getKey();
				int baseY = entry.getValue();
				contentAreaBottom = Math.max(contentAreaBottom, baseY + widget.getHeight());
			}
			for (RowLabel label : this.rowLabels) {
				contentAreaBottom = Math.max(contentAreaBottom, label.y + ROW_HEIGHT);
			}
			for (InfoLine line : this.infoLines) {
				contentAreaBottom = Math.max(contentAreaBottom, line.y + this.textRenderer.fontHeight + 2);
			}
			this.contentScrollMaxPx = Math.max(0, contentAreaBottom - contentTop - contentViewportHeight);
			this.contentScrollPx = MathHelper.clamp(this.contentScrollPx, 0, this.contentScrollMaxPx);

			applySidebarScrollOffset();
			applyContentScrollOffset();
		}

		private void applySidebarScrollOffset() {
			int top = sidebarViewportTop();
			int bottom = sidebarViewportBottom();
			for (TabButtonWidget tabButton : this.tabButtons) {
				Integer baseY = this.sidebarTabBaseY.get(tabButton);
				if (baseY == null) {
					continue;
				}
				int y = baseY - this.sidebarScrollPx;
				boolean visible = y >= top && y + tabButton.getHeight() <= bottom;
				tabButton.setY(visible ? y : -10_000);
			}
		}

		private void applyContentScrollOffset() {
			int top = contentViewportTop();
			int bottom = contentViewportBottom();
			for (java.util.Map.Entry<ClickableWidget, Integer> entry : this.contentWidgetBaseY.entrySet()) {
				ClickableWidget widget = entry.getKey();
				int y = entry.getValue() - this.contentScrollPx;
				boolean visible = y >= top && y + widget.getHeight() <= bottom;
				widget.setY(visible ? y : -10_000);
				if (!visible) {
					if (widget == this.openDropdown) {
						this.openDropdown.setOpen(false);
					}
					if (widget instanceof TextFieldWidget textFieldWidget && textFieldWidget.isFocused()) {
						textFieldWidget.setFocused(false);
					}
				}
			}
		}

		private void drawVerticalScrollIndicator(DrawContext context, int x, int top, int bottom, int maxScroll, int currentScroll, int accentColor, int trackColor) {
			int height = bottom - top;
			if (maxScroll <= 0 || height <= 10) {
				return;
			}

			context.fill(x, top, x + 2, bottom, trackColor);

			int thumbHeight = Math.max(18, (int) ((height * (long) height) / (long) (height + maxScroll)));
			int trackTravel = Math.max(1, height - thumbHeight);
			int thumbY = top + Math.round((currentScroll / (float) maxScroll) * trackTravel);
			context.fill(x, thumbY, x + 2, thumbY + thumbHeight, accentColor);
		}

		@Override
		public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
			UiPalette palette = palette();

			context.fill(0, 0, this.width, this.height, palette.overlayBg);
			drawRoundedRectWithBorder(
				context,
				this.panelX,
				this.panelY,
				this.panelW,
				this.panelH,
				PANEL_RADIUS,
				palette.panelBg,
				palette.divider
			);
			// Sidebar background should not overwrite the panel border. Draw it inside the border instead.
			fillRoundedRectNoAa(
				context,
				this.panelX + 1,
				this.panelY + 1,
				this.sidebarW - 1,
				this.panelH - 2,
				Math.max(0, PANEL_RADIUS - 1),
				palette.sidebarBg,
				true,
				false,
				true,
				false
			);
			context.fill(this.contentX - 1, this.panelY + 1, this.contentX, this.panelY + this.panelH - 1, palette.divider);

			context.drawText(this.textRenderer, Text.literal("Sales"), this.panelX + SIDEBAR_PADDING, this.panelY + 14, palette.textPrimary, false);
			context.drawText(this.textRenderer, Text.literal(this.activeTab.label), this.contentX + CONTENT_PADDING, this.panelY + 14, palette.textPrimary, false);

			int contentTop = contentViewportTop();
			int contentBottom = contentViewportBottom();
			for (RowLabel label : this.rowLabels) {
				int rowBaseY = label.y - this.contentScrollPx;
				int y = rowBaseY + (ROW_HEIGHT - this.textRenderer.fontHeight) / 2;
				if (y + this.textRenderer.fontHeight < contentTop || y > contentBottom) {
					continue;
				}
				context.drawText(this.textRenderer, label.text, label.x, y, palette.textSecondary, false);
			}
			for (InfoLine line : this.infoLines) {
				int y = line.y - this.contentScrollPx;
				if (y + this.textRenderer.fontHeight < contentTop || y > contentBottom) {
					continue;
				}
				context.drawText(this.textRenderer, line.text, line.x, y, palette.textSecondary, false);
			}

			drawVerticalScrollIndicator(
				context,
				this.contentX + this.contentW - 5,
				contentTop,
				contentBottom,
				this.contentScrollMaxPx,
				this.contentScrollPx,
				mulAlpha(palette.accent, 0.85F),
				mulAlpha(palette.fieldBorder, 0.7F)
			);
			drawVerticalScrollIndicator(
				context,
				this.contentX - 4,
				sidebarViewportTop(),
				sidebarViewportBottom(),
				this.sidebarScrollMaxPx,
				this.sidebarScrollPx,
				mulAlpha(palette.accent, 0.85F),
				mulAlpha(palette.fieldBorder, 0.7F)
			);

			super.render(context, mouseX, mouseY, delta);

			if (this.openDropdown != null) {
				this.openDropdown.renderOverlay(context, mouseX, mouseY, delta);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
			double mouseX = click.x();
			double mouseY = click.y();
			int button = click.button();

			if (this.openDropdown != null && this.openDropdown.handleOverlayClick(mouseX, mouseY, button)) {
				return true;
			}

			boolean handled = super.mouseClicked(click, doubleClick);
			if (this.openDropdown != null
				&& !this.openDropdown.isMouseOver(mouseX, mouseY)
				&& !this.openDropdown.isMouseOverOverlay(mouseX, mouseY)) {
				this.openDropdown.setOpen(false);
			}
			return handled;
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			if (this.openDropdown != null && this.openDropdown.handleOverlayScroll(mouseX, mouseY, verticalAmount)) {
				return true;
			}
			if (verticalAmount != 0.0D) {
				int deltaPx = (int) Math.round(-verticalAmount * 20.0D);
				if (deltaPx == 0) {
					deltaPx = verticalAmount > 0.0D ? -20 : 20;
				}

				if (isInSidebarViewport(mouseX, mouseY) && this.sidebarScrollMaxPx > 0) {
					int next = MathHelper.clamp(this.sidebarScrollPx + deltaPx, 0, this.sidebarScrollMaxPx);
					if (next != this.sidebarScrollPx) {
						this.sidebarScrollPx = next;
						applySidebarScrollOffset();
					}
					return true;
				}

				if (isInContentViewport(mouseX, mouseY) && this.contentScrollMaxPx > 0) {
					int next = MathHelper.clamp(this.contentScrollPx + deltaPx, 0, this.contentScrollMaxPx);
					if (next != this.contentScrollPx) {
						this.contentScrollPx = next;
						applyContentScrollOffset();
					}
					return true;
				}
			}
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		@Override
		public boolean keyPressed(KeyInput keyInput) {
			if (this.keybindCaptureTarget != null) {
					if (keyInput.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
						this.keybindCaptureTarget = null;
						this.keybindCaptureLabel = "";
						updateKeybindButtons();
						sendClientFeedback("Keybind capture cancelled.");
						return true;
					}

					InputUtil.Key newKey = InputUtil.fromKeyCode(keyInput);
					this.keybindCaptureTarget.setBoundKey(newKey);
					sendClientFeedback(this.keybindCaptureLabel + " keybind updated.");

					this.keybindCaptureTarget = null;
					this.keybindCaptureLabel = "";
					KeyBinding.updateKeysByCode();
				if (this.client != null) {
					this.client.options.write();
				}
				this.clearAndInit();
				return true;
			}
			return super.keyPressed(keyInput);
		}

		private void applyFields() {
			applyFields(true);
		}

		private void applyFields(boolean notify) {
			// Apply only the widgets that exist on the current tab.
			if (this.webhookUrlField != null) {
				String parsedWebhookUrl = protectWebhookFromTruncation(normalizeWebhookInput(this.webhookUrlField.getText()), WEBHOOK_URL);
				WEBHOOK_URL = sanitizeWebhookForStorage(parsedWebhookUrl);
			}
			if (this.webhookDelayField != null) {
				WEBHOOK_DELAY_MS = clampLong(parseLongNoValidation(this.webhookDelayField.getText(), WEBHOOK_DELAY_MS), 50L, 30_000L);
			}
			if (this.webhookPingCountField != null) {
				WEBHOOK_PING_COUNT = clampInt(parseIntNoValidation(this.webhookPingCountField.getText(), WEBHOOK_PING_COUNT), 1, 25);
			}
			if (this.bossTriggerField != null) {
				BOSS_SPAWN_TRIGGER = parseTextNoValidation(this.bossTriggerField.getText(), BOSS_SPAWN_TRIGGER);
			}
			if (this.bossNotifyDelayField != null) {
				BOSS_NOTIFY_DELAY_MS = clampLong(parseLongNoValidation(this.bossNotifyDelayField.getText(), BOSS_NOTIFY_DELAY_MS), 0L, 86_400_000L);
			}

			if (this.museumPvField != null) {
				MUSEUM_PV_NUMBER = clampInt(parseIntNoValidation(this.museumPvField.getText(), MUSEUM_PV_NUMBER), 1, 100);
			}
			if (this.museumClickDelayField != null) {
				MUSEUM_CLICK_DELAY_MS = clampLong(parseLongNoValidation(this.museumClickDelayField.getText(), MUSEUM_CLICK_DELAY_MS), 20L, 10_000L);
			}
			if (this.lobbyClickDelayField != null) {
				LOBBY_CLICK_DELAY_MS = clampLong(parseLongNoValidation(this.lobbyClickDelayField.getText(), LOBBY_CLICK_DELAY_MS), 20L, 10_000L);
			}

			if (this.setupSwapStoreCommandField != null) {
				SETUP_SWAP_STORE_COMMAND = normalizeStoreCommand(parseTextNoValidation(this.setupSwapStoreCommandField.getText(), SETUP_SWAP_STORE_COMMAND));
			}
			if (this.setupSwapGetCommandField != null) {
				SETUP_SWAP_GET_COMMAND = normalizeStoreCommand(parseTextNoValidation(this.setupSwapGetCommandField.getText(), SETUP_SWAP_GET_COMMAND));
			}
			if (this.setupSwapClickDelayField != null) {
				SETUP_SWAP_CLICK_DELAY_MS = clampLong(parseLongNoValidation(this.setupSwapClickDelayField.getText(), SETUP_SWAP_CLICK_DELAY_MS), 20L, 10_000L);
			}
			if (this.setupSwapAttachmentDelayField != null) {
				SETUP_SWAP_ATTACHMENT_DELAY_MS = clampLong(parseLongNoValidation(this.setupSwapAttachmentDelayField.getText(), SETUP_SWAP_ATTACHMENT_DELAY_MS), 0L, 10_000L);
			}

			if (this.gemshopTriggerField != null) {
				GEMSHOP_TRIGGER = parseTextNoValidation(this.gemshopTriggerField.getText(), GEMSHOP_TRIGGER);
			}
			if (this.gemshopBuyNamesField != null) {
				setGemshopBuyNames(parseTextNoValidation(this.gemshopBuyNamesField.getText(), GEMSHOP_BUY_NAMES));
			}
			if (this.gemshopStoreCommandField != null) {
				GEMSHOP_STORE_COMMAND = normalizeStoreCommand(parseTextNoValidation(this.gemshopStoreCommandField.getText(), GEMSHOP_STORE_COMMAND));
			}
			if (this.gemshopClickDelayField != null) {
				GEMSHOP_CLICK_DELAY_MS = clampLong(parseLongNoValidation(this.gemshopClickDelayField.getText(), GEMSHOP_CLICK_DELAY_MS), 20L, 10_000L);
			}
			if (this.gemshopClickCountField != null) {
				GEMSHOP_CLICK_COUNT = clampInt(parseIntNoValidation(this.gemshopClickCountField.getText(), GEMSHOP_CLICK_COUNT), 1, 50);
			}

			if (this.blackmarketTriggerField != null) {
				BLACKMARKET_TRIGGER_TEXT = parseTextNoValidation(this.blackmarketTriggerField.getText(), BLACKMARKET_TRIGGER_TEXT);
			}
			if (this.blackmarketBuyNamesField != null) {
				setBlackmarketBuyNames(parseTextNoValidation(this.blackmarketBuyNamesField.getText(), BLACKMARKET_BUY_NAMES));
			}
			if (this.blackmarketStoreCommandField != null) {
				BLACKMARKET_STORE_COMMAND = normalizeStoreCommand(parseTextNoValidation(this.blackmarketStoreCommandField.getText(), BLACKMARKET_STORE_COMMAND));
			}
			if (this.blackmarketClickDelayField != null) {
				BLACKMARKET_CLICK_DELAY_MS = clampLong(parseLongNoValidation(this.blackmarketClickDelayField.getText(), BLACKMARKET_CLICK_DELAY_MS), 20L, 10_000L);
			}
			if (this.blackmarketClickCountField != null) {
				BLACKMARKET_CLICK_COUNT = clampInt(parseIntNoValidation(this.blackmarketClickCountField.getText(), BLACKMARKET_CLICK_COUNT), 1, 50);
			}

			if (this.merchantTriggerField != null) {
				MERCHANT_TRIGGER = parseTextNoValidation(this.merchantTriggerField.getText(), MERCHANT_TRIGGER);
			}
			if (this.merchantStoreCommandField != null) {
				MERCHANT_STORE_COMMAND = normalizeStoreCommand(parseTextNoValidation(this.merchantStoreCommandField.getText(), MERCHANT_STORE_COMMAND));
			}
			if (this.merchantProtectedNamesField != null) {
				setMerchantProtectedNames(parseTextNoValidation(this.merchantProtectedNamesField.getText(), MERCHANT_PROTECTED_NAMES));
			}
			if (this.merchantBlacklistNamesField != null) {
				setMerchantBlacklistNames(parseTextNoValidation(this.merchantBlacklistNamesField.getText(), MERCHANT_BLACKLIST_NAMES));
			}
			if (this.merchantWebhookNotifyNamesField != null) {
				setMerchantWebhookNotifyNames(parseTextNoValidation(
					this.merchantWebhookNotifyNamesField.getText(),
					MERCHANT_WEBHOOK_NOTIFY_NAMES
				));
			}
			if (this.merchantWebhookNotifyPingCountField != null) {
				MERCHANT_WEBHOOK_NOTIFY_PING_COUNT = clampInt(parseIntNoValidation(
					this.merchantWebhookNotifyPingCountField.getText(),
					MERCHANT_WEBHOOK_NOTIFY_PING_COUNT
				), 1, 25);
			}
			if (this.merchantClickDelayField != null) {
				MERCHANT_CLICK_DELAY_MS = clampLong(parseLongNoValidation(this.merchantClickDelayField.getText(), MERCHANT_CLICK_DELAY_MS), 20L, 10_000L);
			}
			if (this.merchantRepeatDelayField != null) {
				MERCHANT_REPEAT_DELAY_MS = clampLong(parseLongNoValidation(this.merchantRepeatDelayField.getText(), MERCHANT_REPEAT_DELAY_MS), 500L, 120_000L);
			}
			if (this.merchantRepeatCountField != null) {
				MERCHANT_REPEAT_COUNT = clampInt(parseIntNoValidation(this.merchantRepeatCountField.getText(), MERCHANT_REPEAT_COUNT), 1, 25);
			}
			if (this.merchantGapField != null) {
				MERCHANT_FIRST_GUI_GAP_MS = clampLong(parseLongNoValidation(this.merchantGapField.getText(), MERCHANT_FIRST_GUI_GAP_MS), 50L, 10_000L);
			}
			if (this.merchantBarrierScanDelayField != null) {
				MERCHANT_BARRIER_SCAN_DELAY_MS = clampLong(parseLongNoValidation(this.merchantBarrierScanDelayField.getText(), MERCHANT_BARRIER_SCAN_DELAY_MS), 0L, 10_000L);
			}
			if (this.merchantSalvageGapField != null) {
				MERCHANT_SALVAGE_TO_ALL_GAP_MS = clampLong(parseLongNoValidation(this.merchantSalvageGapField.getText(), MERCHANT_SALVAGE_TO_ALL_GAP_MS), 0L, 20_000L);
			}
			if (this.traitRollDelayField != null) {
				TRAIT_ROLL_DELAY_MS = clampLong(parseLongNoValidation(this.traitRollDelayField.getText(), TRAIT_ROLL_DELAY_MS), 20L, 10_000L);
			}
			if (this.cookieRollDelayField != null) {
				COOKIE_ROLL_DELAY_MS = clampLong(parseLongNoValidation(this.cookieRollDelayField.getText(), COOKIE_ROLL_DELAY_MS), 20L, 10_000L);
			}

			if (this.buffsTriggerField != null) {
				BUFFS_TRIGGER = parseTextNoValidation(this.buffsTriggerField.getText(), BUFFS_TRIGGER);
			}
			if (this.buffsClickDelayField != null) {
				BUFFS_CLICK_DELAY_MS = clampLong(parseLongNoValidation(this.buffsClickDelayField.getText(), BUFFS_CLICK_DELAY_MS), 20L, 10_000L);
			}
			if (this.storeTriggerField != null) {
				STORE_PURCHASE_TRIGGER = parseTextNoValidation(this.storeTriggerField.getText(), STORE_PURCHASE_TRIGGER);
			}
			if (this.autoStoreDelayField != null) {
				AUTO_STORE_DELAY_MS = clampLong(parseLongNoValidation(this.autoStoreDelayField.getText(), AUTO_STORE_DELAY_MS), 0L, 30_000L);
			}

			if (this.eggPostOpenDelayField != null) {
				EGG_POST_OPEN_DELAY_MS = clampLong(parseLongNoValidation(this.eggPostOpenDelayField.getText(), EGG_POST_OPEN_DELAY_MS), 0L, 10_000L);
			}
			if (this.eggClickDelayField != null) {
				EGG_CLICK_DELAY_MS = clampLong(parseLongNoValidation(this.eggClickDelayField.getText(), EGG_CLICK_DELAY_MS), 50L, 10_000L);
			}
			if (this.ringScrapperClickDelayField != null) {
				RING_SCRAPPER_CLICK_DELAY_MS = clampLong(
					parseLongNoValidation(this.ringScrapperClickDelayField.getText(), RING_SCRAPPER_CLICK_DELAY_MS),
					20L,
					10_000L
				);
			}

				savePersistedSettings();
				if (notify) {
					sendClientFeedback("Settings applied.");
				}
			}

			private void sendTestWebhook() {
				sendClientFeedback("Sending webhook test...");
				Thread testThread = new Thread(() -> {
					boolean success = sendWebhookPingToUrl(WEBHOOK_URL, "test", false);
					MinecraftClient client = MinecraftClient.getInstance();
					client.execute(() -> sendClientFeedback(success
						? "Webhook test sent successfully."
						: "Webhook test failed. Check URL or network."));
				}, "javamod-webhook-test");
				testThread.setDaemon(true);
				testThread.start();
			}

			private void startKeybindCapture(KeyBinding keyBinding, String label) {
				if (keyBinding == null) {
					sendClientFeedback("Keybind capture failed: keybinding missing.");
					return;
				}
				this.keybindCaptureTarget = keyBinding;
				this.keybindCaptureLabel = label;
				updateKeybindButtons();
				sendClientFeedback("Press a key for " + label + ".");
			}

		private void updateKeybindButtons() {
			boolean awaitingAutomation = this.keybindCaptureTarget == START_AUTOMATION_KEYBIND;
			boolean awaitingGui = this.keybindCaptureTarget == OPEN_GUI_KEYBIND;
			boolean awaitingCancel = this.keybindCaptureTarget == CANCEL_ALL_ROUTINES_KEYBIND;
			if (this.automationKeyButton != null) {
				this.automationKeyButton.setMessage(buildKeybindButtonLabel(
					"Automation Key",
					START_AUTOMATION_KEYBIND,
					awaitingAutomation
				));
			}
			if (this.guiKeyButton != null) {
				this.guiKeyButton.setMessage(buildKeybindButtonLabel(
					"GUI Key",
					OPEN_GUI_KEYBIND,
					awaitingGui
				));
			}
			if (this.cancelRoutinesKeyButton != null) {
				this.cancelRoutinesKeyButton.setMessage(buildKeybindButtonLabel(
					"Cancel Routines Key",
					CANCEL_ALL_ROUTINES_KEYBIND,
					awaitingCancel
				));
			}
		}

		private Text buildKeybindButtonLabel(String label, KeyBinding keyBinding, boolean waiting) {
			if (waiting) {
				return Text.literal(label + ": press key...");
			}
			if (keyBinding == null) {
				return Text.literal(label + ": ?");
			}
			return Text.literal(label + ": " + keyBinding.getBoundKeyLocalizedText().getString());
		}

		private long parseLongNoValidation(String text, long fallback) {
			try {
				return Long.parseLong((text == null ? "" : text).trim());
			} catch (Exception exception) {
				return fallback;
			}
		}

		private int parseIntNoValidation(String text, int fallback) {
			try {
				return Integer.parseInt((text == null ? "" : text).trim());
			} catch (Exception exception) {
				return fallback;
			}
		}

		private String parseTextNoValidation(String text, String fallback) {
			if (text == null) {
				return fallback;
			}
			return text.trim();
		}

		private long clampLong(long value, long min, long max) {
			return MathHelper.clamp(value, min, max);
		}

		private int clampInt(int value, int min, int max) {
			return MathHelper.clamp(value, min, max);
		}

		private void setAllTogglesFromDashboard(boolean enabled) {
			WEBHOOK_ENABLED.set(enabled);
			applyTriggerActionMode(
				GEMSHOP_ENABLED,
				GEMSHOP_WEBHOOK_ONLY,
				enabled ? TriggerActionMode.AUTO : TriggerActionMode.OFF
			);
			applyTriggerActionMode(
				BLACKMARKET_ENABLED,
				BLACKMARKET_WEBHOOK_ONLY,
				enabled ? TriggerActionMode.AUTO : TriggerActionMode.OFF
			);
			applyTriggerActionMode(
				MERCHANT_ENABLED,
				MERCHANT_WEBHOOK_ONLY,
				enabled ? TriggerActionMode.AUTO : TriggerActionMode.OFF
			);
			applyTriggerActionMode(
				BUFFS_ENABLED,
				BUFFS_WEBHOOK_ONLY,
				enabled ? TriggerActionMode.AUTO : TriggerActionMode.OFF
			);
			applyTriggerActionMode(
				STORE_PURCHASE_ENABLED,
				STORE_WEBHOOK_ONLY,
				enabled ? TriggerActionMode.AUTO : TriggerActionMode.OFF
			);
			AUTO_RECONNECT_ENABLED.set(enabled);
			LOBBY_RECONNECT_ENABLED.set(enabled);
			if (enabled) {
				setAutomationMode(automationMode, automationMode == AutomationMode.EGG);
				setAutomationActive(true);
			} else {
				FISHING_ENABLED.set(false);
				MUSEUM_ENABLED.set(false);
				EGG_ENABLED.set(false);
				EGG_PENDING.set(false);
				RING_SCRAPPER_ENABLED.set(false);
				RING_SCRAPPER_PENDING.set(false);
				setAutomationActive(false);
			}
			savePersistedSettings();
		}

		private DropdownWidget<TriggerActionMode> addTriggerModeRow(
			int rowIndex,
			String label,
			AtomicBoolean enabled,
			AtomicBoolean webhookOnly
		) {
			return addDropdownRow(
				rowIndex,
				label,
				List.of(TriggerActionMode.values()),
				mode -> Text.literal(mode.label),
					() -> getTriggerActionMode(enabled, webhookOnly),
					mode -> {
						applyTriggerActionMode(enabled, webhookOnly, mode);
						savePersistedSettings();
						sendClientFeedback(label + " set to " + mode.label + ".");
					}
				);
			}

		private ToggleSwitchWidget addToggleRow(
			int rowIndex,
			String label,
			Supplier<Boolean> getter,
			Consumer<Boolean> setter
		) {
			int y = rowY(rowIndex) + (ROW_HEIGHT - ToggleSwitchWidget.HEIGHT) / 2;
			ToggleSwitchWidget widget = registerContentWidget(this.addDrawableChild(new ToggleSwitchWidget(this, this.controlX, y, getter, setter)));
			this.rowLabels.add(new RowLabel(Text.literal(label), this.labelX, rowY(rowIndex)));
			return widget;
		}

		private ActionButtonWidget addActionRow(
			int rowIndex,
			String label,
			Text buttonText,
			boolean primary,
			Runnable onClick
		) {
			int y = rowY(rowIndex) + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
			ActionButtonWidget widget = registerContentWidget(this.addDrawableChild(new ActionButtonWidget(this, this.controlX, y, this.controlW, BUTTON_HEIGHT, buttonText, primary, onClick)));
			if (!label.isBlank()) {
				this.rowLabels.add(new RowLabel(Text.literal(label), this.labelX, rowY(rowIndex)));
			}
			return widget;
		}

		private StyledTextFieldWidget addTextRow(int rowIndex, String label, String value, String suggestion) {
			return addTextRow(rowIndex, label, value, suggestion, 2048);
		}

		private StyledTextFieldWidget addTextRow(int rowIndex, String label, String value, String suggestion, int maxLength) {
			int y = rowY(rowIndex) + (ROW_HEIGHT - CONTROL_HEIGHT) / 2;
			StyledTextFieldWidget field = registerContentWidget(this.addDrawableChild(new StyledTextFieldWidget(this, this.controlX, y, this.controlW, CONTROL_HEIGHT, suggestion)));
			// IMPORTANT: set max length before setText; TextFieldWidget truncates input to current max length.
			field.setMaxLength(Math.max(1, maxLength));
			field.setText(value == null ? "" : value);
			this.rowLabels.add(new RowLabel(Text.literal(label), this.labelX, rowY(rowIndex)));
			return field;
		}

		private StyledTextFieldWidget addNumberRow(int rowIndex, String label, long value) {
			return addTextRow(rowIndex, label, Long.toString(value), label, 16);
		}

		private StyledTextFieldWidget addNumberRow(int rowIndex, String label, int value) {
			return addTextRow(rowIndex, label, Integer.toString(value), label, 16);
		}

		private <T> DropdownWidget<T> addDropdownRow(
			int rowIndex,
			String label,
			List<T> values,
			java.util.function.Function<T, Text> formatter,
			Supplier<T> getter,
			Consumer<T> setter
		) {
			int y = rowY(rowIndex) + (ROW_HEIGHT - CONTROL_HEIGHT) / 2;
			DropdownWidget<T> widget = registerContentWidget(this.addDrawableChild(new DropdownWidget<>(this, this.controlX, y, this.controlW, CONTROL_HEIGHT, values, formatter, getter, setter)));
			this.rowLabels.add(new RowLabel(Text.literal(label), this.labelX, rowY(rowIndex)));
			return widget;
		}

		// Tab builders (implemented below).
		private void buildGeneralTab() {
			int row = 0;
			this.themeDropdown = addDropdownRow(
				row++,
				"Theme",
				List.of(GuiTheme.DARK, GuiTheme.LIGHT),
				theme -> Text.literal(theme.label),
				() -> GUI_THEME,
				theme -> {
					GUI_THEME = theme;
					savePersistedSettings();
				}
			);
			this.automationModeDropdown = addDropdownRow(
				row++,
				"Automation Mode",
				List.of(AutomationMode.values()),
				mode -> Text.literal(mode.label),
				() -> automationMode,
					mode -> {
						setAutomationMode(mode, mode == AutomationMode.EGG);
						savePersistedSettings();
						sendClientFeedback("Automation mode set to " + mode.label + ".");
					}
				);
			this.autoReconnectToggle = addToggleRow(
				row++,
				"Auto Reconnect",
				() -> AUTO_RECONNECT_ENABLED.get(),
				value -> {
					AUTO_RECONNECT_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.autoConnectStartupToggle = addToggleRow(
				row++,
				"Auto Connect On Startup",
				() -> AUTO_CONNECT_ON_STARTUP_ENABLED.get(),
				value -> {
					setStartupAutoConnectEnabled(value);
					savePersistedSettings();
				}
			);
			this.lobbyReconnectToggle = addToggleRow(
				row++,
				"Lobby Reconnect",
				() -> LOBBY_RECONNECT_ENABLED.get(),
				value -> {
					LOBBY_RECONNECT_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.infoLines.add(new InfoLine(
				Text.literal("Warning: startup auto-connect may break on some launchers.").formatted(Formatting.RED),
				this.labelX,
				rowY(row) + 6
			));
			row++;
			this.serverOnlyTriggersToggle = addToggleRow(
				row++,
				"Server-Only Triggers",
				() -> SERVER_ONLY_TRIGGERS.get(),
				value -> {
					SERVER_ONLY_TRIGGERS.set(value);
					savePersistedSettings();
				}
			);
			this.automationKeyButton = addActionRow(
				row++,
				"Automation Key",
				buildKeybindButtonLabel("Automation Key", START_AUTOMATION_KEYBIND, this.keybindCaptureTarget == START_AUTOMATION_KEYBIND),
				false,
				() -> startKeybindCapture(START_AUTOMATION_KEYBIND, "Automation")
			);
			this.guiKeyButton = addActionRow(
				row++,
				"GUI Key",
				buildKeybindButtonLabel("GUI Key", OPEN_GUI_KEYBIND, this.keybindCaptureTarget == OPEN_GUI_KEYBIND),
				false,
				() -> startKeybindCapture(OPEN_GUI_KEYBIND, "GUI")
			);
			this.cancelRoutinesKeyButton = addActionRow(
				row++,
				"Cancel Routines Key",
				buildKeybindButtonLabel("Cancel Routines Key", CANCEL_ALL_ROUTINES_KEYBIND, this.keybindCaptureTarget == CANCEL_ALL_ROUTINES_KEYBIND),
				false,
				() -> startKeybindCapture(CANCEL_ALL_ROUTINES_KEYBIND, "Cancel routines")
			);
			this.allOnButton = addActionRow(
				row++,
				"All Features",
				Text.literal("All ON"),
					true,
					() -> {
						setAllTogglesFromDashboard(true);
						sendClientFeedback("All features enabled.");
						this.clearAndInit();
					}
				);
			this.allOffButton = addActionRow(
				row++,
				"",
				Text.literal("All OFF"),
					false,
					() -> {
						setAllTogglesFromDashboard(false);
						sendClientFeedback("All features disabled.");
						this.clearAndInit();
					}
				);
			this.lobbyClickDelayField = addNumberRow(row++, "Lobby Click Delay (ms)", LOBBY_CLICK_DELAY_MS);
			}
		private void buildBlackmarketTab() {
			int row = 0;
			this.blackmarketModeDropdown = addTriggerModeRow(
				row++,
				"Mode",
				BLACKMARKET_ENABLED,
				BLACKMARKET_WEBHOOK_ONLY
			);
			this.blackmarketWebhookPingToggle = addToggleRow(
				row++,
				"Webhook Ping",
				() -> BLACKMARKET_WEBHOOK_PING_ENABLED.get(),
				value -> {
					BLACKMARKET_WEBHOOK_PING_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.blackmarketTriggerField = addTextRow(row++, "Trigger", BLACKMARKET_TRIGGER_TEXT, "Restock trigger text");
			this.blackmarketBuyNamesField = addTextRow(row++, "Buy Names (;)", BLACKMARKET_BUY_NAMES, "name1;name2;name3", 16384);
			this.blackmarketStoreCommandField = addTextRow(row++, "Store Cmd", BLACKMARKET_STORE_COMMAND, "pv 5 (or off)");
			this.blackmarketClickDelayField = addNumberRow(row++, "Click Delay (ms)", BLACKMARKET_CLICK_DELAY_MS);
			this.blackmarketClickCountField = addNumberRow(row++, "Click Count", BLACKMARKET_CLICK_COUNT);
		}
		private void buildGemshopTab() {
			int row = 0;
			this.gemshopModeDropdown = addTriggerModeRow(
				row++,
				"Mode",
				GEMSHOP_ENABLED,
				GEMSHOP_WEBHOOK_ONLY
			);
			this.gemshopWebhookPingToggle = addToggleRow(
				row++,
				"Webhook Ping",
				() -> GEMSHOP_WEBHOOK_PING_ENABLED.get(),
				value -> {
					GEMSHOP_WEBHOOK_PING_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.gemshopTriggerField = addTextRow(row++, "Trigger", GEMSHOP_TRIGGER, "Restock trigger text");
			this.gemshopBuyNamesField = addTextRow(row++, "Buy Names (;)", GEMSHOP_BUY_NAMES, "name1;name2;name3", 16384);
			this.gemshopStoreCommandField = addTextRow(row++, "Store Cmd", GEMSHOP_STORE_COMMAND, "pv 5 (or off)");
			this.gemshopClickDelayField = addNumberRow(row++, "Click Delay (ms)", GEMSHOP_CLICK_DELAY_MS);
			this.gemshopClickCountField = addNumberRow(row++, "Click Count", GEMSHOP_CLICK_COUNT);
		}
		private void buildMerchantTab() {
			int row = 0;
			this.merchantModeDropdown = addTriggerModeRow(
				row++,
				"Mode",
				MERCHANT_ENABLED,
				MERCHANT_WEBHOOK_ONLY
			);
			this.merchantWebhookPingToggle = addToggleRow(
				row++,
				"Webhook Ping",
				() -> MERCHANT_WEBHOOK_PING_ENABLED.get(),
				value -> {
					MERCHANT_WEBHOOK_PING_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.merchantTriggerField = addTextRow(row++, "Trigger", MERCHANT_TRIGGER, "Restock trigger text");
			this.merchantStoreCommandField = addTextRow(row++, "Store Cmd", MERCHANT_STORE_COMMAND, "pv 5 (or off)");
			this.merchantProtectedNamesField = addTextRow(row++, "Protected NPCs (;)", MERCHANT_PROTECTED_NAMES, "name1;name2;name3", 16384);
			this.merchantBlacklistNamesField = addTextRow(row++, "Blacklist NPCs (;)", MERCHANT_BLACKLIST_NAMES, "name1;name2;name3", 16384);
			this.merchantWebhookNotifyNamesField = addTextRow(row++, "Webhook NPCs (;)", MERCHANT_WEBHOOK_NOTIFY_NAMES, "name1;name2;name3", 16384);
			this.merchantWebhookNotifyPingToggle = addToggleRow(
				row++,
				"Webhook NPC Ping",
				() -> MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED.get(),
				value -> {
					MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.merchantWebhookNotifyPingCountField = addNumberRow(row++, "Webhook Ping Count", MERCHANT_WEBHOOK_NOTIFY_PING_COUNT);
			this.merchantClickDelayField = addNumberRow(row++, "Click Delay (ms)", MERCHANT_CLICK_DELAY_MS);
			this.merchantRepeatDelayField = addNumberRow(row++, "Repeat Delay (ms)", MERCHANT_REPEAT_DELAY_MS);
			this.merchantRepeatCountField = addNumberRow(row++, "Repeat Count", MERCHANT_REPEAT_COUNT);
			this.merchantGapField = addNumberRow(row++, "Step Gap (ms)", MERCHANT_FIRST_GUI_GAP_MS);
			this.merchantBarrierScanDelayField = addNumberRow(row++, "Barrier Scan Delay (ms)", MERCHANT_BARRIER_SCAN_DELAY_MS);
			this.merchantSalvageGapField = addNumberRow(row++, "Salvage -> All (ms)", MERCHANT_SALVAGE_TO_ALL_GAP_MS);
			this.infoLines.add(new InfoLine(
				Text.literal("Warning: webhook NPC list does not move NPCs to PV. Use Protected NPCs list for that.").formatted(Formatting.RED),
				this.labelX,
				rowY(row) + 6
			));
		}

		private void buildTraitsTab() {
			int row = 0;
			this.traitRollButtonToggle = addToggleRow(
				row++,
				"Show Auto Button",
				() -> TRAIT_ROLL_BUTTON_ENABLED.get(),
				value -> {
					TRAIT_ROLL_BUTTON_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.traitTargetDropdown = addDropdownRow(
				row++,
				"Target Trait",
				List.of(TraitRollTarget.values()),
				target -> Text.literal(target.label),
				() -> TRAIT_ROLL_TARGET,
				target -> {
					TRAIT_ROLL_TARGET = target;
					savePersistedSettings();
				}
			);
			this.traitRollDelayField = addNumberRow(row++, "Roll Delay (ms)", TRAIT_ROLL_DELAY_MS);
			this.infoLines.add(new InfoLine(Text.literal("Open 'Traits' container, then press 'Auto Trait Roll'."), this.labelX, rowY(row) + 6));
		}

		private void buildCookiesTab() {
			int row = 0;
			this.cookieRollButtonToggle = addToggleRow(
				row++,
				"Show Auto Button",
				() -> COOKIE_ROLL_BUTTON_ENABLED.get(),
				value -> {
					COOKIE_ROLL_BUTTON_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.cookieTargetDropdown = addDropdownRow(
				row++,
				"Target Rarity",
				List.of(CookieRollTarget.values()),
				target -> Text.literal(target.label),
				() -> COOKIE_ROLL_TARGET,
				target -> {
					COOKIE_ROLL_TARGET = target;
					savePersistedSettings();
				}
			);
			this.cookieRollDelayField = addNumberRow(row++, "Roll Delay (ms)", COOKIE_ROLL_DELAY_MS);
			this.infoLines.add(new InfoLine(Text.literal("Cookie roll uses fixed container slot 13."), this.labelX, rowY(row) + 6));
		}

		private void buildMediaTab() {
			int row = 0;
			this.titleScreenImageDropdown = addDropdownRow(
				row++,
				"Title Image (.png)",
				getTitleScreenImageOptionsForGui(),
				value -> formatMediaSelectionLabel(value, "Built-in image"),
				() -> getSelectedTitleScreenImageForGui(),
				value -> {
					setSelectedTitleScreenImageForGui(value);
					savePersistedSettings();
				}
			);
			this.startMusicDropdown = addDropdownRow(
				row++,
				"Start Music (.ogg)",
				getStartMusicOptionsForGui(),
				value -> formatMediaSelectionLabel(value, "Built-in music"),
				() -> getSelectedStartMusicForGui(),
				value -> {
					setSelectedStartMusicForGui(value);
					savePersistedSettings();
				}
			);
			this.infoLines.add(new InfoLine(
				Text.literal("Place files in: " + getBrandingMediaRootDirForGui().toAbsolutePath().normalize()),
				this.labelX,
				rowY(row++) + 6
			));
			this.infoLines.add(new InfoLine(
				Text.literal("Subfolders: Titlescreen (.png) and Startmusic (.ogg)"),
				this.labelX,
				rowY(row) + 6
			));
		}

		private Text formatMediaSelectionLabel(String fileName, String fallbackLabel) {
			if (fileName == null || fileName.isBlank()) {
				return Text.literal(fallbackLabel);
			}
			return Text.literal(fileName);
		}

		private void buildSetupSwapTab() {
			int row = 0;
			this.setupSwapModeDropdown = addDropdownRow(
				row++,
				"Mode",
				List.of(SetupSwapMode.values()),
				mode -> Text.literal(mode.label),
				() -> SETUP_SWAP_MODE,
				mode -> {
					SETUP_SWAP_MODE = mode;
					savePersistedSettings();
				}
			);
			this.setupSwapStoreCommandField = addTextRow(row++, "Store PV", SETUP_SWAP_STORE_COMMAND, "pv 4 (or off)");
			this.setupSwapGetCommandField = addTextRow(row++, "Get PV", SETUP_SWAP_GET_COMMAND, "pv 5");
			this.setupSwapRingCountDropdown = addDropdownRow(
				row++,
				"Rings",
				List.of(1, 2, 3, 4, 5),
				value -> Text.literal(Integer.toString(value)),
				() -> SETUP_SWAP_RING_COUNT,
				value -> {
					SETUP_SWAP_RING_COUNT = clampInt(value, 1, 5);
					savePersistedSettings();
				}
			);
			this.setupSwapAttachmentCountDropdown = addDropdownRow(
				row++,
				"Attachments",
				List.of(1, 2, 3, 4, 5, 6),
				value -> Text.literal(Integer.toString(value)),
				() -> SETUP_SWAP_ATTACHMENT_COUNT,
				value -> {
					SETUP_SWAP_ATTACHMENT_COUNT = clampInt(value, 1, 6);
					savePersistedSettings();
				}
			);
			this.setupSwapBossRelicsToggle = addToggleRow(
				row++,
				"Boss Relics",
				() -> SETUP_SWAP_BOSS_RELICS_ENABLED.get(),
				value -> {
					SETUP_SWAP_BOSS_RELICS_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.setupSwapBossRelicCountDropdown = addDropdownRow(
				row++,
				"Boss Relic Amount",
				List.of(1, 2, 3, 4, 5, 6),
				value -> Text.literal(Integer.toString(value)),
				() -> SETUP_SWAP_BOSS_RELIC_COUNT,
				value -> {
					SETUP_SWAP_BOSS_RELIC_COUNT = clampInt(value, 1, 6);
					savePersistedSettings();
				}
			);
			this.setupSwapClickDelayField = addNumberRow(row++, "Click Delay (ms)", SETUP_SWAP_CLICK_DELAY_MS);
			this.setupSwapAttachmentDelayField = addNumberRow(row++, "Attachment Delay (ms)", SETUP_SWAP_ATTACHMENT_DELAY_MS);
			this.setupSwapArmorDropdown = addDropdownRow(
				row++,
				"Armor",
				List.of(SetupSwapArmor.values()),
				armor -> Text.literal(armor.label),
				() -> SETUP_SWAP_ARMOR,
				armor -> {
					SETUP_SWAP_ARMOR = armor;
					savePersistedSettings();
				}
			);

			this.setupSwapRunButton = addActionRow(
				row++,
				"Run",
				Text.literal("Start Now"),
				true,
				() -> {
					applyFields(false);
					enqueueRoutine("setup-swap", SalesClientMod::runSetupSwapRoutine);
					sendClientFeedback("Setup swap queued: " + SETUP_SWAP_MODE.label + ".");
				}
			);

			String keyName = SETUP_SWAP_KEYBIND == null ? "K" : SETUP_SWAP_KEYBIND.getBoundKeyLocalizedText().getString();
			this.infoLines.add(new InfoLine(Text.literal("Keybind: " + keyName), this.labelX, rowY(row) + 6));
		}
		private void buildEggTab() {
			int row = 0;
			this.eggTypeDropdown = addDropdownRow(
				row++,
				"Egg Type",
				List.of(EggType.values()),
				type -> Text.literal(type.displayName),
				() -> selectedEggType,
				type -> {
					selectedEggType = type;
					savePersistedSettings();
				}
			);
			this.eggOpenAmountDropdown = addDropdownRow(
				row++,
				"Egg Opens",
				List.of(1, 3, 9),
				value -> Text.literal(value + "x"),
				() -> EGG_AUTO_OPEN_AMOUNT,
				value -> {
					EGG_AUTO_OPEN_AMOUNT = normalizeEggAutoOpenAmount(value);
					savePersistedSettings();
				}
			);
			this.eggPostOpenDelayField = addNumberRow(row++, "Post-Open Delay (ms)", EGG_POST_OPEN_DELAY_MS);
			this.eggClickDelayField = addNumberRow(row++, "Click Delay (ms)", EGG_CLICK_DELAY_MS);
		}

		private void buildRingScrapperTab() {
			int row = 0;
			this.ringScrapperClickDelayField = addNumberRow(row++, "Click Delay (ms)", RING_SCRAPPER_CLICK_DELAY_MS);
			this.infoLines.add(new InfoLine(Text.literal("Flow: /rings -> Ring Scrapper -> Scrapper."), this.labelX, rowY(row) + 6));
			row++;
			this.infoLines.add(new InfoLine(
				Text.literal("WARNING: This scraps every ring in your inventory.").formatted(Formatting.RED),
				this.labelX,
				rowY(row) + 6
			));
		}

		private void buildMuseumTab() {
			int row = 0;
			this.museumPvField = addNumberRow(row++, "PV Number", MUSEUM_PV_NUMBER);
			this.museumClickDelayField = addNumberRow(row++, "Click Delay (ms)", MUSEUM_CLICK_DELAY_MS);
		}

		private void buildBuffsTab() {
			int row = 0;
			this.buffsModeDropdown = addTriggerModeRow(row++, "Mode", BUFFS_ENABLED, BUFFS_WEBHOOK_ONLY);
			this.buffsWebhookPingToggle = addToggleRow(
				row++,
				"Webhook Ping",
				() -> BUFFS_WEBHOOK_PING_ENABLED.get(),
				value -> {
					BUFFS_WEBHOOK_PING_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.buffsTriggerField = addTextRow(row++, "Trigger", BUFFS_TRIGGER, "Chat trigger text");
			this.buffsClickDelayField = addNumberRow(row++, "Click Delay (ms)", BUFFS_CLICK_DELAY_MS);
		}

		private void buildStoreTab() {
			int row = 0;
			this.storeModeDropdown = addTriggerModeRow(row++, "Mode", STORE_PURCHASE_ENABLED, STORE_WEBHOOK_ONLY);
			this.storeWebhookPingToggle = addToggleRow(
				row++,
				"Webhook Ping",
				() -> STORE_WEBHOOK_PING_ENABLED.get(),
				value -> {
					STORE_WEBHOOK_PING_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.storeTriggerField = addTextRow(row++, "Trigger", STORE_PURCHASE_TRIGGER, "Chat trigger text");
			this.autoStoreDelayField = addNumberRow(row++, "Auto Store Delay (ms)", AUTO_STORE_DELAY_MS);
		}

		private void buildWebhookTab() {
			int row = 0;
			this.webhookUrlField = addTextRow(row++, "Webhook URL", WEBHOOK_URL, "Discord webhook URL");
			this.testWebhookButton = addActionRow(row++, "Test", Text.literal("Send Test"), true, this::sendTestWebhook);
		}

		private void buildBossTab() {
			int row = 0;
			this.webhookToggle = addToggleRow(
				row++,
				"Boss Notifier",
				() -> WEBHOOK_ENABLED.get(),
				value -> {
					WEBHOOK_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.bossWebhookPingToggle = addToggleRow(
				row++,
				"Webhook Ping",
				() -> BOSS_WEBHOOK_PING_ENABLED.get(),
				value -> {
					BOSS_WEBHOOK_PING_ENABLED.set(value);
					savePersistedSettings();
				}
			);
			this.bossTriggerField = addTextRow(row++, "Boss Trigger", BOSS_SPAWN_TRIGGER, "Boss spawn trigger text");
			this.bossNotifyDelayField = addNumberRow(row++, "Second Ping Delay (ms)", BOSS_NOTIFY_DELAY_MS);
			this.webhookPingCountField = addNumberRow(row++, "Ping Count", WEBHOOK_PING_COUNT);
			this.webhookDelayField = addNumberRow(row++, "Ping Gap (ms)", WEBHOOK_DELAY_MS);
		}

		private void buildDailiesTab() {
			int row = 0;
			this.autoDailyToggle = addToggleRow(
				row++,
				"Auto Daily",
				() -> AUTO_DAILY_ENABLED.get(),
				value -> {
					AUTO_DAILY_ENABLED.set(value);
					// Run immediately after enabling.
					if (value) {
						AUTO_DAILY_NEXT_ENQUEUE_AT_MS = 0L;
						AUTO_DAILY_PERKS_LAST_ATTEMPT_MS = 0L;
						AUTO_DAILY_FREECREDITS_LAST_ATTEMPT_MS = 0L;
						AUTO_DAILY_KEYALL_LAST_ATTEMPT_MS = 0L;
					}
					savePersistedSettings();
				}
			);

			this.autoDailyPerksToggle = addToggleRow(
				row++,
				"Perks (/perks)",
				() -> AUTO_DAILY_PERKS_ENABLED.get(),
				value -> {
					AUTO_DAILY_PERKS_ENABLED.set(value);
					if (value) {
						AUTO_DAILY_NEXT_ENQUEUE_AT_MS = 0L;
						AUTO_DAILY_PERKS_LAST_ATTEMPT_MS = 0L;
					}
					savePersistedSettings();
				}
			);
			this.autoDailyFreecreditsToggle = addToggleRow(
				row++,
				"Freecredits (/freecredits)",
				() -> AUTO_DAILY_FREECREDITS_ENABLED.get(),
				value -> {
					AUTO_DAILY_FREECREDITS_ENABLED.set(value);
					if (value) {
						AUTO_DAILY_NEXT_ENQUEUE_AT_MS = 0L;
						AUTO_DAILY_FREECREDITS_LAST_ATTEMPT_MS = 0L;
					}
					savePersistedSettings();
				}
			);

			this.autoDailyKeyallToggle = addToggleRow(
				row++,
				"Keyall (/donator-keyall)",
				() -> AUTO_DAILY_KEYALL_ENABLED.get(),
				value -> {
					AUTO_DAILY_KEYALL_ENABLED.set(value);
					if (value) {
						AUTO_DAILY_NEXT_ENQUEUE_AT_MS = 0L;
						AUTO_DAILY_KEYALL_LAST_ATTEMPT_MS = 0L;
					}
					savePersistedSettings();
				}
			);

			int y = rowY(row) + 6;
			this.infoLines.add(new InfoLine(
				Text.literal("Last /perks: " + (AUTO_DAILY_PERKS_LAST_RUN.isBlank() ? "never" : AUTO_DAILY_PERKS_LAST_RUN)),
				this.labelX,
				y
			));
			y += 12;
			this.infoLines.add(new InfoLine(
				Text.literal("Last /freecredits: " + (AUTO_DAILY_FREECREDITS_LAST_RUN.isBlank() ? "never" : AUTO_DAILY_FREECREDITS_LAST_RUN)),
				this.labelX,
				y
			));
			y += 12;
			this.infoLines.add(new InfoLine(
				Text.literal("Last /donator-keyall: " + (AUTO_DAILY_KEYALL_LAST_RUN.isBlank() ? "never" : AUTO_DAILY_KEYALL_LAST_RUN)),
				this.labelX,
				y
			));
		}

		private static final class RowLabel {
			private final Text text;
			private final int x;
			private final int y;

			private RowLabel(Text text, int x, int y) {
				this.text = text;
				this.x = x;
				this.y = y;
			}
		}

		private static final class InfoLine {
			private final Text text;
			private final int x;
			private final int y;

			private InfoLine(Text text, int x, int y) {
				this.text = text;
				this.x = x;
				this.y = y;
			}
		}

		private enum DashboardTab {
			GENERAL("General"),
			BLACKMARKET("Blackmarket"),
			GEMSHOP("Gemshop"),
			MERCHANT("Merchant"),
			TRAITS("Traits"),
			COOKIES("Cookies"),
			MEDIA("Media"),
			SETUP_SWAP("Setup Swap"),
			EGG("Egg"),
			RING_SCRAPPER("Ring Scrapper"),
			MUSEUM("Museum"),
			BUFFS("Buffs"),
			STORE("Store"),
			WEBHOOK("Webhook"),
			BOSS("Boss"),
			DAILIES("Dailies");

			private final String label;

			DashboardTab(String label) {
				this.label = label;
			}
		}

		private static float clamp01(float value) {
			return MathHelper.clamp(value, 0F, 1F);
		}

		private static float easeOutCubic(float t) {
			float clamped = clamp01(t);
			float inv = 1F - clamped;
			return 1F - inv * inv * inv;
		}

		private static float approachExponential(float current, float target, float rate) {
			float clamped = clamp01(rate);
			return current + (target - current) * clamped;
		}

		private static int mulAlpha(int argb, float alpha) {
			int a = (argb >>> 24) & 0xFF;
			int nextA = MathHelper.clamp(Math.round(a * clamp01(alpha)), 0, 255);
			return (argb & 0x00FFFFFF) | (nextA << 24);
		}

		private static void drawRoundedRectWithBorder(
			net.minecraft.client.gui.DrawContext context,
			int x,
			int y,
			int w,
			int h,
			int radius,
			int bg,
			int border
		) {
			// Border AA is expensive with lots of controls; use the cached rounded mask without fringe AA.
			fillRoundedRectNoAa(context, x, y, w, h, radius, border, true, true, true, true);
			fillRoundedRectNoAa(context, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), bg, true, true, true, true);
		}

		private static void drawRoundedRectWithBorder(
			net.minecraft.client.gui.DrawContext context,
			int x,
			int y,
			int w,
			int h,
			int radius,
			int bg,
			int border,
			boolean roundTopLeft,
			boolean roundTopRight,
			boolean roundBottomLeft,
			boolean roundBottomRight
		) {
			fillRoundedRectNoAa(context, x, y, w, h, radius, border, roundTopLeft, roundTopRight, roundBottomLeft, roundBottomRight);
			fillRoundedRectNoAa(
				context,
				x + 1,
				y + 1,
				w - 2,
				h - 2,
				Math.max(0, radius - 1),
				bg,
				roundTopLeft,
				roundTopRight,
				roundBottomLeft,
				roundBottomRight
			);
		}

		private static void fillRoundedRectNoAa(
			net.minecraft.client.gui.DrawContext context,
			int x,
			int y,
			int w,
			int h,
			int radius,
			int color,
			boolean roundTopLeft,
			boolean roundTopRight,
			boolean roundBottomLeft,
			boolean roundBottomRight
		) {
			fillRoundedRectInternal(context, x, y, w, h, radius, color, roundTopLeft, roundTopRight, roundBottomLeft, roundBottomRight, false);
		}

		private static void fillRoundedRect(
			net.minecraft.client.gui.DrawContext context,
			int x,
			int y,
			int w,
			int h,
			int radius,
			int color,
			boolean roundTopLeft,
			boolean roundTopRight,
			boolean roundBottomLeft,
			boolean roundBottomRight
		) {
			fillRoundedRectInternal(context, x, y, w, h, radius, color, roundTopLeft, roundTopRight, roundBottomLeft, roundBottomRight, true);
		}

		private static void fillRoundedRectInternal(
			net.minecraft.client.gui.DrawContext context,
			int x,
			int y,
			int w,
			int h,
			int radius,
			int color,
			boolean roundTopLeft,
			boolean roundTopRight,
			boolean roundBottomLeft,
			boolean roundBottomRight,
			boolean aaFringe
		) {
			if (w <= 0 || h <= 0) {
				return;
			}
			int r = MathHelper.clamp(radius, 0, Math.min(w, h) / 2);
			if (r == 0) {
				context.fill(x, y, x + w, y + h, color);
				return;
			}

			int x2 = x + w;
			int y2 = y + h;
			int innerW = w - 2 * r;
			int innerH = h - 2 * r;

			if (innerW > 0) {
				context.fill(x + r, y, x2 - r, y2, color);
			}

			if (innerH > 0) {
				context.fill(x, y + r, x + r, y2 - r, color);
				context.fill(x2 - r, y + r, x2, y2 - r, color);
			}

			// Corners: the base mask is built for the top-left corner and mirrored for others.
			if (roundTopLeft) {
				fillCornerMask(context, x, y, r, color, false, false, aaFringe);
			} else {
				context.fill(x, y, x + r, y + r, color);
			}
			if (roundTopRight) {
				fillCornerMask(context, x2 - r, y, r, color, true, false, aaFringe);
			} else {
				context.fill(x2 - r, y, x2, y + r, color);
			}
			if (roundBottomLeft) {
				fillCornerMask(context, x, y2 - r, r, color, false, true, aaFringe);
			} else {
				context.fill(x, y2 - r, x + r, y2, color);
			}
			if (roundBottomRight) {
				fillCornerMask(context, x2 - r, y2 - r, r, color, true, true, aaFringe);
			} else {
				context.fill(x2 - r, y2 - r, x2, y2, color);
			}
		}

		// This runs on the render thread only; a simple array avoids ConcurrentHashMap overhead.
		private static final CornerMaskCache[] CORNER_MASK_CACHE = new CornerMaskCache[48];

		private static CornerMaskCache cornerMaskCache(int radius) {
			int r = Math.max(0, radius);
			if (r < CORNER_MASK_CACHE.length) {
				CornerMaskCache cached = CORNER_MASK_CACHE[r];
				if (cached != null) {
					return cached;
				}
				CornerMaskCache created = new CornerMaskCache(r);
				CORNER_MASK_CACHE[r] = created;
				return created;
			}
			return new CornerMaskCache(r);
		}

		private static final class CornerMaskCache {
			private final int radius;
			private final int[] dxStartByDy;
			private final int[] aaAlphaByDy;

			private CornerMaskCache(int radius) {
				this.radius = Math.max(0, radius);
				this.dxStartByDy = new int[this.radius];
				this.aaAlphaByDy = new int[this.radius];
				if (this.radius <= 0) {
					return;
				}

				float center = this.radius - 0.5F;
				float radiusSq = center * center;
				for (int dy = 0; dy < this.radius; dy++) {
					float fy = dy + 0.5F;
					float dyTerm = (fy - center) * (fy - center);
					float remaining = radiusSq - dyTerm;
					if (remaining <= 0F) {
						this.dxStartByDy[dy] = this.radius;
						this.aaAlphaByDy[dy] = 0;
						continue;
					}

					float sqrtRem = (float) Math.sqrt(remaining);
					float minFx = center - sqrtRem;
					int dxStart = (int) Math.ceil(minFx - 0.5F);
					dxStart = MathHelper.clamp(dxStart, 0, this.radius - 1);
					this.dxStartByDy[dy] = dxStart;

					int dxBoundary = dxStart - 1;
					if (dxBoundary < 0) {
						this.aaAlphaByDy[dy] = 0;
						continue;
					}

					float fx = dxBoundary + 0.5F;
					float distSq = (fx - center) * (fx - center) + dyTerm;
					float dist = (float) Math.sqrt(distSq);
					float coverage = clamp01((center + 0.5F) - dist);
					this.aaAlphaByDy[dy] = MathHelper.clamp(Math.round(coverage * 255F), 0, 255);
				}
			}
		}

		private static void fillCornerMask(
			net.minecraft.client.gui.DrawContext context,
			int x,
			int y,
			int radius,
			int color,
			boolean mirrorX,
			boolean mirrorY,
			boolean aaFringe
		) {
			if (radius <= 0) {
				return;
			}

			// Cached corner spans; avoids per-frame sqrt() and drastically reduces GUI CPU usage.
			CornerMaskCache cache = cornerMaskCache(radius);
			for (int dy = 0; dy < radius; dy++) {
				int dxStart = cache.dxStartByDy[dy];
				if (dxStart >= radius) {
					continue;
				}

				int py = mirrorY ? (radius - 1 - dy) : dy;
				int pxMin = mirrorX ? 0 : dxStart;
				int pxMax = mirrorX ? (radius - 1 - dxStart) : (radius - 1);
				if (pxMin <= pxMax) {
					context.fill(x + pxMin, y + py, x + pxMax + 1, y + py + 1, color);
				}

				if (!aaFringe) {
					continue;
				}
				int dxBoundary = dxStart - 1;
				if (dxBoundary < 0) {
					continue;
				}
				int aaAlpha = cache.aaAlphaByDy[dy];
				if (aaAlpha <= 0) {
					continue;
				}
				int px = mirrorX ? (radius - 1 - dxBoundary) : dxBoundary;
				if (px < 0 || px >= radius) {
					continue;
				}
				context.fill(x + px, y + py, x + px + 1, y + py + 1, mulAlpha(color, aaAlpha / 255F));
			}
		}

		private static final class UiPalette {
			private static final UiPalette DARK = new UiPalette(
				0xAA0B0E14, // overlay
				0xFF121722, // panel
				0xFF0F141E, // sidebar
				0xFF243044, // divider
				0xFFE7EAF0, // text primary
				0xFFAAB3C2, // text secondary
				0xFF4E8EF7, // accent
				0xFF1B2536, // button bg
				0xFF223149, // button hover
				0xFF101826, // field bg
				0xFF2A3A55  // field border
			);

			private static final UiPalette LIGHT = new UiPalette(
				0xAA0B0E14, // overlay
				0xFFE3E8F0, // panel
				0xFFD6DDE8, // sidebar
				0xFFB8C2D6, // divider
				0xFF1D2530, // text primary
				0xFF556172, // text secondary
				0xFF2E6BE6, // accent
				0xFFD0D9E6, // button bg
				0xFFC2CDDE, // button hover
				0xFFEFF3F9, // field bg
				0xFFB5C0D3  // field border
			);

			private final int overlayBg;
			private final int panelBg;
			private final int sidebarBg;
			private final int divider;
			private final int textPrimary;
			private final int textSecondary;
			private final int accent;
			private final int buttonBg;
			private final int buttonHover;
			private final int fieldBg;
			private final int fieldBorder;

			private UiPalette(
				int overlayBg,
				int panelBg,
				int sidebarBg,
				int divider,
				int textPrimary,
				int textSecondary,
				int accent,
				int buttonBg,
				int buttonHover,
				int fieldBg,
				int fieldBorder
			) {
				this.overlayBg = overlayBg;
				this.panelBg = panelBg;
				this.sidebarBg = sidebarBg;
				this.divider = divider;
				this.textPrimary = textPrimary;
				this.textSecondary = textSecondary;
				this.accent = accent;
				this.buttonBg = buttonBg;
				this.buttonHover = buttonHover;
				this.fieldBg = fieldBg;
				this.fieldBorder = fieldBorder;
			}
		}

		private static final class TabButtonWidget extends ClickableWidget {
			private final SalesDashboardScreen screen;
			private final DashboardTab tab;
			private boolean selected;

			private TabButtonWidget(SalesDashboardScreen screen, int x, int y, int width, int height, DashboardTab tab) {
				super(x, y, width, height, Text.literal(tab.label));
				this.screen = screen;
				this.tab = tab;
			}

			private void setSelected(boolean selected) {
				this.selected = selected;
			}

			@Override
			public void onClick(net.minecraft.client.gui.Click click, boolean doubleClick) {
				this.screen.applyFields(false);
				this.screen.activeTab = this.tab;
				this.screen.clearAndInit();
			}

			@Override
			protected void renderWidget(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
				UiPalette palette = this.screen.palette();
				boolean hovered = this.isHovered();
				int x = getX();
				int y = getY();
				int w = getWidth();
				int h = getHeight();

				if (this.selected || hovered) {
					int bg = this.selected ? palette.buttonHover : palette.buttonBg;
					fillRoundedRect(context, x, y, w, h, TAB_RADIUS, bg, true, true, true, true);
				}
				if (this.selected) {
					fillRoundedRect(context, x, y, 3, h, TAB_RADIUS, palette.accent, true, false, true, false);
				}
				context.drawText(
					this.screen.textRenderer,
					this.getMessage(),
					x + 10,
					y + (h - this.screen.textRenderer.fontHeight) / 2,
					palette.textPrimary,
					false
				);
			}

			@Override
			protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
				this.appendDefaultNarrations(builder);
			}
		}

		private static final class ActionButtonWidget extends ClickableWidget {
			private final SalesDashboardScreen screen;
			private final boolean primary;
			private final Runnable onPress;

			private ActionButtonWidget(
				SalesDashboardScreen screen,
				int x,
				int y,
				int width,
				int height,
				Text message,
				boolean primary,
				Runnable onPress
			) {
				super(x, y, width, height, message);
				this.screen = screen;
				this.primary = primary;
				this.onPress = onPress;
			}

			@Override
			public void onClick(net.minecraft.client.gui.Click click, boolean doubleClick) {
				if (this.onPress != null) {
					this.onPress.run();
				}
			}

			@Override
			protected void renderWidget(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
				UiPalette palette = this.screen.palette();
				boolean hovered = this.isHovered();
				int bg = this.primary ? palette.accent : palette.buttonBg;
				if (hovered) {
					bg = this.primary ? palette.accent : palette.buttonHover;
				}
				int border = hovered ? palette.accent : mulAlpha(palette.fieldBorder, 0.85F);
				drawRoundedRectWithBorder(context, getX(), getY(), getWidth(), getHeight(), BUTTON_RADIUS, bg, border);

				int textColor = this.primary ? 0xFFFFFFFF : palette.textPrimary;
				Text text = this.getMessage();
				int textX = getX() + (getWidth() - this.screen.textRenderer.getWidth(text)) / 2;
				int textY = getY() + (getHeight() - this.screen.textRenderer.fontHeight) / 2;
				context.drawText(this.screen.textRenderer, text, textX, textY, textColor, false);
			}

			@Override
			protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
				this.appendDefaultNarrations(builder);
			}
		}

		private static final class ToggleSwitchWidget extends ClickableWidget {
			private static final int WIDTH = 42;
			private static final int HEIGHT = 18;

			private final SalesDashboardScreen screen;
			private final Supplier<Boolean> getter;
			private final Consumer<Boolean> setter;
			private float knobAnim;
			private long lastAnimTimeNs;

			private ToggleSwitchWidget(SalesDashboardScreen screen, int x, int y, Supplier<Boolean> getter, Consumer<Boolean> setter) {
				super(x, y, WIDTH, HEIGHT, Text.empty());
				this.screen = screen;
				this.getter = getter;
				this.setter = setter;
				this.knobAnim = Boolean.TRUE.equals(getter.get()) ? 1F : 0F;
				this.lastAnimTimeNs = System.nanoTime();
			}

			@Override
			public void onClick(net.minecraft.client.gui.Click click, boolean doubleClick) {
				boolean next = !Boolean.TRUE.equals(this.getter.get());
				this.setter.accept(next);
			}

			@Override
			protected void renderWidget(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
				UiPalette palette = this.screen.palette();
				boolean on = Boolean.TRUE.equals(this.getter.get());
				int track = on ? palette.accent : mulAlpha(palette.fieldBorder, 0.95F);
				int knob = palette.panelBg;

				int x = getX();
				int y = getY();
				int w = getWidth();
				int h = getHeight();

				long now = System.nanoTime();
				float dt = (now - this.lastAnimTimeNs) / 1_000_000_000F;
				this.lastAnimTimeNs = now;
				this.knobAnim = approachExponential(this.knobAnim, on ? 1F : 0F, dt * 14F);

				fillRoundedRectNoAa(context, x, y, w, h, h / 2, track, true, true, true, true);
				int pad = 2;
				int knobSize = h - pad * 2;
				int travel = Math.max(0, w - pad * 2 - knobSize);
				int knobX = x + pad + Math.round(travel * this.knobAnim);
				fillRoundedRectNoAa(context, knobX, y + pad, knobSize, knobSize, knobSize / 2, knob, true, true, true, true);
			}

			@Override
			protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
				this.appendDefaultNarrations(builder);
			}
		}

		private static final class DropdownWidget<T> extends ClickableWidget {
			private static final int ITEM_HEIGHT = 18;
			// EggType (and similar lists) exceed 7 entries; allow showing the full list without requiring scrolling.
			// overlayVisibleCount() still clamps by available panel space, so this is safe on small GUIs.
			private static final int MAX_VISIBLE = 13;
			private static final int OVERLAY_PADDING_Y = 2;
			private static final Text DEFAULT_LABEL = Text.literal("-");
			private static final Text ARROW_UP = Text.literal("^");
			private static final Text ARROW_DOWN = Text.literal("v");

			private final SalesDashboardScreen screen;
			private final List<T> values;
			private final java.util.function.Function<T, Text> formatter;
			private final Supplier<T> getter;
			private final Consumer<T> setter;
			private final java.util.Map<T, Text> formattedCache;
			private boolean open;
			private int scrollIndex;
			private float openAnim;
			private long lastAnimTimeNs;
			private List<T> cachedOptions;
			private T cachedCurrent;

			private DropdownWidget(
				SalesDashboardScreen screen,
				int x,
				int y,
				int width,
				int height,
				List<T> values,
				java.util.function.Function<T, Text> formatter,
				Supplier<T> getter,
				Consumer<T> setter
			) {
				super(x, y, width, height, Text.empty());
				this.screen = screen;
				this.values = values == null ? List.of() : List.copyOf(values);
				this.formatter = formatter;
				this.getter = getter;
				this.setter = setter;
				this.formattedCache = new java.util.HashMap<>();
				this.open = false;
				this.scrollIndex = 0;
				this.openAnim = 0F;
				this.lastAnimTimeNs = System.nanoTime();
				this.cachedOptions = List.of();
				this.cachedCurrent = null;
			}

			private void setOpen(boolean open) {
				this.open = open;
				this.lastAnimTimeNs = System.nanoTime();
				if (open) {
					this.screen.openDropdown = this;
				}
			}

			@Override
			public void onClick(net.minecraft.client.gui.Click click, boolean doubleClick) {
				if (this.open) {
					setOpen(false);
					return;
				}
				List<T> options = optionsList();
				if (options.isEmpty()) {
					return;
				}
				if (this.screen.openDropdown != null && this.screen.openDropdown != this) {
					this.screen.openDropdown.setOpen(false);
				}
				this.scrollIndex = 0;
				setOpen(true);
			}

			@Override
			protected void renderWidget(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
				updateAnimation();

				UiPalette palette = this.screen.palette();
				int x = getX();
				int y = getY();
				int w = getWidth();
				int h = getHeight();

				// Treat the dropdown as "expanded" only while the overlay has meaningful height.
				// This avoids sharp bottom corners during the last frames of the close animation.
				List<T> options = optionsList();
				int overlayHeight = options.isEmpty() ? 0 : overlayAnimatedHeight(options.size());
				boolean expanded = overlayHeight > CONTROL_RADIUS * 2;
				int border = (this.open || expanded || this.isHovered()) ? palette.accent : palette.fieldBorder;
				drawRoundedRectWithBorder(
					context,
					x,
					y,
					w,
					h,
					CONTROL_RADIUS,
					palette.fieldBg,
					border,
					true,
					true,
					!expanded,
					!expanded
				);

					Text label = formatValue(this.getter.get());
					int textY = y + (h - 8) / 2;
					context.drawText(this.screen.textRenderer, label, x + 6, textY, palette.textPrimary, false);
					context.drawText(this.screen.textRenderer, this.open ? ARROW_UP : ARROW_DOWN, x + w - 10, textY, palette.textSecondary, false);
			}

			private Text formatValue(T value) {
				if (value == null) {
					return DEFAULT_LABEL;
				}
				Text cached = this.formattedCache.get(value);
				if (cached != null) {
					return cached;
				}
				Text formatted = this.formatter.apply(value);
				this.formattedCache.put(value, formatted);
				return formatted;
			}

			private void updateAnimation() {
				long now = System.nanoTime();
				float dt = (now - this.lastAnimTimeNs) / 1_000_000_000F;
				this.lastAnimTimeNs = now;
				this.openAnim = approachExponential(this.openAnim, this.open ? 1F : 0F, dt * 18F);
				if (!this.open && this.openAnim <= 0.002F && this.screen.openDropdown == this) {
					this.screen.openDropdown = null;
				}
			}

			private List<T> optionsList() {
				T current = this.getter.get();
				boolean changed;
				if (this.cachedCurrent == null) {
					changed = current != null;
				} else {
					changed = current == null || !this.cachedCurrent.equals(current);
				}
				if (!changed) {
					return this.cachedOptions;
				}

				this.cachedCurrent = current;
				if (this.values.isEmpty()) {
					this.cachedOptions = List.of();
					return this.cachedOptions;
				}
				List<T> options = new ArrayList<>(this.values.size());
				for (T value : this.values) {
					if (current != null && current.equals(value)) {
						continue;
					}
					options.add(value);
				}
				this.cachedOptions = List.copyOf(options);
				return this.cachedOptions;
			}

			private int overlayY() {
				// Overlap by 1px to visually merge the header and the list into one expanding box.
				return getY() + getHeight() - 1;
			}

			private int overlayVisibleCount(int optionCount) {
				if (optionCount <= 0) {
					return 0;
				}
				int desired = Math.min(optionCount, MAX_VISIBLE);
				int available = (this.screen.panelY + this.screen.panelH - 10) - overlayY();
				int maxBySpace = Math.max(1, available / ITEM_HEIGHT);
				return Math.min(desired, Math.min(MAX_VISIBLE, maxBySpace));
			}

			private int overlayFullHeight(int optionCount) {
				return overlayVisibleCount(optionCount) * ITEM_HEIGHT + OVERLAY_PADDING_Y * 2;
			}

			private int overlayAnimatedHeight(int optionCount) {
				int full = overlayFullHeight(optionCount);
				if (full <= 0) {
					return 0;
				}
				return Math.round(full * easeOutCubic(this.openAnim));
			}

			private boolean isMouseOverOverlay(double mouseX, double mouseY) {
				if (this.openAnim <= 0.002F) {
					return false;
				}
				List<T> options = optionsList();
				if (options.isEmpty()) {
					return false;
				}
				int x = getX();
				int y = overlayY();
				int w = getWidth();
				int h = overlayAnimatedHeight(options.size());
				if (h <= CONTROL_RADIUS * 2) {
					return false;
				}
				return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
			}

			private void renderOverlay(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
				if (this.openAnim <= 0.002F) {
					return;
				}
				UiPalette palette = this.screen.palette();
				List<T> options = optionsList();
				if (options.isEmpty()) {
					return;
				}
				int x = getX();
				int y = overlayY();
				int w = getWidth();
				int visible = overlayVisibleCount(options.size());
				int fullH = overlayFullHeight(options.size());
				int h = overlayAnimatedHeight(options.size());
				if (visible <= 0 || fullH <= 0 || h <= CONTROL_RADIUS * 2) {
					return;
				}

				int border = (this.open || this.isHovered()) ? palette.accent : palette.fieldBorder;
				int bg = palette.fieldBg;
				drawRoundedRectWithBorder(context, x, y, w, h, CONTROL_RADIUS, bg, border, false, false, true, true);
				// Remove top border line to keep it as one box with the header.
				context.fill(x + 1, y, x + w - 1, y + 1, bg);

				int maxStart = Math.max(0, options.size() - visible);
				this.scrollIndex = MathHelper.clamp(this.scrollIndex, 0, maxStart);

				float eased = easeOutCubic(this.openAnim);
				int hoverBg = mulAlpha(palette.buttonHover, eased);
				int textColor = mulAlpha(palette.textPrimary, eased);
				int itemTop = y + OVERLAY_PADDING_Y;
				int itemBottomLimit = y + h - OVERLAY_PADDING_Y;

				for (int i = 0; i < visible; i++) {
					int idx = this.scrollIndex + i;
					if (idx < 0 || idx >= options.size()) {
						continue;
					}
					int itemY = itemTop + i * ITEM_HEIGHT;
					if (itemY + ITEM_HEIGHT > itemBottomLimit) {
						break;
					}
					boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
					if (hovered) {
						fillRoundedRectNoAa(context, x + 2, itemY, w - 4, ITEM_HEIGHT, 6, hoverBg, true, true, true, true);
					}
					Text text = formatValue(options.get(idx));
					context.drawText(
						this.screen.textRenderer,
						text,
						x + 6,
						itemY + (ITEM_HEIGHT - this.screen.textRenderer.fontHeight) / 2 + 2,
						textColor,
						false
					);
				}
			}

			private boolean handleOverlayClick(double mouseX, double mouseY, int button) {
				if (button != 0 || this.openAnim <= 0.002F) {
					return false;
				}
				if (!isMouseOverOverlay(mouseX, mouseY)) {
					return false;
				}
				List<T> options = optionsList();
				if (options.isEmpty()) {
					setOpen(false);
					return true;
				}
				int y = overlayY();
				int index = (int) ((mouseY - (y + OVERLAY_PADDING_Y)) / ITEM_HEIGHT);
				int visible = overlayVisibleCount(options.size());
				if (index < 0 || index >= visible) {
					return true;
				}
				int selectedIndex = this.scrollIndex + index;
				if (selectedIndex >= 0 && selectedIndex < options.size()) {
					this.setter.accept(options.get(selectedIndex));
				}
				setOpen(false);
				return true;
			}

			private boolean handleOverlayScroll(double mouseX, double mouseY, double verticalAmount) {
				if (this.openAnim <= 0.002F || !isMouseOverOverlay(mouseX, mouseY)) {
					return false;
				}
				List<T> options = optionsList();
				if (options.isEmpty()) {
					return true;
				}
				int visible = overlayVisibleCount(options.size());
				int maxStart = Math.max(0, options.size() - visible);
				if (maxStart == 0) {
					return true;
				}
				int delta = verticalAmount > 0 ? -1 : 1;
				this.scrollIndex = MathHelper.clamp(this.scrollIndex + delta, 0, maxStart);
				return true;
			}

			@Override
			protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
				this.appendDefaultNarrations(builder);
			}
		}

				private static final class StyledTextFieldWidget extends TextFieldWidget {
					private final SalesDashboardScreen screen;
					private final Text placeholder;

				private StyledTextFieldWidget(SalesDashboardScreen screen, int x, int y, int width, int height, String placeholder) {
					super(screen.textRenderer, x, y, width, height, Text.literal(""));
					this.screen = screen;
					this.placeholder = Text.literal(placeholder == null ? "" : placeholder);
					this.setTextShadow(false);
					// TextFieldWidget "suggestion" renders as a suffix, not as a placeholder. We render our own placeholder.
					this.setSuggestion("");
				}

				@Override
				public boolean drawsBackground() {
					// Keep TextFieldWidget's internal layout (padding + vertical centering) but skip the vanilla texture render.
					return false;
				}

				@Override
				public int getInnerWidth() {
					// Match TextFieldWidget's background-on padding (4px left + 4px right).
					return Math.max(0, getWidth() - 8);
				}

				@Override
					public void renderWidget(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
						UiPalette palette = this.screen.palette();
						int baseX = getX();
						int baseY = getY();
						int baseW = getWidth();
						int baseH = getHeight();

					boolean focused = this.isFocused();
					boolean hovered = mouseX >= baseX && mouseX < baseX + baseW && mouseY >= baseY && mouseY < baseY + baseH;
					int border = focused ? palette.accent : (hovered ? mulAlpha(palette.fieldBorder, 0.95F) : palette.fieldBorder);
					drawRoundedRectWithBorder(context, baseX, baseY, baseW, baseH, CONTROL_RADIUS, palette.fieldBg, border);

						this.setEditableColor(palette.textPrimary);
						int contentXOffset = 4;
						int contentYOffset = (baseH - 8) / 2;
						int textX = baseX + contentXOffset;
						int textY = baseY + contentYOffset;
						if (!focused && this.getText().isEmpty() && !this.placeholder.getString().isBlank()) {
							context.drawText(
								this.screen.textRenderer,
								this.placeholder,
								textX,
								textY,
								mulAlpha(palette.textSecondary, 0.75F),
								false
							);
						}
						super.renderWidget(context, mouseX, mouseY, delta);
					}
				}
			}


