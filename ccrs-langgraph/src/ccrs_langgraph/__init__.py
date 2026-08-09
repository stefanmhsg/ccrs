"""Public API for the CCRS LangGraph adapter."""

from ccrs_langgraph._version import (
    DEFAULT_CCRS_JAVA_GROUP,
    DEFAULT_CCRS_JAVA_VERSION,
    __version__,
)
from ccrs_langgraph.ccrs_node import ccrs_node, make_ccrs_node
from ccrs_langgraph.java_runtime import (
    CcrsJavaRuntime,
    CcrsJavaRuntimeError,
    get_default_java_runtime,
)
from ccrs_langgraph.opportunistic.opportunistic_result import (
    get_opportunistic_ccrs_for_latest_tool_calls,
)
from ccrs_langgraph.opportunistic.vocabulary_matcher import (
    VocabularyMatcher,
    evaluate_latest_tool_observation,
    get_default_vocabulary_matcher,
)
from ccrs_langgraph.prompt_context import build_ccrs_prompt_context
from ccrs_langgraph.state import CcrsAgentState
from ccrs_langgraph.contingency import (
    ContingencyCcrs,
    InMemoryCcrsContext,
    InMemoryCcrsTraceHistory,
    Situation,
    get_default_contingency_ccrs,
)


__all__ = [
    "DEFAULT_CCRS_JAVA_GROUP",
    "DEFAULT_CCRS_JAVA_VERSION",
    "CcrsAgentState",
    "CcrsJavaRuntime",
    "CcrsJavaRuntimeError",
    "ContingencyCcrs",
    "InMemoryCcrsContext",
    "InMemoryCcrsTraceHistory",
    "Situation",
    "VocabularyMatcher",
    "__version__",
    "build_ccrs_prompt_context",
    "ccrs_node",
    "evaluate_latest_tool_observation",
    "get_default_contingency_ccrs",
    "get_default_java_runtime",
    "get_default_vocabulary_matcher",
    "get_opportunistic_ccrs_for_latest_tool_calls",
    "make_ccrs_node",
]
