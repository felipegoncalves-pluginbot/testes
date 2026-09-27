# Documento de Instruções do Sanbot OpenSDK

# 1. Visão Geral

Este documento é destinado a desenvolvedores Android.

Este documento serve como um guia para desenvolvedores integrarem rapidamente o Sanbot OpenSDK. Este SDK fornece a aplicativos Android a capacidade de controlar a voz, movimento e outras funções relacionadas do robô.

**Descrição dos nomes de robôs no documento:**
- **Modelo B** = Elf
- **Modelo D** = King Kong
- **Robô de Desktop** = Mini Elf

---

# 2. Processo de Integração

## 2.1 Ambiente de Desenvolvimento

Este SDK pode ser desenvolvido usando ferramentas como Android Studio ou Eclipse. Recomenda-se que a versão do Android Studio seja 1.5 ou superior.
O JDK requerido é a versão 7 ou superior, e a `minSdkVersion` do Android SDK deve ser no mínimo 11.

## 2.2 Instruções de Configuração

Para atender às necessidades de desenvolvimento em diferentes ambientes, fornecemos três versões diferentes do SDK: `SanbotOpenSDK_XXX.aar`, `JarA` e `JarB`.
A diferença entre `JarA` e `JarB` é que `JarB` não fornece as classes base `BindBaseActivity`, `TopBindActivity` e `BindBaseService`. Em vez disso, é necessário usar a classe `BindBaseUtil` para se comunicar com o robô.

### 2.2.1 Instruções de Configuração do SDK em formato .aar

Primeiro, copie o arquivo `SanbotOpenSdk_XXX.aar` (onde XXX representa o número da versão) e o arquivo `gson-2.2.4.jar` para a pasta `libs` do seu módulo.
Adicione o seguinte conteúdo ao arquivo `build.gradle` do módulo do seu aplicativo:

```groovy
repositories {
    flatDir {
        dirs 'libs'
    }
}

dependencies {
    implementation(name:'SanbotOpenSdk_XXX', ext:'aar')
}
```

### 2.2.2 Instruções de Configuração do SDK em formato .jar

1.  Copie todos os arquivos do diretório `src/libs` da pasta `JarA` (ou `JarB`, dependendo de qual pacote Jar você precisa usar) para a pasta `libs` do seu projeto.
2.  Mescle os arquivos de recurso do diretório `src/res` da pasta `JarA` (ou `JarB`) com os arquivos de recurso do seu projeto.
3.  Adicione as seguintes permissões ao arquivo `AndroidManifest.xml` do seu projeto:

    ```xml
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    ```

4.  Se você estiver usando o Android Studio para desenvolvimento, lembre-se de configurar o caminho de leitura dos arquivos `.so`. Adicione o seguinte conteúdo ao arquivo `build.gradle` do seu módulo:

    ```groovy
    android {
        sourceSets {
            main {
                jniLibs.srcDirs = ['libs']
            }
        }
    }
    ```

## 2.3 Instruções de Ofuscação

Ao ofuscar um aplicativo que integra o SDK da Sanbot, é necessário garantir que os métodos relacionados ao SDK não sejam ofuscados. Adicione as seguintes regras de ofuscação (ProGuard):

```proguard
-keep class com.qihancloud.opensdk.** {*;}
-keep class com.sanbot.opensdk.**{*;}
-keep class com.sunbo.main.**{*;}

```

Isso garante que as classes do SDK não sejam ofuscadas, evitando exceções em tempo de execução, como falhas no controle do robô.

## 2.4 Precauções

1.  Atualmente, o processo de desenvolvimento requer o uso do nosso sistema de P&D para testes. O sistema de produção só suportará as funcionalidades atuais a partir da versão 2.21.
2.  O sistema não inclui o Google Services Framework, o que significa que todos os serviços fornecidos pelo Google não podem ser usados (como Google Maps, Google Login, etc.). Os desenvolvedores devem evitar o uso dos SDKs correspondentes da plataforma aberta do Google Android.
3.  Seja cauteloso ao escutar o broadcast de inicialização do sistema. O tempo de envio do broadcast de inicialização foi alterado para ser emitido na primeira vez que o sistema entra na área de trabalho após a inicialização. Antes de usar, considere cuidadosamente se isso afetará seu programa.
4.  O robô retornará automaticamente à área de trabalho após um período de inatividade do usuário. Se você não quiser que seu aplicativo seja fechado enquanto estiver aberto, use o SDK do sistema Android para habilitar a função de manter a tela sempre ligada. Código de referência:

    ```java
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    ```

---

# 3. Introdução Detalhada à Codificação

## 3.1 Descrição Básica do SDK

O SDK suporta o controle do robô apenas em componentes `Activity` e `Service`.

### 3.1.1 Descrição das Classes Herdadas de Activity

Todas as classes de `Activity` no projeto devem herdar de `BindBaseActivity` ou `TopBaseActivity`, sendo `TopBaseActivity` uma subclasse de `BindBaseActivity`. As semelhanças e diferenças entre `BindBaseActivity` e `TopBaseActivity` são as seguintes:

-   **Semelhanças:** Ambas exigem a sobrescrita do método abstrato `onMainServiceConnected()`, que é chamado apenas uma vez, na criação da `Activity`.
-   **Diferenças:** Páginas que herdam de `TopBaseActivity` exibirão a barra de status padrão do sistema no topo. Ao definir o layout, deve-se usar o método `setBodyView`, e o tema da `Application` deve ser um estilo `NoActionBar`.

```java
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.base.TopBaseActivity;
```

> **Nota:** Se você deseja controlar o robô imediatamente no início da `Activity`, o código de controle correspondente **deve** ser colocado no método `onMainServiceConnected()`. Colocá-lo em qualquer outro método do ciclo de vida não terá efeito! Além disso, é necessário registrar a `Activity` usando `register(SuaActivity.class)` no método `onCreate`, e esta chamada deve ser feita **antes** de `super.onCreate()`.

### 3.1.2 Descrição das Classes Herdadas de Service

*(Nota: O documento original pula a numeração 3.1.2, indo direto para 3.1.3. Ajustei para manter a sequência lógica.)*

Se for necessário controlar o robô a partir de um `Service`, você pode herdar da classe `BindBaseService`. Ao herdar de `BindBaseService`, é preciso sobrescrever o método abstrato `onMainServiceConnected()` (que é chamado apenas uma vez, na criação do `Service`). Também é necessário registrar o `Service` usando `register(SeuService.class)` no método `onCreate`, e esta chamada deve ser feita **antes** de `super.onCreate()`. Se o controle do robô for necessário assim que o `Service` for iniciado, o código de controle correspondente deve estar em `onMainServiceConnected()`, caso contrário, não funcionará.

**Exemplo de Código:**

```java
import com.sanbot.opensdk.base.BindBaseService;

public class SampleService extends BindBaseService {
    // ...
    @Override
    protected void onMainServiceConnected() {
        // código de controle aqui
    }
    // ...
    @Override
    public void onCreate() {
        register(SampleService.class);
        super.onCreate();
    }
    // ...
}
```

### 3.1.3 Descrição Especial do Pacote Jar do Modelo B

O pacote Jar do Modelo B é um SDK fornecido para resolver o problema de como controlar o robô quando o desenvolvedor não pode herdar de `BindBaseActivity` e `BindBaseService`. No pacote Jar do Modelo B, a classe `BindBaseUtil` é usada em seu lugar. Esta classe possui os seguintes métodos:

1.  **`BindBaseUtil(Activity activity)` ou `BindBaseUtil(Service service)`**
    Estes são os dois construtores da classe `BindBaseUtil`.

2.  **`public void connectService()`**
    Chame este método para iniciar a conexão com o controle principal do robô. A comunicação com o robô só é possível após uma conexão bem-sucedida. Este método deve ser chamado no método do ciclo de vida `onResume()` de uma `Activity` ou no método `onCreate()` de um `Service`.

3.  **`public void breakConnection()`**
    Chame este método para desconectar do controle principal do robô. Este método deve ser chamado no método do ciclo de vida `onStop()` de uma `Activity` ou no método `onDestroy()` de um `Service`.

4.  **`public void setOnConnectedListener(OnConnectedListener onConnectedListener)`**
    Este método escuta o status da conexão com o controle principal. Como a chamada a `connectService()` é uma operação assíncrona, este listener será chamado de volta quando a conexão for estabelecida com sucesso. Se você precisar executar ações imediatamente após a conexão, pode colocar o código relevante neste callback.

> **Atenção:** Os métodos `connectService()` e `breakConnection()` devem ser estritamente chamados nos métodos de ciclo de vida correspondentes, conforme especificado acima. Lembre-se de sempre chamar `breakConnection()` e não chame esses métodos em outros pontos do ciclo de vida!!!

5.  **`public void register(Class subClass)`**
    Chame este método no método `onCreate()` dos seus componentes `Activity` e `Service` para registrar o objeto `class` do componente atual. Esta chamada deve ser feita **antes** de `super.onCreate()`. Se o registro não for feito ou o objeto estiver incorreto, as funcionalidades de pré-processamento do SDK podem não funcionar corretamente.

### 3.1.4 Descrição do Valor de Retorno do Método `OperationResult`

*(Nota: O documento original lista este item como 3.1.3 também.)*

Todos os métodos de controle do robô retornam um parâmetro do tipo `OperationResult`. Este parâmetro fornece feedback sobre o resultado da execução do método: se foi bem-sucedido, o motivo da falha e o valor de retorno em caso de sucesso.

`OperationResult` possui os três métodos a seguir:

-   **`public int getErrorCode()`**: Retorna o resultado da execução do comando. `1` significa sucesso, e qualquer valor menor que `0` indica falha.
-   **`public String getDescription()`**: Retorna uma descrição do resultado do comando. Se o comando falhar, fornecerá uma explicação específica sobre o motivo da falha.
-   **`public String getResult()`**: Um campo adicional, geralmente usado para armazenar dados de feedback após a execução do comando.

---

## 3.2 Controle de Voz

**Processo de Integração:**

1.  Obtenha uma instância do `SpeechManager`:

```java
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.SpeechManager;

SpeechManager speechManager = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
```

2.  Use o objeto `speechManager` obtido para chamar os métodos correspondentes de controle.

### 3.2.1 Síntese de Voz

-   **Assinatura do Método:**
    `public OperationResult startSpeak(String text)`
-   **Descrição:**
    Método de síntese de voz, usado para fazer o robô falar o texto especificado. O idioma da voz sintetizada depende da configuração de idioma atual do sistema.
-   **Exemplo de Código:**
```java
speechManager.startSpeak("Este é um texto de teste.");
```

### 3.2.2 Síntese de Voz (com parâmetros especificados)

-   **Assinatura do Método:**
    `public OperationResult startSpeak(String text, SpeakOption speakOption)`
-   **Descrição:**
    Uma sobrecarga do método de síntese de voz que permite especificar o idioma, a velocidade e o tom da voz.
-   **Parâmetros:**
    `SpeakOption` possui os seguintes parâmetros:
    -   `languageType`: O idioma da voz sintetizada. Valores possíveis incluem `LAG_CHINESE` (Chinês) e `LAG_ENGLISH_US` (Inglês Americano).
    -   `speed`: A velocidade da voz, com um intervalo de 0 a 100.
    -   `intonation`: O tom da voz, com um intervalo de 0 a 100.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.beans.SpeakOption;

SpeakOption speakOption = new SpeakOption();
speakOption.setSpeed(70);
OperationResult operationResult = speechManager.startSpeak("Esta é uma frase de teste.", speakOption);
```

### 3.2.3 Hibernar

-   **Assinatura do Método:**
    `public OperationResult doSleep()`
-   **Descrição:**
    Coloca o robô em estado de hibernação, fazendo-o parar de falar.
-   **Exemplo de Código:**
```java
speechManager.doSleep();
```

### 3.2.4 Despertar

-   **Assinatura do Método:**
    `public OperationResult doWakeUp()`
-   **Descrição:**
    Ativa o estado de despertar do robô. Este método só pode ser chamado em uma `Activity`; não terá efeito se chamado em um `Service`.
-   **Exemplo de Código:**
```java
speechManager.doWakeUp();
```

### 3.2.5 Callback de Status de Despertar/Hibernar

-   **Assinatura do Método:**
    `public void setOnSpeechListener(SpeechListener speechListener)`
-   **Descrição:**
    `setOnSpeechListener` é uma interface de callback genérica para o controle de voz. O parâmetro passado determina qual função específica o callback irá tratar. Este callback é acionado quando o robô entra no estado de hibernação ou é despertado.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.speech.WakenListener;

speechManager.setOnSpeechListener(new WakenListener() {
    @Override
    public void onWakeUp() {
        // Callback para evento de despertar
    }

    @Override
    public void onSleep() {
        // Callback para evento de hibernar
    }

    @Override
    public void onWakeUpStatus(boolean b) {
        // Callback de status de despertar, b == true significa que foi despertado por uma palavra-chave
    }
});
```

### 3.2.6 Callback de Reconhecimento de Fala

-   **Assinatura do Método:**
    `public void setOnSpeechListener(SpeechListener speechListener)`
-   **Descrição:**
    > **Atenção:** Para compatibilidade com futuras funcionalidades semânticas, esta interface foi alterada a partir da versão 1.1.7.

    Esta interface fornece ao aplicativo de terceiros todo o texto reconhecido pelo robô, exceto as palavras de despertar. Durante este processo, o aplicativo pode decidir se ativa a função de "interceptação" (que impede o robô de processar ou responder ao texto reconhecido). Note que o robô não reconhece a fala do usuário enquanto está em estado de hibernação.

    Por padrão, a função de interceptação está desativada. Para ativá-la para a `Activity` atual, adicione a seguinte instrução de pré-processamento ao seu arquivo `AndroidManifest.xml` (consulte a seção 3.11 para mais detalhes):
```xml
<meta-data android:name="RECOGNIZE_MODE" android:value="1"/>
```
    > **Nota:** O tempo de execução do código dentro do método de callback `onRecognizeResult` não deve exceder 500ms, caso contrário, poderá afetar o funcionamento normal do sistema. Não é recomendado realizar operações de interceptação em um `Service`.

-   **Parâmetros:**
    O callback de reconhecimento de fala implementa a interface `RecognizeListener`, que requer a implementação do seguinte método:
    `boolean onRecognizeResult(Grammar grammar);`

    O usuário pode obter o texto reconhecido através de `grammar.getText()`. O valor de retorno de `onRecognizeResult` é um booleano. Este valor deve ser definido pelo desenvolvedor com base nos requisitos da aplicação. Se retornar `true`, o robô não fará mais nenhuma resposta subsequente a esse texto; se `false`, o robô continuará o processamento. O objeto `Grammar` contém o conteúdo da fala do usuário reconhecido e o resultado da análise semântica. Atualmente, a semântica é suportada apenas em sistemas que usam o motor de semântica da iFlytek. Para detalhes sobre a classe `Grammar`, consulte o "Documento de Instruções de Semântica Aberta da Sanbot".
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.speech.RecognizeListener;

speechManager.setOnSpeechListener(new RecognizeListener() {
    @Override
    public boolean onRecognizeResult(Grammar grammar) {
        System.out.print("Texto reconhecido pelo robô: " + grammar.getText());
        return true;
    }
});
```

### 3.2.7 Callback de Volume de Voz

-   **Assinatura do Método:**
    `public void setOnSpeechListener(SpeechListener speechListener)`
-   **Descrição:**
    Este método é chamado quando o robô detecta som no ambiente, retornando o volume do som detectado. Pode ser usado para determinar o estado da fala do usuário e medir o volume da sua voz. Este callback só é acionado quando o robô está no estado de despertar.
-   **Parâmetros:**
    Este callback também implementa a interface `RecognizeListener` e requer a implementação do método:
    `void onRecognizeVolume(int volume);`
    O valor de `volume` representa o volume do som reconhecido, com um intervalo de 0 a 30.

### 3.2.8 Verificar se o Robô Está Falando

-   **Assinatura do Método:**
    `public OperationResult isSpeaking()`
-   **Descrição:**
    Este método é usado para verificar se o robô está falando no momento.
-   **Valor de Retorno:**
    O método `getResult()` de `OperationResult` retornará o resultado da consulta. Um valor de `"1"` indica que o robô está falando, e `"0"` indica o contrário.
-   **Exemplo de Código:**
```java
OperationResult operationResult = speechManager.isSpeaking();
if (operationResult.getResult().equals("1")) {
    // TODO: O robô está falando
}
```
### 3.2.9 Pausar Síntese de Voz

-   **Assinatura do Método:**
    `public OperationResult pauseSpeak()`
-   **Descrição:**
    Este método é usado para pausar a síntese de voz atual do robô.

### 3.2.10 Continuar Síntese de Voz (não suportado em versões estrangeiras)

-   **Assinatura do Método:**
    `public OperationResult resumeSpeak()`
-   **Descrição:**
    Este método é usado para continuar a síntese de voz que foi pausada.

### 3.2.11 Parar Síntese de Voz (não suportado em versões estrangeiras)

-   **Assinatura do Método:**
    `public OperationResult stopSpeak()`
-   **Descrição:**
    Este método é usado para interromper completamente a síntese de voz atual do robô.

### 3.2.12 Callback de Status de Síntese de Voz

-   **Assinatura do Método:**
    `public void setOnSpeechListener(SpeechListener speechListener)`
-   **Descrição:**
    Este callback é acionado enquanto o robô está sintetizando voz.
-   **Parâmetros:**
    O callback de status de síntese de voz implementa a interface `SpeakListener`, que requer a implementação do método:
    `void onSpeakStatus(SpeakStatus speakStatus);`
    O método `onSpeakStatus` será chamado durante o processo de síntese de voz. O parâmetro `progress` da classe `SpeakStatus` indica o progresso atual da síntese da frase, com valores de `0 <= percent <= 100`.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.speech.SpeakListener;
import com.sanbot.opensdk.function.beans.speech.SpeakStatus;

speechManager.setOnSpeechListener(new SpeakListener() {
    @Override
    public void onSpeakStatus(SpeakStatus speakStatus) {
        System.out.print("Sintetizando voz, progresso: " + speakStatus.getProgress());
    }
});
```

### 3.2.13 Callback de Início de Reconhecimento

-   **Assinatura do Método:**
    `public void setOnSpeechListener(SpeechListener speechListener)`
-   **Descrição:**
    Este método é chamado quando o robô inicia o processo de reconhecimento de voz.
-   **Parâmetros:**
    Este callback também implementa a interface `RecognizeListener` e requer a implementação do método:
```java
@Override
public void onStartRecognize() {
    Log.i("Cris", "Callback de status de início de reconhecimento");
}
```

### 3.2.14 Callback de Fim de Reconhecimento

-   **Assinatura do Método:**
    `public void setOnSpeechListener(SpeechListener speechListener)`
-   **Descrição:**
    Este método é chamado quando o robô finaliza o processo de reconhecimento de voz.
-   **Parâmetros:**
    Este callback também implementa a interface `RecognizeListener` e requer a implementação do método:
```java
@Override
public void onStopRecognize() {
    Log.i("Cris", "Callback de status de fim de reconhecimento");
}
```

### 3.2.15 Callback de Erro de Reconhecimento

-   **Assinatura do Método:**
    `public void setOnSpeechListener(SpeechListener speechListener)`
-   **Descrição:**
    Este método é chamado quando ocorre um erro durante o reconhecimento de voz.
-   **Parâmetros:**
    Este callback também implementa a interface `RecognizeListener` e requer a implementação do método:
```java
@Override
public void onError(int engine, int errorCode) {
    Log.i("Cris", "onError: engine=" + engine + " errorCode=" + errorCode);
}
```
    -   `engine`: O motor de reconhecimento atual. `0` representa iFlytek, `1` representa Du Mí.
    -   `errorCode`: O código de erro do reconhecimento. Por exemplo, se `engine == 0` e `errorCode == 20005`, significa que ninguém está falando. Para outros códigos de erro, consulte a lista de códigos de erro da iFlytek.

### 3.2.16 Despertar e Selecionar Idioma de Reconhecimento

-   **Assinatura do Método:**
    `public OperationResult doWakeUp(WakeUpOption option)`
-   **Descrição:**
    Ativa o estado de despertar do robô. Este método só pode ser chamado em uma `Activity`; não terá efeito se chamado em um `Service`. Clientes internacionais podem precisar selecionar idiomas de diferentes países ao despertar para reconhecimento. Além de alterar o idioma padrão do sistema, agora é possível passar um `WakeUpOption` ao despertar para configurar o idioma de reconhecimento do robô.
-   **Exemplo de Código:**
    Os idiomas atualmente suportados por `WakeUpOption` são:
    -   `LAG_ARABIC_INTERNATIONAL` // Árabe
    -   `LAG_CHINESE_HK` // Chinês (Hong Kong)
    -   `LAG_DANISH` // Dinamarquês
    -   `LAG_ENGLISH_US` // Inglês (EUA)
    -   `LAG_FRENCH_FRANCE` // Francês
    -   `LAG_GERMAN` // Alemão
    -   `LAG_ITALIAN` // Italiano
    -   `LAG_JAPANESE` // Japonês
    -   `LAG_KOREAN` // Coreano
    -   `LAG_POLISH` // Polonês
    -   `LAG_PORTUGUESE_PORTUGAL` // Português
    -   `LAG_SPANISH_SPAIN` // Espanhol
    -   `LAG_TURKISH` // Turco

```jav
import com.sanbot.opensdk.function.beans.WakeUpOption;

WakeUpOption wakeUpOption = new WakeUpOption();
wakeUpOption.setLanguageType(WakeUpOption.LAG_ARABIC_INTERNATIONAL);
speechManager.doWakeUp(wakeUpOption);
```


---
## 3.3 Controle de Hardware

**Processo de Integração:**

1.  Obtenha uma instância do `HardWareManager`:

```java
import com.sanbot.opensdk.function.unit.HardWareManager;

HardWareManager hardWareManager = (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);
```

2.  Use o objeto `hardWareManager` obtido para chamar os métodos correspondentes de controle.

### 3.3.1 Controle da Luz Branca

-   **Assinatura do Método:** `public OperationResult switchWhiteLight(boolean isOpen)`
-   **Descrição:** Usado para ligar ou desligar a luz branca.
-   **Exemplo de Código:**
```java
hardWareManager.switchWhiteLight(true);
```

### 3.3.2 Controle das Luzes LED

-   **Assinatura do Método:** `public OperationResult setLED(LED led)`
-   **Descrição:** Controla as luzes LED do robô.
-   **Parâmetros:**
    O construtor da classe `LED` é: `LED(byte part, byte mode, byte delayTime, byte randomCount)`
    -   `part`: A parte do robô onde o LED está localizado.
    -   `mode`: O modo de controle do LED.
    -   `delayTime`: Usado apenas no modo de piscar, indica o intervalo de pisca (1-255, em unidades de 100ms).
    -   `randomCount`: Usado apenas no modo de piscar com cores aleatórias, indica o número de cores aleatórias (1-7).

    **Valores para `part`:**
    -   `LED.PART_ALL`: Todos os LEDs do robô.
    -   `LED.PART_WHEEL`: LEDs do chassi.
    -   `LED.PART_LEFT_HAND`: LED da asa esquerda.
    -   `LED.PART_RIGHT_HAND`: LED da asa direita.
    -   `LED.PART_LEFT_HEAD`: LED esquerdo da cabeça.
    -   `LED.PART_RIGHT_HEAD`: LED direito da cabeça.

    **Valores para `mode`:**
    -   `LED.MODE_CLOSE`: Desligar.
    -   `LED.MODE_WHITE`, `_RED`, `_GREEN`, `_YELLOW`, `_PINK`, `_PURPLE`, `_BLUE`: Cores fixas.
    -   `LED.MODE_FLICKER_WHITE`, `_RED`, etc.: Cores piscando.
    -   `LED.MODE_FLICKER_RANDOM`: Pisca com cores aleatórias.
-   **Exemplo de Código:**
```java
hardWareManager.setLED(new LED(LED.PART_ALL, LED.MODE_FLICKER_RANDOM, 10, 3));
```

### 3.3.3 Callback de Evento de Toque

-   **Assinatura do Método:** `public void setOnHareWareListener(HardWareListener hareWareListener)`
-   **Descrição:** Este callback é acionado quando um sensor de toque do robô é ativado.
-   **Parâmetros:** Implementa a interface `TouchSensorListener`, com o método `void onTouch(int part)`, onde `part` (1-13) indica a parte tocada.
    -   **1-2:** Queixo (direito/esquerdo)
    -   **3-4:** Peito (direito/esquerdo)
    -   **5-6:** Nuca (esquerda/direita)
    -   **7-8:** Costas (esquerda/direita)
    -   **9-10:** Mãos (esquerda/direita)
    -   **11-13:** Topo da cabeça (centro, frente direita, frente esquerda)
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.hardware.TouchSensorListener;

hardWareManager.setOnHareWareListener(new TouchSensorListener() {
    @Override
    public void onTouch(int part) {
        if (part >= 11 && part <= 13) {
            touchTv.setText("Você tocou na cabeça");
        }
    }
});
```

### 3.3.4 Localização da Fonte Sonora

-   **Assinatura do Método:** `public void setOnHareWareListener(HardWareListener hareWareListener)`
-   **Descrição:** Acionado quando o robô é despertado por voz, indicando a direção da fonte sonora.
-   **Parâmetros:** Implementa a interface `VoiceLocateListener`, com o método `void voiceLocateResult(int angle)`, onde `angle` é o ângulo de desvio da fonte sonora em relação à frente da cabeça (sentido horário).
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.hardware.VoiceLocateListener;

hardWareManager.setOnHareWareListener(new VoiceLocateListener() {
    @Override
    public void voiceLocateResult(int angle) {
        // TODO: Processar o ângulo da fonte sonora
    }
});
```

### 3.3.5 Detecção PIR

-   **Assinatura do Método:** `public void setOnHareWareListener(HardWareListener hareWareListener)`
-   **Descrição:** Acionado quando os módulos PIR (frontal e traseiro) detectam alguém passando ou saindo.
-   **Parâmetros:** Implementa a interface `PIRListener`, com o método `void onPIRCheckResult(boolean isChecked, int part)`.
    -   `isChecked`: `true` se alguém foi detectado, `false` se alguém saiu.
    -   `part`: `1` para PIR frontal, `2` para PIR traseiro.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.hardware.PIRListener;

hardWareManager.setOnHareWareListener(new PIRListener() {
    @Override
    public void onPIRCheckResult(boolean isCheck, int part) {
        System.out.print((part == 1 ? "Frontal" : "Traseiro") + " PIR acionado");
    }
});
```

### 3.3.6 Callback do Sensor Infravermelho (Medição de Distância)

- **Assinatura do Método:** `public void setOnHareWareListener(HardWareListener hareWareListener)`
- **Descrição:** Retorna a distância medida pelos 18 emissores infravermelhos do robô até obstáculos.
- **Parâmetros:** Implementa a interface `InfrareListener`, com o método `void infrareDistance(int part, int distance)`.
    - `part`: O sensor infravermelho específico (**1-17**), correspondendo ao diagrama no documento:
		Sensores de queda:
        1. **Frente, Abaixo:** Ponto central inferior, ligeiramente à direita.
        2. **Frente, Abaixo**: Ponto central inferior, ligeiramente à esquerda.
        Sensores de obstáculo no chão:
        3. **Frente, Base:** Extremo direito.
        4. **Frente, Base:** Segundo ponto da direita.
        5. **Frente, Base:** Quarto ponto da direita.
        6. **Frente, Base:** Ponto central-direito.
        7. **Frente, Base:** Ponto central-esquerdo.
        8. **Frente, Base:** Quarto ponto da esquerda.
        9. **Frente, Base:** Segundo ponto da esquerda.
        Sensores de obstáculo a frente:
        10. **Frente, Base:** Extremo esquerdo.
        11. **Tronco Inferior:** Lado esquerdo do centro.
        12. **Tronco Inferior:** Lado direito do centro.
        13. **Tronco Central:** Centro do peito (sobre a grade do alto-falante).
        14. **Braço Direito:** Lado esquerdo, superior (ombro esquerdo).
        15. **Braço Direito:** Lado esquerdo, abaixo do ponto 14 (cintura esquerda).
        16. **Braço Esquerdo:** Lado direito, superior (ombro direito).
        17. **Braço Esquerdo** Lado direito, abaixo do ponto 16 (cintura direita).

    - `distance`: A distância até o obstáculo em centímetros.

- **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.hardware.InfrareListener;

hardWareManager.setOnHareWareListener(new InfrareListener() {
    @Override
    public void infrareDistance(int part, int distance) {
        // A distância 0 cm é tipicamente ignorada, ou significa 'sem detecção'/'fora de alcance'.
        if (distance != 0) {
            System.out.println("Distância do sensor " + part + ": " + distance + " cm");
            // Adicione lógica aqui para navegação ou reação
        }
    }
});
```
### 3.3.7 Ativar/Desativar Filtro de Linha Preta

-   **Assinatura do Método:** `public OperationResult switchBlackLineFilter(boolean isOpen)`
-   **Descrição:** Ativa um filtro para evitar que linhas pretas largas ou fendas no chão interfiram no sensor de distância e parem as rodas. **Atenção:** Ativar esta função desativa a proteção contra quedas.

### 3.3.8 Definir Brilho da Luz Branca

-   **Assinatura do Método:** `public OperationResult setWhiteLightLevel(int level)`
-   **Descrição:** Define o brilho da luz branca. `level` pode ser 1 (economia), 2 (suave) ou 3 (brilhante).

### 3.3.9 Callback de Funções do Giroscópio

-   **Assinatura do Método:** `public void setOnHareWareListener(HardWareListener hareWareListener)`
-   **Descrição:** Retorna os dados de ângulo medidos pelo giroscópio.
-   **Parâmetros:** Implementa a interface `GyroscopeListener`, com o método `void gyroscopeData(float driftAngle, float elevationAngle, float rollAngle)`.
    -   `driftAngle`: Ângulo de guinada.
    -   `elevationAngle`: Ângulo de arfagem.
    -   `rollAngle`: Ângulo de rolagem.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.hardware.GyroscopeListener;

hardWareManager.setOnHareWareListener(new GyroscopeListener() {
    @Override
    public void gyroscopeData(float driftAngle, float elevationAngle, float rollAngle) {
        // TODO: Processar dados do giroscópio
    }
});
```

### 3.3.10 Detecção de Obstáculos

-   **Assinatura do Método:** `public void setOnHareWareListener(HardWareListener hareWareListener)`
-   **Descrição:** Reporta o status de detecção de obstáculos quando os sensores de distância no chassi, asas e corpo detectam algo próximo.
-   **Parâmetros:** Implementa a interface `ObstacleListener`, com o método `void onObstacleStatus(boolean status)`.
    -   `status`: `true` se um obstáculo foi detectado, `false` se o ambiente está livre.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.hardware.ObstacleListener;

hardWareManager.setOnHareWareListener(new ObstacleListener() {
    @Override
    public void onObstacleStatus(boolean b) {
        if (b) {
            Log.w("info", "onObstacleStatus: Obstáculo detectado!");
        } else {
            Log.i("info", "onObstacleStatus: Ambiente livre.");
        }
    }
});
```
---
## 3.4 Controle de Movimento da Cabeça

**Processo de Integração:**

1.  Obtenha uma instância do `HeadMotionManager`:

```java
import com.sanbot.opensdk.function.unit.HeadMotionManager;

HeadMotionManager headMotionManager = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
```

2.  Use o objeto `headMotionManager` obtido para chamar os métodos correspondentes de controle.

### 3.4.1 Movimento de Ângulo Relativo

-   **Assinatura do Método:** `public OperationResult doRelativeAngleMotion(RelativeAngleHeadMotion relativeAngleHeadMotion)`
-   **Descrição:** Controla a cabeça do robô para girar um determinado ângulo em uma direção específica, a partir da sua posição atual.
-   **Parâmetros:** A classe `RelativeAngleHeadMotion` tem dois parâmetros: `action` e `angle`.
    -   `action`: O modo de movimento da cabeça.
    -   `angle`: O ângulo relativo do movimento.

    **Valores para `action`:**
    -   `RelativeAngleHeadMotion.ACTION_UP`, `_DOWN`, `_LEFT`, `_RIGHT`: Movimentos direcionais.
    -   `RelativeAngleHeadMotion.ACTION_LEFTUP`, `_RIGHTUP`, `_LEFTDOWN`, `_RIGHTDOWN`: Movimentos diagonais.
    -   `RelativeAngleHeadMotion.ACTION_STOP`: Parar movimento.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.beans.headmotion.RelativeAngleHeadMotion;

RelativeAngleHeadMotion relativeAngleHeadMotion = new RelativeAngleHeadMotion(
	RelativeAngleHeadMotion.ACTION_RIGHT, 30
);
headMotionManager.doRelativeAngleMotion(relativeAngleHeadMotion);
```

### 3.4.2 Movimento de Ângulo Absoluto

-   **Assinatura do Método:** `public OperationResult doAbsoluteAngleMotion(AbsoluteAngleHeadMotion absoluteAngleHeadMotion)`
-   **Descrição:** Controla a cabeça do robô para girar até uma posição de ângulo absoluto especificada.
    -   **Ângulo horizontal:** 0 a 180 graus (da esquerda para a direita).
    -   **Ângulo vertical:** 7 a 30 graus (de baixo para cima).
-   **Parâmetros:** A classe `AbsoluteAngleHeadMotion` tem dois parâmetros: `action` e `angle`.
    -   `action`: O modo de movimento da cabeça.
    -   `angle`: O ângulo absoluto do movimento.

    **Valores para `action`:**
    -   `AbsoluteAngleHeadMotion.ACTION_VERTICAL`: Movimento vertical.
    -   `AbsoluteAngleHeadMotion.ACTION_HORIZONTAL`: Movimento horizontal.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.beans.headmotion.AbsoluteAngleHeadMotion;

AbsoluteAngleHeadMotion absoluteAngleHeadMotion = new AbsoluteAngleHeadMotion(
	AbsoluteAngleHeadMotion.ACTION_HORIZONTAL, 130
);
headMotionManager.doAbsoluteAngleMotion(absoluteAngleHeadMotion);
```

### 3.4.3 Trava Central Horizontal

-   **Assinatura do Método:** `public OperationResult doHorizontalCenterLockMotion()`
-   **Descrição:** Gira a cabeça do robô para a posição de 90 graus na horizontal e trava o motor nessa direção (após a trava, a cabeça não pode ser girada manualmente na horizontal).
-   **Exemplo de Código:**
```java
headMotionManager.doHorizontalCenterLockMotion();
```

### 3.4.4 Operação de Localização por Ângulo Absoluto

-   **Assinatura do Método:** `public OperationResult doAbsoluteLocateMotion(LocateAbsoluteAngleHeadMotion locateAbsoluteAngleHeadMotion)`
-   **Descrição:** Move a cabeça para uma posição de ângulo absoluto e permite decidir se os motores devem ser travados após o movimento. Diferente do método de movimento absoluto, este permite travar os motores e especifica diretamente os ângulos horizontal e vertical.
-   **Parâmetros:** A classe `LocateAbsoluteAngleHeadMotion` tem três parâmetros: `action`, `horizontalAngle` e `verticalAngle`.
    -   `action`: Modo de travamento do motor.
    -   `horizontalAngle`, `verticalAngle`: Ângulos absolutos.

    **Valores para `action`:**
    -   `LocateAbsoluteAngleHeadMotion.ACTION_NO_LOCK`: Não travar.
    -   `LocateAbsoluteAngleHeadMotion.ACTION_HORIZONTAL_LOCK`: Travar na horizontal.
    -   `LocateAbsoluteAngleHeadMotion.ACTION_VERTICAL_LOCK`: Travar na vertical.
    -   `LocateAbsoluteAngleHeadMotion.ACTION_BOTH_LOCK`: Travar em ambas as direções.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.beans.headmotion.LocateAbsoluteAngleHeadMotion;

LocateAbsoluteAngleHeadMotion motion = new LocateAbsoluteAngleHeadMotion(
	LocateAbsoluteAngleHeadMotion.ACTION_BOTH_LOCK, 30, 15
);
headMotionManager.doAbsoluteLocateMotion(motion);
```

### 3.4.5 Operação de Localização por Ângulo Relativo

-   **Assinatura do Método:** `public OperationResult doRelativeLocateMotion(LocateRelativeAngleHeadMotion locateRelativeAngleHeadMotion)`
-   **Descrição:** Move a cabeça em um ângulo relativo a partir da posição atual e permite decidir se os motores devem ser travados após o movimento.
-   **Parâmetros:** A classe `LocateRelativeAngleHeadMotion` tem cinco parâmetros: `action`, `horizontalAngle`, `verticalAngle`, `horizontalDirection` e `verticalDirection`.
    -   `action`: Modo de travamento.
    -   `horizontalAngle`, `verticalAngle`: Ângulos relativos.
    -   `horizontalDirection`, `verticalDirection`: Direção do movimento.

    **Valores para `action`:**
    -   Mesmos do item 3.4.4.

    **Valores para direções:**
    -   `LocateRelativeAngleHeadMotion.DIRECTION_LEFT`, `_RIGHT`: Horizontal.
    -   `LocateRelativeAngleHeadMotion.DIRECTION_UP`, `_DOWN`: Vertical.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.beans.headmotion.LocateRelativeAngleHeadMotion;

LocateRelativeAngleHeadMotion motion = new LocateRelativeAngleHeadMotion(
    LocateRelativeAngleHeadMotion.ACTION_BOTH_LOCK, (byte) 30, (byte) 15,
    LocateRelativeAngleHeadMotion.DIRECTION_LEFT,
    LocateRelativeAngleHeadMotion.DIRECTION_UP
);
headMotionManager.doRelativeLocateMotion(motion);
    ```
---
## 3.5 Controle de Movimento das Asas

**Processo de Integração:**

1.  Obtenha uma instância do `WingMotionManager`:

```java
import com.sanbot.opensdk.function.unit.WingMotionManager;

WingMotionManager wingMotionManager = (WingMotionManager) getUnitManager(FuncConstant.WINGMOTION_MANAGER);
```

2.  Use o objeto `wingMotionManager` obtido para chamar os métodos correspondentes de controle.

### 3.5.1 Movimento sem Ângulo

-   **Assinatura do Método:** `public OperationResult doNoAngleMotion(NoAngleWingMotion noAngleWingMotion)`
-   **Descrição:** Controla as asas do robô para realizar movimentos sem ângulo, como levantar a mão ou retornar à posição inicial.
-   **Parâmetros:** `NoAngleWingMotion` tem três parâmetros: `action`, `part` e `speed`.
    -   `action`: Modo de rotação da asa.
    -   `part`: Posição da asa (esquerda/direita).
    -   `speed`: Velocidade do movimento (1-10).

    **Valores para `action`:**
    -   `NoAngleWingMotion.ACTION_UP`, `_DOWN`: Girar para cima/baixo.
    -   `NoAngleWingMotion.ACTION_STOP`: Parar rotação.
    -   `NoAngleWingMotion.ACTION_RESET`: Retornar à posição padrão (vertical para baixo).

    **Valores para `part`:**
    -   `NoAngleWingMotion.PART_LEFT`, `_RIGHT`, `_BOTH`: Esquerda, direita ou ambas.
-   **Exemplo de Código:**
```java


NoAngleWingMotion noAngleWingMotion = new NoAngleWingMotion(
    NoAngleWingMotion.PART_BOTH, 5, NoAngleWingMotion.ACTION_UP
);
wingMotionManager.doNoAngleMotion(noAngleWingMotion);
    ```

### 3.5.2 Movimento de Ângulo Relativo

-   **Assinatura do Método:** `public OperationResult doRelativeAngleMotion(RelativeAngleWingMotion relativeAngleWingMotion)`
-   **Descrição:** Gira a asa do robô em um determinado ângulo a partir da sua posição atual.
-   **Parâmetros:** `RelativeAngleWingMotion` tem quatro parâmetros: `action`, `part`, `speed` e `angle`.
    -   `action`: Modo de rotação.
    -   `part`: Posição da asa.
    -   `speed`: Velocidade (1-8).
    -   `angle`: Ângulo relativo (0-270, aumenta no sentido anti-horário).

    **Valores para `action`:**
    -   `RelativeAngleWingMotion.ACTION_UP`, `_DOWN`: Girar para cima/baixo.

    **Valores para `part`:**
    -   `RelativeWingMotion.PART_LEFT`, `_RIGHT`, `_BOTH`.
-   **Exemplo de Código:**
```java
RelativeAngleWingMotion motion = new RelativeAngleWingMotion(
    RelativeAngleWingMotion.PART_LEFT, 5, RelativeAngleWingMotion.ACTION_UP, 30
);
wingMotionManager.doRelativeAngleMotion(motion);
```

### 3.5.3 Movimento de Ângulo Absoluto

-   **Assinatura do Método:** `public OperationResult doAbsoluteAngleMotion(AbsoluteAngleWingMotion absoluteAngleWingMotion)`
-   **Descrição:** Gira a asa do robô para uma posição de ângulo absoluto.
-   **Parâmetros:** `AbsoluteAngleWingMotion` tem três parâmetros: `speed`, `part` e `angle`.
    -   `speed`: Velocidade (1-8).
    -   `part`: Posição da asa.
    -   `angle`: Ângulo absoluto (0-270, aumenta no sentido anti-horário).

    **Valores para `part`:**
    -   `AbsoluteAngleWingMotion.PART_LEFT`, `_RIGHT`, `_BOTH`.
-   **Exemplo de Código:**
```java
AbsoluteAngleWingMotion motion = new AbsoluteAngleWingMotion(
    AbsoluteAngleWingMotion.PART_LEFT, 5, 0
);
wingMotionManager.doAbsoluteAngleMotion(motion);
```
---
## 3.6 Controle de Movimento das Rodas

**Processo de Integração:**

1.  Obtenha uma instância do `WheelMotionManager`:

```java
import com.sanbot.opensdk.function.unit.WheelMotionManager;

WheelMotionManager wheelMotionManager = (WheelMotionManager) getUnitManager(FuncConstant.WHEELMOTION_MANAGER);
```

2.  Use o objeto `wheelMotionManager` obtido para chamar os métodos de controle.

### 3.6.1 Movimento sem Ângulo

-   **Assinatura do Método:** `public OperationResult doNoAngleMotion(NoAngleWheelMotion noAngleWheelMotion)`
-   **Descrição:** Controla as rodas para movimentos sem ângulo, como ir para frente, para trás ou girar.
-   **Parâmetros:** `NoAngleWheelMotion` tem três parâmetros: `speed`, `action`, e `duration`.
    -   `speed`: Velocidade do movimento (1-10).
    -   `action`: Modo de movimento.
    -   `duration`: Duração do movimento (em unidades de 100ms). Se `0`, o movimento continua até receber um comando de parada.

    **Valores para `action`:**
    -   `NoAngleWheelMotion.ACTION_FORWARD`: Mover para frente.
    -   `NoAngleWheelMotion.ACTION_LEFT`, `_RIGHT`: Girar no lugar para a esquerda/direita.
    -   `NoAngleWheelMotion.ACTION_TURN_LEFT`, `_TURN_RIGHT`: Fazer curva para a esquerda/direita.
    -   `NoAngleWheelMotion.ACTION_STOP_TURN`: Parar de girar.
    -   `NoAngleWheelMotion.ACTION_STOP_RUN`: Parar de andar.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;

NoAngleWheelMotion motion = new NoAngleWheelMotion(
    NoAngleWheelMotion.ACTION_FORWARD_RUN, 5, 3000
);
wheelMotionManager.doNoAngleMotion(motion);
```

### 3.6.2 Movimento de Ângulo Relativo

-   **Assinatura do Método:** `public OperationResult doRelativeAngleMotion(RelativeAngleWheelMotion relativeAngleWheelMotion)`
-   **Descrição:** Gira o robô em um ângulo relativo à sua posição atual.
-   **Parâmetros:** `RelativeAngleWheelMotion` tem três parâmetros: `speed`, `action`, e `angle`.
    -   `speed`: Velocidade (1-10).
    -   `action`: Modo de movimento.
    -   `angle`: Ângulo relativo da rotação.

    **Valores para `action`:**
    -   `RelativeAngleWheelMotion.TURN_LEFT`, `_TURN_RIGHT`: Girar para a esquerda/direita.
    -   `RelativeAngleWheelMotion.TURN_STOP`: Parar de girar.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.beans.wheelmotion.RelativeAngleWheelMotion;

RelativeAngleWheelMotion motion = new RelativeAngleWheelMotion(
    RelativeAngleWheelMotion.TURN_LEFT, 5, 90
);
wheelMotionManager.doRelativeAngleMotion(motion);
```

### 3.6.3 Movimento por Distância

-   **Assinatura do Método:** `public OperationResult doDistanceMotion(DistanceWheelMotion distanceWheelMotion)`
-   **Descrição:** Move o robô por uma distância específica em uma direção.
-   **Parâmetros:** `DistanceWheelMotion` tem três parâmetros: `speed`, `action`, e `distance`.
    -   `speed`: Velocidade (1-10).
    -   `action`: Modo de movimento.
    -   `distance`: Distância a ser percorrida (em cm).

    **Valores para `action`:**
    -   `DistanceWheelMotion.ACTION_FORWARD_RUN`: Mover para frente.
    -   `DistanceWheelMotion.ACTION_STOP_RUN`: Parar de andar.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.beans.wheelmotion.DistanceWheelMotion;

DistanceWheelMotion motion = new DistanceWheelMotion(
    DistanceWheelMotion.ACTION_FORWARD_RUN, 5, 100
);
wheelMotionManager.doDistanceMotion(motion);
```

### 3.6.4 Callback de Status de Movimento das Rodas

-   **Descrição:** Após o movimento das rodas, este callback é acionado para informar o status.
-   **Parâmetros:** O status `s` é retornado. Se `s` for `"0"`, o movimento parou. Qualquer outro valor indica que está em movimento.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.WheelMotionManager;

wheelMotionManager.setWheelMotionListener(new WheelMotionManager.WheelMotionListener() {
    @Override
    public void onWheelStatus(String s) {
        Log.i("Cris", "onWheelStatus: s=" + s);
    }
});
```

## 3.7 Gerenciamento do Sistema

**Condições de Acesso:**
Algumas funções só podem ser acessadas por aplicativos assinados pelo sistema. As funções que exigem assinatura do sistema serão indicadas abaixo.

**Processo de Integração:**

1.  Obtenha uma instância do `SystemManager`:

```java
import com.sanbot.opensdk.function.unit.SystemManager;

SystemManager systemManager = (SystemManager) getUnitManager(FuncConstant.SYSTEM_MANAGER);
```

2.  Use o objeto `systemManager` obtido para chamar os métodos de controle correspondentes.

### 3.7.1 Obter ID do Dispositivo

-   **Assinatura do Método:** `public String getDeviceId()`
-   **Descrição:** Obtém o ID de dispositivo do robô atual.
-   **Exemplo de Código:**
```java
systemManager.getDeviceId();
```

### 3.7.2 Retornar à Interface de Animação do Protetor de Tela

-   **Assinatura do Método:** `public OperationResult doHomeAction()`
-   **Descrição:** Faz a tela do robô voltar para a interface de animação do protetor de tela da área de trabalho.
-   **Valor de Retorno:** O `ErrorCode` do `OperationResult` pode retornar `ErrorCode.FAIL_APP_HAS_LOCKED`, indicando que um aplicativo está atualmente em estado de bloqueio e não é possível retornar à tela de animação.
-   **Exemplo de Código:**
```java
systemManager.doHomeAction();
```

### 3.7.3 Controle de Expressões

-   **Assinatura do Método:** `public OperationResult showEmotion(EmotionsType emotion)`
-   **Descrição:** Faz o robô exibir uma expressão facial específica. Esta função só funciona quando a interface atual é a de reconhecimento facial.
-   **Parâmetros:**
    Valores possíveis para o parâmetro `emotion`:
    -   `EmotionsType.ARROGANCE`: Arrogância
    -   `EmotionsType.SURPRISE`: Surpresa
    -   `EmotionsType.WHISTLE`: Assobio
    -   `EmotionsType.LAUGHTER`: Riso
    -   `EmotionsType.GOODBYE`: Despedida
    -   `EmotionsType.SHY`: Timidez
    -   `EmotionsType.SWEAT`: Suor
    -   `EmotionsType.SNICKER`: Riso malicioso
    -   `EmotionsType.PICKNOSE`: Cutucar o nariz
    -   `EmotionsType.CRY`: Choro
    -   `EmotionsType.ABUSE`: Insulto
    -   `EmotionsType.ANGRY`: Raiva
    -   `EmotionsType.KISS`: Beijo
    -   `EmotionsType.SLEEP`: Sono
    -   `EmotionsType.SMILE`: Sorriso
    -   `EmotionsType.GRIEVANCE`: Tristeza
    -   `EmotionsType.QUESTION`: Dúvida
    -   `EmotionsType.FAINT`: Desmaio
    -   `EmotionsType.PRISE`: Elogio
    -   `EmotionsType.NORMAL`: Padrão
-   **Exemplo de Código:**
```java
systemManager.showEmotion(EmotionsType.PRISE);
```

### 3.7.4 Obter Nível da Bateria

-   **Assinatura do Método:** `public int getBatteryValue()`
-   **Descrição:** Obtém o nível atual da bateria do robô.
-   **Valor de Retorno:** O campo `result` do `OperationResult` retorna o nível da bateria como um `int`.

### 3.7.5 Obter Status da Bateria

-   **Assinatura do Método:** `public int getBatteryStatus()`
-   **Descrição:** Obtém o status de carregamento da bateria do robô.
-   **Valor de Retorno:** O campo `result` do `OperationResult` retorna o status de carregamento com três resultados possíveis:
    -   `SystemManager.STATUS_NORMAL`: Não está carregando.
    -   `SystemManager.STATUS_CHARGE_PILE`: Carregando na base de carregamento.
    -   `SystemManager.SATUS_CHARGE_LINE`: Carregando com o cabo de alimentação.

### 3.7.6 Escutar Alertas de Segurança Doméstica

-   **Assinatura do Método:** `public void setOnIDarlingListener(IDarlingListener iDarlingListener)`
-   **Descrição:** Se a política de defesa do "Segurança Doméstica" estiver ativada, este callback será acionado quando um evento de alarme de segurança ocorrer.
-   **Parâmetros:** O callback de alarme implementa a interface `IDarlingListener`, que requer o método `void onAlarm(int type)`.
    -   `type`: Indica o tipo de alarme. `1` para alarme de bloqueio, `2` para alarme de intrusão e `3` para alarme de ultrapassagem de limite.

### 3.7.7 Obter Versão do Serviço Principal

-   **Assinatura do Método:** `public String getMainServiceVersion()`
-   **Descrição:** Obtém o número da versão do programa de controle principal do sistema.

### 3.7.8 Mostrar/Ocultar Botão Flutuante do Sistema

-   **Assinatura do Método:** `public OperationResult switchFloatBar(boolean isShow, String className)`
-   **Descrição:** Mostra ou oculta o botão flutuante do sistema. Este método só pode ser chamado em uma `Activity`.
-   **Parâmetros:**
    -   `isShow`: `true` para mostrar, `false` para ocultar.
    -   `className`: O nome completo da classe da `Activity` atual (pode ser obtido com `getClass().getName()`).

### 3.7.9 Mostrar/Ocultar Botão Flutuante do Sistema (para evitar piscar)

-   **Assinatura do Método:** `public OperationResult switchFloatBarInApplication(boolean isShow)`
-   **Descrição:** Mostra ou oculta o botão flutuante do sistema, ideal para evitar o efeito de piscar durante a navegação entre `Activities` no mesmo app.
-   **Parâmetros:**
    -   `isShow`: `true` para mostrar, `false` para ocultar.

### 3.7.10 Consultar Tipo de Robô

-   **Assinatura do Método:** `public int getRobotType()`
-   **Descrição:** Obtém o tipo do robô.
-   **Valor de Retorno:**
    -   `SystemManager.ROBOT_TYPE_B`: Modelo B (Elf)
    -   `SystemManager.ROBOT_TYPE_D`: Modelo D (King Kong)
    -   `SystemManager.ROBOT_TYPE_DESKTOP`: Robô de Desktop (Mini Elf)
---
## 3.8 Gerenciamento de Mídia

**Processo de Integração:**

1.  Obtenha uma instância do `HDCameraManager`:

```java
import com.sanbot.opensdk.function.unit.HDCameraManager;

HDCameraManager hdCameraManager = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);
```

2.  Use o objeto `hdCameraManager` obtido para chamar os métodos de controle correspondentes.

### 3.8.1 Obter Callback de Fluxo de Áudio e Vídeo

-   **Assinatura do Método:** `public void setMediaListener(MediaListener mediaListener)`
-   **Descrição:** Obtém os dados de fluxo de áudio e vídeo da câmera de alta definição e do microfone do robô.
-   **Precauções:** O microfone da câmera HD não suporta cancelamento de eco, o que pode causar ruído agudo durante a gravação e reprodução simultâneas.
-   **Parâmetros:** Implementa a interface `MediaStreamListener`, que requer os seguintes métodos:
    -   `void getVideoStream(byte[] data);`
    -   `void getAudioStream(byte[] data);`

### 3.8.2 Abrir Fluxo de Mídia

-   **Assinatura do Método:** `public OperationResult openStream(StreamOption streamOption)`
-   **Descrição:** Abre o fluxo de mídia. Somente após chamar este método, os dados de callback de áudio e vídeo podem ser recebidos.
-   **Parâmetros:** `StreamOption` possui os seguintes parâmetros:
    -   `type`: Modo de decodificação de vídeo.
        -   `StreamOption.TYPE_HARDWARE_DECORD`: Decodificação por hardware (padrão).
        -   `StreamOption.TYPE_SOFTWARE_DECORD`: Decodificação por software.
    -   `isJustIframe`: Se `true`, retorna apenas dados de I-frames (padrão `false`).
    -   `channel`: Formato do fluxo de vídeo.
        -   `StreamOption.MAIN_STREAM`: Fluxo principal, resolução de 1280x720 (padrão).
        -   `StreamOption.SUB_STREAM`: Fluxo secundário, resolução de 640x480.
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.beans.StreamOption;

StreamOption streamOption = new StreamOption();
streamOption.setChannel(StreamOption.MAIN_STREAM);
streamOption.setDecodType(StreamOption.HARDWARE_DECODE);
streamOption.setJustIframe(false);
OperationResult operationResult = hdCameraManager.openStream(streamOption);
int result = Integer.valueOf(operationResult.getResult());
if (result != -1) {
    handleList.add(result);
}
```
    Atualmente, é possível abrir no máximo dois fluxos de vídeo. O método `openStream` retorna um `handle` (identificador) que deve ser usado para fechar o fluxo.

### 3.8.3 Fechar Fluxo de Mídia

-   **Assinatura do Método:** `public OperationResult closeStream(int handle)`
-   **Descrição:** Fecha o fluxo de mídia. É crucial chamar este método no `onDestroy` da sua interface para evitar vazamentos de memória.
-   **Exemplo de Código:**
```java
hdCameraManager.closeStream(handle);
```

### 3.8.4 Obter Callback de Reconhecimento Facial

-   **Assinatura do Método:** `public void setMediaListener(MediaListener mediaListener)`
-   **Descrição:** Este callback é acionado quando o robô detecta um rosto em sua frente. Se o usuário estiver cadastrado no aplicativo "Membros da Família", as informações do usuário serão retornadas junto com o reconhecimento. A precisão pode ser afetada por fatores como iluminação e ângulo do rosto. Não funciona offline.
-   **Parâmetros:** Implementa a interface `FaceRecognizeListener`, que requer o método:
    `void recognizeResult(List<FaceRecognizeBean> faceRecognizeBean);`
    `FaceRecognizeBean` contém a posição do rosto na tela e informações do usuário (se disponíveis), como:
    -   `w`, `h`: Largura e altura da imagem (padrão 1280x720).
    -   `birthday`, `gender`, `user`: Informações do usuário.
    -   `bottom`, `left`, `right`, `top`: Posição do rosto na tela (percentual).
-   **Exemplo de Código:**
```java
import com.sanbot.opensdk.function.unit.interfaces.media.FaceRecognizeListener;
import com.sanbot.opensdk.function.beans.FaceRecognizeBean;

hdCameraManager.setMediaListener(new FaceRecognizeListener() {
    @Override
    public void recognizeResult(List<FaceRecognizeBean> faceRecognizeBean) {
        Log.i("info", "Dados de reconhecimento facial obtidos.");
    }
});
```

### 3.8.5 Capturar Imagem do Fluxo de Vídeo

-   **Assinatura do Método:** `public Bitmap getVideoImage()`
-   **Descrição:** Captura uma imagem do fluxo de vídeo do robô. Pode ser usado em conjunto com o reconhecimento facial para capturar a imagem do rosto.
-   **Exemplo de Código:**
```java
hdCameraManager.setMediaListener(new FaceRecognizeListener() {
    @Override
    public void recognizeResult(FaceRecognizeBean faceRecognizeBean) {
        Bitmap bitmap = mediaManager.getVideoImage();
        if (bitmap != null) {
            Log.i("info", "Imagem de reconhecimento facial obtida.");
        }
    }
});
```

---
## 3.9 Controle do Projetor

**Processo de Integração:**

1.  Obtenha uma instância do `ProjectorManager`:

```java
import com.sanbot.opensdk.function.unit.ProjectorManager;

ProjectorManager projectorManager = (ProjectorManager) getUnitManager(FuncConstant.PROJECTOR_MANAGER);
```

2.  Use o objeto `projectorManager` obtido para chamar os métodos de controle correspondentes.

### 3.9.1 Ligar/Desligar Projetor

-   **Assinatura do Método:** `public OperationResult switchProjector(boolean isOpen)`
-   **Descrição:** Controla o projetor. Deve haver um intervalo de pelo menos 12 segundos entre as operações de ligar e desligar.
-   **Parâmetros:** `isOpen`: `true` para ligar, `false` para desligar.
-   **Exemplo:**
```java
projectorManager.switchProjector(true);
```

### 3.9.2 Definir Espelhamento do Projetor

-   **Assinatura do Método:** `public OperationResult setMirror(int value)`
-   **Descrição:** Define o modo de espelhamento da projeção.
-   **Parâmetros:** `value` (0-3):
    -   `ProjectorManager.MIRROR_CLOSE` (0): Sem espelhamento (padrão).
    -   `ProjectorManager.MIRROR_LR` (1): Espelhamento horizontal.
    -   `ProjectorManager.MIRROR_UD` (2): Espelhamento vertical.
    -   `ProjectorManager.MIRROR_ALL` (3): Espelhamento em ambos os eixos.
-   **Exemplo:**
```java
projectorManager.setMirror(ProjectorManager.MIRROR_ALL);
```

### 3.9.3 Definir Correção Keystone Horizontal

-   **Assinatura do Método:** `public OperationResult setTrapezoidH(int value)`
-   **Descrição:** Define a correção keystone horizontal.
-   **Parâmetros:** `value`: -30 a 30.
-   **Exemplo:**
```java
projectorManager.setTrapezoidH(10);
```

### 3.9.4 Definir Correção Keystone Vertical

-   **Assinatura do Método:** `public OperationResult setTrapezoidV(int value)`
-   **Descrição:** Define a correção keystone vertical.
-   **Parâmetros:** `value`: -20 a 30.
-   **Exemplo:**
```java
projectorManager.setTrapezoidV(10);
```

### 3.9.5 Definir Contraste do Projetor

-   **Assinatura do Método:** `public OperationResult setContrast(int value)`
-   **Descrição:** Define o contraste da imagem.
-   **Parâmetros:** `value`: -15 a 15.
-   **Exemplo:**
```java
projectorManager.setContrast(10);
```

### 3.9.6 Definir Brilho do Projetor

-   **Assinatura do Método:** `public OperationResult setBright(int value)`
-   **Descrição:** Define o brilho da imagem.
-   **Parâmetros:** `value`: -31 a 31.
-   **Exemplo:**
```java
projectorManager.setBright(10);
```

### 3.9.7 Definir Cor do Projetor

-   **Assinatura do Método:** `public OperationResult setColor(int value)`
-   **Descrição:** Define a tonalidade da cor da imagem.
-   **Parâmetros:** `value`: -15 a 15.
-   **Exemplo:**
```java
projectorManager.setColor(10);
```

### 3.9.8 Definir Saturação do Projetor

-   **Assinatura do Método:** `public OperationResult setSaturation(int value)`
-   **Descrição:** Define a saturação da imagem.
-   **Parâmetros:** `value`: -15 a 15.
-   **Exemplo:**
```java
projectorManager.setSaturation(10);
```

### 3.9.9 Definir Nitidez do Projetor

-   **Assinatura do Método:** `public OperationResult setAcuity(int value)`
-   **Descrição:** Define a nitidez da imagem.
-   **Parâmetros:** `value`: 0 a 6.
-   **Exemplo:**
```java
projectorManager.setAcuity(4);
```

### 3.9.10 Ajuste do Eixo Óptico (Modo Especialista)

-   **Assinatura do Método:** `public OperationResult setExpertAxis(int value)`
-   **Descrição:** Ajusta o eixo óptico no modo especialista.
-   **Parâmetros:** `value`: 1 (ativar), 2 (aumentar), 3 (diminuir), 4 (sair sem salvar), 5 (salvar e sair).
-   **Exemplo:**
```java
projectorManager.setExpertAxis(1);
```

### 3.9.11 Ajuste de Fase (Modo Especialista)

-   **Assinatura do Método:** `public OperationResult setExpertPhase(int value)`
-   **Descrição:** Ajusta a fase no modo especialista.
-   **Parâmetros:** `value`: 1 (ativar), 2 (aumentar), 3 (diminuir), 4 (sair sem salvar), 5 (salvar e sair).
-   **Exemplo:**
```java
projectorManager.setExpertPhase(1);
```

### 3.9.12 Definir Modo do Projetor

-   **Assinatura do Método:** `public OperationResult setMode(int mode)`
-   **Descrição:** Define o modo de projeção.
-   **Parâmetros:** `mode`:
    -   `ProjectorManager.MODE_WALL` (1): Modo de projeção em parede.
    -   `ProjectorManager.MODE_CEILING` (2): Modo de projeção no teto.
-   **Exemplo:**
```java
projectorManager.setMode(ProjectorManager.MODE_CEILING);
```

### 3.9.13 Consultar Dados e Status do Projetor

-   **Assinatura do Método:** `public OperationResult queryConfig(String configName)`
-   **Descrição:** Consulta o status atual (ligado/desligado) e outros valores de configuração do projetor.
-   **Parâmetros:** `configName`:
    -   `ProjectorManager.CONFIG_SWITCH`, `_TRAPEZOIDH`, `_TRAPEZOIDV`, `_CONTRAST`, `_BRIGHT`, `_COLOR`, `_SATURATION`, `_ACUITY`, `_MODE`, `_MIRROR`.
-   **Exemplo:**
```java
import com.sanbot.opensdk.beans.OperationResult;

OperationResult op = projectorManager.queryConfig(ProjectorManager.CONFIG_SWITCH);
if (op.getResult().equals("1")) {
    Log.e("info", "O projetor está ligado.");
}
```

### 3.9.14 Callback da Interface do Projetor

-   **Assinatura do Método:** `public void setOnProjectorListener(OnProjectorListener projectorListener)`
-   **Descrição:** Interface de listener para erros durante o ajuste do modo especialista.
-   **Exemplo:**
```java
projectorManager.setOnProjectorListener(new ProjectorManager.ProjectorListener() {
    @Override
    public void expertModeError(ProjectorExpertMode projectorExpertMode) {
        // TODO: Callback de erro no modo especialista
    }
});
```
---
## 3.10 Controle de Funções de Movimento Modular

**Processo de Integração:**

1.  Obtenha uma instância do `ModularMotionManager`:

```java
ModularMotionManager modularMotionManager = (ModularMotionManager) getUnitManager(FuncConstant.MODULARMOTION_MANAGER);
```

2.  Use o objeto `modularMotionManager` para chamar os métodos de controle.

### 3.10.1 Ativar/Desativar Movimento Livre

-   **Assinatura do Método:** `public OperationResult switchWander(boolean isOpen)`
-   **Descrição:** Ativa ou desativa a função de movimento livre do robô.
-   **Valores de Retorno:** O `ErrorCode` pode ser:
    -   `ErrorCode.FAIL_MOTION_LOCKED`: Outra função de movimento está ativa.
    -   `ErrorCode.FAIL_NO_PERMISSION`: Sem permissão.
    -   `ErrorCode.FAIL_IS_CHARGE`: O robô está carregando.

### 3.10.2 Ativar/Desativar Carregamento Automático

-   **Assinatura do Método:** `public OperationResult switchCharge(boolean isOpen)`
-   **Descrição:** Ativa ou desativa a função de carregamento automático.
-   **Valores de Retorno:** O `ErrorCode` pode ser:
    -   `ErrorCode.FAIL_MOTION_LOCKED`: Outra função de movimento está ativa.
    -   `ErrorCode.FAIL_NO_PERMISSION`: Sem permissão.
    -   `ErrorCode.FAIL_IS_CHARGE`: O robô já está carregando.
    -   `ErrorCode.FAIL_NO_CHARGE_PILE`: A base de carregamento não foi encontrada.

### 3.10.3 Obter Status do Interruptor de Movimento Livre

-   **Assinatura do Método:** `public OperationResult getWanderStatus()`
-   **Descrição:** Verifica se a função de movimento livre está ativada.
-   **Valor de Retorno:** `getResult()` retorna `"1"` se estiver ativado, `"0"` se desativado.
-   **Exemplo:**
```java
OperationResult op = modularMotionManager.getWanderStatus();
if (op.getResult().equals("1")) {
    // TODO: Movimento livre está ativado
}
```

### 3.10.4 Obter Status do Interruptor de Carregamento Automático

-   **Assinatura do Método:** `public OperationResult getAutoChargeStatus()`
-   **Descrição:** Verifica se a função de carregamento automático está ativada.
-   **Valor de Retorno:** `getResult()` retorna `"1"` se ativado, `"0"` se desativado.
-   **Exemplo:**
```java
OperationResult op = modularMotionManager.getAutoChargeStatus();
if (op.getResult().equals("1")) {
    // TODO: Carregamento automático ativado
}
```

## 3.11 Instruções de Pré-processamento
As instruções de pré-processamento são instruções que são executadas e entram em vigor imediatamente quando um componente Activity ou Service estabelece uma conexão com o controlador principal. As instruções de pré-processamento são configuradas no arquivo AndroidManifest.xml na forma de tags `<meta-data>`, que são configuradas sob as tags `<application>`, `<activity>` e `<service>` e têm escopos diferentes. A seguir está um diagrama esquemático das instruções de pré-processamento no arquivo AndroidManifest.xml:
O trecho de código mostrado pela seta vermelha é uma instrução de pré-processamento.
O escopo da instrução de pré-processamento configurada sob a tag `<application>` é todos os Activitys e Services no aplicativo atual. É um atributo global, que é equivalente a configurar as mesmas instruções de pré-processamento para cada activity ou service; enquanto as instruções de pré-processamento configuradas sob a tag `<activity>` entrarão em vigor quando a activity atual estiver visível e perderão o efeito quando o método onStop da activity atual for chamado; as instruções de pré-processamento configuradas sob a tag `<service>` entrarão em vigor quando o Service for criado e perderão o efeito quando o método onDestroy do Service for chamado.
As instruções de pré-processamento sob `<application>` serão substituídas pelas mesmas instruções de pré-processamento sob `<activity>` ou `<service>`. Se você precisar usar instruções de pré-processamento sob `<application>` ou `<service>`, por favor, seja cauteloso, porque o Service pode ser executado em segundo plano. Uma vez que as instruções de pré-processamento são configuradas, elas sempre entrarão em vigor devido à sobrevivência do Service. Isso afetará a lógica de interação do robô e a interação de outros aplicativos de terceiros. Portanto, se não for necessário, evite usar instruções de pré-processamento sob `<application>` ou `<service>`.
**Nota**: Devido às limitações do mecanismo de trabalho das instruções de pré-processamento, a reinstalação ou desinstalação do aplicativo no sistema de P&D pode causar um mau funcionamento do robô devido à influência das instruções de pré-processamento. Este fenômeno está relacionado apenas ao comportamento de reinstalação e desinstalação do aplicativo e não afetará o uso normal do aplicativo após a listagem.

### 3.11.1 Interruptor de gravação
**Assinatura do método**:
```xml
<meta-data android:name="CONFIG_RECORD" android:value="true"/>
```
**Descrição do método**:
Se o robô precisar usar a função de gravação, esta instrução deve ser configurada. Se esta instrução entrar em vigor com sucesso, a orelha do robô acenderá uma luz azul. Certifique-se de configurar de acordo com as instruções acima, caso contrário, isso fará com que o controlador principal seja reiniciado e a conexão entre o aplicativo e o controlador principal seja interrompida.

### 3.11.2 Modo de reconhecimento
**Assinatura do método**:
```xml
<meta-data android:name="RECOGNIZE_MODE" android:value="1"/>
```
**Descrição do método**:
Define o modo de reconhecimento de voz do robô, que atualmente suporta apenas o modo de truncamento. Consulte a seção 3.2.6 para obter detalhes.

### 3.11.3 Modo de voz
**Assinatura do método**:
```xml
<meta-data android:name="SPEECH_MODE" android:value="1"/>
```
**Descrição do método**:
O reconhecimento de voz do robô tem dois modos integrados: 1. Depois que o robô é ativado, se ninguém for detectado falando por mais de 10 segundos, ele entrará ativamente no estado de suspensão. Este modo é o modo padrão usado pelo robô; 2. Depois que o robô é ativado, se ninguém for detectado falando em um curto período de tempo (cerca de 2 a 3 segundos), ele entrará imediatamente no estado de suspensão. Quando o robô reconhece as palavras faladas pelo usuário, ele também entra no estado de suspensão. Neste modo, o usuário precisa reativar o robô novamente cada vez que completa uma conversa com o robô para continuar a se comunicar com o robô.
Quando android:value="1", o robô entrará no modo 2 acima.

### 3.11.4 Interruptor de resposta ao toque
**Assinatura do método**:
```xml
<meta-data android:name="FORBID_TOUCH" android:value="true"/>
```
**Descrição do método**:
Depois que esta instrução de pré-processamento é configurada, o usuário ainda pode ouvir os eventos de toque, mas o robô não responderá a nenhum evento de toque, incluindo despertar o robô por toque e outros comportamentos padrão.

### 3.11.5 Interruptor de resposta PIR
**Assinatura do método**:
```xml
<meta-data android:name="FORBID_PIR" android:value="true"/>
```
**Descrição do método**:
Depois que esta instrução de pré-processamento é configurada, o usuário ainda pode ouvir os eventos de disparo PIR, mas o robô não executará os eventos de disparo PIR de acordo com a estratégia padrão (a estratégia padrão se refere ao robô girando 180 graus quando o PIR na parte de trás do robô é acionado).

### 3.11.6 Interruptor de resposta de despertar por voz
**Assinatura do método**:
```xml
<meta-data android:name="FORBID_WAKE_RESPONSE"
android:value="true"/>
```
**Descrição do método**:
Depois que esta instrução de pré-processamento é configurada, o robô não fará nenhuma resposta padrão, exceto para a luz verde na orelha ao despertar por voz.

### 3.11.7 Modo de reconhecimento incorreto
**Assinatura do método**:
```xml
<meta-data
android:name="MISTAKENLY_IDENTIFIED" android:value="true"/>
```
**Descrição do método**:
O controlador principal filtra o reconhecimento de uma palavra por padrão. Depois que esta instrução de pré-processamento é definida, o controlador principal não filtrará mais o reconhecimento de uma palavra, como "sim", "não", etc. Este modo é melhor usado em um ambiente silencioso, e um ambiente barulhento sempre reconhecerá erroneamente.

### 3.11.8 Modo anti-morte (será morto ao reproduzir música em segundo plano)
**Assinatura do método**:
```xml
<meta-data android:name="forbid_stop_music" android:value="true"/>
```
**Descrição do método**:
A versão anterior matava o APP que reproduzia música em segundo plano por padrão. Por exemplo, se a página atual estiver reproduzindo música e, em seguida, pular para outra página, a música ainda está tocando, então este APP será morto. Agora, o modo de pré-processamento é adicionado. Se este modo for configurado, ele não será mais morto, mas o APP deve controlar seu próprio ciclo de vida de reprodução para evitar afetar o efeito de reconhecimento.

### 3.11.9 Modo de solicitação semântica proibida (aplicável apenas a versões estrangeiras)
**Assinatura do método**:
```xml
<meta-data android:name="forbid_request_semantics" android:value="1"/>
```
**Descrição do método**:
O usuário configura este meta-data no Activity correspondente ao AndroidManifest, e o serviço principal lerá esta configuração. Se estiver configurado como 1, o serviço principal não fará solicitações de rede, e o cliente pode fazer sua própria solicitação semântica de acordo com o reconhecimento de texto retornado pelo serviço principal.

## 3.12 Palavras de comando e semântica
### 3.12.1 Palavras de comando globais personalizadas
Os usuários podem controlar o robô por meio de certas palavras ou frases curtas. Essas palavras ou frases curtas são chamadas de palavras de comando. Por exemplo, podemos controlar o robô para ligar a luz branca na cabeça através da palavra de comando "ligar a luz" e controlar o robô para abrir o aplicativo de filme e reproduzir o filme através da palavra de comando "reproduzir filme". Essas palavras de comando são globais e sempre entrarão em vigor, não importa em qual interface o sistema esteja.
O SDK suporta desenvolvedores para personalizar palavras de comando globais para invocar o Activity ou Service especificado pelo desenvolvedor. Ao usar palavras de comando globais personalizadas, os desenvolvedores precisam entender os seguintes pontos:
1. As palavras de comando globais suportam reconhecimento offline, e as palavras de comando também podem entrar em vigor em um estado não conectado.
2. Cada aplicativo suporta a personalização de 10 palavras de comando, e todo o sistema suporta até 100 palavras de comando globais personalizadas. Palavras de comando que excedem este limite de quantidade serão ignoradas automaticamente.
3. A ordem de análise das palavras de comando é incerta, o que significa que se seu aplicativo e outros aplicativos de desenvolvedores definirem as mesmas palavras de comando, é muito provável que suas palavras de comando não entrem em vigor.
4. As palavras de comando globais serão pré-carregadas na primeira vez que o sistema for iniciado. Se o desenvolvedor fizer alterações nas palavras de comando globais durante o desenvolvimento, ele precisará reiniciar o robô ou reiniciar o programa de serviço principal para que entrem em vigor.

O processo de desenvolvimento de palavras de comando globais personalizadas é o seguinte:
1. Adicione a seguinte configuração no arquivo AndroidManifest.xml:
```xml
<provider
android:authorities="<seu nome de pacote do aplicativo>.grammar"
android:name="com.sanbot.opensdk.utils.GrammarProvider"
android:exported="true"/>
```
Observe que o conteúdo entre parênteses acima é substituído pelo nome do pacote do aplicativo correspondente.
2. Crie um novo arquivo `grammar` no diretório `assets` e crie um arquivo chamado `globalgrammar.xml` na nova pasta `grammar`.
3. Escreva a sintaxe das palavras de comando globais em `globalgrammar.xml`.
```xml
<Grammars>
<group>
<command>
<item>Reproduzir vídeo</item>
<item>Abrir vídeo</item>
</command>
<type>activity</type>
<class>com.sanbot.librarydemo.VideoActivity</class>
</group>
<group>
<command>
<item>Iniciar serviço</item>
</command>
<type>service</type>
<class>com.sanbot.librarydemo.MainService</class>
</group>
</Grammars>
```
O acima é um exemplo da sintaxe da palavra de comando global. Quando o usuário diz "reproduzir vídeo" ou "abrir vídeo" para o robô, o robô abrirá automaticamente a página `com.sanbot.librarydemo.VideoActivity`. A seguir, as tags xml no exemplo serão apresentadas em detalhes.
*   `<Grammars>`: A tag raiz da sintaxe.
*   `<group>`: `<group>` define um conjunto de palavras de comando e a definição do comportamento da palavra de comando para iniciar Activity ou Service.
*   `<command>`: `<command>` contém um conjunto de palavras de comando com o mesmo comportamento.
*   `<item>`: Cada `<item>` contém uma palavra de comando.
*   `<type>`: O objeto é iniciado quando a palavra de comando é acionada, e os valores opcionais são `activity` e `service`.
*   `<class>`: O nome da classe da classe a ser iniciada. Observe que as informações completas do nome da classe devem ser preenchidas.
4. Para activity ou service que precisa ser iniciado, adicione a seguinte configuração no arquivo `androidmanifest.xml`:
`android:exported=”true”`.
A palavra de comando global acionada será passada para a activity ou service através do Intent. Se o desenvolvedor precisar obter a palavra de comando acionada, ele pode consultar o seguinte código
```java
public class TestActivity extends BindBaseActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String data = getIntent().getStringExtra("content");
	}
// ...
}
```

### 3.12.2 Semântica personalizada
Na interface de retorno de chamada de reconhecimento de fala (consultar a seção 3.2.6), o conteúdo retornado pela interface pode retornar os resultados da análise semântica que o robô pode reconhecer, além das palavras que o usuário diz ao robô. No entanto, o suporte para esta análise semântica é incompleto e muitas vezes não pode atender às necessidades dos desenvolvedores para semântica em várias cenas específicas. Portanto, o SDK fornece um método para os desenvolvedores personalizarem a semântica.
Ao usar a semântica personalizada, os desenvolvedores precisam entender os seguintes pontos:
1. A semântica personalizada geralmente só pode ser usada em um estado conectado.
2. Cada aplicativo suporta a personalização de 20 semânticas, e as semânticas que excedem este limite de quantidade serão ignoradas automaticamente.
3. A semântica personalizada só entrará em vigor quando o aplicativo do usuário estiver em primeiro plano. Portanto, não é confiável monitorar a semântica personalizada no Service.
4. A semântica personalizada será pré-carregada na primeira vez que o sistema for iniciado. Se o desenvolvedor fizer alterações na semântica personalizada durante o desenvolvimento, ele precisará reiniciar o robô ou reiniciar o programa de serviço principal para que entrem em vigor.

O processo de desenvolvimento de palavras de comando globais personalizadas é o seguinte:
1. Adicione a seguinte configuração no arquivo AndroidManifest.xml:
```xml
<provider
android:authorities="<seu nome de pacote do aplicativo>.grammar"
android:name="com.sanbot.opensdk.utils.GrammarProvider"
android:exported="true"/>
```
Observe que o conteúdo entre parênteses acima é substituído pelo nome do pacote do aplicativo correspondente.
2. Crie um novo arquivo `grammar` no diretório `assets` e crie um arquivo chamado `localgrammar.xml` na nova pasta `grammar`.
3. Escreva a sintaxe das palavras de comando globais em `localgrammar.xml`.
```xml
<Grammars>
<group>
<command>
<item>Reproduzir vídeo</item>
<item>Abrir vídeo</item>
</command>
<topic>qh_video</topic>
<action>start</action>
</group>
<group>
<command>
<item>Pausar vídeo</item>
</command>
<topic>qh_video</topic>
<action>pause</action>
</group>
</Grammars>
```
O acima é um exemplo da sintaxe da palavra de comando global. Quando o usuário diz "reproduzir vídeo" ou "abrir vídeo" para o robô, o robô abrirá automaticamente a página `com.sanbot.librarydemo.VideoActivity`. A seguir, as tags xml no exemplo serão apresentadas em detalhes.
*   `<Grammars>`: A tag raiz da sintaxe.
*   `<group>`: `<group>` define um conjunto de semântica.
*   `<command>`: `<command>` contém um conjunto de semântica com o mesmo tópico e ação.
*   `<item>`: Cada `<item>` contém uma frase curta. Quando o robô reconhece esta frase curta, ele a processará de acordo com as regras semânticas personalizadas do usuário.
*   `<topic>`: Define o tópico da semântica. Para evitar a mistura com a semântica embutida no sistema, recomenda-se nomear o método como “abreviação da empresa (individual)_nome do tópico”. Consulte o “Documento de Descrição Semântica Aberta Sanbao” para obter detalhes.
*   `<action>`: Define a ação da semântica. Consulte o “Documento de Descrição Semântica Aberta Sanbao” para obter detalhes.

## 3.13 Controle inteligente de periféricos
**Processo de acesso**:
1. Solicite o objeto `ZigbeeManager`. O código de solicitação é
```java
ZigbeeManager zigbeeManager = (ZigbeeManager)getUnitManager(FuncConstant.ZIGBEE_MANAGER);
```
2. Use o objeto `zigbeeManager` solicitado para chamar os métodos correspondentes para controle.

### 3.13.1 Obter lista de permissões
**Assinatura do método**:
```java
public OperationResult getWhiteList();
```
**Descrição do método**:
Usado para obter a lista de permissões Zigbee. Os resultados específicos serão retornados no método de retorno de chamada.

### 3.13.2 Determine se o Zigbee está pronto
**Assinatura do método**:
```java
public OperationResult getNotifyReadyStatus();
```
**Descrição do método**:
Verifique se o Zigbee está pronto. Outras operações Zigbee só podem ser realizadas depois que o Zigbee estiver pronto.
**Descrição do valor de retorno**:
O método `getResult()` de `OperationResult` retornará o resultado da consulta. Se o valor retornado for “1”, isso indica que o Zigbee foi inicializado com sucesso e “0” indica que o Zigbee não foi inicializado com sucesso.
**Código de exemplo**:
```java
OperationResult operationResult =zigbeeManager. getNotifyReadyStatus();
if(operationResult.getResult().equals("1")){
//TODO NotifyReady
}
```

### 3.13.3 Obter lista de dispositivos
**Assinatura do método**:
```java
public OperationResult getZigbeeList();
```
**Descrição do método**:
Usado para obter a lista de dispositivos Zigbee. Os resultados específicos serão retornados no método de retorno de chamada.

### 3.13.4 Adicionar à lista de permissões
**Assinatura do método**:
```java
public OperationResult addWhiteList(String command);
```
**Descrição do método**:
Usado para adicionar dispositivos Zigbee à lista de permissões.

### 3.13.5 Excluir lista de permissões
**Assinatura do método**:
```java
public OperationResult deleteWhiteList(String command);
```
**Descrição do método**:
Usado para remover dispositivos Zigbee da lista de permissões.

### 3.13.6 Ativar lista de permissões
**Assinatura do método**:
```java
public OperationResult switchWhtieList(boolean isOpen);
```
**Descrição do método**:
Usado para ativar ou desativar a função de lista de permissões.
**Código de exemplo**:
```java
zigbeeManager.switchWhtieList(true);
```

### 3.13.7 Definir o tempo permitido para os dispositivos entrarem
**Assinatura do método**:
```java
public OperationResult setAllowJoinTime(int time);
```
**Descrição do método**:
Define o tempo permitido para os dispositivos entrarem no gateway. Quando definido como 0, isso indica que os dispositivos podem entrar no gateway a qualquer momento.
**Descrição do parâmetro**:
`time` O tempo permitido para entrar, em segundos.
**Código de exemplo**:
```java
zigbeeManager.setAllowJoinTime(200);
```

### 3.13.8 Excluir dispositivo
**Assinatura do método**:
```java
public OperationResult deleteDevice(String command);
```
**Descrição do método**:
Remove o dispositivo do gateway.

### 3.13.9 Limpar lista de permissões
**Assinatura do método**:
```java
public OperationResult clearWhiteList();
```
**Descrição do método**:
Limpa a lista de permissões Zigbee.

### 3.13.10 Enviar instrução Byte
**Assinatura do método**:
```java
public OperationResult sendByteCommand(byte[] data);
```
**Descrição do método**:
Envia diretamente dados do tipo array de bytes para o módulo Zigbee, que geralmente é usado para comunicação de dispositivos privados.

### 3.13.11 Enviar instrução String
**Assinatura do método**:
```java
public OperationResult sendCommand(String command);
```
**Descrição do método**:
Envia diretamente instruções do tipo String para o módulo Zigbee.

### 3.13.12 Retorno de chamada da interface Zigbee
**Assinatura do método**:
```java
public void setZigbeeListener(ZigbeeListener zigbeeListener);
```
**Descrição do método**:
Interface de escuta relacionada ao Zigbee, usada para obter dados da lista de permissões, monitorar atualizações de dados Zigbee e alterações de status.
**Código de exemplo**:
```java
zigbeeManager.setZigbeeListener(new ZigbeeManager.ZigbeeListener(){
@Override
public void notifyWhiteList(String whiteListData) {
//Obter dados da lista de permissões
}
@Override
public void notifyStatusChange(String info) {
//Os dados do tipo de dispositivo serão retornados aqui
}
@Override
public void notifyInfo(String info) {
//Os dados da lista de dispositivos serão retornados aqui
}
});
```

## 3.14 Rastreamento facial e saudação proativa
**Processo de acesso**:
1. Solicite o objeto `FaceTrackManager`. O código de solicitação é
```java
FaceTrackManager faceTrackManager = (FaceTrackManager)getUnitManager(FuncConstant.FACETRACK_MANAGER);
```
2. Use o objeto `faceTrackManager` solicitado para chamar os métodos correspondentes para controle.

### 3.14.1 Interruptor de rastreamento facial
**Assinatura do método**:
```java
public void switchFaceTrack(boolean isOn);
```
**Descrição do método**:
Envia diretamente instruções do tipo booleano para o `FaceTrackManager`. Se você precisar ativar a função de rastreamento facial em uma interface não facial, você pode abri-la através deste método. Da mesma forma, se você precisar desativar a função de rastreamento facial, você pode passar `false` (se você sair da interface e não desligar manualmente a função de rastreamento facial, o sistema a desligará automaticamente quando o cliente se desconectar)
**Código de exemplo**:
```java
faceTrackManager.switchFaceTrack(true);
```

### 3.14.2 Retorno de chamada do status de rastreamento facial
**Assinatura do método**:
```java
public void setFaceTrackListener(new FaceTrackManager.FaceTrackListener() {});
```
**Descrição do método**:
Retorno de chamada do status de rastreamento facial, o retorno de chamada só será feito se a chave de rastreamento facial for ativada na página atual
**Código de exemplo**:
```java
faceTrackManager.setFaceTrackListener(new FaceTrackManager.FaceTrackListener() {
@Override
public void faceTrackStart() {
Log.i(TAG, "faceTrackStart: ");
}
@Override
public void faceTrackComplete() {
Log.i(TAG, "faceTrackComplete: ");
}
@Override
public void faceTrackFail() {
Log.i(TAG, "faceTrackFail: ");
}
@Override
public void faceTrackLost() {
Log.i(TAG, "faceTrackLost: ");
}
@Override
public void startFindFace() {
Log.i(TAG, "startFindFace: ");
}
});
```
1. Entre eles, o método `startFindFace()` será retornado quando o ir for acionado quando uma pessoa está em frente ao robô e nenhuma pessoa for detectada. Quando este método é retornado, isso indica que o robô está tentando encontrar um rosto movendo-se.
2. O método `faceTrackStart()` indica que um rosto foi detectado e o rastreamento do rosto foi iniciado. Este método só será retornado uma vez em um processo.
3. O método `faceTrackComplete()` indica que o rosto foi rastreado e o rosto está no meio da tela. O usuário pode realizar algumas operações lógicas, como reconhecimento facial. Observe que este método pode ser acionado várias vezes e pode ser acionado novamente após o usuário se mover e rastrear o meio do rosto novamente.
4. O método `faceTrackLost()` indica que não há informações faciais em 10s e o rosto foi perdido. Este método só será acionado após o método `faceTrackStart` ter sido acionado.
5. O método `faceTrackFail()` só é acionado após o método `startFindFace` ter sido acionado e nenhum rosto for encontrado em 10s. Se um rosto for encontrado em 10s, este método não será acionado.

### 3.14.3 Interruptor de saudação proativa
**Assinatura do método**:
```java
public void switchSayHello(boolean isOn);
```
**Descrição do método**:
Envia diretamente instruções do tipo booleano para o `FaceTrackManager`. Se você precisar usar a função de saudação proativa, primeiro ative a função de rastreamento facial, porque a função de saudação proativa só será acionada quando o rastreamento facial estiver no meio da tela
**Código de exemplo**:
```java
faceTrackManager.switchSayHello(true);
```
`true` significa ligar; `false` significa desligar

### 3.14.4 Retorno de chamada do status de saudação proativa
**Assinatura do método**:
```java
public void setUserNameDetectListener(new FaceTrackManager.UserNameDetectListener() {});
```
**Descrição do método**:
O sistema salvará as informações faciais por 5s. Se não houver informações faciais em 5s, as informações faciais salvas serão apagadas automaticamente. Quando o robô não está falando, não está acordado e não está com o microfone desativado, as informações faciais serão retornadas.
**Código de exemplo**:
```java
faceTrackManager.setUserNameDetectListener(new FaceTrackManager.UserNameDetectListener() {
@Override
public void userName(String s) {
Log.i(TAG, "userName: $s"+s);
}
});
```
Observe que `s` pode ser uma string vazia

## 3.15 Protocolo de controle de movimento da área de trabalho
**Processo de acesso**:
1. Solicite o objeto `DesktopMotionManager`. O código de solicitação é
```java
DesktopMotionManager desktopManager = (DesktopMotionManager)getUnitManager(FuncConstant.DESKTOPMOTION_MANAGER);
```
2. Use o objeto `desktopManager` solicitado para chamar os métodos correspondentes para controle.

### 3.15.1 Parar movimento
**Assinatura do método**:
```java
public OperationResult doStop();
```
**Descrição do método**:
Envia instruções diretamente para o serviço de back-end através do objeto `desktopManager`. Depois que o serviço de back-end recebe a instrução, ele para o movimento multi-eixo.
**Código de exemplo**:
```java
desktopManager.doStop();
```

### 3.15.2 Reinicialização do movimento da área de trabalho
**Assinatura do método**:
```java
public OperationResult doReset();
```
**Descrição do método**:
Envia instruções diretamente para o serviço de back-end através do objeto `desktopManager`. Depois que o serviço de back-end recebe a instrução, ele realiza a reinicialização.
**Código de exemplo**:
```java
desktopManager.doReset();
```

### 3.15.3 Movimento de ângulo relativo da área de trabalho
**Assinatura do método**:
```java
public OperationResult doRelativeAngleMotion(RelativeAngleDesktopMotion relativeAngleDesktopMotion);
```
**Descrição do método**:
Envia instruções diretamente para o serviço de back-end através do objeto `desktopManager`. Depois que o serviço de back-end recebe a instrução, ele realiza a operação correspondente.
A velocidade tem positivo e negativo, e o ângulo não tem positivo e negativo
*   Faixa de controle de ângulo vertical: 0-45
*   Faixa de controle de ângulo horizontal: 0-160
*   Faixa de controle de ângulo de rolagem: 0-160
*   Faixa de controle de ângulo para frente e para trás: 0-45
**Código de exemplo**:
```java
RelativeAngleDesktopMotion relativeAngleDesktopMotion=new RelativeAngleDesktopMotion();
relativeAngleDesktopMotion.setForwardDegree((short) 10);//Ângulo de movimento para frente e para trás necessário 10
relativeAngleDesktopMotion.setForwardSpeed((short) 10);// Movendo para trás, o controle de velocidade é 1-60, números positivos se movem para trás, números negativos se movem para frente, e a direção do movimento para frente e para trás é controlada pela velocidade positiva e negativa
relativeAngleDesktopMotion.setHorizontalDegree((short) 10);//Ângulo de movimento horizontal 10
relativeAngleDesktopMotion.setHorizontalTurnSpeed((short) 10);// Controle de velocidade 1-60 Movendo para a esquerda, números positivos se movem para a esquerda, números negativos se movem para a direita, e a direção do movimento esquerdo e direito é controlada pela velocidade positiva e negativa
relativeAngleDesktopMotion.setVerticalDegree((short) 10);//Ângulo de movimento vertical para cima e para baixo 10
relativeAngleDesktopMotion.setVerticalSpeed((short) 10);// Controle de velocidade 1-60 Movendo para cima, números positivos se movem para cima, números negativos se movem para baixo, e a direção do movimento para cima e para baixo é controlada pela velocidade positiva e negativa
relativeAngleDesktopMotion.setRollDegree((short) 10);//Ângulo de movimento de rolagem 10
relativeAngleDesktopMotion.setRollSpeed((short) 10);// Controle de velocidade 1-60 Rolando para a direita, números positivos se movem para a direita, números negativos se movem para a esquerda, e a direção do movimento de rolagem esquerdo e direito é controlada pela velocidade positiva e negativa
desktopMotionManager.doRelativeAngleMotion(relativeAngleDesktopMotion);
```

### 3.15.4 Movimento de ângulo absoluto da área de trabalho
**Assinatura do método**:
```java
public OperationResult doAbsoluteAngleMotion(AbsoluteAngleDesktopMotion absoluteAngleDesktopMotion);
```
**Descrição do método**:
Envia instruções diretamente para o serviço de back-end através do objeto `desktopManager`. Depois que o serviço de back-end recebe a instrução, ele realiza a operação correspondente.
Controle de ângulo absoluto, a velocidade só pode ser um número positivo
*   Faixa de controle de ângulo vertical: 0-45
*   Faixa de controle de ângulo horizontal: 0-160
*   Faixa de controle de ângulo de rolagem: 0-160
*   Faixa de controle de ângulo para frente e para trás: 0-45
**Código de exemplo**:
```java
AbsoluteAngleDesktopMotion absoluteAngleDesktopMotion=new AbsoluteAngleDesktopMotion();
absoluteAngleDesktopMotion.setForwardDegree((short) 10);//Ângulo de movimento para frente e para trás necessário 10
absoluteAngleDesktopMotion.setForwardSpeed((short) 10);
absoluteAngleDesktopMotion.setHorizontalDegree((short) 10);//Ângulo de movimento horizontal 10
absoluteAngleDesktopMotion.setHorizontalTurnSpeed((short) 10);
absoluteAngleDesktopMotion.setVerticalDegree((short) 10);//Ângulo de movimento vertical para cima e para baixo 10
absoluteAngleDesktopMotion.setVerticalSpeed((short) 10);
absoluteAngleDesktopMotion.setRollDegree((short) 10);//Ângulo de movimento de rolagem 10
absoluteAngleDesktopMotion.setRollSpeed((short) 10);
desktopMotionManager.doAbsoluteAngleMotion(absoluteAngleDesktopMotion);
