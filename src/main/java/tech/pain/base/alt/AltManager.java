package tech.pain.base.alt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

/**
 * Менеджер офлайн-аккаунтов: список ников в Pain/alts.json и смена сессии без перезапуска игры.
 * Новый ник начинает действовать при следующем подключении к серверу.
 */
public final class AltManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final List<String> ACCOUNTS = new ArrayList<>();
   private static Session original;
   private static boolean loaded;

   private AltManager() {
   }

   private static Path file() {
      return MinecraftClient.getInstance().runDirectory.toPath().resolve("Pain").resolve("alts.json");
   }

   public static synchronized List<String> accounts() {
      load();
      return ACCOUNTS;
   }

   private static void load() {
      if (loaded) {
         return;
      }

      loaded = true;
      MinecraftClient mc = MinecraftClient.getInstance();
      original = mc.getSession();

      try {
         if (Files.exists(file())) {
            JsonObject root = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), JsonObject.class);
            if (root != null && root.has("accounts")) {
               root.getAsJsonArray("accounts").forEach(e -> {
                  if (valid(e.getAsString()) && !contains(e.getAsString())) {
                     ACCOUNTS.add(e.getAsString());
                  }
               });
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      // аккаунт, с которым запустили игру, всегда в списке
      if (!contains(original.getUsername()) && valid(original.getUsername())) {
         ACCOUNTS.add(0, original.getUsername());
         save();
      }
   }

   private static void save() {
      try {
         Files.createDirectories(file().getParent());
         JsonObject root = new JsonObject();
         JsonArray list = new JsonArray();
         ACCOUNTS.forEach(list::add);
         root.add("accounts", list);
         Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static boolean valid(String name) {
      return name != null && name.matches("[A-Za-z0-9_]{3,16}");
   }

   private static boolean contains(String name) {
      return ACCOUNTS.stream().anyMatch(a -> a.equalsIgnoreCase(name));
   }

   public static synchronized boolean add(String name) {
      load();
      if (!valid(name) || contains(name)) {
         return false;
      }

      ACCOUNTS.add(name);
      save();
      return true;
   }

   public static synchronized void remove(String name) {
      load();
      ACCOUNTS.removeIf(a -> a.equalsIgnoreCase(name));
      save();
   }

   public static String current() {
      return MinecraftClient.getInstance().getSession().getUsername();
   }

   /** Входит под офлайн-ником; аккаунт, с которым игра запущена, возвращается со своей исходной сессией. */
   public static synchronized void login(String name) {
      load();
      if (!valid(name)) {
         return;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      if (original != null && original.getUsername().equalsIgnoreCase(name)) {
         mc.session = original;
         return;
      }

      UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
      mc.session = new Session(name, uuid, "", Optional.empty(), Optional.empty(), Session.AccountType.LEGACY);
   }

   public static String randomName() {
      String[] first = {"Dark", "Cool", "Night", "Fast", "Wild", "Soft", "Grim", "Lucky", "Frost", "Neon", "Void", "Silent"};
      String[] second = {"Fox", "Wolf", "Cat", "Raven", "Blade", "Ghost", "Storm", "Pixel", "Viper", "Bear", "Hawk", "Moon"};
      java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
      String name = first[r.nextInt(first.length)] + second[r.nextInt(second.length)] + r.nextInt(10, 9999);
      return name.length() > 16 ? name.substring(0, 16) : name;
   }
}
