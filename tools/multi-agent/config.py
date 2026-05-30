import os
from pathlib import Path

from dotenv import load_dotenv

_project_root = Path(__file__).resolve().parent.parent.parent
_env_path = _project_root / ".env"

if _env_path.exists():
    load_dotenv(_env_path)
else:
    load_dotenv()

PROJECT_ROOT = str(_project_root)

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-v4-pro")
MAX_REVIEW_ITERATIONS = int(os.getenv("MAX_REVIEW_ITERATIONS", "3"))
MAX_TEMPERATURE = float(os.getenv("MAX_TEMPERATURE", "0.3"))

SKILL_MD_PATH = _project_root / "skill.md"
AGENTS_MD_PATH = _project_root / "AGENTS.md"

if not DEEPSEEK_API_KEY:
    raise RuntimeError(
        "DEEPSEEK_API_KEY not found in environment or .env file.\n"
        f"Create a .env file at {_project_root / '.env'} with:\n"
        "DEEPSEEK_API_KEY=your-api-key-here"
    )
