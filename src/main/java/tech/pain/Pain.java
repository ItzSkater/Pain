package tech.pain;

import java.io.File;
import lombok.Generated;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import tech.pain.base.autobuy.AutoBuyManager;
import tech.pain.base.comand.CommandManager;
import tech.pain.base.config.ConfigManager;
import tech.pain.base.discord.DiscordManager;
import tech.pain.base.filemanager.impl.FriendManager;
import tech.pain.base.filemanager.impl.StaffManager;
import tech.pain.base.macro.MacroManager;
import tech.pain.base.modules.ModuleManager;
import tech.pain.base.notify.NotifyManager;
import tech.pain.base.repository.RCTRepository;
import tech.pain.base.request.ScriptManager;
import tech.pain.base.theme.ThemeManager;
import tech.pain.base.waypoint.WaypointManager;
import tech.pain.client.screens.menu.MenuScreen;
import tech.pain.utility.game.server.ServerHandler;
import tech.pain.utility.render.display.shader.DrawUtil;
import tech.pain.utility.render.display.shader.GlProgram;

public enum Pain implements ClientModInitializer {
   INSTANCE;

   public static final String NAME = "Pain";
   public static final String VER = "";
   public static final String TYPE = "DEV";
   private static final String MOD_ID = "Pain".toLowerCase();
   public static File DIRECTORY;
   private ModuleManager moduleManager;
   private ThemeManager themeManager;
   private MenuScreen menuScreen;
   private MenuScreen rockMenuScreen;
   private ScriptManager scriptManager;
   private ServerHandler serverHandler;
   private FriendManager friendManager;
   private MacroManager macroManager;
   private StaffManager staffManager;
   private AutoBuyManager autoBuyManager;
   private WaypointManager waypointManager;
   private NotifyManager notifyManager;
   private CommandManager commandManager;
   private ConfigManager configManager;
   private RCTRepository rctRepository;
   private DiscordManager discordManager;
   private boolean initialized = false;

   @Override
   public void onInitializeClient() {
      try {
         init();
      } catch (Exception e) {
         e.printStackTrace();
         throw e;
      }
   }

   public void init() {
      if (initialized) {
         return;
      }
      initialized = true;
      
      try {
         DIRECTORY = new File(MinecraftClient.getInstance().runDirectory, "Pain");
         if (!DIRECTORY.exists()) {
            DIRECTORY.mkdirs();
         }
         
         Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            getInstance().shutdown();
         }));
         
         this.friendManager = new FriendManager();
         this.macroManager = new MacroManager();
         this.staffManager = new StaffManager();
         this.notifyManager = new NotifyManager();
         this.serverHandler = new ServerHandler();
         this.rctRepository = new RCTRepository();
         this.themeManager = new ThemeManager();
         this.moduleManager = new ModuleManager();
         this.configManager = new ConfigManager();
         this.autoBuyManager = new AutoBuyManager();
         this.commandManager = new CommandManager();
         this.scriptManager = new ScriptManager();
         try {
            this.discordManager = new DiscordManager();
         } catch (Throwable e) {
            this.discordManager = null;
         }
         this.waypointManager = new WaypointManager();
         this.menuScreen = new MenuScreen();
         this.rockMenuScreen = new tech.pain.client.screens.menu.rock.RockMenuScreen();
         ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
               return Pain.id("after_shader_load");
            }

            @Override
            public void reload(ResourceManager manager) {
               GlProgram.loadAndSetupPrograms();
            }
         });
         DrawUtil.initializeShaders();
      } catch (Exception e) {
         e.printStackTrace();
         throw new RuntimeException("Pain initialization failed", e);
      }
   }

   public void shutdown() {
      this.friendManager.save();
      this.staffManager.save();
      this.configManager.save();
      this.macroManager.save();
      if (this.discordManager != null) {
         this.discordManager.stopRPC();
      }

   }

   public static Identifier id(String path) {
      return Identifier.of("pain", path);
   }

   public static Pain getInstance() {
      return INSTANCE;
   }

   public RCTRepository getRCTRepository() {
      return this.rctRepository;
   }

   @Generated
   public ModuleManager getModuleManager() {
      return this.moduleManager;
   }

   @Generated
   public ThemeManager getThemeManager() {
      return this.themeManager;
   }

   @Generated
   public MenuScreen getMenuScreen() {
      if (this.rockMenuScreen != null && !tech.pain.client.modules.impl.render.Menu.INSTANCE.style.is("Classic")) {
         return this.rockMenuScreen;
      }

      return this.menuScreen;
   }

   @Generated
   public ScriptManager getScriptManager() {
      return this.scriptManager;
   }

   @Generated
   public ServerHandler getServerHandler() {
      return this.serverHandler;
   }

   @Generated
   public FriendManager getFriendManager() {
      return this.friendManager;
   }

   @Generated
   public MacroManager getMacroManager() {
      return this.macroManager;
   }

   @Generated
   public StaffManager getStaffManager() {
      return this.staffManager;
   }

   @Generated
   public AutoBuyManager getAutoBuyManager() {
      return this.autoBuyManager;
   }

   @Generated
   public WaypointManager getWaypointManager() {
      return this.waypointManager;
   }

   @Generated
   public NotifyManager getNotifyManager() {
      return this.notifyManager;
   }

   @Generated
   public CommandManager getCommandManager() {
      return this.commandManager;
   }

   @Generated
   public ConfigManager getConfigManager() {
      return this.configManager;
   }

   @Generated
   public DiscordManager getDiscordManager() {
      return this.discordManager;
   }


   private static Pain[] $values() {
      return new Pain[]{INSTANCE};
   }
}
