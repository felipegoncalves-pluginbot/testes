package com.felipe.elftemplate.logic;

/** Etapas da sequência de RCP para leigos (base AHA / SBV — treino e demonstração). */
public enum CprStep {
  SAFETY(
      "1. Segurança",
      "Verifique se o local é seguro para você e para a vítima.",
      "Olhe ao redor antes de se aproximar.",
      "\u26A0"),
  RESPONSIVENESS(
      "2. Consciência",
      "Toque o ombro e fale em voz alta: \"Você está bem?\"",
      "Não responde? Não reage? Trate como emergência cardíaca.",
      "\u2753"),
  CALL_HELP(
      "3. Ligue 192",
      "Peça para alguém ligar 192 (SAMU) e buscar um desfibrilador portátil, se existir no local.",
      "Sozinho? Ligue 192 no viva-voz, deixe o celular no chão e volte à vítima.",
      "\u260E"),
  CHECK_BREATHING(
      "4. Respiração",
      "Incline a cabeça, eleve o queixo e observe por até 10 segundos.",
      "Ofegaço ou respiração irregular não conta — inicie as compressões.",
      "\uD83D\uDCA8"),
  COMPRESSIONS(
      "5. Compressões",
      "Mãos entrelaçadas no centro do peito, braços retos. Pressione forte e rápido: 100 a 120 por minuto.",
      "Profundidade de 5 a 6 cm. Deixe o tórax voltar entre cada compressão. Não pare até o socorro chegar.",
      "\u2764"),
  DEFIBRILLATOR(
      "6. Desfibrilador",
      "Aparelho portátil que analisa o coração e pode aplicar um choque. Ligue-o e siga a voz do aparelho.",
      "É uma caixa com adesivos (eletrodos) para colar no peito. Sem aparelho? Continue só as compressões.",
      "\u26A1");

  private final String title;
  private final String instruction;
  private final String hint;
  private final String emoji;

  CprStep(String title, String instruction, String hint, String emoji) {
    this.title = title;
    this.instruction = instruction;
    this.hint = hint;
    this.emoji = emoji;
  }

  public String getTitle() {
    return title;
  }

  public String getInstruction() {
    return instruction;
  }

  public String getHint() {
    return hint;
  }

  /** Emoji compatível com Android 6 (API 23) — sem símbolos Unicode 13+. */
  public String getEmoji() {
    return emoji;
  }

  public boolean isCompressionPhase() {
    return this == COMPRESSIONS;
  }
}
