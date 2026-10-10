package tech.pain.base.modules;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import lombok.Generated;
import net.minecraft.client.option.Perspective;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.util.math.MathHelper;
import tech.pain.Pain;
import tech.pain.base.events.impl.input.EventKey;
import tech.pain.base.events.impl.other.EventGameUpdate;
import tech.pain.base.events.impl.render.EventHudRender;
import tech.pain.base.events.impl.server.EventPacket;
import tech.pain.base.macro.Macro;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.setting.Setting;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting.Value;
import tech.pain.client.modules.impl.combat.AntiBot;
import tech.pain.client.modules.impl.combat.Aura;
import tech.pain.client.modules.impl.combat.AutoSwap;
import tech.pain.client.modules.impl.combat.AutoTotem;
import tech.pain.client.modules.impl.combat.ClickPearl;
import tech.pain.client.modules.impl.combat.AnchorAura;
import tech.pain.client.modules.impl.combat.CrystalAura;
import tech.pain.client.modules.impl.combat.TargetTP;
import tech.pain.client.modules.impl.combat.TargetStrafe;
import tech.pain.client.modules.impl.misc.AHHelper;
import tech.pain.client.modules.impl.misc.AutoAccept;
import tech.pain.client.modules.impl.misc.AutoEZ;
import tech.pain.client.modules.impl.misc.AutoRespawn;
import tech.pain.client.modules.impl.misc.ClickAction;
import tech.pain.client.modules.impl.misc.ElytraHelper;
import tech.pain.client.modules.impl.misc.FakePlayer;
import tech.pain.client.modules.impl.misc.FlickBot;
import tech.pain.client.modules.impl.misc.FreeCam;
import tech.pain.client.modules.impl.misc.ItemScroller;
import tech.pain.client.modules.impl.misc.NameProtect;
import tech.pain.client.modules.impl.misc.NoInteract;
import tech.pain.client.modules.impl.misc.Panic;
import tech.pain.client.modules.impl.misc.ScoreboardHealth;
import tech.pain.client.modules.impl.misc.ServerHelper;
import tech.pain.client.modules.impl.movement.AirStuck;
import tech.pain.client.modules.impl.movement.AutoSprint;
import tech.pain.client.modules.impl.movement.ElytraAccelerate;
import tech.pain.client.modules.impl.movement.ElytraBooster;
import tech.pain.client.modules.impl.movement.ElytraMotion;
import tech.pain.client.modules.impl.movement.ElytraRecast;
import tech.pain.client.modules.impl.movement.GuiWalk;
import tech.pain.client.modules.impl.movement.NoSlow;
import tech.pain.client.modules.impl.movement.NoWeb;
import tech.pain.client.modules.impl.movement.Speed;
import tech.pain.client.modules.impl.player.AutoArmor;
import tech.pain.client.modules.impl.player.AutoTool;
import tech.pain.client.modules.impl.player.Blink;
import tech.pain.client.modules.impl.player.FastBreak;
import tech.pain.client.modules.impl.player.NoDelay;
import tech.pain.client.modules.impl.player.NoPush;
import tech.pain.client.modules.impl.render.AntiInvisible;
import tech.pain.client.modules.impl.render.Crosshair;
import tech.pain.client.modules.impl.render.CustomFog;
import tech.pain.client.modules.impl.render.EntityESP;
import tech.pain.client.modules.impl.render.FullBright;
import tech.pain.client.modules.impl.render.Interface;
import tech.pain.client.modules.impl.render.Menu;
import tech.pain.client.modules.impl.render.NoRender;
import tech.pain.client.modules.impl.render.Predictions;
import tech.pain.client.modules.impl.render.SwingAnimation;
import tech.pain.client.modules.impl.render.TargetESP;
import tech.pain.client.modules.impl.render.ViewModel;
import tech.pain.client.modules.impl.render.WorldTime;
import tech.pain.client.screens.menu.MenuScreen;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.rotation.Rotation;
import tech.pain.utility.interfaces.IMinecraft;

public final class ModuleManager implements IMinecraft {
   private final List<Module> modules = new ArrayList<>();
   private boolean isBack;
   private boolean isRotated;
   private float acceleration;

   private long lastKeyPressTime = 0;
   private int lastKeyCode = -1;
   private static final long DEBOUNCE_THRESHOLD_MS = 200;

   public ModuleManager() {
      init();
      EventManager.register(this);
   }

   private void init() {
      registerCombat();
      registerMovement();
      registerRender();
      registerPlayer();
      registerMisc();
      registerSwarm();
   }

   private void registerSwarm() {
      registerModule(tech.pain.client.modules.impl.swarm.SwarmModule.INSTANCE);
   }

   private void registerCombat() {
      registerModule(AntiBot.INSTANCE);
      registerModule(Aura.INSTANCE);
      registerModule(AutoSwap.INSTANCE);
      registerModule(AutoTotem.INSTANCE);
      registerModule(tech.pain.client.modules.impl.combat.Criticals.INSTANCE);
      registerModule(tech.pain.client.modules.impl.combat.AttributeSwap.INSTANCE);
      registerModule(tech.pain.client.modules.impl.combat.RageBot.INSTANCE);
      registerAlpha("tech.pain.client.modules.impl.combat.ElytraTarget");
      registerModule(ClickPearl.INSTANCE);
      registerModule(CrystalAura.INSTANCE);
      registerModule(AnchorAura.INSTANCE);
      registerModule(TargetTP.INSTANCE);
      registerModule(TargetStrafe.INSTANCE);
   }

   private void registerMovement() {
      registerModule(AutoSprint.INSTANCE);
      registerAlpha("tech.pain.client.modules.impl.movement.Scaffold");
      registerModule(ElytraBooster.INSTANCE);
      registerModule(ElytraRecast.INSTANCE);
      registerModule(GuiWalk.INSTANCE);
      registerModule(NoSlow.INSTANCE);
      registerModule(Speed.INSTANCE);
      registerModule(AirStuck.INSTANCE);
      registerModule(ElytraMotion.INSTANCE);
      registerModule(NoWeb.INSTANCE);
   }

   private void registerRender() {
      registerModule(Interface.INSTANCE);
      registerModule(AntiInvisible.INSTANCE);
      registerModule(Menu.INSTANCE);
      registerModule(NoRender.INSTANCE);
      registerModule(Predictions.INSTANCE);
      registerModule(SwingAnimation.INSTANCE);
      registerModule(Crosshair.INSTANCE);
      registerModule(ViewModel.INSTANCE);
      registerModule(CustomFog.INSTANCE);
      registerModule(FullBright.INSTANCE);
      registerModule(WorldTime.INSTANCE);
      registerModule(EntityESP.INSTANCE);
      registerModule(TargetESP.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.Shaders.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.Chams.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.WeatherControl.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.HitFlash.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.ChinaHat.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.Halo.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.BlockHighlight.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.HitMarker.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.Vignette.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.KillEffect.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.Trails.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.Nametags.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.ESP.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.SmoothCamera.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.Tracers.INSTANCE);
   }

   private void registerPlayer() {
      registerModule(AutoTool.INSTANCE);
      registerModule(AutoArmor.INSTANCE);
      registerAlpha("tech.pain.client.modules.impl.player.ChestSorter");
      registerAlpha("tech.pain.client.modules.impl.player.BaseFinder");
      registerModule(Blink.INSTANCE);
      registerModule(NoDelay.INSTANCE);
      registerModule(FastBreak.INSTANCE);
      registerModule(NoPush.INSTANCE);
   }

   private void registerMisc() {
      registerModule(ServerHelper.INSTANCE);
      registerModule(ElytraHelper.INSTANCE);
      registerModule(ItemScroller.INSTANCE);
      registerModule(ClickAction.INSTANCE);
      registerModule(FreeCam.INSTANCE);
      registerModule(FakePlayer.INSTANCE);
      registerModule(FlickBot.INSTANCE);
      registerModule(Panic.INSTANCE);
      registerModule(AutoEZ.INSTANCE);
      if (tech.pain.Edition.ALPHA) {
         registerModule(tech.pain.client.modules.impl.misc.MusicLyrics.INSTANCE);
      }
      if (tech.pain.Edition.ALPHA) {
         registerModule(tech.pain.client.modules.impl.misc.ChatLyrics.INSTANCE);
      }
      registerModule(tech.pain.client.modules.impl.misc.Spammer.INSTANCE);
      registerModule(tech.pain.client.modules.impl.misc.HitSound.INSTANCE);
      registerModule(tech.pain.client.modules.impl.player.AutoWarden.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.Weather.INSTANCE);
      registerAlpha("tech.pain.client.modules.impl.render.HitEffect");
      registerAlpha("tech.pain.client.modules.impl.render.Thunder");
      if (tech.pain.Edition.ALPHA) {
         registerModule(tech.pain.client.modules.impl.render.FireChams.INSTANCE);
      }
      registerAlpha("tech.pain.client.modules.impl.render.MotionBlur");
      registerAlpha("tech.pain.client.modules.impl.render.JumpEffect");
      registerAlpha("tech.pain.client.modules.impl.render.BlockESP");
      registerAlpha("tech.pain.client.modules.impl.render.StorageESP");
      registerAlpha("tech.pain.client.modules.impl.render.Minimap");
      registerModule(tech.pain.client.modules.impl.render.Menu3D.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.Browser3D.INSTANCE);
      if (tech.pain.Edition.ALPHA) {
         registerModule(tech.pain.client.modules.impl.render.Pet.INSTANCE);
      }
      if (tech.pain.Edition.ALPHA) {
         registerModule(tech.pain.client.modules.impl.render.ClawdSkin.INSTANCE);
      }
      registerModule(tech.pain.client.modules.impl.render.SkyShader.INSTANCE);
      registerModule(tech.pain.client.modules.impl.render.CoolInterface.INSTANCE);
      registerModule(AHHelper.INSTANCE);
      registerModule(NoInteract.INSTANCE);
      registerModule(AutoAccept.INSTANCE);
      registerModule(AutoRespawn.INSTANCE);
      registerModule(NameProtect.INSTANCE);
      registerModule(ScoreboardHealth.INSTANCE);
      registerModule(ElytraAccelerate.INSTANCE);
   }

   /**
    * Модуль версии Alpha. В сборке Free этих классов нет вовсе, поэтому обращаемся к ним по имени:
    * нет класса — модуль просто не появляется.
    */
   private void registerAlpha(String className) {
      if (!tech.pain.Edition.ALPHA) {
         return;
      }

      try {
         Object instance = Class.forName(className).getField("INSTANCE").get(null);
         this.registerModule((Module)instance);
      } catch (ReflectiveOperationException | LinkageError e) {
         System.err.println("[Pain] модуль Alpha не загружен: " + className + " (" + e + ")");
      }
   }

   private void registerModule(Module module) {
      modules.add(module);
   }

   public Module getModule(String name) {
      return modules.stream()
              .filter(module -> module.getName().equalsIgnoreCase(name))
              .findFirst()
              .orElse(null);
   }

   public Set<Module> getActiveModules() {
      Set<Module> active = new HashSet<>();
      for (Module module : modules) {
         if (module.isEnabled()) {
            active.add(module);
         }
      }
      return active;
   }

   @EventTarget
   public void onKey(EventKey event) {
      if (mc.currentScreen == null && event.getAction() == 1) {
         int keyCode = event.getKeyCode();
         long currentTime = System.currentTimeMillis();
         
         if (keyCode == lastKeyCode && (currentTime - lastKeyPressTime) < DEBOUNCE_THRESHOLD_MS) {
            return;
         }
         
         lastKeyCode = keyCode;
         lastKeyPressTime = currentTime;
         
         for (Module module : modules) {
            // у Panic нет бинда — оставляем меню, иначе его нечем выключить
            boolean menuEscape = Panic.INSTANCE.getKeyCode() == -1 && (module == tech.pain.client.modules.impl.render.Menu.INSTANCE || module == tech.pain.client.modules.impl.render.Menu3D.INSTANCE);
            if (Panic.ACTIVE && module != Panic.INSTANCE && !menuEscape) {
               continue;
            }

            if (module.getKeyCode() == keyCode && module.getKeyCode() != -1) {
               module.toggle();
            }
         }

         for (Macro macro : Pain.getInstance().getMacroManager().getItems()) {
            if (keyCode == macro.getBind()) {
               mc.getNetworkHandler().sendChatMessage(macro.getText());
            }
         }
      }
   }

   @EventTarget
   public void onRender(EventHudRender e) {
      Pain.getInstance().getThemeManager().getCurrentTheme().getAnimation().update(1.0F);

      for (Module module : modules) {
         module.getAnimation().update(module.isEnabled());

         for (Setting setting : module.getSettings()) {
            if (setting instanceof BooleanSetting booleanSetting) {
               booleanSetting.getAnimation().update(booleanSetting.isEnabled());
            } else if (setting instanceof ModeSetting modeSetting) {
               for (ModeSetting.Value value : modeSetting.getValues()) {
                  value.getAnimation().update(value.isSelected());
               }
            } else if (setting instanceof MultiBooleanSetting multiBooleanSetting) {
               for (MultiBooleanSetting.Value value : multiBooleanSetting.getBooleanSettings()) {
                  value.getAnimation().update(value.isEnabled());
               }
            }
         }
      }

      MenuScreen menuScreen = Pain.getInstance().getMenuScreen();
      if (menuScreen.needToClose) {
         if (menuScreen.savedRunnable != null) {
            menuScreen.savedRunnable.run();
         }

         if (menuScreen.openAnimationMetanoise.getValue() <= 0.27F) {
            menuScreen.savedRunnable = null;
            menuScreen.needToClose = false;
            menuScreen.openAnimationMetanoise.setValue(0.0F);
            menuScreen.openAnimationMetanoise.setStartValue(0.0F);
         }
      }
   }

   @EventTarget
   private void onPacket(EventPacket e) {
      if (e.getPacket() instanceof CloseScreenS2CPacket && mc.currentScreen instanceof MenuScreen) {
         e.cancel();
      }
   }

   @EventTarget
   private void onGameUpdate(EventGameUpdate e) {
      if (mc.player == null) return;

      if (!Aura.INSTANCE.isEnabled() || Aura.INSTANCE.getTarget() == null) {
         float cameraYaw = mc.gameRenderer.getCamera().getYaw();
         float cameraPitch = mc.gameRenderer.getCamera().getPitch();

         if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            Aura.INSTANCE.lastYaw = cameraYaw - 180.0F;
            Aura.INSTANCE.lastPitch = -cameraPitch;
         } else {
            Aura.INSTANCE.lastYaw = cameraYaw;
            Aura.INSTANCE.lastPitch = cameraPitch;
         }

         if (Aura.INSTANCE.rotationMode.is("Vanilla")) {
            return;
         }

         Rotation current = new Rotation(mc.player.getYaw(), mc.player.getPitch());
         float deltaYaw = MathHelper.wrapDegrees(cameraYaw - current.getYaw());
         float deltaPitch = cameraPitch - current.getPitch();

         if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            deltaYaw = MathHelper.wrapDegrees(cameraYaw - 180.0F - current.getYaw());
            deltaPitch = -cameraPitch - current.getPitch();
         }

         acceleration += 0.0024F;
         float smooth = MathHelper.clamp(acceleration, 0.0F, 1.0F);
         float newYaw = current.getYaw() + deltaYaw * smooth;
         float newPitch = current.getPitch() + deltaPitch * (smooth / 2.0F);

         Rotation smoothRot = new Rotation(newYaw, newPitch);
         RotationComponent.update(smoothRot, 360.0F, 360.0F, 360.0F, 360.0F, 0, 2, false);
      }
   }


   public List<Module> getModules() {
      return modules;
   }

   public boolean isBack() {
      return isBack;
   }

   public boolean isRotated() {
      return isRotated;
   }

   public float getAcceleration() {
      return acceleration;
   }

   public void setBack(boolean isBack) {
      this.isBack = isBack;
   }

   public void setRotated(boolean isRotated) {
      this.isRotated = isRotated;
   }

   public void setAcceleration(float acceleration) {
      this.acceleration = acceleration;
   }
}