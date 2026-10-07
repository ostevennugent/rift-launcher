import { useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import { APPS, AI_APPS, PRESETS, appById, isAi } from "@/lib/launcher/catalog";
import type { Accent, Density, Glow, PageId, QuickKey } from "@/lib/launcher/types";
import { useLauncher } from "@/lib/launcher/store";
import { Group, PanelFrame, Segment, ToggleRow, press } from "@/components/launcher/bits";
import { cn } from "@/lib/cn";

const ACCENTS: { id: Accent; label: string }[] = [
  { id: "ion", label: "Ion" },
  { id: "signal", label: "Signal" },
  { id: "amber", label: "Amber" },
  { id: "acid", label: "Acid" },
];

const DENSITIES: { id: Density; label: string }[] = [
  { id: "compact", label: "Compact" },
  { id: "regular", label: "Regular" },
  { id: "roomy", label: "Roomy" },
];

const QUICK: { key: QuickKey; label: string }[] = [
  { key: "airplane", label: "Airplane" },
  { key: "wifi", label: "Wi-Fi" },
  { key: "bluetooth", label: "Bluetooth" },
  { key: "dnd", label: "Silent" },
  { key: "flashlight", label: "Light" },
  { key: "rotation", label: "Rotate" },
  { key: "hotspot", label: "Hotspot" },
];

export function SettingsPanel() {
  const open = useLauncher((s) => s.layer === "settings");
  const close = useLauncher((s) => s.setLayer);
  const settings = useLauncher((s) => s.settings);
  const patch = useLauncher((s) => s.patch);
  const patchWidgets = useLauncher((s) => s.patchWidgets);
  const patchOverlays = useLauncher((s) => s.patchOverlays);
  const setPageEnabled = useLauncher((s) => s.setPageEnabled);
  const toggleQuick = useLauncher((s) => s.toggleQuick);
  const toggleDock = useLauncher((s) => s.toggleDock);
  const moveDock = useLauncher((s) => s.moveDock);
  const applyPreset = useLauncher((s) => s.applyPreset);
  const showHint = useLauncher((s) => s.showHint);
  const reset = useLauncher((s) => s.reset);
  const dockNote = useLauncher((s) => s.dockNote);
  const [arm, setArm] = useState(false);

  return (
    <PanelFrame open={open} label="Launcher settings" title="Settings" onClose={() => close(null)} origin="right">
      <div className="px-3 pb-10">
        <p className="pt-4 text-sm text-pretty text-muted">
          Pages, modules, overlays, and the dock. Accent and radios stay when you apply a preset.
        </p>

        <Group title="Device">
          <label className="block border-b border-border py-3 last:border-b-0">
            <span className="mb-2 block font-mono text-xs tracking-widest text-muted uppercase">Name</span>
            <input
              value={settings.deviceName}
              maxLength={18}
              spellCheck={false}
              aria-label="Device name"
              onChange={(e) => patch({ deviceName: e.target.value.slice(0, 18) })}
              className="h-11 w-full border border-border bg-bg px-3 font-mono text-base text-fg outline-none focus-visible:border-primary"
            />
          </label>
        </Group>

        <Group title="Presets">
          <div className="grid gap-2 py-3">
            {PRESETS.map((preset) => (
              <button
                key={preset.id}
                type="button"
                onClick={() => applyPreset(preset.id)}
                className={cn("flex h-14 flex-col items-start justify-center border border-border px-3 text-left", press)}
              >
                <span className="text-sm">{preset.name}</span>
                <span className="font-mono text-xs text-muted">{preset.detail}</span>
              </button>
            ))}
          </div>
        </Group>

        <Group title="Assistants">
          <p className="py-3 text-sm text-pretty text-muted">
            Ask on any app sends that screen here. The default is listed first. Replies stay on this device.
          </p>
          {AI_APPS.map((app) => {
            const on = settings.defaultAi === app.id;
            return (
              <button
                key={app.id}
                type="button"
                aria-pressed={on}
                onClick={() => {
                  if (isAi(app.id)) patch({ defaultAi: app.id });
                }}
                className="flex min-h-14 w-full items-center justify-between gap-3 border-b border-border py-2 text-left last:border-b-0"
              >
                <span>
                  <span className="block text-sm">{app.name}</span>
                  <span className="block text-xs text-pretty text-muted">{app.blurb}</span>
                </span>
                <span className={cn("font-mono text-xs", on ? "text-primary" : "text-muted")}>{on ? "Default" : "Set"}</span>
              </button>
            );
          })}
        </Group>

        <Group title="Look">
          <Segment label="Accent" value={settings.accent} options={ACCENTS} onChange={(accent) => patch({ accent })} />
          <Segment
            label="Density"
            value={settings.density}
            options={DENSITIES}
            onChange={(density) => patch({ density })}
          />
          <Segment
            label="Status"
            value={settings.statusStyle}
            options={[
              { id: "full", label: "Full" },
              { id: "compact", label: "Compact" },
            ]}
            onChange={(statusStyle) => patch({ statusStyle })}
          />
          <Segment
            label="Glow"
            value={settings.glow}
            options={[
              { id: "off" as Glow, label: "Off" },
              { id: "low" as Glow, label: "Low" },
              { id: "high" as Glow, label: "High" },
            ]}
            onChange={(glow) => patch({ glow })}
          />
          <ToggleRow
            label="Icon labels"
            checked={settings.showLabels}
            onChange={(showLabels) => patch({ showLabels })}
          />
          <ToggleRow
            label="Motion"
            hint="Page changes and the ticker."
            checked={settings.motion}
            onChange={(motion) => patch({ motion })}
          />
        </Group>

        <Group title="Clock">
          <Segment
            label="Format"
            value={settings.clockFormat}
            options={[
              { id: "24" as const, label: "24h" },
              { id: "12" as const, label: "12h" },
            ]}
            onChange={(clockFormat) => patch({ clockFormat })}
          />
          <ToggleRow
            label="Seconds"
            checked={settings.showSeconds}
            onChange={(showSeconds) => patch({ showSeconds })}
          />
        </Group>

        <Group title="Pages">
          {(["brief", "apps", "deck"] as PageId[]).map((id) => (
            <ToggleRow
              key={id}
              label={id === "brief" ? "Brief" : id === "apps" ? "Apps" : "Deck"}
              hint="At least one page stays on."
              checked={settings.pages[id]}
              onChange={(on) => setPageEnabled(id, on)}
            />
          ))}
        </Group>

        <Group title="Brief modules">
          <ToggleRow label="Weather" checked={settings.widgets.weather} onChange={(v) => patchWidgets({ weather: v })} />
          <ToggleRow label="Agenda" checked={settings.widgets.agenda} onChange={(v) => patchWidgets({ agenda: v })} />
          <ToggleRow label="Comms" checked={settings.widgets.comms} onChange={(v) => patchWidgets({ comms: v })} />
          <ToggleRow label="Transit" checked={settings.widgets.transit} onChange={(v) => patchWidgets({ transit: v })} />
          <ToggleRow label="Vitals" checked={settings.widgets.vitals} onChange={(v) => patchWidgets({ vitals: v })} />
          <ToggleRow label="Wire" checked={settings.widgets.headlines} onChange={(v) => patchWidgets({ headlines: v })} />
        </Group>

        <Group title="Overlays">
          <ToggleRow
            label="Overlay stack"
            hint="Master switch for every layer below."
            checked={settings.overlays.enabled}
            onChange={(enabled) => patchOverlays({ enabled })}
          />
          <ToggleRow label="Corner brackets" checked={settings.overlays.brackets} onChange={(brackets) => patchOverlays({ brackets })} />
          <ToggleRow label="Scanlines" checked={settings.overlays.scanline} onChange={(scanline) => patchOverlays({ scanline })} />
          <ToggleRow label="Grid" checked={settings.overlays.grid} onChange={(grid) => patchOverlays({ grid })} />
          <ToggleRow label="Ticker" checked={settings.overlays.ticker} onChange={(ticker) => patchOverlays({ ticker })} />
          <ToggleRow
            label="Next-event chip"
            hint="Drag it to park it."
            checked={settings.overlays.chip}
            onChange={(chip) => patchOverlays({ chip })}
          />
        </Group>

        <Group title="Dock">
          <div className="border-b border-border py-3">
            <div className="mb-2 font-mono text-xs tracking-widest text-muted uppercase">Order</div>
            {settings.dock.length === 0 ? <p className="text-sm text-muted">Dock is empty. Add up to four.</p> : null}
            <ul>
              {settings.dock.map((id, index) => {
                const app = appById(id);
                if (!app) return null;
                return (
                  <li key={id} className="flex items-center gap-2">
                    <span className="min-w-0 flex-1 truncate text-sm">{app.name}</span>
                    <button
                      type="button"
                      aria-label={`Move ${app.name} up`}
                      disabled={index === 0}
                      onClick={() => moveDock(index, -1)}
                      className="flex size-11 items-center justify-center disabled:opacity-30"
                    >
                      <ChevronUp className="size-4" />
                    </button>
                    <button
                      type="button"
                      aria-label={`Move ${app.name} down`}
                      disabled={index === settings.dock.length - 1}
                      onClick={() => moveDock(index, 1)}
                      className="flex size-11 items-center justify-center disabled:opacity-30"
                    >
                      <ChevronDown className="size-4" />
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
          <div className="py-3">
            <div className="mb-2 font-mono text-xs tracking-widest text-muted uppercase">
              Add or remove · {settings.dock.length}/4
            </div>
            {dockNote ? <p className="mb-2 font-mono text-xs text-hot">{dockNote}</p> : null}
            <div className="grid grid-cols-4 gap-1">
              {APPS.map((app) => {
                const on = settings.dock.includes(app.id);
                const Icon = app.icon;
                return (
                  <button
                    key={app.id}
                    type="button"
                    aria-pressed={on}
                    onClick={() => toggleDock(app.id)}
                    className={cn(
                      "flex h-14 flex-col items-center justify-center gap-1 border text-xs",
                      on ? "border-primary text-primary" : "border-border text-muted",
                    )}
                  >
                    <Icon className="size-4" />
                    <span className="w-full truncate px-1">{app.name}</span>
                  </button>
                );
              })}
            </div>
          </div>
        </Group>

        <Group title="Radios">
          {QUICK.map((item) => (
            <ToggleRow
              key={item.key}
              label={item.label}
              checked={settings.quick[item.key]}
              onChange={() => toggleQuick(item.key)}
            />
          ))}
        </Group>

        <Group title="Gestures">
          <ul className="space-y-2 py-3 text-sm text-pretty text-muted">
            <li>Status bar opens controls.</li>
            <li>Swipe sideways between pages. Arrow keys do the same.</li>
            <li>Long-press an icon for a preview.</li>
            <li>Drag the next-event chip to move it.</li>
            <li>Ask on an app hands that screen to Relay, Draft, or Trace.</li>
            <li>The grid icon on the dock opens every app.</li>
          </ul>
          <button
            type="button"
            onClick={showHint}
            className={cn("mb-3 h-11 w-full border border-border text-sm", press)}
          >
            Show the hint again
          </button>
        </Group>

        <button
          type="button"
          onClick={() => {
            if (!arm) {
              setArm(true);
              return;
            }
            reset();
            setArm(false);
          }}
          className={cn("mt-6 h-11 w-full border border-hot text-sm text-hot", press)}
        >
          {arm ? "Confirm reset" : "Reset launcher"}
        </button>
        <p className="mt-4 text-center font-mono text-xs text-muted">RIFT 0.9 · information launcher</p>
      </div>
    </PanelFrame>
  );
}
