package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventAttack;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.api.setting.impl.StringSetting;
import tech.pain.client.modules.impl.combat.Criticals;
import tech.pain.utility.render.level.Render3DUtil;

/**
 * Рабочий фейк-игрок для проверки Aura, критов и TargetHud без второго аккаунта.
 * Существует только на клиенте: удары по нему на сервер не уходят, урон считается локально
 * (сила удара, перезарядка, крит, броня), он получает урон, отлетает, тратит тотемы и возрождается.
 */
@ModuleAnnotation(
   name = "FakePlayer",
   category = Category.MISC,
   description = "Фейк-игрок: получает урон, отлетает, тратит тотемы и возрождается — для проверки Aura и критов"
)
public final class FakePlayer extends Module {
   public static final FakePlayer INSTANCE = new FakePlayer();
   private final StringSetting name = new StringSetting("Ник", "FakePlayer", 16);
   private final ModeSetting movement = new ModeSetting("Движение", "Стоит", "По кругу", "Прыгает", "За тобой");
   private final NumberSetting health = new NumberSetting("Здоровье", 20.0F, 1.0F, 40.0F, 1.0F);
   private final BooleanSetting copyInventory = new BooleanSetting("Копировать броню и предметы", true);
   private final NumberSetting totems = new NumberSetting("Тотемов", 3.0F, 0.0F, 30.0F, 1.0F);
   private final BooleanSetting regen = new BooleanSetting("Восстанавливает здоровье", true);
   private final BooleanSetting knockback = new BooleanSetting("Отлетает от ударов", true);
   private final BooleanSetting showDamage = new BooleanSetting("Показывать урон", true);
   private final BooleanSetting esp = new BooleanSetting("ESP", true);
   private static int nextId = -112000;
   private OtherClientPlayerEntity fake;
   private Vec3d origin = Vec3d.ZERO;
   private Vec3d push = Vec3d.ZERO;
   private int totemsLeft;
   private int ticks;
   private int sinceHit;
   private float jump;
   private float jumpVelocity;

   private FakePlayer() {
   }

   /** true, если это наш фейк: Aura и AntiBot не должны считать его ботом, а Criticals — слать за него пакеты. */
   public static boolean isFake(Entity entity) {
      return entity != null && entity == INSTANCE.fake;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.spawn();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.remove();
   }

   private void spawn() {
      if (mc.player == null || mc.world == null) {
         return;
      }

      this.remove();
      String nick = this.name.get().trim().isEmpty() ? "FakePlayer" : this.name.get().trim();
      GameProfile profile = new GameProfile(UUID.randomUUID(), nick);
      // скин берём свой, чтобы фейк не был Стивом
      profile.getProperties().putAll(mc.player.getGameProfile().getProperties());
      OtherClientPlayerEntity clone = new OtherClientPlayerEntity(mc.world, profile);
      clone.copyPositionAndRotation(mc.player);
      clone.setHeadYaw(mc.player.getHeadYaw());
      clone.setBodyYaw(mc.player.bodyYaw);
      if (this.copyInventory.isEnabled()) {
         clone.getInventory().clone(mc.player.getInventory());
      }

      clone.setHealth(this.health.getCurrent());
      clone.setId(nextId--);
      mc.world.addEntity(clone);
      this.fake = clone;
      this.origin = clone.getPos();
      this.push = Vec3d.ZERO;
      this.totemsLeft = (int)this.totems.getCurrent();
      this.ticks = 0;
      this.jump = 0.0F;
      this.jumpVelocity = 0.0F;
      this.holdTotem();
   }

   private void holdTotem() {
      if (this.fake != null) {
         this.fake.setStackInHand(Hand.OFF_HAND, this.totemsLeft > 0 ? new ItemStack(Items.TOTEM_OF_UNDYING) : ItemStack.EMPTY);
      }
   }

   private void remove() {
      if (this.fake != null) {
         if (mc.world != null) {
            mc.world.removeEntity(this.fake.getId(), Entity.RemovalReason.DISCARDED);
         }

         this.fake = null;
      }
   }

   /** Удар по фейку: на сервер ничего не отправляем, урон считаем сами. */
   @EventTarget
   public void onAttack(EventAttack event) {
      if (this.fake == null || event.getTarget() != this.fake || mc.player == null) {
         return;
      }

      event.setCancelled(true);
      float cooldown = mc.player.getAttackCooldownProgress(0.5F);
      float damage = (float)mc.player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) * (0.2F + cooldown * cooldown * 0.8F);
      boolean falling = mc.player.fallDistance > 0.0F && !mc.player.isOnGround() && !mc.player.isClimbing() && !mc.player.isTouchingWater()
         && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS) && !mc.player.hasVehicle();
      boolean packetCrit = Criticals.INSTANCE.isEnabled() && mc.player.isOnGround();
      boolean crit = cooldown > 0.9F && (falling || packetCrit);
      if (crit) {
         damage *= 1.5F;
      }

      damage = DamageUtil.getDamageLeft(this.fake, damage, mc.player.getDamageSources().playerAttack(mc.player), (float)this.fake.getArmor(),
         (float)this.fake.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
      mc.player.swingHand(Hand.MAIN_HAND);
      mc.player.resetLastAttackedTicks();
      // как у настоящего игрока: пока идёт неуязвимость после удара, новый урон не проходит
      if (this.fake.hurtTime > 5) {
         return;
      }

      this.fake.hurtTime = 10;
      this.fake.maxHurtTime = 10;
      this.sinceHit = 0;
      if (crit) {
         mc.player.addCritParticles(this.fake);
         mc.world.playSound(mc.player, this.fake.getX(), this.fake.getY(), this.fake.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
      }

      mc.world.playSound(mc.player, this.fake.getX(), this.fake.getY(), this.fake.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0F, 1.0F);
      if (this.knockback.isEnabled()) {
         float yaw = mc.player.getYaw() * 0.017453292F;
         double strength = mc.player.isSprinting() ? 0.55 : 0.3;
         this.push = this.push.add((double)(-MathHelper.sin(yaw)) * strength, 0.0, (double)MathHelper.cos(yaw) * strength);
      }

      float left = this.fake.getHealth() - damage;
      String note = "";
      if (left <= 0.0F) {
         if (this.totemsLeft > 0) {
            --this.totemsLeft;
            left = 1.0F;
            mc.particleManager.addEmitter(this.fake, ParticleTypes.TOTEM_OF_UNDYING, 30);
            mc.world.playSound(mc.player, this.fake.getX(), this.fake.getY(), this.fake.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);
            note = " · тотем, осталось " + this.totemsLeft;
            this.holdTotem();
         } else {
            // «умер»: возвращаем на место с полным здоровьем и новым запасом тотемов
            mc.world.playSound(mc.player, this.fake.getX(), this.fake.getY(), this.fake.getZ(), SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 1.0F, 1.0F);
            left = this.health.getCurrent();
            this.totemsLeft = (int)this.totems.getCurrent();
            this.push = Vec3d.ZERO;
            this.fake.setPosition(this.origin);
            note = " · убит, возродился";
            this.holdTotem();
         }
      }

      this.fake.setHealth(left);
      if (this.showDamage.isEnabled()) {
         mc.inGameHud.setOverlayMessage(Text.of(String.format("%s−%.1f%s  §7осталось %.1f%s", crit ? "§c✦ " : "§f", damage, crit ? " крит" : "", left, note)), false);
      }
   }

   @EventTarget
   private void onRender3D(EventRender3D event) {
      if (this.esp.isEnabled() && this.fake != null && !this.fake.isRemoved()) {
         Box box = this.fake.getBoundingBox();
         int color = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();
         Render3DUtil.drawBox(box, color, 1.0F);
      }
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (this.fake == null || mc.player == null || mc.world == null) {
         return;
      }

      if (this.fake.isRemoved() || this.fake.getWorld() != mc.world) {
         this.fake = null;
         this.spawn();
         return;
      }

      ++this.ticks;
      ++this.sinceHit;
      // медленное восстановление, если по нему давно не били
      if (this.regen.isEnabled() && this.sinceHit > 60 && this.ticks % 10 == 0 && this.fake.getHealth() < this.health.getCurrent()) {
         this.fake.setHealth(Math.min(this.health.getCurrent(), this.fake.getHealth() + 1.0F));
      }

      // отлёт от удара затухает, потом фейк возвращается к своему маршруту
      this.push = this.push.multiply(0.6);
      Vec3d base;
      if (this.movement.is("За тобой")) {
         Vec3d toPlayer = mc.player.getPos().subtract(this.fake.getPos());
         double dist = toPlayer.horizontalLength();
         base = dist > 2.5 ? this.fake.getPos().add(toPlayer.multiply(Math.min(0.22, dist - 2.5) / dist, 0.0, Math.min(0.22, dist - 2.5) / dist)).withAxis(net.minecraft.util.math.Direction.Axis.Y, mc.player.getY()) : this.fake.getPos();
         this.origin = base;
      } else if (this.movement.is("По кругу")) {
         double angle = (double)this.ticks * 0.08;
         base = this.origin.add(Math.cos(angle) * 2.5, 0.0, Math.sin(angle) * 2.5);
      } else {
         base = this.origin;
      }

      if (this.movement.is("Прыгает")) {
         if (this.jump <= 0.0F) {
            this.jumpVelocity = 0.42F;
         }

         this.jump = Math.max(0.0F, this.jump + this.jumpVelocity);
         this.jumpVelocity = (this.jumpVelocity - 0.08F) * 0.98F;
      } else {
         this.jump = 0.0F;
      }

      Vec3d before = this.fake.getPos();
      Vec3d target = this.movement.is("За тобой") ? base.add(this.push) : base.add(this.push).add(0.0, (double)this.jump, 0.0);
      if (this.movement.is("За тобой")) {
         this.origin = target;
      } else if (this.push.lengthSquared() > 1.0E-4 && this.movement.is("Стоит")) {
         // стоячего фейка удар сдвигает насовсем, как настоящего игрока
         this.origin = this.origin.add(this.push);
      }

      this.fake.prevX = before.x;
      this.fake.prevY = before.y;
      this.fake.prevZ = before.z;
      this.fake.lastRenderX = before.x;
      this.fake.lastRenderY = before.y;
      this.fake.lastRenderZ = before.z;
      this.fake.setPosition(target);
      Vec3d moved = target.subtract(before);
      // смотрит туда, куда идёт; стоя на месте — на игрока
      Vec3d look = moved.horizontalLengthSquared() > 1.0E-4 && !this.movement.is("Стоит") ? moved : mc.player.getPos().subtract(target);
      float yaw = (float)(MathHelper.atan2(look.z, look.x) * 57.29577951308232) - 90.0F;
      this.fake.prevYaw = this.fake.getYaw();
      this.fake.prevHeadYaw = this.fake.headYaw;
      this.fake.prevBodyYaw = this.fake.bodyYaw;
      this.fake.setYaw(yaw);
      this.fake.setHeadYaw(yaw);
      this.fake.setBodyYaw(yaw);
      // анимация ног по скорости
      this.fake.limbAnimator.updateLimbs(Math.min(1.0F, (float)moved.horizontalLength() * 4.0F), 0.4F, 1.0F);
   }
}
