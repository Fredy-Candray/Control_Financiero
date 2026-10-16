"""Render synthetic report PDFs and check pagination/content without any production data."""
from pathlib import Path
import re
import pypdfium2 as pdfium
import pdfplumber
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[2]
output = root / 'tmp/pdfs'
output.mkdir(parents=True, exist_ok=True)
for source in sorted((root / 'output/pdf').glob('cofi-demo-*.pdf')):
    thumbs = []
    with pdfplumber.open(source) as pdf:
        contents = []
        for index, page in enumerate(pdf.pages):
            assert page.images, (source.name, index, 'missing COFI logo')
            text = page.extract_text() or ''
            assert f'Página {index + 1} de {len(pdf.pages)}' in text, (source.name, index, 'footer')
            assert len(text) > 150, (source.name, index, 'nearly empty page')
            # Text must remain within the page and above the reserved footer.
            for char in page.chars:
                assert 25 <= char['x0'] < char['x1'] <= page.width - 20, (source.name, index, char)
                assert 12 <= char['top'] <= char['bottom'] < page.height - 10, (source.name, index, char)
            contents.append(text)
        combined = '\n'.join(contents)
        expected = {'sin-movimientos': 0, 'un-movimiento': 1, 'categorias': 20, 'varios-meses': 42, 'muchos-movimientos': 350}
        scenario = source.stem.replace('cofi-demo-', '')
        for movement in range(1, expected[scenario] + 1):
            assert f'REF{movement:04}' in combined, (source.name, movement, 'missing movement')
        print(source.name, len(pdf.pages), 'pages; all movements and footers verified')
    doc = pdfium.PdfDocument(source)
    for index in range(len(doc)):
        page = doc[index]
        image = page.render(scale=.65).to_pil().convert('RGB')
        if index == 0 or scenario == 'varios-meses' or (scenario == 'muchos-movimientos' and index in [1, len(doc) - 1]):
            page.render(scale=1.4).to_pil().save(output / f'{scenario}-{index + 1}.png')
        image.thumbnail((235, 335))
        thumb = Image.new('RGB', (255, 365), '#dbe3df')
        thumb.paste(image, ((255 - image.width) // 2, 18))
        ImageDraw.Draw(thumb).text((10, 346), f'{scenario} / {index + 1}', fill='black')
        thumbs.append(thumb)
    columns = 4
    sheet = Image.new('RGB', (columns * 255, ((len(thumbs) + columns - 1) // columns) * 365), 'white')
    for i, image in enumerate(thumbs):
        sheet.paste(image, ((i % columns) * 255, (i // columns) * 365))
    sheet.save(output / f'{scenario}-overview.png')
