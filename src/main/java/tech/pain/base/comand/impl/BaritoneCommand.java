package tech.pain.base.comand.impl;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import tech.pain.base.comand.api.CommandAbstract;
import tech.pain.utility.game.other.MessageUtil;

public class BaritoneCommand extends CommandAbstract {
   public BaritoneCommand() {
      super("baritone");
   }

   public void execute(LiteralArgumentBuilder<CommandSource> builder) {
      builder.then(literal("goto")
         .then(arg("x", IntegerArgumentType.integer())
            .then(arg("z", IntegerArgumentType.integer()).executes((context) -> {
               int x = IntegerArgumentType.getInteger(context, "x");
               int z = IntegerArgumentType.getInteger(context, "z");
               baritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
               MessageUtil.displayInfo("Baritone: иду к " + x + " " + z);
               return 1;
            })
               .then(arg("y", IntegerArgumentType.integer()).executes((context) -> {
                  int x = IntegerArgumentType.getInteger(context, "x");
                  int z = IntegerArgumentType.getInteger(context, "z");
                  int y = IntegerArgumentType.getInteger(context, "y");
                  baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z));
                  MessageUtil.displayInfo("Baritone: иду к " + x + " " + y + " " + z);
                  return 1;
               })))));

      builder.then(literal("mine").then(arg("block", StringArgumentType.greedyString()).executes((context) -> {
         String name = StringArgumentType.getString(context, "block");
         baritone().getMineProcess().mineByName(name.trim().split("\\s+"));
         MessageUtil.displayInfo("Baritone: копаю " + name);
         return 1;
      })));

      builder.then(literal("follow").executes((context) -> {
         baritone().getFollowProcess().follow((entity) -> entity instanceof PlayerEntity && entity != mc.player);
         MessageUtil.displayInfo("Baritone: следую за игроками");
         return 1;
      }));

      builder.then(literal("stop").executes((context) -> {
         stopAll();
         MessageUtil.displayInfo("Baritone: стоп");
         return 1;
      }));

      builder.then(literal("cancel").executes((context) -> {
         stopAll();
         MessageUtil.displayInfo("Baritone: стоп");
         return 1;
      }));
   }

   private static void stopAll() {
      IBaritone baritone = baritone();
      baritone.getMineProcess().cancel();
      baritone.getFollowProcess().cancel();
      baritone.getCustomGoalProcess().setGoal(null);
      baritone.getPathingBehavior().cancelEverything();
   }

   private static IBaritone baritone() {
      return BaritoneAPI.getProvider().getPrimaryBaritone();
   }
}
