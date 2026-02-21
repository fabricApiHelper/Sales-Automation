package javamod;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javamod.mixin.ScreenAddDrawableChildAccessor;

public class SalesClientMod implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("javamod-sales");
	private static final String CHAT_PREFIX = "[Sales] ";
	private static final String SETTINGS_FILE_NAME = "javamod-sales.properties";
	private static final String BRANDING_MEDIA_DIR_NAME = "javamod-media";
	private static final String BRANDING_TITLESCREEN_DIR_NAME = "Titlescreen";
	private static final String BRANDING_STARTMUSIC_DIR_NAME = "Startmusic";
	private static final Object SETTINGS_LOCK = new Object();
	private static final int SETTINGS_SCHEMA_VERSION = 15;
	private static final KeyBinding.Category SALES_KEYBIND_CATEGORY =
		KeyBinding.Category.create(Identifier.of("javamod", "sales"));

	private static final String DEFAULT_WEBHOOK_URL = "";
	private static final String SALES_SERVER_IP = "sales.minehut.gg";
	private static final String SALES_SERVER_PROXY_IP = "sales.minekeep.gg";
	private static final List<String> SALES_SERVER_HOST_ALIASES = List.of(SALES_SERVER_IP, SALES_SERVER_PROXY_IP);
	private static volatile String LAST_SALES_SERVER_HOST = SALES_SERVER_IP;
	private static final String SALES_SERVER_NAME = "Sales Server";
	private static final DateTimeFormatter AUTO_DAILY_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter AUTO_DAILY_TIMESTAMP_SECONDS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final Pattern FROM_CRYSTAL_TYPE_PATTERN = Pattern.compile(
		"(?i)from\\s+crystal\\s*:?\\s*(common|rare|epic|legendary|mythic|gothic)"
	);
	private static final Pattern CRYSTAL_TYPE_LINE_PATTERN = Pattern.compile(
		"(?im)^\\s*(common|rare|epic|legendary|mythic|gothic)\\b"
	);
	private static final Pattern BOOSTER_PERCENT_PATTERN = Pattern.compile(
		"(?i)booster\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%"
	);
	// Some servers/clients deliver player chat as "system" messages (tellraw), i.e. without sender metadata.
	// This heuristic detects common chat-line formats to prevent players from spoofing trigger phrases.
	private static final Pattern PLAYER_CHAT_LINE_PATTERN = Pattern.compile(
		"^\\s*(?:\\[[^\\]]{1,64}\\]\\s*)*(?:<)?[A-Za-z0-9_]{3,16}(?:>)?\\s*(?::|\u00BB|\u2192|->)\\s*.+$"
	);
	// More permissive speaker extraction: allows extra non-whitespace tokens (e.g. rank prefixes) and then verifies
	// the extracted token is a real online player before treating it as player chat.
	private static final Pattern PLAYER_CHAT_SPEAKER_EXTRACT_PATTERN = Pattern.compile(
		"^\\s*(?:\\[[^\\]]{1,64}\\]\\s*)*(?:\\S{1,32}\\s+){0,8}(?:<)?([A-Za-z0-9_]{3,16})(?:>)?\\s*(?::|\u00BB|\u2192|->)\\s*.+$"
	);
	// Vanilla /me (emote) format: "* <player> <text>" (often delivered as a system message with no sender metadata).
	private static final Pattern PLAYER_EMOTE_SPEAKER_EXTRACT_PATTERN = Pattern.compile(
		"^\\s*\\*\\s*(?:\\[[^\\]]{1,64}\\]\\s*)*(?:\\S{1,32}\\s+){0,8}([A-Za-z0-9_]{3,16})\\s+.+$"
	);
	// Fallback for servers that format chat as "<prefix> <player> <text>" without ":"/"->" separators.
	private static final Pattern PLAYER_NAME_PREFIX_EXTRACT_PATTERN = Pattern.compile(
		"^\\s*(?:\\[[^\\]]{1,64}\\]\\s*)*(?:\\S{1,32}\\s+){0,8}(?:<)?([A-Za-z0-9_]{3,16})(?:>)?\\s+.+$"
	);
	private static final Pattern POSSIBLE_PLAYER_NAME_TOKEN_PATTERN = Pattern.compile(
		"(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})(?![A-Za-z0-9_])"
	);
	// Many server GUIs use different labels (Type/Mode/Setup/Set). Accept all.
	private static final Pattern SETUP_SWAP_TYPE_PATTERN = Pattern.compile(
		"(?i)(?:type|mode|setup|set)\\s*:?\\s*(gem|money|stars|cash)\\b"
	);
	private static final Pattern SETUP_SWAP_WORD_MONEY_PATTERN = Pattern.compile("(?i)\\bmoney\\b");
	private static final Pattern SETUP_SWAP_WORD_GEM_PATTERN = Pattern.compile("(?i)\\bgem\\b");
	private static final Pattern SETUP_SWAP_WORD_STARS_PATTERN = Pattern.compile("(?i)\\bstars\\b");
	private static final Pattern SETUP_SWAP_WORD_STAR_PATTERN = Pattern.compile("(?i)\\bstar(?:s)?\\b");
	private static final Pattern SETUP_SWAP_RING_KIND_PATTERN = Pattern.compile(
		"(?i)type\\s*:?\\s*ring\\b"
	);
	private static final Pattern SETUP_SWAP_BOSS_RELIC_MARKER_PATTERN = Pattern.compile("(?i)\\bboss\\s*relic\\b");
	private static final Pattern SETUP_SWAP_BOOST_PATTERN = Pattern.compile(
		"(?i)boost\\s*:?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%"
	);
	private static final Pattern VAULT_SHORT_COMMAND_PATTERN = Pattern.compile("(?i)^(pv|py)\\s*(\\d+)$");
	private static final Pattern TRAIT_TIER_PATTERN = Pattern.compile("(?i)\\b(banker|midas|wizard)\\s*(i{1,3}|iv|v)\\b");
	private static final Pattern TRAIT_WIZARD_PATTERN = Pattern.compile("(?i)\\bwizard\\b");
	private static final Pattern TRAIT_MIDAS_PATTERN = Pattern.compile("(?i)\\bmidas\\b");
	private static final Pattern TRAIT_BANKER_PATTERN = Pattern.compile("(?i)\\bbanker\\b");
	private static final Pattern TRAIT_ACCOUNTANT_PATTERN = Pattern.compile("(?i)\\baccountant\\b");
	private static final Pattern TRAIT_KING_PATTERN = Pattern.compile("(?i)\\bking\\b");
	private static final Pattern TRAITS_TITLE_WORD_PATTERN = Pattern.compile("(?i)\\btraits\\b");
	private static final Pattern COOKIES_TITLE_WORD_PATTERN = Pattern.compile("(?i)\\bcookies\\b");
	private static final Pattern MERCHANT_TYPE_LINE_PATTERN = Pattern.compile("(?im)^\\s*type\\s*:\\s*(.+?)\\s*$");
	private static final Pattern COOKIE_ROLLED_CHAT_PATTERN = Pattern.compile(
		"(?i)\\byou\\s+rolled\\s+a(?:n)?\\s*\\(?\\s*(s\\+\\+|s\\+|s|a\\+|a|b\\+|b|c\\+|c|d\\+|d|e\\+|e|f\\+|f)\\s*\\)?\\s+cookie\\b"
	);
	static volatile String WEBHOOK_URL = DEFAULT_WEBHOOK_URL;
	static volatile GuiTheme GUI_THEME = GuiTheme.DARK;
	private static final String DEFAULT_BOSS_SPAWN_TRIGGER = "Has Spawned!";
	private static final String DEFAULT_GEMSHOP_TRIGGER = "The Gem Shop has been restocked!";
	private static final String DEFAULT_GEMSHOP_BUY_NAMES = "";
	private static final String DEFAULT_GEMSHOP_STORE_COMMAND = "";
	private static final String DEFAULT_BLACKMARKET_TRIGGER_TEXT = "The Black Market has been restocked!";
	private static final String DEFAULT_BLACKMARKET_BUY_NAMES = "";
	private static final String DEFAULT_BLACKMARKET_STORE_COMMAND = "";
	private static final String DEFAULT_MERCHANT_TRIGGER = "Back in Stock!";
	private static final String DEFAULT_MERCHANT_STORE_COMMAND = "pv 2";
	private static final String DEFAULT_BUFFS_TRIGGER = "Successfully researched";
	private static final String DEFAULT_STORE_PURCHASE_TRIGGER = "Store Purchase";
	private static final String DEFAULT_MERCHANT_PROTECTED_NAMES = "";
	private static final String DEFAULT_MERCHANT_BLACKLIST_NAMES = "";
	private static final String DEFAULT_MERCHANT_WEBHOOK_NOTIFY_NAMES = "";
	static volatile String BOSS_SPAWN_TRIGGER = DEFAULT_BOSS_SPAWN_TRIGGER;
	private static final String WEBHOOK_BOSS_MESSAGE = "Boss has Spawned";
	static volatile String GEMSHOP_TRIGGER = DEFAULT_GEMSHOP_TRIGGER;
	static volatile String GEMSHOP_BUY_NAMES = DEFAULT_GEMSHOP_BUY_NAMES;
	static volatile List<String> GEMSHOP_BUY_NAME_PARTS = List.of();
	static volatile String GEMSHOP_STORE_COMMAND = DEFAULT_GEMSHOP_STORE_COMMAND;
	static volatile String BLACKMARKET_TRIGGER_TEXT = DEFAULT_BLACKMARKET_TRIGGER_TEXT;
	static volatile String BLACKMARKET_BUY_NAMES = DEFAULT_BLACKMARKET_BUY_NAMES;
	static volatile List<String> BLACKMARKET_BUY_NAME_PARTS = List.of();
	static volatile String BLACKMARKET_STORE_COMMAND = DEFAULT_BLACKMARKET_STORE_COMMAND;
	static volatile String MERCHANT_TRIGGER = DEFAULT_MERCHANT_TRIGGER;
	static volatile String MERCHANT_STORE_COMMAND = DEFAULT_MERCHANT_STORE_COMMAND;
	static volatile String BUFFS_TRIGGER = DEFAULT_BUFFS_TRIGGER;
	static volatile String STORE_PURCHASE_TRIGGER = DEFAULT_STORE_PURCHASE_TRIGGER;
	static volatile String MERCHANT_PROTECTED_NAMES = DEFAULT_MERCHANT_PROTECTED_NAMES;
	static volatile List<String> MERCHANT_PROTECTED_NAME_PARTS = List.of();
	static volatile String MERCHANT_BLACKLIST_NAMES = DEFAULT_MERCHANT_BLACKLIST_NAMES;
	static volatile List<String> MERCHANT_BLACKLIST_NAME_PARTS = List.of();
	static volatile String MERCHANT_WEBHOOK_NOTIFY_NAMES = DEFAULT_MERCHANT_WEBHOOK_NOTIFY_NAMES;
	static volatile List<String> MERCHANT_WEBHOOK_NOTIFY_NAME_PARTS = List.of();

	private static final int CONTAINER_COLUMNS = 9;
	private static final int PLAYER_INVENTORY_SLOTS = 36;
	private static final int COMMAND_SUCCESS = Command.SINGLE_SUCCESS;
	private static final int FISHING_HOTBAR_SLOT = 1;
	private static final int MUSEUM_HOTBAR_SLOT = 0;
	private static final int[] MUSEUM_BOX_HOTBAR_PREFERRED_SLOTS = {3, 4, 5, 6};
	private static final int EGG_HOTBAR_SLOT = 3;
	private static final int EGG_NEXT_PAGE_SLOT = 8;
	private static final int EGG_LAST_SLOT = 53;
	private static final int EGG_FINAL_SLOT = 50;
	private static final int LOBBY_FAILSAFE_TARGET_SLOT = 13;
	private static final int TINKER_ENTRY_SLOT = 49;
	private static final int TINKER_OPEN_MUSEUM_SLOT = 33;
	private static final int TINKER_BACK_SLOT = 32;
	private static final int TINKER_OPEN_STORAGE_SLOT = 29;
	private static final int TINKER_CONFIRM_STORAGE_SLOT = 49;
	private static final int COOKIE_ROLL_CONTAINER_SLOT = 13;

	private static final long DEFAULT_WEBHOOK_DELAY_MS = 300L;
	private static final int DEFAULT_WEBHOOK_PING_COUNT = 5;
	private static final boolean DEFAULT_BOSS_WEBHOOK_PING_ENABLED = true;
	private static final boolean DEFAULT_GEMSHOP_WEBHOOK_PING_ENABLED = true;
	private static final boolean DEFAULT_BLACKMARKET_WEBHOOK_PING_ENABLED = true;
	private static final boolean DEFAULT_MERCHANT_WEBHOOK_PING_ENABLED = true;
	private static final boolean DEFAULT_MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED = true;
	private static final boolean DEFAULT_BUFFS_WEBHOOK_PING_ENABLED = true;
	private static final boolean DEFAULT_STORE_WEBHOOK_PING_ENABLED = true;
	private static final long DEFAULT_GEMSHOP_CLICK_DELAY_MS = 200L;
	private static final int DEFAULT_GEMSHOP_CLICK_COUNT = 10;
	private static final long DEFAULT_BLACKMARKET_CLICK_DELAY_MS = 200L;
	private static final int DEFAULT_BLACKMARKET_CLICK_COUNT = 10;
	private static final long DEFAULT_MERCHANT_CLICK_DELAY_MS = 200L;
	private static final long DEFAULT_BUFFS_CLICK_DELAY_MS = 200L;
	private static final long DEFAULT_MUSEUM_CLICK_DELAY_MS = 550L;
	private static final long DEFAULT_LOBBY_CLICK_DELAY_MS = 200L;
	private static final long DEFAULT_AUTO_STORE_DELAY_MS = 700L;
	private static final long DEFAULT_BOSS_NOTIFY_DELAY_MS = 5L * 60L * 1000L;
	private static final SetupSwapMode DEFAULT_SETUP_SWAP_MODE = SetupSwapMode.MONEY;
	private static final SetupSwapArmor DEFAULT_SETUP_SWAP_ARMOR = SetupSwapArmor.OFF;
	private static final String DEFAULT_SETUP_SWAP_STORE_COMMAND = "pv 4";
	private static final String DEFAULT_SETUP_SWAP_GET_COMMAND = "pv 5";
	private static final int DEFAULT_SETUP_SWAP_RING_COUNT = 5;
	private static final int DEFAULT_SETUP_SWAP_ATTACHMENT_COUNT = 6;
	private static final boolean DEFAULT_SETUP_SWAP_BOSS_RELICS_ENABLED = false;
	private static final int DEFAULT_SETUP_SWAP_BOSS_RELIC_COUNT = 3;
	private static final long DEFAULT_SETUP_SWAP_CLICK_DELAY_MS = 250L;
	private static final long DEFAULT_SETUP_SWAP_ATTACHMENT_DELAY_MS = 1200L;
	private static final long DEFAULT_MERCHANT_FIRST_GUI_GAP_MS = 700L;
	private static final long DEFAULT_MERCHANT_BARRIER_SCAN_DELAY_MS = 700L;
	private static final long DEFAULT_MERCHANT_SALVAGE_TO_ALL_GAP_MS = 1200L;
	private static final long DEFAULT_COMMAND_TO_GUI_DELAY_MS = 700L;
	private static final long DEFAULT_MERCHANT_REPEAT_DELAY_MS = 7000L;
	private static final int DEFAULT_MERCHANT_REPEAT_COUNT = 3;
	private static final int DEFAULT_MERCHANT_WEBHOOK_NOTIFY_PING_COUNT = 3;
	private static final long DEFAULT_TRAIT_ROLL_DELAY_MS = 250L;
	private static final TraitRollTarget DEFAULT_TRAIT_ROLL_TARGET = TraitRollTarget.BANKER_I;
	private static final long DEFAULT_COOKIE_ROLL_DELAY_MS = 250L;
	private static final CookieRollTarget DEFAULT_COOKIE_ROLL_TARGET = CookieRollTarget.F_PLUS;
	private static final String DEFAULT_TITLESCREEN_IMAGE_FILE = "";
	private static final String DEFAULT_START_MUSIC_FILE = "";
	private static final long DEFAULT_RING_SCRAPPER_CLICK_DELAY_MS = 250L;
	private static final long DEFAULT_FISHING_FAILSAFE_MS = 20_000L;
	private static final long FISHING_RECAST_DELAY_MS = 300L;
	private static final long PERKS_CLICK_DELAY_MS = 300L;
	private static final long DEFAULT_LOBBY_FAILSAFE_REPEAT_MS = 10_000L;
	private static final int DEFAULT_MUSEUM_PV_NUMBER = 1;
	private static final String DEFAULT_MUSEUM_VAULT_COMMAND = "pv";
	private static final int DEFAULT_MUSEUM_BOX_OPEN_COUNT = 30;
	private static final long DEFAULT_MUSEUM_BOX_DELAY_MS = 260L;
	private static final long DEFAULT_MUSEUM_RETRY_DELAY_MS = 1000L;
	private static final long DEFAULT_MUSEUM_REOPEN_PHASE_DELAY_MS = 1200L;
	private static final int MUSEUM_PHASE2_OPEN_ATTEMPTS = 3;
	private static final int DEFAULT_COMMON_MIN_BOOSTER = 20;
	private static final int DEFAULT_RARE_MIN_BOOSTER = 28;
	private static final int DEFAULT_EPIC_MIN_BOOSTER = 40;
	private static final int DEFAULT_LEGENDARY_MIN_BOOSTER = 80;
	private static final int DEFAULT_MYTHIC_MIN_BOOSTER = 120;
	private static final int DEFAULT_GOTHIC_MIN_BOOSTER = 160;

	static volatile long WEBHOOK_DELAY_MS = DEFAULT_WEBHOOK_DELAY_MS;
	static volatile int WEBHOOK_PING_COUNT = DEFAULT_WEBHOOK_PING_COUNT;
	static volatile long GEMSHOP_CLICK_DELAY_MS = DEFAULT_GEMSHOP_CLICK_DELAY_MS;
	static volatile int GEMSHOP_CLICK_COUNT = DEFAULT_GEMSHOP_CLICK_COUNT;
	static volatile long BLACKMARKET_CLICK_DELAY_MS = DEFAULT_BLACKMARKET_CLICK_DELAY_MS;
	static volatile int BLACKMARKET_CLICK_COUNT = DEFAULT_BLACKMARKET_CLICK_COUNT;
	static volatile long MERCHANT_CLICK_DELAY_MS = DEFAULT_MERCHANT_CLICK_DELAY_MS;
	static volatile long BUFFS_CLICK_DELAY_MS = DEFAULT_BUFFS_CLICK_DELAY_MS;
	static volatile long MUSEUM_CLICK_DELAY_MS = DEFAULT_MUSEUM_CLICK_DELAY_MS;
	static volatile long LOBBY_CLICK_DELAY_MS = DEFAULT_LOBBY_CLICK_DELAY_MS;
	static volatile long AUTO_STORE_DELAY_MS = DEFAULT_AUTO_STORE_DELAY_MS;
	static volatile long BOSS_NOTIFY_DELAY_MS = DEFAULT_BOSS_NOTIFY_DELAY_MS;
	static volatile SetupSwapMode SETUP_SWAP_MODE = DEFAULT_SETUP_SWAP_MODE;
	static volatile SetupSwapArmor SETUP_SWAP_ARMOR = DEFAULT_SETUP_SWAP_ARMOR;
	static volatile String SETUP_SWAP_STORE_COMMAND = DEFAULT_SETUP_SWAP_STORE_COMMAND;
	static volatile String SETUP_SWAP_GET_COMMAND = DEFAULT_SETUP_SWAP_GET_COMMAND;
	static volatile int SETUP_SWAP_RING_COUNT = DEFAULT_SETUP_SWAP_RING_COUNT;
	static volatile int SETUP_SWAP_ATTACHMENT_COUNT = DEFAULT_SETUP_SWAP_ATTACHMENT_COUNT;
	static volatile int SETUP_SWAP_BOSS_RELIC_COUNT = DEFAULT_SETUP_SWAP_BOSS_RELIC_COUNT;
	static volatile long SETUP_SWAP_CLICK_DELAY_MS = DEFAULT_SETUP_SWAP_CLICK_DELAY_MS;
	static volatile long SETUP_SWAP_ATTACHMENT_DELAY_MS = DEFAULT_SETUP_SWAP_ATTACHMENT_DELAY_MS;
	static volatile long MERCHANT_FIRST_GUI_GAP_MS = DEFAULT_MERCHANT_FIRST_GUI_GAP_MS;
	static volatile long MERCHANT_BARRIER_SCAN_DELAY_MS = DEFAULT_MERCHANT_BARRIER_SCAN_DELAY_MS;
	static volatile long MERCHANT_SALVAGE_TO_ALL_GAP_MS = DEFAULT_MERCHANT_SALVAGE_TO_ALL_GAP_MS;
	static volatile long COMMAND_TO_GUI_DELAY_MS = DEFAULT_COMMAND_TO_GUI_DELAY_MS;
	private static final long GUI_OPEN_TIMEOUT_MS = 6000L;
	private static final long GUI_POLL_DELAY_MS = 50L;
	static volatile long MERCHANT_REPEAT_DELAY_MS = DEFAULT_MERCHANT_REPEAT_DELAY_MS;
	static volatile int MERCHANT_REPEAT_COUNT = DEFAULT_MERCHANT_REPEAT_COUNT;
	static volatile int MERCHANT_WEBHOOK_NOTIFY_PING_COUNT = DEFAULT_MERCHANT_WEBHOOK_NOTIFY_PING_COUNT;
	static volatile long TRAIT_ROLL_DELAY_MS = DEFAULT_TRAIT_ROLL_DELAY_MS;
	static volatile TraitRollTarget TRAIT_ROLL_TARGET = DEFAULT_TRAIT_ROLL_TARGET;
	static volatile long COOKIE_ROLL_DELAY_MS = DEFAULT_COOKIE_ROLL_DELAY_MS;
	static volatile CookieRollTarget COOKIE_ROLL_TARGET = DEFAULT_COOKIE_ROLL_TARGET;
	static volatile String TITLESCREEN_IMAGE_FILE = DEFAULT_TITLESCREEN_IMAGE_FILE;
	static volatile String START_MUSIC_FILE = DEFAULT_START_MUSIC_FILE;
	static volatile long RING_SCRAPPER_CLICK_DELAY_MS = DEFAULT_RING_SCRAPPER_CLICK_DELAY_MS;
	private static final long MAIN_THREAD_TIMEOUT_MS = 5000L;
	private static final long MESSAGE_DEDUP_WINDOW_MS = 300L;
	private static final long BACKGROUND_LOOP_DELAY_MS = 120L;
	private static final long BACKGROUND_IDLE_LOOP_DELAY_MS = 450L;
	private static final long LOBBY_SCAN_INTERVAL_ACTIVE_MS = 200L;
	private static final long LOBBY_SCAN_INTERVAL_IDLE_MS = 700L;
	static volatile long FISHING_FAILSAFE_MS = DEFAULT_FISHING_FAILSAFE_MS;
	private static final long DEFAULT_EGG_POST_OPEN_DELAY_MS = 450L;
	private static final long DEFAULT_EGG_CLICK_DELAY_MS = 550L;
	private static final int DEFAULT_EGG_AUTO_OPEN_AMOUNT = 1;
	static volatile long EGG_POST_OPEN_DELAY_MS = DEFAULT_EGG_POST_OPEN_DELAY_MS;
	static volatile long EGG_CLICK_DELAY_MS = DEFAULT_EGG_CLICK_DELAY_MS;
	static volatile int EGG_AUTO_OPEN_AMOUNT = DEFAULT_EGG_AUTO_OPEN_AMOUNT;
	private static final long EGG_PAGE_SWITCH_DELAY_MS = 900L;
	private static final long EGG_RETRY_DELAY_MS = 400L;
	private static final long EGG_OPEN_DELAY_MS = 300L;
	private static final long EGG_REOPEN_DELAY_MS = 250L;
	private static final long EGG_RESTART_AFTER_RECONNECT_DELAY_MS = 2000L;
	private static final long EGG_OPEN_WATCHDOG_INTERVAL_MS = 10_000L;
	private static final long EGG_CHAT_CONFIRM_INTERVAL_MS = 60_000L;
	private static final long EGG_CHAT_CONFIRM_WINDOW_MS = 10_000L;
	private static final long AUTO_CONNECT_RETRY_DELAY_MS = 12_000L;
	private static final long LOBBY_SLOT0_JOIN_SALES_RETRY_MS = 6_000L;
	private static final long LOBBY_SLOT0_WORLD_CHANGE_TIMEOUT_MS = 15_000L;
	private static final long AUTO_DAILY_POLL_MS = 800L;
	private static final long AUTO_DAILY_RETRY_COOLDOWN_MS = 60_000L;
	private static final long AUTO_DAILY_STAGGER_MS = 6_000L;
	private static final long ONLINE_PLAYER_NAME_CACHE_REFRESH_INTERVAL_MS = 500L;
	private static final long ONLINE_PLAYER_NAME_CACHE_FRESH_WINDOW_MS = 2_000L;
	// Prevent running dailies immediately after joining (often still in lobby / cache not populated yet).
	private static final long AUTO_DAILY_AFTER_CONNECT_GRACE_MS = 30_000L;
	private static final long AUTO_DAILY_PERKS_INTERVAL_MS = 4L * 60L * 60L * 1000L;
	private static final long AUTO_DAILY_FREECREDITS_INTERVAL_MS = 24L * 60L * 60L * 1000L;
	private static final long AUTO_DAILY_KEYALL_INTERVAL_MS = 5L * 60L * 60L * 1000L;
	static volatile long LOBBY_FAILSAFE_REPEAT_MS = DEFAULT_LOBBY_FAILSAFE_REPEAT_MS;
	private static final long LOBBY_FAILSAFE_RETRY_MS = 1000L;
	private static final long LOBBY_FAILSAFE_GUI_TIMEOUT_MS = 5000L;
	private static final int LOBBY_COMPASS_OPEN_ATTEMPTS = 3;
	private static final int EGG_OPEN_ATTEMPTS = 3;
	static volatile int MUSEUM_PV_NUMBER = DEFAULT_MUSEUM_PV_NUMBER;
	static volatile String MUSEUM_VAULT_COMMAND = DEFAULT_MUSEUM_VAULT_COMMAND;
	static volatile int MUSEUM_BOX_OPEN_COUNT = DEFAULT_MUSEUM_BOX_OPEN_COUNT;
	static volatile long MUSEUM_BOX_DELAY_MS = DEFAULT_MUSEUM_BOX_DELAY_MS;
	static volatile long MUSEUM_RETRY_DELAY_MS = DEFAULT_MUSEUM_RETRY_DELAY_MS;
	static volatile long MUSEUM_REOPEN_PHASE_DELAY_MS = DEFAULT_MUSEUM_REOPEN_PHASE_DELAY_MS;

	private static final int[] GEMSHOP_ROW2_SLOTS = {12, 13, 14, 15, 16};
	private static final int[] GEMSHOP_ROW4_SLOTS = {30, 31, 32, 33};

	private static final int MERCHANT_STEP_ONE_SLOT = 4;
	private static final int MERCHANT_STEP_ONE_EXTRA_SLOT = 46;
	private static final int MERCHANT_STEP_TWO_SLOT = 15;
	private static final int MERCHANT_STEP_THREE_SLOT = 53;
	private static final int MERCHANT_BARRIER_WALL_COUNT = 33;
	private static final int MERCHANT_FLOW_RESTART_ATTEMPTS = 3;
	private static final long MERCHANT_FLOW_RESTART_DELAY_MS = 350L;
	private static final int MERCHANT_SPECIAL_STASH_ATTEMPTS = 4;
	private static final int MERCHANT_SPECIAL_VERIFY_ATTEMPTS = 8;
	private static final long MERCHANT_SPECIAL_VERIFY_DELAY_MS = 250L;
	private static final long MERCHANT_REOPEN_AFTER_STASH_DELAY_MS = 5000L;
	private static final int CRYSTAL_PARSE_CACHE_LIMIT = 1024;

	static final AtomicBoolean WEBHOOK_ENABLED = new AtomicBoolean(false);
	// Prevent players from spoofing server restock/boss triggers by typing the trigger phrases in chat.
	static final AtomicBoolean SERVER_ONLY_TRIGGERS = new AtomicBoolean(true);
	static final AtomicBoolean GEMSHOP_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean BLACKMARKET_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean MERCHANT_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean BUFFS_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean STORE_PURCHASE_ENABLED = new AtomicBoolean(false);
	// Name kept for backward compatibility in code/config migration; this now means "webhook enabled".
	static final AtomicBoolean GEMSHOP_WEBHOOK_ONLY = new AtomicBoolean(false);
	static final AtomicBoolean BLACKMARKET_WEBHOOK_ONLY = new AtomicBoolean(false);
	static final AtomicBoolean MERCHANT_WEBHOOK_ONLY = new AtomicBoolean(false);
	static final AtomicBoolean BUFFS_WEBHOOK_ONLY = new AtomicBoolean(false);
	static final AtomicBoolean STORE_WEBHOOK_ONLY = new AtomicBoolean(false);
	static final AtomicBoolean BOSS_WEBHOOK_PING_ENABLED = new AtomicBoolean(DEFAULT_BOSS_WEBHOOK_PING_ENABLED);
	static final AtomicBoolean GEMSHOP_WEBHOOK_PING_ENABLED = new AtomicBoolean(DEFAULT_GEMSHOP_WEBHOOK_PING_ENABLED);
	static final AtomicBoolean BLACKMARKET_WEBHOOK_PING_ENABLED = new AtomicBoolean(DEFAULT_BLACKMARKET_WEBHOOK_PING_ENABLED);
	static final AtomicBoolean MERCHANT_WEBHOOK_PING_ENABLED = new AtomicBoolean(DEFAULT_MERCHANT_WEBHOOK_PING_ENABLED);
	static final AtomicBoolean MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED = new AtomicBoolean(DEFAULT_MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED);
	static final AtomicBoolean BUFFS_WEBHOOK_PING_ENABLED = new AtomicBoolean(DEFAULT_BUFFS_WEBHOOK_PING_ENABLED);
	static final AtomicBoolean STORE_WEBHOOK_PING_ENABLED = new AtomicBoolean(DEFAULT_STORE_WEBHOOK_PING_ENABLED);
	static final AtomicBoolean AUTO_RECONNECT_ENABLED = new AtomicBoolean(true);
	static final AtomicBoolean AUTO_CONNECT_ON_STARTUP_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean LOBBY_RECONNECT_ENABLED = new AtomicBoolean(true);
	static final AtomicBoolean AUTO_DAILY_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean AUTO_DAILY_PERKS_ENABLED = new AtomicBoolean(true);
	static final AtomicBoolean AUTO_DAILY_FREECREDITS_ENABLED = new AtomicBoolean(true);
	static final AtomicBoolean AUTO_DAILY_KEYALL_ENABLED = new AtomicBoolean(true);
	static final AtomicBoolean FISHING_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean EGG_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean MUSEUM_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean RING_SCRAPPER_ENABLED = new AtomicBoolean(false);
	static final AtomicBoolean EGG_PENDING = new AtomicBoolean(false);
	static final AtomicBoolean RING_SCRAPPER_PENDING = new AtomicBoolean(false);
	private static final AtomicBoolean MERCHANT_BLACKLIST_HARD_STOP_REQUESTED = new AtomicBoolean(false);
	static final AtomicBoolean EGG_RECONNECT_RESTART_SCHEDULED = new AtomicBoolean(false);
	static final AtomicBoolean EGG_FORCE_REOPEN = new AtomicBoolean(false);
	static final AtomicBoolean LOBBY_FAILSAFE_ACTIVE = new AtomicBoolean(false);
	static final AtomicBoolean AUTOMATION_ACTIVE = new AtomicBoolean(false);
	private static final AtomicBoolean TRAIT_ROLLER_RUNNING = new AtomicBoolean(false);
	private static final AtomicBoolean COOKIE_ROLLER_RUNNING = new AtomicBoolean(false);
	static final AtomicBoolean TRAIT_ROLL_BUTTON_ENABLED = new AtomicBoolean(true);
	static final AtomicBoolean COOKIE_ROLL_BUTTON_ENABLED = new AtomicBoolean(true);
	static final AtomicBoolean SETUP_SWAP_BOSS_RELICS_ENABLED = new AtomicBoolean(DEFAULT_SETUP_SWAP_BOSS_RELICS_ENABLED);
	private static final AtomicBoolean SALES_SILENT_MODE = new AtomicBoolean(false);
	private static final AtomicBoolean SALES_SILENT_SUPPRESSING_SCREEN = new AtomicBoolean(false);
	private static final AtomicLong SALES_SILENT_LAST_SUPPRESSED_AT_MS = new AtomicLong(0L);
	private static final AtomicInteger SALES_SILENT_LAST_CONTAINER_ID = new AtomicInteger(-1);
	static volatile EggType selectedEggType = EggType.DEFAULT;
	static volatile AutomationMode automationMode = AutomationMode.FISHING;
	private static final AtomicLong LAST_EGG_CHAT_MATCH_MS = new AtomicLong(0L);
	private static volatile EggType LAST_EGG_CHAT_MATCH_TYPE = null;
	static volatile String AUTO_DAILY_PERKS_LAST_RUN = "";
	static volatile String AUTO_DAILY_FREECREDITS_LAST_RUN = "";
	static volatile String AUTO_DAILY_KEYALL_LAST_RUN = "";
	static volatile long AUTO_DAILY_NEXT_ENQUEUE_AT_MS = 0L;
	static volatile long AUTO_DAILY_PERKS_LAST_ATTEMPT_MS = 0L;
	static volatile long AUTO_DAILY_FREECREDITS_LAST_ATTEMPT_MS = 0L;
	static volatile long AUTO_DAILY_KEYALL_LAST_ATTEMPT_MS = 0L;
	static final AtomicBoolean AUTO_DAILY_PERKS_SCHEDULED = new AtomicBoolean(false);
	static final AtomicBoolean AUTO_DAILY_FREECREDITS_SCHEDULED = new AtomicBoolean(false);
	static final AtomicBoolean AUTO_DAILY_KEYALL_SCHEDULED = new AtomicBoolean(false);
	static KeyBinding START_AUTOMATION_KEYBIND;
	static KeyBinding OPEN_GUI_KEYBIND;
	static KeyBinding SETUP_SWAP_KEYBIND;
	static KeyBinding CANCEL_ALL_ROUTINES_KEYBIND;

	private static final String SETUP_SWAP_ROUTINE_NAME = "setup-swap";
	private static final AtomicBoolean SETUP_SWAP_CANCEL_REQUESTED = new AtomicBoolean(false);
	private static final AtomicReference<Thread> SETUP_SWAP_ROUTINE_THREAD = new AtomicReference<>(null);
	private static final AtomicReference<Thread> EVENT_ROUTINE_THREAD = new AtomicReference<>(null);

	private static final AtomicBoolean EVENT_ROUTINE_RUNNING = new AtomicBoolean(false);
	private static final AtomicInteger BACKGROUND_THREAD_COUNTER = new AtomicInteger(1);

	private static final ThreadPoolExecutor ROUTINE_EXECUTOR = new ThreadPoolExecutor(
		1,
		1,
		0L,
		TimeUnit.MILLISECONDS,
		new LinkedBlockingQueue<>(),
		runnable -> {
			Thread thread = new Thread(runnable, "javamod-sales-routine");
			thread.setDaemon(true);
			return thread;
		}
	);

	private static final ThreadPoolExecutor BACKGROUND_EXECUTOR = new ThreadPoolExecutor(
		4,
		4,
		0L,
		TimeUnit.MILLISECONDS,
		new LinkedBlockingQueue<>(),
		runnable -> {
			Thread thread = new Thread(
				runnable,
				"javamod-sales-background-" + BACKGROUND_THREAD_COUNTER.getAndIncrement()
			);
			thread.setDaemon(true);
			return thread;
		}
	);

	// Webhook scheduling should not block the routine queue (e.g. boss reminder delays).
	private static final ScheduledThreadPoolExecutor WEBHOOK_SCHEDULER = new ScheduledThreadPoolExecutor(
		1,
		runnable -> {
			Thread thread = new Thread(runnable, "javamod-sales-webhook");
			thread.setDaemon(true);
			return thread;
		}
	);
	static {
		WEBHOOK_SCHEDULER.setRemoveOnCancelPolicy(true);
		WEBHOOK_SCHEDULER.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
	}

	// Prevent runaway queue growth when servers spam trigger messages.
	private static final Set<String> ROUTINE_PENDING_NAMES = ConcurrentHashMap.newKeySet();
	private static final long ROUTINE_QUEUE_NOTIFY_COOLDOWN_MS = 1200L;
	private static volatile long lastRoutineQueueNotifyMs = 0L;

	private static final Object MESSAGE_LOCK = new Object();
	private static String lastMessage = "";
	private static long lastMessageTimestampMs = 0L;
	private static volatile long lastAutoConnectAttemptMs = 0L;
	private static volatile long lastLobbyJoinSalesAttemptMs = 0L;
	private static final AtomicBoolean HAS_CONNECTED_TO_MULTIPLAYER = new AtomicBoolean(false);
	private static final AtomicBoolean STARTUP_AUTO_CONNECT_ATTEMPTED = new AtomicBoolean(false);
	private static final Map<String, CrystalParseResult> CRYSTAL_PARSE_CACHE = new ConcurrentHashMap<>();

	// Client-state cache: avoids frequent callOnClientThread() for simple reads, reducing allocations and main-thread queueing.
	private static final AtomicInteger CACHED_COMPASS_HOTBAR_SLOT = new AtomicInteger(-1);
	private static final AtomicBoolean CACHED_HAS_FISHING_BOBBER = new AtomicBoolean(false);
	private static final AtomicBoolean CACHED_IS_CONNECTED_TO_SERVER = new AtomicBoolean(false);
	private static final AtomicBoolean CACHED_IS_HANDLED_SCREEN = new AtomicBoolean(false);
	private static final AtomicInteger CACHED_OPEN_CONTAINER_ROWS = new AtomicInteger(-1);
	private static final AtomicInteger CACHED_OPEN_SCREEN_SYNC_ID = new AtomicInteger(-1);
	private static final AtomicBoolean CACHED_ON_SALES_SERVER = new AtomicBoolean(false);
	private static final AtomicLong CACHED_SALES_SERVER_CONNECTED_AT_MS = new AtomicLong(0L);
	private static final Set<String> CACHED_ONLINE_PLAYER_NAMES = ConcurrentHashMap.newKeySet();
	private static volatile long cachedOnlinePlayerNamesLastRefreshAtMs = 0L;

	// LocalDate.now() allocates; cache epoch-day and refresh periodically (expiry checks are date-level).
	private static final long TODAY_EPOCH_REFRESH_INTERVAL_MS = 30_000L;
	private static volatile long cachedTodayEpochDay = -1L;
	private static volatile long nextTodayEpochRefreshAtMs = 0L;

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(ClientBrandingAssets::armBootSoundPlayback);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientBrandingAssets.ensureBootSoundArmed(client);
			ClientBrandingAssets.tickBootSoundPlayback(client);
		});

		ensureBrandingMediaDirectories();
		loadPersistedSettings();
		normalizeAutomationState();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(buildSalesCommand())
		);

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChatMessage(message.getString(), true, null));
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
			onChatMessage(message.getString(), false, sender == null ? null : sender.name())
		);
		registerTraitRollButtonHook();

		START_AUTOMATION_KEYBIND = KeyBindingHelper.registerKeyBinding(
			new KeyBinding(
				"key.javamod.start_automation",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				SALES_KEYBIND_CATEGORY
			)
		);
		OPEN_GUI_KEYBIND = KeyBindingHelper.registerKeyBinding(
			new KeyBinding(
				"key.javamod.open_gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				SALES_KEYBIND_CATEGORY
			)
		);
		SETUP_SWAP_KEYBIND = KeyBindingHelper.registerKeyBinding(
			new KeyBinding(
				"key.javamod.setup_swap",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				SALES_KEYBIND_CATEGORY
			)
		);
		CANCEL_ALL_ROUTINES_KEYBIND = KeyBindingHelper.registerKeyBinding(
			new KeyBinding(
				"key.javamod.cancel_all_routines",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				SALES_KEYBIND_CATEGORY
			)
		);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			updateClientStateCache(client);
			handleAutoConnectTick(client);
			if (client.currentScreen instanceof SalesDashboardScreen) {
				return;
			}
			while (START_AUTOMATION_KEYBIND.wasPressed()) {
				startAutomationFromKeybind();
			}
			while (OPEN_GUI_KEYBIND.wasPressed()) {
				openConfigGuiFromKeybind(client);
			}
			while (SETUP_SWAP_KEYBIND.wasPressed()) {
				startSetupSwapFromKeybind();
			}
			while (CANCEL_ALL_ROUTINES_KEYBIND.wasPressed()) {
				cancelAllRoutinesNow();
			}
		});

		BACKGROUND_EXECUTOR.execute(SalesClientMod::runAutomationWorker);
		BACKGROUND_EXECUTOR.execute(SalesClientMod::runLobbyCompassFailsafeWorker);
		BACKGROUND_EXECUTOR.execute(SalesClientMod::runAutoDailyWorker);

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ROUTINE_EXECUTOR.shutdownNow();
			BACKGROUND_EXECUTOR.shutdownNow();
			WEBHOOK_SCHEDULER.shutdownNow();
		});

		LOGGER.info("Sales client automation initialized for 1.21.11");
	}

	private static void registerTraitRollButtonHook() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof HandledScreen<?> handledScreen)) {
				return;
			}
			if (shouldShowAutoTraitRollButton(handledScreen)) {
				addAutoContainerButtonIfMissing(
					screen,
					scaledWidth,
					scaledHeight,
					"Auto Trait Roll",
					SalesClientMod::onAutoTraitRollButtonPressed
				);
			}
			if (shouldShowAutoCookieRollButton(handledScreen)) {
				addAutoContainerButtonIfMissing(
					screen,
					scaledWidth,
					scaledHeight,
					"Auto Cookie Roll",
					SalesClientMod::onAutoCookieRollButtonPressed
				);
			}
		});
	}

	private static void addAutoContainerButtonIfMissing(
		Screen screen,
		int scaledWidth,
		int scaledHeight,
		String label,
		Runnable onPress
	) {
		for (var widget : Screens.getButtons(screen)) {
			if (label.equals(widget.getMessage().getString())) {
				return;
			}
		}

		int buttonWidth = 108;
		int buttonHeight = 20;
		int buttonX = Math.max(2, (scaledWidth / 2) + 70);
		int buttonY = Math.max(2, (scaledHeight / 2) - 110);
		ButtonWidget button = ButtonWidget.builder(
			Text.literal(label),
			pressed -> onPress.run()
		).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build();
		((ScreenAddDrawableChildAccessor) screen).javamod$addDrawableChild(button);
	}

	private static void updateClientStateCache(MinecraftClient client) {
		int compassSlot = -1;
		boolean hasBobber = false;
		ClientPlayNetworkHandler networkHandler = null;
		if (client != null && client.player != null) {
			for (int slot = 0; slot < 9; slot++) {
				if (client.player.getInventory().getStack(slot).isOf(Items.COMPASS)) {
					compassSlot = slot;
					break;
				}
			}
			hasBobber = client.player.fishHook != null;
			networkHandler = client.getNetworkHandler();
		}
		CACHED_COMPASS_HOTBAR_SLOT.set(compassSlot);
		CACHED_HAS_FISHING_BOBBER.set(hasBobber);

		boolean connected = client != null
			&& client.player != null
			&& client.world != null
			&& networkHandler != null;
		CACHED_IS_CONNECTED_TO_SERVER.set(connected);
		refreshOnlinePlayerNameCache(networkHandler, connected);

		boolean handled = false;
		int rows = -1;
		int syncId = -1;
		ScreenHandler handler = getActiveHandledScreenHandler(client);
		if (handler != null) {
			handled = true;
			rows = getContainerRows(handler);
			syncId = handler.syncId;
		}
		CACHED_IS_HANDLED_SCREEN.set(handled);
		CACHED_OPEN_CONTAINER_ROWS.set(rows);
		CACHED_OPEN_SCREEN_SYNC_ID.set(syncId);
	}

	private static void refreshOnlinePlayerNameCache(ClientPlayNetworkHandler handler, boolean connected) {
		long now = System.currentTimeMillis();
		if (!connected || handler == null) {
			CACHED_ONLINE_PLAYER_NAMES.clear();
			cachedOnlinePlayerNamesLastRefreshAtMs = now;
			return;
		}
		if (now - cachedOnlinePlayerNamesLastRefreshAtMs < ONLINE_PLAYER_NAME_CACHE_REFRESH_INTERVAL_MS) {
			return;
		}

		Set<String> fresh = new java.util.HashSet<>();
		try {
			for (var entry : handler.getPlayerList()) {
				if (entry == null || entry.getProfile() == null) {
					continue;
				}
				String name = entry.getProfile().name();
				if (name == null || name.isBlank()) {
					continue;
				}
				fresh.add(name.toLowerCase(Locale.ROOT));
			}
		} catch (Exception ignored) {
			return;
		}

		CACHED_ONLINE_PLAYER_NAMES.clear();
		CACHED_ONLINE_PLAYER_NAMES.addAll(fresh);
		cachedOnlinePlayerNamesLastRefreshAtMs = now;
	}

	private static boolean isOnlinePlayerNameCacheFresh() {
		long now = System.currentTimeMillis();
		return now - cachedOnlinePlayerNamesLastRefreshAtMs <= ONLINE_PLAYER_NAME_CACHE_FRESH_WINDOW_MS;
	}

	private static boolean isCachedOnlinePlayerName(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		return CACHED_ONLINE_PLAYER_NAMES.contains(name.toLowerCase(Locale.ROOT));
	}

	private static long todayEpochDay() {
		long now = System.currentTimeMillis();
		if (cachedTodayEpochDay < 0L || now >= nextTodayEpochRefreshAtMs) {
			cachedTodayEpochDay = LocalDate.now().toEpochDay();
			nextTodayEpochRefreshAtMs = now + TODAY_EPOCH_REFRESH_INTERVAL_MS;
		}
		return cachedTodayEpochDay;
	}

	public static boolean tryHandleHiddenSilentCommand(String rawInput) {
		if (!matchesSilentToggleCommand(rawInput)) {
			return false;
		}

		boolean enabled = !SALES_SILENT_MODE.get();
		SALES_SILENT_MODE.set(enabled);
		SALES_SILENT_SUPPRESSING_SCREEN.set(false);
		sendClientFeedback("Silent mode: " + (enabled ? "ON" : "OFF"));
		return true;
	}

	public static void onClientScreenSet(Screen screen) {
		if (!SALES_SILENT_MODE.get() || screen == null || screen instanceof ChatScreen) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}

		if (client.player != null && client.player.currentScreenHandler != null) {
			SALES_SILENT_LAST_CONTAINER_ID.set(client.player.currentScreenHandler.syncId);
		} else {
			SALES_SILENT_LAST_CONTAINER_ID.set(-1);
		}
		SALES_SILENT_LAST_SUPPRESSED_AT_MS.set(System.currentTimeMillis());

		client.execute(() -> {
			if (!SALES_SILENT_MODE.get()) {
				return;
			}
			if (client.currentScreen == null || client.currentScreen instanceof ChatScreen) {
				return;
			}
			if (!SALES_SILENT_SUPPRESSING_SCREEN.compareAndSet(false, true)) {
				return;
			}
			try {
				client.setScreen(null);
			} finally {
				SALES_SILENT_SUPPRESSING_SCREEN.set(false);
			}
		});
	}

	public static boolean shouldShowAutoTraitRollButton(HandledScreen<?> screen) {
		if (!TRAIT_ROLL_BUTTON_ENABLED.get()) {
			return false;
		}
		if (screen == null || screen.getScreenHandler() == null) {
			return false;
		}
		String title = screen.getTitle().getString();
		if (title == null || title.isBlank()) {
			return false;
		}
		return isTraitsTitle(title);
	}

	public static boolean shouldShowAutoCookieRollButton(HandledScreen<?> screen) {
		if (!COOKIE_ROLL_BUTTON_ENABLED.get()) {
			return false;
		}
		if (screen == null || screen.getScreenHandler() == null) {
			return false;
		}
		String title = screen.getTitle().getString();
		if (title == null || title.isBlank()) {
			return false;
		}
		return isCookiesTitle(title);
	}

	public static void onAutoTraitRollButtonPressed() {

		if (TRAIT_ROLLER_RUNNING.get()) {
			TRAIT_ROLLER_RUNNING.set(false);
			sendClientFeedback("Trait roller stopped.");
			return;
		}

		Integer rollSlot = findRollTraitSlotInOpenContainer();
		if (rollSlot == null) {
			sendClientFeedback("Trait roller: open the Traits GUI first.");
			return;
		}

		enqueueRoutine("trait-roll", SalesClientMod::runAutoTraitRollRoutine);
	}

	public static void onAutoCookieRollButtonPressed() {

		if (COOKIE_ROLLER_RUNNING.get()) {
			COOKIE_ROLLER_RUNNING.set(false);
			sendClientFeedback("Cookie roller stopped.");
			return;
		}

		Integer rollSlot = findRollCookieSlotInOpenContainer();
		if (rollSlot == null) {
			sendClientFeedback("Cookie roller: open the Cookies GUI first.");
			return;
		}

		enqueueRoutine("cookie-roll", SalesClientMod::runAutoCookieRollRoutine);
	}

	private static void runAutoTraitRollRoutine() {
		if (!TRAIT_ROLLER_RUNNING.compareAndSet(false, true)) {
			return;
		}

		sendClientFeedback("Trait roller started (target: " + TRAIT_ROLL_TARGET.label + ", delay: " + TRAIT_ROLL_DELAY_MS + "ms).");
		try {
			long clickDelay = MathHelper.clamp(TRAIT_ROLL_DELAY_MS, 20L, 10_000L);
			while (TRAIT_ROLLER_RUNNING.get()) {
				if (!isTraitsContainerOpen()) {
					sendClientFeedback("Trait roller stopped: Traits GUI closed.");
					return;
				}

				Integer slot = findRollTraitSlotInOpenContainer();
				if (slot == null) {
					sendClientFeedback("Trait roller stopped: could not find 'Roll Trait' anvil.");
					return;
				}

				if (!clickAnySlot(slot, 0, SlotActionType.PICKUP, clickDelay, "trait roller", true)) {
					sendClientFeedback("Trait roller stopped: click failed.");
					return;
				}
			}
		} finally {
			TRAIT_ROLLER_RUNNING.set(false);
		}
	}

	private static void runAutoCookieRollRoutine() {
		if (!COOKIE_ROLLER_RUNNING.compareAndSet(false, true)) {
			return;
		}

		sendClientFeedback("Cookie roller started (target: " + COOKIE_ROLL_TARGET.label + ", delay: " + COOKIE_ROLL_DELAY_MS + "ms).");
		try {
			long clickDelay = MathHelper.clamp(COOKIE_ROLL_DELAY_MS, 20L, 10_000L);
			while (COOKIE_ROLLER_RUNNING.get()) {
				if (!isCookiesContainerOpen()) {
					sendClientFeedback("Cookie roller stopped: Cookies GUI closed.");
					return;
				}

				Integer slot = findRollCookieSlotInOpenContainer();
				if (slot == null) {
					sendClientFeedback("Cookie roller stopped: could not find cookie roll button.");
					return;
				}

				if (!clickAnySlot(slot, 0, SlotActionType.PICKUP, clickDelay, "cookie roller", true)) {
					sendClientFeedback("Cookie roller stopped: click failed.");
					return;
				}
			}
		} finally {
			COOKIE_ROLLER_RUNNING.set(false);
		}
	}

	private static boolean isTraitsContainerOpen() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null) {
				return false;
			}

			if (client.currentScreen instanceof HandledScreen<?> handledScreen) {
				return isTraitsTitle(handledScreen.getTitle().getString());
			}

			if (client.player.currentScreenHandler == null || client.player.currentScreenHandler == client.player.playerScreenHandler) {
				return false;
			}

			// Silent-mode fallback: when the GUI is hidden there is no currentScreen title.
			// In that case, keep rolling only while the dedicated roll slot remains detectable.
			return findRollTraitSlotInOpenContainer() != null;
		}, false);
	}

	private static boolean isCookiesContainerOpen() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null) {
				return false;
			}

			if (client.currentScreen instanceof HandledScreen<?> handledScreen) {
				return isCookiesTitle(handledScreen.getTitle().getString());
			}

			if (client.player.currentScreenHandler == null || client.player.currentScreenHandler == client.player.playerScreenHandler) {
				return false;
			}

			// Silent-mode fallback: when the GUI is hidden there is no currentScreen title.
			// In that case, keep rolling only while the dedicated roll slot remains detectable.
			return findRollCookieSlotInOpenContainer() != null;
		}, false);
	}

	private static boolean isTraitsTitle(String title) {
		if (title == null || title.isBlank()) {
			return false;
		}
		return TRAITS_TITLE_WORD_PATTERN.matcher(title).find();
	}

	private static boolean isCookiesTitle(String title) {
		if (title == null || title.isBlank()) {
			return false;
		}
		return COOKIES_TITLE_WORD_PATTERN.matcher(title).find();
	}

	private static Integer findRollTraitSlotInOpenContainer() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}

			int topSlots = topSlotsForHandler(handler);
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (stack.isEmpty() || !isAnvilItem(stack.getItem())) {
					continue;
				}
				if (stackNameOrBlobContains(stack, "roll trait")) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static Integer findRollCookieSlotInOpenContainer() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}

			int topSlots = topSlotsForHandler(handler);
			if (topSlots <= COOKIE_ROLL_CONTAINER_SLOT) {
				return null;
			}
			return COOKIE_ROLL_CONTAINER_SLOT;
		}, null);
	}

	private static boolean isAnvilItem(Item item) {
		return item == Items.ANVIL || item == Items.CHIPPED_ANVIL || item == Items.DAMAGED_ANVIL;
	}

	private static boolean matchesSilentToggleCommand(String rawInput) {
		if (rawInput == null) {
			return false;
		}
		String normalized = rawInput.trim().replaceAll("\\s+", " ");
		return normalized.equalsIgnoreCase("/sales silent");
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildSalesCommand() {
		return ClientCommandManager.literal("sales")
			.executes(context -> sendHelp(context.getSource()))
			.then(ClientCommandManager.literal("help").executes(context -> sendHelp(context.getSource())))
			.then(ClientCommandManager.literal("commands").executes(context -> sendHelp(context.getSource())))
			.then(ClientCommandManager.literal("gui").executes(context -> openConfigGui(context.getSource())))
			.then(ClientCommandManager.literal("status").executes(context -> sendStatus(context.getSource())))
			.then(buildConfigCommandLiteral("config"))
			.then(buildGemshopBuyCommandLiteral("gemshop"))
			.then(buildBlackmarketBuyCommandLiteral("bm"))
			.then(buildMerchantStoreCommandLiteral("merchant"));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildGemshopBuyCommandLiteral(String literal) {
		return ClientCommandManager.literal(literal)
			.executes(context -> listGemshopTargets(context.getSource()))
			.then(ClientCommandManager.literal("add")
				.then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(context ->
					addGemshopTarget(context.getSource(), StringArgumentType.getString(context, "name")))))
			.then(ClientCommandManager.literal("list").executes(context ->
				listGemshopTargets(context.getSource())))
			.then(ClientCommandManager.literal("remove")
				.then(ClientCommandManager.argument("value", StringArgumentType.greedyString()).executes(context ->
					removeGemshopTarget(context.getSource(), StringArgumentType.getString(context, "value")))))
			.then(ClientCommandManager.literal("store")
				.executes(context -> showGemshopStoreCommand(context.getSource()))
				.then(ClientCommandManager.argument("command", StringArgumentType.greedyString()).executes(context ->
					setGemshopStoreCommand(context.getSource(), StringArgumentType.getString(context, "command")))));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildFishingCommandLiteral(String literal) {
		return ClientCommandManager.literal(literal)
			.executes(context -> setFishingToggle(context.getSource(), true))
			.then(ClientCommandManager.literal("on").executes(context ->
				setFishingToggle(context.getSource(), true)))
			.then(ClientCommandManager.literal("off").executes(context ->
				setFishingToggle(context.getSource(), false)));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildMuseumCommandLiteral(String literal) {
		return ClientCommandManager.literal(literal)
			.executes(context -> setMuseumToggle(context.getSource(), true))
			.then(ClientCommandManager.literal("on").executes(context ->
				setMuseumToggle(context.getSource(), true)))
			.then(ClientCommandManager.literal("off").executes(context ->
				setMuseumToggle(context.getSource(), false)))
			.then(ClientCommandManager.literal("pv")
				.then(ClientCommandManager.argument("number", IntegerArgumentType.integer(1)).executes(context ->
					setMuseumPvNumber(context.getSource(), IntegerArgumentType.getInteger(context, "number")))))
			.then(ClientCommandManager.literal("vault")
				.then(ClientCommandManager.argument("command", StringArgumentType.word()).executes(context ->
					setMuseumVaultCommand(context.getSource(), StringArgumentType.getString(context, "command")))));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildBlackmarketBuyCommandLiteral(String literal) {
		return ClientCommandManager.literal(literal)
			.executes(context -> listBlackmarketTargets(context.getSource()))
			.then(ClientCommandManager.literal("add")
				.then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(context ->
					addBlackmarketTarget(context.getSource(), StringArgumentType.getString(context, "name")))))
			.then(ClientCommandManager.literal("list").executes(context ->
				listBlackmarketTargets(context.getSource())))
			.then(ClientCommandManager.literal("remove")
				.then(ClientCommandManager.argument("value", StringArgumentType.greedyString()).executes(context ->
					removeBlackmarketTarget(context.getSource(), StringArgumentType.getString(context, "value")))))
			.then(ClientCommandManager.literal("store")
				.executes(context -> showBlackmarketStoreCommand(context.getSource()))
				.then(ClientCommandManager.argument("command", StringArgumentType.greedyString()).executes(context ->
					setBlackmarketStoreCommand(context.getSource(), StringArgumentType.getString(context, "command")))));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildMerchantStoreCommandLiteral(String literal) {
		return ClientCommandManager.literal(literal)
			.executes(context -> listMerchantProtectedTargets(context.getSource()))
			.then(ClientCommandManager.literal("add")
				.then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(context ->
					addMerchantProtectedTarget(context.getSource(), StringArgumentType.getString(context, "name")))))
			.then(ClientCommandManager.literal("list").executes(context ->
				listMerchantProtectedTargets(context.getSource())))
			.then(ClientCommandManager.literal("remove")
				.then(ClientCommandManager.argument("value", StringArgumentType.greedyString()).executes(context ->
					removeMerchantProtectedTarget(context.getSource(), StringArgumentType.getString(context, "value")))))
			.then(ClientCommandManager.literal("store")
				.executes(context -> showMerchantStoreCommand(context.getSource()))
				.then(ClientCommandManager.argument("command", StringArgumentType.greedyString()).executes(context ->
					setMerchantStoreCommand(context.getSource(), StringArgumentType.getString(context, "command")))));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildConfigCommandLiteral(String literal) {
		return ClientCommandManager.literal(literal)
			.executes(context -> openConfigLocation(context.getSource()))
			.then(ClientCommandManager.literal("open").executes(context ->
				openConfigLocation(context.getSource())))
			.then(ClientCommandManager.literal("path").executes(context ->
				showConfigPath(context.getSource())))
			.then(ClientCommandManager.literal("export").executes(context ->
				exportConfigToClipboard(context.getSource())))
			.then(ClientCommandManager.literal("import")
				.executes(context -> importConfigFromClipboard(context.getSource()))
				.then(ClientCommandManager.argument("value", StringArgumentType.greedyString()).executes(context ->
					importConfig(context.getSource(), StringArgumentType.getString(context, "value")))));
	}

	private static int openConfigGui(FabricClientCommandSource source) {
		MinecraftClient client = source.getClient();
		client.execute(() -> client.setScreen(new SalesDashboardScreen(client.currentScreen)));
		return COMMAND_SUCCESS;
	}

	private static void openConfigGuiFromKeybind(MinecraftClient client) {
		if (client == null) {
			return;
		}
		if (client.currentScreen instanceof SalesDashboardScreen) {
			return;
		}
		client.setScreen(new SalesDashboardScreen(client.currentScreen));
	}

	private static int openConfigLocation(FabricClientCommandSource source) {
		Path configPath = getSettingsPath().toAbsolutePath().normalize();
		sendSourceFeedback(source, "Config path: " + configPath);
		boolean opened = openPathInFileExplorer(configPath);
		if (!opened) {
			sendSourceFeedback(source, "Could not open file explorer automatically. Open the path manually.");
		}
		return COMMAND_SUCCESS;
	}

	private static int showConfigPath(FabricClientCommandSource source) {
		Path configPath = getSettingsPath().toAbsolutePath().normalize();
		sendSourceFeedback(source, "Config path: " + configPath);
		return COMMAND_SUCCESS;
	}

	private static int exportConfigToClipboard(FabricClientCommandSource source) {
		savePersistedSettings();
		Path configPath = getSettingsPath();
		try {
			byte[] bytes = Files.readAllBytes(configPath);
			String encoded = Base64.getEncoder().encodeToString(bytes);
			MinecraftClient client = source.getClient();
			if (client != null) {
				client.execute(() -> client.keyboard.setClipboard(encoded));
			}
			sendSourceFeedback(source, "Config exported to clipboard (Base64).");
			sendSourceFeedback(source, "Import on another client with: /sales config import clipboard");
			sendSourceFeedback(source, "Warning: export includes webhook URL.");
		} catch (Exception exception) {
			LOGGER.error("Config export failed", exception);
			sendSourceFeedback(source, "Config export failed. Check logs.");
		}
		return COMMAND_SUCCESS;
	}

	private static int importConfigFromClipboard(FabricClientCommandSource source) {
		MinecraftClient client = source.getClient();
		String clipboard = "";
		if (client != null) {
			clipboard = callOnClientThread(() -> client.keyboard.getClipboard(), "");
		}
		return importConfig(source, clipboard);
	}

	private static int importConfig(FabricClientCommandSource source, String rawValue) {
		String normalized = rawValue == null ? "" : rawValue.trim();
		if (normalized.isEmpty()) {
			sendSourceFeedback(source, "Usage: /sales config import clipboard");
			sendSourceFeedback(source, "   or: /sales config import <base64>");
			sendSourceFeedback(source, "   or: /sales config import <path_to_properties_file>");
			return COMMAND_SUCCESS;
		}
		if (normalized.equalsIgnoreCase("clipboard")) {
			return importConfigFromClipboard(source);
		}

		byte[] configBytes = null;
		try {
			Path candidatePath = Path.of(normalized);
			if (Files.exists(candidatePath) && Files.isRegularFile(candidatePath)) {
				configBytes = Files.readAllBytes(candidatePath);
			}
		} catch (Exception ignored) {
			// Not a valid local path.
		}
		if (configBytes == null) {
			try {
				// Accept pastes with whitespace/newlines.
				String base64 = rawValue.replaceAll("\\s+", "");
				configBytes = Base64.getDecoder().decode(base64);
			} catch (Exception exception) {
				sendSourceFeedback(source, "Config import failed: not a file path and not valid Base64.");
				return COMMAND_SUCCESS;
			}
		}

		if (configBytes.length <= 0 || configBytes.length > 512_000) {
			sendSourceFeedback(source, "Config import failed: invalid size.");
			return COMMAND_SUCCESS;
		}

		// Validate it looks like a .properties file before writing it to disk.
		Properties properties = new Properties();
		try (ByteArrayInputStream inputStream = new ByteArrayInputStream(configBytes)) {
			properties.load(inputStream);
		} catch (Exception exception) {
			sendSourceFeedback(source, "Config import failed: could not parse .properties format.");
			return COMMAND_SUCCESS;
		}

		Path settingsPath = getSettingsPath();
		Path backupPath = settingsPath.resolveSibling(settingsPath.getFileName() + ".bak");
		try {
			Path parent = settingsPath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			if (Files.exists(settingsPath)) {
				Files.copy(settingsPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			Files.write(settingsPath, configBytes);
		} catch (Exception exception) {
			LOGGER.error("Config import failed", exception);
			sendSourceFeedback(source, "Config import failed: could not write file.");
			return COMMAND_SUCCESS;
		}

		loadPersistedSettings();
		normalizeAutomationState();
		sendSourceFeedback(source, "Config imported successfully.");
		if (Files.exists(backupPath)) {
			sendSourceFeedback(source, "Backup saved: " + backupPath.toAbsolutePath().normalize());
		}
		return COMMAND_SUCCESS;
	}

	private static boolean openPathInFileExplorer(Path path) {
		if (path == null) {
			return false;
		}

		Path resolved = path.toAbsolutePath().normalize();
		Path target = Files.isDirectory(resolved) ? resolved : resolved.getParent();
		if (target == null) {
			return false;
		}

		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		try {
			if (os.contains("win")) {
				if (!Files.isDirectory(resolved) && Files.exists(resolved)) {
					new ProcessBuilder("explorer.exe", "/select," + resolved).start();
				} else {
					new ProcessBuilder("explorer.exe", target.toString()).start();
				}
				return true;
			}
			if (os.contains("mac")) {
				new ProcessBuilder("open", target.toString()).start();
				return true;
			}

			// Best-effort on Linux and everything else.
			new ProcessBuilder("xdg-open", target.toString()).start();
			return true;
		} catch (Exception exception) {
			LOGGER.warn("Failed to open path in file explorer: {}", target, exception);
			return false;
		}
	}

	private static void handleAutoConnectTick(MinecraftClient client) {
		if (client == null) {
			return;
		}
		if (!hasAutomationAccess()) {
			return;
		}
		if (isConnectedToRemoteServer(client)) {
			HAS_CONNECTED_TO_MULTIPLAYER.set(true);
			STARTUP_AUTO_CONNECT_ATTEMPTED.set(true);
			return;
		}

		Screen currentScreen = client.currentScreen;
		long now = System.currentTimeMillis();

		// Optional startup connect: one best-effort attempt from menu screens.
		if (AUTO_CONNECT_ON_STARTUP_ENABLED.get()
			&& !STARTUP_AUTO_CONNECT_ATTEMPTED.get()
			&& isStartupAutoConnectScreen(currentScreen)) {
			if (now - lastAutoConnectAttemptMs >= AUTO_CONNECT_RETRY_DELAY_MS) {
				lastAutoConnectAttemptMs = now;
				STARTUP_AUTO_CONNECT_ATTEMPTED.set(true);
				autoConnectToSalesServer(client, currentScreen);
			}
			return;
		}

		if (!AUTO_RECONNECT_ENABLED.get()) {
			return;
		}
		// Do not auto-join on fresh startup. Reconnect starts only after the player has joined once manually.
		if (!HAS_CONNECTED_TO_MULTIPLAYER.get()) {
			return;
		}
		// Only reconnect from the disconnected screen. This prevents reconnect attempts during server transfers.
		if (!(currentScreen instanceof DisconnectedScreen)) {
			return;
		}

		if (now - lastAutoConnectAttemptMs < AUTO_CONNECT_RETRY_DELAY_MS) {
			return;
		}
		lastAutoConnectAttemptMs = now;
		autoConnectToSalesServer(client, currentScreen);
	}

	private static boolean isStartupAutoConnectScreen(Screen screen) {
		if (screen == null) {
			return false;
		}
		if (screen instanceof TitleScreen) {
			return true;
		}
		return screen.getClass().getName().equals("net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen");
	}

	private static boolean isConnectedToServer(MinecraftClient client) {
		return client.player != null && client.world != null && client.getNetworkHandler() != null;
	}

	private static boolean isConnectedToRemoteServer(MinecraftClient client) {
		if (!isConnectedToServer(client)) {
			return false;
		}
		ClientPlayNetworkHandler handler = client.getNetworkHandler();
		return handler != null && !handler.getConnection().isLocal();
	}

	private static void autoConnectToSalesServer(MinecraftClient client, Screen parent) {
		try {
			String host = preferredSalesServerHost();
			ServerAddress address = ServerAddress.parse(host);
			ServerInfo info = new ServerInfo(SALES_SERVER_NAME, host, ServerInfo.ServerType.OTHER);
			ConnectScreen.connect(parent == null ? new TitleScreen() : parent, client, address, info, false, null);
			LOGGER.info("Auto-connect attempt to {}", host);
		} catch (Exception exception) {
			LOGGER.error("Auto-connect failed", exception);
		}
	}

	private static String preferredSalesServerHost() {
		String candidate = LAST_SALES_SERVER_HOST;
		return isSalesServerHost(candidate) ? candidate : SALES_SERVER_IP;
	}

	private static boolean isSalesServerHost(String host) {
		if (host == null || host.isBlank()) {
			return false;
		}
		String trimmed = host.trim();
		for (String allowed : SALES_SERVER_HOST_ALIASES) {
			if (allowed != null && !allowed.isBlank() && allowed.equalsIgnoreCase(trimmed)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSalesServerAddress(String rawAddress) {
		if (rawAddress == null || rawAddress.isBlank()) {
			return false;
		}
		String trimmed = rawAddress.trim();
		try {
			ServerAddress parsed = ServerAddress.parse(trimmed);
			return isSalesServerHost(parsed.getAddress());
		} catch (Exception exception) {
			// Best-effort: strip a ":port" suffix if present.
			String hostOnly = trimmed;
			int colon = trimmed.indexOf(':');
			if (colon > 0 && !trimmed.contains("]")) {
				hostOnly = trimmed.substring(0, colon);
			}
			return isSalesServerHost(hostOnly);
		}
	}

	private static int sendHelp(FabricClientCommandSource source) {
		sendSourceFeedback(source, "Sales command overview:");
		sendSourceFeedback(source, "/sales help");
		sendSourceFeedback(source, "/sales gui");
		sendSourceFeedback(source, "/sales status");
		sendSourceFeedback(source, "/sales config");
		sendSourceFeedback(source, "/sales config export");
		sendSourceFeedback(source, "/sales config import clipboard");
		sendSourceFeedback(source, "/sales gemshop add <name>");
		sendSourceFeedback(source, "/sales gemshop list");
		sendSourceFeedback(source, "/sales gemshop remove <name|index>");
		sendSourceFeedback(source, "/sales gemshop store <command|off>");
		sendSourceFeedback(source, "/sales bm add <name>");
		sendSourceFeedback(source, "/sales bm list");
		sendSourceFeedback(source, "/sales bm remove <name|index>");
		sendSourceFeedback(source, "/sales bm store <command|off>");
		sendSourceFeedback(source, "/sales merchant add <name>");
		sendSourceFeedback(source, "/sales merchant list");
		sendSourceFeedback(source, "/sales merchant remove <name|index>");
		sendSourceFeedback(source, "/sales merchant store <command|off>");
		sendSourceFeedback(
			source,
			"Hotkeys: "
				+ keybindDisplayName(START_AUTOMATION_KEYBIND, "G")
				+ " toggles automation, "
				+ keybindDisplayName(OPEN_GUI_KEYBIND, "O")
				+ " opens the Sales GUI, "
				+ keybindDisplayName(CANCEL_ALL_ROUTINES_KEYBIND, "P")
				+ " cancels all routines."
		);
		sendSourceFeedback(source, "All automation toggles remain available in /sales gui.");
		return COMMAND_SUCCESS;
	}

	private static String keybindDisplayName(KeyBinding keyBinding, String fallback) {
		if (keyBinding == null) {
			return fallback;
		}
		return keyBinding.getBoundKeyLocalizedText().getString();
	}

	private static int sendStatus(FabricClientCommandSource source) {
		sendSourceFeedback(
			source,
			"webhook=" + onOff(WEBHOOK_ENABLED.get())
				+ " webhookPings=" + WEBHOOK_PING_COUNT
				+ " bossPing=" + onOff(BOSS_WEBHOOK_PING_ENABLED.get())
				+ " gemshop=" + triggerModeText(getTriggerActionMode(GEMSHOP_ENABLED, GEMSHOP_WEBHOOK_ONLY))
				+ " gsPing=" + onOff(GEMSHOP_WEBHOOK_PING_ENABLED.get())
				+ " gsTargets=" + GEMSHOP_BUY_NAME_PARTS.size()
				+ " gsStore=" + (GEMSHOP_STORE_COMMAND.isBlank() ? "off" : "/" + GEMSHOP_STORE_COMMAND)
				+ " blackmarket=" + triggerModeText(getTriggerActionMode(BLACKMARKET_ENABLED, BLACKMARKET_WEBHOOK_ONLY))
				+ " bmPing=" + onOff(BLACKMARKET_WEBHOOK_PING_ENABLED.get())
				+ " bmTargets=" + BLACKMARKET_BUY_NAME_PARTS.size()
				+ " bmStore=" + (BLACKMARKET_STORE_COMMAND.isBlank() ? "off" : "/" + BLACKMARKET_STORE_COMMAND)
				+ " merchant=" + triggerModeText(getTriggerActionMode(MERCHANT_ENABLED, MERCHANT_WEBHOOK_ONLY))
				+ " merchantPing=" + onOff(MERCHANT_WEBHOOK_PING_ENABLED.get())
				+ " merchantProtect=" + MERCHANT_PROTECTED_NAME_PARTS.size()
				+ " merchantBlacklist=" + MERCHANT_BLACKLIST_NAME_PARTS.size()
				+ " merchantWebhookWatch=" + MERCHANT_WEBHOOK_NOTIFY_NAME_PARTS.size()
				+ " merchantNotifyPing=" + onOff(MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED.get())
				+ " merchantWebhookPings=" + MERCHANT_WEBHOOK_NOTIFY_PING_COUNT
				+ " merchantStore=" + (MERCHANT_STORE_COMMAND.isBlank() ? "off" : "/" + MERCHANT_STORE_COMMAND)
				+ " merchantRepeats=" + MERCHANT_REPEAT_COUNT
				+ " buffs=" + triggerModeText(getTriggerActionMode(BUFFS_ENABLED, BUFFS_WEBHOOK_ONLY))
				+ " buffsPing=" + onOff(BUFFS_WEBHOOK_PING_ENABLED.get())
				+ " store=" + triggerModeText(getTriggerActionMode(STORE_PURCHASE_ENABLED, STORE_WEBHOOK_ONLY))
				+ " storePing=" + onOff(STORE_WEBHOOK_PING_ENABLED.get())
				+ " autoReconnect=" + onOff(AUTO_RECONNECT_ENABLED.get())
				+ " startupConnect=" + onOff(AUTO_CONNECT_ON_STARTUP_ENABLED.get())
				+ " lobbyReconnect=" + onOff(LOBBY_RECONNECT_ENABLED.get())
				+ " autoDaily=" + onOff(AUTO_DAILY_ENABLED.get())
				+ " fishing=" + onOff(FISHING_ENABLED.get())
				+ " museum=" + onOff(MUSEUM_ENABLED.get())
				+ " museumPv=" + MUSEUM_PV_NUMBER
				+ " egg=" + onOff(EGG_ENABLED.get())
				+ " ringScrapper=" + onOff(RING_SCRAPPER_ENABLED.get())
				+ " cookieTarget=" + COOKIE_ROLL_TARGET.label
				+ " cookieDelay=" + COOKIE_ROLL_DELAY_MS
				+ " eggType=" + selectedEggType.displayName
				+ " eggOpens=" + EGG_AUTO_OPEN_AMOUNT
				+ " eggPending=" + yesNo(EGG_PENDING.get())
				+ " automationMode=" + automationMode.label
				+ " automationActive=" + yesNo(AUTOMATION_ACTIVE.get())
				+ " lobbyFailsafe=" + yesNo(LOBBY_FAILSAFE_ACTIVE.get())
				+ " webhookUrlSet=" + yesNo(hasWebhookUrlConfigured())
		);
		return COMMAND_SUCCESS;
	}

	private static int sendEggUsage(FabricClientCommandSource source) {
		sendSourceFeedback(source, "Egg usage:");
		sendSourceFeedback(source, "/sales egg <" + eggTypeList() + ">");
		sendSourceFeedback(source, "/sales egg on");
		sendSourceFeedback(source, "/sales egg off");
		sendSourceFeedback(source, "Current egg type: " + selectedEggType.displayName);
		return COMMAND_SUCCESS;
	}

	private static int handleEggArgument(FabricClientCommandSource source, String rawValue) {
		String value = rawValue == null ? "" : rawValue.trim();
		if (value.equalsIgnoreCase("on")) {
			return startEggAutomation(source, selectedEggType.displayName);
		}
		if (value.equalsIgnoreCase("off")) {
			return stopEggAutomation(source);
		}
		return startEggAutomation(source, value);
	}

	private static int showGemshopStoreCommand(FabricClientCommandSource source) {
		sendSourceFeedback(
			source,
			"Gemshop store command: "
				+ (GEMSHOP_STORE_COMMAND.isBlank() ? "off" : "/" + GEMSHOP_STORE_COMMAND)
		);
		return COMMAND_SUCCESS;
	}

	private static int setGemshopStoreCommand(FabricClientCommandSource source, String rawCommand) {

		GEMSHOP_STORE_COMMAND = normalizeStoreCommand(rawCommand);
		savePersistedSettings();
		sendSourceFeedback(
			source,
			"Gemshop store command set to "
				+ (GEMSHOP_STORE_COMMAND.isBlank() ? "off" : "/" + GEMSHOP_STORE_COMMAND)
		);
		return COMMAND_SUCCESS;
	}

	private static int addGemshopTarget(FabricClientCommandSource source, String rawName) {

		String name = rawName == null ? "" : rawName.trim();
		if (name.isBlank()) {
			sendSourceFeedback(source, "Usage: /sales gemshop add <name>");
			return COMMAND_SUCCESS;
		}

		List<String> targets = parseConfiguredNameList(GEMSHOP_BUY_NAMES);
		for (String target : targets) {
			if (target.equalsIgnoreCase(name)) {
				sendSourceFeedback(source, "Gemshop target already exists: " + target);
				return COMMAND_SUCCESS;
			}
		}
		targets = new ArrayList<>(targets);
		targets.add(name);
		setGemshopBuyNames(String.join(";", targets));
		savePersistedSettings();
		sendSourceFeedback(source, "Gemshop target added: " + name);
		return COMMAND_SUCCESS;
	}

	private static int listGemshopTargets(FabricClientCommandSource source) {

		List<String> targets = parseConfiguredNameList(GEMSHOP_BUY_NAMES);
		if (targets.isEmpty()) {
			sendSourceFeedback(source, "Gemshop target list is empty. Use /sales gemshop add <name>.");
			return COMMAND_SUCCESS;
		}

		sendSourceFeedback(source, "Gemshop buy targets (" + targets.size() + "):");
		for (int i = 0; i < targets.size(); i++) {
			sendSourceFeedback(source, (i + 1) + ". " + targets.get(i));
		}
		return COMMAND_SUCCESS;
	}

	private static int removeGemshopTarget(FabricClientCommandSource source, String rawValue) {

		String value = rawValue == null ? "" : rawValue.trim();
		if (value.isBlank()) {
			sendSourceFeedback(source, "Usage: /sales gemshop remove <name|index>");
			return COMMAND_SUCCESS;
		}

		List<String> targets = new ArrayList<>(parseConfiguredNameList(GEMSHOP_BUY_NAMES));
		if (targets.isEmpty()) {
			sendSourceFeedback(source, "Gemshop target list is already empty.");
			return COMMAND_SUCCESS;
		}

		String removed = null;
		try {
			int index = Integer.parseInt(value);
			if (index >= 1 && index <= targets.size()) {
				removed = targets.remove(index - 1);
			}
		} catch (NumberFormatException ignored) {
			// Value is not an index, continue with text-based remove.
		}

		if (removed == null) {
			for (int i = 0; i < targets.size(); i++) {
				if (targets.get(i).equalsIgnoreCase(value)) {
					removed = targets.remove(i);
					break;
				}
			}
		}

		if (removed == null) {
			sendSourceFeedback(source, "Target not found: " + value);
			return COMMAND_SUCCESS;
		}

		setGemshopBuyNames(String.join(";", targets));
		savePersistedSettings();
		sendSourceFeedback(source, "Gemshop target removed: " + removed);
		return COMMAND_SUCCESS;
	}

	private static int showBlackmarketStoreCommand(FabricClientCommandSource source) {
		sendSourceFeedback(
			source,
			"Blackmarket store command: "
				+ (BLACKMARKET_STORE_COMMAND.isBlank() ? "off" : "/" + BLACKMARKET_STORE_COMMAND)
		);
		return COMMAND_SUCCESS;
	}

	private static int setBlackmarketStoreCommand(FabricClientCommandSource source, String rawCommand) {

		BLACKMARKET_STORE_COMMAND = normalizeStoreCommand(rawCommand);
		savePersistedSettings();
		sendSourceFeedback(
			source,
			"Blackmarket store command set to "
				+ (BLACKMARKET_STORE_COMMAND.isBlank() ? "off" : "/" + BLACKMARKET_STORE_COMMAND)
		);
		return COMMAND_SUCCESS;
	}

	private static int addBlackmarketTarget(FabricClientCommandSource source, String rawName) {

		String name = rawName == null ? "" : rawName.trim();
		if (name.isBlank()) {
			sendSourceFeedback(source, "Usage: /sales bm add <name>");
			return COMMAND_SUCCESS;
		}

		List<String> targets = parseConfiguredNameList(BLACKMARKET_BUY_NAMES);
		for (String target : targets) {
			if (target.equalsIgnoreCase(name)) {
				sendSourceFeedback(source, "Blackmarket target already exists: " + target);
				return COMMAND_SUCCESS;
			}
		}
		targets = new ArrayList<>(targets);
		targets.add(name);
		setBlackmarketBuyNames(String.join(";", targets));
		savePersistedSettings();
		sendSourceFeedback(source, "Blackmarket target added: " + name);
		return COMMAND_SUCCESS;
	}

	private static int listBlackmarketTargets(FabricClientCommandSource source) {

		List<String> targets = parseConfiguredNameList(BLACKMARKET_BUY_NAMES);
		if (targets.isEmpty()) {
			sendSourceFeedback(source, "Blackmarket target list is empty. Use /sales bm add <name>.");
			return COMMAND_SUCCESS;
		}

		sendSourceFeedback(source, "Blackmarket buy targets (" + targets.size() + "):");
		for (int i = 0; i < targets.size(); i++) {
			sendSourceFeedback(source, (i + 1) + ". " + targets.get(i));
		}
		return COMMAND_SUCCESS;
	}

	private static int removeBlackmarketTarget(FabricClientCommandSource source, String rawValue) {

		String value = rawValue == null ? "" : rawValue.trim();
		if (value.isBlank()) {
			sendSourceFeedback(source, "Usage: /sales bm remove <name|index>");
			return COMMAND_SUCCESS;
		}

		List<String> targets = new ArrayList<>(parseConfiguredNameList(BLACKMARKET_BUY_NAMES));
		if (targets.isEmpty()) {
			sendSourceFeedback(source, "Blackmarket target list is already empty.");
			return COMMAND_SUCCESS;
		}

		String removed = null;
		try {
			int index = Integer.parseInt(value);
			if (index >= 1 && index <= targets.size()) {
				removed = targets.remove(index - 1);
			}
		} catch (NumberFormatException ignored) {
			// Value is not an index, continue with text-based remove.
		}

		if (removed == null) {
			for (int i = 0; i < targets.size(); i++) {
				if (targets.get(i).equalsIgnoreCase(value)) {
					removed = targets.remove(i);
					break;
				}
			}
		}

		if (removed == null) {
			sendSourceFeedback(source, "Target not found: " + value);
			return COMMAND_SUCCESS;
		}

		setBlackmarketBuyNames(String.join(";", targets));
		savePersistedSettings();
		sendSourceFeedback(source, "Blackmarket target removed: " + removed);
		return COMMAND_SUCCESS;
	}

	private static int showMerchantStoreCommand(FabricClientCommandSource source) {
		sendSourceFeedback(
			source,
			"Merchant store command: "
				+ (MERCHANT_STORE_COMMAND.isBlank() ? "off" : "/" + MERCHANT_STORE_COMMAND)
		);
		return COMMAND_SUCCESS;
	}

	private static int setMerchantStoreCommand(FabricClientCommandSource source, String rawCommand) {

		MERCHANT_STORE_COMMAND = normalizeStoreCommand(rawCommand);
		savePersistedSettings();
		sendSourceFeedback(
			source,
			"Merchant store command set to "
				+ (MERCHANT_STORE_COMMAND.isBlank() ? "off" : "/" + MERCHANT_STORE_COMMAND)
		);
		return COMMAND_SUCCESS;
	}

	private static int addMerchantProtectedTarget(FabricClientCommandSource source, String rawName) {

		String name = rawName == null ? "" : rawName.trim();
		if (name.isBlank()) {
			sendSourceFeedback(source, "Usage: /sales merchant add <name>");
			return COMMAND_SUCCESS;
		}

		List<String> targets = parseConfiguredNameList(MERCHANT_PROTECTED_NAMES);
		for (String target : targets) {
			if (target.equalsIgnoreCase(name)) {
				sendSourceFeedback(source, "Merchant protected target already exists: " + target);
				return COMMAND_SUCCESS;
			}
		}
		targets = new ArrayList<>(targets);
		targets.add(name);
		setMerchantProtectedNames(String.join(";", targets));
		savePersistedSettings();
		sendSourceFeedback(source, "Merchant protected target added: " + name);
		return COMMAND_SUCCESS;
	}

	private static int listMerchantProtectedTargets(FabricClientCommandSource source) {

		List<String> targets = parseConfiguredNameList(MERCHANT_PROTECTED_NAMES);
		if (targets.isEmpty()) {
			sendSourceFeedback(source, "Merchant protected list is empty. Use /sales merchant add <name>.");
			return COMMAND_SUCCESS;
		}

		sendSourceFeedback(source, "Merchant protected targets (" + targets.size() + "):");
		for (int i = 0; i < targets.size(); i++) {
			sendSourceFeedback(source, (i + 1) + ". " + targets.get(i));
		}
		return COMMAND_SUCCESS;
	}

	private static int removeMerchantProtectedTarget(FabricClientCommandSource source, String rawValue) {

		String value = rawValue == null ? "" : rawValue.trim();
		if (value.isBlank()) {
			sendSourceFeedback(source, "Usage: /sales merchant remove <name|index>");
			return COMMAND_SUCCESS;
		}

		List<String> targets = new ArrayList<>(parseConfiguredNameList(MERCHANT_PROTECTED_NAMES));
		if (targets.isEmpty()) {
			sendSourceFeedback(source, "Merchant protected list is already empty.");
			return COMMAND_SUCCESS;
		}

		String removed = null;
		try {
			int index = Integer.parseInt(value);
			if (index >= 1 && index <= targets.size()) {
				removed = targets.remove(index - 1);
			}
		} catch (NumberFormatException ignored) {
			// Value is not an index, continue with text-based remove.
		}

		if (removed == null) {
			for (int i = 0; i < targets.size(); i++) {
				if (targets.get(i).equalsIgnoreCase(value)) {
					removed = targets.remove(i);
					break;
				}
			}
		}

		if (removed == null) {
			sendSourceFeedback(source, "Target not found: " + value);
			return COMMAND_SUCCESS;
		}

		setMerchantProtectedNames(String.join(";", targets));
		savePersistedSettings();
		sendSourceFeedback(source, "Merchant protected target removed: " + removed);
		return COMMAND_SUCCESS;
	}

	private static int setToggle(FabricClientCommandSource source, String name, AtomicBoolean toggle, boolean enabled) {
		toggle.set(enabled);
		savePersistedSettings();
		sendSourceFeedback(source, name + " set to " + onOff(enabled));
		return COMMAND_SUCCESS;
	}

	private static int setTriggerModeCommand(
		FabricClientCommandSource source,
		String name,
		AtomicBoolean enabled,
		AtomicBoolean webhookOnly,
		TriggerActionMode mode
	) {
		applyTriggerActionMode(enabled, webhookOnly, mode);
		savePersistedSettings();
		sendSourceFeedback(source, name + " set to " + triggerModeText(mode));
		return COMMAND_SUCCESS;
	}

	private static int setFishingToggle(FabricClientCommandSource source, boolean enabled) {
		if (enabled) {
			setAutomationMode(AutomationMode.FISHING, false);
			selectHotbarSlot(FISHING_HOTBAR_SLOT);
			setAutomationActive(true);
			sendSourceFeedback(source, "Fishing automation is now on.");
		} else {
			FISHING_ENABLED.set(false);
			sendSourceFeedback(source, "Fishing automation is now off.");
		}
		savePersistedSettings();
		return COMMAND_SUCCESS;
	}

	private static int setMuseumToggle(FabricClientCommandSource source, boolean enabled) {
		if (enabled) {
			setAutomationMode(AutomationMode.MUSEUM, false);
			setAutomationActive(true);
			sendSourceFeedback(source, "Museum automation is now on.");
		} else {
			MUSEUM_ENABLED.set(false);
			if (automationMode == AutomationMode.MUSEUM) {
				setAutomationActive(false);
			}
			sendSourceFeedback(source, "Museum automation is now off.");
		}
		savePersistedSettings();
		return COMMAND_SUCCESS;
	}

	private static int setMuseumPvNumber(FabricClientCommandSource source, int pvNumber) {
		MUSEUM_PV_NUMBER = Math.max(1, pvNumber);
		savePersistedSettings();
		sendSourceFeedback(source, "Museum PV set to " + MUSEUM_PV_NUMBER + ".");
		return COMMAND_SUCCESS;
	}

	private static int setMuseumVaultCommand(FabricClientCommandSource source, String rawCommand) {
		String command = rawCommand == null ? "" : rawCommand.trim().toLowerCase(Locale.ROOT);
		if (command.isEmpty()) {
			sendSourceFeedback(source, "Museum vault command cannot be empty.");
			return COMMAND_SUCCESS;
		}
		MUSEUM_VAULT_COMMAND = command;
		savePersistedSettings();
		sendSourceFeedback(source, "Museum vault command set to /" + MUSEUM_VAULT_COMMAND + " <number>.");
		return COMMAND_SUCCESS;
	}

	private static int startEggAutomation(FabricClientCommandSource source, String rawEggType) {
		EggType parsed = parseEggType(rawEggType);
		if (parsed == null) {
			sendSourceFeedback(source, "Unknown egg type. Valid: " + eggTypeList());
			return COMMAND_SUCCESS;
		}

		selectedEggType = parsed;
		setAutomationMode(AutomationMode.EGG, true);
		setAutomationActive(true);
		savePersistedSettings();
		sendSourceFeedback(source, "Egg automation started with: " + parsed.displayName);
		return COMMAND_SUCCESS;
	}

	private static int stopEggAutomation(FabricClientCommandSource source) {
		EGG_ENABLED.set(false);
		EGG_PENDING.set(false);
		if (automationMode == AutomationMode.EGG) {
			setAutomationActive(false);
		}
		savePersistedSettings();
		sendSourceFeedback(source, "Egg automation stopped.");
		return COMMAND_SUCCESS;
	}

	private static int setAllToggles(FabricClientCommandSource source, boolean enabled) {
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
		AUTO_DAILY_ENABLED.set(enabled);
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
		sendSourceFeedback(source, "All features set to " + onOff(enabled) + ".");
		return COMMAND_SUCCESS;
	}

	private static void startAutomationFromKeybind() {
		if (AUTOMATION_ACTIVE.get()) {
			setAutomationActive(false);
			sendClientFeedback("Automation paused.");
			return;
		}

		if (automationMode == AutomationMode.EGG) {
			setAutomationMode(AutomationMode.EGG, true);
			setAutomationActive(true);
			savePersistedSettings();
			sendClientFeedback("Automation running: Egg (" + selectedEggType.displayName + ").");
			return;
		}

		if (automationMode == AutomationMode.MUSEUM) {
			setAutomationMode(AutomationMode.MUSEUM, false);
			setAutomationActive(true);
			savePersistedSettings();
			sendClientFeedback("Automation running: Museum / Tinker.");
			return;
		}
		if (automationMode == AutomationMode.RING_SCRAPPER) {
			setAutomationMode(AutomationMode.RING_SCRAPPER, false);
			setAutomationActive(true);
			savePersistedSettings();
			sendClientFeedback("Automation running: Ring Scrapper.");
			return;
		}

		setAutomationMode(AutomationMode.FISHING, false);
		selectHotbarSlot(FISHING_HOTBAR_SLOT);
		setAutomationActive(true);
		savePersistedSettings();
		sendClientFeedback("Automation running: Fishing.");
	}

	private static void startSetupSwapFromKeybind() {
		if (ROUTINE_PENDING_NAMES.contains(SETUP_SWAP_ROUTINE_NAME)) {
			requestSetupSwapCancel();
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.getNetworkHandler() == null) {
			sendClientFeedback("Setup swap: not in-game.");
			return;
		}
		if (!isConnectedToSalesServer(client)) {
			sendClientFeedback("Setup swap: not on " + String.join(" / ", SALES_SERVER_HOST_ALIASES) + ".");
			return;
		}
		if (hasCompassInHotbar()) {
			sendClientFeedback("Setup swap: in lobby (compass) -> abort.");
			return;
		}

		SetupSwapMode mode = SETUP_SWAP_MODE;
		SETUP_SWAP_CANCEL_REQUESTED.set(false);
		enqueueRoutine(SETUP_SWAP_ROUTINE_NAME, SalesClientMod::runSetupSwapRoutine);
		sendClientFeedback("Setup swap queued: " + mode.label + ".");
	}

	private static void requestSetupSwapCancel() {
		boolean first = SETUP_SWAP_CANCEL_REQUESTED.compareAndSet(false, true);
		Thread routineThread = SETUP_SWAP_ROUTINE_THREAD.get();
		if (first) {
			sendClientFeedback(routineThread == null ? "Setup swap: cancel requested (queued)." : "Setup swap: cancel requested.");
		}
		if (routineThread != null) {
			routineThread.interrupt();
		}
		closeCurrentHandledScreen();
	}

	static void setAutomationMode(AutomationMode mode, boolean queueEggRun) {
		automationMode = mode;
		switch (mode) {
			case FISHING -> {
				FISHING_ENABLED.set(true);
				MUSEUM_ENABLED.set(false);
				EGG_ENABLED.set(false);
				EGG_PENDING.set(false);
				RING_SCRAPPER_ENABLED.set(false);
				RING_SCRAPPER_PENDING.set(false);
				selectHotbarSlot(FISHING_HOTBAR_SLOT);
			}
			case MUSEUM -> {
				FISHING_ENABLED.set(false);
				MUSEUM_ENABLED.set(true);
				EGG_ENABLED.set(false);
				EGG_PENDING.set(false);
				RING_SCRAPPER_ENABLED.set(false);
				RING_SCRAPPER_PENDING.set(false);
			}
			case EGG -> {
				FISHING_ENABLED.set(false);
				MUSEUM_ENABLED.set(false);
				EGG_ENABLED.set(true);
				EGG_PENDING.set(queueEggRun);
				RING_SCRAPPER_ENABLED.set(false);
				RING_SCRAPPER_PENDING.set(false);
			}
			case RING_SCRAPPER -> {
				FISHING_ENABLED.set(false);
				MUSEUM_ENABLED.set(false);
				EGG_ENABLED.set(false);
				EGG_PENDING.set(false);
				RING_SCRAPPER_ENABLED.set(true);
				RING_SCRAPPER_PENDING.set(true);
			}
		}
	}

	static void setAutomationActive(boolean active) {
		AUTOMATION_ACTIVE.set(active);
		if (!active) {
			LOBBY_FAILSAFE_ACTIVE.set(false);
		}
	}

	private static boolean selectHotbarSlot(int hotbarSlot) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return false;
			}
			client.player.getInventory().setSelectedSlot(hotbarSlot);
			return true;
		}, false);
	}

	private static void normalizeAutomationState() {
		boolean fishing = FISHING_ENABLED.get();
		boolean museum = MUSEUM_ENABLED.get();
		boolean egg = EGG_ENABLED.get();
		boolean ringScrapper = RING_SCRAPPER_ENABLED.get();
		int enabledModes = (fishing ? 1 : 0) + (museum ? 1 : 0) + (egg ? 1 : 0) + (ringScrapper ? 1 : 0);

		if (enabledModes > 1) {
			if (automationMode == AutomationMode.EGG) {
				fishing = false;
				museum = false;
				ringScrapper = false;
			} else if (automationMode == AutomationMode.MUSEUM) {
				fishing = false;
				egg = false;
				ringScrapper = false;
			} else if (automationMode == AutomationMode.RING_SCRAPPER) {
				fishing = false;
				museum = false;
				egg = false;
			} else {
				museum = false;
				egg = false;
				ringScrapper = false;
			}
		}

		if (!egg) {
			EGG_PENDING.set(false);
		}
		if (!ringScrapper) {
			RING_SCRAPPER_PENDING.set(false);
		}

		FISHING_ENABLED.set(fishing);
		MUSEUM_ENABLED.set(museum);
		EGG_ENABLED.set(egg);
		RING_SCRAPPER_ENABLED.set(ringScrapper);

		if (egg) {
			automationMode = AutomationMode.EGG;
		} else if (ringScrapper) {
			automationMode = AutomationMode.RING_SCRAPPER;
		} else if (museum) {
			automationMode = AutomationMode.MUSEUM;
		} else if (fishing) {
			automationMode = AutomationMode.FISHING;
		}
		AUTOMATION_ACTIVE.set(false);
		LOBBY_FAILSAFE_ACTIVE.set(false);
	}

	private static boolean hasAutomationAccess() {
		return true;
	}

	private static boolean shouldRunLobbyFailsafe() {
		if (!LOBBY_RECONNECT_ENABLED.get()) {
			return false;
		}
		return CACHED_IS_CONNECTED_TO_SERVER.get();
	}

	private static Path getConfigDirPath() {
		try {
			Path configDir = FabricLoader.getInstance().getConfigDir();
			if (configDir != null) {
				return configDir;
			}
		} catch (Exception ignored) {
			// Fallback below.
		}

		// Some custom launchers/clients have odd loader setups. Fall back to gameDir/config.
		try {
			Path gameDir = FabricLoader.getInstance().getGameDir();
			if (gameDir != null) {
				return gameDir.resolve("config");
			}
		} catch (Exception ignored) {
			// Fallback below.
		}

		// Last resort: relative config directory.
		return Path.of("config");
	}

	private static Path getSettingsPath() {
		return getConfigDirPath().resolve(SETTINGS_FILE_NAME);
	}

	static Path getBrandingMediaRootDirForGui() {
		return getConfigDirPath().resolve(BRANDING_MEDIA_DIR_NAME);
	}

	static List<String> getTitleScreenImageOptionsForGui() {
		List<String> options = new ArrayList<>();
		options.add("");
		options.addAll(listBrandingFiles(getBrandingTitleScreenDir(), ".png"));
		return List.copyOf(options);
	}

	static List<String> getStartMusicOptionsForGui() {
		List<String> options = new ArrayList<>();
		options.add("");
		options.addAll(listBrandingFiles(getBrandingStartMusicDir(), ".ogg"));
		return List.copyOf(options);
	}

	static String getSelectedTitleScreenImageForGui() {
		return TITLESCREEN_IMAGE_FILE;
	}

	static String getSelectedStartMusicForGui() {
		return START_MUSIC_FILE;
	}

	static void setSelectedTitleScreenImageForGui(String fileName) {
		TITLESCREEN_IMAGE_FILE = normalizeMediaSelection(fileName, getBrandingTitleScreenDir(), ".png");
	}

	static void setSelectedStartMusicForGui(String fileName) {
		START_MUSIC_FILE = normalizeMediaSelection(fileName, getBrandingStartMusicDir(), ".ogg");
	}

	static Path resolveSelectedTitleScreenImagePath() {
		return resolveSelectedMediaPath(TITLESCREEN_IMAGE_FILE, getBrandingTitleScreenDir(), ".png");
	}

	static Path resolveSelectedStartMusicPath() {
		return resolveSelectedMediaPath(START_MUSIC_FILE, getBrandingStartMusicDir(), ".ogg");
	}

	private static Path getBrandingTitleScreenDir() {
		return getBrandingMediaRootDirForGui().resolve(BRANDING_TITLESCREEN_DIR_NAME);
	}

	private static Path getBrandingStartMusicDir() {
		return getBrandingMediaRootDirForGui().resolve(BRANDING_STARTMUSIC_DIR_NAME);
	}

	static void ensureBrandingMediaDirectories() {
		try {
			Files.createDirectories(getBrandingTitleScreenDir());
		} catch (Exception ignored) {
			// Best-effort only.
		}
		try {
			Files.createDirectories(getBrandingStartMusicDir());
		} catch (Exception ignored) {
			// Best-effort only.
		}
	}

	private static List<String> listBrandingFiles(Path dir, String extensionLower) {
		ensureBrandingMediaDirectories();
		if (dir == null || extensionLower == null || extensionLower.isBlank()) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path path : stream) {
				if (path == null || !Files.isRegularFile(path)) {
					continue;
				}
				String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
				if (fileName.isBlank()) {
					continue;
				}
				if (fileName.toLowerCase(Locale.ROOT).endsWith(extensionLower.toLowerCase(Locale.ROOT))) {
					names.add(fileName);
				}
			}
		} catch (Exception ignored) {
			return List.of();
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return List.copyOf(names);
	}

	private static String normalizeMediaSelection(String rawValue, Path dir, String extensionLower) {
		if (rawValue == null) {
			return "";
		}
		String value = rawValue.trim();
		if (value.isBlank()) {
			return "";
		}
		if (value.contains("/") || value.contains("\\") || value.contains("..")) {
			return "";
		}
		if (extensionLower == null || extensionLower.isBlank()) {
			return "";
		}
		String lowered = value.toLowerCase(Locale.ROOT);
		if (!lowered.endsWith(extensionLower.toLowerCase(Locale.ROOT))) {
			return "";
		}

		Path resolved = resolveSelectedMediaPath(value, dir, extensionLower);
		if (resolved == null || resolved.getFileName() == null) {
			return "";
		}
		return resolved.getFileName().toString();
	}

	private static Path resolveSelectedMediaPath(String fileName, Path dir, String extensionLower) {
		if (fileName == null || fileName.isBlank() || dir == null) {
			return null;
		}
		ensureBrandingMediaDirectories();
		Path candidate = dir.resolve(fileName).normalize();
		if (!candidate.startsWith(dir)) {
			return null;
		}
		if (Files.isRegularFile(candidate)) {
			return candidate;
		}
		for (String listed : listBrandingFiles(dir, extensionLower)) {
			if (!listed.equalsIgnoreCase(fileName)) {
				continue;
			}
			Path listedPath = dir.resolve(listed).normalize();
			if (listedPath.startsWith(dir) && Files.isRegularFile(listedPath)) {
				return listedPath;
			}
		}
		return null;
	}

	private static void loadPersistedSettings() {
		Path settingsPath = getSettingsPath();
		if (!Files.exists(settingsPath)) {
			// Ensure the config file exists on first run.
			savePersistedSettings();
			return;
		}

		Properties properties = new Properties();
		synchronized (SETTINGS_LOCK) {
			try (InputStream inputStream = Files.newInputStream(settingsPath)) {
				properties.load(inputStream);
			} catch (Exception exception) {
				LOGGER.error("Failed to load settings file {}", settingsPath, exception);
				return;
			}
		}

		boolean migrated = false;
		int loadedSettingsVersion = readIntSetting(properties, "settings.version", 1, 1, 100);
		GUI_THEME = readGuiThemeSetting(properties, "gui.theme", GuiTheme.DARK);
		TRAIT_ROLL_BUTTON_ENABLED.set(readBooleanSetting(properties, "toggle.trait_roll_button", TRAIT_ROLL_BUTTON_ENABLED.get()));
		COOKIE_ROLL_BUTTON_ENABLED.set(readBooleanSetting(properties, "toggle.cookie_roll_button", COOKIE_ROLL_BUTTON_ENABLED.get()));
		TITLESCREEN_IMAGE_FILE = normalizeMediaSelection(
			readRawString(properties, "branding.titlescreen", DEFAULT_TITLESCREEN_IMAGE_FILE),
			getBrandingTitleScreenDir(),
			".png"
		);
		START_MUSIC_FILE = normalizeMediaSelection(
			readRawString(properties, "branding.startmusic", DEFAULT_START_MUSIC_FILE),
			getBrandingStartMusicDir(),
			".ogg"
		);
		String encodedWebhook = readStringSetting(properties, "webhook.url.b64", "");
		if (!encodedWebhook.isEmpty()) {
			WEBHOOK_URL = decodeBase64Setting(encodedWebhook, DEFAULT_WEBHOOK_URL);
		} else {
			WEBHOOK_URL = readStringSetting(properties, "webhook.url", DEFAULT_WEBHOOK_URL);
		}
		BOSS_SPAWN_TRIGGER = readStringSetting(properties, "trigger.boss", DEFAULT_BOSS_SPAWN_TRIGGER);
		GEMSHOP_TRIGGER = readStringSetting(properties, "trigger.gemshop", DEFAULT_GEMSHOP_TRIGGER);
		setGemshopBuyNames(readStringSetting(
			properties,
			"gemshop.buy_names",
			DEFAULT_GEMSHOP_BUY_NAMES
		));
		GEMSHOP_STORE_COMMAND = normalizeStoreCommand(readRawString(
			properties,
			"gemshop.store_command",
			DEFAULT_GEMSHOP_STORE_COMMAND
		));
		BLACKMARKET_TRIGGER_TEXT = readStringSetting(properties, "trigger.blackmarket", DEFAULT_BLACKMARKET_TRIGGER_TEXT);
		setBlackmarketBuyNames(readStringSetting(
			properties,
			"blackmarket.buy_names",
			DEFAULT_BLACKMARKET_BUY_NAMES
		));
		BLACKMARKET_STORE_COMMAND = normalizeStoreCommand(readRawString(
			properties,
			"blackmarket.store_command",
			DEFAULT_BLACKMARKET_STORE_COMMAND
		));
		MERCHANT_TRIGGER = readStringSetting(properties, "trigger.merchant", DEFAULT_MERCHANT_TRIGGER);
		MERCHANT_STORE_COMMAND = normalizeStoreCommand(readRawString(
			properties,
			"merchant.store_command",
			DEFAULT_MERCHANT_STORE_COMMAND
		));
		BUFFS_TRIGGER = readStringSetting(properties, "trigger.buffs", DEFAULT_BUFFS_TRIGGER);
		STORE_PURCHASE_TRIGGER = readStringSetting(properties, "trigger.store", DEFAULT_STORE_PURCHASE_TRIGGER);
		setMerchantProtectedNames(readStringSetting(
			properties,
			"merchant.protected_names",
			DEFAULT_MERCHANT_PROTECTED_NAMES
		));
		setMerchantBlacklistNames(readStringSetting(
			properties,
			"merchant.blacklist_names",
			DEFAULT_MERCHANT_BLACKLIST_NAMES
		));
		setMerchantWebhookNotifyNames(readStringSetting(
			properties,
			"merchant.webhook_notify_names",
			DEFAULT_MERCHANT_WEBHOOK_NOTIFY_NAMES
		));
		SETUP_SWAP_MODE = readSetupSwapModeSetting(properties, "setup_swap.mode", DEFAULT_SETUP_SWAP_MODE);
		SETUP_SWAP_ARMOR = readSetupSwapArmorSetting(properties, "setup_swap.armor", DEFAULT_SETUP_SWAP_ARMOR);
		SETUP_SWAP_STORE_COMMAND = normalizeStoreCommand(readRawString(
			properties,
			"setup_swap.store_command",
			DEFAULT_SETUP_SWAP_STORE_COMMAND
		));
		SETUP_SWAP_GET_COMMAND = normalizeStoreCommand(readRawString(
			properties,
			"setup_swap.get_command",
			DEFAULT_SETUP_SWAP_GET_COMMAND
		));
		SETUP_SWAP_RING_COUNT = readIntSetting(properties, "setup_swap.ring_count", DEFAULT_SETUP_SWAP_RING_COUNT, 1, 5);
		SETUP_SWAP_ATTACHMENT_COUNT = readIntSetting(properties, "setup_swap.attachment_count", DEFAULT_SETUP_SWAP_ATTACHMENT_COUNT, 1, 6);
		SETUP_SWAP_BOSS_RELICS_ENABLED.set(readBooleanSetting(
			properties,
			"setup_swap.boss_relics_enabled",
			DEFAULT_SETUP_SWAP_BOSS_RELICS_ENABLED
		));
		SETUP_SWAP_BOSS_RELIC_COUNT = readIntSetting(
			properties,
			"setup_swap.boss_relic_count",
			DEFAULT_SETUP_SWAP_BOSS_RELIC_COUNT,
			1,
			6
		);

		WEBHOOK_DELAY_MS = readLongSetting(properties, "delay.webhook", DEFAULT_WEBHOOK_DELAY_MS, 50, 30_000);
		WEBHOOK_PING_COUNT = readIntSetting(properties, "webhook.ping_count", DEFAULT_WEBHOOK_PING_COUNT, 1, 25);
		BOSS_WEBHOOK_PING_ENABLED.set(readBooleanSetting(
			properties,
			"webhook.ping.boss",
			DEFAULT_BOSS_WEBHOOK_PING_ENABLED
		));
		GEMSHOP_WEBHOOK_PING_ENABLED.set(readBooleanSetting(
			properties,
			"webhook.ping.gemshop",
			DEFAULT_GEMSHOP_WEBHOOK_PING_ENABLED
		));
		BLACKMARKET_WEBHOOK_PING_ENABLED.set(readBooleanSetting(
			properties,
			"webhook.ping.blackmarket",
			DEFAULT_BLACKMARKET_WEBHOOK_PING_ENABLED
		));
		MERCHANT_WEBHOOK_PING_ENABLED.set(readBooleanSetting(
			properties,
			"webhook.ping.merchant",
			DEFAULT_MERCHANT_WEBHOOK_PING_ENABLED
		));
		MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED.set(readBooleanSetting(
			properties,
			"webhook.ping.merchant_notify",
			DEFAULT_MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED
		));
		BUFFS_WEBHOOK_PING_ENABLED.set(readBooleanSetting(
			properties,
			"webhook.ping.buffs",
			DEFAULT_BUFFS_WEBHOOK_PING_ENABLED
		));
		STORE_WEBHOOK_PING_ENABLED.set(readBooleanSetting(
			properties,
			"webhook.ping.store",
			DEFAULT_STORE_WEBHOOK_PING_ENABLED
		));
		long legacyClickDelayMs = readLongSetting(properties, "delay.click", DEFAULT_GEMSHOP_CLICK_DELAY_MS, 20, 10_000);
		GEMSHOP_CLICK_DELAY_MS = readLongSetting(properties, "gemshop.click_delay", DEFAULT_GEMSHOP_CLICK_DELAY_MS, 20, 10_000);
		GEMSHOP_CLICK_COUNT = readIntSetting(properties, "gemshop.click_count", DEFAULT_GEMSHOP_CLICK_COUNT, 1, 50);
		BLACKMARKET_CLICK_DELAY_MS = readLongSetting(properties, "blackmarket.click_delay", DEFAULT_BLACKMARKET_CLICK_DELAY_MS, 20, 10_000);
		BLACKMARKET_CLICK_COUNT = readIntSetting(properties, "blackmarket.click_count", DEFAULT_BLACKMARKET_CLICK_COUNT, 1, 50);
		MERCHANT_CLICK_DELAY_MS = readLongSetting(properties, "merchant.click_delay", legacyClickDelayMs, 20, 10_000);
		BUFFS_CLICK_DELAY_MS = readLongSetting(properties, "buffs.click_delay", legacyClickDelayMs, 20, 10_000);
		long legacyMuseumPullDelayMs = readLongSetting(properties, "museum.box_pull_delay", legacyClickDelayMs, 20, 10_000);
		MUSEUM_CLICK_DELAY_MS = readLongSetting(properties, "museum.click_delay", legacyMuseumPullDelayMs, 20, 10_000);
		LOBBY_CLICK_DELAY_MS = readLongSetting(properties, "lobby.click_delay", legacyClickDelayMs, 20, 10_000);
		AUTO_STORE_DELAY_MS = readLongSetting(properties, "auto_store.delay", DEFAULT_AUTO_STORE_DELAY_MS, 0, 30_000);
		BOSS_NOTIFY_DELAY_MS = readLongSetting(properties, "boss.notify_delay", DEFAULT_BOSS_NOTIFY_DELAY_MS, 0, 86_400_000);
		SETUP_SWAP_CLICK_DELAY_MS = readLongSetting(properties, "setup_swap.click_delay", DEFAULT_SETUP_SWAP_CLICK_DELAY_MS, 20, 10_000);
		SETUP_SWAP_ATTACHMENT_DELAY_MS = readLongSetting(properties, "setup_swap.attachment_delay", DEFAULT_SETUP_SWAP_ATTACHMENT_DELAY_MS, 0, 10_000);
		EGG_POST_OPEN_DELAY_MS = readLongSetting(properties, "delay.egg_post_open", DEFAULT_EGG_POST_OPEN_DELAY_MS, 0, 10_000);
		EGG_CLICK_DELAY_MS = readLongSetting(properties, "delay.egg_click", DEFAULT_EGG_CLICK_DELAY_MS, 50, 10_000);
		MERCHANT_FIRST_GUI_GAP_MS = readLongSetting(properties, "delay.merchant_gap", DEFAULT_MERCHANT_FIRST_GUI_GAP_MS, 50, 10_000);
		MERCHANT_BARRIER_SCAN_DELAY_MS = readLongSetting(properties, "merchant.barrier_scan_delay", DEFAULT_MERCHANT_BARRIER_SCAN_DELAY_MS, 0, 10_000);
		MERCHANT_SALVAGE_TO_ALL_GAP_MS = readLongSetting(
			properties,
			"delay.merchant_salvage_to_all",
			DEFAULT_MERCHANT_SALVAGE_TO_ALL_GAP_MS,
			0,
			20_000
		);
		COMMAND_TO_GUI_DELAY_MS = readLongSetting(properties, "delay.command_to_gui", DEFAULT_COMMAND_TO_GUI_DELAY_MS, 50, 10_000);
		MERCHANT_REPEAT_DELAY_MS = readLongSetting(properties, "delay.merchant_repeat", DEFAULT_MERCHANT_REPEAT_DELAY_MS, 500, 120_000);
		MERCHANT_REPEAT_COUNT = readIntSetting(properties, "merchant.repeat_count", DEFAULT_MERCHANT_REPEAT_COUNT, 1, 25);
		MERCHANT_WEBHOOK_NOTIFY_PING_COUNT = readIntSetting(
			properties,
			"merchant.webhook_notify_ping_count",
			DEFAULT_MERCHANT_WEBHOOK_NOTIFY_PING_COUNT,
			1,
			25
		);
		TRAIT_ROLL_DELAY_MS = readLongSetting(properties, "trait.roll_delay", DEFAULT_TRAIT_ROLL_DELAY_MS, 20, 10_000);
		TRAIT_ROLL_TARGET = readTraitRollTargetSetting(properties, "trait.target", DEFAULT_TRAIT_ROLL_TARGET);
		COOKIE_ROLL_DELAY_MS = readLongSetting(properties, "cookie.roll_delay", DEFAULT_COOKIE_ROLL_DELAY_MS, 20, 10_000);
		COOKIE_ROLL_TARGET = readCookieRollTargetSetting(properties, "cookie.target", DEFAULT_COOKIE_ROLL_TARGET);
		RING_SCRAPPER_CLICK_DELAY_MS = readLongSetting(
			properties,
			"ring_scrapper.click_delay",
			DEFAULT_RING_SCRAPPER_CLICK_DELAY_MS,
			20,
			10_000
		);
		FISHING_FAILSAFE_MS = readLongSetting(properties, "delay.fishing_failsafe", DEFAULT_FISHING_FAILSAFE_MS, 1000, 120_000);
		LOBBY_FAILSAFE_REPEAT_MS = readLongSetting(properties, "delay.lobby_repeat", DEFAULT_LOBBY_FAILSAFE_REPEAT_MS, 1000, 120_000);
		MUSEUM_BOX_DELAY_MS = readLongSetting(properties, "museum.box_delay", DEFAULT_MUSEUM_BOX_DELAY_MS, 20, 10_000);
		MUSEUM_RETRY_DELAY_MS = readLongSetting(properties, "museum.retry_delay", DEFAULT_MUSEUM_RETRY_DELAY_MS, 100, 60_000);
		MUSEUM_REOPEN_PHASE_DELAY_MS = readLongSetting(properties, "museum.reopen_phase_delay", DEFAULT_MUSEUM_REOPEN_PHASE_DELAY_MS, 100, 10_000);
		MUSEUM_BOX_OPEN_COUNT = readIntSetting(properties, "museum.box_open_count", DEFAULT_MUSEUM_BOX_OPEN_COUNT, 1, 64);
		MUSEUM_PV_NUMBER = readIntSetting(properties, "museum.pv_number", DEFAULT_MUSEUM_PV_NUMBER, 1, 100);
		MUSEUM_VAULT_COMMAND = readStringSetting(properties, "museum.vault_command", DEFAULT_MUSEUM_VAULT_COMMAND);
		if (MUSEUM_VAULT_COMMAND.isBlank()) {
			MUSEUM_VAULT_COMMAND = DEFAULT_MUSEUM_VAULT_COMMAND;
		}

		WEBHOOK_ENABLED.set(readBooleanSetting(properties, "toggle.webhook", WEBHOOK_ENABLED.get()));
		TriggerActionMode gemshopMode = readTriggerModeSetting(
			properties,
			"mode.gemshop",
			readBooleanSetting(properties, "toggle.gemshop", GEMSHOP_ENABLED.get())
				? TriggerActionMode.AUTO
				: TriggerActionMode.OFF
		);
		TriggerActionMode blackmarketMode = readTriggerModeSetting(
			properties,
			"mode.blackmarket",
			readBooleanSetting(properties, "toggle.blackmarket", BLACKMARKET_ENABLED.get())
				? TriggerActionMode.AUTO
				: TriggerActionMode.OFF
		);
		TriggerActionMode merchantMode = readTriggerModeSetting(
			properties,
			"mode.merchant",
			readBooleanSetting(properties, "toggle.merchant", MERCHANT_ENABLED.get())
				? TriggerActionMode.AUTO
				: TriggerActionMode.OFF
		);
		TriggerActionMode buffsMode = readTriggerModeSetting(
			properties,
			"mode.buffs",
			readBooleanSetting(properties, "toggle.buffs", BUFFS_ENABLED.get())
				? TriggerActionMode.AUTO
				: TriggerActionMode.OFF
		);
		TriggerActionMode storeMode = readTriggerModeSetting(
			properties,
			"mode.store",
			readBooleanSetting(properties, "toggle.store", STORE_PURCHASE_ENABLED.get())
				? TriggerActionMode.AUTO
				: TriggerActionMode.OFF
		);
		applyTriggerActionMode(GEMSHOP_ENABLED, GEMSHOP_WEBHOOK_ONLY, gemshopMode);
		applyTriggerActionMode(BLACKMARKET_ENABLED, BLACKMARKET_WEBHOOK_ONLY, blackmarketMode);
		applyTriggerActionMode(MERCHANT_ENABLED, MERCHANT_WEBHOOK_ONLY, merchantMode);
		applyTriggerActionMode(BUFFS_ENABLED, BUFFS_WEBHOOK_ONLY, buffsMode);
		applyTriggerActionMode(STORE_PURCHASE_ENABLED, STORE_WEBHOOK_ONLY, storeMode);
		AUTO_RECONNECT_ENABLED.set(readBooleanSetting(properties, "toggle.auto_reconnect", AUTO_RECONNECT_ENABLED.get()));
		setStartupAutoConnectEnabled(readBooleanSetting(
			properties,
			"toggle.auto_connect_startup",
			AUTO_CONNECT_ON_STARTUP_ENABLED.get()
		));
		LOBBY_RECONNECT_ENABLED.set(readBooleanSetting(properties, "toggle.lobby_reconnect", LOBBY_RECONNECT_ENABLED.get()));
		SERVER_ONLY_TRIGGERS.set(readBooleanSetting(properties, "toggle.server_only_triggers", SERVER_ONLY_TRIGGERS.get()));
		AUTO_DAILY_ENABLED.set(readBooleanSetting(properties, "toggle.auto_daily", AUTO_DAILY_ENABLED.get()));
		AUTO_DAILY_PERKS_ENABLED.set(readBooleanSetting(properties, "toggle.auto_daily_perks", AUTO_DAILY_PERKS_ENABLED.get()));
		AUTO_DAILY_FREECREDITS_ENABLED.set(readBooleanSetting(properties, "toggle.auto_daily_freecredits", AUTO_DAILY_FREECREDITS_ENABLED.get()));
		AUTO_DAILY_KEYALL_ENABLED.set(readBooleanSetting(properties, "toggle.auto_daily_keyall", AUTO_DAILY_KEYALL_ENABLED.get()));
		AUTO_DAILY_PERKS_LAST_RUN = normalizeAutoDailyTimestamp(readRawString(properties, "auto_daily.perks_last_run", ""));
		AUTO_DAILY_FREECREDITS_LAST_RUN = normalizeAutoDailyTimestamp(readRawString(properties, "auto_daily.freecredits_last_run", ""));
		AUTO_DAILY_KEYALL_LAST_RUN = normalizeAutoDailyTimestamp(readRawString(properties, "auto_daily.keyall_last_run", ""));
		if (AUTO_DAILY_PERKS_LAST_RUN.isBlank()) {
			AUTO_DAILY_PERKS_LAST_RUN = formatAutoDailyTimestamp(readLongSetting(
				properties,
				"auto_daily.perks_last_run_ms",
				0L,
				0L,
				Long.MAX_VALUE
			));
		}
		if (AUTO_DAILY_FREECREDITS_LAST_RUN.isBlank()) {
			AUTO_DAILY_FREECREDITS_LAST_RUN = formatAutoDailyTimestamp(readLongSetting(
				properties,
				"auto_daily.freecredits_last_run_ms",
				0L,
				0L,
				Long.MAX_VALUE
			));
		}
		if (AUTO_DAILY_KEYALL_LAST_RUN.isBlank()) {
			AUTO_DAILY_KEYALL_LAST_RUN = formatAutoDailyTimestamp(readLongSetting(
				properties,
				"auto_daily.keyall_last_run_ms",
				0L,
				0L,
				Long.MAX_VALUE
			));
		}
		FISHING_ENABLED.set(readBooleanSetting(properties, "toggle.fishing", FISHING_ENABLED.get()));
		MUSEUM_ENABLED.set(readBooleanSetting(properties, "toggle.museum", MUSEUM_ENABLED.get()));
		EGG_ENABLED.set(readBooleanSetting(properties, "toggle.egg", EGG_ENABLED.get()));
		RING_SCRAPPER_ENABLED.set(readBooleanSetting(properties, "toggle.ring_scrapper", RING_SCRAPPER_ENABLED.get()));
		EGG_PENDING.set(readBooleanSetting(properties, "toggle.egg_pending", EGG_PENDING.get()));
		RING_SCRAPPER_PENDING.set(false);

		if (loadedSettingsVersion < SETTINGS_SCHEMA_VERSION) {
			migrated = true;
		}

		EggType loadedEggType = parseEggType(readStringSetting(properties, "egg.type", selectedEggType.displayName));
		if (loadedEggType != null) {
			selectedEggType = loadedEggType;
		}
		EGG_AUTO_OPEN_AMOUNT = normalizeEggAutoOpenAmount(readIntSetting(
			properties,
			"egg.open_amount",
			DEFAULT_EGG_AUTO_OPEN_AMOUNT,
			1,
			9
		));

		automationMode = parseAutomationMode(
			readStringSetting(properties, "automation.mode", automationMode.name()),
			automationMode
		);

		if (migrated) {
			savePersistedSettings();
		}
	}

	static void savePersistedSettings() {
		Properties properties = new Properties();
		WEBHOOK_URL = sanitizeWebhookForStorage(WEBHOOK_URL);
		properties.setProperty("settings.version", Integer.toString(SETTINGS_SCHEMA_VERSION));
		properties.setProperty("gui.theme", GUI_THEME.configKey);
		properties.setProperty("toggle.trait_roll_button", Boolean.toString(TRAIT_ROLL_BUTTON_ENABLED.get()));
		properties.setProperty("toggle.cookie_roll_button", Boolean.toString(COOKIE_ROLL_BUTTON_ENABLED.get()));
		properties.setProperty("branding.titlescreen", TITLESCREEN_IMAGE_FILE);
		properties.setProperty("branding.startmusic", START_MUSIC_FILE);
		properties.setProperty("webhook.url", WEBHOOK_URL);
		properties.setProperty("webhook.url.b64", encodeBase64Setting(WEBHOOK_URL));
		properties.setProperty("trigger.boss", BOSS_SPAWN_TRIGGER);
		properties.setProperty("trigger.gemshop", GEMSHOP_TRIGGER);
		properties.setProperty("gemshop.buy_names", GEMSHOP_BUY_NAMES);
		properties.setProperty("gemshop.store_command", GEMSHOP_STORE_COMMAND);
		properties.setProperty("trigger.blackmarket", BLACKMARKET_TRIGGER_TEXT);
		properties.setProperty("blackmarket.buy_names", BLACKMARKET_BUY_NAMES);
		properties.setProperty("blackmarket.store_command", BLACKMARKET_STORE_COMMAND);
		properties.setProperty("trigger.merchant", MERCHANT_TRIGGER);
		properties.setProperty("merchant.store_command", MERCHANT_STORE_COMMAND);
		properties.setProperty("trigger.buffs", BUFFS_TRIGGER);
		properties.setProperty("trigger.store", STORE_PURCHASE_TRIGGER);
		properties.setProperty("merchant.protected_names", MERCHANT_PROTECTED_NAMES);
		properties.setProperty("merchant.blacklist_names", MERCHANT_BLACKLIST_NAMES);
		properties.setProperty("merchant.webhook_notify_names", MERCHANT_WEBHOOK_NOTIFY_NAMES);
		properties.setProperty("setup_swap.mode", SETUP_SWAP_MODE.configKey);
		properties.setProperty("setup_swap.armor", SETUP_SWAP_ARMOR.configKey);
		properties.setProperty("setup_swap.store_command", SETUP_SWAP_STORE_COMMAND);
		properties.setProperty("setup_swap.get_command", SETUP_SWAP_GET_COMMAND);
		properties.setProperty("setup_swap.ring_count", Integer.toString(SETUP_SWAP_RING_COUNT));
		properties.setProperty("setup_swap.attachment_count", Integer.toString(SETUP_SWAP_ATTACHMENT_COUNT));
		properties.setProperty("setup_swap.boss_relics_enabled", Boolean.toString(SETUP_SWAP_BOSS_RELICS_ENABLED.get()));
		properties.setProperty("setup_swap.boss_relic_count", Integer.toString(SETUP_SWAP_BOSS_RELIC_COUNT));

		properties.setProperty("delay.webhook", Long.toString(WEBHOOK_DELAY_MS));
		properties.setProperty("webhook.ping_count", Integer.toString(WEBHOOK_PING_COUNT));
		properties.setProperty("webhook.ping.boss", Boolean.toString(BOSS_WEBHOOK_PING_ENABLED.get()));
		properties.setProperty("webhook.ping.gemshop", Boolean.toString(GEMSHOP_WEBHOOK_PING_ENABLED.get()));
		properties.setProperty("webhook.ping.blackmarket", Boolean.toString(BLACKMARKET_WEBHOOK_PING_ENABLED.get()));
		properties.setProperty("webhook.ping.merchant", Boolean.toString(MERCHANT_WEBHOOK_PING_ENABLED.get()));
		properties.setProperty("webhook.ping.merchant_notify", Boolean.toString(MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED.get()));
		properties.setProperty("webhook.ping.buffs", Boolean.toString(BUFFS_WEBHOOK_PING_ENABLED.get()));
		properties.setProperty("webhook.ping.store", Boolean.toString(STORE_WEBHOOK_PING_ENABLED.get()));
		properties.setProperty("gemshop.click_delay", Long.toString(GEMSHOP_CLICK_DELAY_MS));
		properties.setProperty("gemshop.click_count", Integer.toString(GEMSHOP_CLICK_COUNT));
		properties.setProperty("blackmarket.click_delay", Long.toString(BLACKMARKET_CLICK_DELAY_MS));
		properties.setProperty("blackmarket.click_count", Integer.toString(BLACKMARKET_CLICK_COUNT));
		properties.setProperty("merchant.click_delay", Long.toString(MERCHANT_CLICK_DELAY_MS));
		properties.setProperty("buffs.click_delay", Long.toString(BUFFS_CLICK_DELAY_MS));
		properties.setProperty("museum.click_delay", Long.toString(MUSEUM_CLICK_DELAY_MS));
		properties.setProperty("lobby.click_delay", Long.toString(LOBBY_CLICK_DELAY_MS));
		properties.setProperty("auto_store.delay", Long.toString(AUTO_STORE_DELAY_MS));
		properties.setProperty("boss.notify_delay", Long.toString(BOSS_NOTIFY_DELAY_MS));
		properties.setProperty("setup_swap.click_delay", Long.toString(SETUP_SWAP_CLICK_DELAY_MS));
		properties.setProperty("setup_swap.attachment_delay", Long.toString(SETUP_SWAP_ATTACHMENT_DELAY_MS));
		properties.setProperty("delay.egg_post_open", Long.toString(EGG_POST_OPEN_DELAY_MS));
		properties.setProperty("delay.egg_click", Long.toString(EGG_CLICK_DELAY_MS));
		properties.setProperty("delay.merchant_gap", Long.toString(MERCHANT_FIRST_GUI_GAP_MS));
		properties.setProperty("merchant.barrier_scan_delay", Long.toString(MERCHANT_BARRIER_SCAN_DELAY_MS));
		properties.setProperty("delay.merchant_salvage_to_all", Long.toString(MERCHANT_SALVAGE_TO_ALL_GAP_MS));
		properties.setProperty("delay.command_to_gui", Long.toString(COMMAND_TO_GUI_DELAY_MS));
		properties.setProperty("delay.merchant_repeat", Long.toString(MERCHANT_REPEAT_DELAY_MS));
		properties.setProperty("merchant.repeat_count", Integer.toString(MERCHANT_REPEAT_COUNT));
		properties.setProperty("merchant.webhook_notify_ping_count", Integer.toString(MERCHANT_WEBHOOK_NOTIFY_PING_COUNT));
		properties.setProperty("trait.roll_delay", Long.toString(TRAIT_ROLL_DELAY_MS));
		properties.setProperty("trait.target", TRAIT_ROLL_TARGET.configKey);
		properties.setProperty("cookie.roll_delay", Long.toString(COOKIE_ROLL_DELAY_MS));
		properties.setProperty("cookie.target", COOKIE_ROLL_TARGET.configKey);
		properties.setProperty("ring_scrapper.click_delay", Long.toString(RING_SCRAPPER_CLICK_DELAY_MS));
		properties.setProperty("delay.fishing_failsafe", Long.toString(FISHING_FAILSAFE_MS));
		properties.setProperty("delay.lobby_repeat", Long.toString(LOBBY_FAILSAFE_REPEAT_MS));
		properties.setProperty("museum.box_delay", Long.toString(MUSEUM_BOX_DELAY_MS));
		properties.setProperty("museum.retry_delay", Long.toString(MUSEUM_RETRY_DELAY_MS));
		properties.setProperty("museum.reopen_phase_delay", Long.toString(MUSEUM_REOPEN_PHASE_DELAY_MS));
		properties.setProperty("museum.box_open_count", Integer.toString(MUSEUM_BOX_OPEN_COUNT));
		properties.setProperty("museum.pv_number", Integer.toString(MUSEUM_PV_NUMBER));
		properties.setProperty("museum.vault_command", MUSEUM_VAULT_COMMAND);

		properties.setProperty("toggle.webhook", Boolean.toString(WEBHOOK_ENABLED.get()));
		properties.setProperty("toggle.gemshop", Boolean.toString(GEMSHOP_ENABLED.get()));
		properties.setProperty("toggle.blackmarket", Boolean.toString(BLACKMARKET_ENABLED.get()));
		properties.setProperty("toggle.merchant", Boolean.toString(MERCHANT_ENABLED.get()));
		properties.setProperty("toggle.buffs", Boolean.toString(BUFFS_ENABLED.get()));
		properties.setProperty("toggle.store", Boolean.toString(STORE_PURCHASE_ENABLED.get()));
		properties.setProperty("mode.gemshop", triggerModeText(getTriggerActionMode(GEMSHOP_ENABLED, GEMSHOP_WEBHOOK_ONLY)));
		properties.setProperty("mode.blackmarket", triggerModeText(getTriggerActionMode(BLACKMARKET_ENABLED, BLACKMARKET_WEBHOOK_ONLY)));
		properties.setProperty("mode.merchant", triggerModeText(getTriggerActionMode(MERCHANT_ENABLED, MERCHANT_WEBHOOK_ONLY)));
		properties.setProperty("mode.buffs", triggerModeText(getTriggerActionMode(BUFFS_ENABLED, BUFFS_WEBHOOK_ONLY)));
		properties.setProperty("mode.store", triggerModeText(getTriggerActionMode(STORE_PURCHASE_ENABLED, STORE_WEBHOOK_ONLY)));
		properties.setProperty("toggle.auto_reconnect", Boolean.toString(AUTO_RECONNECT_ENABLED.get()));
		properties.setProperty("toggle.auto_connect_startup", Boolean.toString(AUTO_CONNECT_ON_STARTUP_ENABLED.get()));
		properties.setProperty("toggle.lobby_reconnect", Boolean.toString(LOBBY_RECONNECT_ENABLED.get()));
		properties.setProperty("toggle.server_only_triggers", Boolean.toString(SERVER_ONLY_TRIGGERS.get()));
		properties.setProperty("toggle.auto_daily", Boolean.toString(AUTO_DAILY_ENABLED.get()));
		properties.setProperty("toggle.auto_daily_perks", Boolean.toString(AUTO_DAILY_PERKS_ENABLED.get()));
		properties.setProperty("toggle.auto_daily_freecredits", Boolean.toString(AUTO_DAILY_FREECREDITS_ENABLED.get()));
		properties.setProperty("toggle.auto_daily_keyall", Boolean.toString(AUTO_DAILY_KEYALL_ENABLED.get()));
		properties.setProperty("auto_daily.perks_last_run", AUTO_DAILY_PERKS_LAST_RUN);
		properties.setProperty("auto_daily.freecredits_last_run", AUTO_DAILY_FREECREDITS_LAST_RUN);
		properties.setProperty("auto_daily.keyall_last_run", AUTO_DAILY_KEYALL_LAST_RUN);
		properties.setProperty("toggle.fishing", Boolean.toString(FISHING_ENABLED.get()));
		properties.setProperty("toggle.museum", Boolean.toString(MUSEUM_ENABLED.get()));
		properties.setProperty("toggle.egg", Boolean.toString(EGG_ENABLED.get()));
		properties.setProperty("toggle.ring_scrapper", Boolean.toString(RING_SCRAPPER_ENABLED.get()));
		properties.setProperty("toggle.egg_pending", Boolean.toString(EGG_PENDING.get()));
		properties.setProperty("egg.type", selectedEggType.displayName);
		properties.setProperty("egg.open_amount", Integer.toString(EGG_AUTO_OPEN_AMOUNT));
		properties.setProperty("automation.mode", automationMode.name());

		Path settingsPath = getSettingsPath();
		synchronized (SETTINGS_LOCK) {
			try {
				Files.createDirectories(settingsPath.getParent());
				try (OutputStream outputStream = Files.newOutputStream(settingsPath)) {
					properties.store(outputStream, "JavaMod Sales settings");
				}
			} catch (Exception exception) {
				LOGGER.error("Failed to save settings file {}", settingsPath, exception);
			}
		}
	}

	static void setMerchantProtectedNames(String rawNames) {
		String normalized = normalizeMerchantProtectedNames(rawNames);
		MERCHANT_PROTECTED_NAMES = normalized;
		MERCHANT_PROTECTED_NAME_PARTS = parseMerchantProtectedNameParts(normalized);
	}

	static void setStartupAutoConnectEnabled(boolean enabled) {
		AUTO_CONNECT_ON_STARTUP_ENABLED.set(enabled);
		if (enabled) {
			STARTUP_AUTO_CONNECT_ATTEMPTED.set(false);
		}
	}

	static void setMerchantBlacklistNames(String rawNames) {
		String normalized = normalizeMerchantProtectedNames(rawNames);
		MERCHANT_BLACKLIST_NAMES = normalized;
		MERCHANT_BLACKLIST_NAME_PARTS = parseMerchantProtectedNameParts(normalized);
	}

	static void setMerchantWebhookNotifyNames(String rawNames) {
		String normalized = normalizeMerchantProtectedNames(rawNames);
		MERCHANT_WEBHOOK_NOTIFY_NAMES = normalized;
		MERCHANT_WEBHOOK_NOTIFY_NAME_PARTS = parseMerchantProtectedNameParts(normalized);
	}

	static void setGemshopBuyNames(String rawNames) {
		String normalized = normalizeMerchantProtectedNames(rawNames);
		GEMSHOP_BUY_NAMES = normalized;
		GEMSHOP_BUY_NAME_PARTS = parseMerchantProtectedNameParts(normalized);
	}

	static void setBlackmarketBuyNames(String rawNames) {
		String normalized = normalizeMerchantProtectedNames(rawNames);
		BLACKMARKET_BUY_NAMES = normalized;
		BLACKMARKET_BUY_NAME_PARTS = parseMerchantProtectedNameParts(normalized);
	}

	private static List<String> parseConfiguredNameList(String names) {
		if (names == null || names.isBlank()) {
			return List.of();
		}

		String[] parts = names.split(";");
		List<String> parsed = new ArrayList<>();
		for (String part : parts) {
			if (part == null) {
				continue;
			}
			String trimmed = part.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			parsed.add(trimmed);
		}
		return parsed;
	}

	static String normalizeStoreCommand(String rawCommand) {
		if (rawCommand == null) {
			return "";
		}

		String normalized = rawCommand.trim();
		if (normalized.isBlank()) {
			return "";
		}
		if (normalized.startsWith("/")) {
			normalized = normalized.substring(1).trim();
		}
		if (normalized.equalsIgnoreCase("off")
			|| normalized.equalsIgnoreCase("none")
			|| normalized.equalsIgnoreCase("disable")
			|| normalized.equalsIgnoreCase("disabled")) {
			return "";
		}

		Matcher vaultShortMatcher = VAULT_SHORT_COMMAND_PATTERN.matcher(normalized);
		if (vaultShortMatcher.matches()) {
			String base = vaultShortMatcher.group(1).toLowerCase(Locale.ROOT);
			String number = vaultShortMatcher.group(2);
			return base + " " + number;
		}
		return normalized;
	}

	private static String normalizeMerchantProtectedNames(String rawNames) {
		if (rawNames == null || rawNames.isBlank()) {
			return "";
		}

		String[] parts = rawNames.split(";");
		List<String> cleaned = new ArrayList<>();
		List<String> seenLower = new ArrayList<>();
		for (String part : parts) {
			if (part == null) {
				continue;
			}
			String trimmed = part.trim();
			if (trimmed.isEmpty()) {
				continue;
			}

			String lowered = trimmed.toLowerCase(Locale.ROOT);
			if (seenLower.contains(lowered)) {
				continue;
			}
			seenLower.add(lowered);
			cleaned.add(trimmed);
		}
		return String.join(";", cleaned);
	}

	private static List<String> parseMerchantProtectedNameParts(String names) {
		if (names == null || names.isBlank()) {
			return List.of();
		}

		String[] parts = names.split(";");
		List<String> parsed = new ArrayList<>();
		for (String part : parts) {
			if (part == null) {
				continue;
			}
			String lowered = part.trim().toLowerCase(Locale.ROOT);
			if (lowered.isEmpty() || parsed.contains(lowered)) {
				continue;
			}
			parsed.add(lowered);
		}
		return List.copyOf(parsed);
	}

	private static boolean readBooleanSetting(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	private static long readLongSetting(Properties properties, String key, long fallback, long min, long max) {
		String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			long parsed = Long.parseLong(value.trim());
			if (parsed < min || parsed > max) {
				return fallback;
			}
			return parsed;
		} catch (Exception exception) {
			return fallback;
		}
	}

	private static int readIntSetting(Properties properties, String key, int fallback, int min, int max) {
		String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			int parsed = Integer.parseInt(value.trim());
			if (parsed < min || parsed > max) {
				return fallback;
			}
			return parsed;
		} catch (Exception exception) {
			return fallback;
		}
	}

	private static String readStringSetting(Properties properties, String key, String fallback) {
		String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? fallback : trimmed;
	}

	private static String readRawString(Properties properties, String key, String fallback) {
		String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		return value.trim();
	}

	private static BigDecimal readBigDecimalSetting(Properties properties, String key, BigDecimal fallback) {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		BigDecimal parsed = parseDecimalAmount(value);
		return parsed == null ? fallback : parsed;
	}

	private static BigDecimal parseDecimalAmount(String rawAmount) {
		if (rawAmount == null || rawAmount.isBlank()) {
			return null;
		}
		try {
			String normalized = rawAmount.trim().replace(",", ".");
			return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
		} catch (Exception exception) {
			return null;
		}
	}

	private static AutomationMode parseAutomationMode(String raw, AutomationMode fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return AutomationMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (Exception exception) {
			return fallback;
		}
	}

	private static String normalizeAutoDailyTimestamp(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		LocalDateTime parsed = parseAutoDailyTimestamp(value);
		if (parsed == null) {
			return "";
		}
		return AUTO_DAILY_TIMESTAMP_FORMATTER.format(parsed);
	}

	private static LocalDateTime parseAutoDailyTimestamp(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		String trimmed = value.trim();
		try {
			return LocalDateTime.parse(trimmed, AUTO_DAILY_TIMESTAMP_FORMATTER);
		} catch (Exception ignored) {
			// fall through
		}
		try {
			return LocalDateTime.parse(trimmed, AUTO_DAILY_TIMESTAMP_SECONDS_FORMATTER);
		} catch (Exception ignored) {
			// fall through
		}
		try {
			return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		} catch (Exception ignored) {
			// fall through
		}

		// Accept ISO-ish values where users may have replaced 'T' with a space (or vice versa).
		String swapped = trimmed.contains("T")
			? trimmed.replace('T', ' ')
			: trimmed.replace(' ', 'T');
		try {
			return LocalDateTime.parse(swapped, AUTO_DAILY_TIMESTAMP_FORMATTER);
		} catch (Exception ignored) {
			// fall through
		}
		try {
			return LocalDateTime.parse(swapped, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		} catch (Exception ignored) {
			// fall through
		}

		return null;
	}

	private static String formatAutoDailyTimestamp(long epochMs) {
		if (epochMs <= 0L) {
			return "";
		}
		try {
			LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
			return AUTO_DAILY_TIMESTAMP_FORMATTER.format(dateTime);
		} catch (Exception exception) {
			return "";
		}
	}

	private static long parseAutoDailyTimestampToEpochMs(String value) {
		LocalDateTime parsed = parseAutoDailyTimestamp(value);
		if (parsed == null) {
			return -1L;
		}
		try {
			return parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		} catch (Exception exception) {
			return -1L;
		}
	}

	static String normalizeWebhookInput(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
	}

	private static boolean isWebhookBaseOnly(String value) {
		String normalized = normalizeWebhookInput(value).replace('\\', '/').trim();
		return normalized.equalsIgnoreCase("https://discord.com/api/webhooks")
			|| normalized.equalsIgnoreCase("https://discord.com/api/webhooks/")
			|| normalized.equalsIgnoreCase("http://discord.com/api/webhooks")
			|| normalized.equalsIgnoreCase("http://discord.com/api/webhooks/")
			|| normalized.equalsIgnoreCase("discord.com/api/webhooks")
			|| normalized.equalsIgnoreCase("discord.com/api/webhooks/");
	}

	private static boolean hasWebhookIdAndToken(String value) {
		String resolved = resolveWebhookUrl(value);
		return resolved.matches("^https?://(discord\\.com|discordapp\\.com)/api/webhooks/\\d+/\\S+$");
	}

	static String sanitizeWebhookForStorage(String value) {
		String normalized = normalizeWebhookInput(value);
		if (normalized.isEmpty()) {
			return "";
		}
		return resolveWebhookUrl(normalized);
	}

	static String protectWebhookFromTruncation(String candidate, String current) {
		String normalizedCandidate = normalizeWebhookInput(candidate);
		if (normalizedCandidate.isEmpty()) {
			return "";
		}
		if (isWebhookBaseOnly(normalizedCandidate) && hasWebhookIdAndToken(current)) {
			LOGGER.warn("Blocked truncated webhook overwrite, keeping existing full webhook.");
			return current;
		}
		return normalizedCandidate;
	}

	private static String resolveWebhookUrl(String rawValue) {
		String value = normalizeWebhookInput(rawValue);
		if (value.isEmpty()) {
			return "";
		}

		// Accept plain "id/token" or "id token" to bypass URL input restrictions.
		String compact = value.replace('\\', '/').trim();
		if (compact.matches("^\\d+/\\S+$")) {
			return "https://discord.com/api/webhooks/" + compact;
		}
		if (compact.matches("^\\d+\\s+\\S+$")) {
			String[] parts = compact.split("\\s+", 2);
			return "https://discord.com/api/webhooks/" + parts[0] + "/" + parts[1];
		}

		// Accept Base64 encoded full URL as input.
		String decoded = decodeBase64Setting(value, "");
		if (!decoded.isEmpty()) {
			String decodedTrimmed = decoded.trim();
			if (decodedTrimmed.startsWith("http://") || decodedTrimmed.startsWith("https://")) {
				return decodedTrimmed;
			}
		}

		return value;
	}

	private static String encodeBase64Setting(String value) {
		try {
			return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
		} catch (Exception exception) {
			return "";
		}
	}

	private static String decodeBase64Setting(String value, String fallback) {
		try {
			byte[] decoded = Base64.getDecoder().decode(value);
			return new String(decoded, StandardCharsets.UTF_8);
		} catch (Exception exception) {
			return fallback;
		}
	}

	private static boolean hasWebhookUrlConfigured() {
		return !resolveWebhookUrl(WEBHOOK_URL).isEmpty();
	}

	private static String onOff(boolean value) {
		return value ? "on" : "off";
	}

	private static String triggerModeText(TriggerActionMode mode) {
		return mode.configKey;
	}

	private static GuiTheme readGuiThemeSetting(Properties properties, String key, GuiTheme fallback) {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "dark" -> GuiTheme.DARK;
			case "light" -> GuiTheme.LIGHT;
			default -> fallback;
		};
	}

	private static SetupSwapMode readSetupSwapModeSetting(Properties properties, String key, SetupSwapMode fallback) {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "gem" -> SetupSwapMode.GEM;
			case "stars", "star" -> SetupSwapMode.STARS;
			case "money", "cash" -> SetupSwapMode.MONEY;
			default -> fallback;
		};
	}

	private static SetupSwapArmor readSetupSwapArmorSetting(Properties properties, String key, SetupSwapArmor fallback) {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (SetupSwapArmor armor : SetupSwapArmor.values()) {
			if (armor.configKey.equalsIgnoreCase(normalized)) {
				return armor;
			}
		}
		return fallback;
	}

	private static TraitRollTarget readTraitRollTargetSetting(Properties properties, String key, TraitRollTarget fallback) {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (TraitRollTarget target : TraitRollTarget.values()) {
			if (target.configKey.equalsIgnoreCase(normalized)) {
				return target;
			}
		}
		return fallback;
	}

	private static CookieRollTarget readCookieRollTargetSetting(Properties properties, String key, CookieRollTarget fallback) {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		for (CookieRollTarget target : CookieRollTarget.values()) {
			if (target.configKey.equalsIgnoreCase(normalized)) {
				return target;
			}
		}
		return fallback;
	}

static TriggerActionMode getTriggerActionMode(AtomicBoolean enabled, AtomicBoolean webhookOnly) {
		boolean automationEnabled = enabled.get();
		boolean webhookEnabled = webhookOnly.get();
		if (!automationEnabled && !webhookEnabled) {
			return TriggerActionMode.OFF;
		}
		if (!automationEnabled) {
			return TriggerActionMode.WEBHOOK;
		}
		return webhookEnabled ? TriggerActionMode.BOTH : TriggerActionMode.AUTO;
	}

	static void applyTriggerActionMode(
		AtomicBoolean enabled,
		AtomicBoolean webhookOnly,
		TriggerActionMode mode
	) {
		switch (mode) {
			case OFF -> {
				enabled.set(false);
				webhookOnly.set(false);
			}
			case WEBHOOK -> {
				enabled.set(false);
				webhookOnly.set(true);
			}
			case AUTO -> {
				enabled.set(true);
				webhookOnly.set(false);
			}
			case BOTH -> {
				enabled.set(true);
				webhookOnly.set(true);
			}
		}
	}

	private static TriggerActionMode readTriggerModeSetting(
		Properties properties,
		String key,
		TriggerActionMode fallback
	) {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "off" -> TriggerActionMode.OFF;
			case "webhook" -> TriggerActionMode.WEBHOOK;
			case "both" -> TriggerActionMode.BOTH;
			case "auto", "on" -> TriggerActionMode.AUTO;
			default -> fallback;
		};
	}

	private static String yesNo(boolean value) {
		return value ? "yes" : "no";
	}

	private static String eggTypeList() {
		return "default|desert|cactus|ice|hell|heavenly|brain-rot|dino|pumpkin|witch|robot|void|corrupt|dragon";
	}

	private static EggType parseEggType(String rawEggType) {
		if (rawEggType == null || rawEggType.isBlank()) {
			return null;
		}

		String normalized = rawEggType
			.toLowerCase(Locale.ROOT)
			.replace("-", "")
			.replace("_", "")
			.replace(" ", "");

		return switch (normalized) {
			case "default" -> EggType.DEFAULT;
			case "desert" -> EggType.DESERT;
			case "cactus" -> EggType.CACTUS;
			case "ice" -> EggType.ICE;
			case "hell" -> EggType.HELL;
			case "heavenly" -> EggType.HEAVENLY;
			case "brainrot" -> EggType.BRAIN_ROT;
			case "dino" -> EggType.DINO;
			case "pumpkin" -> EggType.PUMPKIN;
			case "witch" -> EggType.WITCH;
			case "robot" -> EggType.ROBOT;
			case "void" -> EggType.VOID;
			case "corrupt" -> EggType.CORRUPT;
			case "dragon", "dragonegg" -> EggType.DRAGON;
			default -> null;
		};
	}

	static int normalizeEggAutoOpenAmount(int value) {
		return switch (value) {
			case 1, 3, 9 -> value;
			default -> DEFAULT_EGG_AUTO_OPEN_AMOUNT;
		};
	}

	private static void maybeRecordEggChatConfirmation(String message) {
		if (message == null || message.isBlank()) {
			return;
		}
		// Ignore local mod feedback.
		if (message.startsWith(CHAT_PREFIX)) {
			return;
		}
		if (!EGG_ENABLED.get() || !AUTOMATION_ACTIVE.get()) {
			return;
		}

		EggType eggType = selectedEggType;
		if (eggType == null) {
			return;
		}
		if (eggType.chatNameMatcher.matcher(message).find()) {
			LAST_EGG_CHAT_MATCH_TYPE = eggType;
			LAST_EGG_CHAT_MATCH_MS.set(System.currentTimeMillis());
		}
	}

	private static void maybeHandleTraitRollerChat(String message) {
		if (!TRAIT_ROLLER_RUNNING.get() || message == null || message.isBlank()) {
			return;
		}
		if (message.startsWith(CHAT_PREFIX)) {
			return;
		}

		String lowered = message.toLowerCase(Locale.ROOT);
		if (!lowered.contains("trait") && !lowered.contains("roll")) {
			return;
		}

		TraitRollTarget rolled = parseTraitRollTargetFromText(message);
		if (rolled == null) {
			return;
		}
		if (!isRolledTraitSameOrBetter(rolled, TRAIT_ROLL_TARGET)) {
			return;
		}

		if (!TRAIT_ROLLER_RUNNING.compareAndSet(true, false)) {
			return;
		}

		sendClientFeedback("Trait roller: rolled " + rolled.label + " (target " + TRAIT_ROLL_TARGET.label + ") -> stopping.");
		closeCurrentHandledScreen();
	}

	static void cancelAllRoutinesNow() {
		// Stop persistent/background automation loops.
		setAutomationActive(false);
		EGG_PENDING.set(false);
		EGG_FORCE_REOPEN.set(false);
		RING_SCRAPPER_PENDING.set(false);

		// Stop GUI-local rollers immediately.
		TRAIT_ROLLER_RUNNING.set(false);
		COOKIE_ROLLER_RUNNING.set(false);

		// Cancel setup-swap flow.
		SETUP_SWAP_CANCEL_REQUESTED.set(true);
		Thread setupSwapThread = SETUP_SWAP_ROUTINE_THREAD.get();
		if (setupSwapThread != null) {
			setupSwapThread.interrupt();
		}

		// Cancel currently running event routine thread and drop queued routines.
		Thread eventRoutineThread = EVENT_ROUTINE_THREAD.get();
		if (eventRoutineThread != null) {
			eventRoutineThread.interrupt();
		}
		ROUTINE_EXECUTOR.getQueue().clear();
		ROUTINE_PENDING_NAMES.clear();

		// Close any active container GUI so click-driven routines break immediately.
		closeCurrentHandledScreen();
		sendClientFeedback("All routines cancelled.");
	}

	private static void maybeHandleCookieRollerChat(String message) {
		if (!COOKIE_ROLLER_RUNNING.get() || message == null || message.isBlank()) {
			return;
		}
		if (message.startsWith(CHAT_PREFIX)) {
			return;
		}

		String lowered = message.toLowerCase(Locale.ROOT);
		if (!lowered.contains("cookie")) {
			return;
		}

		CookieRollTarget rolled = parseCookieRollTargetFromText(message);
		if (rolled == null) {
			return;
		}
		if (!isRolledCookieSameOrBetter(rolled, COOKIE_ROLL_TARGET)) {
			return;
		}

		if (!COOKIE_ROLLER_RUNNING.compareAndSet(true, false)) {
			return;
		}

		sendClientFeedback("Cookie roller: rolled " + rolled.label + " (target " + COOKIE_ROLL_TARGET.label + ") -> stopping.");
		closeCurrentHandledScreen();
	}

	private static TraitRollTarget parseTraitRollTargetFromText(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}

		if (TRAIT_KING_PATTERN.matcher(text).find()) {
			return TraitRollTarget.KING;
		}
		if (TRAIT_ACCOUNTANT_PATTERN.matcher(text).find()) {
			return TraitRollTarget.ACCOUNTANT;
		}

		Matcher matcher = TRAIT_TIER_PATTERN.matcher(text);
		if (matcher.find()) {
			String base = matcher.group(1).toLowerCase(Locale.ROOT);
			int tier = romanToTier(matcher.group(2));
			return traitTierTarget(base, tier);
		}

		// Fallback for unusual formatting where numerals may be missing from the chat line.
		if (TRAIT_WIZARD_PATTERN.matcher(text).find()) {
			return TraitRollTarget.WIZARD_I;
		}
		if (TRAIT_MIDAS_PATTERN.matcher(text).find()) {
			return TraitRollTarget.MIDAS_I;
		}
		if (TRAIT_BANKER_PATTERN.matcher(text).find()) {
			return TraitRollTarget.BANKER_I;
		}
		return null;
	}

	private static CookieRollTarget parseCookieRollTargetFromText(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		Matcher matcher = COOKIE_ROLLED_CHAT_PATTERN.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		String token = matcher.group(1);
		if (token == null) {
			return null;
		}
		String normalized = token.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "F+" -> CookieRollTarget.F_PLUS;
			case "F" -> CookieRollTarget.F;
			case "E" -> CookieRollTarget.E;
			case "E+" -> CookieRollTarget.E_PLUS;
			case "D" -> CookieRollTarget.D;
			case "D+" -> CookieRollTarget.D_PLUS;
			case "C" -> CookieRollTarget.C;
			case "C+" -> CookieRollTarget.C_PLUS;
			case "B" -> CookieRollTarget.B;
			case "B+" -> CookieRollTarget.B_PLUS;
			case "A" -> CookieRollTarget.A;
			case "A+" -> CookieRollTarget.A_PLUS;
			case "S" -> CookieRollTarget.S;
			case "S+" -> CookieRollTarget.S_PLUS;
			case "S++" -> CookieRollTarget.S_DOUBLE_PLUS;
			default -> null;
		};
	}

	private static int romanToTier(String roman) {
		if (roman == null) {
			return 1;
		}
		return switch (roman.trim().toUpperCase(Locale.ROOT)) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			case "V" -> 5;
			default -> 1;
		};
	}

	private static TraitRollTarget traitTierTarget(String base, int tier) {
		String normalized = base == null ? "" : base.trim().toLowerCase(Locale.ROOT);
		int clampedTier = MathHelper.clamp(tier, 1, 5);
		return switch (normalized) {
			case "banker" -> switch (clampedTier) {
				case 1 -> TraitRollTarget.BANKER_I;
				case 2 -> TraitRollTarget.BANKER_II;
				case 3 -> TraitRollTarget.BANKER_III;
				case 4 -> TraitRollTarget.BANKER_IV;
				default -> TraitRollTarget.BANKER_V;
			};
			case "midas" -> switch (clampedTier) {
				case 1 -> TraitRollTarget.MIDAS_I;
				case 2 -> TraitRollTarget.MIDAS_II;
				case 3 -> TraitRollTarget.MIDAS_III;
				case 4 -> TraitRollTarget.MIDAS_IV;
				default -> TraitRollTarget.MIDAS_V;
			};
			case "wizard" -> switch (MathHelper.clamp(clampedTier, 1, 3)) {
				case 1 -> TraitRollTarget.WIZARD_I;
				case 2 -> TraitRollTarget.WIZARD_II;
				default -> TraitRollTarget.WIZARD_III;
			};
			default -> null;
		};
	}

	private static boolean isRolledTraitSameOrBetter(TraitRollTarget rolled, TraitRollTarget target) {
		if (rolled == null || target == null) {
			return false;
		}

		boolean rolledWizard = isWizardTrait(rolled);
		boolean targetWizard = isWizardTrait(target);
		if (targetWizard) {
			if (!rolledWizard) {
				return false;
			}
			return wizardTier(rolled) >= wizardTier(target);
		}
		if (rolledWizard) {
			return false;
		}
		return rolled.rankScore >= target.rankScore;
	}

	private static boolean isRolledCookieSameOrBetter(CookieRollTarget rolled, CookieRollTarget target) {
		if (rolled == null || target == null) {
			return false;
		}
		return rolled.rankScore >= target.rankScore;
	}

	private static boolean isWizardTrait(TraitRollTarget target) {
		return target == TraitRollTarget.WIZARD_I
			|| target == TraitRollTarget.WIZARD_II
			|| target == TraitRollTarget.WIZARD_III;
	}

	private static int wizardTier(TraitRollTarget target) {
		return switch (target) {
			case WIZARD_I -> 1;
			case WIZARD_II -> 2;
			case WIZARD_III -> 3;
			default -> 0;
		};
	}

	private static void onChatMessage(String message, boolean gameMessage, String senderName) {
		if (message == null || message.isBlank() || isDuplicateMessage(message)) {
			return;
		}

		if (!hasAutomationAccess()) {
			return;
		}

		maybeRecordEggChatConfirmation(message);
		// Roller result messages can arrive as CHAT or GAME depending on server/plugin formatting.
		// Evaluate them before SERVER_ONLY_TRIGGERS filtering so valid rolls are never skipped.
		maybeHandleTraitRollerChat(message);
		maybeHandleCookieRollerChat(message);

		boolean bossTrigger = WEBHOOK_ENABLED.get() && message.contains(BOSS_SPAWN_TRIGGER);
		boolean gemshopTrigger = message.contains(GEMSHOP_TRIGGER);
		boolean blackMarketTrigger = message.contains(BLACKMARKET_TRIGGER_TEXT);
		boolean merchantTrigger = message.contains(MERCHANT_TRIGGER);
		boolean buffsTrigger = message.contains(BUFFS_TRIGGER);
		boolean storePurchaseTrigger = message.contains(STORE_PURCHASE_TRIGGER);
		boolean possibleAutomationTrigger =
			bossTrigger
				|| gemshopTrigger
				|| blackMarketTrigger
				|| merchantTrigger
				|| buffsTrigger
				|| storePurchaseTrigger;

		// Optional safety: ignore trigger phrases typed by players.
		if (SERVER_ONLY_TRIGGERS.get() && possibleAutomationTrigger) {
			boolean fromPlayer = senderName != null && !senderName.isBlank();
			if (!fromPlayer) {
				fromPlayer = messageLooksLikePlayerChatLine(message);
			}
			if (!fromPlayer) {
				fromPlayer = messageContainsKnownOnlinePlayerNameToken(message);
			}
			if (fromPlayer) {
				return;
			}
		}

		if (bossTrigger) {
			enqueueRoutine("webhook", SalesClientMod::runBossWebhookRoutine);
		}

		if (gemshopTrigger) {
			handleTriggerMode(
				"gemshop",
				getTriggerActionMode(GEMSHOP_ENABLED, GEMSHOP_WEBHOOK_ONLY),
				"Gem Shop restocked",
				SalesClientMod::runGemShopRoutine,
				GEMSHOP_WEBHOOK_PING_ENABLED.get()
			);
		}

		if (blackMarketTrigger) {
			handleTriggerMode(
				"blackmarket",
				getTriggerActionMode(BLACKMARKET_ENABLED, BLACKMARKET_WEBHOOK_ONLY),
				"Black Market restocked",
				SalesClientMod::runBlackmarketRoutine,
				BLACKMARKET_WEBHOOK_PING_ENABLED.get()
			);
		}

		if (merchantTrigger) {
			handleTriggerMode(
				"merchant",
				getTriggerActionMode(MERCHANT_ENABLED, MERCHANT_WEBHOOK_ONLY),
				"Merchant restocked",
				SalesClientMod::runMerchantRoutine,
				MERCHANT_WEBHOOK_PING_ENABLED.get()
			);
		}

		if (buffsTrigger) {
			handleTriggerMode(
				"buffs",
				getTriggerActionMode(BUFFS_ENABLED, BUFFS_WEBHOOK_ONLY),
				"Buffs trigger detected",
				SalesClientMod::runBuffsRoutine,
				BUFFS_WEBHOOK_PING_ENABLED.get()
			);
		}

		if (storePurchaseTrigger) {
			handleTriggerMode(
				"store",
				getTriggerActionMode(STORE_PURCHASE_ENABLED, STORE_WEBHOOK_ONLY),
				"Store purchase trigger detected",
				SalesClientMod::runStorePurchaseRoutine,
				STORE_WEBHOOK_PING_ENABLED.get()
			);
		}
	}

	private static boolean messageLooksLikePlayerChatLine(String message) {
		if (message == null || message.isBlank()) {
			return false;
		}
		String trimmed = message.trim();
		if (trimmed.startsWith(CHAT_PREFIX)) {
			return false;
		}
		if (PLAYER_CHAT_LINE_PATTERN.matcher(trimmed).matches()) {
			return true;
		}

		String speaker = extractPossiblePlayerSpeakerName(trimmed);
		return speaker != null && isKnownOnlinePlayerName(speaker);
	}

	private static boolean messageContainsKnownOnlinePlayerNameToken(String message) {
		if (message == null || message.isBlank()) {
			return false;
		}
		String trimmed = message.trim();
		if (trimmed.startsWith(CHAT_PREFIX)) {
			return false;
		}

		Matcher matcher = POSSIBLE_PLAYER_NAME_TOKEN_PATTERN.matcher(trimmed);
		while (matcher.find()) {
			String token = matcher.group(1);
			if (token == null || token.isBlank()) {
				continue;
			}
			if (isCachedOnlinePlayerName(token)) {
				return true;
			}
		}

		if (isOnlinePlayerNameCacheFresh()) {
			return false;
		}

		return messageContainsKnownOnlinePlayerNameTokenSlow(trimmed);
	}

	private static boolean messageContainsKnownOnlinePlayerNameTokenSlow(String trimmedMessage) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayNetworkHandler handler = client == null ? null : client.getNetworkHandler();
			if (handler == null) {
				return false;
			}

			Matcher matcher = POSSIBLE_PLAYER_NAME_TOKEN_PATTERN.matcher(trimmedMessage);
			while (matcher.find()) {
				String token = matcher.group(1);
				if (token == null || token.isBlank()) {
					continue;
				}
				boolean online = false;
				try {
					online = handler.getCaseInsensitivePlayerInfo(token) != null;
				} catch (Exception ignored) {
					// Older/newer mappings may not have the case-insensitive helper.
				}
				if (!online) {
					try {
						online = handler.getPlayerListEntry(token) != null;
					} catch (Exception ignored) {
						online = false;
					}
				}
				if (online) {
					return true;
				}
			}
			return false;
		}, false);
	}

	private static String extractPossiblePlayerSpeakerName(String trimmedMessage) {
		if (trimmedMessage == null || trimmedMessage.isBlank()) {
			return null;
		}
		Matcher emote = PLAYER_EMOTE_SPEAKER_EXTRACT_PATTERN.matcher(trimmedMessage);
		if (emote.matches()) {
			return emote.group(1);
		}
		Matcher chat = PLAYER_CHAT_SPEAKER_EXTRACT_PATTERN.matcher(trimmedMessage);
		if (chat.matches()) {
			return chat.group(1);
		}
		Matcher prefix = PLAYER_NAME_PREFIX_EXTRACT_PATTERN.matcher(trimmedMessage);
		if (prefix.matches()) {
			return prefix.group(1);
		}
		return null;
	}

	private static boolean isKnownOnlinePlayerName(String name) {
		String normalized = name == null ? "" : name.trim();
		if (normalized.isEmpty() || normalized.length() < 3 || normalized.length() > 16) {
			return false;
		}
		if (isCachedOnlinePlayerName(normalized)) {
			return true;
		}
		if (isOnlinePlayerNameCacheFresh()) {
			return false;
		}
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayNetworkHandler handler = client == null ? null : client.getNetworkHandler();
			if (handler == null) {
				return false;
			}
			try {
				return handler.getCaseInsensitivePlayerInfo(normalized) != null;
			} catch (Exception ignored) {
				// Older/newer mappings may not have the case-insensitive helper.
				try {
					return handler.getPlayerListEntry(normalized) != null;
				} catch (Exception ignored2) {
					return false;
				}
			}
		}, false);
	}

	private static void handleTriggerMode(
		String routineName,
		TriggerActionMode mode,
		String webhookMessage,
		Runnable autoRoutine,
		boolean webhookPingEnabled
	) {
		Runnable webhookRoutine = () -> sendWebhookPing(formatWebhookTriggerMessage(webhookMessage, webhookPingEnabled));
		switch (mode) {
			case OFF -> {
				// no-op
			}
			case WEBHOOK -> enqueueRoutine(routineName + "-webhook", webhookRoutine);
			case AUTO -> enqueueRoutine(routineName, autoRoutine);
			case BOTH -> {
				enqueueRoutine(routineName + "-webhook", webhookRoutine);
				enqueueRoutine(routineName, autoRoutine);
			}
		}
	}

	private static String formatWebhookTriggerMessage(String webhookMessage, boolean pingEnabled) {
		String normalized = webhookMessage == null ? "" : webhookMessage.trim();
		String prefix = pingEnabled ? "@everyone " : "";
		return prefix + "[Sales] " + normalized;
	}

	private static boolean isDuplicateMessage(String message) {
		synchronized (MESSAGE_LOCK) {
			long now = System.currentTimeMillis();
			boolean duplicate = message.equals(lastMessage) && now - lastMessageTimestampMs <= MESSAGE_DEDUP_WINDOW_MS;
			lastMessage = message;
			lastMessageTimestampMs = now;
			return duplicate;
		}
	}

	static void enqueueRoutine(String name, Runnable routine) {
		if (AUTOMATION_ACTIVE.get() && EGG_ENABLED.get()) {
			// After any event routine, run egg once again.
			EGG_PENDING.set(true);
		}

		// Do not enqueue the same routine multiple times while it is pending/running.
		if (!ROUTINE_PENDING_NAMES.add(name)) {
			return;
		}

		int queued = ROUTINE_EXECUTOR.getQueue().size();
		if (queued > 0 || ROUTINE_EXECUTOR.getActiveCount() > 0) {
			long now = System.currentTimeMillis();
			if (now - lastRoutineQueueNotifyMs >= ROUTINE_QUEUE_NOTIFY_COOLDOWN_MS) {
				lastRoutineQueueNotifyMs = now;
				sendClientFeedback("Queued routine: " + name + " (" + (queued + 1) + ")");
			}
		}

		try {
			ROUTINE_EXECUTOR.execute(() -> {
				Thread current = Thread.currentThread();
				EVENT_ROUTINE_THREAD.set(current);
				EVENT_ROUTINE_RUNNING.set(true);
				try {
					routine.run();
				} catch (Throwable throwable) {
					LOGGER.error("Routine failed: {}", name, throwable);
					sendClientFeedback("Routine failed: " + name);
					closeCurrentHandledScreen();
				} finally {
					EVENT_ROUTINE_THREAD.compareAndSet(current, null);
					EVENT_ROUTINE_RUNNING.set(false);
					ROUTINE_PENDING_NAMES.remove(name);
				}
			});
		} catch (Exception exception) {
			ROUTINE_PENDING_NAMES.remove(name);
			throw exception;
		}
	}

	private static boolean isEventRoutineBusy() {
		return EVENT_ROUTINE_RUNNING.get()
			|| ROUTINE_EXECUTOR.getActiveCount() > 0
			|| !ROUTINE_EXECUTOR.getQueue().isEmpty();
	}

	// Sends configurable webhook notifications when a boss spawn message is detected.
	private static void runBossWebhookRoutine() {
		// Do not block the routine queue with delays. Schedule webhook work separately.
		scheduleBossWebhookRound(0L);
		long secondPingDelayMs = Math.max(0L, BOSS_NOTIFY_DELAY_MS);
		if (secondPingDelayMs > 0L) {
			scheduleBossWebhookRound(secondPingDelayMs);
		}
	}

	private static void scheduleBossWebhookRound(long delayMs) {
		long clampedDelay = Math.max(0L, delayMs);
		try {
			WEBHOOK_SCHEDULER.schedule(SalesClientMod::sendBossWebhookRound, clampedDelay, TimeUnit.MILLISECONDS);
		} catch (Exception exception) {
			LOGGER.warn("Failed to schedule boss webhook round", exception);
		}
	}

	private static void sendBossWebhookRound() {
		boolean pingEnabled = BOSS_WEBHOOK_PING_ENABLED.get();
		int pingCount = pingEnabled ? Math.max(1, WEBHOOK_PING_COUNT) : 1;
		String message = (pingEnabled ? "@everyone " : "") + WEBHOOK_BOSS_MESSAGE;
		for (int i = 0; i < pingCount; i++) {
			sendWebhookPing(message);
			if (i < pingCount - 1 && !sleepQuietly(WEBHOOK_DELAY_MS)) {
				return;
			}
		}
	}

	// Opens /gemshop and buys configured item-name matches (10 clicks per match).
	private static void runGemShopRoutine() {
		if (GEMSHOP_BUY_NAME_PARTS.isEmpty()) {
			sendClientFeedback("Gemshop: no targets configured. Use /sales gemshop add <name>.");
			return;
		}

		if (!openContainerWithCommand("gemshop", 6, "gemshop")) {
			return;
		}

		boolean completed = false;
		try {
			int clickCount = Math.max(1, GEMSHOP_CLICK_COUNT);
			long clickDelayMs = Math.max(20L, GEMSHOP_CLICK_DELAY_MS);
			List<Integer> targetSlots = findGemshopTargetSlots(6);
			if (targetSlots.isEmpty()) {
				sendClientFeedback("Gemshop: no matching items detected.");
				return;
			}
			for (int slot : targetSlots) {
				for (int click = 0; click < clickCount; click++) {
					if (!clickSingleSlot(slot, clickDelayMs, "gemshop match")) {
						return;
					}
				}
			}
			completed = true;
		} finally {
			closeCurrentHandledScreen();
		}

		if (completed) {
			stashMatchedItemsToStore(
				GEMSHOP_STORE_COMMAND,
				6,
				SalesClientMod::isGemshopTargetItem,
				"gemshop",
				GEMSHOP_CLICK_DELAY_MS
			);
		}
	}

	private static List<Integer> findGemshopTargetSlots(int expectedRows) {
		return callOnClientThread(() -> {
			List<Integer> slots = new ArrayList<>();
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return slots;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return slots;
			}

			int topSlots = rows * CONTAINER_COLUMNS;
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (isGemshopTargetItem(stack)) {
					slots.add(slot);
				}
			}
			return slots;
		}, List.of());
	}

	private static boolean isGemshopTargetItem(ItemStack stack) {
		if (stack == null || stack.isEmpty() || GEMSHOP_BUY_NAME_PARTS.isEmpty()) {
			return false;
		}

		String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
		String fastBlob = buildStackFastParseBlob(stack).toLowerCase(Locale.ROOT);
		for (String part : GEMSHOP_BUY_NAME_PARTS) {
			if (displayName.contains(part) || fastBlob.contains(part)) {
				return true;
			}
		}
		return false;
	}

	// Opens /blackmarket and buys configured item-name matches (10 clicks per match).
	private static void runBlackmarketRoutine() {
		if (BLACKMARKET_BUY_NAME_PARTS.isEmpty()) {
			sendClientFeedback("Blackmarket: no targets configured. Use /sales bm add <name>.");
			return;
		}

		if (!openContainerWithCommand("blackmarket", 4, "blackmarket")) {
			return;
		}

		boolean completed = false;
		try {
			int clickCount = Math.max(1, BLACKMARKET_CLICK_COUNT);
			long clickDelayMs = Math.max(20L, BLACKMARKET_CLICK_DELAY_MS);
			List<Integer> targetSlots = findBlackmarketTargetSlots(4);
			if (targetSlots.isEmpty()) {
				sendClientFeedback("Blackmarket: no matching items detected.");
				return;
			}
			for (int slot : targetSlots) {
				for (int click = 0; click < clickCount; click++) {
					if (!clickSingleSlot(slot, clickDelayMs, "blackmarket match")) {
						return;
					}
				}
			}
			completed = true;
		} finally {
			closeCurrentHandledScreen();
		}

		if (completed) {
			stashMatchedItemsToStore(
				BLACKMARKET_STORE_COMMAND,
				6,
				SalesClientMod::isBlackmarketTargetItem,
				"blackmarket",
				BLACKMARKET_CLICK_DELAY_MS
			);
		}
	}

	private static List<Integer> findBlackmarketTargetSlots(int expectedRows) {
		return callOnClientThread(() -> {
			List<Integer> slots = new ArrayList<>();
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return slots;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return slots;
			}

			int topSlots = rows * CONTAINER_COLUMNS;
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (isBlackmarketTargetItem(stack)) {
					slots.add(slot);
				}
			}
			return slots;
		}, List.of());
	}

	private static boolean isBlackmarketTargetItem(ItemStack stack) {
		if (stack == null || stack.isEmpty() || BLACKMARKET_BUY_NAME_PARTS.isEmpty()) {
			return false;
		}

		String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
		String fastBlob = buildStackFastParseBlob(stack).toLowerCase(Locale.ROOT);
		for (String part : BLACKMARKET_BUY_NAME_PARTS) {
			if (displayName.contains(part) || fastBlob.contains(part)) {
				return true;
			}
		}
		return false;
	}

	private static boolean stashMatchedItemsToStore(
		String storeCommand,
		int expectedRows,
		Predicate<ItemStack> predicate,
		String routineName,
		long clickDelayMs
	) {
		String normalizedCommand = normalizeStoreCommand(storeCommand);
		if (normalizedCommand.isBlank()) {
			return true;
		}

		long clickDelay = Math.max(20L, clickDelayMs);
		long preOpenDelayMs = Math.max(0L, AUTO_STORE_DELAY_MS);
		closeCurrentHandledScreen();
		if (preOpenDelayMs > 0L && !sleepQuietly(preOpenDelayMs)) {
			return false;
		}
		if (!openContainerWithCommand(normalizedCommand, expectedRows, routineName + " store")) {
			return false;
		}

		try {
			return quickMovePlayerItemsMatching(
				expectedRows,
				predicate,
				routineName + " store move",
				clickDelay
			);
		} finally {
			closeCurrentHandledScreen();
		}
	}

	// Runs a 3-step merchant flow with configurable repeat count and delay.
	private static void runMerchantRoutine() {
		MERCHANT_BLACKLIST_HARD_STOP_REQUESTED.set(false);
		List<String> merchantWebhookNotifiedKeys = new ArrayList<>();
		for (int cycle = 0; cycle < MERCHANT_REPEAT_COUNT; cycle++) {
			if (MERCHANT_BLACKLIST_HARD_STOP_REQUESTED.get()) {
				return;
			}
			boolean stopRemainingRepeats = false;
			boolean completedCycle = false;

			merchantAttempt:
			for (int attempt = 1; attempt <= MERCHANT_FLOW_RESTART_ATTEMPTS; attempt++) {
				if (MERCHANT_BLACKLIST_HARD_STOP_REQUESTED.get()) {
					return;
				}
				if (attempt > 1) {
					sendClientFeedback("Merchant: restarting routine (attempt " + attempt + "/" + MERCHANT_FLOW_RESTART_ATTEMPTS + ")...");
					closeCurrentHandledScreen();
					if (!sleepQuietly(MERCHANT_FLOW_RESTART_DELAY_MS)) {
						return;
					}
				}

				if (!openContainerWithCommand("merchant", 6, "merchant step1")) {
					if (attempt < MERCHANT_FLOW_RESTART_ATTEMPTS) {
						continue;
					}
					return;
				}

				try {
					// Allow the first merchant GUI to refresh/populate before scanning for a full barrier wall.
					if (!sleepQuietly(MERCHANT_BARRIER_SCAN_DELAY_MS)) {
						return;
					}
					if (merchantAbortIfBlacklistedNpcPresent("merchant first GUI")) {
						return;
					}
					int barrierCount = countItemInOpenContainer(6, Items.BARRIER);
					if (barrierCount == MERCHANT_BARRIER_WALL_COUNT) {
						sendClientFeedback("Merchant cancelled: first GUI has " + MERCHANT_BARRIER_WALL_COUNT + " barriers.");
						return;
					}

					Integer firstGuiSyncId = getCurrentHandledScreenSyncId();
					if (firstGuiSyncId == null) {
						sendClientFeedback("Merchant: first GUI is missing, retrying.");
						continue;
					}

					// Give the GUI time to populate before scanning buttons (some clients are slower here).
					if (!sleepQuietly(MERCHANT_FIRST_GUI_GAP_MS)) {
						return;
					}
					Integer buyAllSlot = findFirstTopContainerSlotMatching(6, stack -> stackNameOrBlobContains(stack, "buy all"));
					if (buyAllSlot == null) {
						sendClientFeedback("Merchant: could not find Buy all, retrying.");
						continue;
					}
					if (!clickSingleSlot(buyAllSlot, MERCHANT_CLICK_DELAY_MS, "merchant buy all")) {
						sendClientFeedback("Merchant: Buy all click failed, retrying.");
						continue;
					}
					if (!sleepQuietly(MERCHANT_FIRST_GUI_GAP_MS)) {
						return;
					}
					boolean reloaded = waitForContainerReload(firstGuiSyncId, 6, GUI_OPEN_TIMEOUT_MS);
					if (!reloaded && !waitForContainerRows(6, GUI_OPEN_TIMEOUT_MS)) {
						sendClientFeedback("Merchant: first GUI reload did not finish, retrying.");
						continue;
					}
					if (merchantAbortIfBlacklistedNpcPresent("merchant after buy all")) {
						return;
					}
					merchantNotifyWebhookTargetsIfPresent("merchant after buy all", merchantWebhookNotifiedKeys);

					// Allow the refreshed GUI to populate before scanning the barrier wall again.
					if (!sleepQuietly(MERCHANT_BARRIER_SCAN_DELAY_MS)) {
						return;
					}
					int barrierCountAfterFirstClick = countItemInOpenContainer(6, Items.BARRIER);
					boolean barrierWallAfterFirstClick = barrierCountAfterFirstClick == MERCHANT_BARRIER_WALL_COUNT;
					if (barrierWallAfterFirstClick) {
						// Finish current flow first, then stop future repeats.
						stopRemainingRepeats = true;
					}

					// Give the GUI time to update before scanning the next button.
					if (!sleepQuietly(MERCHANT_FIRST_GUI_GAP_MS)) {
						return;
					}

					int protectedAfterFirstClick = countMerchantProtectedItemsInInventory();
					if (protectedAfterFirstClick > 0) {
						if (!stashMerchantProtectedItems()) {
							return;
						}
						sendClientFeedback("Merchant: protected NPC detected and moved to /" + MERCHANT_STORE_COMMAND + ".");
						if (!sleepQuietly(MERCHANT_REOPEN_AFTER_STASH_DELAY_MS)) {
							return;
						}
						if (!runMerchantFromSecondClickAfterStash(merchantWebhookNotifiedKeys)) {
							if (MERCHANT_BLACKLIST_HARD_STOP_REQUESTED.get()) {
								return;
							}
							sendClientFeedback("Merchant: resume failed, retrying full routine.");
							continue;
						}
					} else {
						Integer minionForgeSlot = findFirstTopContainerSlotMatching(6, stack -> stackNameOrBlobContains(stack, "minion forge"));
						if (minionForgeSlot == null) {
							sendClientFeedback("Merchant: could not find Minion Forge, retrying.");
							continue;
						}
						if (!clickSingleSlot(minionForgeSlot, MERCHANT_CLICK_DELAY_MS, "merchant minion forge")) {
							sendClientFeedback("Merchant: Minion Forge click failed, retrying.");
							continue;
						}
						if (!waitForContainerRows(4, GUI_OPEN_TIMEOUT_MS)) {
							sendClientFeedback("Merchant: salvage GUI did not open, retrying.");
							continue;
						}
						if (merchantAbortIfBlacklistedNpcPresent("merchant salvage menu")) {
							return;
						}

						// Give the salvage GUI time to populate before scanning for buttons.
						if (!sleepQuietly(MERCHANT_FIRST_GUI_GAP_MS)) {
							return;
						}
						Integer salvageSlot = findFirstTopContainerSlotMatching(
							4,
							stack -> stackNameOrBlobContains(stack, "salvage") && !stackNameOrBlobContains(stack, "salvage all")
						);
						if (salvageSlot == null) {
							sendClientFeedback("Merchant: could not find Salvage, retrying.");
							continue;
						}
						if (!clickSingleSlot(salvageSlot, MERCHANT_CLICK_DELAY_MS, "merchant salvage")) {
							sendClientFeedback("Merchant: Salvage click failed, retrying.");
							continue;
						}
						if (!waitForContainerRows(6, GUI_OPEN_TIMEOUT_MS)) {
							sendClientFeedback("Merchant: salvage-all GUI did not open, retrying.");
							continue;
						}
						if (merchantAbortIfBlacklistedNpcPresent("merchant salvage-all menu")) {
							return;
						}
						merchantNotifyWebhookTargetsIfPresent("merchant salvage-all menu", merchantWebhookNotifiedKeys);

						int protectedCount = countMerchantProtectedItemsInInventory();
						if (protectedCount > 0) {
							if (!stashMerchantProtectedItems()) {
								return;
							}
							sendClientFeedback("Merchant: protected NPC moved to /" + MERCHANT_STORE_COMMAND + ".");
							if (!sleepQuietly(MERCHANT_REOPEN_AFTER_STASH_DELAY_MS)) {
								return;
							}
							if (!runMerchantFromSecondClickAfterStash(merchantWebhookNotifiedKeys)) {
								if (MERCHANT_BLACKLIST_HARD_STOP_REQUESTED.get()) {
									return;
								}
								sendClientFeedback("Merchant: resume failed, retrying full routine.");
								continue;
							}
						} else {
							if (!sleepQuietly(MERCHANT_SALVAGE_TO_ALL_GAP_MS)) {
								return;
							}
							if (!merchantClickSalvageAllWithRetry("merchant salvage all")) {
								sendClientFeedback("Merchant: could not click Salvage All, retrying full routine.");
								continue;
							}
							merchantNotifyWebhookTargetsIfPresent("merchant after salvage all", merchantWebhookNotifiedKeys);
							if (!stashMerchantProtectedItemsIfPresent("Merchant: protected NPC matched list after buy and moved to /")) {
								return;
							}
						}
					}

					if (barrierWallAfterFirstClick) {
						sendClientFeedback("Merchant: first-click barrier scan is full. Current run finished, remaining repeats skipped.");
					}
					completedCycle = true;
					break merchantAttempt;
				} finally {
					closeCurrentHandledScreen();
				}
			}

			if (!completedCycle) {
				sendClientFeedback("Merchant cancelled: too many failures (GUI not ready / missing buttons).");
				return;
			}

			if (stopRemainingRepeats) {
				return;
			}
			if (cycle < MERCHANT_REPEAT_COUNT - 1 && !sleepQuietly(MERCHANT_REPEAT_DELAY_MS)) {
				return;
			}
		}
	}

	private static boolean runMerchantFromSecondClickAfterStash(List<String> merchantWebhookNotifiedKeys) {
		final int attempts = 3;
		for (int attempt = 1; attempt <= attempts; attempt++) {
			if (MERCHANT_BLACKLIST_HARD_STOP_REQUESTED.get()) {
				return false;
			}
			if (attempt > 1 && !sleepQuietly(MERCHANT_FLOW_RESTART_DELAY_MS)) {
				return false;
			}
			if (!openContainerWithCommand("merchant", 6, "merchant resume")) {
				if (attempt < attempts) {
					continue;
				}
				return false;
			}

			try {
				// Give the GUI time to populate before scanning buttons (some clients are slower here).
				if (!sleepQuietly(MERCHANT_FIRST_GUI_GAP_MS)) {
					return false;
				}
				if (merchantAbortIfBlacklistedNpcPresent("merchant resume first GUI")) {
					return false;
				}

				Integer minionForgeSlot = findFirstTopContainerSlotMatching(6, stack -> stackNameOrBlobContains(stack, "minion forge"));
				if (minionForgeSlot == null) {
					if (attempt < attempts) {
						sendClientFeedback("Merchant resume: could not find Minion Forge, retrying.");
						continue;
					}
					sendClientFeedback("Merchant resume cancelled: could not find Minion Forge.");
					return false;
				}
				if (!clickSingleSlot(minionForgeSlot, MERCHANT_CLICK_DELAY_MS, "merchant resume minion forge")) {
					if (attempt < attempts) {
						sendClientFeedback("Merchant resume: Minion Forge click failed, retrying.");
						continue;
					}
					return false;
				}
				if (!waitForContainerRows(4, GUI_OPEN_TIMEOUT_MS)) {
					if (attempt < attempts) {
						sendClientFeedback("Merchant resume: salvage GUI did not open, retrying.");
						continue;
					}
					sendClientFeedback("Merchant resume salvage GUI did not open.");
					return false;
				}
				if (merchantAbortIfBlacklistedNpcPresent("merchant resume salvage menu")) {
					return false;
				}

				// Give the salvage GUI time to populate before scanning for buttons.
				if (!sleepQuietly(MERCHANT_FIRST_GUI_GAP_MS)) {
					return false;
				}
				Integer salvageSlot = findFirstTopContainerSlotMatching(
					4,
					stack -> stackNameOrBlobContains(stack, "salvage") && !stackNameOrBlobContains(stack, "salvage all")
				);
				if (salvageSlot == null) {
					if (attempt < attempts) {
						sendClientFeedback("Merchant resume: could not find Salvage, retrying.");
						continue;
					}
					sendClientFeedback("Merchant resume cancelled: could not find Salvage.");
					return false;
				}
				if (!clickSingleSlot(salvageSlot, MERCHANT_CLICK_DELAY_MS, "merchant resume salvage")) {
					if (attempt < attempts) {
						sendClientFeedback("Merchant resume: Salvage click failed, retrying.");
						continue;
					}
					return false;
				}
				if (!waitForContainerRows(6, GUI_OPEN_TIMEOUT_MS)) {
					if (attempt < attempts) {
						sendClientFeedback("Merchant resume: salvage-all GUI did not open, retrying.");
						continue;
					}
					sendClientFeedback("Merchant resume salvage-all GUI did not open.");
					return false;
				}
				if (merchantAbortIfBlacklistedNpcPresent("merchant resume salvage-all menu")) {
					return false;
				}
				merchantNotifyWebhookTargetsIfPresent("merchant resume salvage-all menu", merchantWebhookNotifiedKeys);

				int protectedCount = countMerchantProtectedItemsInInventory();
				if (protectedCount > 0) {
					if (!stashMerchantProtectedItems()) {
						return false;
					}
					if (!sleepQuietly(MERCHANT_REOPEN_AFTER_STASH_DELAY_MS)) {
						return false;
					}
					if (attempt < 3) {
						sendClientFeedback("Merchant: protected NPC found again, retrying resume from step 2.");
						continue;
					}
					sendClientFeedback("Merchant: protected NPC keeps appearing, stopping resume for safety.");
					return false;
				}

				if (!sleepQuietly(MERCHANT_SALVAGE_TO_ALL_GAP_MS)) {
					return false;
				}
				if (!merchantClickSalvageAllWithRetry("merchant resume salvage all")) {
					if (attempt < attempts) {
						sendClientFeedback("Merchant resume: could not click Salvage All, retrying.");
						continue;
					}
					return false;
				}
				merchantNotifyWebhookTargetsIfPresent("merchant resume after salvage all", merchantWebhookNotifiedKeys);
				return stashMerchantProtectedItemsIfPresent("Merchant: protected NPC matched list after buy and moved to /");
			} finally {
				closeCurrentHandledScreen();
			}
		}

		return false;
	}

	private static boolean merchantClickSalvageAllWithRetry(String routineName) {
		final int attempts = 3;
		for (int attempt = 1; attempt <= attempts; attempt++) {
			Integer salvageAllSlot = findFirstTopContainerSlotMatching(6, stack -> stackNameOrBlobContains(stack, "salvage all"));
			if (salvageAllSlot != null) {
				if (clickSingleSlot(salvageAllSlot, MERCHANT_CLICK_DELAY_MS, routineName, true)) {
					return true;
				}
			}

			// Retry last click (best-effort): in some cases the GUI isn't ready yet.
			if (attempt < attempts) {
				if (!sleepQuietly(250L)) {
					return false;
				}
				continue;
			}
		}
		return false;
	}

	private static boolean stashMerchantProtectedItems() {
		String storeCommand = normalizeStoreCommand(MERCHANT_STORE_COMMAND);
		if (storeCommand.isBlank()) {
			sendClientFeedback("Merchant: no store command configured. Use /sales merchant store <command>.");
			return false;
		}

		for (int attempt = 1; attempt <= MERCHANT_SPECIAL_STASH_ATTEMPTS; attempt++) {
			int specialCount = countMerchantProtectedItemsInInventory();
			if (specialCount <= 0) {
				return true;
			}

			sendClientFeedback("Merchant: found " + specialCount + " [SECRET]/[EXCLUSIVE], moving to /" + storeCommand + "...");
			closeCurrentHandledScreen();
			long preOpenDelayMs = Math.max(0L, AUTO_STORE_DELAY_MS);
			if (preOpenDelayMs > 0L && !sleepQuietly(preOpenDelayMs)) {
				return false;
			}
			if (!openContainerWithCommand(storeCommand, 6, "merchant /" + storeCommand)) {
				return false;
			}

			try {
				if (!quickMovePlayerItemsMatching(
					6,
					SalesClientMod::isMerchantProtectedItem,
					"merchant stash protected",
					MERCHANT_CLICK_DELAY_MS
				)) {
					return false;
				}
			} finally {
				closeCurrentHandledScreen();
			}

			if (!waitForMerchantProtectedItemsCleared()) {
				if (attempt < MERCHANT_SPECIAL_STASH_ATTEMPTS) {
					sendClientFeedback("Merchant: protected items still in inventory, retrying stash...");
					if (!sleepQuietly(MERCHANT_SPECIAL_VERIFY_DELAY_MS)) {
						return false;
					}
					continue;
				}

				sendClientFeedback("Merchant: protected items are still in inventory. Stopping to avoid salvage loss.");
				return false;
			}
		}

		return true;
	}

	private static boolean stashMerchantProtectedItemsIfPresent(String messagePrefix) {
		if (countMerchantProtectedItemsInInventory() <= 0) {
			return true;
		}
		if (!stashMerchantProtectedItems()) {
			return false;
		}
		sendClientFeedback(messagePrefix + MERCHANT_STORE_COMMAND + ".");
		return true;
	}

	private static boolean waitForMerchantProtectedItemsCleared() {
		for (int i = 0; i < MERCHANT_SPECIAL_VERIFY_ATTEMPTS; i++) {
			if (countMerchantProtectedItemsInInventory() == 0) {
				return true;
			}
			if (!sleepQuietly(MERCHANT_SPECIAL_VERIFY_DELAY_MS)) {
				return false;
			}
		}
		return countMerchantProtectedItemsInInventory() == 0;
	}

	private static boolean merchantAbortIfBlacklistedNpcPresent(String stage) {
		if (MERCHANT_BLACKLIST_NAME_PARTS.isEmpty()) {
			return false;
		}
		String matchedName = findMerchantBlacklistedNpcNameInOpenContainerAnyRows();
		if (matchedName == null) {
			return false;
		}
		if (MERCHANT_BLACKLIST_HARD_STOP_REQUESTED.compareAndSet(false, true)) {
			sendClientFeedback("Merchant stopped: blacklisted NPC detected (" + matchedName + ") in " + stage + ".");
			sendClientFeedback("Merchant stopped: remaining iterations cancelled.");
		}
		return true;
	}

	private static String findMerchantBlacklistedNpcNameInOpenContainerAnyRows() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int topSlots = topSlotsForHandler(handler);
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (isMerchantBlacklistedNpcStack(stack)) {
					return stack.getName().getString();
				}
			}
			return null;
		}, null);
	}

	private static boolean isMerchantBlacklistedNpcStack(ItemStack stack) {
		if (stack == null || stack.isEmpty() || MERCHANT_BLACKLIST_NAME_PARTS.isEmpty()) {
			return false;
		}

		String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
		String fastBlob = buildStackFastParseBlob(stack).toLowerCase(Locale.ROOT);
		for (String namePart : MERCHANT_BLACKLIST_NAME_PARTS) {
			if (displayName.contains(namePart) || fastBlob.contains(namePart)) {
				return true;
			}
		}
		return false;
	}

	private static void merchantNotifyWebhookTargetsIfPresent(String stage, List<String> notifiedKeysLower) {
		if (MERCHANT_WEBHOOK_NOTIFY_NAME_PARTS.isEmpty() || notifiedKeysLower == null) {
			return;
		}
		if (!hasWebhookUrlConfigured()) {
			return;
		}

		List<MerchantWebhookTarget> matchedTargets = findMerchantWebhookTargetsInInventory();
		for (MerchantWebhookTarget matchedTarget : matchedTargets) {
			String key = matchedTarget.dedupeKeyLower;
			if (notifiedKeysLower.contains(key)) {
				continue;
			}
			notifiedKeysLower.add(key);
			scheduleMerchantWebhookNotifyBurst(matchedTarget.displayName, matchedTarget.typeDisplay, stage);
		}
	}

	private static List<MerchantWebhookTarget> findMerchantWebhookTargetsInInventory() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || MERCHANT_WEBHOOK_NOTIFY_NAME_PARTS.isEmpty()) {
				return List.of();
			}

			List<MerchantWebhookTarget> matches = new ArrayList<>();
			List<String> seenLower = new ArrayList<>();
			for (int slot = 0; slot < PLAYER_INVENTORY_SLOTS; slot++) {
				ItemStack stack = client.player.getInventory().getStack(slot);
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				String displayName = stack.getName().getString();
				String displayLower = displayName.toLowerCase(Locale.ROOT);
				String fastBlob = buildStackFastParseBlob(stack).toLowerCase(Locale.ROOT);
				for (String namePart : MERCHANT_WEBHOOK_NOTIFY_NAME_PARTS) {
					if (!displayLower.contains(namePart) && !fastBlob.contains(namePart)) {
						continue;
					}
					String resolvedName = displayName == null ? "" : displayName.trim();
					if (resolvedName.isEmpty()) {
						resolvedName = namePart;
					}
					String typeDisplay = extractMerchantTypeFromStack(stack);
					String key = (resolvedName + "|" + typeDisplay).toLowerCase(Locale.ROOT);
					if (!seenLower.contains(key)) {
						seenLower.add(key);
						matches.add(new MerchantWebhookTarget(key, resolvedName, typeDisplay));
					}
					break;
				}
			}
			return List.copyOf(matches);
		}, List.of());
	}

	private static String extractMerchantTypeFromStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}

		String fastBlob = buildStackFastParseBlob(stack);
		Matcher fastMatcher = MERCHANT_TYPE_LINE_PATTERN.matcher(fastBlob);
		if (fastMatcher.find()) {
			return normalizeMerchantTypeDisplay(fastMatcher.group(1));
		}

		String tooltipBlob = buildStackParseBlob(stack);
		Matcher tooltipMatcher = MERCHANT_TYPE_LINE_PATTERN.matcher(tooltipBlob);
		if (tooltipMatcher.find()) {
			return normalizeMerchantTypeDisplay(tooltipMatcher.group(1));
		}
		return "";
	}

	private static String normalizeMerchantTypeDisplay(String rawType) {
		if (rawType == null || rawType.isBlank()) {
			return "";
		}
		String value = rawType.trim();
		if (value.startsWith("[") && value.endsWith("]") && value.length() >= 2) {
			value = value.substring(1, value.length() - 1).trim();
		}
		if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
			if (value.length() >= 2) {
				value = value.substring(1, value.length() - 1).trim();
			}
		}
		return value;
	}

	private static void scheduleMerchantWebhookNotifyBurst(String matchedName, String typeDisplay, String stage) {
		String resolvedName = matchedName == null || matchedName.isBlank() ? "unknown" : matchedName.trim();
		String resolvedType = typeDisplay == null ? "" : typeDisplay.trim();
		String resolvedStage = stage == null ? "" : stage.trim();
		try {
			WEBHOOK_SCHEDULER.execute(() -> sendMerchantWebhookNotifyBurst(resolvedName, resolvedType, resolvedStage));
		} catch (Exception exception) {
			LOGGER.warn("Failed to schedule merchant webhook notify burst", exception);
		}
	}

	private static void sendMerchantWebhookNotifyBurst(String matchedName, String typeDisplay, String stage) {
		boolean pingEnabled = MERCHANT_WEBHOOK_NOTIFY_PING_ENABLED.get();
		int pingCount = pingEnabled ? Math.max(1, MERCHANT_WEBHOOK_NOTIFY_PING_COUNT) : 1;
		String suffix = stage.isBlank() ? "" : " (" + stage + ")";
		String typeSuffix = typeDisplay == null || typeDisplay.isBlank() ? "" : " | Type: " + typeDisplay;
		String message =
			(pingEnabled ? "@everyone " : "")
				+ "[Sales] Merchant webhook target found: "
				+ matchedName
				+ typeSuffix
				+ suffix;
		for (int i = 0; i < pingCount; i++) {
			sendWebhookPing(message);
			if (i < pingCount - 1 && !sleepQuietly(WEBHOOK_DELAY_MS)) {
				return;
			}
		}
	}

	private static final class MerchantWebhookTarget {
		private final String dedupeKeyLower;
		private final String displayName;
		private final String typeDisplay;

		private MerchantWebhookTarget(String dedupeKeyLower, String displayName, String typeDisplay) {
			this.dedupeKeyLower = dedupeKeyLower == null ? "" : dedupeKeyLower;
			this.displayName = displayName == null ? "" : displayName;
			this.typeDisplay = typeDisplay == null ? "" : typeDisplay;
		}
	}

	private static int countMerchantProtectedItemsInInventory() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return 0;
			}

			int count = 0;
			for (int slot = 0; slot < PLAYER_INVENTORY_SLOTS; slot++) {
				ItemStack stack = client.player.getInventory().getStack(slot);
				if (isMerchantProtectedItem(stack)) {
					count++;
				}
			}
			return count;
		}, 0);
	}

	private static boolean isMerchantProtectedItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}

		String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
		if (displayName.contains("[secret]") || displayName.contains("[exclusive]")) {
			return true;
		}

		String fastBlob = buildStackFastParseBlob(stack).toLowerCase(Locale.ROOT);
		if (fastBlob.contains("[secret]") || fastBlob.contains("[exclusive]")) {
			return true;
		}

		for (String namePart : MERCHANT_PROTECTED_NAME_PARTS) {
			if (displayName.contains(namePart) || fastBlob.contains(namePart)) {
				return true;
			}
		}
		return false;
	}

	private static int countItemInOpenContainer(int expectedRows, net.minecraft.item.Item item) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return -1;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return -1;
			}

			int containerSlots = rows * CONTAINER_COLUMNS;
			int count = 0;
			for (int slot = 0; slot < containerSlots; slot++) {
				if (handler.getSlot(slot).getStack().isOf(item)) {
					count++;
				}
			}
			return count;
		}, -1);
	}

	private static List<Integer> findTopContainerSlotsMatching(int expectedRows, Predicate<ItemStack> predicate) {
		return callOnClientThread(() -> {
			List<Integer> slots = new ArrayList<>();
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return slots;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return slots;
			}

			int topSlots = rows * CONTAINER_COLUMNS;
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isEmpty() && predicate.test(stack)) {
					slots.add(slot);
				}
			}
			return slots;
		}, List.of());
	}

	private static Integer findFirstTopContainerSlotMatching(int expectedRows, Predicate<ItemStack> predicate) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return null;
			}

			int topSlots = rows * CONTAINER_COLUMNS;
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isEmpty() && predicate.test(stack)) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static boolean stackNameOrBlobContains(ItemStack stack, String needleLower) {
		if (stack == null || stack.isEmpty() || needleLower == null || needleLower.isBlank()) {
			return false;
		}
		String name = stack.getName().getString().toLowerCase(Locale.ROOT);
		if (name.contains(needleLower)) {
			return true;
		}
		String blob = buildStackFastParseBlob(stack).toLowerCase(Locale.ROOT);
		return blob.contains(needleLower);
	}

	// Opens /buffs and clicks Yellow Stained Glass Pane upgrades (supports a single Next Page click).
	private static void runBuffsRoutine() {
		if (!openContainerWithCommand("buffs", 6, "buffs")) {
			return;
		}

		try {
			List<Integer> slots = findTopContainerSlotsMatching(
				6,
				stack -> stack.isOf(Items.YELLOW_STAINED_GLASS_PANE)
			);
			if (slots.isEmpty()) {
				Integer nextPageSlot = findFirstTopContainerSlotMatching(6, stack -> stackNameOrBlobContains(stack, "next page"));
				if (nextPageSlot == null) {
					return;
				}
				if (!clickSingleSlot(nextPageSlot, BUFFS_CLICK_DELAY_MS, "buffs next page", true)) {
					return;
				}
				// Give the GUI time to render the next page before rescanning.
				if (!sleepQuietly(EGG_PAGE_SWITCH_DELAY_MS)) {
					return;
				}
				slots = findTopContainerSlotsMatching(
					6,
					stack -> stack.isOf(Items.YELLOW_STAINED_GLASS_PANE)
				);
			}

			for (int slot : slots) {
				if (!clickSingleSlot(slot, BUFFS_CLICK_DELAY_MS, "buffs yellow pane", true)) {
					return;
				}
			}
		} finally {
			closeCurrentHandledScreen();
		}
	}

	// Sends "gg" in chat when a store purchase message appears.
	private static void runStorePurchaseRoutine() {
		long delayMs = Math.max(0L, AUTO_STORE_DELAY_MS);
		if (delayMs > 0L && !sleepQuietly(delayMs)) {
			return;
		}
		if (!sendChatMessage("gg")) {
			sendClientFeedback("store routine failed to send gg");
		}
	}

	static void runSetupSwapRoutine() {
		if (!hasAutomationAccess()) {
			return;
		}

		final Thread routineThread = Thread.currentThread();
		SETUP_SWAP_ROUTINE_THREAD.set(routineThread);
		try {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				sendClientFeedback("Setup swap cancelled.");
				return;
			}

			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.getNetworkHandler() == null) {
				sendClientFeedback("Setup swap: not in-game.");
				return;
			}
			if (!isConnectedToSalesServer(client)) {
				sendClientFeedback("Setup swap: not on " + String.join(" / ", SALES_SERVER_HOST_ALIASES) + ".");
				return;
			}
			if (hasCompassInHotbar()) {
				sendClientFeedback("Setup swap: in lobby (compass) -> abort.");
				return;
			}

			SetupSwapMode mode = SETUP_SWAP_MODE;
			int ringCount = MathHelper.clamp(SETUP_SWAP_RING_COUNT, 1, 5);
			int attachmentCount = MathHelper.clamp(SETUP_SWAP_ATTACHMENT_COUNT, 1, 6);
			boolean bossRelicsEnabled = SETUP_SWAP_BOSS_RELICS_ENABLED.get();
			int bossRelicCount = MathHelper.clamp(SETUP_SWAP_BOSS_RELIC_COUNT, 1, 6);
			long clickDelayMs = MathHelper.clamp(SETUP_SWAP_CLICK_DELAY_MS, 20L, 10_000L);
			long attachmentDelayMs = MathHelper.clamp(SETUP_SWAP_ATTACHMENT_DELAY_MS, 0L, 10_000L);
			SetupSwapArmor armor = SETUP_SWAP_ARMOR;
			String storeCommand = normalizeStoreCommand(SETUP_SWAP_STORE_COMMAND);
			String getCommand = normalizeStoreCommand(SETUP_SWAP_GET_COMMAND);

			sendClientFeedback(
				"Setup swap started: "
					+ mode.label
					+ " rings=" + ringCount
					+ " attachments=" + attachmentCount
					+ " bossRelics=" + (bossRelicsEnabled ? bossRelicCount : "off")
					+ "."
			);
			closeCurrentHandledScreen();
			if (!sleepQuietly(clickDelayMs)) {
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					sendClientFeedback("Setup swap cancelled.");
				}
				return;
			}

			if (!setupSwapUnequipRings(ringCount, clickDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: unequip rings.");
				return;
			}
			if (!setupSwapUnequipAttachments(attachmentDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: unequip attachments.");
				return;
			}
			if (bossRelicsEnabled && !setupSwapUnequipBossRelics(attachmentDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: unequip boss relics.");
				return;
			}
			if (!setupSwapStashOldItems(storeCommand, clickDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: stash old items.");
				return;
			}
			if (!setupSwapPullBestAttachments(getCommand, mode, attachmentCount, clickDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: pull attachments from /" + getCommand + ".");
				return;
			}
			if (bossRelicsEnabled && !setupSwapPullBestBossRelics(getCommand, mode, bossRelicCount, clickDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: pull boss relics from /" + getCommand + ".");
				return;
			}
			if (!setupSwapEquipAttachments(mode, attachmentCount, attachmentDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: equip attachments.");
				return;
			}
			if (bossRelicsEnabled && !setupSwapEquipBossRelics(mode, bossRelicCount, attachmentDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: equip boss relics.");
				return;
			}
			if (!setupSwapEquipRings(storeCommand, getCommand, mode, ringCount, clickDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: equip rings.");
				return;
			}
			if (!setupSwapEquipArmor(armor, clickDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: equip armor.");
				return;
			}
			if (!setupSwapPrestigeUpgrades(mode, clickDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: prestige upgrades.");
				return;
			}
			if (!setupSwapMiningLevel(mode, clickDelayMs)) {
				sendClientFeedback(SETUP_SWAP_CANCEL_REQUESTED.get()
					? "Setup swap cancelled."
					: "Setup swap failed: mining level.");
				return;
			}

			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				sendClientFeedback("Setup swap cancelled.");
			} else {
				sendClientFeedback("Setup swap done.");
			}
		} finally {
			SETUP_SWAP_ROUTINE_THREAD.compareAndSet(routineThread, null);
			SETUP_SWAP_CANCEL_REQUESTED.set(false);
			closeCurrentHandledScreen();
		}
	}

	private static boolean setupSwapUnequipRings(int ringCount, long clickDelayMs) {
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		for (int i = 0; i < ringCount; i++) {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			closeCurrentHandledScreen();
			if (!sleepQuietly(Math.max(20L, clickDelayMs))) {
				return false;
			}
			if (!openContainerWithCommandAnyRows("rings", "setup-swap rings")) {
				return false;
			}

			Integer ringSlot = findFirstTopContainerSlotMatchingAnyRows(stack ->
				isSetupSwapRingStack(stack, false));
			if (ringSlot == null) {
				closeCurrentHandledScreen();
				return true;
			}
			if (!clickAnySlot(ringSlot, 0, SlotActionType.PICKUP, clickDelayMs, "setup-swap rings unequip", true)) {
				closeCurrentHandledScreen();
				return false;
			}

			// /rings closes the GUI on click; if it doesn't, close it manually.
			if (!waitUntilNoHandledScreenSimple(GUI_OPEN_TIMEOUT_MS)) {
				closeCurrentHandledScreen();
			}
		}
		return true;
	}

	private static boolean setupSwapUnequipAttachments(long attachmentDelayMs) {
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		closeCurrentHandledScreen();
		if (!sleepQuietly(Math.max(20L, attachmentDelayMs))) {
			return false;
		}
		if (!openContainerWithCommandAnyRows("attachment", "setup-swap attachments unequip")) {
			return false;
		}

		try {
			while (true) {
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				Integer slot = findFirstTopContainerSlotMatchingAnyRows(SalesClientMod::isSetupSwapAttachmentTopStack);
				if (slot == null) {
					return true;
				}
				if (!clickAnySlot(slot, 0, SlotActionType.PICKUP, attachmentDelayMs, "setup-swap attachment unequip", true)) {
					return false;
				}
			}
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean setupSwapUnequipBossRelics(long attachmentDelayMs) {
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		closeCurrentHandledScreen();
		if (!sleepQuietly(Math.max(20L, attachmentDelayMs))) {
			return false;
		}
		if (!openContainerWithCommandAnyRows("bossrelic", "setup-swap boss relics unequip")) {
			return false;
		}

		try {
			while (true) {
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				Integer slot = findFirstTopContainerSlotMatchingAnyRows(stack -> isSetupSwapBossRelicStack(stack, null));
				if (slot == null) {
					return true;
				}
				if (!clickAnySlot(slot, 0, SlotActionType.PICKUP, attachmentDelayMs, "setup-swap boss relic unequip", true)) {
					return false;
				}
			}
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean setupSwapStashOldItems(String storeCommand, long clickDelayMs) {
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		if (storeCommand.isBlank()) {
			sendClientFeedback("Setup swap: Store PV is off/blank; skipping stash.");
			return true;
		}
		long delayMs = Math.max(20L, clickDelayMs);
		closeCurrentHandledScreen();
		if (!sleepQuietly(delayMs)) {
			return false;
		}
		if (!openContainerWithCommand(storeCommand, 6, "setup-swap store")) {
			return false;
		}

		try {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			// Method 1: QUICK_MOVE (shift-click) is fastest but isn't always honored by custom servers.
			if (!quickMovePlayerItemsMatching(
				6,
				SalesClientMod::isSetupSwapStashItem,
				"setup-swap store quick-move",
				delayMs
			)) {
				return false;
			}

			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			// Method 2: manual pickup->place fallback for anything left in inventory.
			return setupSwapMoveAllPlayerItemsMatchingToEmptyContainerSlots(
				6,
				SalesClientMod::isSetupSwapStashItem,
				delayMs,
				"setup-swap store move"
			);
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean isSetupSwapStashItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (isSetupSwapRingStack(stack, false)) {
			return true;
		}
		String blob = buildStackParseBlob(stack);
		if (isSetupSwapBossRelicBlob(blob)) {
			return parseSetupSwapBoostFromBlob(blob) >= 0D;
		}
		boolean attachmentItem = stack.isOf(Items.EMERALD) || stack.isOf(Items.DIAMOND) || stack.isOf(Items.NETHER_STAR);
		if (!attachmentItem) {
			return false;
		}
		// Avoid stashing normal materials; attachments include a Boost line in the tooltip.
		return parseSetupSwapBoostFromBlob(blob) >= 0D;
	}

	private static boolean isSetupSwapAttachmentTopStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		boolean attachmentItem = stack.isOf(Items.EMERALD) || stack.isOf(Items.DIAMOND) || stack.isOf(Items.NETHER_STAR);
		if (!attachmentItem) {
			return false;
		}
		String blob = buildStackParseBlob(stack);
		if (isSetupSwapBossRelicBlob(blob)) {
			return false;
		}
		return parseSetupSwapBoostFromBlob(blob) >= 0D;
	}

	private static boolean isSetupSwapRingStack(ItemStack stack, boolean allowUniversal) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		// /rings GUI may show empty slots as barriers; never treat those as rings.
		if (stack.isOf(Items.BARRIER)) {
			return false;
		}

		String blob = buildStackParseBlob(stack);
		String lower = blob.toLowerCase(Locale.ROOT);
		boolean hasRingMarker = SETUP_SWAP_RING_KIND_PATTERN.matcher(blob).find()
			|| lower.contains(" ring")
			|| stack.getName().getString().toLowerCase(Locale.ROOT).contains("ring");
		if (!hasRingMarker) {
			return false;
		}
		if (!allowUniversal && lower.contains("universal")) {
			return false;
		}
		// Real rings have a Boost line; placeholders generally don't.
		return parseSetupSwapBoostFromBlob(blob) >= 0D;
	}

	private static boolean setupSwapPullBestAttachments(
		String getCommand,
		SetupSwapMode mode,
		int attachmentCount,
		long clickDelayMs
	) {
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		if (getCommand.isBlank()) {
			sendClientFeedback("Setup swap: Get PV is off/blank; cannot continue.");
			return false;
		}
		if (attachmentCount <= 0) {
			return true;
		}
		closeCurrentHandledScreen();
		if (!sleepQuietly(Math.max(20L, clickDelayMs))) {
			return false;
		}
		if (!openContainerWithCommand(getCommand, 6, "setup-swap get attachments")) {
			return false;
		}

		try {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			List<Integer> bestSlots = findBestSetupSwapAttachmentSlotsInOpenContainer(6, mode);
			if (bestSlots.isEmpty()) {
				sendClientFeedback("Setup swap: no attachments found in /" + getCommand + ".");
				return false;
			}

			int moved = 0;
			for (int slot : bestSlots) {
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				if (moved >= attachmentCount) {
					break;
				}
				if (!clickAnySlot(slot, 0, SlotActionType.QUICK_MOVE, clickDelayMs, "setup-swap get attachment", true)) {
					return false;
				}
				moved++;
			}
			if (moved < attachmentCount) {
				sendClientFeedback("Setup swap: only found " + moved + "/" + attachmentCount + " attachments.");
			}
			return true;
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean setupSwapPullBestBossRelics(
		String getCommand,
		SetupSwapMode mode,
		int bossRelicCount,
		long clickDelayMs
	) {
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		if (getCommand.isBlank()) {
			sendClientFeedback("Setup swap: Get PV is off/blank; cannot continue.");
			return false;
		}
		if (bossRelicCount <= 0) {
			return true;
		}
		closeCurrentHandledScreen();
		if (!sleepQuietly(Math.max(20L, clickDelayMs))) {
			return false;
		}
		if (!openContainerWithCommand(getCommand, 6, "setup-swap get boss relics")) {
			return false;
		}

		try {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			List<Integer> bestSlots = findBestSetupSwapBossRelicSlotsInOpenContainer(6, mode);
			if (bestSlots.isEmpty()) {
				sendClientFeedback("Setup swap: no boss relics found in /" + getCommand + ".");
				return false;
			}

			int moved = 0;
			for (int slot : bestSlots) {
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				if (moved >= bossRelicCount) {
					break;
				}
				if (!clickAnySlot(slot, 0, SlotActionType.QUICK_MOVE, clickDelayMs, "setup-swap get boss relic", true)) {
					return false;
				}
				moved++;
			}
			if (moved < bossRelicCount) {
				sendClientFeedback("Setup swap: only found " + moved + "/" + bossRelicCount + " boss relics.");
			}
			return true;
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean setupSwapEquipAttachments(SetupSwapMode mode, int attachmentCount, long attachmentDelayMs) {
		if (attachmentCount <= 0) {
			return true;
		}
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		long delay = Math.max(20L, attachmentDelayMs);
		closeCurrentHandledScreen();
		if (!sleepQuietly(delay)) {
			return false;
		}
		if (!openContainerWithCommandAnyRows("attachment", "setup-swap attachments equip")) {
			return false;
		}

		try {
			for (int i = 0; i < attachmentCount; i++) {
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				if (!CACHED_IS_HANDLED_SCREEN.get()) {
					// Attachment GUI closed unexpectedly; reopen and continue.
					closeCurrentHandledScreen();
					if (!sleepQuietly(delay)) {
						return false;
					}
					if (!openContainerWithCommandAnyRows("attachment", "setup-swap attachments equip")) {
						return false;
					}
				}

				Integer targetPaneSlot = findFirstTopContainerSlotMatchingAnyRows(SalesClientMod::isSetupSwapGreenPane);
				if (targetPaneSlot == null) {
					sendClientFeedback("Setup swap: no green panes left for attachments.");
					return true;
				}

				Integer attachmentSlot = findBestSetupSwapAttachmentPlayerSlotInOpenScreen(mode);
				if (attachmentSlot == null) {
					sendClientFeedback("Setup swap: no matching attachments in inventory.");
					return true;
				}

				if (setupSwapMoveSlotToSlot(attachmentSlot, targetPaneSlot, delay, "setup-swap equip attachment")) {
					continue;
				}

				// Retry once: reopen the GUI (helps when the server/client desyncs or closes the screen mid-move).
				closeCurrentHandledScreen();
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				if (!sleepQuietly(delay)) {
					return false;
				}
				if (!openContainerWithCommandAnyRows("attachment", "setup-swap attachments equip")) {
					return false;
				}

				targetPaneSlot = findFirstTopContainerSlotMatchingAnyRows(SalesClientMod::isSetupSwapGreenPane);
				attachmentSlot = findBestSetupSwapAttachmentPlayerSlotInOpenScreen(mode);
				if (targetPaneSlot == null || attachmentSlot == null) {
					sendClientFeedback("Setup swap: attachment retry failed (slots missing).");
					return false;
				}
				if (!setupSwapMoveSlotToSlot(attachmentSlot, targetPaneSlot, delay, "setup-swap equip attachment retry")) {
					return false;
				}
			}
			return true;
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean setupSwapEquipBossRelics(SetupSwapMode mode, int bossRelicCount, long attachmentDelayMs) {
		if (bossRelicCount <= 0) {
			return true;
		}
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		long delay = Math.max(20L, attachmentDelayMs);
		closeCurrentHandledScreen();
		if (!sleepQuietly(delay)) {
			return false;
		}
		if (!openContainerWithCommandAnyRows("bossrelic", "setup-swap boss relics equip")) {
			return false;
		}

		try {
			for (int i = 0; i < bossRelicCount; i++) {
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				if (!CACHED_IS_HANDLED_SCREEN.get()) {
					closeCurrentHandledScreen();
					if (!sleepQuietly(delay)) {
						return false;
					}
					if (!openContainerWithCommandAnyRows("bossrelic", "setup-swap boss relics equip")) {
						return false;
					}
				}

				Integer targetPaneSlot = findFirstTopContainerSlotMatchingAnyRows(SalesClientMod::isSetupSwapGreenPane);
				if (targetPaneSlot == null) {
					sendClientFeedback("Setup swap: no green panes left for boss relics.");
					return true;
				}

				Integer relicSlot = findBestSetupSwapBossRelicPlayerSlotInOpenScreen(mode);
				if (relicSlot == null) {
					sendClientFeedback("Setup swap: no matching boss relics in inventory.");
					return true;
				}

				if (setupSwapMoveSlotToSlot(relicSlot, targetPaneSlot, delay, "setup-swap equip boss relic")) {
					continue;
				}

				closeCurrentHandledScreen();
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				if (!sleepQuietly(delay)) {
					return false;
				}
				if (!openContainerWithCommandAnyRows("bossrelic", "setup-swap boss relics equip")) {
					return false;
				}

				targetPaneSlot = findFirstTopContainerSlotMatchingAnyRows(SalesClientMod::isSetupSwapGreenPane);
				relicSlot = findBestSetupSwapBossRelicPlayerSlotInOpenScreen(mode);
				if (targetPaneSlot == null || relicSlot == null) {
					sendClientFeedback("Setup swap: boss relic retry failed (slots missing).");
					return false;
				}
				if (!setupSwapMoveSlotToSlot(relicSlot, targetPaneSlot, delay, "setup-swap equip boss relic retry")) {
					return false;
				}
			}
			return true;
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean isSetupSwapGreenPane(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.isOf(Items.GREEN_STAINED_GLASS_PANE) || stack.isOf(Items.LIME_STAINED_GLASS_PANE);
	}

	private static boolean setupSwapEquipRings(
		String storeCommand,
		String getCommand,
		SetupSwapMode mode,
		int ringCount,
		long clickDelayMs
	) {
		if (ringCount <= 0) {
			return true;
		}
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		if (getCommand.isBlank()) {
			sendClientFeedback("Setup swap: Get PV is off/blank; cannot equip rings.");
			return false;
		}

		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		if (!setupSwapEnsureRingsHotbarSlotsReady(storeCommand, getCommand, clickDelayMs)) {
			return false;
		}

		int remaining = ringCount;
		int[] hotbarSlots = new int[] {3, 4, 5, 6};
		while (remaining > 0) {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			closeCurrentHandledScreen();
			if (!sleepQuietly(Math.max(20L, clickDelayMs))) {
				return false;
			}
			if (!openContainerWithCommand(getCommand, 6, "setup-swap get rings")) {
				return false;
			}

			List<Integer> usedHotbar = new ArrayList<>();
			try {
				for (int hotbarSlot : hotbarSlots) {
					if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
						return false;
					}
					if (remaining <= 0) {
						break;
					}

					Integer ringSlot = findBestSetupSwapRingSlotInOpenContainer(6, mode);
					if (ringSlot == null) {
						break;
					}

					// Some servers block SWAP (number key) in /pv. Prefer a normal pickup->place into the hotbar slot.
					int targetHotbarSlot = toPlayerHotbarSlotIndexInSixRowContainer(hotbarSlot);
					boolean moved = setupSwapMoveSlotToSlot(ringSlot, targetHotbarSlot, clickDelayMs, "setup-swap ring to hotbar")
						&& waitForPlayerHotbarSlotNotEmpty(hotbarSlot, 1000L);
					if (!moved) {
						// Best-effort fallback: SWAP.
						moved = clickAnySlot(ringSlot, hotbarSlot, SlotActionType.SWAP, clickDelayMs, "setup-swap ring swap", true)
							&& waitForPlayerHotbarSlotNotEmpty(hotbarSlot, 1000L);
					}
					if (!moved) {
						return false;
					}
					usedHotbar.add(hotbarSlot);
					remaining--;
				}
			} finally {
				closeCurrentHandledScreen();
			}

			if (usedHotbar.isEmpty()) {
				sendClientFeedback("Setup swap: no rings found in /" + getCommand + ".");
				return false;
			}

			for (int hotbarSlot : usedHotbar) {
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				if (!sleepQuietly(Math.max(20L, clickDelayMs))) {
					return false;
				}
				if (!useHotbarItem(hotbarSlot)) {
					sendClientFeedback("Setup swap: failed to use ring on hotbar slot " + hotbarSlot + ".");
					return false;
				}
				if (!sleepQuietly(Math.max(20L, clickDelayMs))) {
					return false;
				}
				closeCurrentHandledScreen();
			}

			if (!setupSwapAreHotbarSlotsEmpty()) {
				sendClientFeedback("Setup swap: ring equip did not clear hotbar slots 3-6. Aborting.");
				return false;
			}
		}
		return true;
	}

	private static boolean waitForPlayerHotbarSlotNotEmpty(int hotbarSlot, long timeoutMs) {
		long deadline = System.currentTimeMillis() + Math.max(50L, timeoutMs);
		while (System.currentTimeMillis() < deadline) {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			Boolean hasItem = callOnClientThread(() -> {
				MinecraftClient client = MinecraftClient.getInstance();
				return client.player != null && !client.player.getInventory().getStack(hotbarSlot).isEmpty();
			}, false);
			if (Boolean.TRUE.equals(hasItem)) {
				return true;
			}
			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}
		return false;
	}

	private static int toPlayerHotbarSlotIndexInSixRowContainer(int hotbarSlot) {
		// GenericContainerScreenHandler layout: [top 6x9] + [player main 27] + [hotbar 9]
		// Hotbar indices are 0..8 (same indices used by SlotActionType.SWAP "button").
		return 6 * CONTAINER_COLUMNS + (PLAYER_INVENTORY_SLOTS - 9) + hotbarSlot;
	}

	private static boolean setupSwapAreHotbarSlotsEmpty() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return false;
			}
			for (int slot = 3; slot <= 6; slot++) {
				if (!client.player.getInventory().getStack(slot).isEmpty()) {
					return false;
				}
			}
			return true;
		}, false);
	}

	private static boolean setupSwapEnsureRingsHotbarSlotsReady(String storeCommand, String getCommand, long clickDelayMs) {
		if (setupSwapAreHotbarSlotsEmpty()) {
			return true;
		}
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}

		long delayMs = Math.max(20L, clickDelayMs);
		String normalizedStore = normalizeStoreCommand(storeCommand);

		// Preferred: clear into Store PV so we don't rely on free inventory space.
		if (!normalizedStore.isBlank()) {
			closeCurrentHandledScreen();
			if (!sleepQuietly(delayMs)) {
				return false;
			}
			if (!openContainerWithCommand(normalizedStore, 6, "setup-swap clear hotbar")) {
				return false;
			}
			try {
				for (int hotbarSlot = 3; hotbarSlot <= 6; hotbarSlot++) {
					if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
						return false;
					}
					final int slotIndex = hotbarSlot;
					boolean empty = callOnClientThread(() -> {
						MinecraftClient client = MinecraftClient.getInstance();
						return client.player != null && client.player.getInventory().getStack(slotIndex).isEmpty();
					}, true);
					if (empty) {
						continue;
					}

					Integer emptyContainerSlot = findFirstEmptyTopContainerSlotInOpenScreen(6);
					if (emptyContainerSlot == null) {
						sendClientFeedback("Setup swap: Store PV is full; cannot clear hotbar slots 3-6.");
						return false;
					}

					int fromSlot = toPlayerHotbarSlotIndexInSixRowContainer(slotIndex);
					if (!setupSwapMoveSlotToSlot(fromSlot, emptyContainerSlot, delayMs, "setup-swap clear hotbar")) {
						return false;
					}
				}
			} finally {
				closeCurrentHandledScreen();
			}

			if (!setupSwapAreHotbarSlotsEmpty()) {
				sendClientFeedback("Setup swap: failed to clear hotbar slots 3-6.");
				return false;
			}
			return true;
		}

		// Fallback: no Store PV -> try to move items into empty player main inventory slots.
		closeCurrentHandledScreen();
		if (!sleepQuietly(delayMs)) {
			return false;
		}
		if (!openContainerWithCommand(getCommand, 6, "setup-swap clear hotbar")) {
			return false;
		}
		try {
			for (int hotbarSlot = 3; hotbarSlot <= 6; hotbarSlot++) {
				if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
					return false;
				}
				final int slotIndex = hotbarSlot;
				boolean empty = callOnClientThread(() -> {
					MinecraftClient client = MinecraftClient.getInstance();
					return client.player != null && client.player.getInventory().getStack(slotIndex).isEmpty();
				}, true);
				if (empty) {
					continue;
				}

				Integer emptyMainSlot = findFirstEmptyPlayerMainInventorySlotInOpenScreen(6);
				if (emptyMainSlot == null) {
					sendClientFeedback("Setup swap: inventory full; cannot clear hotbar slots 3-6 (enable Store PV).");
					return false;
				}

				int fromSlot = toPlayerHotbarSlotIndexInSixRowContainer(slotIndex);
				if (!setupSwapMoveSlotToSlot(fromSlot, emptyMainSlot, delayMs, "setup-swap clear hotbar")) {
					return false;
				}
			}
		} finally {
			closeCurrentHandledScreen();
		}

		if (!setupSwapAreHotbarSlotsEmpty()) {
			sendClientFeedback("Setup swap: failed to clear hotbar slots 3-6.");
			return false;
		}
		return true;
	}

	private static Integer findFirstEmptyPlayerMainInventorySlotInOpenScreen(int expectedRows) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return null;
			}
			int topSlots = rows * CONTAINER_COLUMNS;
			int mainSlots = PLAYER_INVENTORY_SLOTS - 9;
			int max = Math.min(handler.slots.size(), topSlots + mainSlots);
			for (int slot = topSlots; slot < max; slot++) {
				if (handler.getSlot(slot).getStack().isEmpty()) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static boolean setupSwapEquipArmor(SetupSwapArmor armor, long clickDelayMs) {
		if (armor == null || armor == SetupSwapArmor.OFF || armor.matchLower.isBlank()) {
			return true;
		}
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}

		closeCurrentHandledScreen();
		if (!sleepQuietly(Math.max(20L, clickDelayMs))) {
			return false;
		}
		if (!openContainerWithCommandAnyRows("armor", "setup-swap armor")) {
			return false;
		}

		try {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			int before = CACHED_OPEN_SCREEN_SYNC_ID.get();
			Integer armorSlot = findFirstTopContainerSlotMatchingAnyRows(stack -> stackNameOrBlobContains(stack, armor.matchLower));
			if (armorSlot == null) {
				sendClientFeedback("Setup swap: armor not found: " + armor.label);
				return false;
			}
			if (!clickAnySlot(armorSlot, 0, SlotActionType.PICKUP, clickDelayMs, "setup-swap armor select", true)) {
				return false;
			}
			waitForHandledScreenSyncIdChange(before, GUI_OPEN_TIMEOUT_MS);

			Integer confirmSlot = findFirstTopContainerSlotMatchingAnyRows(stack ->
				stack.isOf(Items.GREEN_DYE) || stack.isOf(Items.LIME_DYE) || stackNameOrBlobContains(stack, "green dye"));
			if (confirmSlot != null) {
				clickAnySlot(confirmSlot, 0, SlotActionType.PICKUP, clickDelayMs, "setup-swap armor confirm", true);
			}
			return true;
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean setupSwapPrestigeUpgrades(SetupSwapMode mode, long clickDelayMs) {
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		closeCurrentHandledScreen();
		if (!sleepQuietly(Math.max(20L, clickDelayMs))) {
			return false;
		}
		if (!openContainerWithCommandAnyRows("prestige", "setup-swap prestige")) {
			return false;
		}

		try {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			int firstSync = CACHED_OPEN_SCREEN_SYNC_ID.get();
			Integer upgradesSlot = findFirstTopContainerSlotMatchingAnyRows(stack -> stackNameOrBlobContains(stack, "upgrades"));
			if (upgradesSlot == null) {
				sendClientFeedback("Setup swap: prestige upgrades not found.");
				return false;
			}
			if (!clickAnySlot(upgradesSlot, 0, SlotActionType.PICKUP, clickDelayMs, "setup-swap prestige upgrades", true)) {
				return false;
			}
			waitForHandledScreenSyncIdChange(firstSync, GUI_OPEN_TIMEOUT_MS);

			int secondSync = CACHED_OPEN_SCREEN_SYNC_ID.get();
			Integer tntMinecartSlot = findFirstTopContainerSlotMatchingAnyRows(stack -> stack.isOf(Items.TNT_MINECART));
			if (tntMinecartSlot == null) {
				sendClientFeedback("Setup swap: prestige TNT minecart not found.");
				return false;
			}
			if (!clickAnySlot(tntMinecartSlot, 0, SlotActionType.PICKUP, clickDelayMs, "setup-swap prestige tnt", true)) {
				return false;
			}
			waitForHandledScreenSyncIdChange(secondSync, GUI_OPEN_TIMEOUT_MS);

			Item main = setupSwapUpgradeItemForMode(mode);
			Integer mainSlot = findFirstTopContainerSlotMatchingAnyRows(stack -> stack.isOf(main));
			if (mainSlot == null) {
				sendClientFeedback("Setup swap: prestige item not found (" + mode.label + ").");
				return false;
			}
			if (!clickAnySlot(mainSlot, 0, SlotActionType.QUICK_MOVE, clickDelayMs, "setup-swap prestige shift main", true)) {
				return false;
			}

			Integer gApple = findFirstTopContainerSlotMatchingAnyRows(stack -> stack.isOf(Items.GOLDEN_APPLE));
			if (gApple != null) {
				if (!clickAnySlot(gApple, 0, SlotActionType.QUICK_MOVE, clickDelayMs, "setup-swap prestige shift apple", true)) {
					return false;
				}
			}

			Integer chest = findFirstTopContainerSlotMatchingAnyRows(stack -> stack.isOf(Items.CHEST));
			if (chest != null) {
				clickAnySlot(chest, 0, SlotActionType.QUICK_MOVE, clickDelayMs, "setup-swap prestige shift chest", true);
			}
			return true;
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean setupSwapMiningLevel(SetupSwapMode mode, long clickDelayMs) {
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		closeCurrentHandledScreen();
		if (!sleepQuietly(Math.max(20L, clickDelayMs))) {
			return false;
		}
		if (!openContainerWithCommandAnyRows("mininglevel", "setup-swap mininglevel")) {
			return false;
		}

		try {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			int firstSync = CACHED_OPEN_SCREEN_SYNC_ID.get();
			Integer netherStarSlot = findFirstTopContainerSlotMatchingAnyRows(stack -> stack.isOf(Items.NETHER_STAR));
			if (netherStarSlot == null) {
				sendClientFeedback("Setup swap: /mininglevel nether star not found.");
				return false;
			}
			if (!clickAnySlot(netherStarSlot, 0, SlotActionType.PICKUP, clickDelayMs, "setup-swap mininglevel star", true)) {
				return false;
			}
			waitForHandledScreenSyncIdChange(firstSync, GUI_OPEN_TIMEOUT_MS);

			int secondSync = CACHED_OPEN_SCREEN_SYNC_ID.get();
			Integer tntMinecartSlot = findFirstTopContainerSlotMatchingAnyRows(stack -> stack.isOf(Items.TNT_MINECART));
			if (tntMinecartSlot == null) {
				sendClientFeedback("Setup swap: mininglevel TNT minecart not found.");
				return false;
			}
			if (!clickAnySlot(tntMinecartSlot, 0, SlotActionType.PICKUP, clickDelayMs, "setup-swap mininglevel tnt", true)) {
				return false;
			}
			waitForHandledScreenSyncIdChange(secondSync, GUI_OPEN_TIMEOUT_MS);

			Item main = setupSwapUpgradeItemForMode(mode);
			Integer mainSlot = findFirstTopContainerSlotMatchingAnyRows(stack -> stack.isOf(main));
			if (mainSlot == null) {
				sendClientFeedback("Setup swap: mininglevel item not found (" + mode.label + ").");
				return false;
			}
			return clickAnySlot(mainSlot, 0, SlotActionType.QUICK_MOVE, clickDelayMs, "setup-swap mininglevel shift main", true);
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static Item setupSwapUpgradeItemForMode(SetupSwapMode mode) {
		if (mode == SetupSwapMode.STARS) {
			return Items.NETHER_STAR;
		}
		if (mode == SetupSwapMode.GEM) {
			// Never diamond here.
			return Items.LAPIS_LAZULI;
		}
		return Items.EMERALD;
	}

	private static boolean waitForHandledScreenSyncIdChange(int previousSyncId, long timeoutMs) {
		if (previousSyncId < 0) {
			return true;
		}
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			if (!CACHED_IS_HANDLED_SCREEN.get()) {
				// Screen closed; treat as "changed".
				return true;
			}
			int now = CACHED_OPEN_SCREEN_SYNC_ID.get();
			if (now >= 0 && now != previousSyncId) {
				return true;
			}
			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}
		return false;
	}

	private static boolean waitUntilNoHandledScreenSimple(long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			if (!CACHED_IS_HANDLED_SCREEN.get()) {
				return true;
			}
			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}
		return !CACHED_IS_HANDLED_SCREEN.get();
	}

	private static boolean setupSwapMoveSlotToSlot(int fromSlot, int toSlot, long delayMs, String routineName) {
		if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
			return false;
		}
		long delay = Math.max(20L, delayMs);
		if (!clickAnySlot(fromSlot, 0, SlotActionType.PICKUP, delay, routineName + " pickup", true)) {
			return false;
		}
		if (!clickAnySlot(toSlot, 0, SlotActionType.PICKUP, delay, routineName + " place", true)) {
			return false;
		}

		int cursorCount = getCursorStackCount();
		if (cursorCount == 0) {
			return true;
		}
		if (cursorCount < 0) {
			return false;
		}

		// Best-effort: place back to avoid leaving an item stuck on the cursor.
		clickAnySlot(fromSlot, 0, SlotActionType.PICKUP, delay, routineName + " revert", true);
		return false;
	}

	private static boolean setupSwapMoveAllPlayerItemsMatchingToEmptyContainerSlots(
		int expectedRows,
		Predicate<ItemStack> predicate,
		long delayMs,
		String routineName
	) {
		int moves = 0;
		int maxMoves = 128;
		while (moves < maxMoves) {
			if (SETUP_SWAP_CANCEL_REQUESTED.get()) {
				return false;
			}
			Integer fromSlot = findFirstPlayerSlotMatchingInOpenScreen(expectedRows, predicate);
			if (fromSlot == null) {
				return true;
			}

			Integer emptyContainerSlot = findFirstEmptyTopContainerSlotInOpenScreen(expectedRows);
			if (emptyContainerSlot == null) {
				sendClientFeedback("Setup swap: store PV is full (no empty slots).");
				return false;
			}

			if (!setupSwapMoveSlotToSlot(fromSlot, emptyContainerSlot, delayMs, routineName)) {
				return false;
			}
			moves++;
		}

		sendClientFeedback("Setup swap: store move aborted (too many items).");
		return false;
	}

	private static Integer findFirstEmptyTopContainerSlotInOpenScreen(int expectedRows) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return null;
			}
			int topSlots = rows * CONTAINER_COLUMNS;
			for (int slot = 0; slot < topSlots; slot++) {
				if (handler.getSlot(slot).getStack().isEmpty()) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static Integer findFirstPlayerSlotMatchingInOpenScreen(int expectedRows, Predicate<ItemStack> predicate) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return null;
			}

			int topSlots = rows * CONTAINER_COLUMNS;
			int max = Math.min(handler.slots.size(), topSlots + PLAYER_INVENTORY_SLOTS);
			for (int slot = topSlots; slot < max; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isEmpty() && predicate.test(stack)) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static SetupSwapMode parseSetupSwapTypeFromBlob(String blob) {
		if (blob == null || blob.isBlank()) {
			return null;
		}
		Matcher matcher = SETUP_SWAP_TYPE_PATTERN.matcher(blob);
		if (matcher.find()) {
			String raw = matcher.group(1);
			if (raw != null) {
				return switch (raw.trim().toLowerCase(Locale.ROOT)) {
					case "gem" -> SetupSwapMode.GEM;
					case "stars" -> SetupSwapMode.STARS;
					case "money", "cash" -> SetupSwapMode.MONEY;
					default -> null;
				};
			}
		}

		// Fallback: sometimes the type exists but isn't prefixed with "Type:".
		// Use whole-word matches to avoid false positives (e.g. "Nether Star").
		if (SETUP_SWAP_WORD_MONEY_PATTERN.matcher(blob).find()) {
			return SetupSwapMode.MONEY;
		}
		if (SETUP_SWAP_WORD_GEM_PATTERN.matcher(blob).find()) {
			return SetupSwapMode.GEM;
		}
		if (SETUP_SWAP_WORD_STARS_PATTERN.matcher(blob).find()) {
			return SetupSwapMode.STARS;
		}

		return null;
	}

	private static SetupSwapMode parseSetupSwapBossRelicTypeFromBlob(String blob) {
		if (blob == null || blob.isBlank()) {
			return null;
		}
		SetupSwapMode parsed = parseSetupSwapTypeFromBlob(blob);
		if (parsed != null) {
			return parsed;
		}
		String lower = blob.toLowerCase(Locale.ROOT);
		if (lower.contains("money [boss relic]") || lower.contains("[boss relic] money")) {
			return SetupSwapMode.MONEY;
		}
		if (lower.contains("gem [boss relic]") || lower.contains("[boss relic] gem")) {
			return SetupSwapMode.GEM;
		}
		if (lower.contains("star [boss relic]")
			|| lower.contains("stars [boss relic]")
			|| lower.contains("[boss relic] star")
			|| lower.contains("[boss relic] stars")) {
			return SetupSwapMode.STARS;
		}
		if (!isSetupSwapBossRelicBlob(blob)) {
			return null;
		}
		if (SETUP_SWAP_WORD_MONEY_PATTERN.matcher(blob).find()) {
			return SetupSwapMode.MONEY;
		}
		if (SETUP_SWAP_WORD_GEM_PATTERN.matcher(blob).find()) {
			return SetupSwapMode.GEM;
		}
		if (SETUP_SWAP_WORD_STAR_PATTERN.matcher(blob).find()) {
			return SetupSwapMode.STARS;
		}
		return null;
	}

	private static boolean isSetupSwapBossRelicBlob(String blob) {
		return blob != null && !blob.isBlank() && SETUP_SWAP_BOSS_RELIC_MARKER_PATTERN.matcher(blob).find();
	}

	private static boolean isSetupSwapBossRelicStack(ItemStack stack, SetupSwapMode requiredMode) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		String blob = buildStackParseBlob(stack);
		if (!isSetupSwapBossRelicBlob(blob)) {
			return false;
		}
		double boost = parseSetupSwapBoostFromBlob(blob);
		if (boost < 0D) {
			return false;
		}
		if (requiredMode == null) {
			return true;
		}
		SetupSwapMode parsedType = parseSetupSwapBossRelicTypeFromBlob(blob);
		return parsedType == requiredMode;
	}

	private static double parseSetupSwapBoostFromBlob(String blob) {
		if (blob == null || blob.isBlank()) {
			return -1D;
		}
		Matcher matcher = SETUP_SWAP_BOOST_PATTERN.matcher(blob);
		if (!matcher.find()) {
			return -1D;
		}
		try {
			return Double.parseDouble(matcher.group(1));
		} catch (Exception exception) {
			return -1D;
		}
	}

	private static Integer findBestSetupSwapRingSlotInOpenContainer(int expectedRows, SetupSwapMode mode) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return null;
			}
			int topSlots = rows * CONTAINER_COLUMNS;
			int bestSlot = -1;
			double bestBoost = -1D;
			int bestSlotAny = -1;
			double bestBoostAny = -1D;
			boolean sawTypedRing = false;
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!isSetupSwapRingStack(stack, false)) {
					continue;
				}
				String blob = buildStackParseBlob(stack);
				SetupSwapMode parsedType = parseSetupSwapTypeFromBlob(blob);
				if (parsedType != null) {
					sawTypedRing = true;
				}
				double boost = parseSetupSwapBoostFromBlob(blob);
				if (boost >= 0D) {
					if (boost > bestBoostAny) {
						bestBoostAny = boost;
						bestSlotAny = slot;
					}
					if (parsedType == mode && boost > bestBoost) {
						bestBoost = boost;
						bestSlot = slot;
					}
				}
			}
			if (bestSlot >= 0) {
				return bestSlot;
			}
			// If we saw any rings that had a type marker, but none matched the requested mode, don't pick a random ring.
			if (sawTypedRing) {
				return null;
			}
			return bestSlotAny < 0 ? null : bestSlotAny;
		}, null);
	}

	private static List<Integer> findBestSetupSwapAttachmentSlotsInOpenContainer(int expectedRows, SetupSwapMode mode) {
		return callOnClientThread(() -> {
			List<SetupSwapCandidate> candidates = new ArrayList<>();
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return List.of();
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return List.of();
			}
			int topSlots = rows * CONTAINER_COLUMNS;
			Item desiredItem = setupSwapAttachmentItemForMode(mode);
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (stack.isEmpty() || !stack.isOf(desiredItem)) {
					continue;
				}
				String blob = buildStackParseBlob(stack);
				if (isSetupSwapBossRelicBlob(blob)) {
					continue;
				}
				SetupSwapMode parsedType = parseSetupSwapTypeFromBlob(blob);
				if (parsedType != null && parsedType != mode) {
					continue;
				}
				double boost = parseSetupSwapBoostFromBlob(blob);
				if (boost >= 0D) {
					candidates.add(new SetupSwapCandidate(slot, boost));
				}
			}
			candidates.sort((a, b) -> Double.compare(b.boost, a.boost));
			List<Integer> out = new ArrayList<>(candidates.size());
			for (SetupSwapCandidate candidate : candidates) {
				out.add(candidate.slot);
			}
			return List.copyOf(out);
		}, List.of());
	}

	private static Integer findBestSetupSwapAttachmentPlayerSlotInOpenScreen(SetupSwapMode mode) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int topSlots = topSlotsForHandler(handler);
			int max = Math.min(handler.slots.size(), topSlots + PLAYER_INVENTORY_SLOTS);
			Item desiredItem = setupSwapAttachmentItemForMode(mode);

			int bestSlot = -1;
			double bestBoost = -1D;
			for (int slot = topSlots; slot < max; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (stack.isEmpty() || !stack.isOf(desiredItem)) {
					continue;
				}
				String blob = buildStackParseBlob(stack);
				if (isSetupSwapBossRelicBlob(blob)) {
					continue;
				}
				SetupSwapMode parsedType = parseSetupSwapTypeFromBlob(blob);
				if (parsedType != null && parsedType != mode) {
					continue;
				}
				double boost = parseSetupSwapBoostFromBlob(blob);
				if (boost > bestBoost) {
					bestBoost = boost;
					bestSlot = slot;
				}
			}
			return bestSlot < 0 ? null : bestSlot;
		}, null);
	}

	private static List<Integer> findBestSetupSwapBossRelicSlotsInOpenContainer(int expectedRows, SetupSwapMode mode) {
		return callOnClientThread(() -> {
			List<SetupSwapCandidate> exactCandidates = new ArrayList<>();
			List<SetupSwapCandidate> fallbackCandidates = new ArrayList<>();
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return List.of();
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return List.of();
			}
			int topSlots = rows * CONTAINER_COLUMNS;
			boolean sawTypedRelic = false;
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!isSetupSwapBossRelicStack(stack, null)) {
					continue;
				}
				String blob = buildStackParseBlob(stack);
				SetupSwapMode parsedType = parseSetupSwapBossRelicTypeFromBlob(blob);
				if (parsedType != null) {
					sawTypedRelic = true;
				}
				double boost = parseSetupSwapBoostFromBlob(blob);
				if (boost < 0D) {
					continue;
				}
				fallbackCandidates.add(new SetupSwapCandidate(slot, boost));
				if (parsedType == mode) {
					exactCandidates.add(new SetupSwapCandidate(slot, boost));
				}
			}
			List<SetupSwapCandidate> selected;
			if (!exactCandidates.isEmpty()) {
				selected = exactCandidates;
			} else if (sawTypedRelic) {
				return List.of();
			} else {
				selected = fallbackCandidates;
			}
			selected.sort((a, b) -> Double.compare(b.boost, a.boost));
			List<Integer> out = new ArrayList<>(selected.size());
			for (SetupSwapCandidate candidate : selected) {
				out.add(candidate.slot);
			}
			return List.copyOf(out);
		}, List.of());
	}

	private static Integer findBestSetupSwapBossRelicPlayerSlotInOpenScreen(SetupSwapMode mode) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int topSlots = topSlotsForHandler(handler);
			int max = Math.min(handler.slots.size(), topSlots + PLAYER_INVENTORY_SLOTS);

			int bestExactSlot = -1;
			double bestExactBoost = -1D;
			int bestAnySlot = -1;
			double bestAnyBoost = -1D;
			boolean sawTypedRelic = false;
			for (int slot = topSlots; slot < max; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!isSetupSwapBossRelicStack(stack, null)) {
					continue;
				}
				String blob = buildStackParseBlob(stack);
				SetupSwapMode parsedType = parseSetupSwapBossRelicTypeFromBlob(blob);
				if (parsedType != null) {
					sawTypedRelic = true;
				}
				double boost = parseSetupSwapBoostFromBlob(blob);
				if (boost < 0D) {
					continue;
				}
				if (boost > bestAnyBoost) {
					bestAnyBoost = boost;
					bestAnySlot = slot;
				}
				if (parsedType == mode && boost > bestExactBoost) {
					bestExactBoost = boost;
					bestExactSlot = slot;
				}
			}
			if (bestExactSlot >= 0) {
				return bestExactSlot;
			}
			if (sawTypedRelic) {
				return null;
			}
			return bestAnySlot < 0 ? null : bestAnySlot;
		}, null);
	}

	private static int topSlotsForHandler(ScreenHandler handler) {
		if (handler == null) {
			return 0;
		}
		int rows = getContainerRows(handler);
		int topSlots = rows > 0
			? rows * CONTAINER_COLUMNS
			: handler.slots.size() - PLAYER_INVENTORY_SLOTS;
		return MathHelper.clamp(topSlots, 0, handler.slots.size());
	}

	private static Item setupSwapAttachmentItemForMode(SetupSwapMode mode) {
		return switch (mode) {
			case STARS -> Items.NETHER_STAR;
			case GEM -> Items.DIAMOND;
			case MONEY -> Items.EMERALD;
		};
	}

	private static Integer findFirstTopContainerSlotMatchingAnyRows(Predicate<ItemStack> predicate) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int topSlots = topSlotsForHandler(handler);
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isEmpty() && predicate.test(stack)) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static Integer findFirstPlayerSlotMatchingAnyRows(Predicate<ItemStack> predicate) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int topSlots = topSlotsForHandler(handler);
			int max = Math.min(handler.slots.size(), topSlots + PLAYER_INVENTORY_SLOTS);
			for (int slot = topSlots; slot < max; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isEmpty() && predicate.test(stack)) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static Integer findFirstPlayerRingSlotAnyRows() {
		return findFirstPlayerSlotMatchingAnyRows(stack -> isSetupSwapRingStack(stack, true));
	}

	// Detects lobby by checking for a compass in the hotbar and runs the lobby recovery flow.
	private static void runLobbyCompassFailsafeWorker() {
		long nextAttemptAt = 0L;
		long nextStateScanAt = 0L;
		boolean shouldRun = false;
		int compassSlot = -1;
		boolean compassInHotbar = false;
		boolean inLobby = false;

		while (!Thread.currentThread().isInterrupted()) {
			if (!hasAutomationAccess()) {
				if (LOBBY_FAILSAFE_ACTIVE.get()) {
					LOBBY_FAILSAFE_ACTIVE.set(false);
				}
				nextStateScanAt = 0L;
				sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
				continue;
			}

			if (!LOBBY_RECONNECT_ENABLED.get()) {
				if (LOBBY_FAILSAFE_ACTIVE.get()) {
					LOBBY_FAILSAFE_ACTIVE.set(false);
				}
				nextStateScanAt = 0L;
				sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
				continue;
			}

			long now = System.currentTimeMillis();
			long scanInterval = LOBBY_FAILSAFE_ACTIVE.get()
				? LOBBY_SCAN_INTERVAL_ACTIVE_MS
				: LOBBY_SCAN_INTERVAL_IDLE_MS;
			if (now >= nextStateScanAt) {
				shouldRun = shouldRunLobbyFailsafe();
				compassSlot = shouldRun ? findCompassHotbarSlot() : -1;
				compassInHotbar = shouldRun && compassSlot >= 0;
				inLobby = shouldRun && compassInHotbar;
				nextStateScanAt = now + scanInterval;
			}

			if (LOBBY_FAILSAFE_ACTIVE.get() && (!shouldRun || !inLobby)) {
				LOBBY_FAILSAFE_ACTIVE.set(false);
				sendClientFeedback("Lobby clear -> failsafe stopped.");
				if (shouldRun && !compassInHotbar) {
					scheduleEggRestartAfterReconnect();
				}
			} else if (!LOBBY_FAILSAFE_ACTIVE.get() && inLobby) {
				LOBBY_FAILSAFE_ACTIVE.set(true);
				nextAttemptAt = 0L;
				nextStateScanAt = 0L;
				EGG_RECONNECT_RESTART_SCHEDULED.set(false);
				sendClientFeedback("Lobby detected (compass) -> failsafe started.");
			}

			if (LOBBY_FAILSAFE_ACTIVE.get() && shouldRun && inLobby && !isEventRoutineBusy()) {
				now = System.currentTimeMillis();
				if (now >= nextAttemptAt) {
					boolean success = tryLobbyFailsafeCycle(compassSlot);
					nextAttemptAt = System.currentTimeMillis() + (success ? LOBBY_FAILSAFE_REPEAT_MS : LOBBY_FAILSAFE_RETRY_MS);
				}
			}

			sleepQuietly(LOBBY_FAILSAFE_ACTIVE.get() ? BACKGROUND_LOOP_DELAY_MS : BACKGROUND_IDLE_LOOP_DELAY_MS);
		}
	}

	private static void runAutoDailyWorker() {
		boolean wasEnabled = false;

		while (!Thread.currentThread().isInterrupted()) {
			boolean enabled = hasAutomationAccess() && AUTO_DAILY_ENABLED.get();
			if (!enabled) {
				wasEnabled = false;
				AUTO_DAILY_PERKS_LAST_ATTEMPT_MS = 0L;
				AUTO_DAILY_FREECREDITS_LAST_ATTEMPT_MS = 0L;
				AUTO_DAILY_KEYALL_LAST_ATTEMPT_MS = 0L;
				AUTO_DAILY_NEXT_ENQUEUE_AT_MS = 0L;
				sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
				continue;
			}

			MinecraftClient client = MinecraftClient.getInstance();
			boolean onSalesServer = isConnectedToSalesServer(client);
			long now = System.currentTimeMillis();
			if (!onSalesServer) {
				AUTO_DAILY_NEXT_ENQUEUE_AT_MS = 0L;
				sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
				continue;
			}
			if (isAutoDailyConnectGraceActive(now)) {
				// Give the server time to move the player out of lobby and let client caches populate.
				sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
				continue;
			}
			if (isInLobbyForAutoDaily()) {
				AUTO_DAILY_NEXT_ENQUEUE_AT_MS = 0L;
				sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
				continue;
			}

			if (!wasEnabled) {
				wasEnabled = true;
				// Allow an immediate first run on enable (no retry-cooldown wait).
				AUTO_DAILY_PERKS_LAST_ATTEMPT_MS = 0L;
				AUTO_DAILY_FREECREDITS_LAST_ATTEMPT_MS = 0L;
				AUTO_DAILY_KEYALL_LAST_ATTEMPT_MS = 0L;
				AUTO_DAILY_NEXT_ENQUEUE_AT_MS = 0L;
			}

			if (now >= AUTO_DAILY_NEXT_ENQUEUE_AT_MS) {
				boolean enqueued =
					maybeEnqueueAutoDailyPerks(now)
						|| maybeEnqueueAutoDailyFreecredits(now)
						|| maybeEnqueueAutoDailyKeyall(now);
				if (enqueued) {
					AUTO_DAILY_NEXT_ENQUEUE_AT_MS = now + AUTO_DAILY_STAGGER_MS;
				}
			}

			sleepQuietly(AUTO_DAILY_POLL_MS);
		}
	}

	private static boolean isConnectedToSalesServer(MinecraftClient client) {
		if (client == null) {
			updateSalesServerConnectionCache(false);
			return false;
		}
		if (!isConnectedToRemoteServer(client)) {
			updateSalesServerConnectionCache(false);
			return false;
		}

		ServerInfo serverEntry = client.getCurrentServerEntry();
		if (serverEntry != null && serverEntry.address != null && isSalesServerAddress(serverEntry.address)) {
			rememberSalesServerHost(serverEntry.address);
			updateSalesServerConnectionCache(true);
			return true;
		}

		// Fallback: if the server entry is missing, best-effort match from the connection address.
		ClientPlayNetworkHandler handler = client.getNetworkHandler();
		if (handler == null) {
			updateSalesServerConnectionCache(false);
			return false;
		}
		SocketAddress address = handler.getConnection().getAddress();
		if (address == null) {
			updateSalesServerConnectionCache(false);
			return false;
		}
		if (address instanceof InetSocketAddress inet) {
			String host = inet.getHostString();
			if (host != null && !host.isBlank() && isSalesServerAddress(host + ":" + inet.getPort())) {
				rememberSalesServerHost(host);
				updateSalesServerConnectionCache(true);
				return true;
			}
		}

		String raw = address.toString();
		boolean match = false;
		if (raw != null) {
			String lowerRaw = raw.toLowerCase(Locale.ROOT);
			for (String host : SALES_SERVER_HOST_ALIASES) {
				if (host != null && !host.isBlank() && lowerRaw.contains(host.toLowerCase(Locale.ROOT))) {
					match = true;
					rememberSalesServerHost(host);
					break;
				}
			}
		}
		updateSalesServerConnectionCache(match);
		return match;
	}

	private static void rememberSalesServerHost(String rawAddressOrHost) {
		if (rawAddressOrHost == null || rawAddressOrHost.isBlank()) {
			return;
		}
		try {
			ServerAddress parsed = ServerAddress.parse(rawAddressOrHost.trim());
			String host = parsed.getAddress();
			if (isSalesServerHost(host)) {
				LAST_SALES_SERVER_HOST = host;
				return;
			}
		} catch (Exception ignored) {
			// fall back below
		}
		String hostOnly = rawAddressOrHost.trim();
		int colon = hostOnly.indexOf(':');
		if (colon > 0 && !hostOnly.contains("]")) {
			hostOnly = hostOnly.substring(0, colon);
		}
		if (isSalesServerHost(hostOnly)) {
			LAST_SALES_SERVER_HOST = hostOnly;
		}
	}

	private static boolean isInLobbyForAutoDaily() {
		// Skip Auto Daily in the lobby to avoid command/UI failures.
		// Uses client-state cache to avoid cross-thread inventory scans on every poll.
		return hasCompassInHotbar();
	}

	private static void updateSalesServerConnectionCache(boolean onSalesServer) {
		boolean wasOnSalesServer = CACHED_ON_SALES_SERVER.getAndSet(onSalesServer);
		if (onSalesServer) {
			if (!wasOnSalesServer) {
				CACHED_SALES_SERVER_CONNECTED_AT_MS.set(System.currentTimeMillis());
			}
			return;
		}
		CACHED_SALES_SERVER_CONNECTED_AT_MS.set(0L);
	}

	private static boolean isAutoDailyConnectGraceActive(long nowMs) {
		long connectedAtMs = CACHED_SALES_SERVER_CONNECTED_AT_MS.get();
		return connectedAtMs > 0L && nowMs - connectedAtMs < AUTO_DAILY_AFTER_CONNECT_GRACE_MS;
	}

	private static boolean isAutoDailyDue(long now, String lastRunTimestamp, long intervalMs) {
		long lastRunMs = parseAutoDailyTimestampToEpochMs(lastRunTimestamp);
		if (lastRunMs <= 0L) {
			return true;
		}
		return now - lastRunMs >= intervalMs;
	}

	private static boolean shouldAttemptAutoDaily(long now, long lastAttemptMs) {
		return lastAttemptMs <= 0L || now - lastAttemptMs >= AUTO_DAILY_RETRY_COOLDOWN_MS;
	}

	private static boolean maybeEnqueueAutoDailyPerks(long now) {
		if (!AUTO_DAILY_PERKS_ENABLED.get()) {
			return false;
		}
		if (!isAutoDailyDue(now, AUTO_DAILY_PERKS_LAST_RUN, AUTO_DAILY_PERKS_INTERVAL_MS)) {
			return false;
		}
		if (!shouldAttemptAutoDaily(now, AUTO_DAILY_PERKS_LAST_ATTEMPT_MS)) {
			return false;
		}
		if (!AUTO_DAILY_PERKS_SCHEDULED.compareAndSet(false, true)) {
			return false;
		}
		AUTO_DAILY_PERKS_LAST_ATTEMPT_MS = now;

		enqueueRoutine("auto-daily-perks", () -> {
			try {
				if (!hasAutomationAccess()
					|| !AUTO_DAILY_ENABLED.get()
					|| !AUTO_DAILY_PERKS_ENABLED.get()
					|| !isConnectedToSalesServer(MinecraftClient.getInstance())
					|| isAutoDailyConnectGraceActive(System.currentTimeMillis())
					|| isInLobbyForAutoDaily()) {
					return;
				}
					if (runPerksEmeraldRoutine()) {
						AUTO_DAILY_PERKS_LAST_RUN = AUTO_DAILY_TIMESTAMP_FORMATTER.format(LocalDateTime.now());
						savePersistedSettings();
					}
				} finally {
					AUTO_DAILY_PERKS_SCHEDULED.set(false);
			}
		});
		return true;
	}

	private static boolean maybeEnqueueAutoDailyFreecredits(long now) {
		if (!AUTO_DAILY_FREECREDITS_ENABLED.get()) {
			return false;
		}
		if (!isAutoDailyDue(now, AUTO_DAILY_FREECREDITS_LAST_RUN, AUTO_DAILY_FREECREDITS_INTERVAL_MS)) {
			return false;
		}
		if (!shouldAttemptAutoDaily(now, AUTO_DAILY_FREECREDITS_LAST_ATTEMPT_MS)) {
			return false;
		}
		if (!AUTO_DAILY_FREECREDITS_SCHEDULED.compareAndSet(false, true)) {
			return false;
		}
		AUTO_DAILY_FREECREDITS_LAST_ATTEMPT_MS = now;

		enqueueRoutine("auto-daily-freecredits", () -> {
			try {
				if (!hasAutomationAccess()
					|| !AUTO_DAILY_ENABLED.get()
					|| !AUTO_DAILY_FREECREDITS_ENABLED.get()
					|| !isConnectedToSalesServer(MinecraftClient.getInstance())
					|| isAutoDailyConnectGraceActive(System.currentTimeMillis())
					|| isInLobbyForAutoDaily()) {
					return;
				}
				if (sendChatCommand("freecredits")) {
					AUTO_DAILY_FREECREDITS_LAST_RUN = AUTO_DAILY_TIMESTAMP_FORMATTER.format(LocalDateTime.now());
					savePersistedSettings();
				}
			} finally {
				AUTO_DAILY_FREECREDITS_SCHEDULED.set(false);
			}
		});
		return true;
	}

	private static boolean maybeEnqueueAutoDailyKeyall(long now) {
		if (!AUTO_DAILY_KEYALL_ENABLED.get()) {
			return false;
		}
		if (!isAutoDailyDue(now, AUTO_DAILY_KEYALL_LAST_RUN, AUTO_DAILY_KEYALL_INTERVAL_MS)) {
			return false;
		}
		if (!shouldAttemptAutoDaily(now, AUTO_DAILY_KEYALL_LAST_ATTEMPT_MS)) {
			return false;
		}
		if (!AUTO_DAILY_KEYALL_SCHEDULED.compareAndSet(false, true)) {
			return false;
		}
		AUTO_DAILY_KEYALL_LAST_ATTEMPT_MS = now;

		enqueueRoutine("auto-daily-keyall", () -> {
			try {
				if (!hasAutomationAccess()
					|| !AUTO_DAILY_ENABLED.get()
					|| !AUTO_DAILY_KEYALL_ENABLED.get()
					|| !isConnectedToSalesServer(MinecraftClient.getInstance())
					|| isAutoDailyConnectGraceActive(System.currentTimeMillis())
					|| isInLobbyForAutoDaily()) {
					return;
				}
				if (sendChatCommand("donator-keyall")) {
					AUTO_DAILY_KEYALL_LAST_RUN = AUTO_DAILY_TIMESTAMP_FORMATTER.format(LocalDateTime.now());
					savePersistedSettings();
				}
			} finally {
				AUTO_DAILY_KEYALL_SCHEDULED.set(false);
			}
		});
		return true;
	}

	private static boolean runPerksEmeraldRoutine() {
		if (isInLobbyForAutoDaily()) {
			return false;
		}
		closeCurrentHandledScreen();
		if (!sleepQuietly(PERKS_CLICK_DELAY_MS)) {
			return false;
		}
		if (!openContainerWithCommandAnyRows("perks", "perks")) {
			return false;
		}

		try {
			// Some clients load the GUI contents late. Wait up to 1s for the emerald to appear.
			long deadline = System.currentTimeMillis() + 1000L;
			Integer emeraldSlot = null;
			while (System.currentTimeMillis() < deadline) {
				emeraldSlot = findTopContainerItemSlotInOpenScreen(Items.EMERALD);
				if (emeraldSlot != null) {
					break;
				}
				if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
					return false;
				}
			}
			if (emeraldSlot == null) {
				// Treat as done: if the emerald isn't there (already claimed / UI changed), still record the timestamp.
				return true;
			}
			return clickAnySlot(emeraldSlot, 0, SlotActionType.PICKUP, PERKS_CLICK_DELAY_MS, "perks emerald", true);
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static int findCompassHotbarSlot() {
		return CACHED_COMPASS_HOTBAR_SLOT.get();
	}

	private static boolean hasCompassInHotbar() {
		return CACHED_COMPASS_HOTBAR_SLOT.get() >= 0;
	}

	private static void scheduleEggRestartAfterReconnect() {
		if (!EGG_ENABLED.get()) {
			return;
		}
		if (!EGG_RECONNECT_RESTART_SCHEDULED.compareAndSet(false, true)) {
			return;
		}

		BACKGROUND_EXECUTOR.execute(() -> {
			try {
				if (!sleepQuietly(EGG_RESTART_AFTER_RECONNECT_DELAY_MS)) {
					return;
				}
				if (!EGG_ENABLED.get()) {
					return;
				}
				if (hasCompassInHotbar()) {
					sendClientFeedback("Reconnect not done yet (compass still in hotbar). Retrying...");
					return;
				}

				if (!AUTOMATION_ACTIVE.get()) {
					setAutomationActive(true);
				}
				EGG_PENDING.set(true);
				sendClientFeedback("Reconnect done -> egg start queued.");
			} finally {
				EGG_RECONNECT_RESTART_SCHEDULED.set(false);
			}
		});
	}

	private static boolean tryLobbyFailsafeCycle(int detectedCompassSlot) {
		if (!LOBBY_FAILSAFE_ACTIVE.get() || !shouldRunLobbyFailsafe() || isEventRoutineBusy()) {
			return false;
		}
		int compassSlot = detectedCompassSlot >= 0 ? detectedCompassSlot : findCompassHotbarSlot();

		// Minehut lobby flow: slot 0 means "join sales" first, then run the normal lobby GUI routine.
		if (compassSlot == 0) {
			if (!tryLobbyJoinSalesFromMinehutLobby()) {
				return false;
			}
			compassSlot = findCompassHotbarSlot();
			if (compassSlot == 0) {
				return false;
			}
		}
		if (compassSlot < 0) {
			return false;
		}

		if (!openLobbyCompassGui(compassSlot)) {
			return false;
		}

		try {
			return clickSingleSlot(LOBBY_FAILSAFE_TARGET_SLOT, LOBBY_CLICK_DELAY_MS, "lobby failsafe slot 13", true);
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean tryLobbyJoinSalesFromMinehutLobby() {
		long now = System.currentTimeMillis();
		if (now - lastLobbyJoinSalesAttemptMs < LOBBY_SLOT0_JOIN_SALES_RETRY_MS) {
			return false;
		}

		if (!sendChatCommand("join sales")) {
			return false;
		}
		lastLobbyJoinSalesAttemptMs = now;
		sendClientFeedback("Lobby failsafe: compass on slot 0 -> sent /join sales.");

		long deadline = now + LOBBY_SLOT0_WORLD_CHANGE_TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			if (!LOBBY_FAILSAFE_ACTIVE.get() || !shouldRunLobbyFailsafe() || isEventRoutineBusy()) {
				return false;
			}
			MinecraftClient client = MinecraftClient.getInstance();
			boolean onSalesServer = isConnectedToSalesServer(client);
			int compassSlot = findCompassHotbarSlot();
			if (onSalesServer || compassSlot != 0) {
				sendClientFeedback("Lobby failsafe: world change detected after /join sales.");
				return true;
			}
			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}

		sendClientFeedback("Lobby failsafe: /join sales did not transfer yet, retrying.");
		return false;
	}

	private static boolean openLobbyCompassGui(int detectedCompassSlot) {
		if (isContainerRowsOpen(4)) {
			return true;
		}

		for (int attempt = 0; attempt < LOBBY_COMPASS_OPEN_ATTEMPTS; attempt++) {
			if (!LOBBY_FAILSAFE_ACTIVE.get() || !shouldRunLobbyFailsafe() || isEventRoutineBusy()) {
				return false;
			}

			callOnClientThread(() -> {
				MinecraftClient client = MinecraftClient.getInstance();
				if (client.player != null && getActiveHandledScreenHandler(client) != null) {
					client.player.closeHandledScreen();
				}
				return null;
			}, null);

			int compassSlot = detectedCompassSlot >= 0 ? detectedCompassSlot : findCompassHotbarSlot();
			if (compassSlot < 0) {
				return false;
			}
			if (!useHotbarItem(compassSlot)) {
				if (!sleepQuietly(EGG_REOPEN_DELAY_MS)) {
					return false;
				}
				continue;
			}
			if (!sleepQuietly(EGG_OPEN_DELAY_MS)) {
				return false;
			}
			if (waitForContainerRowsDuringLobbyFailsafe(4, LOBBY_FAILSAFE_GUI_TIMEOUT_MS)) {
				return true;
			}
			if (!sleepQuietly(EGG_REOPEN_DELAY_MS)) {
				return false;
			}
		}

		return false;
	}

	private static boolean waitForContainerRowsDuringLobbyFailsafe(int expectedRows, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (!LOBBY_FAILSAFE_ACTIVE.get() || !shouldRunLobbyFailsafe() || isEventRoutineBusy()) {
				return false;
			}
			if (CACHED_OPEN_CONTAINER_ROWS.get() == expectedRows) {
				return true;
			}

			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}
		return false;
	}

	private static boolean isContainerRowsOpen(int expectedRows) {
		return CACHED_OPEN_CONTAINER_ROWS.get() == expectedRows;
	}

	private static void runAutomationWorker() {
		FishingAutomationState fishingState = new FishingAutomationState();
		EggAutomationState eggState = new EggAutomationState();
		AutomationMode lastMode = null;

		while (!Thread.currentThread().isInterrupted()) {
			AutomationMode mode = resolveEnabledAutomationMode();
			if (mode != lastMode) {
				fishingState.reset();
				eggState.reset();
				lastMode = mode;
			}

			if (mode == AutomationMode.EGG) {
				tickEggAutomation(eggState);
				continue;
			}
			if (mode == AutomationMode.MUSEUM) {
				tickMuseumAutomation();
				continue;
			}
			if (mode == AutomationMode.RING_SCRAPPER) {
				tickRingScrapperAutomation();
				continue;
			}
			if (mode == AutomationMode.FISHING) {
				tickFishingAutomation(fishingState);
				continue;
			}

			sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
		}
	}

	private static AutomationMode resolveEnabledAutomationMode() {
		if (EGG_ENABLED.get()) {
			return AutomationMode.EGG;
		}
		if (MUSEUM_ENABLED.get()) {
			return AutomationMode.MUSEUM;
		}
		if (RING_SCRAPPER_ENABLED.get()) {
			return AutomationMode.RING_SCRAPPER;
		}
		if (FISHING_ENABLED.get()) {
			return AutomationMode.FISHING;
		}
		return null;
	}

	private static void tickFishingAutomation(FishingAutomationState state) {
		boolean paused =
			!hasAutomationAccess()
				|| !AUTOMATION_ACTIVE.get()
				|| !FISHING_ENABLED.get()
				|| LOBBY_FAILSAFE_ACTIVE.get()
				|| isEventRoutineBusy();
		if (paused) {
			if (isEventRoutineBusy()) {
				state.pausedByEvent = true;
			}
			sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
			return;
		}

		if (state.pausedByEvent) {
			// Restart from a clean cast after any queued/active event routine finished.
			state.rodIsCast = false;
			state.lastCastTimestamp = 0L;
			state.pausedByEvent = false;
		}

		boolean hasBobber = hasFishingBobber();
		if (!state.rodIsCast) {
			if (hasBobber) {
				state.rodIsCast = true;
				state.lastCastTimestamp = System.currentTimeMillis();
			} else if (useHotbarItem(FISHING_HOTBAR_SLOT)) {
				state.rodIsCast = true;
				state.lastCastTimestamp = System.currentTimeMillis();
				sleepQuietly(FISHING_RECAST_DELAY_MS);
			}
			sleepQuietly(BACKGROUND_LOOP_DELAY_MS);
			return;
		}

		if (!hasBobber) {
			if (useHotbarItem(FISHING_HOTBAR_SLOT)) {
				state.lastCastTimestamp = System.currentTimeMillis();
				sleepQuietly(FISHING_RECAST_DELAY_MS);
			}
			sleepQuietly(BACKGROUND_LOOP_DELAY_MS);
			return;
		}

		long now = System.currentTimeMillis();
		if (now - state.lastCastTimestamp > FISHING_FAILSAFE_MS) {
			if (useHotbarItem(FISHING_HOTBAR_SLOT) && sleepQuietly(FISHING_RECAST_DELAY_MS)) {
				useHotbarItem(FISHING_HOTBAR_SLOT);
				state.lastCastTimestamp = System.currentTimeMillis();
			}
		}

		sleepQuietly(BACKGROUND_LOOP_DELAY_MS);
	}

	private static void tickEggAutomation(EggAutomationState state) {
		boolean eventBusy = isEventRoutineBusy();
		boolean pausedBase =
			!hasAutomationAccess()
				|| !AUTOMATION_ACTIVE.get()
				|| !EGG_ENABLED.get()
				|| LOBBY_FAILSAFE_ACTIVE.get()
				|| eventBusy;
		if (pausedBase) {
			if (eventBusy) {
				state.pausedByEvent = true;
			}
			state.nextEggWatchdogAt = 0L;
			state.nextEggChatScanAt = 0L;
			state.eggChatWindowStartAt = 0L;
			state.eggChatWindowEndAt = 0L;
			state.eggChatWindowEggType = null;
			sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
			return;
		}

		long now = System.currentTimeMillis();
		if (state.eggChatWindowEndAt > 0L && now >= state.eggChatWindowEndAt) {
			EggType expected = state.eggChatWindowEggType == null ? selectedEggType : state.eggChatWindowEggType;
			EggType lastType = LAST_EGG_CHAT_MATCH_TYPE;
			long lastSeen = LAST_EGG_CHAT_MATCH_MS.get();
			boolean seen = lastType == expected && lastSeen >= state.eggChatWindowStartAt && lastSeen <= state.eggChatWindowEndAt;

			state.eggChatWindowStartAt = 0L;
			state.eggChatWindowEndAt = 0L;
			state.eggChatWindowEggType = null;

			if (!seen) {
				sendClientFeedback("Egg watchdog: no chat confirmation for " + expected.displayName + " -> restarting.");
				EGG_FORCE_REOPEN.set(true);
				EGG_PENDING.set(true);
				sleepQuietly(BACKGROUND_LOOP_DELAY_MS);
				return;
			}
		}
		if (state.eggChatWindowEndAt == 0L && now >= state.nextEggChatScanAt) {
			state.eggChatWindowEggType = selectedEggType;
			state.eggChatWindowStartAt = now;
			state.eggChatWindowEndAt = now + EGG_CHAT_CONFIRM_WINDOW_MS;
			state.nextEggChatScanAt = now + EGG_CHAT_CONFIRM_INTERVAL_MS;
		}

		if (!EGG_PENDING.get()) {
			if (state.nextEggWatchdogAt == 0L) {
				state.nextEggWatchdogAt = now + EGG_OPEN_WATCHDOG_INTERVAL_MS;
			}

			if (now >= state.nextEggWatchdogAt) {
				state.nextEggWatchdogAt = now + EGG_OPEN_WATCHDOG_INTERVAL_MS;
				if (!isSixRowContainerOpen()) {
					EGG_PENDING.set(true);
					sendClientFeedback("Egg watchdog: GUI not open -> restarting.");
					sleepQuietly(BACKGROUND_LOOP_DELAY_MS);
					return;
				}
			}

			sleepQuietly(BACKGROUND_LOOP_DELAY_MS);
			return;
		}

		EggType eggType = selectedEggType;
		boolean forceReopen = state.pausedByEvent || EGG_FORCE_REOPEN.getAndSet(false);
		state.pausedByEvent = false;
		state.nextEggWatchdogAt = System.currentTimeMillis() + EGG_OPEN_WATCHDOG_INTERVAL_MS;
		boolean completed = runEggSequence(eggType, forceReopen);

		if (completed) {
			EGG_PENDING.set(false);
			sendClientFeedback("Egg routine completed: " + eggType.displayName);
			return;
		}

		sleepQuietly(EGG_RETRY_DELAY_MS);
	}

	private static void tickMuseumAutomation() {
		if (shouldPauseMuseumAutomation()) {
			sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
			return;
		}

		boolean completed = runMuseumCycle();
		if (!completed) {
			sleepQuietly(MUSEUM_RETRY_DELAY_MS);
		}
	}

	private static void tickRingScrapperAutomation() {
		if (shouldPauseRingScrapperAutomation()) {
			sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
			return;
		}

		if (!RING_SCRAPPER_PENDING.compareAndSet(true, false)) {
			sleepQuietly(BACKGROUND_IDLE_LOOP_DELAY_MS);
			return;
		}

		runRingScrapperCycle();
		setAutomationActive(false);
	}

	private static boolean shouldPauseRingScrapperAutomation() {
		return !hasAutomationAccess()
			|| !AUTOMATION_ACTIVE.get()
			|| !RING_SCRAPPER_ENABLED.get()
			|| LOBBY_FAILSAFE_ACTIVE.get()
			|| isEventRoutineBusy();
	}

	private static boolean runRingScrapperCycle() {
		long delayMs = MathHelper.clamp(RING_SCRAPPER_CLICK_DELAY_MS, 20L, 10_000L);
		closeCurrentHandledScreen();
		if (!sleepQuietly(delayMs)) {
			return false;
		}
		if (!openContainerWithCommandAnyRows("rings", "ring scrapper /rings")) {
			return false;
		}

		try {
			Integer ringScrapperSlot = findFirstTopContainerSlotMatchingAnyRows(
				stack -> stackNameOrBlobContains(stack, "ring scrapper")
			);
			if (ringScrapperSlot == null) {
				sendClientFeedback("Ring scrapper: could not find 'Ring Scrapper' in /rings.");
				return false;
			}

			int beforeMenuSync = CACHED_OPEN_SCREEN_SYNC_ID.get();
			if (!clickAnySlot(ringScrapperSlot, 0, SlotActionType.PICKUP, delayMs, "ring scrapper open menu", true)) {
				sendClientFeedback("Ring scrapper: failed to open menu.");
				return false;
			}
			if (!waitForRingScrapperScrapperSlot(beforeMenuSync, GUI_OPEN_TIMEOUT_MS)) {
				sendClientFeedback("Ring scrapper: menu did not open.");
				return false;
			}

			Integer scrapperSlot = findFirstTopContainerSlotMatchingAnyRows(
				SalesClientMod::isRingScrapperTargetStack
			);
			if (scrapperSlot == null) {
				sendClientFeedback("Ring scrapper: could not find 'Scrapper'.");
				return false;
			}

			int moved = 0;
			int maxMoves = PLAYER_INVENTORY_SLOTS;
			while (moved < maxMoves) {
				if (shouldPauseRingScrapperAutomation()) {
					return false;
				}
				if (!CACHED_IS_HANDLED_SCREEN.get()) {
					sendClientFeedback("Ring scrapper stopped: GUI closed.");
					return false;
				}

				Integer ringSlot = findFirstPlayerRingSlotAnyRows();
				if (ringSlot == null) {
					break;
				}

				if (!ringScrapperMoveSlotToScrapper(ringSlot, scrapperSlot, delayMs)) {
					sendClientFeedback("Ring scrapper stopped: failed to scrap a ring.");
					return false;
				}
				moved++;
			}

			if (moved == 0) {
				sendClientFeedback("Ring scrapper: no rings found in inventory.");
			} else {
				sendClientFeedback("Ring scrapper done: scrapped " + moved + " ring(s).");
			}
			return true;
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean waitForRingScrapperScrapperSlot(int previousSyncId, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (shouldPauseRingScrapperAutomation()) {
				return false;
			}
			if (!CACHED_IS_HANDLED_SCREEN.get()) {
				return false;
			}
			int nowSync = CACHED_OPEN_SCREEN_SYNC_ID.get();
			if (nowSync != previousSyncId) {
				return true;
			}
			Integer slot = findFirstTopContainerSlotMatchingAnyRows(SalesClientMod::isRingScrapperTargetStack);
			if (slot != null) {
				return true;
			}
			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}
		return false;
	}

	private static boolean ringScrapperMoveSlotToScrapper(int ringSlot, int scrapperSlot, long delayMs) {
		if (!clickAnySlot(ringSlot, 0, SlotActionType.PICKUP, delayMs, "ring scrapper pickup", true)) {
			return false;
		}
		if (!clickAnySlot(scrapperSlot, 0, SlotActionType.PICKUP, delayMs, "ring scrapper drop", true)) {
			clickAnySlot(ringSlot, 0, SlotActionType.PICKUP, delayMs, "ring scrapper revert", true);
			return false;
		}

		int cursorCount = getCursorStackCount();
		if (cursorCount == 0) {
			return true;
		}

		// Best effort to avoid blocking future clicks with an item on cursor.
		clickAnySlot(ringSlot, 0, SlotActionType.PICKUP, delayMs, "ring scrapper revert", true);
		return false;
	}

	private static boolean isRingScrapperTargetStack(ItemStack stack) {
		return stackNameOrBlobContains(stack, "scrapper")
			&& !stackNameOrBlobContains(stack, "ring scrapper");
	}

	// Runs a pause/resume fishing loop that recasts when the bobber disappears.
	private static void runFishingWorker() {
		FishingAutomationState state = new FishingAutomationState();
		while (!Thread.currentThread().isInterrupted()) {
			tickFishingAutomation(state);
		}
	}

	// Runs the egg routine and restarts it if another event interrupted the flow.
	private static void runEggWorker() {
		EggAutomationState state = new EggAutomationState();
		while (!Thread.currentThread().isInterrupted()) {
			tickEggAutomation(state);
		}
	}

	private static boolean runEggSequence(EggType eggType, boolean forceReopen) {
		if (LOBBY_FAILSAFE_ACTIVE.get() || isEventRoutineBusy()) {
			return false;
		}
		if (!ensureEggContainerOpen(forceReopen)) {
			return false;
		}
		if (LOBBY_FAILSAFE_ACTIVE.get() || isEventRoutineBusy()) {
			return false;
		}

		// Some clients need extra time after the GUI opens before any clicks are reliable.
		if (!sleepQuietly(EGG_POST_OPEN_DELAY_MS)) {
			return false;
		}
		if (LOBBY_FAILSAFE_ACTIVE.get() || isEventRoutineBusy()) {
			return false;
		}

		if (eggType.secondPage) {
			if (!clickSingleSlot(EGG_NEXT_PAGE_SLOT, EGG_CLICK_DELAY_MS, "egg next page", true)) {
				return false;
			}
			// Give the GUI enough time to render the second page before selecting the egg.
			if (!sleepQuietly(EGG_PAGE_SWITCH_DELAY_MS)) {
				return false;
			}
			if (LOBBY_FAILSAFE_ACTIVE.get() || isEventRoutineBusy()) {
				return false;
			}
		}

		if (!clickSingleSlot(eggType.slot, EGG_CLICK_DELAY_MS, "egg select " + eggType.displayName, true)) {
			return false;
		}

		Integer autoEggSlot = findFirstTopContainerSlotMatching(6, stack -> stackNameOrBlobContains(stack, "auto egg"));
		if (autoEggSlot == null) {
			sendClientFeedback("Egg: could not find the Auto Egg button.");
			return false;
		}
		if (!clickSingleSlot(autoEggSlot, EGG_CLICK_DELAY_MS, "egg auto egg", true)) {
			return false;
		}

		Integer openAmountSlot = findFirstTopContainerSlotMatching(
			6,
			stack -> stack.isOf(Items.TURTLE_EGG) && stack.getCount() == EGG_AUTO_OPEN_AMOUNT
		);
		if (openAmountSlot == null) {
			// "Auto Egg" may open a submenu; give it a moment to populate the new page.
			if (!sleepQuietly(EGG_PAGE_SWITCH_DELAY_MS)) {
				return false;
			}
			openAmountSlot = findFirstTopContainerSlotMatching(
				6,
				stack -> stack.isOf(Items.TURTLE_EGG) && stack.getCount() == EGG_AUTO_OPEN_AMOUNT
			);
		}
		if (openAmountSlot == null) {
			sendClientFeedback("Egg: could not find a Turtle Egg option for x" + EGG_AUTO_OPEN_AMOUNT + ".");
			return false;
		}
		if (!clickSingleSlot(openAmountSlot, EGG_CLICK_DELAY_MS, "egg open x" + EGG_AUTO_OPEN_AMOUNT, true)) {
			return false;
		}

		return clickSingleSlot(EGG_FINAL_SLOT, EGG_CLICK_DELAY_MS, "egg confirm", true);
	}

	private static boolean ensureEggContainerOpen(boolean forceReopen) {
		if (forceReopen && isSixRowContainerOpen()) {
			closeCurrentHandledScreen();
			if (!sleepQuietly(EGG_REOPEN_DELAY_MS)) {
				return false;
			}
		}

		if (!forceReopen && isSixRowContainerOpen()) {
			return true;
		}

		for (int attempt = 1; attempt <= EGG_OPEN_ATTEMPTS; attempt++) {
			if (!useHotbarItemOnBlock(EGG_HOTBAR_SLOT)) {
				if (attempt == 1) {
					sendClientFeedback("Egg open needs a block target. Aim at a block.");
				}
				if (attempt < EGG_OPEN_ATTEMPTS && !sleepQuietly(EGG_REOPEN_DELAY_MS)) {
					return false;
				}
				continue;
			}
			if (!sleepQuietly(EGG_OPEN_DELAY_MS)) {
				return false;
			}
			if (waitForContainerRowsInterruptible(6, GUI_OPEN_TIMEOUT_MS)) {
				return true;
			}
			if (LOBBY_FAILSAFE_ACTIVE.get() || isEventRoutineBusy()) {
				return false;
			}
			if (attempt < EGG_OPEN_ATTEMPTS && !sleepQuietly(EGG_REOPEN_DELAY_MS)) {
				return false;
			}
		}

		sendClientFeedback("Egg GUI did not open. Retrying routine.");
		return false;
	}

	// Runs the Museum / Tinker automation loop while the mode is enabled.
	private static void runMuseumWorker() {
		while (!Thread.currentThread().isInterrupted()) {
			tickMuseumAutomation();
		}
	}

	private static boolean shouldPauseMuseumAutomation() {
		return !hasAutomationAccess()
			|| !AUTOMATION_ACTIVE.get()
			|| !MUSEUM_ENABLED.get()
			|| LOBBY_FAILSAFE_ACTIVE.get()
			|| isEventRoutineBusy();
	}

	private static boolean runMuseumCycle() {
		if (shouldPauseMuseumAutomation()) {
			return false;
		}

		if (hasItemInPlayerInventory(Items.PRISMARINE_SHARD)) {
			// If crystals are already in inventory, process them first and skip pulling new boxes.
			return runTinkerMuseumFlow();
		}

		if (!hasItemInPlayerInventory(Items.ENDER_CHEST)) {
			if (!openMuseumVaultAndPullBoxes()) {
				return false;
			}
		}

		if (!hasItemInPlayerInventory(Items.ENDER_CHEST)) {
			sendClientFeedback("Museum: no crystal boxes found (Ender Chests).");
			return false;
		}

		if (!openCrystalBoxesFromHotbar()) {
			return false;
		}

		return runTinkerMuseumFlow();
	}

	private static boolean openMuseumVaultAndPullBoxes() {
		String command = MUSEUM_VAULT_COMMAND + " " + MUSEUM_PV_NUMBER;
		if (!openContainerWithCommand(command, 6, "museum vault")) {
			return false;
		}

		int targetHotbarSlot = findOrReserveMuseumBoxHotbarSlot();
		if (targetHotbarSlot < 0) {
			closeCurrentHandledScreen();
			sendClientFeedback("Museum: no free hotbar slot for boxes (use slots 3-6).");
			return false;
		}

		boolean moved;
		try {
			moved = pullCrystalBoxesFromVault(6, targetHotbarSlot, "museum boxes");
		} finally {
			closeCurrentHandledScreen();
		}

		if (!moved) {
			sendClientFeedback("Museum: no Ender Chest stack found in /" + command + ".");
			return false;
		}
		return sleepQuietly(MUSEUM_CLICK_DELAY_MS);
	}

	private static boolean pullCrystalBoxesFromVault(int expectedRows, int hotbarSlot, String routineName) {
		Integer sourceContainerSlot = findContainerItemSlotInOpenScreen(expectedRows, Items.ENDER_CHEST);
		if (sourceContainerSlot == null) {
			sendClientFeedback("Museum: no Ender Chest stack found in vault.");
			return false;
		}

		// Method 1: QUICK_MOVE (shift-click) from container to player inventory.
		if (!clickAnySlot(
			sourceContainerSlot,
			0,
			SlotActionType.QUICK_MOVE,
			MUSEUM_CLICK_DELAY_MS,
			routineName + " quick-move",
			false
		)) {
			return false;
		}
		if (!sleepQuietly(MUSEUM_CLICK_DELAY_MS)) {
			return false;
		}
		if (findMuseumBoxHotbarSlotForUse() >= 0) {
			return true;
		}

		// Method 2: If boxes landed in main inventory, swap one stack into the requested hotbar slot.
		Integer sourcePlayerSlot = findPlayerItemSlotInOpenScreen(expectedRows, Items.ENDER_CHEST);
		if (sourcePlayerSlot == null) {
			sendClientFeedback("Museum: Ender Chest was not moved into player inventory.");
			return false;
		}

		if (!clickAnySlot(
			sourcePlayerSlot,
			hotbarSlot,
			SlotActionType.SWAP,
			MUSEUM_CLICK_DELAY_MS,
			routineName + " hotbar-swap",
			false
		)) {
			return false;
		}
		if (!sleepQuietly(MUSEUM_CLICK_DELAY_MS)) {
			return false;
		}

		if (findMuseumBoxHotbarSlotForUse() < 0) {
			sendClientFeedback("Museum: could not place Ender Chest into hotbar slot " + hotbarSlot + ".");
			return false;
		}
		return true;
	}

	private static boolean openCrystalBoxesFromHotbar() {
		int hotbarSlot = findMuseumBoxHotbarSlotForUse();
		if (hotbarSlot < 0) {
			sendClientFeedback("Museum: no Ender Chest in hotbar, continuing to next step.");
			return true;
		}

		int opened = 0;
		for (int i = 0; i < MUSEUM_BOX_OPEN_COUNT; i++) {
			if (shouldPauseMuseumAutomation()) {
				return false;
			}
			if (!hasFreePlayerInventorySlot()) {
				break;
			}
			hotbarSlot = findMuseumBoxHotbarSlotForUse();
			if (hotbarSlot < 0) {
				sendClientFeedback("Museum: no more Ender Chests in hotbar, continuing to next step.");
				break;
			}
			if (!useHotbarItem(hotbarSlot)) {
				sendClientFeedback("Museum: failed to open crystal box (" + (i + 1) + "), continuing.");
				break;
			}
			opened++;
			if (!sleepQuietly(MUSEUM_BOX_DELAY_MS)) {
				return false;
			}
		}

		if (opened == 0 && !hasItemInPlayerInventory(Items.PRISMARINE_SHARD)) {
			sendClientFeedback("Museum: no crystal boxes opened, continuing to next step.");
		}
		return true;
	}

	private static boolean runTinkerMuseumFlow() {
		if (shouldPauseMuseumAutomation()) {
			return false;
		}

		try {
			// Phase 1: open museum and place eligible crystals.
			if (!useHotbarItem(MUSEUM_HOTBAR_SLOT)) {
				sendClientFeedback("Museum: failed to open tinker item on hotbar slot 0.");
				return false;
			}
			if (!sleepQuietly(MUSEUM_BOX_DELAY_MS)) {
				return false;
			}
			if (!waitForContainerRowsInterruptible(6, GUI_OPEN_TIMEOUT_MS)) {
				sendClientFeedback("Museum: expected 9x6 tinker GUI.");
				return false;
			}
			if (!clickSingleSlot(TINKER_ENTRY_SLOT, MUSEUM_CLICK_DELAY_MS, "museum enter tinker")) {
				return false;
			}
			if (!waitForContainerRowsInterruptible(4, GUI_OPEN_TIMEOUT_MS)) {
				sendClientFeedback("Museum: expected 9x4 tinker menu.");
				return false;
			}
			if (!clickSingleSlot(TINKER_OPEN_MUSEUM_SLOT, MUSEUM_CLICK_DELAY_MS, "museum open museum")) {
				return false;
			}
			if (!waitForContainerRowsInterruptible(4, GUI_OPEN_TIMEOUT_MS)) {
				sendClientFeedback("Museum: expected 9x4 museum GUI.");
				return false;
			}

			int movedCrystals = 0;
			int stuckFailures = 0;
			int lastFailedPlayerSlot = -1;
			while (true) {
				if (shouldPauseMuseumAutomation()) {
					return false;
				}

				List<MuseumTransferPlan> plans = collectMuseumTransferPlans();
				if (plans.isEmpty()) {
					if (movedCrystals == 0 && hasItemInPlayerInventory(Items.PRISMARINE_SHARD)) {
						sendClientFeedback("Museum: no eligible crystals found (type/booster parse or below minimum).");
					}
					break;
				}

				MuseumTransferPlan plan = plans.get(0);
				if (!moveSlotToSlot(plan.playerSlot, plan.museumSlot, MUSEUM_CLICK_DELAY_MS, "museum transfer")) {
					sendClientFeedback("Museum: skipped one crystal transfer.");
					if (lastFailedPlayerSlot == plan.playerSlot) {
						stuckFailures++;
					} else {
						lastFailedPlayerSlot = plan.playerSlot;
						stuckFailures = 1;
					}
					if (stuckFailures >= 3) {
						sendClientFeedback("Museum: transfer stuck on one crystal, continuing with next steps.");
						break;
					}
					continue;
				}

				movedCrystals++;
				stuckFailures = 0;
				lastFailedPlayerSlot = -1;
			}

			// User-requested flow: close after museum phase and reopen from hotbar slot 0.
			closeCurrentHandledScreen();
			if (!waitUntilNoHandledScreen(GUI_OPEN_TIMEOUT_MS)) {
				sendClientFeedback("Museum: GUI did not close cleanly before phase 2.");
				return false;
			}
			if (!sleepQuietly(MUSEUM_REOPEN_PHASE_DELAY_MS)) {
				return false;
			}
			if (shouldPauseMuseumAutomation()) {
				return false;
			}

			// Phase 2: reopen tinker flow and open storage GUI.
			if (!openTinkerForMuseumPhaseTwo()) {
				return false;
			}
			if (!clickSingleSlot(TINKER_ENTRY_SLOT, MUSEUM_CLICK_DELAY_MS, "museum re-enter tinker")) {
				return false;
			}
			if (!waitForContainerRowsInterruptible(4, GUI_OPEN_TIMEOUT_MS)) {
				sendClientFeedback("Museum: expected 9x4 tinker menu (phase 2).");
				return false;
			}
			if (!clickSingleSlot(TINKER_OPEN_STORAGE_SLOT, MUSEUM_CLICK_DELAY_MS, "museum open storage")) {
				return false;
			}
			if (!waitForContainerRowsInterruptible(6, GUI_OPEN_TIMEOUT_MS)) {
				sendClientFeedback("Museum: expected 9x6 crystal storage.");
				return false;
			}

			// Insert only crystals below threshold. Keep eligible (above-threshold) crystals in inventory.
			if (!quickMovePlayerItemsMatching(
				6,
				stack -> stack.isOf(Items.PRISMARINE_SHARD) && !isCrystalEligibleForMuseum(stack),
				"museum move leftovers",
				MUSEUM_CLICK_DELAY_MS
			)) {
				return false;
			}

			if (!clickSingleSlot(TINKER_CONFIRM_STORAGE_SLOT, MUSEUM_CLICK_DELAY_MS, "museum storage confirm")) {
				return false;
			}
			if (!sleepQuietly(1000L)) {
				return false;
			}
			return true;
		} finally {
			closeCurrentHandledScreen();
		}
	}

	private static boolean openTinkerForMuseumPhaseTwo() {
		for (int attempt = 1; attempt <= MUSEUM_PHASE2_OPEN_ATTEMPTS; attempt++) {
			if (shouldPauseMuseumAutomation()) {
				return false;
			}

			closeCurrentHandledScreen();
			if (!waitUntilNoHandledScreen(GUI_OPEN_TIMEOUT_MS)) {
				if (attempt >= MUSEUM_PHASE2_OPEN_ATTEMPTS) {
					sendClientFeedback("Museum: GUI did not close before phase 2 retry.");
					return false;
				}
				if (!sleepQuietly(MUSEUM_RETRY_DELAY_MS)) {
					return false;
				}
				continue;
			}

			if (!sleepQuietly(MUSEUM_REOPEN_PHASE_DELAY_MS)) {
				return false;
			}
			if (!useHotbarItem(MUSEUM_HOTBAR_SLOT)) {
				if (attempt >= MUSEUM_PHASE2_OPEN_ATTEMPTS) {
					sendClientFeedback("Museum: failed to reopen tinker item on hotbar slot 0.");
					return false;
				}
				if (!sleepQuietly(MUSEUM_RETRY_DELAY_MS)) {
					return false;
				}
				continue;
			}
			if (!sleepQuietly(MUSEUM_BOX_DELAY_MS)) {
				return false;
			}
			if (waitForContainerRowsInterruptible(6, GUI_OPEN_TIMEOUT_MS)) {
				return true;
			}
			if (attempt >= MUSEUM_PHASE2_OPEN_ATTEMPTS) {
				sendClientFeedback("Museum: expected 9x6 tinker GUI (phase 2).");
				return false;
			}
			if (!sleepQuietly(MUSEUM_RETRY_DELAY_MS)) {
				return false;
			}
		}
		return false;
	}

	private static boolean isCrystalEligibleForMuseum(ItemStack stack) {
		CrystalParseResult parseResult = parseCrystalStack(stack);
		if (parseResult.type == null) {
			return false;
		}
		return parseResult.booster >= getConfiguredBoosterMin(parseResult.type);
	}

	private static List<MuseumTransferPlan> collectMuseumTransferPlans() {
		return callOnClientThread(() -> {
			List<MuseumTransferPlan> plans = new ArrayList<>();
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return plans;
			}
			int rows = getContainerRows(handler);
			if (rows != 4) {
				return plans;
			}

			int topSlots = rows * CONTAINER_COLUMNS;
			int max = Math.min(handler.slots.size(), topSlots + PLAYER_INVENTORY_SLOTS);
			for (int slot = topSlots; slot < max; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isOf(Items.PRISMARINE_SHARD) || stack.isEmpty()) {
					continue;
				}

				CrystalParseResult parseResult = parseCrystalStack(stack);
				if (parseResult.type == null) {
					continue;
				}

				if (parseResult.booster < getConfiguredBoosterMin(parseResult.type)) {
					continue;
				}

				plans.add(new MuseumTransferPlan(slot, parseResult.type.museumSlot));
			}
			return plans;
		}, List.of());
	}

	private static int getConfiguredBoosterMin(CrystalType type) {
		return switch (type) {
			case COMMON -> DEFAULT_COMMON_MIN_BOOSTER;
			case RARE -> DEFAULT_RARE_MIN_BOOSTER;
			case EPIC -> DEFAULT_EPIC_MIN_BOOSTER;
			case LEGENDARY -> DEFAULT_LEGENDARY_MIN_BOOSTER;
			case MYTHIC -> DEFAULT_MYTHIC_MIN_BOOSTER;
			case GOTHIC -> DEFAULT_GOTHIC_MIN_BOOSTER;
		};
	}

	private static CrystalType detectCrystalType(ItemStack stack) {
		return parseCrystalStack(stack).type;
	}

	private static CrystalType parseCrystalTypeToken(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return null;
		}
		return switch (rawToken.trim().toLowerCase(Locale.ROOT)) {
			case "common" -> CrystalType.COMMON;
			case "rare" -> CrystalType.RARE;
			case "epic" -> CrystalType.EPIC;
			case "legendary" -> CrystalType.LEGENDARY;
			case "mythic" -> CrystalType.MYTHIC;
			case "gothic" -> CrystalType.GOTHIC;
			default -> null;
		};
	}

	private static double extractBoosterPercent(ItemStack stack) {
		return parseCrystalStack(stack).booster;
	}

	private static CrystalParseResult parseCrystalStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return CrystalParseResult.EMPTY;
		}

		String cacheKey = buildCrystalCacheKey(stack);
		CrystalParseResult cached = CRYSTAL_PARSE_CACHE.get(cacheKey);
		if (cached != null) {
			return cached;
		}

		CrystalParseResult parsed = parseCrystalStackUncached(stack);
		if (CRYSTAL_PARSE_CACHE.size() >= CRYSTAL_PARSE_CACHE_LIMIT) {
			CRYSTAL_PARSE_CACHE.clear();
		}
		CRYSTAL_PARSE_CACHE.put(cacheKey, parsed);
		return parsed;
	}

	private static CrystalParseResult parseCrystalStackUncached(ItemStack stack) {
		String fastBlob = buildStackFastParseBlob(stack);
		CrystalType type = parseCrystalTypeFromBlob(fastBlob);
		double booster = parseBoosterFromBlob(fastBlob);

		if (type != null && booster >= 0D) {
			return new CrystalParseResult(type, booster);
		}

		String tooltipBlob = buildStackParseBlob(stack);
		if (type == null) {
			type = parseCrystalTypeFromBlob(tooltipBlob);
		}
		if (booster < 0D) {
			booster = parseBoosterFromBlob(tooltipBlob);
		}

		return new CrystalParseResult(type, booster);
	}

	private static String buildCrystalCacheKey(ItemStack stack) {
		return stack.getItem() + "|" + stack.getName().getString() + "|" + stack.getComponents();
	}

	private static CrystalType parseCrystalTypeFromBlob(String blob) {
		Matcher typeLineMatcher = CRYSTAL_TYPE_LINE_PATTERN.matcher(blob);
		if (typeLineMatcher.find()) {
			CrystalType parsed = parseCrystalTypeToken(typeLineMatcher.group(1));
			if (parsed != null) {
				return parsed;
			}
		}

		Matcher fromCrystalMatcher = FROM_CRYSTAL_TYPE_PATTERN.matcher(blob);
		if (fromCrystalMatcher.find()) {
			CrystalType parsed = parseCrystalTypeToken(fromCrystalMatcher.group(1));
			if (parsed != null) {
				return parsed;
			}
		}

		String normalizedBlob = blob.toLowerCase(Locale.ROOT);
		for (CrystalType type : CrystalType.values()) {
			if (type.matcher.matcher(normalizedBlob).find()) {
				return type;
			}
		}
		return null;
	}

	private static double parseBoosterFromBlob(String blob) {
		Matcher matcher = BOOSTER_PERCENT_PATTERN.matcher(blob);
		if (!matcher.find()) {
			return -1D;
		}
		try {
			return Double.parseDouble(matcher.group(1));
		} catch (Exception exception) {
			return -1D;
		}
	}

	private static String buildStackFastParseBlob(ItemStack stack) {
		StringBuilder builder = new StringBuilder();
		builder.append(stack.getName().getString()).append('\n');
		builder.append(stack.getComponents().toString()).append('\n');
		builder.append(stack);
		return builder.toString();
	}

	private static String buildStackParseBlob(ItemStack stack) {
		StringBuilder builder = new StringBuilder(buildStackFastParseBlob(stack));
		builder.append('\n');
		try {
			MinecraftClient client = MinecraftClient.getInstance();
			List<Text> tooltip = stack.getTooltip(
				Item.TooltipContext.DEFAULT,
				client == null ? null : client.player,
				TooltipType.BASIC
			);
			for (Text line : tooltip) {
				builder.append(line.getString()).append('\n');
			}
		} catch (Exception ignored) {
			// Fallback parsers below still run.
		}
		return builder.toString();
	}

	private static boolean quickMovePlayerItemsMatching(
		int expectedRows,
		Predicate<ItemStack> predicate,
		String routineName,
		long clickDelayMs
	) {
		long delayMs = Math.max(20L, clickDelayMs);
		List<Integer> playerSlots = callOnClientThread(() -> {
			List<Integer> slots = new ArrayList<>();
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return slots;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return slots;
			}

			int topSlots = rows * CONTAINER_COLUMNS;
			int max = Math.min(handler.slots.size(), topSlots + PLAYER_INVENTORY_SLOTS);
			for (int slot = topSlots; slot < max; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isEmpty() && predicate.test(stack)) {
					slots.add(slot);
				}
			}
			return slots;
		}, List.of());

		for (int slot : playerSlots) {
			if (!clickAnySlot(slot, 0, SlotActionType.QUICK_MOVE, delayMs, routineName, true)) {
				return false;
			}
		}
		return true;
	}

	private static Integer findContainerItemSlotInOpenScreen(int expectedRows, Item item) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return null;
			}

			int topSlots = rows * CONTAINER_COLUMNS;
			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isEmpty() && stack.isOf(item)) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static Integer findPlayerItemSlotInOpenScreen(int expectedRows, Item item) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int rows = getContainerRows(handler);
			if (rows != expectedRows) {
				return null;
			}

			int topSlots = rows * CONTAINER_COLUMNS;
			int max = Math.min(handler.slots.size(), topSlots + PLAYER_INVENTORY_SLOTS);
			for (int slot = topSlots; slot < max; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isEmpty() && stack.isOf(item)) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static boolean moveSlotToSlot(int fromSlot, int toSlot, long delayMs, String routineName) {
		if (!clickAnySlot(fromSlot, 0, SlotActionType.PICKUP, delayMs, routineName, false)) {
			return false;
		}
		// Crystals are single items on this server. One target click should consume the carried crystal.
		if (!clickAnySlot(toSlot, 0, SlotActionType.PICKUP, delayMs, routineName, false)) {
			return false;
		}

		int cursorCount = getCursorStackCount();
		if (cursorCount == 0) {
			return true;
		}
		if (cursorCount < 0) {
			return false;
		}

		sendClientFeedback("Museum transfer did not consume crystal on slot " + toSlot + ".");
		// Best effort: place the remaining stack back to source to avoid blocking next GUI clicks.
		clickAnySlot(fromSlot, 0, SlotActionType.PICKUP, delayMs, routineName, true);
		return false;
	}

	private static int getCursorStackCount() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return -1;
			}
			ItemStack cursor = handler.getCursorStack();
			return cursor.isEmpty() ? 0 : cursor.getCount();
		}, -1);
	}

	private static boolean hasItemInPlayerInventory(Item item) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return false;
			}
			for (int slot = 0; slot < PLAYER_INVENTORY_SLOTS; slot++) {
				if (client.player.getInventory().getStack(slot).isOf(item)) {
					return true;
				}
			}
			return false;
		}, false);
	}

	private static boolean hasFreePlayerInventorySlot() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return false;
			}
			for (int slot = 0; slot < PLAYER_INVENTORY_SLOTS; slot++) {
				if (client.player.getInventory().getStack(slot).isEmpty()) {
					return true;
				}
			}
			return false;
		}, false);
	}

	private static int findHotbarItemSlot(Item item) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return -1;
			}
			for (int slot = 0; slot < 9; slot++) {
				if (client.player.getInventory().getStack(slot).isOf(item)) {
					return slot;
				}
			}
			return -1;
		}, -1);
	}

	private static int findOrReserveMuseumBoxHotbarSlot() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return -1;
			}

			// Reuse an existing Ender Chest slot in the preferred range first.
			for (int slot : MUSEUM_BOX_HOTBAR_PREFERRED_SLOTS) {
				if (client.player.getInventory().getStack(slot).isOf(Items.ENDER_CHEST)) {
					return slot;
				}
			}

			// Otherwise only use empty preferred slots (3-6), never 0/1/2/7/8.
			for (int slot : MUSEUM_BOX_HOTBAR_PREFERRED_SLOTS) {
				if (client.player.getInventory().getStack(slot).isEmpty()) {
					return slot;
				}
			}

			return -1;
		}, -1);
	}

	private static int findMuseumBoxHotbarSlotForUse() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return -1;
			}

			for (int slot : MUSEUM_BOX_HOTBAR_PREFERRED_SLOTS) {
				if (client.player.getInventory().getStack(slot).isOf(Items.ENDER_CHEST)) {
					return slot;
				}
			}

			// Fallback for legacy states where boxes might already be on another slot.
			for (int slot = 0; slot < 9; slot++) {
				if (client.player.getInventory().getStack(slot).isOf(Items.ENDER_CHEST)) {
					return slot;
				}
			}
			return -1;
		}, -1);
	}

	private static boolean useHotbarItemOnBlock(int hotbarSlot) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.interactionManager == null || client.getNetworkHandler() == null) {
				return false;
			}
			if (client.currentScreen != null) {
				return false;
			}
			if (!(client.crosshairTarget instanceof BlockHitResult blockHitResult)
				|| client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
				return false;
			}

			client.player.getInventory().setSelectedSlot(hotbarSlot);
			client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHitResult);
			return true;
		}, false);
	}

	private static boolean isSixRowContainerOpen() {
		return CACHED_OPEN_CONTAINER_ROWS.get() == 6;
	}

	private static boolean hasFishingBobber() {
		return CACHED_HAS_FISHING_BOBBER.get();
	}

	private static boolean useHotbarItem(int hotbarSlot) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.interactionManager == null || client.getNetworkHandler() == null) {
				return false;
			}
			if (client.currentScreen != null) {
				return false;
			}

			client.player.getInventory().setSelectedSlot(hotbarSlot);
			client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
			return true;
		}, false);
	}

	private static int toZeroBasedSlot(int oneBasedRow, int oneBasedColumn) {
		return (oneBasedRow - 1) * CONTAINER_COLUMNS + (oneBasedColumn - 1);
	}

	private static boolean openContainerWithCommand(String command, int expectedRows, String routineName) {
		if (!sendChatCommand(command)) {
			sendClientFeedback("failed to send /" + command);
			return false;
		}

		if (!sleepQuietly(COMMAND_TO_GUI_DELAY_MS)) {
			return false;
		}

		if (!waitForContainerRows(expectedRows, GUI_OPEN_TIMEOUT_MS)) {
			sendClientFeedback(routineName + " expected " + expectedRows + "x9 gui");
			return false;
		}

		return true;
	}

	private static boolean openContainerWithCommandAnyRows(String command, String routineName) {
		if (!sendChatCommand(command)) {
			sendClientFeedback("failed to send /" + command);
			return false;
		}

		if (!sleepQuietly(COMMAND_TO_GUI_DELAY_MS)) {
			return false;
		}

		if (!waitForAnyContainerOpen(GUI_OPEN_TIMEOUT_MS)) {
			sendClientFeedback(routineName + " expected gui");
			return false;
		}

		return true;
	}

	private static boolean waitForContainerRows(int expectedRows, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (CACHED_OPEN_CONTAINER_ROWS.get() == expectedRows) {
				return true;
			}

			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}

		return false;
	}

	private static boolean waitForAnyContainerOpen(long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (CACHED_IS_HANDLED_SCREEN.get()) {
				return true;
			}

			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}

		return false;
	}

	private static Integer findTopContainerItemSlotInOpenScreen(Item item) {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			int rows = getContainerRows(handler);
			int topSlots = rows > 0
				? rows * CONTAINER_COLUMNS
				: handler.slots.size() - PLAYER_INVENTORY_SLOTS;
			topSlots = Math.min(topSlots, handler.slots.size());
			if (topSlots <= 0) {
				return null;
			}

			for (int slot = 0; slot < topSlots; slot++) {
				ItemStack stack = handler.getSlot(slot).getStack();
				if (!stack.isEmpty() && stack.isOf(item)) {
					return slot;
				}
			}
			return null;
		}, null);
	}

	private static boolean waitForContainerRowsInterruptible(int expectedRows, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (LOBBY_FAILSAFE_ACTIVE.get() || isEventRoutineBusy()) {
				return false;
			}
			if (CACHED_OPEN_CONTAINER_ROWS.get() == expectedRows) {
				return true;
			}

			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}

		return false;
	}

	private static boolean waitUntilNoHandledScreen(long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (shouldPauseMuseumAutomation()) {
				return false;
			}
			if (!CACHED_IS_HANDLED_SCREEN.get()) {
				return true;
			}

			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}
		return false;
	}

	private static boolean waitForContainerReload(int previousSyncId, int expectedRows, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (CACHED_OPEN_CONTAINER_ROWS.get() == expectedRows && CACHED_OPEN_SCREEN_SYNC_ID.get() != previousSyncId) {
				return true;
			}

			if (!sleepQuietly(GUI_POLL_DELAY_MS)) {
				return false;
			}
		}
		return false;
	}

	private static Integer getCurrentHandledScreenSyncId() {
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return null;
			}
			return handler.syncId;
		}, null);
	}

	private static boolean clickSlotsWithRepeat(int[] slots, int clicksPerSlot, long delayMs, String routineName) {
		for (int slot : slots) {
			for (int click = 0; click < clicksPerSlot; click++) {
				if (!clickSingleSlot(slot, delayMs, routineName)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean clickSingleSlot(int slot, long delayMs, String routineName) {
		return clickSingleSlot(slot, delayMs, routineName, false);
	}

	private static boolean clickSingleSlot(int slot, long delayMs, String routineName, boolean silentFailure) {
		boolean clicked = callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.interactionManager == null) {
				return false;
			}
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return false;
			}
			int rows = getContainerRows(handler);
			if (rows <= 0) {
				return false;
			}

			int containerSlots = rows * CONTAINER_COLUMNS;
			if (slot < 0 || slot >= containerSlots) {
				return false;
			}

			client.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, client.player);
			return true;
		}, false);

		if (!clicked) {
			if (!silentFailure) {
				sendClientFeedback(routineName + " failed on slot " + slot);
			}
			return false;
		}

		return sleepQuietly(delayMs);
	}

	private static boolean clickAnySlot(
		int slot,
		int button,
		SlotActionType actionType,
		long delayMs,
		String routineName,
		boolean silentFailure
	) {
		boolean clicked = callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.interactionManager == null) {
				return false;
			}
			ScreenHandler handler = getActiveHandledScreenHandler(client);
			if (handler == null) {
				return false;
			}
			if (slot < 0 || slot >= handler.slots.size()) {
				return false;
			}

			client.interactionManager.clickSlot(handler.syncId, slot, button, actionType, client.player);
			return true;
		}, false);

		if (!clicked) {
			if (!silentFailure) {
				sendClientFeedback(routineName + " failed on slot " + slot);
			}
			return false;
		}
		return sleepQuietly(delayMs);
	}

	private static int getContainerRows(ScreenHandler handler) {
		if (handler instanceof GenericContainerScreenHandler genericContainer) {
			return genericContainer.getRows();
		}

		int topSlots = handler.slots.size() - PLAYER_INVENTORY_SLOTS;
		if (topSlots > 0 && topSlots % CONTAINER_COLUMNS == 0) {
			return topSlots / CONTAINER_COLUMNS;
		}

		return -1;
	}

	private static ScreenHandler getActiveHandledScreenHandler(MinecraftClient client) {
		if (client == null || client.player == null) {
			return null;
		}
		if (client.currentScreen instanceof HandledScreen<?> handledScreen) {
			return handledScreen.getScreenHandler();
		}
		if (!SALES_SILENT_MODE.get()) {
			return null;
		}
		ScreenHandler handler = client.player.currentScreenHandler;
		if (handler == null || handler == client.player.playerScreenHandler) {
			return null;
		}
		return handler;
	}

	private static boolean sendChatCommand(String command) {
		String normalized = command.startsWith("/") ? command.substring(1) : command;
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
			if (networkHandler == null || client.player == null) {
				return false;
			}

			networkHandler.sendChatCommand(normalized);
			return true;
		}, false);
	}

	private static boolean sendChatMessage(String message) {
		String normalized = message == null ? "" : message.trim();
		if (normalized.isEmpty()) {
			return false;
		}
		return callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
			if (networkHandler == null || client.player == null) {
				return false;
			}

			networkHandler.sendChatMessage(normalized);
			return true;
		}, false);
	}

	private static void closeCurrentHandledScreen() {
		callOnClientThread(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null && getActiveHandledScreenHandler(client) != null) {
				client.player.closeHandledScreen();
			}
			return null;
		}, null);
	}

	private static boolean sendWebhookPing(String content) {
		return sendWebhookPingToUrl(WEBHOOK_URL, content, true);
	}

	static boolean sendWebhookPingToUrl(String rawWebhookUrl, String content, boolean notifyInChatOnError) {
		String webhookUrl = resolveWebhookUrl(rawWebhookUrl);

		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setDoOutput(true);

			String payload = "{\"content\":\"" + escapeJson(content) + "\"}";
			try (OutputStream outputStream = connection.getOutputStream()) {
				outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
			}

			int response = connection.getResponseCode();
			if (response < 200 || response >= 300) {
				LOGGER.warn("Discord webhook responded with {}", response);
				if (notifyInChatOnError) {
					sendClientFeedback("Webhook send failed (HTTP " + response + ").");
				}
				return false;
			}
			return true;
		} catch (Exception exception) {
			LOGGER.error("Webhook request failed", exception);
			if (notifyInChatOnError) {
				sendClientFeedback("Webhook send failed.");
			}
			return false;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private static String escapeJson(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"");
	}

	private static boolean sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	

	

	private static Text salesMessage(String message) {
		return Text.literal(CHAT_PREFIX + message);
	}

	private static void sendSourceFeedback(FabricClientCommandSource source, String message) {
		source.sendFeedback(salesMessage(message));
	}

	static void sendClientFeedback(String message) {
		sendClientFeedback(salesMessage(message));
	}

	static void sendClientFeedback(Text message) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				client.player.sendMessage(message, false);
			}
		});
	}

	private static <T> T callOnClientThread(Callable<T> task, T fallback) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return fallback;
		}
		if (client.isOnThread()) {
			try {
				return task.call();
			} catch (Exception exception) {
				LOGGER.error("Client thread task failed", exception);
				return fallback;
			}
		}

		CompletableFuture<T> future = new CompletableFuture<>();
		client.execute(() -> {
			try {
				future.complete(task.call());
			} catch (Exception exception) {
				future.completeExceptionally(exception);
			}
		});

		try {
			return future.get(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return fallback;
		} catch (TimeoutException exception) {
			return fallback;
		} catch (ExecutionException exception) {
			LOGGER.error("Client thread task failed", exception);
			return fallback;
		}
	}
	// UI screens are defined in SalesDashboardScreen.java

	private static final class MuseumTransferPlan {
		private final int playerSlot;
		private final int museumSlot;

		private MuseumTransferPlan(int playerSlot, int museumSlot) {
			this.playerSlot = playerSlot;
			this.museumSlot = museumSlot;
		}
	}

	private static final class CrystalParseResult {
		private static final CrystalParseResult EMPTY = new CrystalParseResult(null, -1D);

		private final CrystalType type;
		private final double booster;

		private CrystalParseResult(CrystalType type, double booster) {
			this.type = type;
			this.booster = booster;
		}
	}

	private enum CrystalType {
		COMMON("common", 2),
		RARE("rare", 3),
		EPIC("epic", 4),
		LEGENDARY("legendary", 5),
		MYTHIC("mythic", 6),
		GOTHIC("gothic", 11);

		private final Pattern matcher;
		private final int museumSlot;

		CrystalType(String keyword, int museumSlot) {
			this.matcher = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE);
			this.museumSlot = museumSlot;
		}
	}

}






