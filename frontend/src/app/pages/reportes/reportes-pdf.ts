import { jsPDF } from 'jspdf';
import { autoTable } from 'jspdf-autotable';

export interface SeccionPdf { titulo: string; columnas: string[]; filas: string[][]; nota?: string; }
export function crearReportePdf(titulo: string, filtros: string[], secciones: SeccionPdf[]): jsPDF {
  const doc = new jsPDF({ orientation: 'landscape', format: 'a4' });
  doc.setProperties({ title: titulo, author: 'Control Financiero' });
  doc.setFont('helvetica', 'bold'); doc.setFontSize(20); doc.setTextColor(18, 64, 47);
  doc.text('Control Financiero', 14, 18);
  doc.setFontSize(13); doc.text(titulo, 14, 27);
  doc.setFont('helvetica', 'normal'); doc.setFontSize(9); doc.setTextColor(70);
  let y = 35;
  for (const filtro of filtros) {
    const lineas = doc.splitTextToSize(filtro, 269);
    doc.text(lineas, 14, y); y += lineas.length * 4.5;
  }
  y += 7;
  for (const seccion of secciones) {
    const nota = seccion.nota ? doc.splitTextToSize(seccion.nota, 269) : [];
    if (y + 25 + nota.length * 4 > 190) { doc.addPage(); y = 18; }
    doc.setFont('helvetica', 'bold'); doc.setFontSize(11); doc.setTextColor(18, 64, 47);
    doc.text(seccion.titulo, 14, y); y += 6;
    if (nota.length) { doc.setFont('helvetica', 'normal'); doc.setFontSize(8); doc.setTextColor(80); doc.text(nota, 14, y); y += nota.length * 4 + 3; }
    let finalY = y;
    autoTable(doc, {
      startY: y, margin: { top: 16, bottom: 16, left: 14, right: 14 },
      head: [seccion.columnas], body: seccion.filas.length ? seccion.filas : [seccion.columnas.map((_, i) => i === 0 ? 'Sin registros para los filtros seleccionados' : '')],
      theme: 'striped', styles: { font: 'helvetica', fontSize: 8, cellPadding: 3, overflow: 'linebreak' },
      headStyles: { fillColor: [15, 125, 87] }, rowPageBreak: 'avoid',
      didDrawPage: datos => { finalY = datos.cursor?.y ?? y; }
    });
    y = finalY + 12;
  }
  const total = doc.getNumberOfPages();
  for (let pagina = 1; pagina <= total; pagina++) {
    doc.setPage(pagina); doc.setFont('helvetica', 'normal'); doc.setFontSize(8); doc.setTextColor(100);
    doc.text(`Control Financiero · Página ${pagina} de ${total}`, 14, 202);
  }
  return doc;
}
