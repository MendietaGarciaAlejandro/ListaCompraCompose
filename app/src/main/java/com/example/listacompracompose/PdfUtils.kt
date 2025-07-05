package com.example.listacompracompose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import android.content.Intent
import androidx.core.content.FileProvider

object PdfUtils {
    fun createShoppingListPdf(
        context: Context,
        products: List<Product>,
        fileName: String = "lista_compra.pdf"
    ): File {
        val pageWidth = 595  // A4 approx in points
        val pageHeight = 842
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 12f }

        // Una página
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Título
        canvas.drawText("Lista de la Compra", 20f, 30f, paint)

        // Encabezados
        var y = 60f
        paint.textSize = 10f
        products.forEachIndexed { index, p ->
            val line = "${index + 1}. ${p.name} — €%.2f x ${p.quantity}".format(p.price)
            canvas.drawText(line, 20f, y, paint)
            y += 15f
            // Si llenamos la página, podríamos crear más páginas (omito aquí)
        }

        doc.finishPage(page)

        // Guardar en cacheDir
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()
        return file
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Compartir PDF vía")
        )
    }

    fun shareText(context: Context, products: List<Product>) {
        val text = buildString {
            append("Mi lista de la compra:\n")
            products.forEach { p ->
                append("- ${p.name}: €%.2f x ${p.quantity}\n".format(p.price))
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(intent, "Compartir Lista vía")
        )
    }

}
