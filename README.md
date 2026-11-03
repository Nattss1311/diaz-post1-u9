# Recordatorios Citas Service

![CI](https://github.com/TU_USUARIO_GITHUB/diaz-post1-u9/actions/workflows/ci.yml/badge.svg)

> Post-contenido Unidad 9 — **Probar el Diseño: JUnit 5, Mockito, Test Doubles e Integración Continua**
> Patrones de Diseño de Software · Sexto Semestre · Universidad de Santander (UDES)

---

## 1. Descripción

Microservicio en **Spring Boot 4.1.2 con Java 21** que revisa qué citas médicas ocurren dentro de las próximas 24 horas y aún no han sido notificadas, y envía un recordatorio al paciente por su canal preferido (correo electrónico o SMS). Incluye además un recordatorio **urgente** por SMS para citas próximas no confirmadas.

El servicio central, `CitaReminderServiceImpl`, está diseñado desde el inicio con:

| Patrón / Principio | Elemento | Rol |
|---|---|---|
| **Strategy** (Unidad 4) | `ReminderChannel` → `EmailReminderChannel`, `SmsReminderChannel` | Cada canal de envío es una implementación intercambiable de la misma interfaz. |
| **Factory Method** (Unidad 2) | `ReminderChannelFactory` → `ReminderChannelFactoryImpl` | Selecciona en tiempo de ejecución la estrategia según `cita.getCanalPreferido()`. |
| **DIP** | Constructor de `CitaReminderServiceImpl` | Recibe repositorio, factory, canal urgente y `Clock` como abstracciones; no construye ningún colaborador con `new`. |
| **Composition Root** | Paquete `config/` (`ClockConfig`, `CanalUrgenteConfig`) | Único lugar donde se instancian dependencias concretas. |

> **Nota de versión.** La guía indica Spring Boot 3.3.x, rama que ya no está disponible en Spring Initializr. El proyecto se generó con Spring Boot 4.1.2, lo que implicó dos adaptaciones en las pruebas: `@MockBean` (eliminado en Boot 4) se reemplazó por su sucesor oficial `@MockitoBean`, y `@DataJpaTest` se importa desde su nuevo paquete `org.springframework.boot.data.jpa.test.autoconfigure`. El comportamiento de las pruebas es idéntico al descrito en la guía.

### Estructura

```
src/
├── main/java/com/clinica/recordatorios/
│   ├── RecordatoriosCitasServiceApplication.java
│   ├── domain/        Cita (entidad JPA), CanalNotificacion (enum EMAIL, SMS)
│   ├── repository/    CitaRepository (JpaRepository)
│   ├── channel/       ReminderChannel (Strategy), EmailReminderChannel, SmsReminderChannel,
│   │                  ReminderChannelFactory (Factory), ReminderChannelFactoryImpl
│   │   └── gateway/   NotificadorSmsGateway, TwilioNotificadorSmsGateway
│   ├── service/       CitaReminderService, CitaReminderServiceImpl, CitaNoEncontradaException
│   └── config/        ClockConfig, CanalUrgenteConfig   ← composition root
└── test/java/com/clinica/recordatorios/
    ├── service/       CitaReminderServiceImplTest        (unitarias, JUnit 5 + Mockito)
    ├── repository/    CitaRepositoryTest                 (@DataJpaTest + H2)
    └── CitaReminderServiceIntegrationTest                (@SpringBootTest + H2)
```

---

## 2. Parte 1 — Aislamiento y pruebas unitarias con JUnit 5 y Mockito

`CitaReminderServiceImplTest` aísla por completo a `CitaReminderServiceImpl`: el servicio se construye manualmente en `@BeforeEach` pasando un test double por cada parámetro del constructor. Esto solo es posible porque el servicio recibe **abstracciones** (Strategy y Factory) por constructor; ninguna prueba unitaria levanta Spring, base de datos ni proveedor de SMS.

Pruebas de `enviarRecordatoriosPendientes()`:

| Prueba | Qué demuestra |
|---|---|
| `enviarRecordatoriosPendientes_citaEnVentana_usaElCanalPreferido` | La Factory recibe `EMAIL`, la estrategia devuelta recibe el mensaje y la cita se guarda marcada como enviada. |
| `enviarRecordatoriosPendientes_citaConCanalSms_solicitaCanalSmsALaFactory` | La selección depende del canal preferido: con `SMS` nunca se pide `EMAIL`. |
| `enviarRecordatoriosPendientes_ventanaDeterministaSegunClockFijo_consultaLosLimitesExactos` | Con `ArgumentCaptor` se verifican los límites exactos de la ventana: `2026-08-10T09:00` → `2026-08-11T09:00`. |
| `enviarRecordatoriosPendientes_sinCitasPendientes_noConsultaLaFactory` | Sin citas no hay interacción con la Factory ni llamadas a `save()`. |

---

## 3. Parte 2 — Refactor DIP, integración y CI/CD

1. **Problema:** `enviarRecordatorioUrgente(Long citaId)` nació construyendo su canal con `new` dentro del método, lo que impedía sustituirlo en pruebas.
2. **Refactor:** la construcción se movió al composition root (`config/CanalUrgenteConfig.java`) y el servicio recibe `@Qualifier("canalUrgente") ReminderChannel` por constructor.
3. **Pruebas habilitadas por el refactor:** `enviarRecordatorioUrgente_citaExistente_usaCanalUrgenteInyectado`, `enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado` y `enviarRecordatorioUrgente_citaInexistente_lanzaCitaNoEncontradaException`.
4. **Integración:** `CitaRepositoryTest` (`@DataJpaTest`) valida la consulta derivada contra H2 real; `CitaReminderServiceIntegrationTest` (`@SpringBootTest`) ejecuta el flujo completo con el contexto de Spring, un `Clock` fijo `@Primary` y el gateway de SMS sustituido con `@MockitoBean`.
5. **CI:** `.github/workflows/ci.yml` ejecuta `mvn -B verify --no-transfer-progress` en cada push y pull request a `main`, y publica el reporte JaCoCo como artefacto.

---

## 4. Decisiones de diseño

### 4.1 Por qué Strategy facilitó las pruebas

`ReminderChannel` reduce cualquier mecanismo de envío (SMTP, API HTTP de SMS) a un único contrato propio: `enviar(Cita, String)`. Gracias a eso, **un solo `@Mock ReminderChannel` sirve para verificar cualquier canal**: las pruebas comprueban `verify(reminderChannelMock).enviar(eq(cita), anyString())` sin importar si la estrategia real es correo o SMS.

**Sin Strategy**, la lógica de envío estaría escrita en línea dentro del servicio. Probar la selección de canal obligaría a mockear librerías de bajo nivel de terceros (cliente SMTP, cliente HTTP) con APIs ajenas, más métodos y más frágiles, y cada canal nuevo exigiría reescribir las pruebas.

### 4.2 Por qué Factory Method facilitó las pruebas

`ReminderChannelFactory` saca del servicio la decisión "qué canal usar". Esa decisión deja de ser un `if/else` o `switch` interno y se convierte en una **interacción explícita y verificable**:

```java
verify(reminderChannelFactory).obtenerCanal(CanalNotificacion.EMAIL);
verify(reminderChannelFactory, never()).obtenerCanal(CanalNotificacion.EMAIL); // caso SMS
verifyNoInteractions(reminderChannelFactory);                                  // sin citas
```

**Sin Factory**, el condicional embebido solo sería observable indirectamente (qué se imprimió o qué se envió realmente), y no habría forma de afirmar que el servicio *no* consultó canales cuando no hay citas.

### 4.3 Matriz de Test Doubles: Mock vs. Stub vs. Fake

| Colaborador | Test double | Herramienta | ¿Controla respuesta? | ¿Verifica interacción? |
|---|---|---|---|---|
| `CitaRepository` | **Mock** | `@Mock` (Mockito) | Sí (`findBy…`, `findById`) | Sí (`save()` una vez / nunca) |
| `ReminderChannelFactory` | **Mock** | `@Mock` (Mockito) | Sí (devuelve la estrategia) | Sí (`obtenerCanal(EMAIL/SMS)`) |
| `ReminderChannel` (normal y urgente) | **Mock** | `@Mock` (Mockito) | Sí (`doThrow` para simular fallo) | Sí (`enviar(...)` con "URGENTE") |
| `Clock` | **Fake** | `Clock.fixed(...)` (JDK) | Implementación real congelada | No aplica |

- **`CitaRepository` → Mock, no Stub.** Un stub solo controlaría que `findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse` devuelva una lista. Pero el comportamiento que importa también incluye *efectos*: que `save()` se invoque exactamente una vez con la cita marcada como enviada, y que **nunca** se invoque cuando el gateway falla o no hay citas. Cuando una prueba necesita controlar respuestas **y** verificar interacciones, el double correcto es un Mock.
- **`ReminderChannelFactory` / `ReminderChannel` → Mock.** El comportamiento bajo prueba es precisamente la selección y uso de la estrategia. No hay un valor de retorno del servicio que lo revele (`enviar()` es `void`), así que solo se puede comprobar verificando la interacción. Además, el mock del canal urgente permite **inyectar fallos** (`doThrow`) que un objeto real no produciría de forma controlada.
- **`Clock` → Fake (del JDK), deliberadamente no mockeado con Mockito.** `Clock.fixed(Instant, ZoneId)` es una implementación real y completa de `java.time.Clock`: `LocalDateTime.now(clock)` funciona exactamente como en producción, solo que con el instante congelado. Mockear `Clock` obligaría a stubbear `instant()` y `getZone()` por separado, acoplaría la prueba a detalles internos de cómo `LocalDateTime.now` consulta al reloj, y se arriesgaría a un comportamiento distinto del real. El fake es más simple, más legible y más fiel. Su efecto se ve en la prueba de `ArgumentCaptor`: con `Clock.systemDefaultZone()` los límites cambiarían en cada ejecución; con el fake son exactos y reproducibles en cualquier máquina.

### 4.4 Diagnóstico y refactor DIP de `enviarRecordatorioUrgente`

**Diagnóstico (antes).** El método no era testeable por una sola línea:

```java
// ❌ ANTES — Paso 7 (viola DIP)
@Override
public void enviarRecordatorioUrgente(Long citaId) {
    Cita cita = citaRepository.findById(citaId)
            .orElseThrow(() -> new CitaNoEncontradaException(citaId));
    ReminderChannel canalUrgente = new SmsReminderChannel(new TwilioNotificadorSmsGateway()); // ← línea responsable
    canalUrgente.enviar(cita, "URGENTE: su cita es en menos de 2 horas y no ha sido confirmada");
    cita.setRecordatorioEnviado(true);
    citaRepository.save(cita);
}
```

El módulo de alto nivel (el servicio) dependía directamente de dos detalles concretos (`SmsReminderChannel` y `TwilioNotificadorSmsGateway`) que creaba él mismo. Como los objetos nacen *dentro* del método, Mockito no tiene ningún punto donde insertar un test double, y el escenario crítico **"el gateway de SMS falla → la cita no debe marcarse como enviada"** era imposible de probar en aislamiento. Por contraste, `enviarRecordatoriosPendientes()` sí era testeable desde la Parte 1 porque obtiene su canal de una `ReminderChannelFactory` recibida por constructor. Interceptar el `new` con `Mockito.mockConstruction()` habría sido posible técnicamente, pero habría tratado el síntoma con una herramienta más compleja en lugar de corregir el diseño.

**Refactor (después).** El `new` no desaparece: se traslada al único lugar donde es correcto, el composition root, y el servicio pasa a depender de la abstracción:

```java
// ✅ DESPUÉS — config/CanalUrgenteConfig.java
@Configuration
public class CanalUrgenteConfig {
    @Bean
    public ReminderChannel canalUrgente(NotificadorSmsGateway gateway) {
        return new SmsReminderChannel(gateway);
    }
}

// ✅ DESPUÉS — CitaReminderServiceImpl
public CitaReminderServiceImpl(CitaRepository citaRepository,
                               ReminderChannelFactory reminderChannelFactory,
                               @Qualifier("canalUrgente") ReminderChannel canalUrgente,
                               Clock clock) { ... }

@Override
public void enviarRecordatorioUrgente(Long citaId) {
    Cita cita = citaRepository.findById(citaId)
            .orElseThrow(() -> new CitaNoEncontradaException(citaId));
    canalUrgente.enviar(cita, "URGENTE: su cita es en menos de 2 horas y no ha sido confirmada");
    cita.setRecordatorioEnviado(true);
    citaRepository.save(cita);
}
```

| Aspecto | Antes (Paso 7) | Después (Paso 8) |
|---|---|---|
| Dependencia del servicio | Clases concretas (`SmsReminderChannel`, `TwilioNotificadorSmsGateway`) | Abstracción `ReminderChannel` |
| Dónde vive el `new` | Dentro de la lógica de negocio | `config/CanalUrgenteConfig` (composition root) |
| Punto de sustitución en pruebas | Ninguno | Parámetro del constructor |
| ¿Testeable el fallo del gateway? | No | Sí: `enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado` |
| Comportamiento externo | — | Idéntico |

La prueba `enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado` configura `doThrow(...)` sobre el mock del canal urgente y verifica que `save()` nunca se llama y que la cita sigue con `recordatorioEnviado = false`. Su existencia es la evidencia de que **la testabilidad es consecuencia directa de aplicar DIP**.

### 4.5 Por qué H2 real pero gateway de SMS mockeado en integración

En `CitaReminderServiceIntegrationTest` conviven una base de datos real (H2 en memoria) y un `@MockitoBean NotificadorSmsGateway`. No es inconsistente; cada decisión responde a la naturaleza del colaborador:

- **H2 es un Fake aceptable** de la base de datos: implementa el mismo contrato SQL/JPA que una base relacional real, arranca en milisegundos dentro de la JVM, no requiere infraestructura y es determinista. Permite comprobar de verdad que la consulta derivada de Spring Data y el `save()` persisten el estado esperado.
- **El proveedor de SMS no tiene un "H2 equivalente"** gratuito y determinista. Usarlo de verdad haría depender cada ejecución de credenciales, red, cuotas y disponibilidad de un tercero, y además enviaría mensajes reales. Por eso se sustituye solo el borde externo (`NotificadorSmsGateway`) con un mock, mientras el resto de la cadena —Factory → `SmsReminderChannel` → gateway— sí es la implementación real cableada por Spring.

El `Clock` del contexto se sustituye con un bean `@Primary` (`clockFijo`) definido en un `@TestConfiguration`, con un nombre distinto al bean `clock` de `ClockConfig` para respetar la prohibición de sobrescritura de beans de Spring Boot.

### 4.6 Por qué no hay contenedor de base de datos en CI

El workflow no declara ningún bloque `services:` (PostgreSQL, MySQL, etc.). Es posible porque todas las pruebas de persistencia e integración usan H2 embebido, que se levanta dentro del propio proceso de Maven. Si se hubiera probado contra una base real con Testcontainers, el runner necesitaría Docker disponible, el pipeline tardaría más en arrancar y habría más puntos de fallo ajenos al código. La elección de H2 en la Parte 2 simplifica directamente el `ci.yml` a cuatro pasos: checkout, JDK 21 con caché de Maven, `mvn verify` y publicación del reporte de cobertura.

---

## 5. Ejecutar las pruebas

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn test     # Compila y ejecuta las 9 pruebas (unitarias, @DataJpaTest y @SpringBootTest) con Surefire
mvn verify   # Todo lo anterior + genera el reporte de cobertura JaCoCo en target/site/jacoco/index.html
```

> Las tres clases de prueba terminan en `*Test`, por lo que Surefire las ejecuta todas en la fase `test`. `mvn verify` es el comando usado por el pipeline de CI porque además produce el reporte de cobertura.

| Clase | Tipo | Pruebas |
|---|---|---|
| `CitaReminderServiceImplTest` | Unitaria (Mockito, sin Spring) | 7 |
| `CitaRepositoryTest` | Integración de persistencia (`@DataJpaTest` + H2) | 1 |
| `CitaReminderServiceIntegrationTest` | Integración completa (`@SpringBootTest` + H2) | 1 |

---

## 6. Evidencias

### 6.1 Pruebas en verde (`mvn verify`)
![Pruebas en verde](docs/evidencias/01-pruebas-verde.png)

### 6.2 Pruebas en verde desde el IDE
![Pruebas en el IDE](docs/evidencias/02-pruebas-ide.png)

### 6.3 Reporte de cobertura JaCoCo
![Cobertura JaCoCo](docs/evidencias/03-cobertura-jacoco.png)

### 6.4 Pipeline de GitHub Actions en estado "Success"
![GitHub Actions Success](docs/evidencias/04-github-actions-success.png)

---

## 7. Herramientas utilizadas

| Categoría | Tecnología | Versión |
|---|---|---|
| Lenguaje | Java (Eclipse Temurin) | 21 |
| Framework | Spring Boot (Web MVC, Data JPA) | 4.1.2 |
| Persistencia | Spring Data JPA / Hibernate, H2 Database (en memoria) | gestionadas por Spring Boot |
| Productividad | Lombok | gestionada por Spring Boot |
| Pruebas | JUnit 5 (Jupiter), Mockito, Spring Boot Test (`@DataJpaTest`, `@SpringBootTest`, `@MockitoBean`) | gestionadas por Spring Boot |
| Cobertura | JaCoCo Maven Plugin | 0.8.13 |
| Build | Apache Maven | 3.9+ |
| CI/CD | GitHub Actions (`actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4`) | — |
| Control de versiones | Git, GitHub | — |

---

## 8. Conclusiones

Este laboratorio evidenció que la testabilidad no es una propiedad que se añade al final, sino una consecuencia directa del diseño. Strategy redujo cualquier canal de envío a una interfaz propia que un único mock puede representar, y Factory Method transformó una decisión condicional oculta en una interacción explícita y verificable; gracias a ambos, el servicio pudo probarse en completo aislamiento con Mockito y un `Clock.fixed` del JDK como fake, obteniendo resultados deterministas.

El caso de `enviarRecordatorioUrgente` lo confirmó por contraste: mientras el método construía su canal con `new`, el escenario de fallo del gateway era imposible de probar, y bastó aplicar DIP —mover la construcción al composition root e inyectar la abstracción por constructor— para habilitar esa prueba sin alterar el comportamiento externo. Finalmente, elegir H2 como fake de la base de datos y un mock solo para el proveedor externo de SMS permitió pruebas de integración fieles y rápidas, y un pipeline de integración continua sin infraestructura adicional.
