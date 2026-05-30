import os
import subprocess
from pathlib import Path

from langchain_core.tools import tool

from config import PROJECT_ROOT, SKILL_MD_PATH, AGENTS_MD_PATH

ALLOWED_BASH_PREFIXES = (
    "./gradlew", "gradle", "git", "ls", "find", "pytest",
    "python", "python3", "cat", "head", "tail", "grep",
    "rg", "mkdir", "touch", "cp", "mv", "rm",
)


def _resolve_path(path: str) -> str:
    p = Path(path)
    if not p.is_absolute():
        p = Path(PROJECT_ROOT) / p
    return str(p.resolve())


def _is_path_safe(path: str) -> bool:
    try:
        resolved = Path(_resolve_path(path)).resolve()
        resolved.relative_to(PROJECT_ROOT)
        return True
    except ValueError:
        return False


@tool
def read_file(path: str) -> str:
    """Read the contents of a file. Path can be absolute or relative to project root."""
    resolved = _resolve_path(path)
    try:
        return Path(resolved).read_text(encoding="utf-8")
    except FileNotFoundError:
        return f"Error: File not found: {resolved}"
    except Exception as e:
        return f"Error reading file: {e}"


@tool
def write_file(path: str, content: str) -> str:
    """Create or overwrite a file with the given content. Path can be absolute or relative to project root."""
    resolved = _resolve_path(path)
    try:
        Path(resolved).parent.mkdir(parents=True, exist_ok=True)
        Path(resolved).write_text(content, encoding="utf-8")
        return f"File written successfully: {resolved}"
    except Exception as e:
        return f"Error writing file: {e}"


@tool
def edit_file(path: str, old_string: str, new_string: str) -> str:
    """
    Replace old_string with new_string in the file at path.
    Only replaces the first occurrence. Use with care.
    Path can be absolute or relative to project root.
    """
    resolved = _resolve_path(path)
    try:
        content = Path(resolved).read_text(encoding="utf-8")
    except FileNotFoundError:
        return f"Error: File not found: {resolved}"

    if old_string not in content:
        return f"Error: old_string not found in {resolved}"

    new_content = content.replace(old_string, new_string, 1)
    Path(resolved).write_text(new_content, encoding="utf-8")
    return f"Edit applied successfully to: {resolved}"


@tool
def list_directory(path: str = ".") -> str:
    """List contents of a directory. Path can be absolute or relative to project root."""
    resolved = _resolve_path(path)
    try:
        entries = []
        for entry in sorted(Path(resolved).iterdir()):
            suffix = "/" if entry.is_dir() else ""
            entries.append(f"  {entry.name}{suffix}")
        return f"Contents of {resolved}:\n" + "\n".join(entries) if entries else f"{resolved} is empty"
    except FileNotFoundError:
        return f"Error: Directory not found: {resolved}"
    except Exception as e:
        return f"Error listing directory: {e}"


@tool
def search_code(pattern: str) -> str:
    """
    Search for a pattern in the codebase using ripgrep.
    Searches relevant source files (Kotlin, Gradle, XML, Python).
    """
    import subprocess
    file_types = "*.kt,*.kts,*.xml,*.py,*.gradle"
    try:
        result = subprocess.run(
            ["rg", "--type-add", f"code:{file_types}", "-t", "code",
             "-n", "--no-heading", pattern, PROJECT_ROOT],
            capture_output=True, text=True, timeout=30
        )
        output = result.stdout.strip()
        return output if output else f"No matches found for pattern: {pattern}"
    except FileNotFoundError:
        return "Error: ripgrep (rg) not installed. Install it for code search."
    except subprocess.TimeoutExpired:
        return f"Search timed out for pattern: {pattern}"
    except Exception as e:
        return f"Error searching code: {e}"


@tool
def run_command(command: str) -> str:
    """
    Run a shell command and return its output.
    Allowed commands: ./gradlew, gradle, git, ls, find, mkdir, touch, cp, mv, rm, and python.
    Working directory is the project root.
    """
    cmd_parts = command.strip().split()
    if not cmd_parts:
        return "Error: empty command"

    allowed = False
    for prefix in ALLOWED_BASH_PREFIXES:
        if command.strip().startswith(prefix):
            allowed = True
            break

    if not allowed:
        return f"Error: command not allowed. Allowed prefixes: {ALLOWED_BASH_PREFIXES}"

    try:
        result = subprocess.run(
            command, shell=True, capture_output=True, text=True,
            timeout=120, cwd=PROJECT_ROOT
        )
        output = result.stdout.strip()
        if result.stderr.strip():
            output += "\n[stderr]\n" + result.stderr.strip()
        output += f"\n[exit code: {result.returncode}]"
        return output if output.strip() else f"Command completed with exit code {result.returncode}"
    except subprocess.TimeoutExpired:
        return "Error: command timed out (120s limit)"
    except Exception as e:
        return f"Error running command: {e}"


@tool
def read_project_guides() -> str:
    """Read the project's AGENTS.md and skill.md files for coding standards and project context."""
    result_parts = []
    for path, label in [(AGENTS_MD_PATH, "AGENTS.md"), (SKILL_MD_PATH, "skill.md")]:
        try:
            content = Path(path).read_text(encoding="utf-8")
            result_parts.append(f"=== {label} ===\n{content}")
        except FileNotFoundError:
            result_parts.append(f"=== {label} ===\n[File not found: {path}]")
    return "\n\n".join(result_parts)


CODING_TOOLS = [read_file, write_file, edit_file, list_directory, search_code, run_command, read_project_guides]
REVIEW_TOOLS = [read_file, search_code, run_command, read_project_guides]
SUMMARIZE_TOOLS = [run_command, read_file]
REFINE_TOOLS = [read_project_guides, read_file, list_directory]
