# ERROR REPORT — Mates Aconcagua

**Generado:** 2026-05-31  
**Alcance:** Backend (Spring Boot) + Frontend (React/Vite)

---

## Resumen por severidad

| Severidad | Cantidad |
|-----------|----------|
| 🔴 Crítico | 5 |
| 🟠 Alto | 7 |
| 🟡 Medio | 8 |
| 🔵 Bajo | 6 |
| **Total** | **26** |

---

## 🔴 Crítico

### C-01 — Precio de ítem de orden tomado del cliente (no de la DB)
**Archivo:** `api/src/main/java/com/matesaconcahua/api/service/OrderService.java`  
**Línea:** 97  
**Descripción:** En `buildItems()`, el `unitPrice` de cada `OrderItem` se extrae del mapa enviado por el cliente (`ci.get("price")`). Un usuario malintencionado puede enviar un precio de $0.01 para cualquier producto y la orden se guardará con ese precio.

```java
// VULNERABLE — precio viene del cliente
item.setUnitPrice(BigDecimal.valueOf(((Number) ci.get("price")).doubleValue()));
```

**Fix:** Usar siempre el precio del producto recuperado de la DB:
```java
item.setUnitPrice(product.getPrice());
```

---

### C-02 — Race condition en la reducción de stock (sin bloqueo pesimista)
**Archivo:** `api/src/main/java/com/matesaconcahua/api/service/OrderService.java`  
**Líneas:** 71-91  
**Descripción:** `validateStock()` lee el stock y luego `buildItems()` lo decrementa en dos operaciones separadas. Bajo carga concurrente, dos hilos pueden pasar la validación con stock = 1 y ambos decrementar, dejando stock en -1. No hay `@Lock(LockModeType.PESSIMISTIC_WRITE)` ni `@Version` para control optimista.

**Fix (opción A — bloqueo pesimista):**
```java
// En ProductRepository:
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") Integer id);
```
Usar `findByIdForUpdate` dentro del `@Transactional` de `create()`.

**Fix (opción B — Optimistic Locking):**  
Agregar `@Version private Long version;` en `Product` y manejar `OptimisticLockException` con reintento.

---

### C-03 — NPE al acceder a `body.get("total")` o `body.get("cart")` sin validación
**Archivo:** `api/src/main/java/com/matesaconcahua/api/controller/OrderController.java`  
**Líneas:** 31-32  
**Descripción:** Si el cliente no envía `total` o `cart` en el cuerpo, se producirá `NullPointerException` no controlada al invocar `.doubleValue()` en `null` o al hacer el cast sin chequeo.

```java
List<Map<String, Object>> cartItems = (List<Map<String, Object>>) body.get("cart");   // puede ser null
double total = ((Number) body.get("total")).doubleValue();                             // NPE si null
```

**Fix:**
```java
if (body.get("cart") == null || body.get("total") == null)
    return ResponseEntity.badRequest().body(Map.of("error", "Faltan campos: cart, total"));
```

---

### C-04 — NPE al acceder a `body.get("productId")` o `body.get("rating")` en ReviewController sin validación
**Archivo:** `api/src/main/java/com/matesaconcahua/api/controller/ReviewController.java`  
**Líneas:** 27-28  
**Descripción:** Si el cuerpo no contiene `productId` o `rating`, la llamada a `.intValue()` sobre `null` lanza `NullPointerException`. No hay validación previa ni DTO con `@Valid`.

**Fix:** Agregar validaciones nulas o crear un DTO `CreateReviewRequest` con `@Valid @NotNull`.

---

### C-05 — pom.xml usa Spring Boot 4.0.6 (versión inexistente / inestable)
**Archivo:** `api/pom.xml`  
**Línea:** 8  
**Descripción:** La versión del parent `spring-boot-starter-parent` es `4.0.6`. Spring Boot 4.x no existe como versión pública estable. La versión estable actual es `3.4.x`. Esto puede causar fallos de compilación o comportamiento impredecible en producción.

```xml
<version>4.0.6</version>   <!-- ¡No existe! -->
```

**Fix:** Cambiar a `3.4.5` (o la última `3.x` estable disponible).

---

## 🟠 Alto

### A-01 — El endpoint `POST /api/checkout/create-preference` es público (sin autenticación requerida)
**Archivo:** `api/src/main/java/com/matesaconcahua/api/config/SecurityConfig.java`  
**Línea:** 44  
**Descripción:** La regla `.requestMatchers(HttpMethod.POST, "/api/checkout/**").permitAll()` permite a cualquier usuario no autenticado crear preferencias de pago. Esto puede ser abusado para crear preferencias masivas con cargos a la cuenta de MercadoPago.

**Fix:** Cambiar `permitAll()` a `.authenticated()` para el endpoint de checkout.

---

### A-02 — El total de la orden se acepta del cliente sin recalcularlo en el servidor
**Archivo:** `api/src/main/java/com/matesaconcahua/api/service/OrderService.java`  
**Línea:** 51  
**Descripción:** `order.setTotal(BigDecimal.valueOf(totalRaw))` usa el total enviado por el cliente. El total correcto debería calcularse en el servidor sumando `product.getPrice() * quantity` para cada ítem.

**Fix:**
```java
BigDecimal serverTotal = items.stream()
    .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
    .reduce(BigDecimal.ZERO, BigDecimal::add);
order.setTotal(serverTotal);
```

---

### A-03 — URL de órdenes incorrecta en `Orders.jsx` (falta prefijo `/api`)
**Archivo:** `src/pages/Orders.jsx`  
**Línea:** 24  
**Descripción:** La petición para obtener órdenes del usuario usa `${BASE_URL}/orders` en lugar de `${BASE_URL}/api/orders`. Esto produce un 404 en producción. El mismo error ocurre en `CheckoutResult.jsx` línea 106.

```js
// INCORRECTO
const res = await fetch(`${BASE_URL}/orders`, ...);
// CORRECTO
const res = await fetch(`${BASE_URL}/api/orders`, ...);
```

**Fix:** Corregir la URL a `/api/orders` en ambos archivos, o usar `orderService.getOrders(accessToken)`.

---

### A-04 — `checkout` en CartContext usa `totalPrice` antes de su definición (posible referencia a valor viejo)
**Archivo:** `src/context/CartContext.jsx`  
**Líneas:** 107, 125  
**Descripción:** La función `checkout` referencia `totalPrice` en la línea 107 (dentro del try), pero `totalPrice` se define en la línea 125 como una constante derivada de `cart`. En la closure de `checkout`, `totalPrice` es el valor del render anterior, no el valor actualizado del carrito en el momento de llamar a `checkout`. En un carrito con actualizaciones rápidas, podría enviarse un total incorrecto.

**Fix:** Calcular `totalPrice` localmente dentro de la función `checkout`:
```js
const total = cart.reduce((s, i) => s + i.price * i.quantity, 0);
```

---

### A-05 — Sin validación de `rating` (1-5) en ReviewService / ReviewController
**Archivo:** `api/src/main/java/com/matesaconcahua/api/service/ReviewService.java`  
**Líneas:** 29-41 | `ReviewController.java` líneas 27-28  
**Descripción:** No se valida que `rating` esté en el rango [1, 5]. Un cliente puede enviar rating = 999 o rating = -1 y se guardará sin error.

**Fix:**
```java
if (rating < 1 || rating > 5)
    throw new BusinessException("El rating debe estar entre 1 y 5");
```

---

### A-06 — N+1 query en `OrderService.findAll()` / `findByUser()`
**Archivo:** `api/src/main/java/com/matesaconcahua/api/service/OrderService.java`  
**Líneas:** 34, 30  
**Descripción:** `orderRepository.findAll()` y `findByUserIdOrderByCreatedAtDesc()` cargan órdenes con fetch `EAGER` en `items` y `user`, pero la colección de `items` tiene `FetchType.EAGER` sobre `product`. Esto genera N+1 consultas al cargar `N` órdenes (1 query por orden para sus ítems, 1 query por ítem para su producto).

**Fix:** Agregar en `OrderRepository`:
```java
@Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
List<Order> findByUserIdWithItemsOrderByCreatedAtDesc(@Param("userId") String userId);
```

---

### A-07 — `CheckoutResult.jsx` escribe stock en `localStorage` pero el backend ya lo hace en la DB
**Archivo:** `src/pages/CheckoutResult.jsx`  
**Líneas:** 34-51, 121  
**Descripción:** La función `decrementStock()` reduce el stock en `localStorage` del admin. Si el usuario tiene la sesión sincronizada con el backend, el stock se decrementa dos veces: una en la DB (via `OrderService.buildItems()`) y otra en localStorage. Esto causa que el panel admin muestre stock incorrecto.

**Fix:** Eliminar la función `decrementStock` y la lógica de fallback de localStorage; confiar únicamente en el estado del backend.

---

## 🟡 Medio

### M-01 — `ProductController.create` no usa `@Valid` (sin validación de campos obligatorios)
**Archivo:** `api/src/main/java/com/matesaconcahua/api/controller/ProductController.java`  
**Línea:** 32  
**Descripción:** El endpoint `POST /api/products` recibe un `Product` directamente sin `@Valid`, lo que omite las validaciones `@Column(nullable=false)`. Un admin puede crear un producto sin nombre o sin precio y obtendrá un error 500 de Hibernate en lugar de un 400 informativo.

**Fix:** Crear un DTO `CreateProductRequest` con anotaciones `@NotBlank`, `@NotNull`, `@Positive` y usar `@Valid @RequestBody`.

---

### M-02 — `ProductController.update` no usa `@Valid` — misma causa que M-01
**Archivo:** `api/src/main/java/com/matesaconcahua/api/controller/ProductController.java`  
**Línea:** 39  
**Descripción:** El endpoint `PUT /api/products/{id}` tampoco valida el cuerpo de la petición.

**Fix:** Igual que M-01 — usar DTO con `@Valid`.

---

### M-03 — `JwtUtil.secret` puede ser demasiado corto para HMAC-SHA256
**Archivo:** `api/src/main/java/com/matesaconcahua/api/security/JwtUtil.java`  
**Línea:** 22  
**Descripción:** `Keys.hmacShaKeyFor()` requiere al menos 32 bytes para HS256. Si `JWT_SECRET` es corto, el arranque fallará con una excepción de jjwt. No hay validación explícita del secreto al iniciar la app, lo que dificulta el diagnóstico.

**Fix:** Agregar validación en `@PostConstruct`:
```java
@PostConstruct
public void validate() {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
        throw new IllegalStateException("jwt.secret must be at least 32 bytes");
}
```

---

### M-04 — `Cart.items` almacena JSON como String en columna JSON sin tipo seguro
**Archivo:** `api/src/main/java/com/matesaconcahua/api/entity/Cart.java`  
**Líneas:** 20-21  
**Descripción:** Los ítems del carrito se almacenan como `String` con `columnDefinition = "JSON"`. Si se inserta JSON malformado (por ejemplo, desde un bug en el frontend), la columna quedará con datos corruptos. No hay validación de estructura antes de guardar.

**Fix:** Validar que el JSON sea parseble antes de guardar en `CartController`, o usar una entidad `CartItem` separada.

---

### M-05 — `AuthContext.jsx` expone el token JWT en `localStorage` sin expiración local
**Archivo:** `src/context/AuthContext.jsx`  
**Líneas:** 31-34  
**Descripción:** El token se guarda en `localStorage` sin verificar la expiración en el cliente. Si el token expiró (86400000 ms = 24h), el frontend seguirá pensando que el usuario está autenticado hasta que una petición al backend falle con 401. No hay manejo automático de refresco o expiración.

**Fix:** Al cargar la sesión, decodificar el JWT (sin librería, sólo base64 del payload) y comparar `exp` con `Date.now()`:
```js
function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 < Date.now();
  } catch { return true; }
}
```

---

### M-06 — Hardcoded URLs de n8n webhooks con IP pública en código fuente
**Archivo:** `src/services/n8nService.js`  
**Líneas:** 3-8  
**Descripción:** Las URLs de n8n (incluyendo la IP `66.94.104.64`) están hardcodeadas en el código fuente, que es público en el repositorio. Esto expone la infraestructura interna.

**Fix:** Usar variables de entorno Vite:
```js
bienvenida: import.meta.env.VITE_N8N_BIENVENIDA_URL,
```

---

### M-07 — `Shop.jsx` puede lanzar error si `product.description` es `null`
**Archivo:** `src/pages/Shop.jsx`  
**Línea:** 73  
**Descripción:** El filtro de búsqueda llama a `p.description.toLowerCase()` sin verificar que `description` no sea `null`. El campo `description` en la entidad `Product` no tiene `nullable=false`, por lo que puede ser null.

```js
p.description.toLowerCase().includes(searchTerm.toLowerCase())  // TypeError si description es null
```

**Fix:**
```js
(p.description ?? '').toLowerCase().includes(searchTerm.toLowerCase())
```

---

### M-08 — `Orders.jsx` usa `item.name` pero `OrderItem` no expone `name`
**Archivo:** `src/pages/Orders.jsx`  
**Línea:** 90  
**Descripción:** En la lista de ítems de una orden, se accede a `item.name`, pero la entidad `OrderItem` expone un objeto `product` (con `@JsonIgnoreProperties` parcial). El campo correcto es `item.product.name`. Esto causará que los nombres aparezcan como `undefined` en la vista.

**Fix:**
```jsx
{item.product?.name ?? 'Producto'} <span className="text-gray-400">×{item.quantity}</span>
```

---

## 🔵 Bajo

### B-01 — `GlobalExceptionHandler` atrapa `Exception.class` genérico y oculta stack trace de desarrollo
**Archivo:** `api/src/main/java/com/matesaconcahua/api/exception/GlobalExceptionHandler.java`  
**Líneas:** 45-49  
**Descripción:** El handler genérico devuelve siempre "Error interno del servidor" sin loggear el stack trace. En desarrollo es difícil diagnosticar errores inesperados.

**Fix:** Agregar log:
```java
log.error("Unhandled exception", ex);
```

---

### B-02 — `ProductService.update` llama a `findById` dos veces (extra query)
**Archivo:** `api/src/main/java/com/matesaconcahua/api/service/ProductService.java`  
**Líneas:** 40, 71  
**Descripción:** `delete()` llama `existsById()` y luego `deleteById()`, generando dos queries cuando una sola bastaría (`deleteById` ya lanza `EmptyResultDataAccessException` si no existe en Spring Data JPA con versiones antiguas, aunque en Spring Boot 3 esto cambió). Se podría simplificar.

---

### B-03 — `n8nService.enviarCarritoAbandonado` se llama en CADA `addToCart`
**Archivo:** `src/context/CartContext.jsx`  
**Líneas:** 82-87  
**Descripción:** Cada vez que se agrega un producto al carrito, se dispara el webhook de "carrito abandonado". Esto es incorrecto semánticamente: el carrito abandonado debería dispararse tras un tiempo sin actividad, no inmediatamente al agregar un ítem.

**Fix:** Usar un debounce de 10-30 minutos antes de enviar el webhook de abandono, o dispararlo únicamente cuando el usuario cierra la sesión o la pestaña.

---

### B-04 — `AuthContext.jsx` usa `import.meta.env.VITE_API_URL` directamente en vez de `getBaseUrl()` de `api.js`
**Archivo:** `src/context/AuthContext.jsx`  
**Línea:** 3  
**Descripción:** Existe una función `getBaseUrl()` centralizada en `api.js`, pero `AuthContext` redefine la constante `API` directamente desde `import.meta.env`. Si la variable cambia, hay que actualizarla en dos lugares.

**Fix:** Importar y usar `getBaseUrl()`.

---

### B-05 — `pom.xml` no tiene H2 como dependencia de test
**Archivo:** `api/pom.xml`  
**Descripción:** No hay ninguna dependencia H2 en scope `test`. Los tests de integración que usan `@SpringBootTest` fallarán al intentar conectarse a MySQL (que no existe en CI/CD).

**Fix:** Agregar:
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

---

### B-06 — `LOCAL_STORAGE_KEYS.SESSION` en `constants.js` es `mate_local_session` pero `AuthContext` usa `mate_session`
**Archivo:** `src/utils/constants.js` línea 31 | `src/context/AuthContext.jsx` línea 4  
**Descripción:** Hay dos nombres de clave distintos para la sesión en localStorage. Esto no causa un bug funcional ahora (ambos coexisten), pero si se refactoriza usando `LOCAL_STORAGE_KEYS.SESSION`, se romperá la sesión de usuarios existentes.

**Fix:** Unificar la clave usando la constante de `constants.js`.
