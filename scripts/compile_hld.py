import os
import sys
import subprocess
import html
import re

chapters_dir = os.path.abspath(r'docs/interview-preparation/chapters')
output_md = os.path.abspath(r'docs/interview-preparation/Enterprise_AI_Platform_HLD_and_Interview_Guide.md')
output_html = os.path.abspath(r'docs/interview-preparation/Enterprise_AI_Platform_HLD_and_Interview_Guide.html')
output_pdf = os.path.abspath(r'docs/interview-preparation/Enterprise_AI_Platform_HLD_and_Interview_Guide.pdf')
diagrams_dir = os.path.abspath(r'docs/interview-preparation/diagrams')

# 1. Concatenate all markdown chapters
chapter_files = sorted([f for f in os.listdir(chapters_dir) if f.endswith('.md')])
print(f"Concatenating {len(chapter_files)} chapters...")

combined_md_parts = []
for fname in chapter_files:
    fpath = os.path.join(chapters_dir, fname)
    with open(fpath, 'r', encoding='utf-8') as f:
        combined_md_parts.append(f.read().strip())
    combined_md_parts.append("\n\n---\n\n")

master_md = "\n".join(combined_md_parts)
with open(output_md, 'w', encoding='utf-8') as f:
    f.write(master_md)
print(f"Master Markdown generated: {output_md} ({len(master_md)} bytes)")

# 2. Check if markdown package is installed or use custom parser
try:
    import markdown
    has_markdown = True
except ImportError:
    has_markdown = False
    print("markdown package not installed. Installing markdown via pip...")
    subprocess.run([sys.executable, "-m", "pip", "install", "markdown"], check=False)
    try:
        import markdown
        has_markdown = True
    except ImportError:
        has_markdown = False

# Read markdown and render to HTML
if has_markdown:
    html_body = markdown.markdown(master_md, extensions=['tables', 'fenced_code', 'codehilite', 'toc'])
else:
    # Basic fallback converter
    html_body = f"<pre>{html.escape(master_md)}</pre>"

# Load standalone mermaid diagrams
diagram_html_snippets = {}
if os.path.exists(diagrams_dir):
    for df in sorted(os.listdir(diagrams_dir)):
        if df.endswith('.mmd'):
            diag_id = os.path.splitext(df)[0]
            with open(os.path.join(diagrams_dir, df), 'r', encoding='utf-8') as f:
                diag_content = f.read().strip()
            diagram_html_snippets[diag_id] = f"""
            <div class="diagram-container">
                <div class="diagram-title">System Architecture Visual: {diag_id}</div>
                <pre class="mermaid">
{diag_content}
                </pre>
            </div>
            """

# Read local mermaid.min.js
mermaid_js_path = os.path.join(diagrams_dir, 'mermaid.min.js')
mermaid_inline_js = ""
if os.path.exists(mermaid_js_path):
    with open(mermaid_js_path, 'r', encoding='utf-8') as f:
        mermaid_inline_js = f.read()

# Build comprehensive print-ready HTML
html_template = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Enterprise AI Platform - High Level Design & Interview Guide</title>
<style>
    @page {{
        size: A4;
        margin: 20mm 15mm 20mm 15mm;
        @bottom-right {{
            content: counter(page);
        }}
    }}
    
    body {{
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
        font-size: 10.5pt;
        line-height: 1.55;
        color: #1a202c;
        background-color: #ffffff;
        margin: 0;
        padding: 20px 40px;
    }}
    
    h1, h2, h3, h4, h5, h6 {{
        color: #0f172a;
        font-weight: 700;
        page-break-after: avoid;
        margin-top: 1.4em;
        margin-bottom: 0.5em;
    }}
    
    h1 {{
        font-size: 22pt;
        border-bottom: 2.5px solid #2563eb;
        padding-bottom: 6px;
        margin-top: 1.8em;
    }}
    
    h2 {{
        font-size: 15pt;
        border-bottom: 1.5px solid #cbd5e1;
        padding-bottom: 4px;
        margin-top: 1.5em;
    }}
    
    h3 {{
        font-size: 12pt;
        color: #1e3a8a;
    }}
    
    p, li {{
        text-align: justify;
    }}
    
    code {{
        font-family: "Cascadia Code", "Fira Code", Consolas, Courier, monospace;
        font-size: 9pt;
        background-color: #f1f5f9;
        color: #0f172a;
        padding: 2px 5px;
        border-radius: 4px;
        border: 1px solid #e2e8f0;
    }}
    
    pre {{
        background-color: #0f172a;
        color: #f8fafc;
        padding: 12px 16px;
        border-radius: 6px;
        font-family: "Cascadia Code", "Fira Code", Consolas, Courier, monospace;
        font-size: 8.5pt;
        line-height: 1.45;
        overflow-x: auto;
        page-break-inside: avoid;
        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }}
    
    pre code {{
        background-color: transparent;
        color: inherit;
        padding: 0;
        border: none;
    }}
    
    table {{
        width: 100%;
        border-collapse: collapse;
        margin: 14px 0;
        font-size: 9pt;
        page-break-inside: avoid;
    }}
    
    th, td {{
        border: 1px solid #cbd5e1;
        padding: 7px 10px;
        text-align: left;
    }}
    
    th {{
        background-color: #f8fafc;
        color: #0f172a;
        font-weight: 600;
    }}
    
    tr:nth-child(even) {{
        background-color: #f8fafc;
    }}
    
    blockquote {{
        margin: 12px 0;
        padding: 10px 16px;
        background-color: #eff6ff;
        border-left: 4px solid #3b82f6;
        color: #1e40af;
        border-radius: 0 4px 4px 0;
        page-break-inside: avoid;
    }}
    
    .diagram-container {{
        margin: 20px 0;
        padding: 15px;
        background-color: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        page-break-inside: avoid;
        text-align: center;
    }}
    
    .diagram-title {{
        font-weight: bold;
        color: #2563eb;
        margin-bottom: 10px;
        font-size: 11pt;
    }}
    
    .mermaid {{
        font-family: "Cascadia Code", Consolas, monospace !important;
        font-size: 8.5pt;
        background-color: transparent !important;
        color: #1e293b !important;
    }}
    
    .page-break {{
        page-break-before: always;
    }}
    
    @media print {{
        body {{
            padding: 0;
            background-color: transparent;
        }}
        .no-print {{
            display: none;
        }}
    }}
</style>
</head>
<body>

<div class="main-content">
{html_body}
</div>

<div class="page-break"></div>
<h1>System Architecture & Flow Diagrams Appendix</h1>
<p>The following diagrams provide architectural visualizations of all core workflows and topologies implemented in the Enterprise AI Platform.</p>

{''.join(diagram_html_snippets.values())}

<script>
{mermaid_inline_js}
</script>
<script>
if (typeof mermaid !== 'undefined') {{
    mermaid.initialize({{
        startOnLoad: true,
        theme: 'default',
        securityLevel: 'loose',
        flowchart: {{ useMaxWidth: true, htmlLabels: true, curve: 'basis' }}
    }});
}}
</script>
</body>
</html>
"""

with open(output_html, 'w', encoding='utf-8') as f:
    f.write(html_template)
print(f"HTML Template generated: {output_html} ({len(html_template)} bytes)")
