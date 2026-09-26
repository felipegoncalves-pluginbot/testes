package com.felipe.compiladores.game

import com.felipe.compiladores.engine.DerivationMode.FREE
import com.felipe.compiladores.engine.DerivationMode.LEFTMOST
import com.felipe.compiladores.engine.DerivationMode.RIGHTMOST

/** Gramáticas clássicas usadas em várias fases. */
object Grammars {
    const val EXPR_LL = "E -> T E'\nE' -> + T E' | ε\nT -> F T'\nT' -> * F T' | ε\nF -> ( E ) | id"
    const val EXPR_LR = "E -> E + T | T\nT -> T * F | F\nF -> ( E ) | id"
    const val EXPR_AMB = "E -> E + E | E * E | ( E ) | id"
    const val ANBN = "S -> a S b | ε"
    const val PARENS = "S -> ( S ) S | ε"
    const val IF_ELSE = "S -> i E t S S' | a\nS' -> e S | ε\nE -> b"
    const val LIST_LR0 = "S -> ( L ) | x\nL -> S | L , S"
    const val POINTERS = "S -> L = R | R\nL -> * R | id\nR -> L"
    const val LR1_NOT_LALR = "S -> a A d | b B d | a B e | b A e\nA -> c\nB -> c"
    const val ASB_C = "S -> a S b | c"

    /** Exemplos oferecidos no Laboratório livre. */
    val examples = listOf(
        "Expressões LL" to EXPR_LL,
        "Expressões LR" to EXPR_LR,
        "aⁿbⁿ" to ANBN,
        "Parênteses" to PARENS,
        "If-else" to IF_ELSE,
        "Listas LR(0)" to LIST_LR0,
        "Ponteiros (L = R)" to POINTERS,
        "LR(1) não LALR" to LR1_NOT_LALR,
        "Ambígua" to EXPR_AMB,
    )
}

object Content {
    private const val CYAN = 0xFF22D3EE
    private const val VIOLET = 0xFFA78BFA
    private const val PINK = 0xFFF472B6
    private const val AMBER = 0xFFFBBF24
    private const val GREEN = 0xFF34D399
    private const val ORANGE = 0xFFFB923C
    private const val BLUE = 0xFF60A5FA
    private const val LIME = 0xFFA3E635
    private const val GOLD = 0xFFFACC15

    val worlds: List<World> = listOf(
        World(
            "w1", 1, "Oficina de Derivações", "Gramáticas, derivações e ambiguidade", "🔧", CYAN,
            Lesson(
                "Gramáticas em 1 minuto",
                listOf(
                    "Uma gramática livre de contexto é um conjunto de regras de reescrita. Não-terminais (em azul) ainda serão trocados; terminais (em amarelo) são os tokens finais.",
                    "Derivar é trocar um não-terminal pelo lado direito de uma de suas produções. Cada passo gera uma forma sentencial.",
                    "Derivação mais à esquerda: sempre troque o não-terminal mais à esquerda. É exatamente o que um parser top-down (LL) faz.",
                    "Derivação mais à direita: sempre troque o mais à direita. Um parser bottom-up (LR) produz essa derivação ao contrário!",
                    "Ambígua é a gramática em que uma mesma cadeia tem duas árvores de derivação diferentes.",
                ),
                "S → a S b | ε\nS ⇒ a S b ⇒ a a S b b ⇒ a a b b",
            ),
            listOf(
                Level(
                    "w1l1", "Primeiro passo",
                    "Transforme S em \"a a b b\". Toque no não-terminal destacado e escolha uma produção.",
                    "Cada passo troca um não-terminal por um lado direito. Essa gramática gera aⁿbⁿ: cada S → a S b coloca um 'a' e um 'b' de uma vez, e S → ε encerra.",
                    "Você precisa de dois 'a'. Quantas vezes precisa usar S → a S b antes de fazer S sumir?",
                    Grammars.ANBN, LevelSpec.Derive("a a b b", LEFTMOST),
                ),
                Level(
                    "w1l2", "Sempre à esquerda",
                    "Derive \"id * id\" trocando sempre o não-terminal MAIS À ESQUERDA.",
                    "Numa derivação mais à esquerda, os terminais já fixados à esquerda só crescem. É por isso que um parser top-down consegue ler a entrada da esquerda para a direita enquanto deriva.",
                    "O alvo não tem '+'. Qual produção de E evita o '+'? E qual produção de T traz o '*'?",
                    Grammars.EXPR_LR, LevelSpec.Derive("id * id", LEFTMOST),
                ),
                Level(
                    "w1l3", "Sempre à direita",
                    "Agora derive \"id + id\" trocando sempre o não-terminal MAIS À DIREITA.",
                    "Leia sua derivação de trás para frente: é exatamente a sequência de reduções de um parser shift-reduce! LR = Left-to-right, Rightmost derivation (ao contrário).",
                    "Aqui o 'id' da direita aparece antes do da esquerda. Comece por E → E + T e resolva o T primeiro.",
                    Grammars.EXPR_LR, LevelSpec.Derive("id + id", RIGHTMOST),
                ),
                Level(
                    "w1l4", "Ordem livre",
                    "Derive \"b b a a\" expandindo os não-terminais na ordem que quiser.",
                    "A ordem dos passos muda, mas a árvore final é a mesma. Derivações à esquerda e à direita são só duas maneiras padronizadas de percorrer a mesma árvore.",
                    "O alvo começa com 'b'. Qual alternativa de S coloca o B na frente?",
                    "S -> A B | B A\nA -> a | a A\nB -> b | b B", LevelSpec.Derive("b b a a", FREE),
                ),
                Level(
                    "w1l5", "Caçador de ambiguidade",
                    "Encontre DUAS derivações mais à esquerda diferentes para \"id + id * id\".",
                    "Duas árvores = dois significados: (id + id) * id ou id + (id * id). Por isso a gramática de expressões \"de verdade\" usa E, T e F em camadas: cada camada é um nível de precedência.",
                    "Na primeira derivação comece com E → E + E. Na segunda, com E → E * E.",
                    Grammars.EXPR_AMB, LevelSpec.Derive("id + id * id", LEFTMOST, ambiguity = true),
                    Prediction(
                        "Quantas árvores de derivação diferentes você acha que existem para \"id + id * id\"?",
                        listOf("1", "2", "3"), 1,
                        "Existem 2: uma com o + no topo e outra com o * no topo.",
                    ),
                ),
                Level(
                    "w1l6", "O else pendurado",
                    "Ache duas derivações mais à esquerda para \"if if x else x\".",
                    "É o famoso dangling else: o else pode pertencer ao primeiro ou ao segundo if. Linguagens como C e Java resolvem com uma regra extra: o else casa com o if mais próximo.",
                    "Numa árvore o else fica com o if de fora (S → if S else S no topo); na outra, com o de dentro.",
                    "S -> if S | if S else S | x", LevelSpec.Derive("if if x else x", LEFTMOST, ambiguity = true),
                ),
            ),
        ),
        World(
            "w2", 2, "Laboratório FIRST", "Com o que cada não-terminal pode começar?", "🧪", VIOLET,
            Lesson(
                "FIRST em 1 minuto",
                listOf(
                    "FIRST(α) é o conjunto de terminais que podem aparecer no INÍCIO de alguma cadeia derivada de α. Se α pode virar ε, então ε ∈ FIRST(α).",
                    "Regra 1: se A → a…, então a ∈ FIRST(A).",
                    "Regra 2: se A → B…, tudo de FIRST(B), menos ε, entra em FIRST(A).",
                    "Regra 3 (o vazamento): se B pode virar ε, olhe também o símbolo seguinte. Em A → B C…, FIRST(C) também entra.",
                    "Regra 4: se TODOS os símbolos de uma alternativa podem sumir, ε ∈ FIRST(A).",
                    "Repita as regras até nada mudar: é um algoritmo de ponto fixo.",
                ),
                "A → B c | d     B → b | ε\nFIRST(B) = { b, ε }\nFIRST(A) = { b, c, d }  (o c vaza porque B some)",
            ),
            listOf(
                Level(
                    "w2l1", "Começos diretos",
                    "Marque os terminais de FIRST de cada não-terminal.",
                    "Quando uma alternativa começa com terminal, ele vai direto para o FIRST. Nada de olhar além!",
                    "Olhe só o primeiro símbolo de cada alternativa.",
                    "S -> a B | b\nB -> c | d S", LevelSpec.FirstSets,
                ),
                Level(
                    "w2l2", "Cascata",
                    "Calcule FIRST de S, A e B.",
                    "FIRST desce pela cadeia: S herda de A, que herda de B. Calcular de baixo para cima (B, depois A, depois S) economiza trabalho.",
                    "Comece por B, que só tem terminais. Depois use o resultado em A.",
                    "S -> A b\nA -> B c | a\nB -> d | e", LevelSpec.FirstSets,
                ),
                Level(
                    "w2l3", "O vazamento do ε",
                    "Calcule FIRST. Atenção: A e B podem sumir!",
                    "Como A e B podem virar ε, o 'c' consegue ser o primeiro símbolo de S. Mas ε ∉ FIRST(S): o 'c' nunca some.",
                    "Em S → A B c, se A e B sumirem, quem fica na frente?",
                    "S -> A B c\nA -> a | ε\nB -> b | ε", LevelSpec.FirstSets,
                    Prediction(
                        "Quantos terminais (sem contar ε) você acha que FIRST(S) terá?",
                        listOf("1", "2", "3"), 2,
                        "São 3: a (de A), b (quando A some) e c (quando A e B somem).",
                    ),
                ),
                Level(
                    "w2l4", "Expressões",
                    "A gramática clássica de expressões sem recursão à esquerda. Calcule todos os FIRST.",
                    "E, T e F começam do mesmo jeito: ( ou id. E' e T' são anuláveis — guarde isso, vai ser crucial no FOLLOW e na tabela.",
                    "F é a base. T começa com F; E começa com T.",
                    Grammars.EXPR_LL, LevelSpec.FirstSets,
                ),
                Level(
                    "w2l5", "Ponto fixo",
                    "S usa A, e A usa S. Calcule os FIRST mesmo assim.",
                    "Com recursão, uma passada não basta: repita as regras até nenhuma mudança acontecer. Esse é o algoritmo de ponto fixo que os geradores de parser implementam.",
                    "A pode sumir. Então em S → A a, o 'a' pode aparecer no começo de S. E, como A → S c, tudo de FIRST(S) também entra em FIRST(A).",
                    "S -> A a | b\nA -> S c | ε", LevelSpec.FirstSets,
                ),
            ),
        ),
        World(
            "w3", 3, "Laboratório FOLLOW", "O que pode vir depois de cada não-terminal?", "🧭", PINK,
            Lesson(
                "FOLLOW em 1 minuto",
                listOf(
                    "FOLLOW(A) é o conjunto de terminais que podem aparecer IMEDIATAMENTE depois de A em alguma forma sentencial. Nunca contém ε; pode conter $ (fim da entrada).",
                    "Regra 1: $ ∈ FOLLOW(símbolo inicial).",
                    "Regra 2: em X → α A β, tudo de FIRST(β), menos ε, entra em FOLLOW(A).",
                    "Regra 3: em X → α A (A no fim), ou se β pode virar ε, tudo de FOLLOW(X) entra em FOLLOW(A).",
                    "Dica de ouro: procure A nos LADOS DIREITOS, não nas produções de A!",
                ),
                "S → a A b | c A     A → d\nFOLLOW(A) = { b } ∪ FOLLOW(S) = { b, $ }",
            ),
            listOf(
                Level(
                    "w3l1", "Quem vem depois?",
                    "Marque os FOLLOW de S e A. O $ é o fim da entrada.",
                    "Depois de A vem b (em S → a A b) ou nada (em S → c A). \"Nada\" quer dizer: herda FOLLOW(S) = { $ }.",
                    "Encontre A nos lados direitos. O que aparece logo depois dele?",
                    "S -> a A b | c A\nA -> d", LevelSpec.FollowSets(),
                ),
                Level(
                    "w3l2", "Herança",
                    "Calcule FOLLOW. Repare que B pode sumir.",
                    "Como B pode sumir, o que segue S também pode seguir A. É a regra 3 disfarçada: β = B é anulável.",
                    "Em S → A B, depois de A vem FIRST(B). E se B sumir?",
                    "S -> A B\nA -> a\nB -> b | ε", LevelSpec.FollowSets(),
                ),
                Level(
                    "w3l3", "Parênteses",
                    "Um só não-terminal. Quem pode segui-lo?",
                    "Duas fontes: o ')' que fecha e o $ do fim. O S no final de ( S ) S herda FOLLOW(S) dele mesmo — sem problemas no ponto fixo.",
                    "O primeiro S de ( S ) S é seguido de ')'. E o segundo?",
                    Grammars.PARENS, LevelSpec.FollowSets(),
                ),
                Level(
                    "w3l4", "Expressões",
                    "Calcule FOLLOW para a gramática de expressões. Os FIRST estão aí para ajudar.",
                    "T ganha o '+' porque E' vem depois dele e FIRST(E') tem '+'; T' herda tudo de T. F ganha o '*' do mesmo jeito, via T'. E como E' e T' somem, os FOLLOW também são herdados.",
                    "Comece por E: ele aparece dentro de ( E ). Depois E' herda de E, T olha FIRST(E')...",
                    Grammars.EXPR_LL, LevelSpec.FollowSets(),
                ),
                Level(
                    "w3l5", "Sem rodinhas",
                    "Agora sem os FIRST na tela. Calcule FOLLOW de tudo.",
                    "Cada ocorrência de um não-terminal num lado direito é uma fonte de FOLLOW. B aparece duas vezes, e cada ocorrência contribui com algo diferente.",
                    "Em S → B A, o que vem depois de B é A — e A pode sumir.",
                    "S -> A B d | B A\nA -> a | ε\nB -> b | ε", LevelSpec.FollowSets(showFirst = false),
                ),
            ),
        ),
        World(
            "w4", 4, "Montador de Tabela LL(1)", "A tabela que guia o parser preditivo", "🧩", AMBER,
            Lesson(
                "Tabela LL(1) em 1 minuto",
                listOf(
                    "A tabela M[A, a] responde: com A no topo da pilha e 'a' como próximo token, qual produção usar?",
                    "Para cada produção A → α: coloque-a em M[A, a] para todo a ∈ FIRST(α).",
                    "Se α pode virar ε: coloque-a também em M[A, b] para todo b ∈ FOLLOW(A), inclusive $.",
                    "Célula vazia = erro de sintaxe. Duas produções na mesma célula = conflito: a gramática NÃO é LL(1).",
                ),
                "S → a S b (1) | ε (2)\nM[S, a] = 1     M[S, b] = 2     M[S, $] = 2",
            ),
            listOf(
                Level(
                    "w4l1", "Primeira tabela",
                    "Toque numa célula para colocar produções. Preencha a tabela LL(1).",
                    "Sem ε, só o FIRST de cada alternativa importa.",
                    "FIRST(a S b) = { a } e FIRST(c) = { c }.",
                    Grammars.ASB_C, LevelSpec.LL1Table(),
                ),
                Level(
                    "w4l2", "Quando usar o ε?",
                    "Agora com uma produção vazia. Onde ela entra?",
                    "A produção ε vai nas colunas de FOLLOW(S) = { b, $ }. Leia assim: \"se o próximo token pode vir depois de S, faça S sumir\".",
                    "FIRST(ε) não tem terminais. Use FOLLOW(S).",
                    Grammars.ANBN, LevelSpec.LL1Table(),
                ),
                Level(
                    "w4l3", "Tabela de expressões",
                    "A tabela completa das expressões. FIRST e FOLLOW estão disponíveis.",
                    "Todas as células têm no máximo uma produção: a gramática é LL(1). As produções ε de E' e T' ocupam as colunas de FOLLOW.",
                    "E' → ε vai em FOLLOW(E') = { ), $ }. T' → ε vai em FOLLOW(T') = { +, ), $ }.",
                    Grammars.EXPR_LL, LevelSpec.LL1Table(),
                ),
                Level(
                    "w4l4", "Detetive de conflitos",
                    "Monte a tabela e dê o veredito: é LL(1)?",
                    "S → A a e S → a disputam M[S, a]: como A pode sumir, as duas alternativas podem começar com 'a'. Conflitos às vezes se escondem atrás de um ε!",
                    "Calcule FIRST(A a). Lembre que A pode virar ε.",
                    "S -> A a | a\nA -> b | ε", LevelSpec.LL1Table(askVerdict = true),
                    Prediction(
                        "Olhando rápido: essa gramática é LL(1)?",
                        listOf("Sim", "Não"), 1,
                        "Não é: S → A a e S → a entram ambas em M[S, a].",
                    ),
                ),
                Level(
                    "w4l5", "O else pendurado",
                    "Tabela da gramática do if-then-else (i = if, t = then, e = else).",
                    "O conflito em M[S', e] é a ambiguidade do else. Na prática, o parser escolhe S' → e S: o else fica com o if mais próximo.",
                    "FOLLOW(S') herda FOLLOW(S). E o que pode vir logo depois do S em S → i E t S S'?",
                    Grammars.IF_ELSE, LevelSpec.LL1Table(askVerdict = true),
                ),
            ),
        ),
        World(
            "w5", 5, "Você é o Parser: Top-Down", "Pilha, tabela e derivação mais à esquerda", "🤖", GREEN,
            Lesson(
                "Parser preditivo em 1 minuto",
                listOf(
                    "O parser preditivo usa uma pilha. Começa com S $ e lê a entrada da esquerda para a direita.",
                    "Topo é não-terminal A e o próximo token é a: troque A pelo lado direito de M[A, a]. O primeiro símbolo fica no topo.",
                    "Topo é terminal: se for igual ao token, casa (match) e avança; senão, erro.",
                    "Topo $ e entrada $: aceita!",
                    "Truque: entrada já lida + pilha = forma sentencial da derivação mais à esquerda.",
                ),
                "Pilha: S $     Entrada: a b $\nM[S, a] = S → a S b  ⇒  Pilha: a S b $",
            ),
            listOf(
                Level(
                    "w5l1", "Aquecimento",
                    "Você é o parser. Escolha cada movimento para reconhecer \"a a b b\".",
                    "A pilha guarda o que ainda falta reconhecer. O topo à esquerda + a entrada já lida formam a derivação mais à esquerda.",
                    "Com S no topo e 'a' na entrada, M[S, a] = S → a S b. Com 'b' na entrada, S some.",
                    Grammars.ANBN, LevelSpec.LL1Parse("a a b b"),
                ),
                Level(
                    "w5l2", "Expressões I",
                    "Analise \"id + id\". Às vezes o jogo vai perguntar POR QUE você escolheu.",
                    "Quando E' ou T' somem, a justificativa é sempre FOLLOW: o próximo token pode vir depois deles.",
                    "Consulte M[topo, token]. Quando o topo é T' e o token é '+', qual produção está lá?",
                    Grammars.EXPR_LL, LevelSpec.LL1Parse("id + id", askWhy = true),
                ),
                Level(
                    "w5l3", "Expressões II",
                    "Analise \"( id ) * id\".",
                    "Os parênteses criam uma nova expressão E no meio da pilha. A pilha funciona como a pilha de chamadas de um parser descendente recursivo.",
                    "Depois de F → ( E ), o '(' está no topo: case com a entrada.",
                    Grammars.EXPR_LL, LevelSpec.LL1Parse("( id ) * id", askWhy = true),
                ),
                Level(
                    "w5l4", "Caça ao erro",
                    "A entrada \"id + * id\" tem um erro. Declare o erro no momento certo — nem antes, nem depois.",
                    "O erro aparece assim que M[T, *] está vazia — o mais cedo possível. Parsers LL(1) têm a propriedade do prefixo válido: nunca aceitam um prefixo que não possa ser completado.",
                    "Siga normalmente até ter um não-terminal no topo cuja célula para o token atual está vazia.",
                    Grammars.EXPR_LL, LevelSpec.LL1Parse("id + * id"),
                    Prediction(
                        "Em qual token o parser vai perceber o erro?",
                        listOf("No primeiro id", "No +", "No *", "No último id"), 2,
                        "No *: com T no topo, M[T, *] está vazia.",
                    ),
                ),
                Level(
                    "w5l5", "Sem tabela!",
                    "Analise \"id * ( id + id )\" sem ver a tabela. Você só tem FIRST e FOLLOW.",
                    "Você acabou de montar a tabela de cabeça. É o que um parser descendente recursivo faz com if/switch dentro de cada função.",
                    "Pergunte: o token está em FIRST da alternativa? Se não, a alternativa pode sumir e o token está no FOLLOW?",
                    Grammars.EXPR_LL, LevelSpec.LL1Parse("id * ( id + id )", showTable = false),
                ),
            ),
        ),
        World(
            "w6", 6, "Cirurgia de Gramáticas", "Recursão à esquerda e fatoração", "✂️", ORANGE,
            Lesson(
                "Cirurgia em 1 minuto",
                listOf(
                    "Top-down não aguenta recursão à esquerda: com A → A α, o parser expande A para sempre sem consumir nada.",
                    "Receita: A → A α | β  vira  A → β A'  e  A' → α A' | ε.",
                    "Fatoração à esquerda: A → α β1 | α β2  vira  A → α A'  e  A' → β1 | β2.",
                    "Edite as produções à vontade. O verificador testa: sem recursão à esquerda, MESMA linguagem (todas as cadeias até um tamanho) e, quando pedido, LL(1).",
                ),
                "E → E + T | T\n⇒ E → T E'     E' → + T E' | ε",
            ),
            listOf(
                Level(
                    "w6l1", "Primeira cirurgia",
                    "Elimine a recursão à esquerda de A. Use o novo não-terminal A'.",
                    "A gera b seguido de vários a. Em vez de crescer para a esquerda (A a a), A' cresce para a direita (a a A').",
                    "A linguagem é b a a a… Então A começa com b e depois vem uma repetição de 'a' que pode acabar (ε).",
                    "A -> A a | b", LevelSpec.Surgery(listOf("A'")),
                ),
                Level(
                    "w6l2", "Listas",
                    "Listas separadas por vírgula. Tire a recursão à esquerda.",
                    "Esse padrão (item, depois \"separador item\" repetido) aparece em toda linguagem: parâmetros, argumentos, declarações...",
                    "β = id e α = , id.",
                    "L -> L , id | id", LevelSpec.Surgery(listOf("L'")),
                ),
                Level(
                    "w6l3", "Expressões",
                    "A grande cirurgia: E e T são recursivos à esquerda. Use E' e T'.",
                    "Você reconstruiu a gramática LL(1) de expressões. A precedência (* antes de +) sobreviveu à cirurgia!",
                    "Aplique a receita duas vezes, uma em E e outra em T. F não muda.",
                    Grammars.EXPR_LR, LevelSpec.Surgery(listOf("E'", "T'"), maxLen = 5),
                    Prediction(
                        "Depois da cirurgia, quantos não-terminais a gramática terá?",
                        listOf("3", "5", "6"), 1,
                        "5: E, E', T, T' e F.",
                    ),
                ),
                Level(
                    "w6l4", "Prefixo comum",
                    "Duas alternativas começam iguais. Fatore à esquerda até a gramática ser LL(1).",
                    "Com um só token de lookahead não dá para escolher entre a b c e a b d. Fatorando, você adia a decisão até o ponto em que elas diferem.",
                    "O prefixo comum é \"a b\".",
                    "S -> a b c | a b d | e", LevelSpec.Surgery(listOf("S'"), requireNoCommonPrefix = true),
                ),
                Level(
                    "w6l5", "Fatorando o if",
                    "Fatore o if-then-else. Não precisa virar LL(1) — só tirar o prefixo comum.",
                    "Mesmo fatorada, a gramática continua sem ser LL(1): a ambiguidade do else não se resolve com fatoração. Fatoração só resolve prefixos comuns.",
                    "O prefixo comum é \"i E t S\". O que sobra em cada alternativa: nada (ε) ou \"e S\".",
                    "S -> i E t S | i E t S e S | a\nE -> b",
                    LevelSpec.Surgery(listOf("S'"), requireLL1 = false, requireNoCommonPrefix = true, maxLen = 7),
                ),
            ),
        ),
        World(
            "w7", 7, "Shift-Reduce: Bottom-Up", "Empilhar, reduzir e caçar handles", "🏗️", BLUE,
            Lesson(
                "Bottom-up em 1 minuto",
                listOf(
                    "O parser bottom-up constrói a árvore das folhas para a raiz.",
                    "SHIFT: empilha o próximo token.",
                    "REDUCE A → β: quando o topo da pilha termina com β (o handle), troca β por A.",
                    "ACCEPT: a pilha tem só o símbolo inicial e a entrada acabou.",
                    "A arte é saber QUANDO reduzir. No SLR: reduza por A → β só se o próximo token ∈ FOLLOW(A).",
                    "Pilha + entrada restante = forma sentencial da derivação mais à direita (ao contrário).",
                ),
                "Pilha: $ a c     Entrada: b $\nc é handle de S → c  ⇒  Pilha: $ a S",
            ),
            listOf(
                Level(
                    "w7l1", "Empilhar e reduzir",
                    "Analise \"a c b\" de baixo para cima: empilhe tokens e reduza handles.",
                    "Repare na árvore crescendo de baixo para cima: primeiro S → c, depois S → a S b. É a derivação mais à direita lida ao contrário.",
                    "Empilhe até ver no topo um lado direito completo.",
                    Grammars.ASB_C, LevelSpec.ShiftReduce("a c b"),
                ),
                Level(
                    "w7l2", "Soma",
                    "Analise \"id + id\". Os FOLLOW estão na tela para ajudar a decidir quando reduzir.",
                    "Reduzir id até E antes de empilhar o + é obrigatório: só E pode ser seguido de + no topo da pilha (E + T).",
                    "Com id no topo e '+' na entrada: F → id, depois T → F, depois E → T... pare quando for a hora de empilhar.",
                    Grammars.EXPR_LR, LevelSpec.ShiftReduce("id + id", showFollow = true),
                ),
                Level(
                    "w7l3", "Precedência",
                    "Analise \"id + id * id\". Cuidado com o momento de reduzir.",
                    "Com E + T na pilha e '*' na entrada, reduzir E → E + T seria errado: '*' ∉ FOLLOW(E). É assim que a gramática faz o * ter precedência sobre o +.",
                    "Antes de cada redução, pergunte: o próximo token pode seguir o não-terminal que vou criar?",
                    Grammars.EXPR_LR, LevelSpec.ShiftReduce("id + id * id", showFollow = true),
                    Prediction(
                        "Quando a pilha for \"E + T\" e o próximo token for '*', o parser vai...",
                        listOf("Reduzir por E → E + T", "Empilhar o *", "Declarar erro"), 1,
                        "Empilhar: '*' ∉ FOLLOW(E), então não é hora de reduzir. O T ainda vai crescer para T * F.",
                    ),
                ),
                Level(
                    "w7l4", "Com o autômato",
                    "Analise \"( id + id ) * id\". Agora a pilha mostra os estados e a tabela SLR está disponível.",
                    "Os estados na pilha resumem tudo o que foi lido. Consultar ACTION[estado, token] é tudo que o parser precisa — nada de procurar handles à mão.",
                    "Olhe ACTION[estado do topo, próximo token]. sN = empilhar e ir ao estado N; rN = reduzir pela produção N.",
                    Grammars.EXPR_LR, LevelSpec.ShiftReduce("( id + id ) * id", showStates = true, showTable = true),
                ),
                Level(
                    "w7l5", "Erro na linha",
                    "\"id + + id\" tem um erro. Detecte-o no momento exato.",
                    "O erro aparece quando ACTION fica vazia: com E + na pilha, o parser espera o começo de um T, e '+' não pode começar um T.",
                    "Siga a tabela até achar uma célula vazia.",
                    Grammars.EXPR_LR, LevelSpec.ShiftReduce("id + + id", showStates = true, showTable = true),
                ),
            ),
        ),
        World(
            "w8", 8, "Fábrica de Itens LR", "Itens, fechamento, goto e tabela SLR", "🏭", LIME,
            Lesson(
                "Itens LR(0) em 1 minuto",
                listOf(
                    "Um item LR(0) é uma produção com um ponto: E → E • + T significa \"já vi E, espero + T\".",
                    "Fechamento: se há • antes de um não-terminal B, adicione B → • γ para toda produção de B. Repita.",
                    "goto(I, X): pegue os itens de I com • antes de X, avance o ponto e faça o fechamento.",
                    "Os conjuntos de itens são os estados de um autômato que reconhece os prefixos viáveis — tudo o que pode estar na pilha.",
                    "Tabela SLR: transição com terminal → shift; transição com não-terminal → goto; item completo A → α • → reduce nas colunas de FOLLOW(A); S' → S • → accept no $.",
                ),
                "closure({ S' → • S }) com S → ( L ) | x:\nS' → • S     S → • ( L )     S → • x",
            ),
            listOf(
                Level(
                    "w8l1", "Primeiros itens",
                    "Toque nos espaços entre os símbolos para colocar os pontos (•) de cada item do conjunto pedido.",
                    "O fechamento é uma \"previsão\": se espero um L, posso estar no começo de qualquer produção de L — e, recursivamente, de qualquer coisa com que L comece.",
                    "Comece pelo item do núcleo e siga: • antes de S? Adicione todas as produções de S com o ponto no início.",
                    Grammars.LIST_LR0,
                    LevelSpec.Closure(listOf(ClosureQuestion.Initial, ClosureQuestion.Goto(0, "("), ClosureQuestion.Goto(4, ","))),
                ),
                Level(
                    "w8l2", "Itens de expressões",
                    "Três conjuntos da gramática de expressões LR.",
                    "O estado de F → ( E • ) não precisa de fechamento: o ponto está antes de terminais. Nem todo goto cresce!",
                    "Em goto(I1, +): avance o ponto sobre o + em E → E • + T e feche: • antes de T puxa T, que puxa F.",
                    Grammars.EXPR_LR,
                    LevelSpec.Closure(listOf(ClosureQuestion.Initial, ClosureQuestion.Goto(1, "+"), ClosureQuestion.Goto(4, "E"))),
                ),
                Level(
                    "w8l3", "Tabela SLR",
                    "Preencha ACTION e GOTO. Os estados do autômato estão na tela.",
                    "Cada estado com item completo reduz só nas colunas de FOLLOW. Se reduzisse em todas as colunas, seria uma tabela LR(0).",
                    "FOLLOW(S) = { b, $ }. Os estados 3 e 5 têm itens completos.",
                    Grammars.ASB_C, LevelSpec.SlrTable,
                ),
                Level(
                    "w8l4", "Tabela SLR II",
                    "Uma tabela maior: listas entre parênteses.",
                    "Esta gramática é LR(0): nenhum estado mistura item completo com shift. O FOLLOW nem seria necessário — mas a tabela SLR fica mais enxuta e detecta erros mais cedo.",
                    "FOLLOW(S) = { ), ,, $ } e FOLLOW(L) = { ), , }.",
                    Grammars.LIST_LR0, LevelSpec.SlrTable,
                ),
                Level(
                    "w8l5", "Onde está o conflito?",
                    "Marque as células em que a tabela SLR teria DUAS ações.",
                    "No estado com S → L • = R e R → L •, '=' ∈ FOLLOW(R), então o SLR manda reduzir e empilhar. Mas nenhuma forma sentencial começa com R = ...! O FOLLOW é grosseiro demais; LALR e LR(1) guardam lookaheads por item e resolvem o conflito.",
                    "Procure um estado que tenha um item completo e também uma transição por um terminal que está no FOLLOW daquele não-terminal.",
                    Grammars.POINTERS, LevelSpec.SlrConflict,
                    Prediction(
                        "Essa gramática de atribuição com ponteiros é SLR(1)?",
                        listOf("Sim", "Não"), 1,
                        "Não: há um conflito shift/reduce com '='.",
                    ),
                ),
            ),
        ),
        World(
            "w9", 9, "O Classificador", "Chefão final: LL(1), LR(0), SLR, LALR ou LR(1)?", "👑", GOLD,
            Lesson(
                "A hierarquia em 1 minuto",
                listOf(
                    "LR(0) ⊂ SLR(1) ⊂ LALR(1) ⊂ LR(1). Toda gramática LL(1) também é LR(1).",
                    "Recursão à esquerda ou prefixo comum ⇒ não é LL(1). Mas LR adora recursão à esquerda!",
                    "Conflito no LR(0) que some no SLR ⇒ o FOLLOW resolveu.",
                    "Conflito no SLR que some no LALR ⇒ o lookahead por item resolveu.",
                    "Conflito só no LALR ⇒ a fusão de estados do LR(1) criou um reduce/reduce.",
                    "Gramática ambígua não é LL(1) nem LR(k) para nenhum k.",
                ),
                null,
            ),
            listOf(
                Level(
                    "w9l1", "Expressões LR",
                    "Marque TODAS as classes a que a gramática pertence.",
                    "Recursão à esquerda mata o LL(1), mas é ótima para LR. O LR(0) falha porque E → T • e T → T • * F dividem um estado; o FOLLOW resolve.",
                    "Tem recursão à esquerda? O estado com E → T • também tem T → T • * F?",
                    Grammars.EXPR_LR, LevelSpec.Classify,
                ),
                Level(
                    "w9l2", "Listas LR(0)",
                    "Classifique a gramática das listas.",
                    "Sem nenhum conflito no LR(0), ela pertence a todas as classes LR. Mas a recursão à esquerda em L continua impedindo LL(1).",
                    "Algum estado do autômato LR(0) tem item completo junto com outro item?",
                    Grammars.LIST_LR0, LevelSpec.Classify,
                ),
                Level(
                    "w9l3", "Expressões LL",
                    "Classifique a gramática de expressões sem recursão à esquerda.",
                    "É LL(1) e também SLR. Mas não é LR(0): produções ε criam itens completos (E' → •) junto com shifts no mesmo estado.",
                    "Produções ε geram itens completos logo que o estado é criado...",
                    Grammars.EXPR_LL, LevelSpec.Classify,
                ),
                Level(
                    "w9l4", "Ponteiros",
                    "Classifique a gramática L = R.",
                    "O exemplo clássico LALR mas não SLR: no estado com S → L • = R e R → L •, o lookahead exato de R → L • é só $, não todo o FOLLOW(R) = { =, $ }.",
                    "Lembre do conflito do Mundo 8. O LALR resolve?",
                    Grammars.POINTERS, LevelSpec.Classify,
                ),
                Level(
                    "w9l5", "Gêmeos fundidos",
                    "Uma gramática traiçoeira. Classifique.",
                    "LR(1) sim, LALR não: os estados de A → c • e B → c • têm o mesmo núcleo, e ao fundi-los os lookaheads d e e se misturam — conflito reduce/reduce.",
                    "Depois de \"a c\", o c é A ou B? Depende do próximo token. E depois de \"b c\"?",
                    Grammars.LR1_NOT_LALR, LevelSpec.Classify,
                ),
                Level(
                    "w9l6", "O chefão ambíguo",
                    "A última: expressões ambíguas.",
                    "Ambígua ⇒ nenhuma classe. Geradores como yacc/bison aceitam essa gramática só porque você declara precedência (%left '+') para resolver os conflitos à mão.",
                    "Uma gramática ambígua pode ser LR(k)?",
                    Grammars.EXPR_AMB, LevelSpec.Classify,
                ),
            ),
        ),
    )

    val allLevels: List<Level> = worlds.flatMap { it.levels }

    fun world(id: String) = worlds.first { it.id == id }
    fun level(id: String) = allLevels.first { it.id == id }
    fun worldOf(levelId: String) = worlds.first { w -> w.levels.any { it.id == levelId } }

    fun nextLevel(id: String): Level? {
        val i = allLevels.indexOfFirst { it.id == id }
        return allLevels.getOrNull(i + 1)
    }

    /** Fases de um mundo abrem em sequência; a primeira de cada mundo está sempre aberta. */
    fun isUnlocked(profile: Profile, levelId: String): Boolean {
        val w = worldOf(levelId)
        val i = w.levels.indexOfFirst { it.id == levelId }
        return i == 0 || profile.isDone(w.levels[i - 1].id)
    }
}
