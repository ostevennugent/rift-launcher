import { AGENDA, MAIL, PLACES, THREADS, TRANSIT, TRACKS, appById } from "@/lib/launcher/catalog";
import type { AiId, AskContext, Pulse } from "@/lib/launcher/types";

export type AssistSnap = {
  note: string;
  pulse: Pulse;
  focus: AskContext | null;
  musicTrack: number;
  shots: string[];
};

export function briefContext(snap: AssistSnap): AskContext {
  const next = AGENDA[0];
  return {
    appId: "brief",
    title: "Brief",
    body: [
      next ? `Next: ${next.time} ${next.title}, ${next.where}.` : "No events.",
      `Transit: ${TRANSIT.line} is ${TRANSIT.status}. ${TRANSIT.walk} to ${TRANSIT.stop}.`,
      "Harbor is clear, 18°.",
      `Device: cpu ${snap.pulse.cpu}%, memory ${snap.pulse.mem}%, ${snap.pulse.temp}°.`,
    ].join(" "),
  };
}

export function contextFor(appId: string, snap: AssistSnap): AskContext {
  if (snap.focus?.appId === appId && snap.focus.body.trim()) return snap.focus;
  const app = appById(appId);
  switch (appId) {
    case "notes":
      return { appId, title: "Notes", body: snap.note.trim() || "The note is empty." };
    case "mail":
      return {
        appId,
        title: "Inbox",
        body: MAIL.map((item) => `${item.subject} from ${item.from}. ${item.body}`).join(" "),
      };
    case "messages":
      return {
        appId,
        title: "Messages",
        body: THREADS.map((item) => `${item.name}: ${item.lines.join(" ")}`).join(" "),
      };
    case "calendar":
      return {
        appId,
        title: "Today",
        body: AGENDA.map((item) => `${item.time} ${item.title}, ${item.where}.`).join(" "),
      };
    case "maps":
      return {
        appId,
        title: "Places",
        body: PLACES.map((place) => `${place.name}, ${place.meta}.`).join(" "),
      };
    case "music": {
      const track = TRACKS[snap.musicTrack] ?? TRACKS[0];
      return {
        appId,
        title: track?.title ?? "Music",
        body: track ? `${track.title} by ${track.artist}, ${track.length}.` : "Nothing playing.",
      };
    }
    case "photos":
    case "camera":
      return {
        appId,
        title: "Roll",
        body: snap.shots[0] ? `Latest frame: ${snap.shots[0]}. ${snap.shots.length} on the roll.` : "The roll is empty.",
      };
    case "weather":
      return { appId, title: "Harbor", body: "Clear, 18°. Wind 8 km/h. Humidity 61%." };
    default:
      return { appId, title: app?.name ?? "Screen", body: app?.blurb ?? "No extra detail on this screen." };
  }
}

export function suggestions(ai: AiId, ctx: AskContext | null): string[] {
  if (ai === "draft") return ctx ? ["Tighten this", "Shorter"] : ["Tighten my note", "Shorter"];
  if (ai === "trace") return ["When do I leave?", "Walking times"];
  return ctx ? ["What matters here?", "What should I do next?"] : ["What should I do next?", "How is the device?"];
}

function tighten(text: string, shorter: boolean) {
  const clean = text.replace(/\s+/g, " ").trim();
  if (!clean) return "Nothing on this screen to rewrite.";
  const parts = clean.split(/(?<=[.!?])\s+/).filter(Boolean);
  const limit = shorter ? 1 : 2;
  const out = (parts.length ? parts.slice(0, limit) : [clean]).join(" ");
  const cap = shorter ? 140 : 240;
  return out.length > cap ? `${out.slice(0, cap).trim()}…` : out;
}

function relayReply(q: string, ctx: AskContext | null, snap: AssistSnap) {
  if (q.includes("device") || q.includes("vital") || q.includes("cpu")) {
    const p = snap.pulse;
    return `CPU ${p.cpu}%, memory ${p.mem}%, ${p.temp}°. Uplink ${p.up} mb/s.`;
  }
  if (q.includes("transit") || q.includes("line") || q.includes("train")) {
    return `${TRANSIT.line} is ${TRANSIT.status}. ${TRANSIT.stop} is a ${TRANSIT.walk}.`;
  }
  if (q.includes("next") || q.includes("today") || q.includes("agenda") || q.includes("should i do")) {
    const next = AGENDA[0];
    const follow = AGENDA[1];
    return next
      ? `Next is ${next.title} at ${next.time} (${next.where}). After that, ${follow?.title ?? "the day is clear"} at ${follow?.time ?? ""}. ${TRANSIT.line} is ${TRANSIT.status}.`
      : "Nothing is on the agenda.";
  }
  if (ctx) {
    const clipped = ctx.body.length > 280 ? `${ctx.body.slice(0, 280).trim()}…` : ctx.body;
    return `From ${ctx.title}. ${clipped}`;
  }
  const next = AGENDA[0];
  return next
    ? `Next is ${next.title} at ${next.time}. ${TRANSIT.line} is ${TRANSIT.status}. Harbor is clear, 18°.`
    : "The brief is empty.";
}

function draftReply(prompt: string, ctx: AskContext | null, snap: AssistSnap) {
  const shorter = prompt.toLowerCase().includes("short");
  const source = ctx?.body?.trim() || snap.note.trim();
  if (!source) return "Open a note, a letter, or a message first. Draft only rewrites what is already on the device.";
  return tighten(source, shorter);
}

function traceReply(q: string, ctx: AskContext | null) {
  if (q.includes("walk") || q.includes("place") || q.includes("map")) {
    return PLACES.map((place) => `${place.name}: ${place.meta}.`).join(" ");
  }
  const hit = PLACES.find((place) => ctx?.body.toLowerCase().includes(place.name.toLowerCase()));
  if (hit) return `${hit.name} is ${hit.meta}. Design review is still 09:40 in Room 4.`;
  return `Design review is 09:40 in Room 4. ${TRANSIT.stop} is a ${TRANSIT.walk}, so leave by 09:36. ${TRANSIT.line} is ${TRANSIT.status}. Next block is the transit window at 12:15.`;
}

export function reply(ai: AiId, prompt: string, ctx: AskContext | null, snap: AssistSnap) {
  const q = prompt.toLowerCase();
  if (ai === "draft") return draftReply(prompt, ctx, snap);
  if (ai === "trace") return traceReply(q, ctx);
  return relayReply(q, ctx, snap);
}
