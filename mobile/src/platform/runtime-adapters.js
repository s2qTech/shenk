import { Haptics, ImpactStyle } from "@capacitor/haptics";
import { Browser } from "@capacitor/browser";
import { App } from "@capacitor/app";
import { buildTimerUrl } from "../domain/mobile-domain.js";

export function createRuntimeAdapters() {
  return {
    async impact() {
      try { await Haptics.impact({ style: ImpactStyle.Light }); } catch (_error) { /* Web preview has no haptics. */ }
    },
    async openTimer(timerUrl, parameters) {
      const url = buildTimerUrl(timerUrl, { ...parameters, source: "shenk" });
      if (!url) throw new Error("请先在设置中填写计时器地址。");
      await Browser.open({ url, presentationStyle: "fullscreen" });
    },
    async onAppStateChange(listener) {
      return App.addListener("appStateChange", listener);
    },
    async speak(text) {
      if ("speechSynthesis" in globalThis) globalThis.speechSynthesis.speak(new SpeechSynthesisUtterance(text));
    }
  };
}
