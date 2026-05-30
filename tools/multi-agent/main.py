#!/usr/bin/env python3
"""LiftInsight Multi-Agent Coding Workflow.

A 4-agent pipeline that refines prompts, generates code, reviews
against project standards, and summarizes changes.

Usage:
    python main.py "Add a dark mode toggle to settings"
    python main.py                     (interactive mode)
"""

import sys
import uuid
from pathlib import Path

_sys_path = str(Path(__file__).resolve().parent)
if _sys_path not in sys.path:
    sys.path.insert(0, _sys_path)

from langgraph.errors import GraphInterrupt
from langgraph.types import Command

from config import PROJECT_ROOT, DEEPSEEK_API_KEY
from workflow import create_workflow, WorkflowState


def print_banner():
    print()
    print("  LiftInsight Multi-Agent Workflow")
    print("  " + "-" * 33)
    print()


def main():
    print_banner()

    if len(sys.argv) > 1:
        user_request = " ".join(sys.argv[1:])
    else:
        user_request = input("  What should I build or change?\n  > ").strip()
        if not user_request:
            print("  No request provided. Exiting.")
            return

    print(f"  Request: {user_request[:100]}{'...' if len(user_request) > 100 else ''}")

    workflow = create_workflow()
    config = {"configurable": {"thread_id": f"session-{uuid.uuid4().hex[:8]}"}}

    initial_state: WorkflowState = {
        "messages": [],
        "user_request": user_request,
        "refined_prompt": "",
        "needs_clarification": False,
        "clarification_question": "",
        "user_clarification": "",
        "review_passed": False,
        "review_feedback": "",
        "review_iteration": 0,
        "changes_summary": "",
        "error": "",
    }

    input_data = initial_state

    while True:
        try:
            result = workflow.invoke(input_data, config)
            break
        except GraphInterrupt as e:
            question = str(e)
            if question and question != "None":
                print(f"\n  [?] {question}")

            answer = input("\n  Your answer: ").strip()
            if not answer:
                answer = "no additional details"

            input_data = Command(resume=answer)
        except Exception as e:
            print(f"\n  [!] Workflow error: {e}")
            print(f"  Check that your DEEPSEEK_API_KEY in .env is valid.")
            return

    if isinstance(result, dict):
        if result.get("error"):
            print(f"\n  [!] Error: {result['error']}")
        if result.get("changes_summary"):
            print()
            print(result["changes_summary"])
            print()

    print("  Done.")


if __name__ == "__main__":
    main()
