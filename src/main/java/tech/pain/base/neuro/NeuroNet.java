package tech.pain.base.neuro;

import java.util.Random;

/**
 * Двухслойный перцептрон (tanh скрытый слой, сигмоида на выходе) с обратным
 * распространением. Чистая математика, без зависимостей от Minecraft.
 * Портирован из Luxe (ru.white.manager.neuro.NeuroNet), переписан в стиль Pain.
 */
public class NeuroNet {
   public final int in;
   public final int hid;
   public final int out;
   public final double[][] w1;
   public final double[] b1;
   public final double[][] w2;
   public final double[] b2;
   private static final Random RND = new Random();

   public NeuroNet(int in, int hid, int out) {
      this.in = in;
      this.hid = hid;
      this.out = out;
      this.w1 = new double[hid][in];
      this.b1 = new double[hid];
      this.w2 = new double[out][hid];
      this.b2 = new double[out];
      double s1 = Math.sqrt(2.0D / (double)in);
      double s2 = Math.sqrt(2.0D / (double)hid);

      for (int j = 0; j < hid; ++j) {
         for (int i = 0; i < in; ++i) {
            this.w1[j][i] = (RND.nextDouble() * 2.0D - 1.0D) * s1;
         }
      }

      for (int k = 0; k < out; ++k) {
         for (int j = 0; j < hid; ++j) {
            this.w2[k][j] = (RND.nextDouble() * 2.0D - 1.0D) * s2;
         }
      }
   }

   private static double sigmoid(double x) {
      return 1.0D / (1.0D + Math.exp(-x));
   }

   public double[] forward(double[] x) {
      double[] h = new double[this.hid];

      for (int j = 0; j < this.hid; ++j) {
         double s = this.b1[j];

         for (int i = 0; i < this.in; ++i) {
            s += this.w1[j][i] * x[i];
         }

         h[j] = Math.tanh(s);
      }

      double[] o = new double[this.out];

      for (int k = 0; k < this.out; ++k) {
         double s = this.b2[k];

         for (int j = 0; j < this.hid; ++j) {
            s += this.w2[k][j] * h[j];
         }

         o[k] = sigmoid(s);
      }

      return o;
   }

   private double train1(double[] x, double[] y, double lr) {
      double[] h = new double[this.hid];

      for (int j = 0; j < this.hid; ++j) {
         double s = this.b1[j];

         for (int i = 0; i < this.in; ++i) {
            s += this.w1[j][i] * x[i];
         }

         h[j] = Math.tanh(s);
      }

      double[] o = new double[this.out];

      for (int k = 0; k < this.out; ++k) {
         double s = this.b2[k];

         for (int j = 0; j < this.hid; ++j) {
            s += this.w2[k][j] * h[j];
         }

         o[k] = sigmoid(s);
      }

      double err = 0.0D;
      double[] dOut = new double[this.out];

      for (int k = 0; k < this.out; ++k) {
         double e = o[k] - y[k];
         err += e * e;
         dOut[k] = e * o[k] * (1.0D - o[k]);
      }

      double[] dHid = new double[this.hid];

      for (int j = 0; j < this.hid; ++j) {
         double s = 0.0D;

         for (int k = 0; k < this.out; ++k) {
            s += dOut[k] * this.w2[k][j];
         }

         dHid[j] = s * (1.0D - h[j] * h[j]);
      }

      for (int k = 0; k < this.out; ++k) {
         for (int j = 0; j < this.hid; ++j) {
            this.w2[k][j] -= lr * dOut[k] * h[j];
         }

         this.b2[k] -= lr * dOut[k];
      }

      for (int j = 0; j < this.hid; ++j) {
         for (int i = 0; i < this.in; ++i) {
            this.w1[j][i] -= lr * dHid[j] * x[i];
         }

         this.b1[j] -= lr * dHid[j];
      }

      return err;
   }

   public double train(double[][] X, double[][] Y, int epochs, double lr) {
      double last = 0.0D;
      int n = X.length;

      for (int e = 0; e < epochs; ++e) {
         double tot = 0.0D;

         for (int i = 0; i < n; ++i) {
            tot += this.train1(X[i], Y[i], lr);
         }

         last = tot / (double)Math.max(1, n);
      }

      return last;
   }

   public String serialize() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.in).append(' ').append(this.hid).append(' ').append(this.out).append('\n');

      for (double[] r : this.w1) {
         for (double v : r) {
            sb.append(v).append(' ');
         }
      }

      sb.append('\n');

      for (double v : this.b1) {
         sb.append(v).append(' ');
      }

      sb.append('\n');

      for (double[] r : this.w2) {
         for (double v : r) {
            sb.append(v).append(' ');
         }
      }

      sb.append('\n');

      for (double v : this.b2) {
         sb.append(v).append(' ');
      }

      sb.append('\n');
      return sb.toString();
   }

   public static NeuroNet deserialize(String data) {
      String[] lines = data.split("\n");
      String[] dims = lines[0].trim().split("\\s+");
      int in = Integer.parseInt(dims[0]);
      int hid = Integer.parseInt(dims[1]);
      int out = Integer.parseInt(dims[2]);
      NeuroNet net = new NeuroNet(in, hid, out);
      double[] w1f = parse(lines[1]);
      int idx = 0;

      for (int j = 0; j < hid; ++j) {
         for (int i = 0; i < in; ++i) {
            net.w1[j][i] = w1f[idx++];
         }
      }

      double[] b1f = parse(lines[2]);

      for (int j = 0; j < hid; ++j) {
         net.b1[j] = b1f[j];
      }

      double[] w2f = parse(lines[3]);
      idx = 0;

      for (int k = 0; k < out; ++k) {
         for (int j = 0; j < hid; ++j) {
            net.w2[k][j] = w2f[idx++];
         }
      }

      double[] b2f = parse(lines[4]);

      for (int k = 0; k < out; ++k) {
         net.b2[k] = b2f[k];
      }

      return net;
   }

   private static double[] parse(String line) {
      String[] parts = line.trim().split("\\s+");
      double[] v = new double[parts.length];

      for (int i = 0; i < parts.length; ++i) {
         v[i] = Double.parseDouble(parts[i]);
      }

      return v;
   }
}
