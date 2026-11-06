# Lista de la compra

La app con la que mi padre hace la compra. Empezó como una lista con precios y ha ido creciendo a base de peticiones suyas: que recuerde lo que compra siempre, que sepa cuánto se gasta al mes, que le recuerde una vez a la semana si tiene algo apuntado. Está en Kotlin con Jetpack Compose y funciona desde Android 8.

La regla para cualquier cambio es que él no note que la app se ha complicado. Por eso no tiene login, ni pantallas de configuración que no vaya a tocar, y el inventario de casa vive en otra app ([InventarioCasa](https://github.com/MendietaGarciaAlejandro/InventarioCasa)).

## Lo que hace

La lista se organiza por categorías, que se pueden reordenar arrastrando. Cada producto lleva cantidad y precio, y al escribir se autocompleta con lo comprado otras veces, con el último precio y la última cantidad. Los habituales se marcan con estrella para no tener que escribirlos. También se puede pegar una lista de texto ("leche x2, aceite 3.50€") y la app la convierte en productos.

En el supermercado se usa el modo compras: solo lo que falta, agrupado por categoría, con el total del carro según se va marcando. Al terminar se pulsa "Finalizar compra", que vacía la lista y guarda lo gastado. Con eso salen las estadísticas: gasto de los últimos seis meses, media por compra y, por producto, el precio mínimo, máximo y si ha subido desde la última vez.

Además:

- plantillas para las compras que se repiten
- un widget con lo que queda pendiente
- un recordatorio semanal, que solo salta si hay algo en la lista
- exportar la lista a PDF
- tema claro u oscuro

## La parte compartida

La lista no está solo en el móvil. Se guarda en Supabase, y la misma base de datos la usan una web (para apuntar cosas desde el ordenador o desde mi iPhone) y la app del inventario. Fue lo que más trabajo dio, porque mi padre hace la compra a menudo sin cobertura dentro del supermercado.

Cómo quedó:

- Room sigue siendo la fuente de verdad en el móvil. Todo se escribe primero en local y después se sube, así que la app funciona igual sin red.
- Lo que se crea o se borra sin conexión se guarda en tablas aparte (`pending_deletes` y compañía) y se reintenta al recuperar la red. Sin esto, un producto borrado offline volvía a aparecer en la siguiente descarga.
- Los cambios de la web llegan por un WebSocket de Supabase Realtime. Como no me fío de que esa conexión aguante, la app además pregunta cada 5 segundos mientras está abierta, y un WorkManager reconcilia cada cierto tiempo aunque esté cerrada.
- Al apuntar un producto o en el modo compras, la app avisa si ya hay en casa ("Ya hay 2 en casa"). Lo lee del inventario cada 30 segundos.
- Solo "Finalizar compra" cuenta como comprado: lo que estaba marcado pasa al inventario (de eso se encarga un trigger en la base de datos). Si se quita un producto de la lista sin comprarlo, se desmarca antes de borrarlo para que no sume.

Lo flojo: la lista y el inventario se cruzan por nombre, así que "Leche" y "Leche entera" no se reconocen como lo mismo. Y como no hay login, la app entra con la clave anónima de Supabase. Para una app que usa una persona en su móvil me vale; si alguna vez la usara más gente, sería lo primero que cambiaría.

## Compilar

Hace falta Android Studio y un `local.properties` con las claves de Supabase (no está en el repositorio):

```
SUPABASE_URL=...
SUPABASE_ANON_KEY=...
```

Sin claves la app arranca igual, pero solo en local. El esquema de la base de datos está en el repositorio de la web, en `supabase/schema.sql`.

## Por hacer

- Cruzar la lista con el inventario por código de barras en vez de por nombre
- Partir `CompraApp.kt`, que pasa de mil líneas y se ha quedado como cajón de sastre de pantallas
