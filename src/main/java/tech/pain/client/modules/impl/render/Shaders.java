package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.shaders.ShaderBootstrap;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Шейдеры прямо из меню клиента. Включение модуля включает во встроенном Iris пак Pain Shaders,
 * выключение — отключает шейдеры. Настройки модуля записываются в файл настроек пака, и Iris его перечитывает.
 * Iris — отдельный мод, поэтому обращаемся к нему через рефлексию.
 */
@ModuleAnnotation(
   name = "Shaders",
   category = Category.RENDER,
   description = "Встроенные шейдеры Pain Shaders: тени, отражения, свечение, размытие в движении"
)
public final class Shaders extends Module {
   public static final Shaders INSTANCE = new Shaders();
   private final ModeSetting pack = new ModeSetting("Шейдер", "Pain Shaders");
   private final ModeSetting profile = new ModeSetting("Качество", "Pain", "Быстро", "Красиво", "Максимум");
   private final BooleanSetting shadows = new BooleanSetting("Тени от солнца", false);
   private final BooleanSetting bloom = new BooleanSetting("Свечение", true);
   private final BooleanSetting motionBlur = new BooleanSetting("Размытие в движении", true);
   private final NumberSetting motionBlurStrength = new NumberSetting("Сила размытия", 0.5F, 0.1F, 1.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.motionBlur.isEnabled());
   private String applied = "";
   private long changedAt;
   private String pending = "";

   private Shaders() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.apply(true);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      // если модуль в этом запуске не включали, шейдеры не трогаем: их могли включить в самом Iris
      if (!this.applied.isEmpty()) {
         this.setIris(false);
      }

      this.applied = "";
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      // настройку могли покрутить несколько раз подряд — перезагружаем шейдеры один раз, когда она устоялась
      String now = this.options();
      if (!now.equals(this.pending)) {
         this.pending = now;
         this.changedAt = System.currentTimeMillis();
      } else if (!now.equals(this.applied) && System.currentTimeMillis() - this.changedAt > 700L) {
         this.apply(false);
      }
   }

   /** Настройки пака в том виде, в каком Iris хранит их в файле «имя пака.txt». */
   private String options() {
      StringBuilder sb = new StringBuilder();
      boolean fast = this.profile.is("Быстро");
      boolean max = this.profile.is("Максимум");
      boolean fancy = this.profile.is("Красиво") || max;
      sb.append("BLOOM=").append(this.bloom.isEnabled() && !fast).append('\n');
      sb.append("CLOUD_STYLE=").append(fast ? 0 : 1).append('\n');
      sb.append("WATER_NORMALS=").append(fancy).append('\n');
      sb.append("REFLECTIONS=").append(fast ? 0 : max ? 2 : fancy ? 3 : 1).append('\n');
      sb.append("TAA_MODE=").append(max ? 1 : 0).append('\n');
      sb.append("SMAA=").append(max).append('\n');
      sb.append("GODRAYS=").append(max).append('\n');
      sb.append("SSAO=").append(max).append('\n');
      sb.append("USE_LQ_DEPTH=").append(max).append('\n');
      sb.append("IMAGE_SHARPENING=").append(max).append('\n');
      sb.append("DYNAMIC_SHADOWS=").append(this.shadows.isEnabled()).append('\n');
      // блеск из Weather: под шейдерами это лужи с отражениями, чем сильнее — тем их больше
      float shine = WeatherControl.INSTANCE.shineLevel();
      sb.append("PUDDLES=").append(!fast).append('\n');
      sb.append("PUDDLE_COVERAGE=").append(String.format(java.util.Locale.ROOT, "%.2f", shine > 0.0F ? 0.35F + 0.45F * shine : 0.45F)).append('\n');
      if (shine > 0.0F && !fast) {
         // лужам нужно хоть какое-то отражение
         sb.append("REFLECT_SUN=true\n");
      }

      sb.append("MOTION_BLUR=").append(this.motionBlur.isEnabled()).append('\n');
      sb.append("MOTION_BLUR_STRENGTH=").append(String.format(java.util.Locale.ROOT, "%.1f", this.motionBlurStrength.getCurrent())).append('\n');
      return sb.toString();
   }

   private void apply(boolean force) {
      String now = this.options();
      if (!force && now.equals(this.applied)) {
         return;
      }

      try {
         Path file = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").resolve(ShaderBootstrap.PACK + ".txt");
         Files.createDirectories(file.getParent());
         Files.writeString(file, now, StandardCharsets.UTF_8);
      } catch (Exception e) {
         System.err.println("[Pain] Не удалось записать настройки шейдеров: " + e);
      }

      this.applied = now;
      this.pending = now;
      this.setIris(true);
   }

   private void setIris(boolean enabled) {
      if (!FabricLoader.getInstance().isModLoaded("iris")) {
         return;
      }

      try {
         Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
         Object config = iris.getMethod("getIrisConfig").invoke(null);
         config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, enabled);
         if (enabled) {
            config.getClass().getMethod("setShaderPackName", String.class).invoke(config, ShaderBootstrap.PACK);
         }

         config.getClass().getMethod("save").invoke(config);
         iris.getMethod("reload").invoke(null);
      } catch (Throwable t) {
         Throwable cause = t.getCause() == null ? t : t.getCause();
         System.err.println("[Pain] Iris не переключился: " + cause);
      }
   }
}
