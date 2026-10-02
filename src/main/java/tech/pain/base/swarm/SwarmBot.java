package tech.pain.base.swarm;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientConfigurationPacketListener;
import net.minecraft.network.listener.ClientLoginPacketListener;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.config.ReadyC2SPacket;
import net.minecraft.network.packet.c2s.config.SelectKnownPacksC2SPacket;
import net.minecraft.network.packet.c2s.login.EnterConfigurationC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginCompressionS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.state.ConfigurationStates;
import net.minecraft.text.Text;

/**
 * Один бот роя = отдельное in-process сетевое соединение (ClientConnection),
 * без второго MinecraftClient. Фаза 1: коннект + offline-логин + конфигурация,
 * keep-alive держит соединение. Переход в PLAY (динамические реестры) и трекинг
 * мира — Фаза 2, дорабатывается на живом сервере.
 */
public final class SwarmBot {
   private final String name;
   private ClientConnection connection;
   private volatile boolean alive;

   public SwarmBot(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public boolean isAlive() {
      return this.alive && this.connection != null && this.connection.isOpen();
   }

   public void connect(String host, int port) {
      try {
         InetSocketAddress address = new InetSocketAddress(host, port);
         this.connection = ClientConnection.connect(address, true, (net.minecraft.util.profiler.MultiValueDebugSampleLogImpl)null);
         ClientLoginPacketListener login = this.proxy(ClientLoginPacketListener.class);
         this.connection.connect(host, port, login);
         UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.name).getBytes(StandardCharsets.UTF_8));
         this.connection.send(new LoginHelloC2SPacket(this.name, uuid));
         this.alive = true;
      } catch (Throwable t) {
         this.alive = false;
         System.out.println("[Swarm] " + this.name + " connect failed: " + t);
      }
   }

   public void disconnect() {
      this.alive = false;
      try {
         if (this.connection != null && this.connection.isOpen()) {
            this.connection.disconnect(Text.of("Swarm stop"));
         }
      } catch (Throwable ignored) {
      }
      this.connection = null;
   }

   @SuppressWarnings("unchecked")
   private <T> T proxy(Class<T> iface) {
      return (T)Proxy.newProxyInstance(iface.getClassLoader(), new Class[]{iface}, this.handler);
   }

   private final InvocationHandler handler = new InvocationHandler() {
      public Object invoke(Object proxy, Method method, Object[] args) {
         try {
            if (args != null && args.length >= 1 && args[0] instanceof Packet) {
               SwarmBot.this.onPacket((Packet<?>)args[0]);
            }
         } catch (Throwable ignored) {
         }

         Class<?> rt = method.getReturnType();
         if (rt == boolean.class) {
            return "isConnectionOpen".equals(method.getName()) && SwarmBot.this.isAlive();
         } else if (rt.isPrimitive() && rt != void.class) {
            return 0;
         } else {
            return null;
         }
      }
   };

   private void onPacket(Packet<?> packet) {
      if (this.connection == null) {
         return;
      }

      if (packet instanceof LoginCompressionS2CPacket p) {
         this.connection.setCompressionThreshold(p.getCompressionThreshold(), false);
      } else if (packet instanceof LoginSuccessS2CPacket) {
         this.connection.send(EnterConfigurationC2SPacket.INSTANCE);
         this.connection.transitionInbound(ConfigurationStates.S2C, this.proxy(ClientConfigurationPacketListener.class));
      } else if (packet instanceof KeepAliveS2CPacket p) {
         this.connection.send(new KeepAliveC2SPacket(p.getId()));
      } else if (packet instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket pos) {
         this.connection.send(new net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket(pos.teleportId()));
      } else if (packet instanceof net.minecraft.network.packet.s2c.config.SelectKnownPacksS2CPacket) {
         this.connection.send(new SelectKnownPacksC2SPacket(List.of()));
      } else if (packet instanceof net.minecraft.network.packet.s2c.config.ReadyS2CPacket) {
         this.connection.send(ReadyC2SPacket.INSTANCE);
         // Переход в PLAY требует динамических реестров — Фаза 2, дорабатывается вживую.
         try {
            this.enterPlay();
         } catch (Throwable t) {
            System.out.println("[Swarm] " + this.name + " play-transition pending: " + t);
         }
      }
   }

   private void enterPlay() {
      // PLAY-состояние биндится с RegistryByteBuf (динамические реестры). Берём реестры из
      // текущего клиента — поэтому ты должен сам быть в мире на этом же сервере, чтобы боты вошли.
      net.minecraft.client.world.ClientWorld world = net.minecraft.client.MinecraftClient.getInstance().world;
      if (world == null) {
         System.out.println("[Swarm] " + this.name + ": нет реестров — зайди сам на этот сервер, потом запускай ботов");
         return;
      }

      net.minecraft.registry.DynamicRegistryManager registries = world.getRegistryManager();
      ClientPlayPacketListener play = this.proxy(ClientPlayPacketListener.class);
      this.connection.transitionInbound(
         net.minecraft.network.state.PlayStateFactories.S2C.bind(buf -> new net.minecraft.network.RegistryByteBuf(buf, registries)),
         play
      );
      System.out.println("[Swarm] " + this.name + ": вошёл в игру (PLAY)");
   }
}
