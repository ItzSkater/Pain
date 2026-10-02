package tech.pain.client.modules.impl.misc;

import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;

@ModuleAnnotation(
   name = "ScoreboardHealth",
   category = Category.MISC,
   description = "Фиксит хп цели если оно фейк"
)
public class ScoreboardHealth extends Module {
   public static final ScoreboardHealth INSTANCE = new ScoreboardHealth();
}
