#!/usr/bin/env python3
"""Record the README demo GIF from a *real* `learn` run.

VHS (docs/demo.tape) is the nicer recipe, but it needs a working ttyd + headless
chromium, which isn't available everywhere. This script is the portable fallback:
it runs the real CLI over examples/demo-corpus.txt, captures stdout with real
timestamps into an asciicast v2 file, and renders it to docs/demo.gif with `agg`
(https://github.com/asciinema/agg). Idle time (the local-LLM synthesis wait) is
compressed by agg's --idle-time-limit, so the displayed content is 100% real.

Prereqs: a built install dir (`./gradlew installDist`), a JDK 25 in $JAVA_HOME,
`agg` on PATH, and a responsive local LLM (set --llm-model below to one you have).

Usage:  python docs/record-demo.py
"""
import subprocess, time, json, os, sys, codecs, shutil

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAST = os.path.join(REPO, "build", "demo.cast")
GIF = os.path.join(REPO, "docs", "demo.gif")

java_home = os.environ.get("JAVA_HOME")
if not java_home:
    sys.exit("Set JAVA_HOME to a JDK 25 (the toolchain Gradle builds with).")
java = os.path.join(java_home, "bin", "java.exe" if os.name == "nt" else "java")
classpath = os.path.join(REPO, "build", "install", "skill3", "lib", "*")

PRETTY = ("skill3 learn nimbus-cli --llm-model qwen2.5:0.5b "
          "--input-file ./examples/demo-corpus.txt --output-dir ./build/demo --max-tokens 320")
cmd = [java, "-cp", classpath, "se.deversity.skill3.Skill3App"] + PRETTY.split()[1:]

env = dict(os.environ)
env.pop("JAVA_TOOL_OPTIONS", None)

events = [[0.0, "o", "\x1b[1;32m➜\x1b[0m "]]
t = 0.4
for ch in PRETTY:
    events.append([round(t, 3), "o", ch]); t += 0.022
events.append([round(t + 0.25, 3), "o", "\r\n"])
base = t + 0.45

dec = codecs.getincrementaldecoder("utf-8")(errors="replace")
p = subprocess.Popen(cmd, cwd=REPO, env=env, bufsize=0,
                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
start = time.time()
while True:
    b = p.stdout.read(8)
    if not b:
        break
    text = dec.decode(b)
    if not text:
        continue
    # \n -> \r\n for terminal rendering; fold any non-ASCII (the JVM emits the
    # em-dash as a lone cp1252 byte on Windows) to ASCII so agg renders cleanly.
    text = text.replace("\r\n", "\n").replace("\n", "\r\n")
    text = "".join(c if ord(c) < 127 else "-" for c in text)
    events.append([round(base + (time.time() - start), 3), "o", text])
p.wait()

header = {"version": 2, "width": 150, "height": 19,
          "env": {"SHELL": "bash", "TERM": "xterm-256color"}}
with open(CAST, "w", encoding="utf-8", newline="\n") as f:
    f.write(json.dumps(header) + "\n")
    for e in events:
        f.write(json.dumps(e, ensure_ascii=False) + "\n")
print(f"cast: {CAST} ({len(events)} events)")

if shutil.which("agg"):
    subprocess.run(["agg", "--theme", "dracula", "--font-size", "16",
                    "--cols", "150", "--rows", "19", "--idle-time-limit", "1.2",
                    "--last-frame-duration", "4", CAST, GIF], check=True)
    print(f"gif:  {GIF}")
else:
    print("agg not found; render with:  agg --theme dracula", CAST, GIF)
