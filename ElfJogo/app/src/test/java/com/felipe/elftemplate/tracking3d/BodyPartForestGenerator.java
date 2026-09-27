package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.Test;

/**
 * Gerador do modelo de partes do corpo: treina a floresta e grava o asset do APK.
 *
 * <p>Não roda na suíte normal. Treinar leva minutos e o produto é um artefato versionado, então isto
 * é uma ferramenta acionada de propósito:
 *
 * <pre>
 *   ./gradlew :app:testDebugUnitTest --tests '*BodyPartForestGenerator*' \
 *       -Pbodypart.train=true -Pbodypart.frames=600
 * </pre>
 *
 * <p>As opções vão com {@code -P}, não {@code -D}: {@code -D} para na JVM do Gradle e não chega ao
 * processo de teste. O repasse está em {@code app/build.gradle}.
 *
 * <p>Fica em {@code src/test} porque precisa do renderizador sintético, que é fixture de teste. O
 * alternativa seria duplicar o renderizador num módulo de ferramentas, e um renderizador duplicado é
 * exatamente como treino e validação começam a divergir.
 *
 * <p>Junto do modelo sai um relatório com semente, quantidade de quadros e hiperparâmetros. Sem isso o
 * binário seria um número mágico impossível de reproduzir.
 */
public class BodyPartForestGenerator {

  private static final String ENABLE_PROPERTY = "bodypart.train";

  private static final int DEFAULT_FRAMES = 600;
  private static final int DEFAULT_TREES = 3;
  private static final int DEFAULT_DEPTH = 14;
  private static final int DEFAULT_FEATURES = 140;
  private static final int DEFAULT_THRESHOLDS = 8;
  private static final int DEFAULT_MIN_SAMPLES = 40;
  private static final long DEFAULT_SEED = 20260911L;

  @Test
  public void trainsAndWritesTheForestAsset() throws IOException {
    Assume.assumeTrue(
        "ferramenta de treino; rode com -Dbodypart.train=true",
        Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "false")));

    int frames = intProperty("bodypart.frames", DEFAULT_FRAMES);
    long seed = longProperty("bodypart.seed", DEFAULT_SEED);
    BodyPartTrainingSet trainingSet = new BodyPartTrainingSet();
    trainingSet.generate(frames, seed);
    assertTrue("conjunto de treino vazio", trainingSet.getSampleCount() > 0);

    BodyPartForestTrainer trainer = new BodyPartForestTrainer();
    trainer.configure(
        intProperty("bodypart.trees", DEFAULT_TREES),
        intProperty("bodypart.depth", DEFAULT_DEPTH),
        intProperty("bodypart.features", DEFAULT_FEATURES),
        intProperty("bodypart.thresholds", DEFAULT_THRESHOLDS),
        intProperty("bodypart.minSamples", DEFAULT_MIN_SAMPLES));
    BodyPartForest forest = new BodyPartForest();
    trainer.train(trainingSet, seed + 1, forest);
    assertTrue("floresta nao carregou apos treino", forest.isLoaded());

    Path assetPath = assetDirectory().resolve(BodyPartForestCodec.ASSET_NAME);
    writeModel(assetPath, forest);
    writeReport(assetPath, forest, trainingSet, frames, seed);
    assertTrue("asset nao foi gravado", Files.size(assetPath) > 0);
  }

  private static void writeModel(Path assetPath, BodyPartForest forest) throws IOException {
    Files.createDirectories(assetPath.getParent());
    OutputStream output = Files.newOutputStream(assetPath);
    try {
      BodyPartForestCodec.write(output, forest);
    } finally {
      output.close();
    }
  }

  /** Metadados de reprodutibilidade ao lado do binário. */
  private static void writeReport(
      Path assetPath,
      BodyPartForest forest,
      BodyPartTrainingSet trainingSet,
      int frames,
      long seed)
      throws IOException {
    StringBuilder report = new StringBuilder();
    report.append("# Modelo de partes do corpo (floresta de decisao aleatoria)\n\n");
    report.append("Gerado por BodyPartForestGenerator. Nao editar a mao.\n\n");
    report.append("| campo | valor |\n|---|---|\n");
    report.append("| semente | ").append(seed).append(" |\n");
    report.append("| quadros sinteticos | ").append(frames).append(" |\n");
    report.append("| pixels amostrados | ").append(trainingSet.getSampleCount()).append(" |\n");
    report.append("| partes | ").append(BodyPart.COUNT).append(" |\n");
    report.append("| arvores | ").append(forest.getTreeCount()).append(" |\n");
    report.append("| nos | ").append(forest.getNodeCount()).append(" |\n");
    report.append("| folhas | ").append(forest.getLeafCount()).append(" |\n");
    report.append("| bytes do modelo | ").append(forest.getModelBytes()).append(" |\n");
    report.append("| bytes do asset | ").append(Files.size(assetPath)).append(" |\n");
    Path reportPath = assetPath.resolveSibling("bodypart_forest.md");
    Files.write(reportPath, report.toString().getBytes(Charset.forName("UTF-8")));
  }

  /**
   * Localiza {@code src/main/assets} tanto rodando com o diretório do módulo como com o da raiz.
   *
   * <p>O Gradle usa o diretório do módulo, mas execução direta pela IDE às vezes usa a raiz do
   * projeto. Resolver os dois evita gravar o modelo no lugar errado sem ninguém perceber.
   */
  private static Path assetDirectory() {
    Path fromModule = Paths.get("src/main/assets");
    if (Files.isDirectory(fromModule.getParent())) {
      return fromModule;
    }
    return Paths.get("app/src/main/assets");
  }

  private static int intProperty(String key, int fallback) {
    return Integer.parseInt(System.getProperty(key, Integer.toString(fallback)));
  }

  private static long longProperty(String key, long fallback) {
    return Long.parseLong(System.getProperty(key, Long.toString(fallback)));
  }
}
