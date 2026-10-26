import { setLanguage } from "../ui/i18n.mjs";
import { loadConfig, loadDevices, selectDevice } from "./config.mjs";
import { PhoneConnection } from "./transport.mjs";
import {
  Preview,
  PANEL_META,
  isLocked,
  connectionState,
  connectionMessage,
} from "./preview.mjs";
import { callTool } from "./tools.mjs";
import { MidsceneController } from "./midscene.mjs";

export function createRuntime() {
  let phone;
  let pairRevision = 0;
  const preview = new Preview();
  const midscene = new MidsceneController();
  let queue = Promise.resolve();
  return {
    close() {
      midscene.cancel();
      phone?.close();
      midscene.pairProcess?.kill();
    },
    async call(request) {
      const { name, arguments: args = {} } = request.params;
      if (name === "tapilot_midscene" && args.action === "status")
        return midscene
          .configure(args)
          .catch((error) => ({
            isError: true,
            content: [{ type: "text", text: error.message }],
          }));
      if ((name === "tapilot_disconnect" && Object.keys(args).length === 0) ||
          (name === "tapilot_devices" && ["connect", "disconnect", "forget"].includes(args.action) &&
           (args.action === "connect" || !args.device_id || args.device_id === phone?.config.fingerprint)))
        midscene.cancel();
      if (midscene.active && name === "tapilot_preview_control")
        return Promise.resolve({
          isError: true,
          content: [
            {
              type: "text",
              text: "Midscene is running. Pause it before manual control.",
            },
          ],
        });
      // A hidden panel stops preview work without releasing the CLI-owned transport.
      if (
        name === "tapilot_preview" &&
        args.action === "suspend" &&
        Object.keys(args).every(k => ["action", "viewer_id"].includes(k))
      ) {
        if (midscene.active)
          return Promise.resolve({
            content: [],
            structuredContent: { suspended: false, active: true },
          });
        preview.invalidateViewer(args.viewer_id);
        return Promise.resolve({
          content: [],
          structuredContent: { suspended: true },
        });
      }
      // Privacy pause must interrupt an AI worker instead of waiting behind it.
      if (
        name === "tapilot_preview" &&
        args.action === "pause" &&
        Object.keys(args).every(k => ["action", "viewer_id"].includes(k))
      ) {
        preview.pause("manual");
        midscene.cancel();
        midscene.invalidate();
        return Promise.resolve({
          content: [
            { type: "text", text: "Preview paused; Midscene cancelled" },
          ],
          structuredContent: { state: "manual" },
          _meta: PANEL_META,
        });
      }
      // Preview capture does not update the model action frame and can stay live during AI.
      if (
        midscene.active &&
        name === "tapilot_preview_frame" &&
        Object.keys(args).every(k => k === "viewer_id")
      )
        return preview.frame(phone, args.viewer_id);
      const run = queue.then(async () => {
        try {
          setLanguage(
            phone?.config?.language ??
              (await loadConfig().catch(() => ({}))).language ??
              "en",
          );
          if (name === "tapilot_midscene" && midscene.isAdmin(args.action))
            return await midscene.configure(args);
          const explicitResume = name === 'tapilot_preview_resume' || (name === 'tapilot_preview' && args.action === 'resume');
          if (explicitResume && Object.keys(args).some(key =>
              !(name === 'tapilot_preview' ? ['action', 'viewer_id'] : []).includes(key)))
            throw new Error('Invalid preview arguments');
          const registry = await loadDevices();
          let selected = registry.devices.find(d => d.fingerprint === registry.activeDeviceId);
          // Another host can change pairing or selection without using this runtime.
          // Release the old lease even for read-only device lists or missing config.
          if (phone && (!selected || !selected.autoConnect ||
              ["host", "port", "token", "fingerprint"].some(key => phone.config[key] !== selected[key]))) {
            phone.close(); phone = undefined;
            preview.reconnect();
            midscene.invalidate();
          }
          if (name === 'tapilot_devices' || name === 'tapilot_disconnect') {
            const action = name === 'tapilot_disconnect' ? 'disconnect' : args.action;
            if (!['list','connect','disconnect','forget'].includes(action) ||
                (name === 'tapilot_disconnect' && Object.keys(args).length > 0) ||
                Object.keys(args).some(key => !['action','device_id'].includes(key)) ||
                (args.device_id !== undefined && !/^[a-f0-9]{64}$/.test(args.device_id))) throw Error('Invalid device request');
            const before = await loadDevices();
            const id = args.device_id ?? before.activeDeviceId;
            if (action !== 'list') {
              await selectDevice(action, id);
              if (action === 'connect' || id === before.activeDeviceId) {
                preview.pause('disconnected');
                midscene.invalidate();
                if (phone?.connected) await callTool(phone,'tapilot_disconnect',{}).catch(() => {});
                phone?.close(); phone = undefined;
              }
              if (action === 'connect') {
                phone = new PhoneConnection(await loadConfig());
                await phone.open();
                preview.reconnect();
              }
            }
            const registry = await loadDevices();
            return {content:[], structuredContent:{state:phone?.connected ? (preview.reason ?? 'live') : 'disconnected',
              devices:registry.devices.map(device => ({id:device.fingerprint,
                name:device.name ?? `Android · ${device.host}`, host:device.host,
                selected:device.fingerprint === registry.activeDeviceId,
                state:phone?.connected && phone.config.fingerprint === device.fingerprint ? 'connected'
                  : device.autoConnect ? 'not_connected' : 'disconnected'}))}};
          }
          if (selected && explicitResume && !selected.autoConnect) {
            await selectDevice('connect', selected.fingerprint);
            selected = {...selected, autoConnect:true};
          }
          if (selected && !selected.autoConnect) {
            phone?.close(); phone = undefined;
            preview.pause('disconnected');
            return {content:[{type:'text',text:'Device disconnected by user. Use connect to resume; pairing is kept.'}],
              isError:!['tapilot_preview_open','tapilot_preview_frame','tapilot_preview'].includes(name),
              structuredContent:{state:'disconnected'}};
          }
          if (name === "tapilot_preview_open") {
            if (Object.keys(args).length)
              throw new Error("Invalid preview arguments");
            preview.observe();
          }
          // Pairing may happen in another host process. Never retain its old token.
          const config = await loadConfig();
          if (
            midscene.pairRevision !== pairRevision ||
            (phone &&
              ["host", "port", "token", "fingerprint"].some(
                (key) => phone.config[key] !== config[key],
              ))
          ) {
            phone?.close();
            phone = undefined;
            preview.reconnect();
            midscene.invalidate();
          }
          pairRevision = midscene.pairRevision;
          phone ??= new PhoneConnection(config);
          if (name === "tapilot_midscene") {
            preview.invalidate();
            const guard = () => {
              if (["manual", "device_locked"].includes(preview.reason))
                throw new Error(
                  "Preview paused; explicitly resume before Midscene operations",
                );
            };
            const result = await midscene.run(phone, args, guard);
            preview.observe();
            return result;
          }
          if (
            ![
              "tapilot_preview_frame",
              "tapilot_preview",
              "tapilot_status",
            ].includes(name)
          )
            midscene.invalidate();
          if (name === "tapilot_preview_open") {
            if (Object.keys(args).length)
              throw new Error("Invalid preview arguments");
            preview.observe();
            return {
              content: [{ type: "text", text: "Android Use screen panel" }],
              structuredContent: { state: preview.reason ?? "live" },
              _meta: PANEL_META,
            };
          }
          if (name.startsWith("tapilot_preview")) {
            if (name === "tapilot_preview_control")
              return await preview.control(phone, args);
            const hasAction = name === "tapilot_preview";
            if (
              Object.keys(args).some((k) => !(hasAction ? ["action", "viewer_id"] : name === "tapilot_preview_frame" ? ["viewer_id"] : []).includes(k)) ||
              (hasAction && !["open", "pause", "resume"].includes(args.action))
            )
              throw new Error("Invalid preview arguments");
            if (name === "tapilot_preview_frame")
              return await preview.frame(phone, args.viewer_id);
            if (
              name === "tapilot_preview_resume" ||
              (hasAction && args.action === "resume")
            )
              return { ...(await preview.resume(phone)), _meta: PANEL_META };
            if (!hasAction) throw new Error("Unknown preview tool");
            if (args.action === "pause") preview.pause("manual");
            else preview.observe();
            return {
              content: [{ type: "text", text: "Android Use screen panel" }],
              structuredContent: { state: preview.reason ?? "live" },
              _meta: PANEL_META,
            };
          }
          if (name === "tapilot_disconnect") preview.pause("disconnected");
          if (preview.reason === "manual" && name !== "tapilot_disconnect")
            throw new Error(
              "Preview paused for private authentication. Wait for user completion and explicitly resume before phone operations.",
            );
          if (!["tapilot_status", "tapilot_screen"].includes(name))
            preview.invalidate();
          const run = () => callTool(phone, name, args);
          let value;
          try {
            if (
              preview.reason === "device_locked" &&
              ![
                "tapilot_status",
                "tapilot_screen",
                "tapilot_disconnect",
              ].includes(name)
            )
              throw new Error("Unlock the phone first");
            value = await run();
          } catch (error) {
            if (!isLocked(error)) throw error;
            preview.pause("device_locked");
            throw error;
          }
          if (
            !value.isError &&
            ["tapilot_status", "tapilot_screen"].includes(name)
          ) {
            if (preview.reason === "device_locked") preview.reason = null;
            preview.observe();
          }
          return value;
        } catch (error) {
          if (isLocked(error)) preview.pause("device_locked");
          throw error;
        }
      });
      queue = run.catch(() => {});
      // Never hold the device queue while waiting for a human: the App must still
      // receive the locked state and clear its image while the user unlocks.
      return run
        .then((value) =>
          value.structuredContent
            ? {
                ...value,
                structuredContent: {
                  language: phone?.config?.language ?? "en",
                  device_host: phone?.config?.host,
                  ...value.structuredContent,
                },
              }
            : value,
        )
        .catch((error) => ({
          isError: true,
          content: [
            { type: "text", text: connectionMessage(error) },
            ...(error.installation
              ? [{ type: "text", text: error.installation.download_links }]
              : []),
          ],
          structuredContent: {
            language: phone?.config?.language ?? "en",
            device_host: phone?.config?.host,
            ...(error.installation ? { installation: error.installation } : {}),
            state: ["manual", "device_locked"].includes(preview.reason)
              ? preview.reason
              : connectionState(error),
          },
        }));
    },
  };
}
