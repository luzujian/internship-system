# -*- coding: utf-8 -*-
"""
DeepIntern 实习系统 · 端到端业务测试公共库

职责：
1. 提供真实 MySQL 查询能力（业务库只读校验用；写入仅限测试脚本显式调用）
2. 提供 Playwright 浏览器驱动（真实 Chrome，headless）
3. 提供四端登录、导航、UI 断言、数据库断言、截图与报告能力

约定：
- 测试账号与数据由调用方传入，本库不硬编码业务账号
- 所有"UI 显示 vs 数据库真值"的对比都必须调用 verify()，不允许只断言页面文字
"""

from __future__ import annotations

import json
import os
import sys
import time
import traceback
from dataclasses import dataclass, field
from datetime import date, datetime, timezone, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any, Callable, Iterable, Optional
from urllib.parse import urlparse

try:  # Windows 控制台默认 GBK，强制 UTF-8 输出避免中文乱码
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

import pymysql
from pymysql.cursors import DictCursor

PROJECT_ROOT = Path(__file__).resolve().parents[3]
EVIDENCE_DIR = PROJECT_ROOT / "frontend" / "output" / "e2e-evidence"
REPORT_PATH = PROJECT_ROOT / "frontend" / "output" / "e2e-report.json"

DEFAULT_FRONTEND_URL = os.environ.get("E2E_FRONTEND_URL", "http://localhost:5173")
DEFAULT_BACKEND_URL = os.environ.get("E2E_BACKEND_URL", "http://localhost:8080")


def require_local_target() -> None:
    """Legacy write-capable E2E scripts may run only against isolated local services."""
    loopback = {"localhost", "127.0.0.1", "::1"}
    for name, target in (("E2E_FRONTEND_URL", DEFAULT_FRONTEND_URL),
                         ("E2E_BACKEND_URL", DEFAULT_BACKEND_URL)):
        parsed = urlparse(target)
        if parsed.scheme not in {"http", "https"} or parsed.hostname not in loopback:
            raise RuntimeError(f"{name} must point to a local test service")
    if os.environ.get("E2E_DB_HOST", "127.0.0.1") not in loopback:
        raise RuntimeError("E2E_DB_HOST must point to a local test database")


require_local_target()

DB_CONFIG = {
    "host": os.environ.get("E2E_DB_HOST", "127.0.0.1"),
    "port": int(os.environ.get("E2E_DB_PORT", "3306")),
    "user": os.environ.get("E2E_DB_USER", "user"),
    "password": os.environ.get("E2E_DB_PASSWORD", ""),
    "database": os.environ.get("E2E_DB_NAME", "internship"),
    "charset": "utf8mb4",
    "cursorclass": DictCursor,
    "autocommit": True,
    "connect_timeout": 10,
}

CST = timezone(timedelta(hours=8))


def now_cst() -> str:
    return datetime.now(CST).strftime("%Y-%m-%d %H:%M:%S")


def _jsonable(value: Any) -> Any:
    if isinstance(value, (datetime, date)):
        return value.strftime("%Y-%m-%d %H:%M:%S") if isinstance(value, datetime) else value.strftime("%Y-%m-%d")
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace")
    return value


def jsonable_row(row: dict) -> dict:
    return {k: _jsonable(v) for k, v in row.items()}


class Db:
    """真实业务库访问封装。默认只提供读方法；写方法用于测试数据准备与清理。"""

    def __init__(self, **overrides):
        cfg = dict(DB_CONFIG)
        cfg.update(overrides)
        if cfg["host"] not in {"localhost", "127.0.0.1", "::1"}:
            raise RuntimeError("E2E database overrides must remain on loopback")
        if not cfg["password"]:
            raise RuntimeError("E2E_DB_PASSWORD is required for local tests")
        self._conn = pymysql.connect(**cfg)

    def close(self) -> None:
        try:
            self._conn.close()
        except Exception:
            pass

    def __enter__(self) -> "Db":
        return self

    def __exit__(self, *exc) -> None:
        self.close()

    def one(self, sql: str, params: Iterable[Any] = ()) -> Optional[dict]:
        with self._conn.cursor() as cur:
            cur.execute(sql, tuple(params))
            row = cur.fetchone()
        return jsonable_row(row) if row else None

    def all(self, sql: str, params: Iterable[Any] = ()) -> list[dict]:
        with self._conn.cursor() as cur:
            cur.execute(sql, tuple(params))
            rows = cur.fetchall()
        return [jsonable_row(r) for r in rows]

    def scalar(self, sql: str, params: Iterable[Any] = ()) -> Any:
        row = self.one(sql, params)
        if not row:
            return None
        return next(iter(row.values()), None)

    def execute(self, sql: str, params: Iterable[Any] = ()) -> int:
        with self._conn.cursor() as cur:
            affected = cur.execute(sql, tuple(params))
        return affected

    def ping(self) -> bool:
        try:
            return self.scalar("SELECT 1") == 1
        except Exception:
            return False


@dataclass
class Step:
    phase: str
    name: str
    status: str  # PASS / FAIL / WARN / SKIP
    detail: str = ""
    evidence: list[str] = field(default_factory=list)
    db: dict = field(default_factory=dict)
    ui: dict = field(default_factory=dict)
    severity: str = "major"  # critical / major / minor
    duration_ms: int = 0
    error: str = ""

    def to_dict(self) -> dict:
        return {
            "phase": self.phase,
            "name": self.name,
            "status": self.status,
            "severity": self.severity,
            "detail": self.detail,
            "evidence": self.evidence,
            "db": self.db,
            "ui": self.ui,
            "duration_ms": self.duration_ms,
            "error": self.error,
        }


class Reporter:
    def __init__(self, run_name: str):
        self.run_name = run_name
        self.started_at = now_cst()
        self.steps: list[Step] = []
        self.notes: list[str] = []
        EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)

    def add(self, step: Step) -> Step:
        self.steps.append(step)
        icon = {"PASS": "[PASS]", "FAIL": "[FAIL]", "WARN": "[WARN]", "SKIP": "[SKIP]"}.get(step.status, "[????]")
        line = f"{icon} {step.phase} / {step.name} :: {step.detail}"
        print(line, flush=True)
        return step

    def ok(self, phase: str, name: str, detail: str = "", **kw) -> Step:
        return self.add(Step(phase, name, "PASS", detail, **kw))

    def fail(self, phase: str, name: str, detail: str = "", **kw) -> Step:
        return self.add(Step(phase, name, "FAIL", detail, **kw))

    def warn(self, phase: str, name: str, detail: str = "", **kw) -> Step:
        return self.add(Step(phase, name, "WARN", detail, **kw))

    def skip(self, phase: str, name: str, detail: str = "", **kw) -> Step:
        return self.add(Step(phase, name, "SKIP", detail, **kw))

    def note(self, text: str) -> None:
        self.notes.append(f"{now_cst()} {text}")
        print(f"[NOTE] {text}", flush=True)

    def summary(self) -> dict:
        total = len(self.steps)
        by_status: dict[str, int] = {}
        for s in self.steps:
            by_status[s.status] = by_status.get(s.status, 0) + 1
        failed = [s for s in self.steps if s.status == "FAIL"]
        return {
            "run": self.run_name,
            "startedAt": self.started_at,
            "finishedAt": now_cst(),
            "total": total,
            "byStatus": by_status,
            "failed": len(failed),
            "criticalFailed": len([s for s in failed if s.severity == "critical"]),
            "result": "PASS" if not failed else "FAIL",
        }

    def write(self, path: Path = REPORT_PATH) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "summary": self.summary(),
            "notes": self.notes,
            "steps": [s.to_dict() for s in self.steps],
        }
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        return path

    def print_summary(self) -> dict:
        s = self.summary()
        print("\n" + "=" * 72, flush=True)
        print(f"结果: {s['result']}   步骤: {s['total']}   失败: {s['failed']}   严重失败: {s['criticalFailed']}", flush=True)
        print("明细: " + json.dumps(s["byStatus"], ensure_ascii=False), flush=True)
        print("=" * 72, flush=True)
        return s


class UiError(AssertionError):
    pass


class E2E:
    """Playwright 浏览器会话封装，内含常用断言。"""

    def __init__(self, reporter: Reporter, frontend_url: str = DEFAULT_FRONTEND_URL):
        self.reporter = reporter
        self.frontend_url = frontend_url.rstrip("/")
        self.shot_index = 0
        self.console_errors: list[str] = []
        self._pw = None
        self.browser = None
        self.context = None
        self.page = None

    # ---------- lifecycle ----------
    def start(self, headless: bool = True) -> "E2E":
        from playwright.sync_api import sync_playwright

        self._pw = sync_playwright().start()
        self.browser = self._pw.chromium.launch(channel="chrome", headless=headless, args=["--disable-dev-shm-usage"])
        self.context = self.browser.new_context(
            viewport={"width": 1440, "height": 900},
            locale="zh-CN",
            timezone_id="Asia/Shanghai",
        )
        self.context.set_default_timeout(20000)
        self.context.set_default_navigation_timeout(30000)
        self.page = self.context.new_page()
        self.page.on("console", self._on_console)
        self.page.on("pageerror", lambda e: self.console_errors.append(f"pageerror: {e}"))
        return self

    def stop(self) -> None:
        for closer in (self.context, self.browser):
            try:
                if closer:
                    closer.close()
            except Exception:
                pass
        try:
            if self._pw:
                self._pw.stop()
        except Exception:
            pass

    def __enter__(self) -> "E2E":
        return self.start()

    def __exit__(self, *exc) -> None:
        self.stop()

    def _on_console(self, msg) -> None:
        try:
            if msg.type == "error":
                text = msg.text or ""
                if "favicon" in text.lower():
                    return
                self.console_errors.append(text[:400])
        except Exception:
            pass

    # ---------- evidence ----------
    def shot(self, label: str) -> str:
        self.shot_index += 1
        safe = "".join(ch if ch.isalnum() or ch in "-_" else "_" for ch in label)[:60]
        path = EVIDENCE_DIR / f"{self.shot_index:03d}-{safe}.png"
        try:
            self.page.screenshot(path=str(path), full_page=False)
        except Exception as exc:
            return f"screenshot-failed: {exc}"
        return str(path.relative_to(PROJECT_ROOT))

    # ---------- navigation ----------
    def goto_login(self) -> None:
        self.page.goto(f"{self.frontend_url}/login", wait_until="domcontentloaded")
        self.page.wait_for_selector("input[placeholder='请输入账号']", timeout=20000)

    def clear_session(self) -> None:
        try:
            self.context.clear_cookies()
        except Exception:
            pass
        try:
            self.page.goto(f"{self.frontend_url}/login", wait_until="domcontentloaded")
            self.page.evaluate("() => { try { localStorage.clear(); sessionStorage.clear(); } catch (e) {} }")
        except Exception:
            pass

    def login(self, username: str, password: str, expected_prefix: str, timeout: int = 30000) -> str:
        """通过真实登录页登录，返回最终落地 URL。"""
        self.goto_login()
        self.page.fill("input[placeholder='请输入账号']", username)
        self.page.fill("input[placeholder='请输入密码']", password)
        with self.page.expect_navigation(wait_until="domcontentloaded", timeout=timeout):
            self.page.click("button:has-text('登 录')")
        try:
            self.page.wait_for_url(lambda url: expected_prefix in url, timeout=timeout)
        except Exception:
            pass
        self.page.wait_for_load_state("networkidle", timeout=20000)
        url = self.page.url
        if expected_prefix not in url:
            raise UiError(f"登录后未进入 {expected_prefix}，当前 URL = {url}")
        return url

    def open(self, path: str, expect_text: Optional[str] = None, timeout: int = 20000) -> None:
        """打开路由并等待页面主体渲染。

        expect_text 只做可见性提示，不强制要求（菜单/抽屉里常存在同名隐藏文本）。
        """
        self.page.goto(f"{self.frontend_url}{path}", wait_until="domcontentloaded")
        try:
            self.page.wait_for_load_state("networkidle", timeout=timeout)
        except Exception:
            pass
        if expect_text:
            try:
                self.page.get_by_text(expect_text, exact=False).first.wait_for(state="visible", timeout=timeout)
            except Exception:
                # 页面可能用其它措辞渲染，交由后续具体断言负责
                pass

    def wait_content(self, timeout: int = 20000) -> None:
        """等待页面出现业务内容容器（表格 / 卡片 / 空状态 / 头部）。"""
        self.page.wait_for_selector(
            ".el-table__row, .el-table__empty-text, .el-empty, .el-card, .el-collapse-item,"
            " .job-card, .position-card, .application-card, .internship-card, .stat-card,"
            " .home-container, .welcome-section, .page-header, .header-content, .chart-container",
            timeout=timeout,
        )

    def wait_table(self, timeout: int = 20000) -> None:
        self.page.wait_for_selector(".el-table__row, .el-table__empty-text, .el-empty", timeout=timeout)

    def api_calls(self, fragment: str) -> list:
        return [c for c in getattr(self, "api_log", []) if fragment in c.get("url", "")]

    def last_api(self, fragment: str, method: str = "GET"):
        for item in reversed(getattr(self, "api_log", [])):
            if item.get("method") == method and fragment in item.get("url", ""):
                return item
        return None

    # ---------- assertions ----------
    def expect_visible(self, selector: str, message: str, timeout: int = 15000) -> None:
        try:
            self.page.wait_for_selector(selector, state="visible", timeout=timeout)
        except Exception as exc:
            raise UiError(f"{message}；选择器未出现: {selector} ({exc.__class__.__name__})") from exc

    def expect_true(self, condition: bool, message: str) -> None:
        if not condition:
            raise UiError(message)

    def expect_eq(self, actual: Any, expected: Any, message: str) -> None:
        if str(actual).strip() != str(expected).strip():
            raise UiError(f"{message}；期望={expected!r} 实际={actual!r}")

    def text_present(self, text: str, timeout: int = 12000) -> bool:
        try:
            self.page.wait_for_selector(f"text={text}", timeout=timeout)
            return True
        except Exception:
            return False

    def body_text(self) -> str:
        try:
            return self.page.inner_text("body")
        except Exception:
            return ""


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def assert_equal(actual: Any, expected: Any, message: str) -> None:
    if str(actual).strip() != str(expected).strip():
        raise AssertionError(f"{message}；期望={expected!r} 实际={actual!r}")


class StepRunner:
    """把一个测试步骤的计时、异常捕获、报告写入统一起来。"""

    def __init__(self, reporter: Reporter, ui: Optional[E2E] = None):
        self.reporter = reporter
        self.ui = ui

    def run(
        self,
        phase: str,
        name: str,
        func: Callable[[], "StepResult"],
        severity: str = "major",
        screenshot: Optional[str] = None,
    ) -> Step:
        started = time.time()
        try:
            result = func()
            elapsed = int((time.time() - started) * 1000)
            evidence = list(result.evidence)
            if self.ui and screenshot:
                evidence.append(self.ui.shot(screenshot))
            return self.reporter.ok(
                phase, name, result.detail, evidence=evidence, db=result.db, ui=result.ui,
                severity=severity, duration_ms=elapsed,
            )
        except Exception as exc:
            elapsed = int((time.time() - started) * 1000)
            evidence: list[str] = []
            if self.ui:
                try:
                    evidence.append(self.ui.shot(f"FAIL-{name}"))
                except Exception:
                    pass
            return self.reporter.fail(
                phase, name, f"{exc.__class__.__name__}: {exc}", evidence=evidence,
                severity=severity, duration_ms=elapsed, error=traceback.format_exc(limit=6),
            )


@dataclass
class StepResult:
    detail: str = ""
    evidence: list[str] = field(default_factory=list)
    db: dict = field(default_factory=dict)
    ui: dict = field(default_factory=dict)


def ensure_dirs() -> None:
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
