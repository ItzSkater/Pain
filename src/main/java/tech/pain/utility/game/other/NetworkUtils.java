package tech.pain.utility.game.other;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.packet.Packet;
import tech.pain.utility.interfaces.IMinecraft;

public class NetworkUtils implements IMinecraft {
   private static final List<Packet<?>> silentPackets = new ArrayList();

   public static void sendSilentPacket(Packet<?> packet) {
      silentPackets.add(packet);
      mc.getNetworkHandler().sendPacket(packet);
   }

   public static void sendPacket(Packet<?> packet) {
      mc.getNetworkHandler().sendPacket(packet);
   }

   public static List<Packet<?>> getSilentPackets() {
      return silentPackets;
   }

   public static void clearSilentPackets() {
      silentPackets.clear();
   }
}
