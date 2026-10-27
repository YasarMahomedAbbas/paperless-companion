"""Prints a Kover XML report as a Markdown table for the GitHub job summary."""
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
print("### Coverage\n\n| Counter | Covered |\n|---|---|")
for counter in root.findall("counter"):
    missed, covered = int(counter.get("missed")), int(counter.get("covered"))
    total = missed + covered
    print(f"| {counter.get('type').title()} | {covered / total:.1%} ({covered}/{total}) |")
