#!/usr/bin/env python3
# UserPromptSubmit Hook — 사용자 질문을 .claude/prompt-logs/<오늘 날짜>.txt 에 덧붙여 기록한다.
import datetime
import json
import os
import sys

prompt = json.load(sys.stdin).get("prompt", "")
now = datetime.datetime.now()
log_dir = os.path.join(os.environ.get("CLAUDE_PROJECT_DIR", "."), ".claude", "prompt-logs")
os.makedirs(log_dir, exist_ok=True)
with open(os.path.join(log_dir, now.strftime("%Y-%m-%d") + ".txt"), "a", encoding="utf-8") as f:
    f.write(f"[{now:%H:%M:%S}] {prompt}\n\n")
