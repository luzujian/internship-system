# -*- coding: utf-8 -*-
"""
DeepIntern 实习系统 · 全链路业务自动化测试（写入型，可回滚）

被测主链路（四角色真实页面操作 + 数据库真值核对）：

  P1 管理员   : 导入学生 Excel -> 生成学生账号
  P2 学生     : 默认密码登录 -> 浏览岗位 -> 提交岗位申请
  P3 企业     : 查看申请 -> 同意面试 -> 生成面试邀请 / Offer
  P4 学生     : 实习确认页选择 Offer -> 提交实习确认
  P5 企业     : 确认实习 -> 学生实习状态生效
  P6 学生     : 实习状态 / 申请记录回显
  P7 教师     : 学生管理中可见该实习学生
  P8 清理     : 删除本次测试创建的全部数据并校验无残留

安全约束：
- 测试数据一律带 E2E 前缀，只在本次运行内创建
- 所有写操作走系统自身页面/接口，不直接改业务状态绕过校验
- 结束时按依赖逆序清理，并断言无残留

用法：
    python frontend/tests/e2e/run_business_flow.py            # 完整执行
    python frontend/tests/e2e/run_business_flow.py --keep     # 保留数据（调试用）
    python frontend/tests/e2e/run_business_flow.py --cleanup-only
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

os.environ.setdefault("PYTHONIOENCODING", "utf-8")
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import (  # noqa: E402
    DEFAULT_BACKEND_URL, PROJECT_ROOT, Db, E2E, Reporter, StepResult, StepRunner, check, ensure_dirs,
)
import testdata  # noqa: E402
from testdata import (  # noqa: E402
    CLASS_NAME, COMPANY_USERNAME, MAJOR_NAME, POSITION_NAME, STUDENT_NAME, STUDENT_NO,
    STUDENT_PASSWORD, TestDataRegistry, build_student_excel,
)

ADMIN = {"username": "admin2", "password": "123456"}
TEACHER = {"username": "t001", "password": "123456"}
COMPANY = {"username": COMPANY_USERNAME, "password": "123456"}

COMPANY_ID = 1
APPLY_PHONE = "13800001234"
APPLY_INTRO = "E2E 自动化测试自我介绍：熟悉 Python 与数据分析，希望在实习中提升工程能力。"


def api_data(ui: E2E, fragment: str, method: str = "GET"):
    item = ui.last_api(fragment, method)
    if not item or not isinstance(item.get("body"), dict):
        return None
    body = item["body"]
    data = body.get("data", body)
    if isinstance(data, dict):
        for key in ("records", "rows", "list", "content"):
            if isinstance(data.get(key), list):
                return data[key]
    return data


def row_of(ui: E2E, text: str):
    """返回包含指定文本的表格行 locator。"""
    return ui.page.locator("tr", has_text=text).first


def click_row_button(ui: E2E, row_text: str, button_text: str) -> None:
    row = row_of(ui, row_text)
    row.wait_for(state="visible", timeout=20000)
    btn = row.locator(f"button:has-text('{button_text}')").first
    btn.wait_for(state="visible", timeout=15000)
    btn.click()


def confirm_dialog(ui: E2E, button_text: str, timeout: int = 15000) -> None:
    dlg = ui.page.locator(".el-message-box")
    dlg.wait_for(state="visible", timeout=timeout)
    dlg.locator(f"button:has-text('{button_text}')").first.click()
    try:
        dlg.wait_for(state="hidden", timeout=15000)
    except Exception:
        pass


def wait_message(ui: E2E, text: str, timeout: int = 20000) -> str:
    """等待 ElMessage 成功提示，返回提示文本。"""
    selector = f".el-message:has-text('{text}')"
    ui.page.wait_for_selector(selector, timeout=timeout)
    return ui.page.locator(selector).first.inner_text()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--keep", action="store_true", help="保留测试数据，不执行清理")
    parser.add_argument("--cleanup-only", action="store_true", help="只执行清理并校验")
    parser.add_argument("--headed", action="store_true", help="显示浏览器窗口")
    args = parser.parse_args()

    ensure_dirs()
    rep = Reporter("business-flow")
    ui = E2E(rep)
    runner = StepRunner(rep, ui)
    db = Db()
    registry = TestDataRegistry()

    excel_path = build_student_excel()

    print("=" * 72)
    print("DeepIntern 全链路业务自动化测试")
    print(f"  测试学生 : {STUDENT_NO} / {STUDENT_NAME}  密码 {STUDENT_PASSWORD}")
    print(f"  学院专业 : {MAJOR_NAME}  班级 {CLASS_NAME}")
    print(f"  测试岗位 : {POSITION_NAME}（企业1，存量企业账号）")
    print(f"  学生导入表: {excel_path}")
    print(f"  数据库   : {db.one('SELECT DATABASE() AS db')}")
    print("=" * 72)

    before_counts = registry.snapshot(db)
    rep.note(f"运行前基线: {json.dumps(before_counts, ensure_ascii=False)}")

    if args.cleanup_only:
        existing = db.one("SELECT id FROM student_users WHERE student_user_id=%s", (STUDENT_NO,))
        if existing:
            registry.student_id = existing["id"]
        pos = db.one("SELECT id FROM position WHERE position_name=%s", (POSITION_NAME,))
        if pos:
            registry.position_id = pos["id"]
        removed = registry.cleanup(db)
        left = registry.verify_clean(db)
        print("清理结果:", json.dumps(removed, ensure_ascii=False))
        print("残留检查:", json.dumps(left, ensure_ascii=False))
        db.close()
        return 0 if not left else 1

    ui.start(headless=not args.headed)

    try:
        # ================= P1 管理员导入学生 =================
        def admin_import():
            ui.login(ADMIN["username"], ADMIN["password"], "/admin")
            ui.open("/admin/student-users")
            ui.wait_content()

            ui.page.locator(".header-content + *, .el-dropdown, button:has-text('导入')").first.wait_for(state="visible", timeout=15000)
            ui.page.locator("button:has-text('导入')").first.click()
            menu_item = ui.page.locator(".el-dropdown-menu__item:has-text('选择文件导入')").first
            menu_item.wait_for(state="visible", timeout=15000)
            menu_item.click()
            dialog = ui.page.locator(".el-dialog").filter(has_text="导入Excel学生数据")
            dialog.wait_for(state="visible", timeout=15000)
            shot_dialog = ui.shot("P1-admin-import-dialog")

            ui.page.set_input_files("input[type='file'][accept*='.xlsx']", str(excel_path))
            dialog.locator(".el-dialog__footer button:has-text('导入')").first.click()

            body = ""
            for _ in range(40):
                ui.page.wait_for_timeout(1000)
                body = ui.body_text()
                if "导入完成" in body or STUDENT_NO in body:
                    break
            shot_done = ui.shot("P1-admin-import-result")

            row = db.one("SELECT id, student_user_id AS student_id, name, major_id, class_id, status FROM student_users WHERE student_user_id=%s", (STUDENT_NO,))
            check(row is not None, f"导入后数据库未生成学生 {STUDENT_NO}；页面文本片段={body[-400:]}")
            registry.student_id = row["id"]

            status_row = db.one("SELECT id, status, company_confirm_status FROM student_internship_status WHERE student_id=%s", (row["id"],))
            check(status_row is not None, "导入学生后未自动创建实习状态记录")
            check(status_row["status"] == 0, f"新学生实习状态应为 0，实际 {status_row['status']}")

            return StepResult(
                detail=f"导入成功: id={row['id']} 学号={row['student_id']} 姓名={row['name']}；实习状态已初始化 status=0",
                evidence=[shot_dialog, shot_done],
                db={"student": row, "internshipStatus": status_row},
            )

        runner.run("P1-管理员", "导入学生 Excel 并生成账号", admin_import, severity="critical")
        ui.clear_session()

        # ================= P1.5 企业创建测试岗位 =================
        def company_create_position():
            login = ui.page.request.post(
                f"{DEFAULT_BACKEND_URL}/api/auth/login",
                data={"username": COMPANY["username"], "password": COMPANY["password"]},
            )
            login_body = login.json()
            check(login_body.get("code") == 200, f"企业登录失败: {login_body}")
            token = login_body["data"]["accessToken"]
            headers = {"Authorization": f"Bearer {token}"}
            categories_res = ui.page.request.get(
                f"{DEFAULT_BACKEND_URL}/api/admin/position-categories/public", headers=headers
            )
            categories_body = categories_res.json()
            check(categories_body.get("code") == 200 and categories_body.get("data"),
                  f"岗位类别为空: {categories_body}")
            category = categories_body["data"][0]
            payload = {
                "positionName": POSITION_NAME, "department": category["name"],
                "positionType": "实习", "province": "北京市", "city": "市辖区",
                "district": "东城区", "detailAddress": "E2E测试地址",
                "salaryMin": 1, "salaryMax": 2, "salaryUnit": "K/月",
                "description": "E2E全链路测试岗位", "requirements": "仅用于自动化测试",
                "plannedRecruit": 1, "status": "active", "categoryId": category["id"],
                "providesAccommodation": 0,
            }
            created = ui.page.request.post(
                f"{DEFAULT_BACKEND_URL}/api/company/positions", headers=headers, data=payload
            )
            body = created.json()
            check(body.get("code") == 200, f"创建测试岗位失败: {body}")
            row = db.one("SELECT id, company_id, position_name, status FROM position WHERE position_name=%s",
                         (POSITION_NAME,))
            check(row is not None and row["status"] == "active", f"测试岗位未生效: {row}")
            registry.position_id = row["id"]
            return StepResult(detail=f"测试岗位已创建并生效: id={row['id']} status={row['status']}",
                              db={"position": row})

        runner.run("P1.5-企业", "创建可申请测试岗位", company_create_position, severity="critical")
        ui.clear_session()

        # ================= P2 学生申请岗位 =================
        def student_apply():
            ui.login(STUDENT_NO, STUDENT_PASSWORD, "/student")
            ui.open("/student/profile")
            ui.wait_content()
            body_profile = ui.body_text()

            ui.open("/student/jobs")
            ui.wait_content()
            shot_jobs = ui.shot("P2-student-jobs")

            pos = db.one("SELECT id, company_id, position_name, status FROM position WHERE position_name=%s", (POSITION_NAME,))
            check(pos is not None, f"数据库未找到测试岗位 {POSITION_NAME}")

            search = ui.page.locator("input[placeholder*='搜索']").first
            if search.count():
                search.fill(POSITION_NAME)
                ui.page.keyboard.press("Enter")
                ui.page.wait_for_timeout(2500)

            check(POSITION_NAME in ui.page.inner_text("body"), f"岗位列表中未出现测试岗位 {POSITION_NAME}")
            card = ui.page.locator(f".job-card:has-text('{POSITION_NAME}')").first
            card.wait_for(state="visible", timeout=15000)
            card.locator("button:has-text('立即申请')").first.click()

            dialog = ui.page.locator(".el-dialog").filter(has_text="提交申请")
            dialog.wait_for(state="visible", timeout=15000)
            shot_apply = ui.shot("P2-student-apply-dialog")

            phone = dialog.locator("input[placeholder*='手机'], input[placeholder*='联系']").first
            if phone.count() and (phone.input_value() or "") == "":
                phone.fill(APPLY_PHONE)
            textarea = dialog.locator("textarea").first
            if textarea.count():
                textarea.fill(APPLY_INTRO)

            dialog.locator("button:has-text('提交申请')").first.click()
            msg = wait_message(ui, "申请提交成功", timeout=25000)
            shot_ok = ui.shot("P2-student-apply-success")

            app = db.one(
                "SELECT id, student_id, position_id, company_id, position_name, status, phone, self_introduction "
                "FROM student_job_application WHERE student_id=%s AND position_id=%s ORDER BY id DESC",
                (registry.student_id, pos["id"]),
            )
            check(app is not None, "提交申请后 student_job_application 无记录")
            check(app["status"] == "pending", f"新申请状态应为 pending，实际 {app['status']}")

            mirror = db.one(
                "SELECT id, status FROM internship_application WHERE student_id=%s AND position_id=%s ORDER BY id DESC",
                (registry.student_id, pos["id"]),
            )

            return StepResult(
                detail=f"申请成功: applicationId={app['id']} status=pending；镜像申请记录={mirror['id'] if mirror else '无'}；提示『{msg}』",
                evidence=[shot_jobs, shot_apply, shot_ok],
                db={"application": app, "mirror": mirror, "position": pos, "profileRendered": "个人中心" in body_profile},
            )

        runner.run("P2-学生", "浏览岗位并提交实习申请", student_apply, severity="critical")
        ui.clear_session()

        # ================= P3 企业处理申请 =================
        def company_approve():
            ui.login(COMPANY["username"], COMPANY["password"], "/company")
            ui.open("/company/application-view")
            ui.wait_content()
            shot_list = ui.shot("P3-company-applications")

            check(STUDENT_NAME in ui.page.inner_text("body"), f"企业申请列表未出现测试学生 {STUDENT_NAME}")
            click_row_button(ui, STUDENT_NAME, "同意面试")
            confirm_dialog(ui, "确定同意")
            msg = wait_message(ui, "已同意该学生的申请", timeout=25000)
            shot_ok = ui.shot("P3-company-approved")

            app = db.one(
                "SELECT id, status FROM student_job_application WHERE student_id=%s ORDER BY id DESC",
                (registry.student_id,),
            )
            check(app is not None, "企业同意后未找到申请记录")
            check(app["status"] in ("approved", "interview_passed", "pending_interview"),
                  f"企业同意后面试状态异常: {app['status']}")
            if app["status"] != "interview_passed":
                click_row_button(ui, STUDENT_NAME, "面试通过")
                confirm_dialog(ui, "确定")
                wait_message(ui, "已确认面试通过", timeout=25000)
                app = db.one(
                    "SELECT id, status FROM student_job_application WHERE student_id=%s ORDER BY id DESC",
                    (registry.student_id,),
                )
                check(app["status"] == "interview_passed", f"面试通过状态未生效: {app}")

            invitation = db.one(
                "SELECT id, company_id, position_id, student_id, status, interview_type "
                "FROM interview_invitation WHERE student_id=%s ORDER BY id DESC",
                (registry.student_id,),
            )
            check(invitation is not None, "企业同意后未生成面试邀请/Offer（interview_invitation）")
            registry.created.setdefault("invitation_id", []).append(invitation["id"])

            return StepResult(
                detail=f"企业同意面试成功: application.status={app['status']}；offerId={invitation['id']} 状态={invitation['status']}；提示『{msg}』",
                evidence=[shot_list, shot_ok],
                db={"application": app, "invitation": invitation},
            )

        runner.run("P3-企业", "查看申请并同意面试（生成 Offer）", company_approve, severity="critical")
        ui.clear_session()

        # ================= P4 学生提交实习确认 =================
        def student_confirm():
            ui.login(STUDENT_NO, STUDENT_PASSWORD, "/student")
            ui.open("/student/internship-confirmation-form")
            ui.wait_content()
            off_body = ui.page.inner_text("body")
            shot_form = ui.shot("P4-student-confirmation-form")
            check(POSITION_NAME in off_body or "实习确认" in off_body, "实习确认页未渲染内容")

            invitation = db.one(
                "SELECT id FROM interview_invitation WHERE student_id=%s ORDER BY id DESC",
                (registry.student_id,),
            )
            check(invitation is not None, "缺少面试邀请，无法选择 Offer")

            offer = ui.page.locator(".offer-item", has_text=POSITION_NAME).first
            offer.wait_for(state="visible", timeout=15000)
            offer.click()

            remark = ui.page.locator("textarea").first
            if remark.count():
                remark.fill("E2E 自动化测试提交的实习确认")

            confirm_btn = ui.page.locator("button:has-text('确认该实习')").first
            confirm_btn.wait_for(state="visible", timeout=15000)
            confirm_btn.click()
            ui.page.wait_for_timeout(800)
            dlg = ui.page.locator(".el-message-box")
            if dlg.count() and dlg.is_visible():
                dlg.locator("button:has-text('确认提交')").first.click()
            msg = wait_message(ui, "实习确认已提交", timeout=25000)
            shot_ok = ui.shot("P4-student-confirmation-submitted")

            record = db.one(
                "SELECT id, student_id, company_id, position_id, status "
                "FROM internship_confirmation_record WHERE student_id=%s ORDER BY id DESC",
                (registry.student_id,),
            )
            check(record is not None, "提交实习确认后 internship_confirmation_record 无记录")
            registry.confirmation_id = record["id"]

            assignment = db.one(
                "SELECT id, stage_no, status, current_flag, source_type FROM internship_stage_assignment "
                "WHERE student_id=%s ORDER BY id DESC",
                (registry.student_id,),
            )

            return StepResult(
                detail=f"实习确认提交成功: recordId={record['id']} status={record['status']}；阶段记录={assignment['id'] if assignment else '无'}；提示『{msg}』",
                evidence=[shot_form, shot_ok],
                db={"confirmationRecord": record, "stageAssignment": assignment},
            )

        runner.run("P4-学生", "选择 Offer 并提交实习确认", student_confirm, severity="critical")
        ui.clear_session()

        # ================= P5 企业确认实习 =================
        def company_confirm():
            ui.login(COMPANY["username"], COMPANY["password"], "/company")
            ui.open("/company/internship-confirm")
            ui.wait_content()
            shot_list = ui.shot("P5-company-confirm-list")

            record_id = registry.confirmation_id
            row = ui.page.locator("tr", has_text=STUDENT_NAME).first
            if row.count() == 0:
                row = ui.page.locator(f"tr:has-text('{POSITION_NAME}')").first
            row.wait_for(state="visible", timeout=20000)
            row.locator("button:has-text('确认')").first.click()

            dlg = ui.page.locator(".el-message-box")
            dlg.wait_for(state="visible", timeout=15000)
            dlg.locator("button:has-text('确定'), button:has-text('确认')").first.click()
            msg = wait_message(ui, "已确认", timeout=25000)
            shot_ok = ui.shot("P5-company-confirmed")

            record = db.one(
                "SELECT id, status FROM internship_confirmation_record WHERE id=%s",
                (record_id,),
            )
            check(record is not None, "企业确认后记录丢失")
            check(str(record["status"]) in ("1", "2"), f"企业确认后记录状态应推进，实际 {record['status']}")

            status_row = db.one(
                "SELECT id, status, company_confirm_status, company_name, position_name, company_id, position_id "
                "FROM student_internship_status WHERE student_id=%s",
                (registry.student_id,),
            )
            check(status_row is not None, "企业确认后学生实习状态记录丢失")
            check(status_row["status"] >= 1, f"企业确认后学生实习状态应 >=1（已落实实习），实际 {status_row['status']}")
            check(status_row["company_name"] == COMPANY_USERNAME or status_row["company_id"] == COMPANY_ID,
                  f"学生实习单位与企业1不一致: {status_row}")

            return StepResult(
                detail=(
                    f"企业确认成功: record.status={record['status']}；"
                    f"学生实习状态={status_row['status']} 单位={status_row['company_name']} 岗位={status_row['position_name']}；提示『{msg}』"
                ),
                evidence=[shot_list, shot_ok],
                db={"confirmationRecord": record, "internshipStatus": status_row},
            )

        runner.run("P5-企业", "确认实习并生效学生实习状态", company_confirm, severity="critical")
        ui.clear_session()

        # ================= P6 学生端回显 =================
        def student_echo():
            ui.login(STUDENT_NO, STUDENT_PASSWORD, "/student")
            ui.open("/student/home")
            ui.wait_content()
            home_body = ui.page.inner_text("body")
            shot_home = ui.shot("P6-student-home")

            ui.open("/student/applications")
            ui.wait_content()
            apps_body = ui.page.inner_text("body")
            shot_apps = ui.shot("P6-student-applications")

            status_row = db.one(
                "SELECT status, company_name, position_name FROM student_internship_status WHERE student_id=%s",
                (registry.student_id,),
            )
            check(POSITION_NAME in apps_body, f"学生申请页未回显岗位 {POSITION_NAME}")
            check(
                (status_row.get("company_name") or "") in home_body or "实习" in home_body,
                "学生首页未回显实习信息",
            )
            return StepResult(
                detail=f"学生端回显正常: 岗位 {POSITION_NAME} 出现在申请页；首页实习信息可见；库内状态={status_row['status']}",
                evidence=[shot_home, shot_apps],
                db={"internshipStatus": status_row},
            )

        runner.run("P6-学生", "首页与申请记录回显确认结果", student_echo, severity="major")
        ui.clear_session()

        # ================= P7 教师端可见性 =================
        def teacher_visibility():
            ui.login(TEACHER["username"], TEACHER["password"], "/teacher")
            ui.open("/teacher/student-tracking")
            ui.wait_content()
            body = ui.page.inner_text("body")
            shot = ui.shot("P7-teacher-tracking")
            found = STUDENT_NAME in body or STUDENT_NO in body

            if not found:
                search = ui.page.locator("input[placeholder*='搜索'], input[placeholder*='学号'], input[placeholder*='姓名']").first
                if search.count():
                    search.fill(STUDENT_NO)
                    ui.page.keyboard.press("Enter")
                    ui.page.wait_for_timeout(2500)
                    body = ui.page.inner_text("body")
                    found = STUDENT_NAME in body or STUDENT_NO in body
                    shot = ui.shot("P7-teacher-tracking-search")

            check(found, "教师端实习跟踪未显示测试学生：请确认测试教师具备该班级数据范围，否则为真实可见性缺陷")
            return StepResult(
                detail="教师端实习跟踪可见测试学生",
                evidence=[shot],
                db={"teacherVisible": found},
            )

        runner.run("P7-教师", "教师端实习跟踪可见性", teacher_visibility, severity="minor")
        ui.clear_session()

    finally:
        ui.stop()

        if args.keep:
            rep.note("按 --keep 参数要求保留测试数据，跳过清理")
            rep.warn("P8-清理", "测试数据保留（调试模式）", "数据未清理，请手工执行 --cleanup-only", severity="minor")
        else:
            def cleanup():
                removed = registry.cleanup(db)
                left = registry.verify_clean(db)
                check(not left, f"清理后仍有残留: {left}")
                total = sum(v for k, v in removed.items() if isinstance(v, int))
                return StepResult(
                    detail=f"已删除测试数据 {total} 行，残留检查通过（无 E2E 痕迹）",
                    db={"removed": removed, "leftovers": left},
                )

            runner.run("P8-清理", "回滚本次全部测试数据", cleanup, severity="critical")

        after_counts = registry.snapshot(db)
        delta = {k: after_counts.get(k, 0) - before_counts.get(k, 0) for k in before_counts}
        rep.note(f"运行后计数: {json.dumps(after_counts, ensure_ascii=False)}")
        rep.note(f"与基线差值: {json.dumps(delta, ensure_ascii=False)}")
        db.close()

    report_path = rep.write()
    summary = rep.print_summary()
    print(f"报告: {report_path}")
    return 0 if summary["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
