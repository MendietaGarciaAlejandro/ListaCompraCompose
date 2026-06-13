# Lista de la Compra

App Android para gestionar la lista de la compra del día a día. Desarrollada en Kotlin con Jetpack Compose.

## Requisitos

- Android 8.0 (API 26) o superior
- Android Studio para compilar

## Funcionalidades

### Lista principal
- Añadir productos con nombre, precio, cantidad y categoría
- Categorías personalizables y reordenables (drag & drop)
- Botones + / - para ajustar cantidad directamente desde la lista
- Marcar productos como comprados
- Editar o eliminar productos con opción de deshacer (Snackbar)
- Importar lista escribiendo o pegando texto en formato libre (`leche x2`, `aceite 3.50€`, etc.)

### Historial e inteligencia
- Los productos se recuerdan automáticamente (nombre, precio, cantidad, categoría)
- Autocompletado al escribir desde 3 caracteres
- Productos favoritos marcados con estrella, accesibles sin escribir nada
- Precio y cantidad se actualizan si han cambiado respecto al histórico

### Plantillas
- Guardar la lista actual como plantilla para reutilizarla
- Vista previa antes de cargar (opción de reemplazar o añadir a la lista actual)
- Al completar una compra se ofrece guardar la lista como plantilla

### Modo compras
- Pantalla de compra rápida: muestra solo los productos pendientes agrupados por categoría
- Progreso visual en tiempo real
- Total del carrito visible mientras se marca
- Al completar todos los items muestra pantalla de resumen

### Finalizar compra
- Botón de finalizar que vacía la lista y registra la sesión de compra
- Si quedan productos sin marcar avisa antes de continuar

### Estadísticas
- Gráfico de barras con el gasto de los últimos 6 meses
- Promedio mensual y promedio por compra
- Contador de compras realizadas (se incrementa al finalizar, se contabiliza por mes)
- Producto más comprado
- Detalle por producto: precio último, medio, mínimo y máximo, tendencia respecto a la compra anterior
- Exportar la lista actual en PDF

### Widget
- Widget para la pantalla de inicio que muestra los productos pendientes
- Se actualiza automáticamente al modificar la lista
- Toca el widget para abrir la app directamente

### Recordatorios
- Recordatorio semanal configurable (día y hora)
- Solo notifica si hay productos en la lista
- Se activa y configura desde Ajustes

### Ajustes
- Tema claro, oscuro o según el sistema
- Reordenar categorías manualmente

## Stack técnico

- Kotlin + Jetpack Compose (Material 3)
- Room (base de datos local, v9)
- StateFlow + ViewModel
- AppWidgetProvider para el widget
- AlarmManager para recordatorios
- SharedPreferences para preferencias de usuario

## Autor

Alejandro Mendieta García
