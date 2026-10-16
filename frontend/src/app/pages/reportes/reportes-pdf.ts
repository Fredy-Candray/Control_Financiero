import { jsPDF } from 'jspdf';
import { autoTable } from 'jspdf-autotable';
import { ContextoInforme, dineroPdf, fechaPdf, prepararAnalisisPdf } from './reportes-pdf-datos';

export interface SeccionPdf { titulo: string; columnas: string[]; filas: string[][]; nota?: string; }
type Color = [number, number, number];
const verde: Color = [15, 66, 52], esmeralda: Color = [16, 151, 112], gris: Color = [101, 116, 110];
const rojo: Color = [166, 62, 68], suave: Color = [244, 248, 246];
const paleta: Color[] = [esmeralda, verde, [88, 138, 119], [125, 165, 146], [167, 192, 180], [76, 102, 94]];

export async function cargarLogoReportePdf(): Promise<string | undefined> {
  try {
    const response = await fetch(new URL('assets/cofi-logo.png', document.baseURI));
    if (!response.ok) return undefined;
    const blob = await response.blob();
    return await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(blob);
    });
  } catch { return undefined; } // An unavailable brand asset must not block financial exports.
}

export function crearReportePdf(titulo: string, filtros: string[], secciones: SeccionPdf[], contexto?: ContextoInforme, logo?: string): jsPDF {
  const doc = new jsPDF({ orientation: 'portrait', format: 'letter', compress: true });
  doc.setProperties({ title: titulo, author: 'COFI', subject: 'Informe financiero personal en USD' });
  const margen = 16, ancho = doc.internal.pageSize.getWidth() - margen * 2;
  const derecha = margen + ancho, altoPagina = doc.internal.pageSize.getHeight(), limite = altoPagina - 20;
  let y = 18;
  const texto = (contenido: string, x: number, top: number, size = 9, color: Color = gris, bold = false) => {
    doc.setFont('helvetica', bold ? 'bold' : 'normal'); doc.setFontSize(size); doc.setTextColor(...color);
    doc.text(contenido, x, top);
  };
  const nueva = () => { doc.addPage(); y = 25; };
  const espacio = (alto: number) => { if (y + alto > limite) nueva(); };
  const parrafo = (contenido: string, size = 9, color: Color = gris) => {
    doc.setFont('helvetica', 'normal'); doc.setFontSize(size);
    const lineas: string[] = doc.splitTextToSize(contenido, ancho);
    for (const linea of lineas) { espacio(6); texto(linea, margen, y, size, color); y += 4.7; }
    y += 3;
  };
  const tituloSeccion = (contenido: string, reserva = 24) => {
    espacio(reserva + 12); y += 3;
    doc.setDrawColor(220, 232, 225); doc.line(margen, y, derecha, y); y += 8;
    texto(contenido, margen, y, 12, verde, true); y += 8;
  };
  const tabla = (seccion: SeccionPdf) => {
    tituloSeccion(seccion.titulo, 24 + (seccion.nota ? 18 : 0));
    if (seccion.nota) parrafo(seccion.nota, 8);
    if (!seccion.filas.length) { parrafo('Sin registros para los filtros seleccionados.'); return; }
    const detalle = seccion.titulo === 'Detalle completo de movimientos';
    const columns = seccion.columnas.length;
    const columnStyles: Record<number, { halign?: 'right'; cellWidth?: number }> = {};
    seccion.columnas.forEach((nombre, index) => {
      if (/Monto|Ingresos|Gastos|Balance|Deuda|Patrimonio|Disponible|Límite|Planificado|Restante|Salidas|Entradas|Flujo|Porcentaje|Progreso|Utilización|Acumulado|Objetivo|Pagos|Aporte|Variación|Interés|Tasa/i.test(nombre)) columnStyles[index] = { halign: 'right' };
    });
    if (detalle) [23, 22, 43, 22, 23, 23, 22].forEach((cellWidth, index) => columnStyles[index] = { ...columnStyles[index], cellWidth: cellWidth * ancho / 178 });
    autoTable(doc, {
      startY: y, margin: { top: 25, bottom: 20, left: margen, right: margen },
      head: [seccion.columnas], body: seccion.filas,
      foot: contexto && /^(Gastos por categoría|Ingresos por categoría \/ fuente)$/.test(seccion.titulo)
        ? [['Total del período', dineroPdf(seccion.titulo.startsWith('Gastos') ? contexto.gastos : contexto.ingresos), '100.0 %']] : undefined,
      footStyles: { fillColor: suave, textColor: verde, fontStyle: 'bold' }, showFoot: 'lastPage',
      theme: 'plain', showHead: 'everyPage', rowPageBreak: 'avoid',
      styles: { font: 'helvetica', fontSize: columns > 6 ? 6.8 : 8, cellPadding: 2.6, overflow: 'linebreak', textColor: [43, 62, 53], lineColor: [226, 234, 229], lineWidth: 0.15 },
      headStyles: { fillColor: verde, textColor: [255, 255, 255], fontStyle: 'bold', lineWidth: 0 },
      alternateRowStyles: { fillColor: suave }, columnStyles,
      didDrawPage: data => { y = data.cursor?.y ?? y; }
    });
    y += 9;
  };
  const barras = (etiqueta: string, items: { nombre: string; monto: number }[]) => {
    if (!items.length || items.every(i => i.monto === 0)) { parrafo(`No hay datos para ${etiqueta.toLowerCase()}.`); return; }
    const max = Math.max(...items.map(i => Math.abs(i.monto)), 1);
    items.forEach((item, index) => {
      espacio(18);
      const lineas: string[] = doc.splitTextToSize(item.nombre, 125);
      // Long account names flow instead of being clipped.
      for (const linea of lineas) { espacio(6); texto(linea, margen, y, 8, verde); y += 4; }
      doc.setFontSize(8); doc.setTextColor(...(item.monto < 0 ? rojo : verde));
      doc.text(dineroPdf(item.monto), derecha, y - 4, { align: 'right' });
      doc.setFillColor(...suave); doc.roundedRect(margen, y, ancho, 3.5, 1, 1, 'F');
      if (item.monto) { doc.setFillColor(...(item.monto < 0 ? rojo : paleta[index % paleta.length])); doc.roundedRect(margen, y, Math.max(1, Math.abs(item.monto) / max * ancho), 3.5, 1, 1, 'F'); }
      y += 7;
    });
  };
  if (logo) doc.addImage(logo, 'PNG', margen, y - 10, 18, 18, 'cofi-logo', 'FAST');
  const marcaX = margen + (logo ? 23 : 0);
  texto('COFI', marcaX, y, 24, verde, true);
  texto('CONTROL FINANCIERO', marcaX, y + 6, 8, esmeralda); y += 19;
  const lineasTitulo: string[] = doc.splitTextToSize(titulo, ancho);
  for (const linea of lineasTitulo) { texto(linea, margen, y, 18, verde, true); y += 8; }
  if (contexto) {
    texto(`Período: ${fechaPdf(contexto.desde)} al ${fechaPdf(contexto.hasta)} | Moneda: USD`, margen, y, 9, verde); y += 6;
    texto(`Generado: ${contexto.generado.toLocaleString('es-SV', { timeZone: 'America/El_Salvador', hour12: false })} (El Salvador)`, margen, y, 8); y += 7;
  }
  for (const filtro of filtros) parrafo(filtro, 8);
  if (contexto) {
    const a = prepararAnalisisPdf(contexto);
    const indicadores = [
      ['Patrimonio actual', contexto.patrimonio, 'Dinero disponible menos deuda'],
      ['Gasto total del período', contexto.gastos, 'Efectivo, débito y crédito'],
      ['Gasto en efectivo', a.gastoEfectivo, 'Incluido en el gasto total'],
      ['Ingresos del período', contexto.ingresos, 'Entradas registradas como ingreso'],
      ['Deuda de tarjetas', contexto.deuda, 'Deuda actual de tarjetas de crédito'],
      ['Dinero disponible', contexto.dineroDisponible, 'Saldos de efectivo, PAY y débito']
    ] as [string, number, string][];
    espacio(80);
    indicadores.forEach(([label, value, hint], i) => {
      const fila = Math.floor(i / 3), col = i % 3;
      const columnas = 3;
      const width = (ancho - (columnas - 1) * 4) / columnas, x = margen + col * (width + 4);
      const top = y + fila * 36;
      doc.setFillColor(...suave); doc.roundedRect(x, top, width, 32, 2, 2, 'F');
      texto(label, x + 4, top + 7, 8, gris);
      const amount = dineroPdf(value);
      doc.setFont('helvetica', 'bold'); doc.setFontSize(15);
      const size = Math.min(15, 15 * (width - 8) / Math.max(doc.getTextWidth(amount), 1));
      const esGastoODeuda = label.startsWith('Gasto') || label === 'Deuda de tarjetas';
      texto(amount, x + 4, top + 18, size, value < 0 || (esGastoODeuda && value > 0) ? rojo : label === 'Ingresos del período' ? esmeralda : verde, true);
      doc.setFont('helvetica', 'normal'); doc.setFontSize(6.8);
      const hints: string[] = doc.splitTextToSize(hint, width - 8);
      hints.forEach((line, n) => texto(line, x + 4, top + 25 + n * 3, 6.8));
    });
    y += 74;
    texto('Resumen del período', margen, y, 10, verde, true); y += 6; parrafo(a.resumen);
    parrafo(`El gasto total incluye las compras con cualquier medio de pago; ${dineroPdf(a.gastoEfectivo)} corresponden a efectivo y ya están incluidos, no se suman otra vez. Ingresos menos gastos indica el resultado del período; los pagos de tarjeta y las transferencias internas no son gastos adicionales.`, 8);
    parrafo('Los indicadores del período respetan los filtros. Dinero disponible, patrimonio y deuda son valores actuales de las cuentas seleccionadas; no son saldos históricos ni se filtran por tipo o categoría. El dinero disponible no incluye el crédito de las tarjetas ni descuenta su deuda.', 8);

    const analisis = ['resumen', 'ingresos', 'gastos', 'flujo', 'comparativos'].includes(contexto.vista);
    if (analisis && a.cantidad > 0) {
      tituloSeccion('Análisis de ingresos y gastos', 32);
      parrafo(`${a.cantidad} movimientos | ${a.cantidadGastos} gastos | Gasto promedio: ${dineroPdf(a.promedio)}`, 9, verde);
      barras('Ingresos y gastos', [{ nombre: 'Ingresos', monto: contexto.ingresos }, { nombre: 'Gastos', monto: contexto.gastos }]);
      if (a.categorias.length && contexto.gastos > 0) {
        const categorias = a.categorias.slice(0, 5).map(c => ({ nombre: c.nombre, monto: c.monto }));
        if (a.categorias.length > 5) categorias.push({ nombre: 'Otras categorías', monto: a.categorias.slice(5).reduce((s, c) => s + Math.round(c.monto * 100), 0) / 100 });
        doc.setFont('helvetica', 'normal'); doc.setFontSize(8);
        const leyendaAlto = categorias.reduce((alto, c) => alto + doc.splitTextToSize(c.nombre, 102).length * 4 + 7, 6);
        tituloSeccion('Distribución de gastos por categoría', Math.max(54, leyendaAlto));
        const top = y, cx = 43, cy = y + 23, radio = 22;
        let angulo = -Math.PI / 2;
        categorias.forEach((c, i) => {
          const fin = angulo + c.monto / contexto.gastos * Math.PI * 2;
          doc.setFillColor(...paleta[i]);
          const pasos = Math.max(2, Math.ceil((fin - angulo) * 22));
          for (let p = 0; p < pasos; p++) {
            const t1 = angulo + (fin - angulo) * p / pasos, t2 = angulo + (fin - angulo) * (p + 1) / pasos;
            doc.triangle(cx, cy, cx + Math.cos(t1) * radio, cy + Math.sin(t1) * radio, cx + Math.cos(t2) * radio, cy + Math.sin(t2) * radio, 'F');
          }
          angulo = fin;
        });
        doc.setFillColor(255, 255, 255); doc.circle(cx, cy, 13, 'F');
        texto('GASTOS', cx - 8, cy + 1, 7, verde, true);
        let ly = top + 2;
        categorias.forEach((c, i) => {
          doc.setFont('helvetica', 'normal'); doc.setFontSize(8);
          const lines: string[] = doc.splitTextToSize(c.nombre, 102);
          doc.setFillColor(...paleta[i]); doc.roundedRect(76, ly - 2, 2.5, 2.5, .5, .5, 'F');
          for (const line of lines) { texto(line, 82, ly, 8, verde); ly += 4; }
          texto(`${dineroPdf(c.monto)} | ${(c.monto / contexto.gastos * 100).toFixed(1)} %`, 82, ly, 8); ly += 7;
        });
        y = Math.max(top + 52, ly + 4);
      } else parrafo('No hay gastos para mostrar una distribución por categoría.');
      tituloSeccion('Gastos por cuenta o medio de pago'); barras('gastos por cuenta', a.medios);
      if (a.mayores.length) tabla({ titulo: 'Movimientos de mayor importe', columnas: ['Fecha', 'Tipo', 'Descripción', 'Monto'], filas: a.mayores.map(m => [fechaPdf(m.fechaMovimiento || ''), m.tipo.replace('_', ' '), m.descripcion || 'Sin descripción', dineroPdf(Number(m.monto))]), nota: 'Incluye todos los tipos seleccionados. Una transferencia o un pago de tarjeta no constituye un gasto adicional.' });
    } else if (analisis) parrafo('Sin movimientos: no se generan gráficos ni promedios para este período.', 8);
    if (a.mostrarEvolucion && ['resumen', 'ingresos', 'gastos', 'comparativos', 'flujo', 'metas', 'patrimonio', 'deudas'].includes(contexto.vista)) {
      tituloSeccion('Evolución financiera');
      parrafo('Ingresos y gastos según movimientos registrados. El primer y último mes pueden ser parciales. Patrimonio y deuda se reconstruyen desde saldos actuales e historial disponible; no sustituyen estados bancarios.', 8);
      // Blocks of six months preserve readable labels even for long periods.
      for (let offset = 0; offset < contexto.meses.length; offset += 6) {
        const meses = contexto.meses.slice(offset, offset + 6); espacio(72);
        const top = y, base = top + 40, max = Math.max(1, ...meses.flatMap(m => [m.ingresos, m.gastos]));
        texto('Ingresos (verde) / gastos (gris oscuro)', margen, top, 8, verde); const step = ancho / meses.length;
        meses.forEach((m, i) => {
          const x = margen + i * step + step / 2;
          [[m.ingresos, esmeralda, -7], [m.gastos, gris, 1]].forEach(([value, color, dx]) => {
            const h = Number(value) / max * 27; doc.setFillColor(...color as Color);
            if (h) doc.rect(x + Number(dx), base - h, 6, h, 'F');
          });
          texto(m.mes, x - 8, base + 5, 7, verde);
          doc.setFontSize(6.5); doc.setTextColor(...verde);
          doc.text(`I: ${dineroPdf(m.ingresos)}`, x, base + 10, { align: 'center' });
          doc.text(`G: ${dineroPdf(m.gastos)}`, x, base + 14, { align: 'center' });
        }); y += 64;
      }
      const serie = (label: string, campo: 'balance' | 'patrimonio' | 'deuda') => {
        for (let offset = 0; offset < contexto.meses.length; offset += 6) {
          const meses = contexto.meses.slice(offset, offset + 6);
          espacio(55); texto(label, margen, y, 9, verde, true);
          const top = y + 12, bottom = y + 34;
          const valores = meses.map(m => m[campo]);
          const min = Math.min(0, ...valores), max = Math.max(0, ...valores), range = Math.max(1, max - min);
          const step = ancho / Math.max(1, meses.length - 1);
          const puntos = meses.map((m, i) => ({ x: margen + i * step, y: bottom - (m[campo] - min) / range * (bottom - top), m }));
          doc.setDrawColor(...esmeralda); doc.setLineWidth(.6);
          puntos.forEach((p, i) => {
            if (i) doc.line(puntos[i - 1].x, puntos[i - 1].y, p.x, p.y);
            doc.setFillColor(...esmeralda); doc.circle(p.x, p.y, 1, 'F');
            const align = i === 0 ? 'left' : i === puntos.length - 1 ? 'right' : 'center';
            doc.setFontSize(7); doc.setTextColor(...(p.m[campo] < 0 ? rojo : verde));
            doc.text(dineroPdf(p.m[campo]), p.x, p.y - 3, { align });
            doc.text(p.m.mes, p.x, bottom + 6, { align });
          });
          doc.setLineWidth(.2); y += 49;
        }
      };
      serie('Balance mensual del período', 'balance');
      serie('Patrimonio al cierre (reconstruido)', 'patrimonio');
      serie('Deuda al cierre (reconstruida)', 'deuda');
      // Existing monthly table retains exact chart values, balance, cash flow and reconstructed closings.
    } else if (contexto.meses.length > 1) {
      parrafo('No hay actividad en varios meses para una evolución gráfica significativa. El detalle mensual conserva los períodos sin actividad.', 8);
    }
    tituloSeccion('Observaciones financieras', 25);
    for (const observacion of a.observaciones) parrafo(observacion, 9);
    if (contexto.anterior) {
      const previo = contexto.anterior;
      tabla({ titulo: 'Comparación con el período anterior', columnas: ['Indicador', 'Anterior', 'Actual', 'Diferencia', 'Variación'],
        filas: [['Ingresos', previo.ingresos, contexto.ingresos], ['Gastos', previo.gastos, contexto.gastos]].map(([label, antes, actual]) => {
          const delta = (Math.round(Number(actual) * 100) - Math.round(Number(antes) * 100)) / 100;
          return [String(label), dineroPdf(Number(antes)), dineroPdf(Number(actual)), dineroPdf(delta), Number(antes) > 0 ? `${(delta / Number(antes) * 100).toFixed(1)} %` : 'Sin base'];
        }), nota: `Registros del ${fechaPdf(previo.desde)} al ${fechaPdf(previo.hasta)} frente al período consultado: igual cantidad de días y mismos filtros. El historial puede ser incompleto; no es una conciliación bancaria.` });
    } else parrafo('No hay historial suficiente para comparar con un período anterior de igual duración y los mismos filtros. No se calculan porcentajes sin una base disponible.', 8);
    if (contexto.meses.length > 1) parrafo('Los cierres históricos son reconstruidos. No se atribuyen variaciones de deuda o patrimonio a causas que el historial no permita comprobar.', 8);
  }
  for (const seccion of secciones) {
    if (contexto && seccion.titulo === 'Resumen financiero') continue;
    if (contexto && !contexto.movimientos.length && (seccion.titulo === 'Evolución mensual' || !seccion.filas.length)) continue;
    // Up to five categories are already fully labeled in the donut; avoid repeating an identical table.
    if (contexto && contexto.gastos > 0 && seccion.titulo === 'Gastos por categoría' && seccion.filas.length <= 5
      && ['resumen', 'gastos'].includes(contexto.vista)) continue;
    tabla(seccion);
  }
  const total = doc.getNumberOfPages();
  for (let pagina = 1; pagina <= total; pagina++) {
    doc.setPage(pagina);
    if (pagina > 1) {
      if (logo) doc.addImage(logo, 'PNG', margen, 6, 8, 8, 'cofi-logo', 'FAST');
      texto('COFI', margen + (logo ? 11 : 0), 13, 11, verde, true);
      texto('Informe financiero | USD', derecha - 61, 13, 8);
      doc.setDrawColor(220, 232, 225); doc.line(margen, 17, derecha, 17);
    }
    doc.setDrawColor(220, 232, 225); doc.line(margen, altoPagina - 14, derecha, altoPagina - 14);
    texto('COFI | Información financiera personal', margen, altoPagina - 8, 7);
    doc.setFontSize(7); doc.text(`Página ${pagina} de ${total}`, derecha, altoPagina - 8, { align: 'right' });
  }
  return doc;
}
