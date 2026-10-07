import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  FILES,
  HOURS,
  MAIL,
  PAGES,
  PEOPLE,
  PLACES,
  RECENTS,
  TRACKS,
  WALLET,
  ZONES,
  appById,
  isAi,
} from "@/lib/launcher/catalog";
import { AGENDA } from "@/lib/launcher/catalog";
import { THREADS } from "@/lib/launcher/catalog";
import { formatClock, formatZone, useNow } from "@/lib/launcher/clock";
import { useLauncher } from "@/lib/launcher/store";
import { Kicker, PanelFrame, press } from "@/components/launcher/bits";
import { AssistApp } from "@/components/launcher/Assist";
import { cn } from "@/lib/cn";

function ScrollBody({ children }: { children: ReactNode }) {
  return <div className="scroll min-h-0 flex-1 overflow-y-auto px-3 pt-3 pb-8">{children}</div>;
}

function Back({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mb-3 flex h-11 items-center font-mono text-xs tracking-widest text-primary uppercase"
    >
      {label}
    </button>
  );
}

function useScreenFocus(appId: string, title: string | null, body: string | null) {
  const setFocus = useLauncher((s) => s.setFocus);
  useEffect(() => {
    if (!title || !body) {
      setFocus(null);
      return;
    }
    setFocus({ appId, title, body });
    return () => setFocus(null);
  }, [appId, title, body, setFocus]);
}

function PhoneView() {
  const startCall = useLauncher((s) => s.startCall);
  return (
    <ScrollBody>
      <Kicker>Recents</Kicker>
      <ul className="mt-2">
        {RECENTS.map((item) => (
          <li key={item.name} className="flex items-center gap-3 border-b border-border py-2">
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm">{item.name}</span>
              <span className="block font-mono text-xs text-muted">{item.meta}</span>
            </span>
            <button
              type="button"
              onClick={() => startCall(item.name)}
              className={cn("h-11 bg-primary px-3 text-sm text-bg", press)}
            >
              Call
            </button>
          </li>
        ))}
      </ul>
    </ScrollBody>
  );
}

function MessagesView() {
  const [id, setId] = useState<string | null>(null);
  const [extra, setExtra] = useState<Record<string, string[]>>({});
  const [draft, setDraft] = useState("");
  const thread = THREADS.find((item) => item.id === id);
  useScreenFocus(
    "messages",
    thread?.name ?? null,
    thread ? [...thread.lines, ...(extra[thread.id] ?? [])].join(" ") : null,
  );
  if (!thread) {
    return (
      <ScrollBody>
        {THREADS.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => setId(item.id)}
            className="flex min-h-11 w-full flex-col justify-center border-b border-border py-2 text-left"
          >
            <span className="text-sm">{item.name}</span>
            <span className="truncate font-mono text-xs text-muted">{item.lines[item.lines.length - 1]}</span>
          </button>
        ))}
      </ScrollBody>
    );
  }
  const lines = [...thread.lines, ...(extra[thread.id] ?? [])];
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="scroll min-h-0 flex-1 overflow-y-auto px-3 pt-3">
        <Back label="All messages" onClick={() => setId(null)} />
        {lines.map((line, index) => (
          <p key={`${thread.id}-${index}`} className="mb-2 border border-border bg-surface px-3 py-2 text-sm text-pretty">
            {line}
          </p>
        ))}
      </div>
      <form
        className="flex border-t border-border"
        onSubmit={(e) => {
          e.preventDefault();
          const text = draft.trim();
          if (!text) return;
          setExtra((prev) => ({ ...prev, [thread.id]: [...(prev[thread.id] ?? []), text] }));
          setDraft("");
        }}
      >
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Reply"
          aria-label="Reply"
          className="h-12 min-w-0 flex-1 bg-transparent px-3 text-base outline-none placeholder:text-muted"
        />
        <button type="submit" className="h-12 bg-primary px-4 text-sm text-bg">
          Send
        </button>
      </form>
    </div>
  );
}

function MailView() {
  const [id, setId] = useState<string | null>(null);
  const mail = MAIL.find((item) => item.id === id);
  useScreenFocus("mail", mail?.subject ?? null, mail ? `${mail.from}. ${mail.body}` : null);
  if (!mail) {
    return (
      <ScrollBody>
        {MAIL.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => setId(item.id)}
            className="flex min-h-11 w-full flex-col justify-center border-b border-border py-2 text-left"
          >
            <span className="text-sm">{item.subject}</span>
            <span className="font-mono text-xs text-muted">{item.from}</span>
          </button>
        ))}
      </ScrollBody>
    );
  }
  return (
    <ScrollBody>
      <Back label="Inbox" onClick={() => setId(null)} />
      <h3 className="text-lg">{mail.subject}</h3>
      <p className="mt-1 font-mono text-xs text-muted">{mail.from}</p>
      <p className="mt-4 text-sm leading-relaxed text-pretty">{mail.body}</p>
    </ScrollBody>
  );
}

function BrowserView() {
  const [title, setTitle] = useState<string | null>(null);
  const page = PAGES.find((item) => item.title === title);
  if (!page) {
    return (
      <ScrollBody>
        <Kicker>Saved</Kicker>
        <ul className="mt-2">
          {PAGES.map((item) => (
            <li key={item.title}>
              <button
                type="button"
                onClick={() => setTitle(item.title)}
                className="flex min-h-11 w-full flex-col justify-center border-b border-border py-2 text-left"
              >
                <span className="text-sm">{item.title}</span>
                <span className="truncate font-mono text-xs text-muted">{item.detail}</span>
              </button>
            </li>
          ))}
        </ul>
      </ScrollBody>
    );
  }
  return (
    <ScrollBody>
      <Back label="Saved pages" onClick={() => setTitle(null)} />
      <h3 className="text-lg">{page.title}</h3>
      <p className="mt-3 text-sm leading-relaxed text-pretty">{page.detail}</p>
      <p className="mt-4 font-mono text-xs text-muted">Offline in this preview. The page is a saved brief, not the live web.</p>
    </ScrollBody>
  );
}

function MapsView() {
  const [route, setRoute] = useState<string | null>(null);
  return (
    <ScrollBody>
      <Kicker>Places</Kicker>
      {route ? <p className="mt-2 font-mono text-xs text-primary">Walking · {route}</p> : null}
      <ul className="mt-2">
        {PLACES.map((place) => (
          <li key={place.name} className="flex items-center gap-3 border-b border-border py-2">
            <span className="min-w-0 flex-1">
              <span className="block text-sm">{place.name}</span>
              <span className="block font-mono text-xs text-muted">{place.meta}</span>
            </span>
            <button
              type="button"
              onClick={() => setRoute(place.name)}
              className={cn("h-11 border border-border px-3 text-sm", press)}
            >
              Go
            </button>
          </li>
        ))}
      </ul>
    </ScrollBody>
  );
}

function CameraView() {
  const now = useNow();
  const addShot = useLauncher((s) => s.addShot);
  const shots = useLauncher((s) => s.shots);
  return (
    <ScrollBody>
      <div className="flex h-48 items-center justify-center border border-border bg-surface">
        <span className="font-mono text-xs tracking-widest text-muted uppercase">Harbor · viewfinder</span>
      </div>
      <button
        type="button"
        onClick={() => {
          const stamp = now ? formatClock(now, "24", true) : "frame";
          addShot(`${stamp} · Harbor`);
        }}
        className={cn("mt-3 h-11 w-full bg-primary text-sm text-bg", press)}
      >
        Shutter
      </button>
      <p className="mt-3 font-mono text-xs text-muted">{shots.length} frame{shots.length === 1 ? "" : "s"} in the roll</p>
    </ScrollBody>
  );
}

function PhotosView() {
  const shots = useLauncher((s) => s.shots);
  const openApp = useLauncher((s) => s.openApp);
  return (
    <ScrollBody>
      <Kicker>Roll</Kicker>
      {shots.length === 0 ? (
        <div className="mt-3">
          <p className="text-sm text-pretty text-muted">No frames yet.</p>
          <button type="button" onClick={() => openApp("camera")} className={cn("mt-3 h-11 bg-primary px-4 text-sm text-bg", press)}>
            Open camera
          </button>
        </div>
      ) : (
        <ul className="mt-2">
          {shots.map((shot, index) => (
            <li key={`${shot}-${index}`} className="border-b border-border py-3 font-mono text-xs text-muted">
              {shot}
            </li>
          ))}
        </ul>
      )}
    </ScrollBody>
  );
}

function CalendarView() {
  const now = useNow();
  return (
    <ScrollBody>
      <Kicker>Today</Kicker>
      <p className="mt-2 font-mono text-xs text-muted">{now ? now.toDateString() : "—"}</p>
      <ul className="mt-3">
        {AGENDA.map((item) => (
          <li key={item.time} className="flex gap-3 border-b border-border py-3">
            <span className="w-12 font-mono text-xs text-muted tabular-nums">{item.time}</span>
            <span>
              <span className="block text-sm">{item.title}</span>
              <span className="font-mono text-xs text-muted">{item.where}</span>
            </span>
          </li>
        ))}
      </ul>
    </ScrollBody>
  );
}

function MusicView() {
  const music = useLauncher((s) => s.music);
  const toggle = useLauncher((s) => s.togglePlay);
  const skip = useLauncher((s) => s.skip);
  const playTrack = useLauncher((s) => s.playTrack);
  const track = TRACKS[music.track] ?? TRACKS[0];
  return (
    <ScrollBody>
      <Kicker>Now</Kicker>
      <h3 className="mt-2 text-2xl">{track?.title}</h3>
      <p className="font-mono text-xs text-muted">
        {track?.artist} · {track?.length}
      </p>
      <div className="mt-4 flex gap-2">
        <button type="button" aria-label="Previous track" onClick={() => skip(-1)} className={cn("h-11 border border-border px-3 text-sm", press)}>
          Prev
        </button>
        <button type="button" onClick={toggle} className={cn("h-11 bg-primary px-4 text-sm text-bg", press)}>
          {music.playing ? "Pause" : "Play"}
        </button>
        <button type="button" aria-label="Next track" onClick={() => skip(1)} className={cn("h-11 border border-border px-3 text-sm", press)}>
          Next
        </button>
      </div>
      <ul className="mt-6">
        {TRACKS.map((item, index) => (
          <li key={item.title}>
            <button
              type="button"
              onClick={() => playTrack(index)}
              className={cn(
                "flex min-h-11 w-full items-center justify-between border-b border-border text-left",
                index === music.track && "text-primary",
              )}
            >
              <span>
                <span className="block text-sm">{item.title}</span>
                <span className="font-mono text-xs text-muted">{item.artist}</span>
              </span>
              <span className="font-mono text-xs tabular-nums">{item.length}</span>
            </button>
          </li>
        ))}
      </ul>
    </ScrollBody>
  );
}

function NotesView() {
  const note = useLauncher((s) => s.note);
  const setNote = useLauncher((s) => s.setNote);
  return (
    <textarea
      value={note}
      onChange={(e) => setNote(e.target.value)}
      aria-label="Notes"
      className="min-h-0 flex-1 resize-none bg-transparent px-3 py-3 text-base leading-relaxed outline-none"
    />
  );
}

function FilesView() {
  return (
    <ScrollBody>
      {FILES.map((file) => (
        <div key={file.name} className="flex min-h-11 flex-col justify-center border-b border-border py-2">
          <span className="text-sm">{file.name}</span>
          <span className="font-mono text-xs text-muted">{file.meta}</span>
        </div>
      ))}
    </ScrollBody>
  );
}

function TerminalView() {
  const [lines, setLines] = useState<string[]>(["RIFT shell · type help"]);
  const [cmd, setCmd] = useState("");
  const endRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "nearest" });
  }, [lines]);

  function run(raw: string) {
    const text = raw.trim();
    const c = text.toLowerCase();
    if (!c) return;
    if (c === "clear") {
      setLines([]);
      setCmd("");
      return;
    }
    const state = useLauncher.getState();
    const out = [`> ${text}`];
    if (c === "help") out.push("help, time, vitals, hud, clear, uname");
    else if (c === "time") out.push(new Date().toLocaleString());
    else if (c === "vitals") {
      const pulse = state.pulse;
      out.push(`cpu ${pulse.cpu}%  mem ${pulse.mem}%  temp ${pulse.temp}°  up ${pulse.up} mb/s`);
    } else if (c === "hud") {
      const next = !state.settings.overlays.enabled;
      state.toggleOverlay();
      out.push(next ? "overlay on" : "overlay off");
    } else if (c === "uname") out.push("rift 0.9.0 preview");
    else out.push("unknown command");
    setLines((prev) => [...prev, ...out].slice(-40));
    setCmd("");
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="scroll min-h-0 flex-1 overflow-y-auto px-3 py-3 font-mono text-sm">
        {lines.map((line, index) => (
          <p key={`${index}-${line}`} className="text-pretty">
            {line}
          </p>
        ))}
        <div ref={endRef} />
      </div>
      <form
        className="flex border-t border-border"
        onSubmit={(e) => {
          e.preventDefault();
          run(cmd);
        }}
      >
        <span className="flex items-center pl-3 font-mono text-primary" aria-hidden>
          {">"}
        </span>
        <input
          value={cmd}
          onChange={(e) => setCmd(e.target.value)}
          aria-label="Command"
          spellCheck={false}
          className="h-12 min-w-0 flex-1 bg-transparent px-2 font-mono text-base outline-none"
        />
      </form>
    </div>
  );
}

function WalletView() {
  return (
    <ScrollBody>
      {WALLET.map((item) => (
        <div key={item.name} className="flex min-h-11 items-center justify-between gap-3 border-b border-border py-2">
          <span>
            <span className="block text-sm">{item.name}</span>
            <span className="font-mono text-xs text-muted">{item.meta}</span>
          </span>
          <span className="font-mono text-sm tabular-nums">{item.value}</span>
        </div>
      ))}
    </ScrollBody>
  );
}

function WeatherView() {
  return (
    <ScrollBody>
      <div className="text-5xl text-primary tabular-nums">18°</div>
      <p className="mt-1 font-mono text-xs tracking-widest text-muted uppercase">Harbor · clear</p>
      <p className="mt-3 text-sm text-muted">Wind 8 km/h · Humidity 61% · Visibility 14 km</p>
      <div className="mt-4 grid grid-cols-6 gap-1">
        {HOURS.map((hour) => (
          <div key={hour.t} className="border border-border py-2 text-center">
            <div className="font-mono text-xs text-muted">{hour.t}</div>
            <div className="mt-1 text-sm tabular-nums">{hour.v}</div>
          </div>
        ))}
      </div>
    </ScrollBody>
  );
}

function ClockView() {
  const now = useNow();
  const hour12 = useLauncher((s) => s.settings.clockFormat === "12");
  return (
    <ScrollBody>
      {ZONES.map((zone) => (
        <div key={zone.label} className="flex min-h-11 items-center justify-between border-b border-border">
          <span className="text-sm">{zone.label}</span>
          <span className="font-mono text-sm tabular-nums">
            {now ? formatZone(now, hour12, zone.timeZone) : "––:––"}
          </span>
        </div>
      ))}
    </ScrollBody>
  );
}

type CalcState = { display: string; acc: number | null; op: string | null; fresh: boolean };

function formatNum(n: number) {
  if (!Number.isFinite(n)) return "Error";
  const rounded = Math.round(n * 1e6) / 1e6;
  return String(rounded);
}

function applyOp(a: number, op: string, b: number) {
  if (op === "+") return a + b;
  if (op === "−") return a - b;
  if (op === "×") return a * b;
  if (op === "÷") return b === 0 ? Number.NaN : a / b;
  return b;
}

function CalcView() {
  const [calc, setCalc] = useState<CalcState>({ display: "0", acc: null, op: null, fresh: false });

  function onKey(key: string) {
    setCalc((s) => {
      if (key === "C") return { display: "0", acc: null, op: null, fresh: false };
      if (key === "⌫") {
        if (s.fresh || s.display === "Error") return { ...s, display: "0", fresh: false };
        const next = s.display.length <= 1 ? "0" : s.display.slice(0, -1);
        return { ...s, display: next };
      }
      if (key === "%") {
        const n = parseFloat(s.display);
        return { ...s, display: formatNum(n / 100), fresh: true };
      }
      if ("0123456789.".includes(key)) {
        if (s.fresh || s.display === "Error") return { ...s, display: key === "." ? "0." : key, fresh: false };
        if (key === "." && s.display.includes(".")) return s;
        if (s.display === "0" && key !== ".") return { ...s, display: key };
        if (s.display.replace("-", "").length >= 12) return s;
        return { ...s, display: s.display + key };
      }
      const ops = "+−×÷";
      if (key === "=" || ops.includes(key)) {
        const current = parseFloat(s.display);
        if (s.op && s.acc != null && !s.fresh && s.display !== "Error") {
          const result = applyOp(s.acc, s.op, current);
          const display = formatNum(result);
          if (key === "=") return { display, acc: null, op: null, fresh: true };
          return { display, acc: Number.isFinite(result) ? result : null, op: key, fresh: true };
        }
        if (key === "=") return { ...s, fresh: true };
        return { ...s, acc: Number.isFinite(current) ? current : null, op: key, fresh: true };
      }
      return s;
    });
  }

  const keys = ["C", "⌫", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "0", ".", "="];
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="px-3 py-6 text-right font-mono text-4xl tabular-nums">{calc.display}</div>
      <div className="grid flex-1 grid-cols-4 gap-1 p-2">
        {keys.map((key) => (
          <button
            key={key}
            type="button"
            onClick={() => onKey(key)}
            className={cn(
              "min-h-11 border border-border text-lg",
              key === "=" && "bg-primary text-bg",
              "+−×÷".includes(key) && key !== "=" && "text-primary",
              key === "0" && "col-span-2",
              press,
            )}
          >
            {key}
          </button>
        ))}
      </div>
    </div>
  );
}

function PeopleView() {
  const startCall = useLauncher((s) => s.startCall);
  return (
    <ScrollBody>
      {PEOPLE.map((person) => (
        <div key={person.number} className="flex items-center gap-3 border-b border-border py-2">
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm">{person.name}</span>
            <span className="block font-mono text-xs text-muted">
              {person.role} · {person.number}
            </span>
          </span>
          <button type="button" onClick={() => startCall(person.name)} className={cn("h-11 bg-primary px-3 text-sm text-bg", press)}>
            Call
          </button>
        </div>
      ))}
    </ScrollBody>
  );
}

function SettingsApp() {
  const settings = useLauncher((s) => s.settings);
  const setLayer = useLauncher((s) => s.setLayer);
  const rows = [
    ["Accent", settings.accent],
    ["Density", settings.density],
    ["Overlays", settings.overlays.enabled ? "On" : "Off"],
    ["Dock", String(settings.dock.length)],
  ];
  return (
    <ScrollBody>
      <dl>
        {rows.map(([label, value]) => (
          <div key={label} className="flex min-h-11 items-center justify-between border-b border-border">
            <dt className="font-mono text-xs tracking-widest text-muted uppercase">{label}</dt>
            <dd className="text-sm capitalize">{value}</dd>
          </div>
        ))}
      </dl>
      <button
        type="button"
        onClick={() => setLayer("settings")}
        className={cn("mt-4 h-11 w-full bg-primary text-sm text-bg", press)}
      >
        Configure launcher
      </button>
    </ScrollBody>
  );
}

function AppBody({ id }: { id: string }) {
  switch (id) {
    case "phone":
      return <PhoneView />;
    case "messages":
      return <MessagesView />;
    case "mail":
      return <MailView />;
    case "browser":
      return <BrowserView />;
    case "maps":
      return <MapsView />;
    case "camera":
      return <CameraView />;
    case "photos":
      return <PhotosView />;
    case "calendar":
      return <CalendarView />;
    case "music":
      return <MusicView />;
    case "notes":
      return <NotesView />;
    case "files":
      return <FilesView />;
    case "terminal":
      return <TerminalView />;
    case "wallet":
      return <WalletView />;
    case "weather":
      return <WeatherView />;
    case "clock":
      return <ClockView />;
    case "calc":
      return <CalcView />;
    case "people":
      return <PeopleView />;
    case "relay":
      return <AssistApp id="relay" />;
    case "draft":
      return <AssistApp id="draft" />;
    case "trace":
      return <AssistApp id="trace" />;
    case "settings":
      return <SettingsApp />;
    default:
      return (
        <ScrollBody>
          <p className="text-sm text-muted">This app is not on the device.</p>
        </ScrollBody>
      );
  }
}

export function AppView() {
  const open = useLauncher((s) => s.layer === "app");
  const appId = useLauncher((s) => s.appId);
  const close = useLauncher((s) => s.setLayer);
  const ask = useLauncher((s) => s.openAskFromApp);
  const app = appId ? appById(appId) : undefined;
  return (
    <PanelFrame
      open={open}
      label={app?.name ?? "App"}
      title={app?.name ?? "App"}
      onClose={() => close(null)}
      origin="right"
      scroll={false}
      onAsk={app && !isAi(app.id) ? ask : undefined}
    >
      {app ? <AppBody key={app.id} id={app.id} /> : null}
    </PanelFrame>
  );
}
