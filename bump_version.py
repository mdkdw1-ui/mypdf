#!/usr/bin/env python3
import re
from pathlib import Path

VERSION_FILE = Path("version.properties")
INCREMENT = 0.001

content = VERSION_FILE.read_text()
code = int(re.search(r"versionCode=(\d+)", content).group(1))
name = re.search(r"versionName=([\d.]+)", content).group(1)

new_code = code + 1
new_num = round(float(name) + INCREMENT, 3)
new_name = f"{new_num:.3f}"

VERSION_FILE.write_text(f"versionCode={new_code}\nversionName={new_name}\n")
print(f"✅ Bumped: {name} ({code}) -> {new_name} ({new_code})")
