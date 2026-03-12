#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vue to TypeScript Converter
Converts Vue 3 <script setup> files to TypeScript by:
1. Changing <script setup> to <script setup lang="ts">"
2. Adding type imports from @/types/admin
3. Adding type annotations to ref declarations
4. Adding return types to functions
"""

import re
import os
from pathlib import Path

# Base directory
BASE_DIR = Path(r'D:\Web-work\Internship\frontend\src\views\admin')

# Files to convert
FILES_TO_CONVERT = [
    'ScoringRuleManagement.vue',
    'DepartmentMajorManagement.vue',
    'LearningResource.vue',
    'ResourceDocumentManagement.vue',
    'Dashboard.vue',
    'PositionCategoryManagement.vue',
]

# File-specific configurations
FILE_CONFIGS = {
    'ScoringRuleManagement.vue': {
        'type_imports': ['ScoringRule'],
        'script_patterns': {
            r'const loading = ref\(false\)': 'const loading = ref<boolean>(false)',
            r'const saving = ref\(false\)': 'const saving = ref<boolean>(false)',
            r'const dialogVisible = ref\(false\)': 'const dialogVisible = ref<boolean>(false)',
            r'const weightDialogVisible = ref\(false\)': 'const weightDialogVisible = ref<boolean>(false)',
            r'const dialogTitle = ref\(\'\'\)': 'const dialogTitle = ref<string>(\'\')',
            r'const ruleList = ref\(\[\]\)': 'const ruleList = ref<ScoringRule[]>([])',
            r'const filterCategory = ref\(\'\'\)': 'const filterCategory = ref<string>(\'\')',
            r'const filterStatus = ref\(\'\'\)': 'const filterStatus = ref<string | number>(\'\')',
            r'const activeCategories = ref\(\[\]\)': 'const activeCategories = ref<string[]>([])',
            r'const formRef = ref\(null\)': 'const formRef = ref<InstanceType<typeof ElForm> | null>(null)',
            r'const weightFormRef = ref\(null\)': 'const weightFormRef = ref<InstanceType<typeof ElForm> | null>(null)',
            r'const categoryWeights = ref\(\{\}\)': 'const categoryWeights = ref<Record<string, number>>({})',
        }
    },
    'DepartmentMajorManagement.vue': {
        'type_imports': ['Department', 'Major', 'Class'],
    },
    'LearningResource.vue': {
        'type_imports': ['ResourceDocument'],
    },
    'ResourceDocumentManagement.vue': {
        'type_imports': ['ResourceDocument'],
    },
    'Dashboard.vue': {
        'type_imports': ['TeacherUser', 'Announcement', 'OperationLog', 'Feedback'],
    },
    'PositionCategoryManagement.vue': {
        'type_imports': ['Position', 'PositionCategory'],
    },
}

def convert_script_tag(content):
    """Change <script setup> to <script setup lang="ts">"""
    content = re.sub(r'<script\s+setup\s*>', '<script setup lang="ts">', content, flags=re.IGNORECASE)
    return content

def add_type_imports(content, type_imports):
    """Add type imports from @/types/admin"""
    if "from '@/types/admin'" in content or 'from "@/types/admin"' in content:
        return content

    lines = content.split('\n')
    new_lines = []
    imports_added = False

    type_import_line = f"import type {{ {', '.join(type_imports)} }} from '@/types/admin'\n"

    for i, line in enumerate(lines):
        new_lines.append(line)
        if not imports_added and "from 'vue'" in line:
            new_lines.append(type_import_line)
            imports_added = True

    return '\n'.join(new_lines)

def add_ref_types(content):
    """Add type annotations to ref declarations"""
    conversions = [
        (r"(const|let)\s+(\w+)\s*=\s*ref\s*\(\s*false\s*\)", r"\1 \2 = ref<boolean>(false)"),
        (r"(const|let)\s+(\w+)\s*=\s*ref\s*\(\s*true\s*\)", r"\1 \2 = ref<boolean>(true)"),
        (r"(const|let)\s+(\w+)\s*=\s*ref\s*\(\s*0\s*\)", r"\1 \2 = ref<number>(0)"),
        (r"(const|let)\s+(\w+)\s*=\s*ref\s*\(\s*1\s*\)", r"\1 \2 = ref<number>(1)"),
        (r"(const|let)\s+(\w+)\s*=\s*ref\s*\(\s*''\s*\)", r"\1 \2 = ref<string>('')"),
        (r'(const|let)\s+(\w+)\s*=\s*ref\s*\(\s*""\s*\)', r'\1 \2 = ref<string>("")'),
        (r"(const|let)\s+(\w+)FormRef\s*=\s*ref\s*\(\s*null\s*\)", r"\1 \2FormRef = ref<InstanceType<typeof ElForm> | null>(null)"),
        (r"(const|let)\s+(\w+)Ref\s*=\s*ref\s*\(\s*null\s*\)", r"\1 \2Ref = ref<InstanceType<typeof ElForm> | null>(null)"),
        (r"(const|let)\s+(\w+)\s*=\s*ref\s*\(\s*\[\s*\]\s*\)", r"\1 \2 = ref<unknown[]>([])"),
        (r"(const|let)\s+(\w+)\s*=\s*ref\s*\(\s*\{\s*\}\s*\)", r"\1 \2 = ref<Record<string, unknown>>({})"),
    ]

    for pattern, replacement in conversions:
        content = re.sub(pattern, replacement, content)

    return content

def add_function_return_types(content):
    """Add return types to async functions"""
    content = re.sub(
        r'(const\s+\w+\s*=\s*async\s*\([^)]*\))\s*=>',
        r'\1: Promise<void> =>',
        content
    )
    return content

def convert_file(file_path, config):
    """Convert a single Vue file to TypeScript"""
    print(f"Converting: {file_path}")

    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Step 1: Convert script tag
    content = convert_script_tag(content)

    # Step 2: Add type imports
    type_imports = config.get('type_imports', ['BaseSearchForm'])
    content = add_type_imports(content, type_imports)

    # Step 3: Add ref types
    content = add_ref_types(content)

    # Step 4: Add function return types
    content = add_function_return_types(content)

    # Step 5: Apply file-specific patterns
    script_patterns = config.get('script_patterns', {})
    for pattern, replacement in script_patterns.items():
        content = re.sub(pattern, replacement, content)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

    print(f"  Done: {file_path}")
    return True

def main():
    """Main function"""
    print("=" * 60)
    print("Vue to TypeScript Converter")
    print("=" * 60)

    success_count = 0
    error_count = 0

    for filename in FILES_TO_CONVERT:
        file_path = BASE_DIR / filename

        if not file_path.exists():
            print(f"ERROR: File not found: {file_path}")
            error_count += 1
            continue

        config = FILE_CONFIGS.get(filename, {'type_imports': ['BaseSearchForm']})

        try:
            if convert_file(file_path, config):
                success_count += 1
        except Exception as e:
            print(f"ERROR converting {file_path}: {e}")
            error_count += 1

    print("=" * 60)
    print(f"Conversion complete!")
    print(f"  Success: {success_count}")
    print(f"  Errors: {error_count}")
    print("=" * 60)

if __name__ == '__main__':
    main()
