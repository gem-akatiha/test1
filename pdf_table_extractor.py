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

        Note: Because we flattened across all tables, we don't preserve which p
