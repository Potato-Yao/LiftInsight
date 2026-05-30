from langchain_deepseek import ChatDeepSeek
from langgraph.prebuilt import create_react_agent

from config import DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL, DEEPSEEK_MODEL, MAX_TEMPERATURE
from tools import CODING_TOOLS, REVIEW_TOOLS
from prompts import (
    PROMPT_REFINER_SYSTEM,
    CODE_GENERATOR_SYSTEM,
    CODE_REVIEWER_SYSTEM,
    CHANGE_SUMMARIZER_SYSTEM,
)


def _build_llm(temperature: float = MAX_TEMPERATURE) -> ChatDeepSeek:
    return ChatDeepSeek(
        api_key=DEEPSEEK_API_KEY,
        api_base=DEEPSEEK_BASE_URL,
        model=DEEPSEEK_MODEL,
        temperature=temperature,
    )


def create_refiner_llm() -> ChatDeepSeek:
    return _build_llm(temperature=0.2)


def create_generator_agent():
    llm = _build_llm(temperature=0.1)
    return create_react_agent(
        model=llm,
        tools=CODING_TOOLS,
        state_modifier=CODE_GENERATOR_SYSTEM,
    )


def create_reviewer_llm() -> ChatDeepSeek:
    return _build_llm(temperature=0.0)


def create_summarizer_llm() -> ChatDeepSeek:
    return _build_llm(temperature=0.1)
