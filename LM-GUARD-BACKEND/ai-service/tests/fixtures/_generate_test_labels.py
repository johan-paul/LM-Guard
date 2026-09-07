"""One-off generator for two additional real-image test cases used in the Gemini E2E
validation (LM-GUARD real-AI audit). Not part of the test suite itself -- run manually.

Produces:
  missing-consumer-care.jpg  -- identical label to api-tests/fixtures/sample-package.jpg
                                minus the consumer-care line, for a genuine NON_COMPLIANT test.
  blurry-label.jpg           -- the same full label, rendered small and then heavily blurred,
                                for a genuine INCONCLUSIVE (unreadable) test.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT_PATH = r"C:\Windows\Fonts\arialbd.ttf"
LINES_FULL = [
    "ABC Foods Pvt Ltd, Pune",
    "Classic Salted Chips",
    "MRP Rs. 149.00",
    "Net Qty 200 g",
    "MFD 03/2026",
    "care@abcfoods.example",
]
LINES_NO_CARE = LINES_FULL[:-1]


def render(lines, size=(1100, 450), font_size=32):
    img = Image.new("RGB", size, color=(255, 255, 255))
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT_PATH, font_size)
    y = 40
    for line in lines:
        draw.text((30, y), line, fill=(0, 0, 0), font=font)
        y += 66
    return img


if __name__ == "__main__":
    render(LINES_NO_CARE).save("missing-consumer-care.jpg", quality=90)

    full = render(LINES_FULL)
    small = full.resize((220, 90))
    blurry = small.resize((1100, 450)).filter(ImageFilter.GaussianBlur(radius=6))
    blurry.save("blurry-label.jpg", quality=40)

    print("generated missing-consumer-care.jpg and blurry-label.jpg")
