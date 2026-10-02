package tech.pain.base.events.impl.entity;

import lombok.Generated;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import tech.pain.base.events.callables.EventCancellable;

public class EventEntityColor extends EventCancellable {
   private int color;
   private LivingEntityRenderState state;

   @Generated
   public int getColor() {
      return this.color;
   }

   @Generated
   public void setColor(int color) {
      this.color = color;
   }

   @Generated
   public LivingEntityRenderState getState() {
      return this.state;
   }

   @Generated
   public EventEntityColor(int color) {
      this.color = color;
   }

   @Generated
   public EventEntityColor(int color, LivingEntityRenderState state) {
      this.color = color;
      this.state = state;
   }
}
