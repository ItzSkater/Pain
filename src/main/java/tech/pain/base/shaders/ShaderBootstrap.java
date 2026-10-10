package tech.pain.base.shaders;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Кладёт встроенный шейдерпак Pain Shaders в папку shaderpacks и, если Iris ещё ни разу не настраивали,
 * выбирает его по умолчанию. Работает до запуска игры, раньше самого Iris — он читает свой конфиг при старте.
 * Уже существующий конфиг Iris не трогаем: выбор игрока важнее.
 */
public final class ShaderBootstrap implements PreLaunchEntrypoint {
   public static final String PACK = "Pain Shaders.zip";
   private static final String RESOURCE = "/assets/pain/shaderpack/PainShaders.zip";

   @Override
   public void onPreLaunch() {
      try {
         Path game = FabricLoader.getInstance().getGameDir();
         Path packs = game.resolve("shaderpacks");
         Files.createDirectories(packs);
         Path target = packs.resolve(PACK);

         try (InputStream in = ShaderBootstrap.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
               return;
            }

            byte[] bundled = in.readAllBytes();
            // переписываем только если пак обновился вместе с клиентом
            if (!Files.exists(target) || Files.size(target) != (long)bundled.length) {
               Path tmp = packs.resolve(PACK + ".tmp");
               Files.write(tmp, bundled);
               Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
         }

         Path config = FabricLoader.getInstance().getConfigDir().resolve("iris.properties");
         if (!Files.exists(config)) {
            Files.createDirectories(config.getParent());
            Files.writeString(config, "enableShaders=true\nshaderPack=" + PACK + "\ndisableUpdateMessage=true\n", StandardCharsets.UTF_8);
         }
      } catch (Exception e) {
         System.err.println("[Pain] Не удалось подготовить шейдерпак: " + e);
      }
   }
}
