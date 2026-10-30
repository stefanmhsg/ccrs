from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any, Mapping


EVENT_PREFIX = "[REACT-CCRS-EVENT]"


def log_ccrs_event(
    logger: logging.Logger,
    event: str,
    fields: Mapping[str, Any] | None = None,
    *,
    level: int = logging.INFO,
) -> None:
    """Emit stable key-value audit events for the React CCRS adapter."""

    if not event:
        return

    parts = [EVENT_PREFIX, f"event={_format_value(event)}"]
    timed_fields = {**(fields or {}), "timestamp_utc": datetime.now(timezone.utc).isoformat()}
    for key, value in timed_fields.items():
        if not key:
            continue
        parts.append(f"{key}={_format_value(value)}")
    logger.log(level, " ".join(parts))


def _format_value(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return str(value).lower()
    if isinstance(value, (int, float)):
        return str(value)

    text = str(value)
    if text and all(_is_bare_char(char) for char in text):
        return text
    return '"' + _escape(text) + '"'


def _is_bare_char(char: str) -> bool:
    return char.isalnum() or char in "_.:/#@+-"


def _escape(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )
