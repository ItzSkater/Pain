package tech.pain.base.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.mcpfabric.McpFabric;
import dev.mcpfabric.bridge.MainThread;
import dev.mcpfabric.bridge.RpcContext;
import dev.mcpfabric.bridge.RpcException;
import dev.mcpfabric.bridge.RpcRouter;
import dev.mcpfabric.bridge.ThrowingSupplier;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import tech.pain.Pain;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.setting.Setting;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.KeySetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.api.setting.impl.StringSetting;

/**
 * Команды Pain для MCP-моста (вложенный мод mcpfabric, MIT): список модулей, включение,
 * настройки, ClickGUI. Регистрируются в общий роутер моста как методы pain.*.
 */
public final class PainMcp {
   private static boolean registered;

   private PainMcp() {
   }

   /** Мост стартует в своём entrypoint — ждём его роутер в фоне и регистрируемся один раз. */
   public static void init() {
      if (!FabricLoader.getInstance().isModLoaded("mcpfabric")) {
         return;
      }

      Thread waiter = new Thread(() -> {
         for (int i = 0; i < 120 && !registered; ++i) {
            try {
               RpcRouter router = McpFabric.router();
               if (router != null) {
                  register(router);
                  return;
               }

               Thread.sleep(500L);
            } catch (Throwable t) {
               return;
            }
         }
      }, "Pain-MCP");
      waiter.setDaemon(true);
      waiter.start();
   }

   private static synchronized void register(RpcRouter router) {
      if (registered) {
         return;
      }

      registered = true;
      router.register("pain.info", ctx -> call(PainMcp::info));
      router.register("pain.modules.list", ctx -> call(() -> listModules(ctx)));
      router.register("pain.module.get", ctx -> call(() -> describe(module(ctx.getString("name")), true)));
      router.register("pain.module.set", ctx -> call(() -> setModule(ctx)));
      router.register("pain.setting.set", ctx -> call(() -> setSetting(ctx)));
      router.register("pain.gui.open", ctx -> call(() -> openGui(ctx)));
      router.register("pain.shaders", ctx -> call(() -> shaders(ctx)));
   }

   private static <T> T call(ThrowingSupplier<T> task) throws RpcException {
      return MainThread.call(MinecraftClient.getInstance(), 8000L, task);
   }

   private static JsonElement info() {
      MinecraftClient mc = MinecraftClient.getInstance();
      JsonObject o = new JsonObject();
      o.addProperty("client", "Pain");
      o.addProperty("version", FabricLoader.getInstance().getModContainer("pain").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
      int enabled = 0;

      for (Module m : Pain.getInstance().getModuleManager().getModules()) {
         if (m.isEnabled()) {
            ++enabled;
         }
      }

      o.addProperty("modules", Pain.getInstance().getModuleManager().getModules().size());
      o.addProperty("enabled", enabled);
      o.addProperty("screen", mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
      o.addProperty("inWorld", mc.world != null);
      o.addProperty("fps", mc.getCurrentFps());
      return o;
   }

   /**
    * Шейдеры Iris: включить, выключить, сменить пак, перечитать настройки. Iris — необязательный мод,
    * поэтому всё через рефлексию. Параметры строками: enabled ("true"/"false"), pack, reload ("true").
    */
   private static JsonElement shaders(RpcContext ctx) throws RpcException {
      JsonObject o = new JsonObject();
      if (!FabricLoader.getInstance().isModLoaded("iris")) {
         o.addProperty("iris", false);
         return o;
      }

      try {
         Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
         Object config = iris.getMethod("getIrisConfig").invoke(null);
         String enabled = ctx.optString("enabled", "");
         String pack = ctx.optString("pack", "");
         boolean changed = !enabled.isEmpty() || !pack.isEmpty();
         if (!enabled.isEmpty()) {
            config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, Boolean.parseBoolean(enabled));
         }

         if (!pack.isEmpty()) {
            config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack);
         }

         if (changed) {
            config.getClass().getMethod("save").invoke(config);
         }

         if (changed || "true".equals(ctx.optString("reload", ""))) {
            iris.getMethod("reload").invoke(null);
         }

         o.addProperty("iris", true);
         o.addProperty("enabled", (Boolean)config.getClass().getMethod("areShadersEnabled").invoke(config));
         o.addProperty("pack", String.valueOf(iris.getMethod("getCurrentPackName").invoke(null)));
         o.addProperty("fps", MinecraftClient.getInstance().getCurrentFps());
      } catch (ReflectiveOperationException e) {
         Throwable cause = e.getCause() == null ? e : e.getCause();
         o.addProperty("error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
      }

      return o;
   }

   private static JsonElement listModules(RpcContext ctx) throws RpcException {
      String category = ctx.optString("category", "").toLowerCase(Locale.ROOT);
      String query = ctx.optString("query", "").toLowerCase(Locale.ROOT);
      boolean enabledOnly = ctx.optBool("enabledOnly", false);
      JsonArray list = new JsonArray();

      for (Module m : Pain.getInstance().getModuleManager().getModules()) {
         if (enabledOnly && !m.isEnabled()) {
            continue;
         }

         if (!category.isEmpty() && !m.getCategory().name().toLowerCase(Locale.ROOT).equals(category)) {
            continue;
         }

         if (!query.isEmpty() && !m.getName().toLowerCase(Locale.ROOT).contains(query) && !m.getInfo().description().toLowerCase(Locale.ROOT).contains(query)) {
            continue;
         }

         list.add(describe(m, false));
      }

      JsonObject o = new JsonObject();
      o.addProperty("count", list.size());
      o.add("modules", list);
      return o;
   }

   private static JsonObject describe(Module m, boolean withSettings) {
      JsonObject o = new JsonObject();
      o.addProperty("name", m.getName());
      o.addProperty("category", m.getCategory().name());
      o.addProperty("enabled", m.isEnabled());
      o.addProperty("bind", m.getKeyCode());
      o.addProperty("description", m.getInfo().description());
      if (withSettings) {
         JsonArray settings = new JsonArray();

         for (Setting s : m.getSettings()) {
            JsonObject j = setting(s);
            if (j != null) {
               settings.add(j);
            }
         }

         o.add("settings", settings);
      }

      return o;
   }

   private static JsonObject setting(Setting s) {
      JsonObject o = new JsonObject();
      o.addProperty("name", s.getName());
      o.addProperty("visible", s.isVisible());
      if (s instanceof BooleanSetting b) {
         o.addProperty("type", "boolean");
         o.addProperty("value", b.isEnabled());
      } else if (s instanceof NumberSetting n) {
         o.addProperty("type", "number");
         o.addProperty("value", n.getCurrent());
         o.addProperty("min", n.getMin());
         o.addProperty("max", n.getMax());
         o.addProperty("step", n.getIncrement());
      } else if (s instanceof ModeSetting mode) {
         o.addProperty("type", "mode");
         o.addProperty("value", mode.get());
         JsonArray options = new JsonArray();
         mode.getValues().forEach(v -> options.add(v.getName()));
         o.add("options", options);
      } else if (s instanceof MultiBooleanSetting multi) {
         o.addProperty("type", "multi");
         JsonObject values = new JsonObject();
         multi.getBooleanSettings().forEach(v -> values.addProperty(v.getName(), v.isEnabled()));
         o.add("value", values);
      } else if (s instanceof StringSetting str) {
         o.addProperty("type", "string");
         o.addProperty("value", str.get());
         o.addProperty("maxLength", str.getMaxLength());
      } else if (s instanceof KeySetting key) {
         o.addProperty("type", "key");
         o.addProperty("value", key.getKeyCode());
      } else if (s instanceof ColorSetting color) {
         o.addProperty("type", "color");
         o.addProperty("value", String.format("#%08X", color.getIntColor()));
      } else {
         return null;
      }

      return o;
   }

   private static Module module(String name) throws RpcException {
      for (Module m : Pain.getInstance().getModuleManager().getModules()) {
         if (m.getName().equalsIgnoreCase(name)) {
            return m;
         }
      }

      throw RpcException.notFound("Нет модуля: " + name);
   }

   private static JsonElement setModule(RpcContext ctx) throws RpcException {
      Module m = module(ctx.getString("name"));
      Boolean enabled = ctx.optBoolean("enabled");
      if (enabled != null && enabled != m.isEnabled()) {
         m.toggle();
      }

      if (ctx.has("bind")) {
         m.setKeyCode(ctx.getInt("bind"));
      }

      return describe(m, false);
   }

   private static JsonElement setSetting(RpcContext ctx) throws RpcException {
      Module m = module(ctx.getString("module"));
      String name = ctx.getString("setting");
      Setting found = null;

      for (Setting s : m.getSettings()) {
         if (s.getName().equalsIgnoreCase(name)) {
            found = s;
            break;
         }
      }

      if (found == null) {
         throw RpcException.notFound("У модуля " + m.getName() + " нет настройки: " + name);
      }

      if (!ctx.has("value")) {
         throw RpcException.badRequest("Нужен параметр value.");
      }

      JsonElement value = ctx.params().get("value");

      try {
         if (found instanceof BooleanSetting b) {
            b.setEnabled(value.getAsBoolean());
         } else if (found instanceof NumberSetting n) {
            n.setCurrent(Math.max(n.getMin(), Math.min(n.getMax(), value.getAsFloat())));
         } else if (found instanceof ModeSetting mode) {
            String wanted = value.getAsString();
            ModeSetting.Value pick = mode.getValues().stream().filter(v -> v.getName().equalsIgnoreCase(wanted)).findFirst()
               .orElseThrow(() -> RpcException.badRequest("Нет варианта: " + wanted));
            mode.set(pick.getName());
         } else if (found instanceof MultiBooleanSetting multi) {
            String option = ctx.getString("option");
            MultiBooleanSetting.Value pick = multi.getBooleanSettings().stream().filter(v -> v.getName().equalsIgnoreCase(option)).findFirst()
               .orElseThrow(() -> RpcException.badRequest("Нет пункта: " + option));
            pick.setEnabled(value.getAsBoolean());
         } else if (found instanceof StringSetting str) {
            str.set(value.getAsString());
         } else if (found instanceof KeySetting key) {
            key.setKeyCode(value.getAsInt());
         } else if (found instanceof ColorSetting color) {
            color.setColor((int)Long.parseLong(value.getAsString().replace("#", ""), 16));
         } else {
            throw RpcException.badRequest("Этот тип настройки нельзя менять через мост.");
         }
      } catch (RpcException e) {
         throw e;
      } catch (RuntimeException e) {
         throw RpcException.badRequest("Неверное значение: " + e.getMessage());
      }

      return setting(found);
   }

   private static JsonElement openGui(RpcContext ctx) throws RpcException {
      MinecraftClient mc = MinecraftClient.getInstance();
      String screen = ctx.optString("screen", "clickgui").toLowerCase(Locale.ROOT);
      Module menu = tech.pain.client.modules.impl.render.Menu.INSTANCE;
      boolean open = !screen.equals("none") && !screen.equals("close");
      if (open && mc.world == null) {
         throw RpcException.unavailable("ClickGUI открывается только в мире.");
      }

      if (menu.isEnabled() != open) {
         menu.toggle();
      } else if (!open && mc.currentScreen != null) {
         mc.setScreen(null);
      }

      JsonObject o = new JsonObject();
      o.addProperty("screen", mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
      return o;
   }
}
