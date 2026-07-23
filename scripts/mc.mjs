import { spawn, spawnSync } from 'node:child_process';
import net from 'node:net';
import { existsSync, readFileSync, writeFileSync, rmSync, copyFileSync, readdirSync, unlinkSync, mkdirSync, openSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { homedir } from 'node:os';

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const SERVER_DIR = join(ROOT, 'server');
const PLUGIN_DIR = join(ROOT, 'plugin');
const PID_FILE = join(SERVER_DIR, '.server.pid');
const PIPE_PATH = '\\\\.\\pipe\\mc_plugins_control';

// Both point at machine-local installs — override via env vars if yours differ.
const JAVA25 = process.env.MC_PLUGINS_JAVA25
  ?? 'C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.3.9-hotspot\\bin\\java.exe';
const TRUSTSTORE = process.env.MC_PLUGINS_TRUSTSTORE
  ?? join(homedir(), '.mc_plugins_certs', 'jdk25-cacerts');

function isRunning(pid) {
  if (!pid) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function getPid() {
  if (!existsSync(PID_FILE)) return null;
  const pid = parseInt(readFileSync(PID_FILE, 'utf8').trim(), 10);
  return Number.isFinite(pid) ? pid : null;
}

function pingPipe() {
  return new Promise((resolve) => {
    const sock = net.createConnection(PIPE_PATH);
    sock.on('connect', () => {
      sock.end();
      resolve(true);
    });
    sock.on('error', () => resolve(false));
  });
}

async function cmdStart() {
  const existingPid = getPid();
  if (isRunning(existingPid) && (await pingPipe())) {
    console.log(`Server already running (pid ${existingPid}).`);
    return;
  }

  const jarName = readdirSync(SERVER_DIR).find((f) => /^paper-.*\.jar$/.test(f));
  if (!jarName) throw new Error('No paper-*.jar found in server/');

  const supervisor = spawn(
    process.execPath,
    [fileURLToPath(import.meta.url), '_supervise', jarName],
    {
      cwd: SERVER_DIR,
      detached: true,
      stdio: 'ignore',
    }
  );
  supervisor.unref();
  writeFileSync(PID_FILE, String(supervisor.pid));
  console.log(`Server starting (pid ${supervisor.pid}). Tail server/logs/latest.log to watch it boot.`);
}

// Runs as the detached background process. Owns the java child and the
// local control pipe. Never invoked directly by a user.
function cmdSupervise(jarName) {
  mkdirSync(join(SERVER_DIR, 'logs'), { recursive: true });
  const outFd = openSync(join(SERVER_DIR, 'logs', 'supervisor-stdout.log'), 'a');
  const errFd = openSync(join(SERVER_DIR, 'logs', 'supervisor-stderr.log'), 'a');

  const child = spawn(
    JAVA25,
    [
      `-Djavax.net.ssl.trustStore=${TRUSTSTORE}`,
      '-Djavax.net.ssl.trustStorePassword=changeit',
      '-Xms2G',
      '-Xmx2G',
      '-jar',
      jarName,
      '--nogui',
    ],
    {
      cwd: SERVER_DIR,
      stdio: ['pipe', outFd, errFd],
    }
  );

  const pipeServer = net.createServer((sock) => {
    // Forwards whatever text a local client sends straight to the server
    // console — same trust level as typing at the keyboard. The pipe is
    // OS-local IPC only (no network socket), so this never leaves the
    // machine.
    sock.on('data', (data) => {
      const line = data.toString().trim();
      if (line) child.stdin.write(line + '\n');
    });
    sock.on('error', () => {});
  });
  pipeServer.on('error', () => {});
  pipeServer.listen(PIPE_PATH);

  child.on('exit', () => {
    pipeServer.close();
    if (existsSync(PID_FILE)) rmSync(PID_FILE);
    process.exit(0);
  });
}

async function cmdStop() {
  const pid = getPid();
  const connected = await pingPipe();
  if (!connected) {
    console.log('Server is not running.');
    if (existsSync(PID_FILE)) rmSync(PID_FILE);
    return;
  }

  await new Promise((resolve, reject) => {
    const sock = net.createConnection(PIPE_PATH, () => {
      sock.write('stop');
      sock.end();
      resolve();
    });
    sock.on('error', reject);
  });

  console.log('Stop command sent, waiting for shutdown...');
  for (let i = 0; i < 90; i++) {
    if (!isRunning(pid)) break;
    await new Promise((r) => setTimeout(r, 1000));
  }
  console.log(isRunning(pid) ? 'Server did not stop in time.' : 'Server stopped.');
}

function cmdBuild() {
  console.log('Building plugin...');
  const result = spawnSync(join(PLUGIN_DIR, 'gradlew.bat'), ['build'], {
    cwd: PLUGIN_DIR,
    stdio: 'inherit',
    shell: true,
  });
  if (result.status !== 0) throw new Error('Build failed');
}

function cmdDeploy() {
  const libsDir = join(PLUGIN_DIR, 'build', 'libs');
  const jar = readdirSync(libsDir).find((f) => f.endsWith('.jar') && !f.endsWith('-sources.jar'));
  if (!jar) throw new Error('No built jar found — run `pnpm build` first.');

  const pluginsDir = join(SERVER_DIR, 'plugins');
  for (const f of readdirSync(pluginsDir)) {
    if (f.startsWith('weapons-plugin') && f.endsWith('.jar')) {
      unlinkSync(join(pluginsDir, f));
    }
  }
  copyFileSync(join(libsDir, jar), join(pluginsDir, jar));
  console.log(`Deployed ${jar} to server/plugins/`);
}

async function cmdConsole() {
  const line = process.argv.slice(3).join(' ').trim();
  if (!line) throw new Error('Usage: pnpm run console -- <server command>');

  const connected = await pingPipe();
  if (!connected) {
    console.log('Server is not running.');
    return;
  }

  await new Promise((resolve, reject) => {
    const sock = net.createConnection(PIPE_PATH, () => {
      sock.write(line);
      sock.end();
      resolve();
    });
    sock.on('error', reject);
  });
  console.log(`Sent: ${line}`);
}

async function cmdRestart() {
  await cmdStop();
  await cmdStart();
}

async function cmdDev() {
  cmdBuild();
  cmdDeploy();
  await cmdRestart();
}

const commands = {
  start: cmdStart,
  stop: cmdStop,
  restart: cmdRestart,
  build: cmdBuild,
  deploy: cmdDeploy,
  dev: cmdDev,
  console: cmdConsole,
  _supervise: () => cmdSupervise(process.argv[3]),
};

const command = process.argv[2];
if (!commands[command]) {
  console.error(`Unknown command: ${command}. Use one of: ${Object.keys(commands).filter((c) => !c.startsWith('_')).join(', ')}`);
  process.exit(1);
}
await commands[command]();
