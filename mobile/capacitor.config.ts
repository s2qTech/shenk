import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "io.s2qtech.shenk",
  appName: "身刻",
  webDir: "dist",
  bundledWebRuntime: false,
  android: {
    allowMixedContent: false,
    captureInput: true
  }
};

export default config;
