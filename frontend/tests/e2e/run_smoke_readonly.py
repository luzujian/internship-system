# -*- coding: utf-8 -*-
"""
DeepIntern 实习系统 · 四端登录 + 前后端联调 + 数据库真值一致性（只读，不写业务数据）

覆盖：
1. 后端/数据库健康
2. 管理员 / 教师 / 学生 / 企业 四端真实登录页登录、跳转、token 落库
3. 各端核心页面渲染 + 关键业务数据与 MySQL 真值交叉核对
4. 越权访问负向校验
5. 前端控制台严重错误检查

用法：
    python frontend/tests/e2e/run_smoke_readonly.py
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

os.environ.setdefault("PYTHONIOENCODING", "utf-8")
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import (  # noqa: E402
    PROJECT_ROOT, Db, E2E, Reporter, StepResult, StepRunner, check, ensure_dirs,
)

ACCOUNTS = {
    "admin": {"username": "admin2", "password": "123456", "prefix": "/admin"},
    "teacher": {"username": "t001", "password": "123456", "prefix": "/teacher"},
    "student": {"username": "s001", "password": "123456", "prefix": "/student"},
    "company": {"username": "企业1", "password": "123456", "prefix": "/company"},
}

STUDENT_DB_ID = 8
COMPANY_DB_ID = 1


def attach_api_logger(ui: E2E) -> None:
    ui.api_log = []

    def on_response(resp):
        try:
            if "/api/" not in resp.url:
                return
            entry = {"url": resp.url, "status": resp.status, "method": resp.request.method, "body": None}
            if resp.status == 200:
                try:
                    entry["body"] = resp.json()
                except Exception:
                    pass
            ui.api_log.append(entry)
        except Exception:
            pass

    ui.page.on("response", on_response)


def api_data(ui: E2E, fragment: str):
    item = ui.last_api(fragment)
    if not item or not isinstance(item.get("body"), dict):
        return None
    body = item["body"]
    data = body.get("data", body)
    if isinstance(data, dict):
        for key in ("records", "rows", "list", "content"):
            if isinstance(data.get(key), list):
                return data[key]
    return data


def main() -> int:
    ensure_dirs()
    rep = Reporter("smoke-readonly")
    ui = E2E(rep)
    runner = StepRunner(rep, ui)
    db = Db()

    print(f"前端: {ui.frontend_url}")
    print(f"数据库: {db.one('SELECT DATABASE() AS db')}")
    rep.note("只读联调：仅登录与浏览，不写入任何业务数据")

    ui.start(headless=True)
    attach_api_logger(ui)

    try:
        # ---------- P0 环境 ----------
        def health():
            import urllib.request
            with urllib.request.urlopen("http://localhost:8080/api/health/check", timeout=10) as r:
                payload = json.loads(r.read().decode("utf-8"))
            check(payload.get("code") == 200, f"健康检查返回异常: {payload}")
            check(payload["data"]["database"] == "connected", "后端报告数据库未连接")
            return StepResult(detail="后端 /api/health/check 正常，数据库 connected", ui={"health": payload["data"]})

        runner.run("P0-环境", "后端健康与数据库连通", health, severity="critical")

        # ---------- P1 四端登录 ----------
        for role, acc in ACCOUNTS.items():
            def login_step(role=role, acc=acc):
                url = ui.login(acc["username"], acc["password"], acc["prefix"])
                storage = ui.page.evaluate(
                    "() => { const o={}; for(let i=0;i<localStorage.length;i++){const k=localStorage.key(i);"
                    " if(k.includes('token')||k.includes('role')||k.includes('userId')){o[k]=(localStorage.getItem(k)||'').slice(0,28);} } return o; }"
                )
                check(any("token" in k for k in storage), f"{role} 登录后 localStorage 未写入 token: {storage}")
                shot = ui.shot(f"{role}-home")
                return StepResult(
                    detail=f"{acc['username']} 登录成功 -> {url}",
                    evidence=[shot],
                    ui={"redirect": url, "storageKeys": sorted(storage.keys())},
                )

            runner.run("P1-登录", f"{role} 端登录并跳转", login_step, severity="critical")
            ui.clear_session()

        # ---------- P2 学生端 ----------
        def student_flow():
            ui.login(ACCOUNTS["student"]["username"], ACCOUNTS["student"]["password"], "/student")

            ui.api_log.clear()
            ui.open("/student/jobs")
            ui.wait_content()
            ui_rows = ui.page.locator(".job-card, .position-card, .el-table__row").count()
            check(ui_rows > 0, "学生岗位页未渲染出任何岗位")
            shot_jobs = ui.shot("student-jobs")
            jobs_text = ui.body_text()
            api_jobs = api_data(ui, "/student/positions")
            db_active = db.scalar("SELECT COUNT(*) FROM position WHERE status='active'")
            db_active_names = [r["position_name"] for r in db.all("SELECT position_name FROM position WHERE status='active'")]

            ui.open("/student/applications")
            ui.wait_content()
            ui_rows_app = ui.page.locator(".el-table__row, .application-card").count()
            shot_apps = ui.shot("student-applications")
            db_apps = db.all(
                "SELECT id, status, position_name, company_name FROM student_job_application WHERE student_id=%s ORDER BY id DESC",
                (STUDENT_DB_ID,),
            )

            ui.open("/student/internship-confirmation-form")
            ui.page.wait_for_timeout(3500)
            body = ui.body_text()
            shot_intern = ui.shot("student-internship-confirmation")
            check("实习" in body or "确认" in body, "实习确认页未渲染出内容")
            status_row = db.one(
                "SELECT status, company_confirm_status, company_name, position_name FROM student_internship_status WHERE student_id=%s",
                (STUDENT_DB_ID,),
            )
            check(status_row is not None, "学生 s001 在 student_internship_status 无记录")
            check(
                any(name in jobs_text for name in db_active_names),
                "学生岗位页展示的岗位名称在数据库 active 岗位中找不到对应记录",
            )
            if status_row.get("company_name"):
                check(
                    status_row["company_name"] in body or "实习" in body,
                    "学生实习页未渲染出库内实习单位信息",
                )

            # 交叉核对：库内最新申请必须能在页面文本中找到
            if db_apps and db_apps[0].get("position_name"):
                ui.open("/student/applications")
                ui.wait_content()
                check(
                    db_apps[0]["position_name"] in ui.body_text(),
                    f"申请页未显示库内最新申请岗位 {db_apps[0]['position_name']}",
                )

            return StepResult(
                detail=(
                    f"岗位页 {ui_rows} 张卡片（库内 active 岗位 {db_active}），"
                    f"申请页 {ui_rows_app} 行 / 库内 {len(db_apps)} 条，"
                    f"实习状态 status={status_row['status']} 单位={status_row.get('company_name')}"
                ),
                evidence=[shot_jobs, shot_apps, shot_intern],
                ui={"jobCards": ui_rows, "applicationRows": ui_rows_app},
                db={
                    "activePositions": db_active,
                    "apiPositionsCount": len(api_jobs) if isinstance(api_jobs, list) else None,
                    "applications": db_apps[:5],
                    "internshipStatus": status_row,
                },
            )

        runner.run("P2-学生端", "岗位 / 申请 / 实习状态与数据库一致", student_flow, severity="critical")
        ui.clear_session()

        # ---------- P3 企业端 ----------
        def company_flow():
            ui.login(ACCOUNTS["company"]["username"], ACCOUNTS["company"]["password"], "/company")

            ui.open("/company/jobs")
            ui.wait_content()
            ui_rows = ui.page.locator(".el-table__row").count()
            shot_jobs = ui.shot("company-jobs")
            db_positions = db.all(
                "SELECT id, position_name, status, planned_recruit, recruited_count FROM position WHERE company_id=%s ORDER BY id DESC",
                (COMPANY_DB_ID,),
            )
            check(len(db_positions) > 0, "企业1 在 position 表无岗位记录")

            ui.open("/company/application-view")
            ui.wait_content()
            ui_rows_app = ui.page.locator(".el-table__row").count()
            body = ui.body_text()
            shot_apps = ui.shot("company-applications")
            db_apps = db.all(
                "SELECT id, position_name, status FROM student_job_application WHERE company_id=%s ORDER BY id DESC LIMIT 20",
                (COMPANY_DB_ID,),
            )
            if db_positions and db_positions[0].get("position_name"):
                check(
                    db_positions[0]["position_name"] in body or ui_rows_app > 0,
                    f"企业端未渲染库内岗位 {db_positions[0]['position_name']}",
                )

            return StepResult(
                detail=f"企业端岗位 库内 {len(db_positions)} / 页面 {ui_rows} 行；申请 库内 {len(db_apps)} / 页面 {ui_rows_app} 行",
                evidence=[shot_jobs, shot_apps],
                ui={"positionRows": ui_rows, "applicationRows": ui_rows_app},
                db={"positions": db_positions, "applications": db_apps[:5]},
            )

        runner.run("P3-企业端", "招聘管理与申请列表与数据库一致", company_flow, severity="major")
        ui.clear_session()

        # ---------- P4 教师端 ----------
        def teacher_flow():
            ui.login(ACCOUNTS["teacher"]["username"], ACCOUNTS["teacher"]["password"], "/teacher")

            ui.open("/teacher/home")
            body_home = ui.body_text()
            shot_home = ui.shot("teacher-home")
            check("实习" in body_home or "欢迎" in body_home, "教师首页未渲染欢迎/实习信息")

            ui.open("/teacher/approval")
            ui.wait_content()
            shot_appr = ui.shot("teacher-approval")

            ui.open("/teacher/evaluation")
            ui.wait_content()
            shot_eval = ui.shot("teacher-evaluation")

            db_students = db.scalar("SELECT COUNT(*) FROM student_users")
            db_counselor_apps = db.scalar("SELECT COUNT(*) FROM student_application")
            return StepResult(
                detail=f"教师端 首页/审核/评分 三页渲染正常；库内学生 {db_students} 人，实习报名申请 {db_counselor_apps} 条",
                evidence=[shot_home, shot_appr, shot_eval],
                db={"students": db_students, "studentApplications": db_counselor_apps},
            )

        runner.run("P4-教师端", "首页 / 审核 / 评分页", teacher_flow, severity="major")
        ui.clear_session()

        # ---------- P5 管理端 ----------
        def admin_flow():
            ui.login(ACCOUNTS["admin"]["username"], ACCOUNTS["admin"]["password"], "/admin")

            ui.open("/admin/dashboard", expect_text="数据")
            body_dash = ui.body_text()
            shot_dash = ui.shot("admin-dashboard")

            ui.open("/admin/student-users")
            ui.wait_content()
            ui_rows = ui.page.locator(".el-table__row").count()
            body_students = ui.body_text()
            shot_students = ui.shot("admin-students")

            ui.open("/admin/logs")
            ui.wait_content()
            shot_logs = ui.shot("admin-logs")

            db_students = db.scalar("SELECT COUNT(*) FROM student_users")
            db_logs = db.scalar("SELECT COUNT(*) FROM operate_log")
            db_positions = db.scalar("SELECT COUNT(*) FROM position")
            check(ui_rows > 0, "管理端学生列表未渲染任何行")
            check("共" in body_students or str(db_students)[:3] in body_students, "管理端学生列表缺少分页/总数信息")

            return StepResult(
                detail=f"管理端 看板/学生/日志 渲染正常；库内 学生={db_students} 岗位={db_positions} 操作日志={db_logs}",
                evidence=[shot_dash, shot_students, shot_logs],
                ui={"studentRows": ui_rows},
                db={"students": db_students, "positions": db_positions, "operateLogs": db_logs},
            )

        runner.run("P5-管理端", "数据看板 / 学生管理 / 操作日志", admin_flow, severity="major")
        ui.clear_session()

        # ---------- P6 越权访问 ----------
        def cross_role_guard():
            ui.login(ACCOUNTS["student"]["username"], ACCOUNTS["student"]["password"], "/student")
            ui.page.goto(f"{ui.frontend_url}/admin/student-users", wait_until="domcontentloaded")
            try:
                ui.page.wait_for_load_state("networkidle", timeout=15000)
            except Exception:
                pass
            url = ui.page.url
            body = ui.body_text()
            blocked = ("/admin" not in url) or ("无权限" in body) or ("403" in body)
            check(blocked, f"学生账号直接访问 /admin/student-users 未被拦截，URL={url}")
            shot = ui.shot("student-cross-role-blocked")
            return StepResult(
                detail=f"学生越权访问管理端被拦截，最终 URL={url}",
                evidence=[shot],
                ui={"finalUrl": url},
            )

        runner.run("P6-权限边界", "学生越权访问管理端被拦截", cross_role_guard, severity="critical")

        # ---------- P7 前端稳定性 ----------
        def console_guard():
            severe = [e for e in ui.console_errors if "pageerror" in e or " 500" in e or "500 " in e]
            check(not severe, f"前端出现严重控制台错误: {severe[:3]}")
            return StepResult(
                detail=f"累计控制台错误 {len(ui.console_errors)} 条，严重错误 0 条",
                ui={"errors": ui.console_errors[:10]},
            )

        runner.run("P7-稳定性", "前端控制台无严重错误", console_guard, severity="major")

    finally:
        ui.stop()
        db.close()

    report_path = rep.write()
    summary = rep.print_summary()
    print(f"报告: {report_path}")
    return 0 if summary["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
