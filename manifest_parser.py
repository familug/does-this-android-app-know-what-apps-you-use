def _extract_package_name(line: str) -> str:
    """A: android:name(0x01010003)="com.okinc.okex.gp" (Raw: "com.okinc.okex.gp")"""
    return line.split('"')[1]


def query_packages(manifest: str) -> list[str]:
    pkgs = []
    for line in manifest.splitlines():
        if line.startswith("    A: android:name") and "." in line:
            pkgs.append(_extract_package_name(line))
    pkgs.sort()
    return pkgs


def remove_dup_sub_packages(pkgs: list[str]) -> list[str]:
    return sorted(
        [i for i in pkgs if not i.startswith(("android.", "androidx.", "com.android"))]
    )


def extract_query_section(manifest: str) -> str:
    lines = manifest.splitlines()
    start_idx = None
    end_idx = None
    start_end = []
    indents = 0
    for idx, line in enumerate(lines):
        if start_idx is None and ("E: queries") in line:
            start_idx = idx
            indents = line.count(" ", 0, line.index("E:"))
        elif start_idx and line.startswith(indents * " " + "E:"):
            end_idx = idx
            start_end.append((indents, start_idx, end_idx))
            start_idx = None
            end_idx = None

    if start_idx is not None and end_idx is None:
        end_idx = idx
        start_end.append((indents, start_idx, end_idx))

    if len(start_end) == 0:
        return ""

    res = ""
    for indents, start_idx, end_idx in start_end:
        # since E: queries may be indented more, use it as base to determine E: package > A:, not E: intent > E: action > A:
        res += "\n".join(line[indents:] for line in lines[start_idx:end_idx])
    return res


def check_leak_query_packages(manifest: str) -> bool:
    queries = extract_query_section(manifest)

    return "android.intent.action.MAIN" in queries
