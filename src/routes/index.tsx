import { createFileRoute } from "@tanstack/react-router";
import { Launcher } from "@/components/launcher/Launcher";

export const Route = createFileRoute("/")({ component: Home });

function Home() {
  return <Launcher />;
}
