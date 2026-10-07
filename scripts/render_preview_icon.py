"""Export the original icon draft at 24 and 48 px using Pillow.

Only this project's polygon/polyline SVG elements are supported, not arbitrary
SVG. The mod build does not need Python: game textures are pre-exported PNGs.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw


def export(source: Path, size: int) -> Path:
    root = ET.parse(source).getroot()
    if root.attrib.get("viewBox") != "0 0 32 32":
        raise ValueError("Expected the icon's 32 x 32 viewBox")
    supersample = 8
    ratio = size * supersample / 32
    image = Image.new("RGBA", (size * supersample, size * supersample))
    draw = ImageDraw.Draw(image)
    for element in root:
        tag = element.tag.rsplit("}", 1)[-1]
        if tag == "title":
            continue
        if tag not in ("polygon", "polyline"):
            raise ValueError(f"Unsupported SVG element: {tag}")
        points = [
            tuple(float(value) * ratio for value in pair.split(","))
            for pair in element.attrib["points"].split()
        ]
        fill = element.attrib.get("fill", "none")
        if fill != "none":
            if tag != "polygon":
                raise ValueError("Only closed polygons may have a fill")
            draw.polygon(points, fill=fill)
        stroke = element.attrib["stroke"]
        width = float(element.attrib["stroke-width"]) * ratio
        line = points + points[:1] if tag == "polygon" else points
        draw.line(line, fill=stroke, width=round(width), joint="curve")
        # This asset explicitly uses rounded joins and line caps.
        radius = width / 2
        for x, y in points:
            draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=stroke)
    result = source.with_name(f"{source.stem}-{size}.png")
    image.resize((size, size), Image.Resampling.LANCZOS).save(result)
    return result


if __name__ == "__main__":
    icon = Path(__file__).resolve().parents[1] / "docs/ui/assets/block-preview.svg"
    for pixels in (24, 48):
        print(export(icon, pixels))
