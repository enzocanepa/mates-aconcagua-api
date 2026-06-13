# Mates Aconcagua — API Backend

REST API del e-commerce Mates Aconcagua. Construida con Spring Boot y desplegada en Railway. Gestiona autenticación, productos, carrito, pedidos, reseñas y pagos con Mercado Pago.

---

## Stack tecnológico

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white&style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=springboot&logoColor=white&style=flat-square)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white&style=flat-square)
![Railway](https://img.shields.io/badge/Railway-Deploy-0B0D0E?logo=railway&logoColor=white&style=flat-square)
![JWT](https://img.shields.io/badge/JWT-Auth-000000?logo=jsonwebtokens&logoColor=white&style=flat-square)
![MercadoPago](https://img.shields.io/badge/Mercado_Pago-SDK-009EE3?logo=mercadopago&logoColor=white&style=flat-square)

---

## Endpoints

### Autenticación — `/api/auth` (público)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/signup` | Registro de nuevo usuario |
| `POST` | `/api/auth/login` | Inicio de sesión |
| `POST` | `/api/auth/forgot-password` | Solicitar código de recuperación (email) |
| `POST` | `/api/auth/reset-password` | Cambiar contraseña con código |

### Productos — `/api/products`

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/products` | Público | Listar todos los productos |
| `GET` | `/api/products/{id}` | Público | Detalle de un producto |
| `POST` | `/api/products` | Admin | Crear producto |
| `PUT` | `/api/products/{id}` | Admin | Actualizar producto |
| `DELETE` | `/api/products/{id}` | Admin | Eliminar producto |

### Carrito — `/api/cart`

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/cart` | Usuario | Obtener carrito del usuario |
| `POST` | `/api/cart` | Usuario | Guardar carrito del usuario |

### Pedidos — `/api/orders`

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/orders` | Usuario | Pedidos del usuario autenticado |
| `POST` | `/api/orders` | Usuario | Crear pedido desde el carrito |
| `GET` | `/api/orders/admin` | Admin | Todos los pedidos del sistema |
| `PATCH` | `/api/orders/{id}` | Admin | Actualizar estado del pedido |

### Reseñas — `/api/reviews`

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/reviews/{productId}` | Público | Reseñas de un producto |
| `POST` | `/api/reviews` | Usuario | Crear reseña (rating 1-5) |

### Checkout — `/api/checkout`

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `POST` | `/api/checkout/create-preference` | Usuario | Crear preferencia de Mercado Pago |

### Health

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/actuator/health` | Estado del servidor |

---

## Estructura del proyecto

```
src/main/java/com/matesaconcahua/api/
├── controller/
│   ├── AuthController.java
│   ├── ProductController.java
│   ├── CartController.java
│   ├── OrderController.java
│   ├── ReviewController.java
│   └── CheckoutController.java
├── service/
│   ├── ProductService.java
│   ├── OrderService.java
│   └── ReviewService.java
├── entity/
│   ├── User.java
│   ├── Product.java
│   ├── ProductImage.java
│   ├── ProductVariant.java
│   ├── Order.java
│   ├── OrderItem.java
│   ├── Cart.java
│   └── Review.java
├── dto/
│   └── auth/
│       ├── AuthResponse.java
│       ├── LoginRequest.java
│       └── SignupRequest.java
├── repository/
│   ├── UserRepository.java
│   ├── ProductRepository.java
│   ├── OrderRepository.java
│   ├── CartRepository.java
│   └── ReviewRepository.java
├── security/
│   ├── JwtUtil.java
│   └── JwtFilter.java
├── config/
│   ├── SecurityConfig.java
│   └── AppConfig.java
└── exception/
    ├── GlobalExceptionHandler.java
    ├── BusinessException.java
    └── ResourceNotFoundException.java
```

---

## Configuración local

### Requisitos

- Java 21+
- Maven 3.9+
- MySQL 8.0

### Pasos

```bash
# Clonar el repositorio
git clone https://github.com/tu-usuario/mates-aconcagua-api.git
cd mates-aconcagua-api

# Crear la base de datos
mysql -u root -p -e "CREATE DATABASE mates_aconcagua;"

# Configurar variables de entorno (ver sección siguiente)
# Compilar y ejecutar
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

### Variables de entorno

| Variable | Descripción | Ejemplo |
|---|---|---|
| `DB_URL` | URL JDBC de la base de datos | `jdbc:mysql://localhost:3306/mates_aconcagua` |
| `DB_USERNAME` | Usuario de MySQL | `root` |
| `DB_PASSWORD` | Contraseña de MySQL | `secreto` |
| `JWT_SECRET` | Clave secreta para firmar tokens (mín. 32 caracteres) | `clave-super-secreta-de-al-menos-32-chars` |
| `MP_ACCESS_TOKEN` | Access Token de Mercado Pago | `TEST-xxxx...` o `APP_USR-xxxx...` |
| `APP_BASE_URL` | URL del frontend (para redirecciones de MP) | `https://tu-tienda.vercel.app` |
| `CORS_ORIGINS` | Orígenes permitidos por CORS | `https://tu-tienda.vercel.app` |
| `MAIL_USERNAME` | Usuario SMTP de Brevo | `tu@email.com` |
| `MAIL_PASSWORD` | Clave SMTP de Brevo | `xsmtpsib-xxxx...` |
| `MAIL_FROM` | Email remitente verificado en Brevo | `tu@email.com` |

Para desarrollo local, se pueden definir en `application.properties` o como variables del sistema.

---

## Seguridad

### Autenticación JWT

Todos los endpoints protegidos requieren el header:

```
Authorization: Bearer <token>
```

El token se obtiene al hacer login o registro. Expira en **24 horas**.

El payload del token contiene:
```json
{
  "sub": "uuid-del-usuario",
  "email": "usuario@email.com",
  "role": "user",
  "iat": 1700000000,
  "exp": 1700086400
}
```

### Roles

| Rol | Acceso |
|---|---|
| Anónimo | Productos, reseñas, auth |
| `user` | + Carrito, pedidos propios, checkout, crear reseñas |
| `admin` | + CRUD productos, todos los pedidos, cambiar estados |

### Decisiones de diseño de seguridad

- **Precios del servidor**: `OrderService` y `CheckoutController` obtienen los precios de la base de datos, nunca del cliente
- **Stock con lock pesimista**: `OrderService` usa `PESSIMISTIC_WRITE` para evitar condiciones de carrera al decrementar stock
- **Enumeración de emails bloqueada**: `/forgot-password` siempre devuelve 200 independientemente de si el email existe
- **Código de reset de un solo uso**: al usarse, se borra inmediatamente del usuario
- **Contraseñas con BCrypt**: factor de costo por defecto de Spring Security

---

## Flujo de pago (Mercado Pago)

```
Frontend                    Backend                    Mercado Pago
   │                           │                            │
   │── POST /checkout ────────▶│                            │
   │   { items, payer }        │── Valida precios en DB     │
   │                           │── Crea preferencia ───────▶│
   │                           │◀─ { init_point, pref_id } ─│
   │◀─ { init_point } ─────────│                            │
   │                           │                            │
   │── Redirige a MP ─────────────────────────────────────▶│
   │                           │                            │
   │◀─ Redirige a /checkout/exito|error|pendiente ──────────│
```

La API detecta automáticamente si el token de MP es de sandbox (`TEST-`) y usa el `sandboxInitPoint` correspondiente.

---

## Flujo de recuperación de contraseña

```
POST /api/auth/forgot-password   { email }
→ Genera código de 6 dígitos
→ Guarda con expiración de 15 minutos
→ Envía email con código (Brevo SMTP)
→ Responde 200 siempre

POST /api/auth/reset-password    { email, code, newPassword }
→ Verifica código y expiración
→ Actualiza contraseña (BCrypt)
→ Invalida el código usado
→ Responde 200 o 400 (código inválido/expirado)
```

---

## Despliegue en Railway

El proyecto incluye `railway.toml`:

```toml
[build]
builder = "nixpacks"
buildCommand = "./mvnw clean package"

[deploy]
startCommand = "java -jar target/*.jar"
healthcheckPath = "/actuator/health"
healthcheckTimeout = 60
restartPolicyType = "on_failure"
```

### Variables de entorno en Railway

Configurar todas las variables de la tabla de configuración en el panel de Railway → proyecto → Variables.

---

## Tests

```bash
# Ejecutar todos los tests
./mvnw test

# Con reporte de cobertura
./mvnw verify
```

Los tests usan H2 (base de datos en memoria) configurada en `src/test/resources/`.

---

## Manejo de errores

Todas las respuestas de error siguen el formato:

```json
{ "error": "Descripción del error" }
```

| Código | Cuándo ocurre |
|---|---|
| `400` | Validación fallida, datos incorrectos |
| `401` | Token ausente, inválido o expirado |
| `403` | Rol insuficiente (ej: usuario intentando acceder a ruta admin) |
| `404` | Recurso no encontrado |
| `409` | Conflicto (ej: email ya registrado) |
| `500` | Error interno del servidor |
| `502` | Error al conectar con Mercado Pago |

---

## Contacto

**Mates Aconcagua** — Mendoza, Argentina  
Email: [lorenzocona14@gmail.com](mailto:lorenzocona14@gmail.com)
