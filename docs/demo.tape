# VHS script for the README hero GIF — a real, fully-offline `learn` run over a
# made-up corpus (examples/demo-corpus.txt). Record with:  vhs docs/demo.tape
# Requires: vhs, ttyd, ffmpeg, a responsive local Ollama with qwen2.5:0.5b, and
# JAVA_HOME exported to a JDK 25 before running (the toolchain the app builds with).
# Where VHS can't run (no ttyd/headless chromium), use docs/record-demo.py instead.

Output docs/demo.gif

Set Shell "bash"
Set FontSize 18
Set Width 1200
Set Height 720
Set Padding 20
Set Framerate 12
Set Theme "Dracula"

# Hidden setup: put the installed launcher on PATH, quietly. JAVA_HOME must
# already point at a JDK 25 (see the header note above).
Hide
Type 'unset JAVA_TOOL_OPTIONS; export PATH="$PWD/build/install/skill3/bin:$PATH"; clear'
Enter
Sleep 1s
Show

# The real run.
Type@55ms "skill3 learn nimbus-cli --llm-model qwen2.5:0.5b --input-file ./examples/demo-corpus.txt --output-dir ./build/demo --max-tokens 320"
Sleep 800ms
Enter

# Wait for synthesis to finish (LLM time varies), then hold the final frame.
Wait+Screen@5m /Done\. Wrote/
Sleep 3s
