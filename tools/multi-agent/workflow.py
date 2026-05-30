import operator
import sys
import uuid
from typing import TypedDict, Annotated

from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver
from langgraph.types import interrupt
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage

from agents import (
    create_refiner_llm,
    create_generator_agent,
    create_reviewer_llm,
    create_summarizer_llm,
)
from prompts import (
    PROMPT_REFINER_SYSTEM,
    CODE_REVIEWER_SYSTEM,
    CHANGE_SUMMARIZER_SYSTEM,
)
from config import MAX_REVIEW_ITERATIONS, SKILL_MD_CONTENT, AGENTS_MD_CONTENT


class WorkflowState(TypedDict):
    messages: Annotated[list, operator.add]
    user_request: str
    refined_prompt: str
    needs_clarification: bool
    clarification_question: str
    user_clarification: str
    review_passed: bool
    review_feedback: str
    review_iteration: int
    changes_summary: str
    error: str


def _log(emoji: str, msg: str):
    print(f"  {emoji} {msg}", flush=True)


def _parse_clarification_output(text: str) -> tuple[bool, str, str]:
    needs = False
    question = ""
    refined = ""

    lines = text.strip().split("\n")
    for line in lines:
        if line.startswith("CLARIFICATION_NEEDED:") and "YES" in line.upper():
            needs = True
        elif line.startswith("QUESTION:"):
            question = line[len("QUESTION:"):].strip()
        elif line.startswith("REFINED_PROMPT:"):
            refined = line[len("REFINED_PROMPT:"):].strip()

    if needs and not question:
        question_marker = text.find("QUESTION:")
        if question_marker >= 0:
            question_end = text.find("\n", question_marker)
            question = text[question_marker + len("QUESTION:"):question_end].strip()

    if not refined and not needs:
        refined = text.strip()

    return needs, question, refined


def _parse_review_output(text: str) -> tuple[bool, str]:
    passed = False
    issues = ""

    for line in text.strip().split("\n"):
        if line.startswith("PASS:") and "YES" in line.upper():
            passed = True

    if not passed:
        issues_marker = text.find("ISSUES:")
        action_marker = text.find("ACTION_REQUIRED:")
        if issues_marker >= 0:
            if action_marker > issues_marker:
                issues = text[issues_marker:action_marker].strip()
            else:
                issues = text[issues_marker:].strip()
        else:
            issues = text.strip()

    return passed, issues


def _build_project_context() -> str:
    parts = []
    if AGENTS_MD_CONTENT:
        parts.append(f"=== AGENTS.md (Project Guide) ===\n{AGENTS_MD_CONTENT}")
    if SKILL_MD_CONTENT:
        parts.append(f"=== skill.md (Coding Standards) ===\n{SKILL_MD_CONTENT}")
    return "\n\n".join(parts) if parts else "Project guides not available."


def refine_prompt_node(state: WorkflowState) -> dict:
    _log("1", "Prompt Refiner — analyzing request...")
    llm = create_refiner_llm()

    clarification_context = ""
    if state.get("user_clarification"):
        clarification_context = (
            f"\n\nUser's answer to the clarification question: {state['user_clarification']}"
        )

    project_context = _build_project_context()

    context_prompt = f"""You have the full project context below. Use it to refine the user's request.

{project_context}

---
User's request: {state["user_request"]}{clarification_context}
---

Now refine this request into a clear, detailed, actionable coding task. Identify:
- Which files/packages/features are involved
- The exact behavior expected
- Technical constraints (Kotlin, Compose, Material 3, Room, etc.)
- Project conventions from AGENTS.md and skill.md that apply

If any detail is unclear, set CLARIFICATION_NEEDED: YES and ask ONE clear question.

Output format:
CLARIFICATION_NEEDED: YES/NO
QUESTION: <question if YES>
REFINED_PROMPT: <detailed prompt>"""

    response = llm.invoke([HumanMessage(content=context_prompt)])

    needs, question, refined = _parse_clarification_output(response.content)

    if state.get("user_clarification"):
        needs = False

    if not needs and not refined.strip():
        refined = (
            f"Implement the following in the LiftInsight Android project:\n\n"
            f"{state['user_request']}\n\n"
            f"Follow the coding standards in skill.md and project patterns in AGENTS.md. "
            f"Read existing code for conventions before making changes."
        )

    if needs:
        _log("?", f"Clarification needed: {question}")
    else:
        preview = refined[:120].replace("\n", " ")
        _log("1", f"Refined prompt: {preview}{'...' if len(refined) > 120 else ''}")

    return {
        "needs_clarification": needs,
        "clarification_question": question,
        "refined_prompt": refined,
        "messages": [response],
    }


def clarify_node(state: WorkflowState) -> dict:
    answer = interrupt(state["clarification_question"])
    return {"user_clarification": str(answer)}


def generate_code_node(state: WorkflowState) -> dict:
    _log("2", "Code Generator — implementing changes...")

    refined = state["refined_prompt"]
    if state.get("review_feedback"):
        _log("2", f"Fixing review issues (iteration {state.get('review_iteration', 0) + 1})...")
        refined = (
            f"ORIGINAL TASK:\n{state['refined_prompt']}\n\n"
            f"REVIEW FEEDBACK — YOU MUST FIX THESE ISSUES:\n{state['review_feedback']}\n\n"
            f"Read the relevant files, make the fixes, and verify compilation."
        )

    agent = create_generator_agent()
    agent_config = {"configurable": {"thread_id": f"gen-{uuid.uuid4().hex[:8]}"}}
    agent_input = {"messages": [HumanMessage(content=refined)]}

    try:
        result = agent.invoke(agent_input, agent_config)
    except Exception as e:
        _log("!", f"Code generator error: {e}")
        return {
            "messages": [AIMessage(content=f"Generation failed: {e}")],
            "error": str(e),
        }

    _log("2", "Code generation complete.")

    return {
        "messages": result.get("messages", []),
    }


def review_code_node(state: WorkflowState) -> dict:
    _log("3", "Code Reviewer — checking against standards...")
    llm = create_reviewer_llm()

    iteration = state.get("review_iteration", 0) + 1

    project_context = _build_project_context()

    prompt = f"""Review the code changes made in this session against the project standards below.

{project_context}

---
Review the changes (use the run_command tool to check git diff). Check for:
1. Style violations from skill.md (over-abstraction, naming, control flow, blank lines, etc.)
2. Project violations from AGENTS.md (Compose patterns, product focus, missing string resources, DB migrations)
3. Test coverage for non-UI code
4. Compilation errors
---

After your review, output in the exact format:
PASS: YES/NO
If NO:
ISSUES:
- <specific issue with file path and line reference>
ACTION_REQUIRED: <concrete fix instructions>"""

    response = llm.invoke([
        SystemMessage(content=CODE_REVIEWER_SYSTEM),
        HumanMessage(content=prompt)
    ])

    passed, issues = _parse_review_output(response.content)

    if iteration >= MAX_REVIEW_ITERATIONS:
        _log("3", f"Max review iterations ({MAX_REVIEW_ITERATIONS}) reached — accepting as-is.")
        passed = True
        issues = ""

    if passed:
        _log("3", "Review passed.")
    else:
        _log("3", f"Review found issues — sending back for fixes.")

    return {
        "review_passed": passed,
        "review_feedback": issues,
        "review_iteration": iteration,
        "messages": [response],
    }


def summarize_node(state: WorkflowState) -> dict:
    _log("4", "Change Summarizer — generating summary...")

    import subprocess
    from config import PROJECT_ROOT

    diff_output = ""
    try:
        result = subprocess.run(
            ["git", "-C", PROJECT_ROOT, "diff", "--stat"],
            capture_output=True, text=True
        )
        diff_output = result.stdout.strip()
    except Exception as e:
        diff_output = f"Unable to get git diff: {e}"

    if not diff_output:
        diff_output = "No changes detected in git diff."

    llm = create_summarizer_llm()

    prompt = f"""Here is the git diff summary for this session:

{diff_output}

Provide a clear summary of what changed, organized by file."""

    response = llm.invoke([
        SystemMessage(content=CHANGE_SUMMARIZER_SYSTEM),
        HumanMessage(content=prompt)
    ])

    return {
        "changes_summary": response.content,
        "messages": [response],
    }


def _route_after_refine(state: WorkflowState) -> str:
    if state.get("needs_clarification") and not state.get("user_clarification"):
        return "clarify"
    return "generate"


def _route_after_review(state: WorkflowState) -> str:
    if state.get("review_passed"):
        return "summarize"
    return "generate"


def create_workflow():
    builder = StateGraph(WorkflowState)

    builder.add_node("refine", refine_prompt_node)
    builder.add_node("clarify", clarify_node)
    builder.add_node("generate", generate_code_node)
    builder.add_node("review", review_code_node)
    builder.add_node("summarize", summarize_node)

    builder.set_entry_point("refine")

    builder.add_conditional_edges("refine", _route_after_refine, {
        "clarify": "clarify",
        "generate": "generate",
    })

    builder.add_edge("clarify", "refine")

    builder.add_edge("generate", "review")

    builder.add_conditional_edges("review", _route_after_review, {
        "summarize": "summarize",
        "generate": "generate",
    })

    builder.add_edge("summarize", END)

    return builder.compile(checkpointer=MemorySaver())
