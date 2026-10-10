package com.example.listacompracompose

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.util.concurrent.Executors

class ShoppingListWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: ${appWidgetIds.size} widgets")
        appWidgetIds.forEach { id -> scheduleUpdate(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ShoppingListWidget::class.java))
            Log.d(TAG, "ACTION_REFRESH: ${ids.size} widgets")
            ids.forEach { scheduleUpdate(context, manager, it) }
        }
    }

    companion object {
        private const val TAG = "ShoppingWidget"
        const val ACTION_REFRESH = "com.example.listacompracompose.WIDGET_REFRESH"

        private val executor = Executors.newSingleThreadExecutor()

        private val itemIds = intArrayOf(
            R.id.widget_item_0, R.id.widget_item_1, R.id.widget_item_2,
            R.id.widget_item_3, R.id.widget_item_4
        )

        private fun scheduleUpdate(context: Context, manager: AppWidgetManager, widgetId: Int) {
            executor.execute {
                try {
                    val db = AppDatabase.getInstance(context.applicationContext)
                    val pending = db.productDao().getPendingSync()
                    Log.d(TAG, "Loaded ${pending.size} pending items")
                    val views = buildViews(context.applicationContext, pending)
                    manager.updateAppWidget(widgetId, views)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget", e)
                    try {
                        val fallback = RemoteViews(context.packageName, R.layout.widget_shopping_list)
                        // Show error message directly in widget so user can read it
                        val msg = "${e.javaClass.simpleName}: ${e.message?.take(80)}"
                        fallback.setTextViewText(R.id.widget_title, msg)
                        fallback.setTextViewText(R.id.widget_count, "ERROR")
                        fallback.setViewVisibility(R.id.widget_empty, View.GONE)
                        itemIds.forEach { fallback.setViewVisibility(it, View.GONE) }
                        manager.updateAppWidget(widgetId, fallback)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Fallback also failed: ${e2.message}")
                    }
                }
            }
        }

        private fun buildViews(context: Context, pending: List<Product>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_shopping_list)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingOpen)

            itemIds.forEach { views.setViewVisibility(it, View.GONE) }
            views.setViewVisibility(R.id.widget_more, View.GONE)

            if (pending.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_count, "")
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                val total = pending.size
                views.setTextViewText(R.id.widget_count, "$total pendiente${if (total > 1) "s" else ""}")

                pending.take(itemIds.size).forEachIndexed { i, product ->
                    val label = if (product.quantity > 1) "• ${product.name}  x${product.quantity}" else "• ${product.name}"
                    views.setTextViewText(itemIds[i], label)
                    views.setViewVisibility(itemIds[i], View.VISIBLE)
                }

                if (total > itemIds.size) {
                    views.setTextViewText(R.id.widget_more, "+ ${total - itemIds.size} mas...")
                    views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                }
            }

            return views
        }

        fun notifyDataChanged(context: Context) {
            val intent = Intent(context, ShoppingListWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }
}
