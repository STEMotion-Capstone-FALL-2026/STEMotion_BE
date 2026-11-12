#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify-quotes.py - Kiểm tra trích dẫn nguyên văn YCCĐ trong stem-demo-pack.v1.json
so với các tài liệu chương trình chính thức (PDF) lưu tại thư mục nguồn.
"""

import sys
import os
import json
import hashlib
import argparse

try:
    sys.stdout.reconfigure(encoding='utf-8')
except Exception:
    pass

try:
    from pypdf import PdfReader
except ImportError:
    print("LỖI: Cần thư viện pypdf. Cài đặt qua: pip install pypdf", file=sys.stderr)
    sys.exit(2)

def normalize_text(text: str) -> str:
    """Chuẩn hoá khoảng trắng và ký tự nối để so khớp chuỗi con chính xác trong PDF."""
    if not text:
        return ""
    # Loại bỏ khoảng trắng thừa, dấu gạch nối ngắt dòng
    clean = "".join(text.split()).lower()
    clean = clean.replace("-", "").replace("–", "").replace("—", "")
    return clean

def main():
    parser = argparse.ArgumentParser(description="Kiểm tra trích dẫn nguyên văn YCCĐ STEM")
    parser.add_argument("--sources-dir", default=r"D:\stem-sources",
                        help="Đường dẫn thư mục chứa các file PDF chính thức (mặc định D:\\stem-sources)")
    parser.add_argument("--pack", default=os.path.join(os.path.dirname(__file__), "..", "..",
                                                      "src", "main", "resources", "stem", "demo-pack",
                                                      "stem-demo-pack.v1.json"),
                        help="Đường dẫn file gói demo pack")
    args = parser.parse_args()

    pack_path = os.path.abspath(args.pack)
    sources_dir = os.path.abspath(args.sources_dir)

    print("=" * 70)
    print("  STEM DEMO: KIỂM CHỨNG TRÍCH DẪN NGUYÊN VĂN YCCĐ (VERIFY QUOTES)")
    print("=" * 70)
    print(f"Gói dữ liệu : {pack_path}")
    print(f"Thư mục nguồn: {sources_dir}")
    print("-" * 70)

    if not os.path.exists(pack_path):
        print(f"LỖI: Không tìm thấy file gói dữ liệu tại {pack_path}", file=sys.stderr)
        sys.exit(1)

    if not os.path.exists(sources_dir):
        print(f"CẢNH BÁO: Thư mục nguồn {sources_dir} không tồn tại!", file=sys.stderr)

    with open(pack_path, "r", encoding="utf-8") as f:
        pack = json.load(f)

    outcomes = [it for it in pack.get("items", []) if it.get("kind") == "OUTCOME"]
    total_outcomes = len(outcomes)
    passed_count = 0
    null_count = 0
    failed_count = 0

    print(f"Tổng số YCCĐ cần kiểm tra: {total_outcomes}\n")

    for it in outcomes:
        code = it.get("data", {}).get("code", it.get("id"))
        logical_key = it.get("logicalKey")
        text = it.get("data", {}).get("text")
        summary = it.get("data", {}).get("summary")
        locator = it.get("data", {}).get("locator")

        print(f"[{code}] ({logical_key}):")

        if text is None:
            null_count += 1
            print("  Trạng thái: text = null (Chưa có trích dẫn nguyên văn đối soát)")
            print(f"  Tóm tắt ý đồ sư phạm: {summary}")
            print("  -> Đưa vào danh sách 'CẦN NGƯỜI KIỂM TRA'")
            print()
            continue

        if not locator:
            failed_count += 1
            print("  [FAIL] Thiếu thông tin locator")
            print()
            continue

        file_name = locator.get("fileName")
        expected_sha = locator.get("sha256")
        page_num = locator.get("page")
        section = locator.get("section", "")

        if not file_name or not page_num:
            failed_count += 1
            print("  [FAIL] locator thiếu fileName hoặc page")
            print()
            continue

        file_path = os.path.join(sources_dir, file_name)
        if not os.path.exists(file_path):
            failed_count += 1
            print(f"  [FAIL] Không tìm thấy file nguồn '{file_name}' trong {sources_dir}")
            print()
            continue

        # 1. Kiểm tra SHA-256
        with open(file_path, "rb") as sf:
            file_bytes = sf.read()
        actual_sha = hashlib.sha256(file_bytes).hexdigest()

        if expected_sha and actual_sha != expected_sha:
            failed_count += 1
            print(f"  [FAIL] SHA-256 không khớp!")
            print(f"         Kỳ vọng: {expected_sha}")
            print(f"         Thực tế: {actual_sha}")
            print()
            continue

        # 2. Đọc trang PDF và trích xuất text
        try:
            reader = PdfReader(file_path)
            total_pages = len(reader.pages)
            if page_num < 1 or page_num > total_pages:
                failed_count += 1
                print(f"  [FAIL] Số trang {page_num} không hợp lệ (tài liệu có {total_pages} trang)")
                print()
                continue

            page_text = reader.pages[page_num - 1].extract_text() or ""
        except Exception as e:
            failed_count += 1
            print(f"  [FAIL] Lỗi đọc PDF trang {page_num}: {e}")
            print()
            continue

        # 3. So khớp chuỗi con sau chuẩn hoá khoảng trắng
        norm_page = normalize_text(page_text)
        norm_quote = normalize_text(text)

        if norm_quote in norm_page:
            passed_count += 1
            print(f"  [PASS] Khớp nguyên văn tại trang {page_num} của {file_name}")
            print(f"         Chủ đề/mục: {section}")
            print(f"         SHA-256: {actual_sha[:16]}... (khớp)")
            print(f"         Trích dẫn: \"{text}\"")
        else:
            failed_count += 1
            print(f"  [FAIL] Không tìm thấy trích dẫn nguyên văn tại trang {page_num} của {file_name}")
            print(f"         Trích dẫn tìm: \"{text}\"")

        print()

    print("=" * 70)
    print("  KẾT QUẢ TỔNG HỢP VERIFY-QUOTES")
    print("=" * 70)
    print(f"  Số YCCĐ đạt (PASS nguyên văn)      : {passed_count}")
    print(f"  Số YCCĐ chưa đối soát (text: null) : {null_count}")
    print(f"  Số YCCĐ lỗi (FAIL)                 : {failed_count}")
    print(f"  YCCĐ qua verify-quotes: {passed_count}/{total_outcomes}")
    print("=" * 70)

    if failed_count > 0:
        sys.exit(1)
    sys.exit(0)

if __name__ == "__main__":
    main()
