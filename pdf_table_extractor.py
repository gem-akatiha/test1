"""
pdf_compare.py

A utility class to compare two PDFs (text + tables) exactly while being
robust to different ordering of table rows.

Dependencies:
    - pdfplumber
    - pandas

Usage:
    from pdf_compare import PDFComparer
    comparer = PDFComparer()
    result = comparer.compare_pdfs("pdf1.pdf", "pdf2.pdf")
    print(result)
"""

from collections import Counter
from dataclasses import dataclass
import difflib
import pdfplumber
import pandas as pd
import re
from typing import List, Tuple, Dict, Any


@dataclass
class ComparisonResult:
    """
    Simple dataclass to store comparison output.
    """
    text_equal: bool
    text_diffs: List[str]
    tables_equal: bool
    tables_missing_in_pdf2: List[Tuple[int, Tuple[str, ...], int]]  # (table_index, row_tuple, count_diff)
    tables_missing_in_pdf1: List[Tuple[int, Tuple[str, ...], int]]  # rows in pdf2 not in pdf1
    details: Dict[str, Any]


class PDFComparer:
    """
    Compares two PDFs for exact textual and tabular equality while ignoring
    ordering of table rows.

    Main public method:
        compare_pdfs(pdf1_path, pdf2_path, *, text_order_sensitive=True, treat_table_headers=True) -> ComparisonResult

    Parameters you can tweak:
        - text_order_sensitive: if False, textual comparison is done by comparing sorted lines (order-insensitive).
                               If True, it compares lines in reading order and returns a unified diff.
        - treat_table_headers: if True, attempts to detect and compare table headers first. If headers differ,
                               that is reported as a mismatch.
    """

    def __init__(self):
        # Precompile regex to collapse spaces/newlines for normalization
        self._multi_space_re = re.compile(r'\s+')

    # ---------------------------
    # Extraction & Normalization
    # ---------------------------
    def _normalize_text(self, s: str) -> str:
        """
        Normalize textual strings so that formatting differences don't cause false mismatches.
        For exact comparison per user's requirement we do:
          - strip leading/trailing whitespace
          - collapse all whitespace sequences to a single space
          - keep case as-is (exact comparison), but you can easily lower() if desired
        """
        if s is None:
            return ''
        s = s.strip()
        # collapse all whitespace (spaces, newlines, tabs) into single spaces
        s = self._multi_space_re.sub(' ', s)
        return s

    def _normalize_cell(self, s: Any) -> str:
        """
        Normalize a table cell. Accepts non-str (None, numbers) and converts to string first.
        Then perform same normalization as text.
        """
        if s is None:
            return ''
        # If it's not a string, convert to string (this preserves exact textual representation)
        if not isinstance(s, str):
            s = str(s)
        return self._normalize_text(s)

    def extract_text_lines(self, pdf_path: str) -> List[str]:
        """
        Extracts and normalizes textual lines from all pages of the pdf in reading order.
        Returns a list of normalized lines (non-empty).
        """
        lines = []
        with pdfplumber.open(pdf_path) as pdf:
            for page_no, page in enumerate(pdf.pages, start=1):
                raw = page.extract_text() or ""
                # split into lines, normalize each, and keep non-empty lines
                for ln in raw.splitlines():
                    nln = self._normalize_text(ln)
                    if nln != '':
                        lines.append(nln)
        return lines

    def extract_all_table_rows(self, pdf_path: str) -> List[List[str]]:
        """
        Extracts all tables from all pages. Flattens them into a list of rows.
        Each row is a list of normalized cell-strings.

        Note:
            - pdfplumber returns lists of lists for tables; we normalize each cell.
            - Many PDFs have merged/empty cells; we still capture them as empty strings.
        """
        all_rows: List[List[str]] = []
        with pdfplumber.open(pdf_path) as pdf:
            for page_no, page in enumerate(pdf.pages, start=1):
                # extract_tables returns list of tables, each table is list of row-lists
                tables = page.extract_tables() or []
                for table_idx, table in enumerate(tables):
                    for row in table:
                        # sometimes pdfplumber returns None for missing cells; normalize cell-by-cell
                        norm_row = [self._normalize_cell(cell) for cell in row]
                        # If the whole row is empty, skip it
                        if any(cell != '' for cell in norm_row):
                            all_rows.append(norm_row)
        return all_rows

    # ---------------------------
    # Comparison Helpers
    # ---------------------------
    def _text_diffs(self, lines1: List[str], lines2: List[str]) -> List[str]:
        """
        Returns a unified diff (list of strings) between two lists of lines.
        """
        # difflib.unified_diff returns an iterator of strings
        diff = list(difflib.unified_diff(lines1, lines2, fromfile='pdf1', tofile='pdf2', lineterm=''))
        return diff

    def _compare_tables_order_insensitive(self,
                                          table_rows_pdf1: List[List[str]],
                                          table_rows_pdf2: List[List[str]]) -> Tuple[bool, List[Tuple[int, Tuple[str, ...], int]], List[Tuple[int, Tuple[str, ...], int]]]:
        """
        Compare lists of table rows (flattened across all tables) in an order-insensitive
        way using Counter. Each row is converted to a tuple of strings.

        Returns:
            - equality bool
            - list of (dummy_table_index, row_tuple, count_diff) missing in pdf2 (present more times in pdf1)
            - list of (dummy_table_index, row_tuple, count_diff) missing in pdf1 (present more in pdf2)

        Note: Because we flattened across all tables, we don't preserve which page/table a row came from.
        If you need per-table comparison, we can extend the class to keep table-level metadata.
        """
        # Convert rows to tuples to be hashable
        tuples1 = [tuple(row) for row in table_rows_pdf1]
        tuples2 = [tuple(row) for row in table_rows_pdf2]

        c1 = Counter(tuples1)
        c2 = Counter(tuples2)

        # Build lists of rows that have mismatched counts
        missing_in_pdf2 = []
        missing_in_pdf1 = []

        # Rows present in pdf1 but missing or fewer in pdf2
        for row, cnt1 in c1.items():
            cnt2 = c2.get(row, 0)
            if cnt1 > cnt2:
                missing_in_pdf2.append((-1, row, cnt1 - cnt2))  # -1 is dummy index (we flattened)
        # Rows present in pdf2 but missing or fewer in pdf1
        for row, cnt2 in c2.items():
            cnt1 = c1.get(row, 0)
            if cnt2 > cnt1:
                missing_in_pdf1.append((-1, row, cnt2 - cnt1))

        equal = (len(missing_in_pdf2) == 0 and len(missing_in_pdf1) == 0)
        return equal, missing_in_pdf2, missing_in_pdf1

    # ---------------------------
    # Public Comparison API
    # ---------------------------
    def compare_pdfs(self,
                     pdf1_path: str,
                     pdf2_path: str,
                     *,
                     text_order_sensitive: bool = True,
                     treat_table_headers: bool = True) -> ComparisonResult:
        """
        Compare two PDFs' text and tables.

        Parameters:
            - pdf1_path, pdf2_path: paths to the PDFs
            - text_order_sensitive: if True, text comparison is order-sensitive (uses unified diff).
                                    If False, lines are sorted before comparison (order-insensitive).
            - treat_table_headers: currently placeholder. If you want header detection and
                                   per-table matching, we can extend it. For now we flatten tables.

        Returns:
            ComparisonResult dataclass with:
                - text_equal: bool
                - text_diffs: unified diff when not equal (empty list if equal)
                - tables_equal: bool
                - tables_missing_in_pdf2: list of rows missing in pdf2 (with counts)
                - tables_missing_in_pdf1: list of rows missing in pdf1 (with counts)
                - details: extra info (line counts, row counts, etc.)
        """
        # ---- Extract & normalize text lines ----
        lines1 = self.extract_text_lines(pdf1_path)
        lines2 = self.extract_text_lines(pdf2_path)

        # ---- Compare text ----
        if text_order_sensitive:
            # compare in reading order
            text_equal = (lines1 == lines2)
            text_diffs = [] if text_equal else self._text_diffs(lines1, lines2)
        else:
            # order-insensitive: compare sorted lists (exact equality of the multiset of lines)
            c1 = Counter(lines1)
            c2 = Counter(lines2)
            text_equal = (c1 == c2)
            if not text_equal:
                # produce a useful diff: lines present more times in one PDF
                # list of (line, count_diff)
                missing_in_pdf2 = []
                missing_in_pdf1 = []
                for ln, cnt1 in c1.items():
                    cnt2 = c2.get(ln, 0)
                    if cnt1 > cnt2:
                        missing_in_pdf2.append((ln, cnt1 - cnt2))
                for ln, cnt2 in c2.items():
                    cnt1 = c1.get(ln, 0)
                    if cnt2 > cnt1:
                        missing_in_pdf1.append((ln, cnt2 - cnt1))
                text_diffs = [
                    f"Lines in pdf1 but under-represented in pdf2: {missing_in_pdf2}",
                    f"Lines in pdf2 but under-represented in pdf1: {missing_in_pdf1}",
                ]
        # ---- Extract & normalize table rows (flattened) ----
        table_rows1 = self.extract_all_table_rows(pdf1_path)
        table_rows2 = self.extract_all_table_rows(pdf2_path)

        # ---- Compare tables order-insensitive using Counter of row-tuples ----
        tables_equal, missing_in_pdf2, missing_in_pdf1 = self._compare_tables_order_insensitive(table_rows1, table_rows2)

        # ---- Build details for debugging ----
        details = {
            "text_line_count_pdf1": len(lines1),
            "text_line_count_pdf2": len(lines2),
            "table_row_count_pdf1": len(table_rows1),
            "table_row_count_pdf2": len(table_rows2),
        }

        return ComparisonResult(
            text_equal=text_equal,
            text_diffs=text_diffs,
            tables_equal=tables_equal,
            tables_missing_in_pdf2=missing_in_pdf2,
            tables_missing_in_pdf1=missing_in_pdf1,
            details=details
        )


# ---------------------------
# Example usage
# ---------------------------
if __name__ == "__main__":
    import argparse
    import json
    parser = argparse.ArgumentParser(description="Compare two PDFs (text + tables)")
    parser.add_argument("pdf1", help="Path to first PDF")
    parser.add_argument("pdf2", help="Path to second PDF")
    parser.add_argument("--text-order-sensitive", dest="tos", action="store_true", help="Make text comparison order-sensitive (default)")
    parser.add_argument("--text-order-insensitive", dest="tos", action="store_false", help="Make text comparison order-insensitive")
    parser.set_defaults(tos=True)
    args = parser.parse_args()

    comparer = PDFComparer()
    result = comparer.compare_pdfs(args.pdf1, args.pdf2, text_order_sensitive=args.tos)

    # Print a readable summary
    print("===== Comparison Summary =====")
    print(f"Text equal: {result.text_equal}")
    if not result.text_equal:
        print("--- Text diffs ---")
        # If diffs are large, printing them all may be noisy; still print for exact comparison
        for line in result.text_diffs:
            print(line)
    print(f"Tables equal: {result.tables_equal}")
    if not result.tables_equal:
        print("--- Table mismatches (rows present more times in pdf1 than pdf2) ---")
        for dummy_idx, row, count_diff in result.tables_missing_in_pdf2:
            print(f"Missing {count_diff}x -> {row}")
        print("--- Table mismatches (rows present more times in pdf2 than pdf1) ---")
        for dummy_idx, row, count_diff in result.tables_missing_in_pdf1:
            print(f"Missing {count_diff}x -> {row}")

    print("Details:", json.dumps(result.details, indent=2))
