# Plano de Refatoração Modular: Limite Estrito de 250 Linhas e Transposição de Regras Avançadas do ESLint para Java / Android

## 1. Objetivo
1. **Refatorar os 8 arquivos monolíticos** do projeto ElfJogo que atualmente ultrapassam o limite de 250 linhas (com arquivos de até 1.119 linhas), decompondo-os em classes coesas, modulares e desacopladas com **teto máximo estrito de 250 linhas por arquivo**.
2. **Remover a supressão temporária (`checkstyle-suppressions.xml`)** de todos os arquivos do código-fonte de produção (`src/main/java`), ativando a validação irrestrita de `FileLength <= 250` em 100% do projeto.
3. **Transpor e incorporar as regras de alto padrão da configuração ESLint do usuário** (Hexagonal/Boundaries, No-Secrets, Cognitive Complexity <= 12, Proibição de Mocks em Produção, Javadoc obrigatório, Max-Depth 4, Imutabilidade) diretamente para o ecossistema Gradle (Checkstyle, PMD, Spotless, ArchUnit e Android Lint).

---

### Mapeamento Direto: ESLint (Node/TS) vs. Java / Android Studio

| Regra no ESLint do Usuário | Equivalente em Java / Android Studio | Como será Implementado no Projeto |
| :--- | :--- | :--- |
| **`max-lines: { max: 250 }`** | **Checkstyle `FileLength`** (max: 250) | Bloqueio no `./gradlew checkstyle` se qualquer arquivo Java exceder 250 linhas. |
| **`max-lines-per-function: { max: 60-80 }`** | **Checkstyle `MethodLength`** (max: 50) | Bloqueio no Checkstyle se qualquer método ultrapassar 50 linhas. |
| **`max-params: 5`** | **Checkstyle `ParameterNumber`** (max: 5) | Limita parâmetros a 5 por método/construtor. |
| **`max-depth: 4`** | **Checkstyle `NestedIfDepth` (3) + `NestedForDepth` (2)** | Impede aninhamentos profundos e "código pirâmide". |
| **`sonarjs/cognitive-complexity: 12`** | **PMD `CognitiveComplexity` / `CyclomaticComplexity`** | Alerta e falha em métodos com complexidade cognitiva/ciclomática > 10. |
| **`boundaries/dependencies` (Hexagonal)** | **ArchUnit `LayeredArchitectureTest` & `HexagonalArchitectureTest`** | Testes de JUnit validando que `logic` não acessa `server` nem `android.view.*`. |
| **`no-restricted-syntax` (No Mocks in Prod)** | **Checkstyle `RegexpSinglelineJava` + ArchUnit** | Proíbe identificadores e literais contendo `mock` ou `dummy` em `src/main/java`. |
| **`no-secrets/no-secrets` & `ai-guard/no-hardcoded-secret`** | **Android Lint `AuthLeak` + PMD Security + Regexp** | Bloqueia chaves de API, senhas ou tokens literais no código. |
| **`ai-guard/no-sql-string-concat`** | **Android Lint `SqlInjection` + PMD Security** | Proíbe concatenação de strings em instruções SQL / queries. |
| **`perfectionist/sort-imports` & `sort-classes`** | **Spotless (`google-java-format`) + Checkstyle `ModifierOrder`** | Auto-formatação determinística, ordem canônica de modificadores (`public static final`). |
| **`jsdoc/require-jsdoc` & `require-param`** | **Checkstyle `JavadocType` & `JavadocMethod`** | Exige documentação semântica em classes e métodos públicos. |
| **`functional/no-let` / `immutable-data`** | **PMD `AvoidReassigningParameters` + Checkstyle `FinalLocalVariable`** | Incentiva imutabilidade de variáveis locais e parâmetros. |

---

### Diagnóstico dos Arquivos que Excedem 250 Linhas

| Arquivo Atual | Linhas Atuais | Motivo do Inchaço (Smell) | Estratégia de Modularização (<= 250 Linhas) |
| :--- | :---: | :--- | :--- |
| **`KinectTrackingEngine.java`** | **1.119** | Deus-Object contendo DTOs, Histograma 3D, Esqueleto, Gestos e Pipeline de Câmera. | Dividir em 5 classes coesas: <br>1. `KinectJoint.java` & `KinectTrackingResult.java` (Modelos)<br>2. `DepthHistogramSegmenter.java` (Histograma & Blobs)<br>3. `KinectSkeletonExtractor.java` (Extração 3D)<br>4. `KinectGestureClassifier.java` (Classificação de Gestos)<br>5. `KinectTrackingEngine.java` (Fachada leve <= 150 linhas) |
| **`MapActivity.java`** | **630** | Mistura ciclo de vida Android, ROS/RTAB-Map, sensores do Sanbot, WebServer e UI. | Dividir em 3 classes:<br>1. `MapHardwareCoordinator.java` (Sensores Sanbot & Gyro)<br>2. `MapNavigationPresenter.java` (Orquestração de waypoints)<br>3. `MapActivity.java` (Apenas binding de UI <= 200 linhas) |
| **`RobotGameFeedback.java`** | **434** | Feedback visual, auditivo (TTS), asas e expressões faciais num único arquivo. | Dividir em:<br>1. `RobotSpeechFeedback.java` (TTS e voz)<br>2. `RobotVisualFeedback.java` (Luzes LED e emoções)<br>3. `RobotMotionFeedback.java` (Asas e cabeça)<br>4. `RobotGameFeedback.java` (Fachada <= 100 linhas) |
| **`SensorFusionEngine.java`** | **328** | Odometria de rodas, radar de obstáculos, giroscópio e EKF no mesmo arquivo. | Dividir em:<br>1. `WheelOdometryProcessor.java`<br>2. `RadarObstacleDetector.java`<br>3. `SensorFusionEngine.java` (Fachada <= 140 linhas) |
| **`RobotWebServer.java`** | **270** | Roteamento HTTP NanoHTTPD misturado com serialização e segurança WASD. | Extrair `RobotWebRouter.java` e `RobotCommandPayload.java` (WebServer <= 130 linhas). |
| **`MapView.java`** | **259** | Renderização gráfica misturada com transformações de coordenadas do mapa. | Extrair `MapCoordinateTransformer.java` (MapView <= 170 linhas). |
| **`PoseDepthFusion.java`** | **263** | Fusão espacial de ML Kit Pose com mapa Astra Depth em método gigante. | Extrair `PoseJointInterpolator.java` (PoseDepthFusion <= 180 linhas). |
| **`FruitSlicerView.java`** | **251** | Partículas de efeito, lâmina de corte e animações misturadas na View. | Extrair `FruitBladeRenderer.java` (FruitSlicerView <= 160 linhas). |

---

## 2. Segurança
- **Proibição de Mocks e Dummies em Produção:** Implementação de regra no Checkstyle e ArchUnit para impedir que mocks vazem para pacotes de produção (`src/main/java`).
- **Prevenção de Hardcoded Secrets:** Checkstyle `RegexpSinglelineJava` bloqueando padrões de tokens, chaves e credenciais literais.
- **Fail-Secure e Sanitização de Comandos WASD:** Garantir que no `RobotWebServer` e no `RobotWebRouter` todas as entradas sejam validadas e protegidas contra injeções ou comandos nulos.
- **Isolamento de Concorrência:** Migração de threads isoladas em `ExecutorService` protegidas contra vazamentos de memória na destruição de Activities.

---

## 3. Arquitetura Escolhida: Padrão Facade + Single Responsibility (SRP)
Para refatorar as classes monolíticas sem quebrar os callers existentes (Activities e Motores de Jogo), adotaremos o padrão **Facade (GoF)**:
- A classe principal (`KinectTrackingEngine`, `RobotGameFeedback`, `SensorFusionEngine`) mantém suas assinaturas públicas de métodos estáveis, mas delega a execução para submódulos especializados e testáveis de até 150 linhas cada.
- Eliminação total de acoplamento direto de hardware na camada `logic`.

---

## 4. Interfaces e Contratos dos Novos Módulos

### Extração 1: Módulos de Rastreamento Kinect
- `com.felipe.elftemplate.tracking.model.KinectJoint` (DTO imutável, <= 50 linhas)
- `com.felipe.elftemplate.tracking.model.KinectTrackingResult` (DTO de resultado, <= 90 linhas)
- `com.felipe.elftemplate.tracking.segmentation.DepthHistogramSegmenter` (Cálculo de profundidade de pico, proximidade e histograma, <= 180 linhas)
- `com.felipe.elftemplate.tracking.skeleton.KinectSkeletonExtractor` (Mapeamento espacial 3D, <= 200 linhas)
- `com.felipe.elftemplate.tracking.gesture.KinectGestureClassifier` (Classificação de gestos com histerese, <= 180 linhas)

### Extração 2: Módulos de Feedback do Robô
- `com.felipe.elftemplate.movement.feedback.RobotSpeechFeedback` (Fala e TTS, <= 80 linhas)
- `com.felipe.elftemplate.movement.feedback.RobotVisualFeedback` (LEDs e Emoções faciais, <= 90 linhas)
- `com.felipe.elftemplate.movement.feedback.RobotMotionFeedback` (Braços e cabeça física, <= 120 linhas)

### Extração 3: Módulos de Fusão de Sensores
- `com.felipe.elftemplate.movement.fusion.WheelOdometryProcessor` (Odometria das rodas, <= 110 linhas)
- `com.felipe.elftemplate.movement.fusion.RadarObstacleDetector` (Matriz de radar e obstáculos, <= 130 linhas)

---

## 5. Alternativas e Trade-offs

| Abordagem | Vantagens | Desvantagens | Decisão |
| :--- | :--- | :--- | :--- |
| **Opção 1: Padrão Facade + SRP Modular (Recomendada)** | Respeita o teto de 250 linhas; não quebra compatibilidade com as Activities existentes; preserva contratos públicos. | Cria novos arquivos Java para acomodar as responsabilidades divididas. | **Adotada**. Máxima modularidade e segurança de regressão. |
| **Opção 2: Reescrita Completa em Kotlin** | Menor verbosidade em data classes. | Código legado do Sanbot SDK em Java 8 com callbacks em C++ NDK exigiria conversão arriscada neste momento. | **Descartada** para esta etapa. |
| **Opção 3: Aumentar o limite de linhas do Checkstyle para 1.000** | Não exige refatoração imediata. | Viola a exigência explícita do usuário de manter arquivos limpos e modularizados de no máximo 250 linhas. | **Descartada**. |
| **Opção 4: Supressão contínua em checkstyle-suppressions.xml** | Fácil manutenção. | Mantém o débito técnico invisível e não resolve a complexidade ciclomática nem o tamanho dos arquivos. | **Descartada**. |

---

## 6. Limites Estritos de Qualidade a serem Ativados
- **Tamanho Máximo de Arquivo:** 250 linhas (`FileLength` ativado sem supressões em código de produção).
- **Tamanho Máximo de Método:** 50 linhas (`MethodLength`).
- **Parâmetros por Método:** Máximo 5 parâmetros (`ParameterNumber`).
- **Profundidade Máxima de Aninhamento:** Máximo 3 níveis de `if` e 2 de `for` (`NestedIfDepth`, `NestedForDepth`).
- **Complexidade Ciclomática:** Máximo 10 por método.
- **Proibição de Mocks:** 0 ocorrências de `mock` ou `dummy` em classes de produção.

---

## 7. Estratégia TDD e Plano de Testes
1. **Preservação de Testes Existentes:** Todos os 64 testes unitários existentes (`MirrorGameEngineTest`, `TennisGameEngineTest`, etc.) devem passar com 100% de sucesso.
2. **Novos Testes Unitários dos Módulos Extraídos:**
   - `DepthHistogramSegmenterTest.java`: Testa cálculo de mediana e histerese de proximidade.
   - `KinectGestureClassifierTest.java`: Testa histerese de braços e detecção de agachamento/pulo.
   - `RobotSpeechFeedbackTest.java` e `RobotVisualFeedbackTest.java`: Testa despacho de comandos de feedback.
   - `WheelOdometryProcessorTest.java`: Testa integração de deslocamento e ângulo.
3. **Novos Testes ArchUnit:**
   - `NoMocksInProductionCodeTest.java`: Valida que nenhuma classe em `src/main/java` contém a palavra `mock` ou `dummy`.
   - `HexagonalArchitectureTest.java`: Valida o isolamento de camadas inspirado no ESLint `boundaries/dependencies`.
4. **Validação do Gate:** `./gradlew codeQuality` deve rodar limpo com 0 supressões de FileLength.

---

## 8. Etapas Técnicas de Execução
1. **Atualização das Regras do Checkstyle e PMD:**
   - Adicionar regras `NestedIfDepth`, `NestedForDepth`, `ParameterNumber (5)`, `RegexpSinglelineJava (No Mocks in Prod)`.
   - Atualizar `config/checkstyle/checkstyle-suppressions.xml` para remover as supressões de `FileLength`.
2. **Refatoração dos 8 Arquivos Monolíticos:**
   - Decompor `KinectTrackingEngine.java` em componentes menores no pacote `com.felipe.elftemplate.tracking`.
   - Decompor `RobotGameFeedback.java` no pacote `com.felipe.elftemplate.movement`.
   - Decompor `SensorFusionEngine.java` no pacote `com.felipe.elftemplate.movement`.
   - Modularizar `MapActivity.java`, `RobotWebServer.java`, `MapView.java`, `FruitSlicerView.java` e `PoseDepthFusion.java`.
3. **Execução de Formatação e Testes TDD:**
   - Executar `./gradlew spotlessApply`.
   - Executar `./gradlew testDebugUnitTest`.
   - Executar `./gradlew checkstyle` e `./gradlew pmd`.
4. **Validação Final do Gate Unificado:**
   - Executar `./gradlew codeQuality`.
   - Executar `./gradlew --stop`.

---

## 9. Perguntas Técnicas / Alinhamento
1. **Estrutura de Pacotes dos Módulos Extraídos:**
   - Deseja manter as classes extraídas no mesmo pacote das classes originais (ex: `tracking`, `movement`) ou prefere subpacotes especializados (ex: `tracking.gesture`, `tracking.segmentation`)? *(Recomendamos o mesmo pacote ou subpacotes diretos para manter visibilidade de pacote).*
2. **Ordem de Execução:**
   - Podemos executar a refatoração arquivo por arquivo, validando os testes unitários a cada etapa para garantir regressão zero?
