from __future__ import annotations

import argparse
import html
import re
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import LETTER
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus import (
    KeepTogether,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


BRAND = colors.HexColor("#17365D")
HEADER_FILL = colors.HexColor("#DCE6F1")
RULE = colors.HexColor("#8EA9C1")
TEXT = colors.HexColor("#1F2933")


def inline_markup(value: str) -> str:
    value = html.escape(value.strip())
    value = re.sub(r"`([^`]+)`", r'<font name="Courier">\1</font>', value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", value)
    return value


def table_from(lines: list[str], available_width: float, body_style: ParagraphStyle) -> Table:
    rows = []
    for line in lines:
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells):
            continue
        rows.append([Paragraph(inline_markup(cell), body_style) for cell in cells])

    column_count = len(rows[0])
    if column_count == 3:
        widths = [available_width * 0.38, available_width * 0.31, available_width * 0.31]
    else:
        widths = [available_width * 0.43, available_width * 0.57]

    table = Table(rows, colWidths=widths, repeatRows=1, hAlign="LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), HEADER_FILL),
        ("TEXTCOLOR", (0, 0), (-1, 0), BRAND),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("GRID", (0, 0), (-1, -1), 0.6, RULE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F7FAFC")]),
    ]))
    return table


def page_frame(canvas, document) -> None:
    canvas.saveState()
    width, height = LETTER
    canvas.setStrokeColor(RULE)
    canvas.setLineWidth(0.6)
    canvas.line(0.7 * inch, height - 0.55 * inch, width - 0.7 * inch, height - 0.55 * inch)
    canvas.setFillColor(BRAND)
    canvas.setFont("Helvetica-Bold", 8)
    canvas.drawString(0.7 * inch, height - 0.42 * inch, "ABC CORP / PEOPLE POLICY")
    canvas.setFillColor(colors.HexColor("#5B6770"))
    canvas.setFont("Helvetica", 8)
    canvas.drawString(0.7 * inch, 0.42 * inch, "ABC Corp | Employee Leave Policy | Internal Demo")
    canvas.drawRightString(width - 0.7 * inch, 0.42 * inch, f"Page {document.page}")
    canvas.restoreState()


def build_pdf(markdown_path: Path, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(
        name="PolicyTitle",
        parent=styles["Title"],
        fontName="Helvetica-Bold",
        fontSize=24,
        leading=28,
        alignment=TA_CENTER,
        textColor=BRAND,
        spaceAfter=5,
    ))
    styles.add(ParagraphStyle(
        name="PolicySubtitle",
        parent=styles["Heading1"],
        fontName="Helvetica-Bold",
        fontSize=17,
        leading=21,
        alignment=TA_CENTER,
        textColor=TEXT,
        spaceBefore=0,
        spaceAfter=8,
    ))
    styles.add(ParagraphStyle(
        name="PolicyVersion",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=9.5,
        leading=12,
        alignment=TA_CENTER,
        textColor=colors.HexColor("#5B6770"),
        spaceAfter=15,
    ))
    styles.add(ParagraphStyle(
        name="PolicyHeading",
        parent=styles["Heading2"],
        fontName="Helvetica-Bold",
        fontSize=13,
        leading=16,
        textColor=BRAND,
        spaceBefore=11,
        spaceAfter=5,
        keepWithNext=True,
    ))
    styles.add(ParagraphStyle(
        name="PolicyBody",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=9.3,
        leading=13,
        textColor=TEXT,
        spaceAfter=6,
    ))
    styles.add(ParagraphStyle(
        name="PolicyTable",
        parent=styles["PolicyBody"],
        fontSize=8.5,
        leading=10.5,
        spaceAfter=0,
    ))
    styles.add(ParagraphStyle(
        name="PolicyBullet",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=9.3,
        leading=12.5,
        textColor=TEXT,
        leftIndent=13,
        firstLineIndent=-8,
        bulletIndent=2,
        spaceAfter=3,
    ))

    document = SimpleDocTemplate(
        str(output_path),
        pagesize=LETTER,
        leftMargin=0.7 * inch,
        rightMargin=0.7 * inch,
        topMargin=0.72 * inch,
        bottomMargin=0.68 * inch,
        title="ABC Corp Employee Leave Policy",
        author="ABC Corp",
        subject="Employee leave rules for the ELMS technical demonstration",
    )

    story = []
    lines = markdown_path.read_text(encoding="utf-8").splitlines()
    index = 0
    heading_count = 0
    while index < len(lines):
        raw = lines[index].strip()
        if not raw:
            index += 1
            continue

        if raw.startswith("| "):
            table_lines = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                table_lines.append(lines[index])
                index += 1
            table = table_from(table_lines, document.width, styles["PolicyTable"])
            story.append(KeepTogether([table]) if len(table_lines) <= 7 else table)
            story.append(Spacer(1, 5))
            continue

        if raw.startswith("# "):
            story.append(Spacer(1, 7))
            story.append(Paragraph(inline_markup(raw[2:]), styles["PolicyTitle"]))
        elif raw.startswith("## "):
            story.append(Paragraph(inline_markup(raw[3:]), styles["PolicySubtitle"]))
        elif raw.startswith("### "):
            heading_count += 1
            heading = Paragraph(inline_markup(raw[4:]), styles["PolicyHeading"])
            story.append(KeepTogether([heading]))
        elif raw.startswith("- "):
            story.append(Paragraph(inline_markup(raw[2:]), styles["PolicyBullet"], bulletText="-"))
        elif raw.startswith("**Version"):
            story.append(Paragraph(inline_markup(raw), styles["PolicyVersion"]))
        else:
            story.append(Paragraph(inline_markup(raw), styles["PolicyBody"]))
        index += 1

    if heading_count != 12:
        raise ValueError(f"Expected 12 policy sections, found {heading_count}")

    document.build(story, onFirstPage=page_frame, onLaterPages=page_frame)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("markdown", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build_pdf(args.markdown, args.output)


if __name__ == "__main__":
    main()
