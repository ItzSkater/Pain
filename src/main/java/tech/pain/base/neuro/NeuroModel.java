package tech.pain.base.neuro;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NeuroModel {
   public static final Path DIR = Path.of("Pain", "neuro");
   private static final Path CURRENT_FILE = DIR.resolve("current.txt");
   public static final int INPUTS = 14;
   public static final int HIDDEN = 20;
   public static final int OUTPUTS = 13;
   private static final int MAX_MEMORY = 60000;
   public static final float MAX_YAW_SPEED = 180.0F;
   public static final float MAX_PITCH_SPEED = 120.0F;
   public static final float MAX_PEAK_SPEED = 90.0F;
   public static final float MAX_YAW_SHAKE = 8.0F;
   public static final float MAX_PITCH_SHAKE = 5.0F;
   public static final float MAX_YAW_LEAD = 35.0F;
   public static final float MAX_PITCH_LEAD = 22.0F;
   public static final float MAX_YAW_ACCEL = 35.0F;
   public static final float MAX_PITCH_ACCEL = 22.0F;
   public final String name;
   public NeuroNet net;
   public int samples;
   public double loss = -1.0;
   private final List<double[]> memoryX = new ArrayList<>();
   private final List<double[]> memoryY = new ArrayList<>();
   private int replayIndex = -1;
   public static final int STROKE_SIZE = 3;
   private static final int MAX_STROKES = 50000;
   private final List<float[]> strokes = new ArrayList<>();
   private int strokeIndex = -1;
   public static final int TRAJ_SIZE = 5;
   private static final int MAX_TRAJ = 60000;
   private final List<float[]> traj = new ArrayList<>();
   private int trajIndex = -1;

   public NeuroModel(String name) {
      this.name = name;
      this.net = new NeuroNet(14, 20, 13);
   }

   public float[] predict(float yawErr, float pitchErr) {
      NeuroModel.Prediction prediction = this.predictDetailed(yawErr, pitchErr);
      return new float[]{prediction.yawSpeed, prediction.pitchSpeed};
   }

   public NeuroModel.Prediction predictDetailed(float yawErr, float pitchErr) {
      double[] x = defaultFeatures(yawErr, pitchErr);
      return this.predictDetailed(x);
   }

   public NeuroModel.Prediction predictDetailed(double[] x) {
      NeuroModel.Prediction network = this.predictFromNetwork(x);
      NeuroModel.Prediction remembered = this.predictFromMemory(x);
      return remembered != null ? remembered.withSpeed(network.yawSpeed, network.pitchSpeed) : network;
   }

   private NeuroModel.Prediction predictFromNetwork(double[] x) {
      double[] o = this.net.forward(x);
      float yawSpeed = (float)(o[0] * 180.0);
      float pitchSpeed = (float)(o[1] * 120.0);
      return o.length < 13
         ? new NeuroModel.Prediction(
            Math.max(yawSpeed, 12.0F), Math.max(pitchSpeed, 10.0F), 0.5F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, false
         )
         : new NeuroModel.Prediction(
            Math.max(yawSpeed, 12.0F),
            Math.max(pitchSpeed, 10.0F),
            clamp01((float)o[2]),
            clamp01((float)o[3]),
            clamp01((float)o[4]),
            unitToSigned((float)o[5]) * 8.0F,
            unitToSigned((float)o[6]) * 5.0F,
            unitToSigned((float)o[7]) * 35.0F,
            unitToSigned((float)o[8]) * 22.0F,
            unitToSigned((float)o[9]) * 35.0F,
            unitToSigned((float)o[10]) * 22.0F,
            clamp01((float)o[11]),
            clamp01((float)o[12]),
            false
         );
   }

   public void remember(double[][] x, double[][] y) {
      for (int i = 0; i < x.length && i < y.length; i++) {
         if (x[i].length == 14 && y[i].length == 13) {
            if (this.memoryX.size() >= 60000) {
               this.memoryX.remove(0);
               this.memoryY.remove(0);
            }

            this.memoryX.add((double[])x[i].clone());
            this.memoryY.add((double[])y[i].clone());
         }
      }
   }

   private NeuroModel.Prediction predictFromMemory(double[] x) {
      if (!this.memoryX.isEmpty() && x.length == 14) {
         if (this.replayIndex >= 0 && this.replayIndex < this.memoryX.size()) {
            double sequenceDist = sampleDistance(x, this.memoryX.get(this.replayIndex));
            if (sequenceDist < 8.0) {
               return predictionFromOutput(this.memoryY.get(this.replayIndex++));
            }
         }

         double bestDist = Double.MAX_VALUE;
         double[] best = null;
         int bestIndex = -1;

         for (int i = 0; i < this.memoryX.size(); i++) {
            double dist = sampleDistance(x, this.memoryX.get(i));
            if (dist < bestDist) {
               bestDist = dist;
               best = this.memoryY.get(i);
               bestIndex = i;
            }
         }

         this.replayIndex = bestIndex >= 0 ? bestIndex + 1 : -1;
         return best == null ? null : predictionFromOutput(best);
      } else {
         return null;
      }
   }

   private static double sampleDistance(double[] a, double[] b) {
      double dist = 0.0;

      for (int i = 0; i < 14; i++) {
         double weight = switch (i) {
            case 0, 1, 2, 3 -> 3.0;
            case 4, 5, 6, 7 -> 2.4;
            case 8, 9, 10 -> 1.8;
            default -> 1.0;
            case 12, 13 -> 1.5;
         };
         double d = a[i] - b[i];
         dist += d * d * weight;
      }

      return dist;
   }

   private static NeuroModel.Prediction predictionFromOutput(double[] o) {
      return new NeuroModel.Prediction(
         Math.max((float)o[0] * 180.0F, 12.0F),
         Math.max((float)o[1] * 120.0F, 10.0F),
         clamp01((float)o[2]),
         clamp01((float)o[3]),
         clamp01((float)o[4]),
         unitToSigned((float)o[5]) * 8.0F,
         unitToSigned((float)o[6]) * 5.0F,
         unitToSigned((float)o[7]) * 35.0F,
         unitToSigned((float)o[8]) * 22.0F,
         unitToSigned((float)o[9]) * 35.0F,
         unitToSigned((float)o[10]) * 22.0F,
         o.length > 11 ? clamp01((float)o[11]) : 1.0F,
         o.length > 12 ? clamp01((float)o[12]) : 0.0F,
         true
      );
   }

   public static double[] defaultFeatures(float yawErr, float pitchErr) {
      return features(yawErr, pitchErr, 0.0F, 0.0F, 0.0F, 0.0F, 0.5F, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F);
   }

   public static double[] features(
      float yawErr,
      float pitchErr,
      float appliedYaw,
      float appliedPitch,
      float prevAppliedYaw,
      float prevAppliedPitch,
      float hitX,
      float hitY,
      float hitZ,
      float distance,
      float prevSmoothness,
      float prevPeak
   ) {
      return new double[]{
         Math.min(Math.abs(yawErr) / 180.0, 1.0),
         Math.min(Math.abs(pitchErr) / 90.0, 1.0),
         signedToUnit(yawErr, 180.0F),
         signedToUnit(pitchErr, 90.0F),
         signedToUnit(appliedYaw, 35.0F),
         signedToUnit(appliedPitch, 22.0F),
         signedToUnit(appliedYaw - prevAppliedYaw, 35.0F),
         signedToUnit(appliedPitch - prevAppliedPitch, 22.0F),
         clamp01(hitX),
         clamp01(hitY),
         clamp01(hitZ),
         Math.min(Math.max(distance, 0.0F) / 8.0, 1.0),
         clamp01(prevSmoothness),
         clamp01(prevPeak)
      };
   }

   public void addStrokes(List<float[]> recorded) {
      for (float[] s : recorded) {
         if (s.length == 3) {
            if (this.strokes.size() >= 50000) {
               this.strokes.remove(0);
            }

            this.strokes.add((float[])s.clone());
         }
      }
   }

   public int strokeCount() {
      return this.strokes.size();
   }

   public float[] nextJitter(float speed) {
      if (this.strokes.isEmpty()) {
         return new float[]{0.0F, 0.0F};
      } else {
         if (this.strokeIndex >= 0 && this.strokeIndex < this.strokes.size()) {
            float[] s = this.strokes.get(this.strokeIndex);
            if (Math.abs(s[2] - speed) <= speedTolerance(speed)) {
               this.strokeIndex++;
               return new float[]{s[0], s[1]};
            }
         }

         int best = 0;
         float bestDist = Float.MAX_VALUE;
         int start = ThreadLocalRandom.current().nextInt(this.strokes.size());

         for (int i = 0; i < this.strokes.size(); i++) {
            int idx = (start + i) % this.strokes.size();
            float dist = Math.abs(this.strokes.get(idx)[2] - speed);
            if (dist < bestDist) {
               bestDist = dist;
               best = idx;
            }

            if (dist <= speedTolerance(speed) * 0.25F) {
               best = idx;
               break;
            }
         }

         this.strokeIndex = best + 1;
         float[] s = this.strokes.get(best);
         return new float[]{s[0], s[1]};
      }
   }

   private static float speedTolerance(float speed) {
      return Math.max(3.0F, speed * 0.6F);
   }

   public void addTrajectory(List<float[]> recorded) {
      for (float[] s : recorded) {
         if (s.length == 5) {
            if (this.traj.size() >= 60000) {
               this.traj.remove(0);
            }

            this.traj.add((float[])s.clone());
         }
      }
   }

   public int trajCount() {
      return this.traj.size();
   }

   public float[] nextMove(float yawErr, float pitchErr, float sinceAttack) {
      if (this.traj.isEmpty()) {
         return null;
      } else {
         if (this.trajIndex >= 0 && this.trajIndex < this.traj.size()) {
            float[] s = this.traj.get(this.trajIndex);
            if (s[4] >= 0.0F && trajDistance(s, yawErr, pitchErr, sinceAttack) <= followTolerance(yawErr, pitchErr)) {
               this.trajIndex++;
               return scaledMove(s, yawErr, pitchErr);
            }
         }

         int best = -1;
         float bestDist = Float.MAX_VALUE;
         int start = ThreadLocalRandom.current().nextInt(this.traj.size());

         for (int i = 0; i < this.traj.size(); i++) {
            int idx = (start + i) % this.traj.size();
            float[] s = this.traj.get(idx);
            if (!(s[4] < 0.0F)) {
               float dist = trajDistance(s, yawErr, pitchErr, sinceAttack);
               if (dist < bestDist) {
                  bestDist = dist;
                  best = idx;
               }

               if (dist <= followTolerance(yawErr, pitchErr) * 0.2F) {
                  best = idx;
                  break;
               }
            }
         }

         if (best >= 0 && !(bestDist > followTolerance(yawErr, pitchErr) * 4.0F)) {
            this.trajIndex = best + 1;
            return scaledMove(this.traj.get(best), yawErr, pitchErr);
         } else {
            this.trajIndex = -1;
            return null;
         }
      }
   }

   private static float[] scaledMove(float[] s, float yawErr, float pitchErr) {
      float recErr = (float)Math.hypot(s[0], s[1]);
      float curErr = (float)Math.hypot(yawErr, pitchErr);
      float scale = recErr < 1.0F ? 1.0F : clamp(curErr / recErr, 0.5F, 2.0F);
      return new float[]{s[2] * scale, s[3] * scale};
   }

   private static float trajDistance(float[] s, float yawErr, float pitchErr, float sinceAttack) {
      float dYaw = s[0] - yawErr;
      float dPitch = s[1] - pitchErr;
      float dAtk = (Math.min(s[4], 40.0F) - Math.min(sinceAttack, 40.0F)) * 1.2F;
      return dYaw * dYaw + dPitch * dPitch + dAtk * dAtk;
   }

   private static float followTolerance(float yawErr, float pitchErr) {
      float err = (float)Math.hypot(yawErr, pitchErr);
      float tol = 6.0F + err * 0.35F;
      return tol * tol;
   }

   public void ensureModernShape() {
      if (this.net.in != 14 || this.net.out != 13) {
         this.net = new NeuroNet(14, 20, 13);
         this.samples = 0;
         this.loss = -1.0;
         this.memoryX.clear();
         this.memoryY.clear();
      }
   }

   public static double signedToUnit(float value, float maxAbs) {
      return maxAbs <= 0.0F ? 0.5 : (clamp(value / maxAbs, -1.0F, 1.0F) + 1.0F) * 0.5F;
   }

   private static float unitToSigned(float value) {
      return clamp01(value) * 2.0F - 1.0F;
   }

   private static float clamp01(float value) {
      return clamp(value, 0.0F, 1.0F);
   }

   private static float clamp(float value, float min, float max) {
      return value < min ? min : Math.min(value, max);
   }

   public void save() {
      try {
         Files.createDirectories(DIR);
         String body = this.samples
            + " "
            + this.loss
            + "\n"
            + this.net.serialize()
            + this.serializeMemory()
            + this.serializeStrokes()
            + this.serializeTrajectory();
         Files.writeString(this.file(), body, StandardCharsets.UTF_8);
      } catch (Exception var2) {
      }
   }

   private String serializeStrokes() {
      StringBuilder sb = new StringBuilder();
      sb.append("STROKES ").append(this.strokes.size()).append('\n');

      for (float[] s : this.strokes) {
         sb.append(s[0]).append(' ').append(s[1]).append(' ').append(s[2]).append('\n');
      }

      return sb.toString();
   }

   private String serializeTrajectory() {
      StringBuilder sb = new StringBuilder();
      sb.append("TRAJ ").append(this.traj.size()).append('\n');

      for (float[] s : this.traj) {
         sb.append(s[0]).append(' ').append(s[1]).append(' ').append(s[2]).append(' ').append(s[3]).append(' ').append(s[4]).append('\n');
      }

      return sb.toString();
   }

   private String serializeMemory() {
      StringBuilder sb = new StringBuilder();
      sb.append("MEMORY ").append(this.memoryX.size()).append('\n');

      for (int row = 0; row < this.memoryX.size(); row++) {
         for (double v : this.memoryX.get(row)) {
            sb.append(v).append(' ');
         }

         sb.append("| ");

         for (double v : this.memoryY.get(row)) {
            sb.append(v).append(' ');
         }

         sb.append('\n');
      }

      return sb.toString();
   }

   public Path file() {
      return DIR.resolve(this.name + ".neuro");
   }

   public static void saveCurrentName(String name) {
      try {
         Files.createDirectories(DIR);
         if (name != null && !name.isBlank()) {
            Files.writeString(CURRENT_FILE, name, StandardCharsets.UTF_8);
         } else {
            Files.deleteIfExists(CURRENT_FILE);
         }
      } catch (Exception var2) {
      }
   }

   public static String loadCurrentName() {
      try {
         if (!Files.exists(CURRENT_FILE)) {
            return null;
         } else {
            String name = Files.readString(CURRENT_FILE, StandardCharsets.UTF_8).trim();
            return name.isEmpty() ? null : name;
         }
      } catch (Exception var1) {
         return null;
      }
   }

   public static NeuroModel load(String name) {
      try {
         Path p = DIR.resolve(name + ".neuro");
         if (!Files.exists(p)) {
            return null;
         } else {
            String data = Files.readString(p, StandardCharsets.UTF_8);
            int nl = data.indexOf(10);
            String meta = data.substring(0, nl).trim();
            String[] m = meta.split("\\s+");
            NeuroModel model = new NeuroModel(name);
            model.samples = Integer.parseInt(m[0]);
            model.loss = Double.parseDouble(m[1]);
            model.net = NeuroNet.deserialize(data.substring(nl + 1));
            model.loadMemory(data.substring(nl + 1));
            model.loadStrokes(data.substring(nl + 1));
            model.loadTrajectory(data.substring(nl + 1));
            return model;
         }
      } catch (Exception var7) {
         return null;
      }
   }

   private void loadMemory(String data) {
      String[] lines = data.split("\n");
      int start = -1;

      for (int i = 0; i < lines.length; i++) {
         if (lines[i].startsWith("MEMORY ")) {
            start = i + 1;
            break;
         }
      }

      if (start >= 0) {
         this.memoryX.clear();
         this.memoryY.clear();

         for (int ix = start; ix < lines.length && this.memoryX.size() < 60000; ix++) {
            String line = lines[ix].trim();
            if (!line.isEmpty()) {
               if (line.startsWith("STROKES ")) {
                  break;
               }

               int sep = line.indexOf(124);
               if (sep >= 0) {
                  double[] x = parseVector(line.substring(0, sep), 14);
                  double[] y = parseVector(line.substring(sep + 1), 13);
                  if (x != null && y != null) {
                     this.memoryX.add(x);
                     this.memoryY.add(y);
                  }
               }
            }
         }
      }
   }

   private void loadStrokes(String data) {
      String[] lines = data.split("\n");
      int start = -1;

      for (int i = 0; i < lines.length; i++) {
         if (lines[i].startsWith("STROKES ")) {
            start = i + 1;
            break;
         }
      }

      if (start >= 0) {
         this.strokes.clear();

         for (int ix = start; ix < lines.length && this.strokes.size() < 50000; ix++) {
            String line = lines[ix].trim();
            if (!line.isEmpty()) {
               if (line.startsWith("TRAJ ")) {
                  break;
               }

               String[] parts = line.split("\\s+");
               if (parts.length >= 3) {
                  try {
                     this.strokes.add(new float[]{Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
                  } catch (Exception var8) {
                  }
               }
            }
         }
      }
   }

   private void loadTrajectory(String data) {
      String[] lines = data.split("\n");
      int start = -1;

      for (int i = 0; i < lines.length; i++) {
         if (lines[i].startsWith("TRAJ ")) {
            start = i + 1;
            break;
         }
      }

      if (start >= 0) {
         this.traj.clear();

         for (int ix = start; ix < lines.length && this.traj.size() < 60000; ix++) {
            String line = lines[ix].trim();
            if (!line.isEmpty()) {
               String[] parts = line.split("\\s+");
               if (parts.length >= 5) {
                  try {
                     this.traj
                        .add(
                           new float[]{
                              Float.parseFloat(parts[0]),
                              Float.parseFloat(parts[1]),
                              Float.parseFloat(parts[2]),
                              Float.parseFloat(parts[3]),
                              Float.parseFloat(parts[4])
                           }
                        );
                  } catch (Exception var8) {
                  }
               }
            }
         }
      }
   }

   private static double[] parseVector(String text, int expected) {
      String[] parts = text.trim().split("\\s+");
      if (parts.length < expected) {
         return null;
      } else {
         double[] values = new double[expected];

         try {
            for (int i = 0; i < expected; i++) {
               values[i] = Double.parseDouble(parts[i]);
            }

            return values;
         } catch (Exception var5) {
            return null;
         }
      }
   }

      public static class Prediction {
      public final float yawSpeed;
      public final float pitchSpeed;
      public final float hitX;
      public final float hitY;
      public final float hitZ;
      public final float yawShake;
      public final float pitchShake;
      public final float yawLead;
      public final float pitchLead;
      public final float yawAccel;
      public final float pitchAccel;
      public final float smoothness;
      public final float peak;
      public final boolean remembered;

      public Prediction(
         float yawSpeed,
         float pitchSpeed,
         float hitX,
         float hitY,
         float hitZ,
         float yawShake,
         float pitchShake,
         float yawLead,
         float pitchLead,
         float yawAccel,
         float pitchAccel
      ) {
         this(yawSpeed, pitchSpeed, hitX, hitY, hitZ, yawShake, pitchShake, yawLead, pitchLead, yawAccel, pitchAccel, 1.0F, 0.0F, false);
      }

      public Prediction(
         float yawSpeed,
         float pitchSpeed,
         float hitX,
         float hitY,
         float hitZ,
         float yawShake,
         float pitchShake,
         float yawLead,
         float pitchLead,
         float yawAccel,
         float pitchAccel,
         float smoothness,
         float peak,
         boolean remembered
      ) {
         this.yawSpeed = yawSpeed;
         this.pitchSpeed = pitchSpeed;
         this.hitX = hitX;
         this.hitY = hitY;
         this.hitZ = hitZ;
         this.yawShake = yawShake;
         this.pitchShake = pitchShake;
         this.yawLead = yawLead;
         this.pitchLead = pitchLead;
         this.yawAccel = yawAccel;
         this.pitchAccel = pitchAccel;
         this.smoothness = smoothness;
         this.peak = peak;
         this.remembered = remembered;
      }

      public NeuroModel.Prediction withSpeed(float yawSpeed, float pitchSpeed) {
         return new NeuroModel.Prediction(
            yawSpeed,
            pitchSpeed,
            this.hitX,
            this.hitY,
            this.hitZ,
            this.yawShake,
            this.pitchShake,
            this.yawLead,
            this.pitchLead,
            this.yawAccel,
            this.pitchAccel,
            this.smoothness,
            this.peak,
            this.remembered
         );
      }
   }
}
