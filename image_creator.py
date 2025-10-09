#!/usr/bin/env python3
"""
pdf_compare_report.py

Compare two PDFs (text, tables, images) using pdfplumber as primary extractor.
Outputs an HTML report into the specified output directory.

Usage:
    python pdf_compare_report.py src.pdf trg.pdf ./out_report
"""

import sys
import os
import shutil
import tempfile
from pathlib import Path
import base64
import json
import io

import pdfplumber
from pdf2image import convert_from_path
import pytesseract
from PIL import Image, ImageChops, ImageOps, ImageDraw
import imagehash
import cv2
import numpy as np
import pandas as pd
import difflib
from jinja2 import Template

# ---------------------------
# Utilities
# ---------------------------
def ensure_dir(p):
    Path(p).mkdir(parents=True, exist_ok=True)

def save_pil(img: Image.Image, path: Path):
    img.save(path, format="PNG")
    return str(path)

def pil_to_base64(img: Image.Image):
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode("ascii")

# ---------------------------
# Extractors (pdfplumber primary)
# ---------------------------
def extract_text_by_pages_pdfplumber(pdf_path: str):
    pages_text = []
    with pdfplumber.open(pdf_path) as pdf:
        for page in pdf.pages:
            text = page.extract_text() or ""
            pages_text.append([line for line in text.splitlines()])
    return pages_text

def extract_tables_pdfplumber(pdf_path: str):
    dfs = []
    with pdfplumber.open(pdf_path) as pdf:
        for pnum, page in enumerate(pdf.pages, start=1):
            try:
                tables = page.extract_tables()
            except Exception:
                tables = []
            for t in tables:
                if not t: 
                    continue
                # first row as header if looks like header
                header = t[0]
                rows = t[1:] if len(t) > 1 else []
                df = pd.DataFrame(rows, columns=header)
                dfs.append({'page': pnum, 'df': df})
    return dfs

# ---------------------------
# Image extraction & OCR
# ---------------------------
def render_pdf_pages_to_images(pdf_path: str, dpi=200):
    # returns list of PIL images (one per page)
    images = convert_from_path(pdf_path, dpi=dpi)
    return images

def extract_embedded_images_using_pdfplumber(pdf_path: str):
    # return list of dicts: {page, bbox, image_pil}
    items = []
    with pdfplumber.open(pdf_path) as pdf:
        for pnum, page in enumerate(pdf.pages, start=1):
            for im in page.images:
                # pdfplumber.image object gives x0,y0,x1,y1; try extract via crop of rendered page if needed
                bbox = (im.get("x0"), im.get("top") or im.get("y0"), im.get("x1"), im.get("bottom") or im.get("y1"))
                items.append({'page': pnum, 'bbox': bbox, 'obj': im})
    return items

def ocr_image(pil_img: Image.Image, lang='eng', config='--psm 3'):
    text = pytesseract.image_to_string(pil_img, lang=lang, config=config)
    return text

# ---------------------------
# Image comparison helpers
# ---------------------------
def phash_distance(img1: Image.Image, img2: Image.Image):
    return imagehash.phash(img1) - imagehash.phash(img2)

def pixel_diff_image(img1: Image.Image, img2: Image.Image, threshold=30):
    # convert to same size
    if img1.size != img2.size:
        img2 = img2.resize(img1.size)
    a = np.array(img1.convert('RGB'))
    b = np.array(img2.convert('RGB'))
    diff = cv2.absdiff(a, b)
    gray = cv2.cvtColor(diff, cv2.COLOR_BGR2GRAY)
    _, th = cv2.threshold(gray, threshold, 255, cv2.THRESH_BINARY)
    # create visual overlay: highlight diffs in red on top of img2
    overlay = img2.convert("RGBA")
    mask = Image.fromarray(th).convert("L")
    red = Image.new("RGBA", overlay.size, (255, 0, 0, 120))
    overlay.paste(red, (0,0), mask)
    return Image.fromarray(diff), overlay, mask

# ---------------------------
# Text compare (page-line)
# ---------------------------
def compare_text_pages(src_pages, trg_pages):
    diffs = []
    max_pages = max(len(src_pages), len(trg_pages))
    for p in range(max_pages):
        src_lines = src_pages[p] if p < len(src_pages) else []
        trg_lines = trg_pages[p] if p < len(trg_pages) else []
        sm = difflib.SequenceMatcher(a=src_lines, b=trg_lines)
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag == 'equal':
                continue
            diffs.append({
                'page': p+1,
                'op': tag,  # replace, delete, insert
                'src_range': (i1, i2),
                'trg_range': (j1, j2),
                'src_lines': src_lines[i1:i2],
                'trg_lines': trg_lines[j1:j2]
            })
    return diffs

# ---------------------------
# Tables compare (basic)
# ---------------------------
def compare_tables_list(src_tables, trg_tables):
    # simple compare by page order; more advanced matching can be added
    diffs = []
    max_len = max(len(src_tables), len(trg_tables))
    for i in range(max_len):
        s = src_tables[i] if i < len(src_tables) else None
        t = trg_tables[i] if i < len(trg_tables) else None
        page = s['page'] if s else (t['page'] if t else None)
        if s is None:
            diffs.append({'page': page, 'type': 'table_added', 'details': f'table added on page {page}'})
            continue
        if t is None:
            diffs.append({'page': page, 'type': 'table_deleted', 'details': f'table deleted on page {page}'})
            continue
        df1 = s['df'].fillna('').astype(str)
        df2 = t['df'].fillna('').astype(str)
        if df1.equals(df2):
            continue
        # find cell diffs
        dif_cells = []
        rows = max(df1.shape[0], df2.shape[0])
        cols = max(df1.shape[1], df2.shape[1])
        for r in range(rows):
            for c in range(cols):
                a = df1.iloc[r, c] if (r < df1.shape[0] and c < df1.shape[1]) else ''
                b = df2.iloc[r, c] if (r < df2.shape[0] and c < df2.shape[1]) else ''
                if a != b:
                    dif_cells.append({'row': r, 'col': c, 'a': a, 'b': b})
        diffs.append({'page': page, 'type': 'table_modified', 'cells': dif_cells})
    return diffs

# ---------------------------
# Main pipeline
# ---------------------------
def compare_pdfs(src_pdf, trg_pdf, out_dir):
    ensure_dir(out_dir)
    assets_dir = Path(out_dir) / "assets"
    ensure_dir(assets_dir)

    # 1. Text & table extraction (pdfplumber)
    src_text = extract_text_by_pages_pdfplumber(src_pdf)
    trg_text = extract_text_by_pages_pdfplumber(trg_pdf)
    text_diffs = compare_text_pages(src_text, trg_text)

    src_tables = extract_tables_pdfplumber(src_pdf)
    trg_tables = extract_tables_pdfplumber(trg_pdf)
    table_diffs = compare_tables_list(src_tables, trg_tables)

    # 2. Page images + OCR
    src_page_imgs = render_pdf_pages_to_images(src_pdf, dpi=200)
    trg_page_imgs = render_pdf_pages_to_images(trg_pdf, dpi=200)

    image_diffs = []
    max_pages = max(len(src_page_imgs), len(trg_page_imgs))
    for p in range(max_pages):
        s_img = src_page_imgs[p] if p < len(src_page_imgs) else None
        t_img = trg_page_imgs[p] if p < len(trg_page_imgs) else None
        if s_img is None:
            image_diffs.append({'page': p+1, 'type': 'page_inserted'})
            continue
        if t_img is None:
            image_diffs.append({'page': p+1, 'type': 'page_deleted'})
            continue
        # compute phash distance
        ph = phash_distance(s_img, t_img)
        diff_img, overlay, mask = pixel_diff_image(s_img, t_img, threshold=30)
        nonzero_pixels = np.array(mask).astype(bool).sum()
        # OCR both pages
        ocr_s = ocr_image(s_img)
        ocr_t = ocr_image(t_img)
        # save visuals
        s_path = Path(assets_dir) / f"p{p+1}_src.png"
        t_path = Path(assets_dir) / f"p{p+1}_trg.png"
        ovr_path = Path(assets_dir) / f"p{p+1}_overlay.png"
        diff_path = Path(assets_dir) / f"p{p+1}_diff.png"
        save_pil(s_img, s_path)
        save_pil(t_img, t_path)
        save_pil(overlay, ovr_path)
        save_pil(diff_img, diff_path)
        image_diffs.append({
            'page': p+1,
            'phash_distance': int(ph),
            'changed_pixels': int(nonzero_pixels),
            'src_img': str(s_path.name),
            'trg_img': str(t_path.name),
            'overlay_img': str(ovr_path.name),
            'diff_img': str(diff_path.name),
            'ocr_src': ocr_s,
            'ocr_trg': ocr_t
        })

    # 3. create report data
    report = {
        'summary': {
            'pages_src': len(src_page_imgs),
            'pages_trg': len(trg_page_imgs),
            'text_diffs': len(text_diffs),
            'table_diffs': len(table_diffs),
            'image_pages_compared': len(image_diffs)
        },
        'text_diffs': text_diffs,
        'table_diffs': table_diffs,
        'image_diffs': image_diffs
    }

    # 4. Render HTML using jinja2 template (inline template here)
    template_html = """
    <!doctype html>
    <html>
    <head>
      <meta charset="utf-8"/>
      <title>PDF Compare Report</title>
      <style>
        body { font-family: Arial, sans-serif; margin: 24px; }
        h1,h2 { color: #003366; }
        .summary { border: 1px solid #ddd; padding: 12px; background:#f7f9fb }
        .diff-block { border: 1px solid #eee; padding: 12px; margin: 12px 0; }
        pre { background:#fff; padding: 8px; overflow:auto }
        .images { display:flex; gap: 8px; flex-wrap:wrap; }
        .image-item { width:300px; border:1px solid #ddd; padding:8px; background:#fff }
        table { border-collapse: collapse; }
        td, th { border: 1px solid #ddd; padding: 6px; }
      </style>
    </head>
    <body>
      <h1>PDF Compare Report</h1>
      <div class="summary">
        <b>Source pages:</b> {{summary.pages_src}} &nbsp;
        <b>Target pages:</b> {{summary.pages_trg}} &nbsp;
        <b>Text diffs:</b> {{summary.text_diffs}} &nbsp;
        <b>Table diffs:</b> {{summary.table_diffs}} &nbsp;
        <b>Image pages:</b> {{summary.image_pages_compared}}
      </div>

      <h2>Text differences (page / op)</h2>
      {% if text_diffs %}
        {% for d in text_diffs %}
          <div class="diff-block">
            <b>Page {{d.page}} - {{d.op}}</b><br/>
            <table>
              <tr><th>Src lines ({{d.src_range[0]}}:{{d.src_range[1]}})</th><th>Trg lines ({{d.trg_range[0]}}:{{d.trg_range[1]}})</th></tr>
              <tr>
                <td><pre>{{ d.src_lines | join('\\n') }}</pre></td>
                <td><pre>{{ d.trg_lines | join('\\n') }}</pre></td>
              </tr>
            </table>
          </div>
        {% endfor %}
      {% else %}
        <p>No text differences found.</p>
      {% endif %}

      <h2>Table differences</h2>
      {% if table_diffs %}
        {% for t in table_diffs %}
          <div class="diff-block">
            <b>Page {{t.page}} - {{t.type}}</b><br/>
            {% if t.type == 'table_modified' %}
              <b>Cells changed:</b> {{t.cells | length}}<br/>
              <table>
                <tr><th>row</th><th>col</th><th>src</th><th>trg</th></tr>
                {% for c in t.cells %}
                  <tr><td>{{c.row}}</td><td>{{c.col}}</td><td>{{c.a}}</td><td>{{c.b}}</td></tr>
                {% endfor %}
              </table>
            {% else %}
              <pre>{{ t.details }}</pre>
            {% endif %}
          </div>
        {% endfor %}
      {% else %}
        <p>No table differences found.</p>
      {% endif %}

      <h2>Image / page visual differences</h2>
      {% for im in image_diffs %}
        <div class="diff-block">
          <b>Page {{im.page}}</b>
          <div style="display:flex;gap:12px;align-items:flex-start;margin-top:8px;">
            <div class="image-item">
              <div><b>Source</b></div>
              <img src="./assets/{{im.src_img}}" style="width:100%;"/>
            </div>
            <div class="image-item">
              <div><b>Target</b></div>
              <img src="./assets/{{im.trg_img}}" style="width:100%;"/>
            </div>
            <div class="image-item">
              <div><b>Visual overlay (differences highlighted)</b></div>
              <img src="./assets/{{im.overlay_img}}" style="width:100%;"/>
            </div>
            <div class="image-item">
              <div><b>Pixel diff</b></div>
              <img src="./assets/{{im.diff_img}}" style="width:100%;"/>
            </div>
          </div>
          <div style="margin-top:8px;">
            <b>phash distance:</b> {{im.phash_distance}} &nbsp; <b>changed pixels:</b> {{im.changed_pixels}}
          </div>
          <details style="margin-top:8px;">
            <summary>OCR text from page images</summary>
            <h4>Source OCR</h4><pre>{{im.ocr_src}}</pre>
            <h4>Target OCR</h4><pre>{{im.ocr_trg}}</pre>
          </details>
        </div>
      {% endfor %}

    </body>
    </html>
    """

    html = Template(template_html).render(**{
        'summary': report['summary'],
        'text_diffs': report['text_diffs'],
        'table_diffs': report['table_diffs'],
        'image_diffs': report['image_diffs']
    })
    report_path = Path(out_dir) / "report.html"
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(html)

    print(f"Report written to: {report_path.resolve()}")
    return report_path

# ---------------------------
# CLI
# ---------------------------
def main():
    if len(sys.argv) < 4:
        print("Usage: python pdf_compare_report.py src.pdf trg.pdf out_dir")
        sys.exit(1)
    src, trg, out = sys.argv[1], sys.argv[2], sys.argv[3]
    if not Path(src).exists() or not Path(trg).exists():
        print("Source/target PDF not found")
        sys.exit(1)
    ensure_dir(out)
    # copy both PDFs into out/assets for reference
    shutil.copy(src, Path(out)/"src.pdf")
    shutil.copy(trg, Path(out)/"trg.pdf")
    compare_pdfs(src, trg, out)

if __name__ == "__main__":
    main()
