import { readFileSync } from "node:fs";
/**
 * Runtime configuration for the mcpfabric MCP server.
 *
 * All values come from environment variables so the server can be configured from an MCP client's
 * launch config (e.g. Claude Desktop `mcpServers` entry) without code changes.
 */
export interface ServerConfig {
  /** Base URL of the in-game HTTP bridge exposed by the Fabric mod. */
  bridgeUrl: string;
  /** Bearer token from the mod's config/mcpfabric.config.json file. */
  token: string | undefined;
  /** Per-request timeout for bridge calls, in milliseconds. */
  timeoutMs: number;
  /** Transport used to talk to the MCP client. */
  transport: "stdio" | "http";
  /** Port for the streamable-HTTP transport (only used when transport === "http"). */
  httpPort: number;
  /** Serve the agent runtime tools (memory, goals, map, jobs). */
  agent: boolean;
  /** Directory of the agent database (default ~/.mcpfabric). */
  dataDir: string | undefined;
  /** Fixed world id for agent memory instead of the one reported by the game. */
  world: string | undefined;
}

function int(value: string | undefined, fallback: number): number {
  const n = value === undefined ? NaN : Number.parseInt(value, 10);
  return Number.isFinite(n) ? n : fallback;
}

/** Pain: token straight from the mod config, so it never has to be copied into the MCP client. */
function tokenFromFile(path: string | undefined): string | undefined {
  if (!path) return undefined;
  try {
    const token = JSON.parse(readFileSync(path, "utf8")).token;
    return typeof token === "string" && token ? token : undefined;
  } catch {
    return undefined;
  }
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): ServerConfig {
  const rawUrl = env.MCPFABRIC_URL ?? "http://127.0.0.1:25599";
  // Normalise: strip a trailing slash so we can append paths cleanly.
  const bridgeUrl = rawUrl.replace(/\/+$/, "");

  const transport = (env.MCPFABRIC_TRANSPORT ?? "stdio").toLowerCase();
  if (transport !== "stdio" && transport !== "http") {
    throw new Error(`MCPFABRIC_TRANSPORT must be "stdio" or "http", got "${transport}"`);
  }

  return {
    bridgeUrl,
    token: env.MCPFABRIC_TOKEN || tokenFromFile(env.MCPFABRIC_CONFIG),
    timeoutMs: int(env.MCPFABRIC_TIMEOUT_MS, 15000),
    transport,
    httpPort: int(env.MCPFABRIC_HTTP_PORT, 25600),
    agent: !/^(0|false|off|no)$/i.test(env.MCPFABRIC_AGENT ?? ""),
    dataDir: env.MCPFABRIC_DATA_DIR || undefined,
    world: env.MCPFABRIC_WORLD || undefined,
  };
}
