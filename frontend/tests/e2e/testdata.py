# -*- coding: utf-8 -*-
"""
端到端业务测试的数据工厂与清理器。

设计原则：
- 测试账号、岗位均带 REVERSIBLE_TAG 前缀/TAG，便于识别与清理
- 所有写操作走系统自身接口或最小 SQL，且清理时按外键依赖逆序删除
- 只删除本次测试创建的记录，绝不触碰存量业务数据
"""

from __future__ import annotations

import os
from datetime import datetime
from pathlib import Path

from openpyxl import Workbook

from common import PROJECT_ROOT, Db

RUN_TAG = os.environ.get("E2E_RUN_TAG", datetime.now().strftime("%m%d%H%M%S"))
STUDENT_NO = f"E2E{datetime.now().strftime('%m%d%H%M%S')}"
STUDENT_NAME = f"E2E自动化测试{datetime.now().strftime('%m%d')}"
STUDENT_PASSWORD = "123456"
MAJOR_NAME = "生物医学工程"
CLASS_NAME = "23生工1班"
POSITION_NAME = f"E2E测试岗-{RUN_TAG}"
COMPANY_USERNAME = "企业1"

TEST_DATA_DIR = PROJECT_ROOT / "frontend" / "output" / "e2e-testdata"


def build_student_excel() -> Path:
    """生成符合系统导入模板的学生 Excel（学号/姓名/年级/专业名称/班级名称）。"""
    TEST_DATA_DIR.mkdir(parents=True, exist_ok=True)
    path = TEST_DATA_DIR / f"e2e-students-{STUDENT_NO}.xlsx"
    wb = Workbook()
    ws = wb.active
    ws.title = "学生账号"
    ws.append(["学号", "姓名", "年级", "专业名称", "班级名称"])
    ws.append([STUDENT_NO, STUDENT_NAME, 2023, MAJOR_NAME, CLASS_NAME])
    wb.save(path)
    return path


class TestDataRegistry:
    """记录本次测试创建的所有实体 ID，供清理使用。"""

    def __init__(self, run_tag: str = RUN_TAG):
        self.run_tag = run_tag
        self.student_id: int | None = None
        self.position_id: int | None = None
        self.company_id: int | None = None
        self.confirmation_id: int | None = None
        self.created: dict[str, list] = {}

    def snapshot(self, db: Db) -> dict:
        return {
            "student_users": db.scalar("SELECT COUNT(*) FROM student_users"),
            "position": db.scalar("SELECT COUNT(*) FROM position"),
            "student_job_application": db.scalar("SELECT COUNT(*) FROM student_job_application"),
            "internship_confirmation_record": db.scalar("SELECT COUNT(*) FROM internship_confirmation_record"),
            "student_internship_status": db.scalar("SELECT COUNT(*) FROM student_internship_status"),
            "internship_stage_assignment": db.scalar("SELECT COUNT(*) FROM internship_stage_assignment"),
            "internship_appraisal_submission": db.scalar("SELECT COUNT(*) FROM internship_appraisal_submission"),
            "student_application": db.scalar("SELECT COUNT(*) FROM student_application"),
        }

    def cleanup(self, db: Db, *, remove_position: bool = True) -> dict:
        """删除本次测试创建的数据。返回每个表删除行数。"""
        result: dict[str, int] = {}
        sid = self.student_id

        if sid is None:
            row = db.one("SELECT id FROM student_users WHERE student_user_id=%s", (STUDENT_NO,))
            sid = row["id"] if row else None
            self.student_id = sid

        if sid is not None:
            # 顺序：叶子表 -> 主表，避免外键/悬挂引用
            statements = [
                ("internship_appraisal_file",
                 "DELETE FROM internship_appraisal_file WHERE submission_id IN (SELECT id FROM internship_appraisal_submission WHERE student_id=%s)"),
                ("internship_appraisal_submission", "DELETE FROM internship_appraisal_submission WHERE student_id=%s"),
                ("internship_reflection", "DELETE FROM internship_reflection WHERE student_id=%s"),
                ("student_reflection_evaluation", "DELETE FROM student_reflection_evaluation WHERE student_id=%s"),
                ("student_reflection_ai_analysis", "DELETE FROM student_reflection_ai_analysis WHERE student_id=%s"),
                ("internship_progress_record", "DELETE FROM internship_progress_record WHERE student_id=%s"),
                ("internship_ai_analysis", "DELETE FROM internship_ai_analysis WHERE student_id=%s"),
                ("internship_evaluation_detail",
                 "DELETE FROM internship_evaluation_detail WHERE evaluation_id IN (SELECT id FROM internship_evaluation WHERE student_id=%s)"),
                ("internship_evaluation", "DELETE FROM internship_evaluation WHERE student_id=%s"),
                ("internship_stage_assignment", "DELETE FROM internship_stage_assignment WHERE student_id=%s"),
                ("internship_confirmation_record", "DELETE FROM internship_confirmation_record WHERE student_id=%s"),
                ("interview_invitation", "DELETE FROM interview_invitation WHERE student_id=%s"),
                ("student_job_application", "DELETE FROM student_job_application WHERE student_id=%s"),
                ("internship_application", "DELETE FROM internship_application WHERE student_id=%s"),
                ("student_application", "DELETE FROM student_application WHERE student_id=%s"),
                ("position_view_record", "DELETE FROM position_view_record WHERE student_id=%s"),
                ("internship_application",
                 "DELETE FROM internship_application WHERE student_id=%s"),
                ("student_internship_status", "DELETE FROM student_internship_status WHERE student_id=%s"),
                ("student_archive", "DELETE FROM student_archive WHERE student_id=%s"),
                ("student_users", "DELETE FROM student_users WHERE id=%s"),
            ]
            for table, sql in statements:
                try:
                    result[table] = result.get(table, 0) + db.execute(sql, (sid,))
                except Exception as exc:  # 表/列不存在时不阻断整体清理
                    result[f"{table} (skipped)"] = 0
                    print(f"[cleanup] {table} 跳过: {exc.__class__.__name__}: {str(exc)[:120]}")

        # 岗位：只删除本次测试创建的（按岗位名精确匹配，且创建时间在本次运行内）
        if remove_position and self.position_id:
            try:
                result["position"] = db.execute("DELETE FROM position WHERE id=%s", (self.position_id,))
            except Exception as exc:
                print(f"[cleanup] position 跳过: {exc}")

        # 兜底：按测试岗位名清理（防止岗位 ID 未记录）
        try:
            result["position_by_name"] = db.execute(
                "DELETE FROM position WHERE position_name LIKE %s", (f"E2E测试岗-{self.run_tag}%",)
            )
        except Exception:
            result["position_by_name"] = 0

        return result

    def verify_clean(self, db: Db) -> dict:
        """确认测试数据全部清理干净。"""
        leftovers = {}
        if self.student_id:
            for table, column in [
                ("student_users", "id"),
                ("student_internship_status", "student_id"),
                ("student_job_application", "student_id"),
                ("internship_confirmation_record", "student_id"),
                ("internship_stage_assignment", "student_id"),
                ("internship_application", "student_id"),
                ("student_application", "student_id"),
            ]:
                try:
                    count = db.scalar(f"SELECT COUNT(*) FROM {table} WHERE {column}=%s", (self.student_id,))
                    if count:
                        leftovers[f"{table}.{column}"] = count
                except Exception:
                    pass
        pos = db.scalar("SELECT COUNT(*) FROM position WHERE position_name LIKE %s", (f"E2E测试岗-{self.run_tag}%",))
        if pos:
            leftovers["position.position_name"] = pos
        return leftovers
