import operator
from typing import TypedDict, Annotated

from langgraph.graph import StateGraph, END
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
from config import MAX_REVIEW_ITERATIONS


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


def _parse_clarification_output(text: str) -> tuple[bool, str, str]:
    """Parse the refiner output for CLARIFICATION_NEEDED and QUESTION or REFINED_PROMPT."""
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
        refined_marker = text.find("REFINED_PROMPT:")
        question_marker = text.find("QUESTION:")
        if question_marker >= 0:
            question_end = text.find("\n", question_marker)
            question = text[question_marker + len("QUESTION:"):question_end].strip()

    if not refined and not needs:
        refined = text.strip()

    return needs, question, refined


def _parse_review_output(text: str) -> tuple[bool, str]:
    """Parse the reviewer output for PASS and ISSUES."""
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


def refine_prompt_node(state: WorkflowState) -> dict:
    llm = create_refiner_llm()

    clarification_context = ""
    if state.get("user_clarification"):
        clarification_context = (
            f"\n\nUser's answer to the clarification question: {state['user_clarification']}"
        )

    context_prompt = f"""{PROMPT_REFINER_SYSTEM}

User's request: {state["user_request"]}{clarification_context}

First, read the project guides (AGENTS.md and skill.md) using the read_project_guides tool to understand the project context. Then refine the prompt.

Remember: if any detail is unclear, set CLARIFICATION_NEEDED: YES and ask a clear question.
Otherwise, set CLARIFICATION_NEEDED: NO and provide the REFINED_PROMPT."""

    response = llm.invoke([SystemMessage(content=PROMPT_REFINER_SYSTEM), HumanMessage(content=context_prompt)])

    needs, question, refined = _parse_clarification_output(response.content)

    if state.get("user_clarification"):
        needs = False

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
    agent = create_generator_agent()

    refined = state["refined_prompt"]
    if state.get("review_feedback"):
        refined = (
            f"ORIGINAL TASK:\n{state['refined_prompt']}\n\n"
            f"REVIEW FEEDBACK - YOU MUST FIX THESE ISSUES:\n{state['review_feedback']}\n\n"
            f"Read the relevant files, make the fixes, and verify compilation."
        )

    agent_input = {"messages": [HumanMessage(content=refined)]}

    result = agent.invoke(agent_input)

    return {
        "messages": result.get("messages", []),
    }


def review_code_node(state: WorkflowState) -> dict:
    llm = create_reviewer_llm()

    iteration = state.get("review_iteration", 0) + 1

    prompt = f"""{CODE_REVIEWER_SYSTEM}

Review the code changes made in this session. Use the available tools to:
1. Read the files that were modified or created
2. Compare them against skill.md and AGENTS.md standards
3. Check for compilation errors and test failures

After your review, output in the exact format:
PASS: YES/NO
If NO:
ISSUES:
- <specific issues>
ACTION_REQUIRED: <fix instructions>"""

    response = llm.invoke([
        SystemMessage(content=CODE_REVIEWER_SYSTEM),
        HumanMessage(content=prompt)
    ])

    passed, issues = _parse_review_output(response.content)

    if iteration >= MAX_REVIEW_ITERATIONS:
        passed = True
        issues = ""

    return {
        "review_passed": passed,
        "review_feedback": issues,
        "review_iteration": iteration,
        "messages": [response],
    }


def summarize_node(state: WorkflowState) -> dict:
    import subprocess
    from config import PROJECT_ROOT

    try:
        result = subprocess.run(
            ["git", "diff", "--stat"],
            capture_output=True, text=True, cwd=PROJECT_ROOT
        )
        diff_output = result.stdout.strip()
        if not diff_output:
            diff_output = "No changes detected in git diff."
    except Exception as e:
        diff_output = f"Unable to get git diff: {e}"

    llm = create_summarizer_llm()

    prompt = f"""{CHANGE_SUMMARIZER_SYSTEM}

Here is the git diff summary for this session:

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

    return builder.compile()
