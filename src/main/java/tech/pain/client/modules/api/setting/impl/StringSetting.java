package tech.pain.client.modules.api.setting.impl;

import com.google.gson.JsonObject;
import java.util.function.Supplier;
import tech.pain.client.modules.api.setting.Setting;

/** Текстовая настройка: ник, название хома и т.п. Редактируется прямо в клик-гуи. */
public class StringSetting extends Setting {
   private String value;
   private final int maxLength;

   public StringSetting(String name, String value) {
      this(name, value, 32);
   }

   public StringSetting(String name, String value, int maxLength) {
      super(name);
      this.value = value == null ? "" : value;
      this.maxLength = maxLength;
   }

   public StringSetting(String name, String value, Supplier<Boolean> visible) {
      this(name, value, 32);
      this.setVisible(visible);
   }

   public String get() {
      return this.value;
   }

   public void set(String value) {
      String v = value == null ? "" : value;
      this.value = v.length() > this.maxLength ? v.substring(0, this.maxLength) : v;
   }

   public int getMaxLength() {
      return this.maxLength;
   }

   public void safe(JsonObject propertiesObject) {
      propertiesObject.addProperty(String.valueOf(this.name), this.value);
   }

   public void load(JsonObject propertiesObject) {
      this.set(propertiesObject.get(String.valueOf(this.name)).getAsString());
   }
}
