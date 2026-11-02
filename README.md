# Recordatorios Citas Service
![CI](https://github.com/[usuario]/diaz-post1-u9/actions/workflows/ci.yml/badge.svg)

## Descripción
Repositorio del post-contenido de la Unidad 9 de Patrones de Diseño de Software Sexto Semestre. Un único proyecto Spring Boot que envía recordatorios de citas por el canal preferido del paciente, construido desde el inicio con Strategy (ReminderChannel) y Factory Method (ReminderChannelFactory) inyectados por constructor.

## Parte 1 Pruebas Unitarias con JUnit 5 y Mockito
CitaReminderServiceImplTest aísla CitaReminderServiceImpl de sus tres colaboradores. Ver "Decisiones de diseño" para la justificación de por qué cada uno se mocked, se stubeó o se resolvió con un fake.

## Parte 2 Refactor DIP, Integración y CI/CD
enviarRecordatorioUrgente nació con un "new" hardcodeado que lo hacía imposible de testear; el refactor lo movió a config/CanalUrgenteConfig.java e inyectó la abstracción por constructor. CitaRepositoryTest (@DataJpaTest) y CitaReminderServiceIntegrationTest (@SpringBootTest) prueban la persistencia y el flujo completo con H2 y un Clock fijo. El pipeline .github/workflows/ci.yml ejecuta mvn verify en cada push.

## Decisiones de diseño
**Por qué Strategy facilitó las pruebas.** Sin ReminderChannel como interfaz, la lógica de envío (SMTP o SMS) estaría escrita en línea dentro del servicio, y probar la selección de canal exigiría mockear librerías de bajo nivel (cliente SMTP, cliente HTTP) en vez de una sola interfaz propia con un método enviar(). Con Strategy, un solo @Mock ReminderChannel basta para verificar CUALQUIER canal.

**Por qué Factory facilitó las pruebas.** Sin ReminderChannelFactory, el servicio tendría un if/else o un switch embebido decidiendo el canal, y esa decisión solo sería observable indirectamente (revisando qué se imprimió o qué se envió realmente). Con la Factory inyectada, la selección de canal se verifica como una interacción explícita: verify(reminderChannelFactory).obtenerCanal(EMAIL).

**Mock vs. Stub vs. Fake (Parte 1).** CitaRepository y ReminderChannelFactory/ReminderChannel se mockearon porque las pruebas verifican interacciones (save(), obtenerCanal()), no solo valores de retorno. Clock se resolvió con Clock.fixed(...), un fake real del JDK, porque mockearlo habría sido más complejo y menos fiel al comportamiento real que usar la implementación fija que Java ya provee para pruebas.

**El refactor DIP (Parte 2).** enviarRecordatorioUrgente() no se pudo testear mientras construía SmsReminderChannel con "new" dentro del método. Moverlo a canalUrgente() en CanalUrgenteConfig e inyectar ReminderChannel por constructor lo hizo testeable sin cambiar su comportamiento externo: la prueba enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado es la prueba de que la testabilidad es consecuencia directa de aplicar DIP.

## Ejecutar las pruebas
```bash
mvn test
mvn verify
Herramientas utilizadas
Java 21, Spring Boot 3.3, Spring Data JPA, H2, JUnit 5, Mockito
Apache Maven, GitHub Actions, Git, GitHub

Conclusiones
La correcta aplicación del Principio de Inversión de Dependencias (DIP) y patrones como Strategy y Factory es indispensable no solo para el desacoplamiento estructural, sino para viabilizar la inyección de Test Doubles. Externalizar la creación de objetos hacia el Composition Root habilitó la simulación de fallos y garantizó una cobertura profunda. Además, la adopción de herramientas como H2 para pruebas de integración agiliza el flujo en pipelines de CI sin sacrificar fidelidad funcional.