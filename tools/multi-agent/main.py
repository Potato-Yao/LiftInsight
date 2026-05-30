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

from config import PROJECT_ROOT
from workflow import create_workflow, WorkflowState


def print_banner():
    print()
    print("  LiftInsight Multi-Agent Workflow")
    print("  " + "-" * 33)
    print()


def print_phase(label: str):
    print(f"\n  [{label}]")


def main():
    print_banner()

    if len(sys.argv) > 1:
        user_request = " ".join(sys.argv[1:])
    else:
        user_request = input("  What should I build or change?\n  > ").strip()
        if not user_request:
            print("  No request provided. Exiting.")
            return

    print_phase("1 - Prompt Refiner")
    print(f"  Analyzing: {user_request[:80]}{'...' if len(user_request) > 80 else ''}")

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
                print_phase("? - Clarification Needed")
                print(f"  {question}")

            answer = input("\n  Your answer: ").strip()
            if not answer:
                answer = "no additional details"

            input_data = Command(resume=answer)

    if isinstance(result, dict) and result.get("changes_summary"):
        print_phase("4 - Change Summary")
        print()
        print(result["changes_summary"])
        print()

    print("  Done.")


if __name__ == "__main__":
    main()
