# TESTING — Mates Aconcagua

Guía completa para ejecutar los tests del backend (Spring Boot) y del frontend (React/Vite).

---

## Índice
1. [Backend — Spring Boot / JUnit 5](#backend)
2. [Frontend — Vitest + React Testing Library](#frontend)
3. [Ejecutar ambos con un solo comando](#ambos)
4. [Estructura de los tests](#estructura)
5. [Referencia a ERROR_REPORT.md](#errores)

---

## Backend — Spring Boot / JUnit 5 {#backend}

### Requisitos previos
- Java 21
- Maven (o el wrapper `./mvnw` incluido en el proyecto)
- No se necesita MySQL — los tests usan H2 en memoria con el perfil `test`

### Ejecutar todos los tests
```bash
# Desde el directorio: C:\Users\enzoc\Desktop\api\api\api\
./mvnw test
# o en Windows:
mvnw.cmd test
```

### Ejecutar una clase específica
```bash
./mvnw test -Dtest=AuthControllerTest
./mvnw test -Dtest=ProductControllerTest
./mvnw test -Dtest=OrderControllerTest
./mvnw test -Dtest=CheckoutControllerTest
./mvnw test -Dtest=SecurityTest
```

### Ejecutar un método de test específico
```bash
./mvnw test -Dtest=AuthControllerTest#signup_happyPath
```

### Ver reporte de cobertura (JaCoCo)
Para agregar cobertura, añadir el plugin JaCoCo en `pom.xml`:
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```
Luego ejecutar:
```bash
./mvnw test
# Reporte HTML en: target/site/jacoco/index.html
```

### Configuración de base de datos de test
Los tests usan el perfil `test` que carga:
`src/test/resources/application-test.properties`

```properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL
spring.jpa.hibernate.ddl-auto=create-drop
jwt.secret=test-secret-key-for-unit-tests-minimum-32-chars
```

H2 se levanta automáticamente, no se requiere ninguna configuración externa.

---

## Frontend — Vitest + React Testing Library {#frontend}

### Requisitos previos
- Node.js 18+
- npm

### Instalar dependencias de test
```bash
# Desde: C:\Users\enzoc\Desktop\Ecommerce_Mates\
npm install
```

Los paquetes de test nuevos son:
- `vitest` — test runner compatible con Vite
- `@testing-library/react` — renderizado de componentes React en tests
- `@testing-library/jest-dom` — matchers adicionales (`.toBeInTheDocument()`, etc.)
- `@testing-library/user-event` — simulación de interacciones de usuario
- `msw` — Mock Service Worker para interceptar peticiones HTTP
- `jsdom` — DOM virtual para tests en Node.js
- `@vitest/coverage-v8` — cobertura de código

### Configurar variables de entorno de test
```bash
cp .env.test.example .env.test
# Editar .env.test si se necesitan URLs reales
```

### Ejecutar todos los tests
```bash
npm test
# o con modo watch:
npm test -- --watch
```

### Ejecutar con interfaz visual
```bash
npm run test:ui
# Abre el navegador en http://localhost:51204/__vitest__/
```

### Ejecutar con cobertura
```bash
npm run test:coverage
# Reporte HTML en: coverage/index.html
```

### Ejecutar un archivo específico
```bash
npm test src/services/api.test.js
npm test src/context/CartContext.test.jsx
npm test src/pages/Checkout.test.jsx
npm test src/pages/admin/AdminProducts.test.jsx
```

### Ejecutar tests que coinciden con un patrón
```bash
npm test -- -t "should add a product"
```

---

## Ejecutar ambos con un solo comando {#ambos}

### Windows (PowerShell)
```powershell
cd C:\Users\enzoc\Desktop\api\api\api
.\mvnw.cmd test
if ($LASTEXITCODE -eq 0) {
    cd C:\Users\enzoc\Desktop\Ecommerce_Mates
    npm test -- --run
}
```

### Script de CI combinado (bash)
```bash
#!/bin/bash
set -e

echo "=== Tests Backend ==="
cd /path/to/api/api/api
./mvnw test

echo "=== Tests Frontend ==="
cd /path/to/Ecommerce_Mates
npm ci
npm test -- --run

echo "=== Todos los tests pasaron ==="
```

---

## Estructura de los tests {#estructura}

### Backend
```
api/api/api/src/test/
├── resources/
│   └── application-test.properties     # Config H2 + JWT para tests
└── java/com/matesaconcahua/api/
    ├── AuthControllerTest.java          # POST /api/auth/signup y /login
    ├── ProductControllerTest.java       # CRUD /api/products
    ├── OrderControllerTest.java         # CRUD /api/orders
    ├── CheckoutControllerTest.java      # POST /api/checkout/create-preference
    └── SecurityTest.java                # Autenticación, autorización, JWT
```

**Patrón de cada test class:**
- `@SpringBootTest` — levanta contexto completo de Spring
- `@AutoConfigureMockMvc` — inyecta `MockMvc` para HTTP virtual
- `@ActiveProfiles("test")` — usa `application-test.properties` con H2
- `@Transactional` — cada test revierte sus cambios al terminar

### Frontend
```
Ecommerce_Mates/src/
├── test/
│   ├── setup.ts                         # Importa @testing-library/jest-dom
│   ├── handlers.js                      # Handlers MSW (mocks de todos los endpoints)
│   └── server.js                        # setupServer(…handlers)
├── services/
│   └── api.test.js                      # Tests de apiRequest, ApiError, getBaseUrl
├── context/
│   └── CartContext.test.jsx             # Tests del carrito (add, remove, clear, persist)
└── pages/
    ├── Checkout.test.jsx                # Tests de la página de checkout
    └── admin/
        └── AdminProducts.test.jsx       # Tests del panel admin de productos
```

**Patrón de cada test file:**
- `vi.mock(...)` — mockea módulos externos antes de importar el componente
- `render(...)` + `MemoryRouter` — renderiza componentes con router simulado
- `userEvent.setup()` — simula interacciones reales (click, type, select)
- `waitFor(...)` — espera por cambios asíncronos en el DOM
- `screen.getByText(...)` — queries semánticas orientadas al usuario

---

## Referencia a ERROR_REPORT.md {#errores}

Los bugs y problemas encontrados durante la revisión de código están documentados en:

**`C:\Users\enzoc\Desktop\api\api\ERROR_REPORT.md`**

Resumen rápido:
- **5 críticos** — incluyendo precios manipulables desde el cliente (C-01), race condition en stock (C-02), y Spring Boot 4.x inválido en pom.xml (C-05)
- **7 altos** — endpoint de checkout público sin auth, total de orden aceptado del cliente, URL incorrecta en Orders.jsx
- **8 medios** — falta de validación `@Valid` en productos, JWT sin longitud mínima, stock duplicado en localStorage
- **6 bajos** — logging ausente en handler genérico, clave localStorage inconsistente, n8n webhooks hardcodeados

Los tests de `OrderControllerTest` verifican explícitamente el bug C-01 (intento de manipulación de precios) y el bug C-03 (body sin campos requeridos). El bug C-02 (race condition) requiere tests de concurrencia que están fuera del alcance de tests de unidad estándar.
