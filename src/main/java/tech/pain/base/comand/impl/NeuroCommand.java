package tech.pain.base.comand.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import tech.pain.base.comand.api.CommandAbstract;
import tech.pain.base.neuro.NeuroManager;
import tech.pain.base.neuro.NeuroModel;
import tech.pain.utility.game.other.MessageUtil;

public class NeuroCommand extends CommandAbstract {
   public NeuroCommand() {
      super("neuro");
   }

   public void execute(LiteralArgumentBuilder<CommandSource> builder) {
      builder.then(literal("create").then(arg("name", StringArgumentType.word()).executes((context) -> {
         String name = StringArgumentType.getString(context, "name");
         if (NeuroManager.get().create(name)) {
            MessageUtil.displayInfo("Neuro: модель '" + name + "' создана и выбрана");
         } else {
            MessageUtil.displayInfo("Neuro: модель '" + name + "' уже существует");
         }

         return 1;
      })));

      builder.then(literal("select").then(arg("name", StringArgumentType.word()).executes((context) -> {
         String name = StringArgumentType.getString(context, "name");
         MessageUtil.displayInfo(NeuroManager.get().select(name) ? "Neuro: выбрана модель '" + name + "'" : "Neuro: модель '" + name + "' не найдена");
         return 1;
      })));

      builder.then(literal("delete").then(arg("name", StringArgumentType.word()).executes((context) -> {
         String name = StringArgumentType.getString(context, "name");
         MessageUtil.displayInfo(NeuroManager.get().delete(name) ? "Neuro: модель '" + name + "' удалена" : "Neuro: модель '" + name + "' не найдена");
         return 1;
      })));

      builder.then(literal("start").executes((context) -> {
         NeuroManager.get().startTrain();
         return 1;
      }));

      builder.then(literal("stop").executes((context) -> {
         NeuroManager.get().stopTrain();
         return 1;
      }));

      builder.then(literal("list").executes((context) -> {
         StringBuilder sb = new StringBuilder("Neuro модели: ");
         for (NeuroModel model : NeuroManager.get().all()) {
            sb.append(model.name).append("(").append(model.samples).append(") ");
         }

         NeuroModel cur = NeuroManager.get().getCurrent();
         sb.append("| текущая: ").append(cur == null ? "нет" : cur.name);
         MessageUtil.displayInfo(sb.toString());
         return 1;
      }));
   }
}
