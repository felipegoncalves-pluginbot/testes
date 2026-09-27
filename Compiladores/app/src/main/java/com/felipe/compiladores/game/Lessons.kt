package com.felipe.compiladores.game

import com.felipe.compiladores.game.Scene.BrainPower
import com.felipe.compiladores.game.Scene.Fireside
import com.felipe.compiladores.game.Scene.KeyPoints
import com.felipe.compiladores.game.Scene.NoDumbQuestions
import com.felipe.compiladores.game.Scene.Talk
import com.felipe.compiladores.game.Scene.WatchIt

/**
 * Aulas no estilo "Head First": conversa em primeira pessoa com o mascote (Parsy),
 * uma ideia por cena, imagem animada junto da palavra, pausas para pensar,
 * armadilhas comuns e um resumo curto no final.
 */
object Lessons {

    val w1 = Lesson(
        "Gramáticas são receitas",
        listOf(
            Talk("Oi! Eu sou o *Parsy*, um parser em treinamento. Meu trabalho: descobrir se uma frase segue as regras de uma linguagem. Me ajuda?", Mood.HAPPY),
            Talk(
                "Uma gramática é um *livro de receitas*: cada regra diz \"troque isto por aquilo\". Olha só eu seguindo `S → a S b` duas vezes e depois `S → ε`:",
                Mood.HAPPY, Visual.Derivation(Grammars.ANBN, listOf("S", "a S b", "a a S b b", "a a b b")),
            ),
            Talk(
                "Repare nas cores: *não-terminais* como `S` ainda vão ser trocados. *Terminais* como `a` são as palavras de verdade — nunca mudam. E `ε` é o nada, a cadeia vazia.",
                Mood.THINK, Visual.Legend,
            ),
            BrainPower("Quantos passos para gerar a a a b b b com essa receita?", "4 passos: três vezes S → a S b e uma vez S → ε."),
            Talk(
                "Se eu sempre troco o não-terminal *mais à esquerda*, faço uma *derivação à esquerda*. É assim que um parser *top-down* pensa.",
                Mood.HAPPY, Visual.Derivation(Grammars.EXPR_LR, listOf("E", "E + T", "T + T", "F + T", "id + T", "id + F", "id + id")),
            ),
            WatchIt("Derivação *mais à direita* não é ler de trás para frente! Você ainda começa em `S`; só escolhe o não-terminal mais à direita para trocar."),
            Talk(
                "E se a mesma frase tiver *duas árvores*? Aí a gramática é *ambígua*: `id + id * id` pode ser (id + id) * id ou id + (id * id). O compilador não saberia o que você quis dizer!",
                Mood.SURPRISED, Visual.TwoTrees(Grammars.EXPR_AMB, "id + id * id"),
            ),
            NoDumbQuestions(
                listOf(
                    "Ambiguidade é culpa da linguagem ou da gramática?" to "Quase sempre da gramática. Dá para reescrevê-la sem mudar a linguagem — é o que a gramática E, T, F faz.",
                    "Por que o parser não tenta todas as opções?" to "Porque tentar e voltar atrás (backtracking) é caro. A graça dos parsers LL e LR é nunca voltar atrás.",
                ),
            ),
            KeyPoints(
                listOf(
                    "Derivar = trocar um não-terminal pelo lado direito de uma regra.",
                    "À esquerda: troque sempre o não-terminal mais à esquerda (top-down).",
                    "À direita: troque o mais à direita (é o que o bottom-up desfaz).",
                    "Duas árvores para a mesma frase = gramática ambígua.",
                ),
            ),
        ),
    )

    val wf = Lesson(
        "Montando sua fábrica",
        listOf(
            Talk("Até agora você *usou* gramáticas. Agora vai *construir* uma! Pense numa fábrica: cada produção é uma máquina da linha.", Mood.HAPPY),
            Talk(
                "A esteira traz frases de teste. As verdes precisam sair *aceitas*; as vermelhas, *rejeitadas*. Sua fábrica só é aprovada se acertar todas.",
                Mood.THINK, Visual.Conveyor,
            ),
            Talk(
                "Truque 1: *repetição é recursão*. `A → a A | a` gera a, a a, a a a…",
                Mood.HAPPY, Visual.Derivation("A -> a A | a", listOf("A", "a A", "a a A", "a a a")),
            ),
            Talk("Truque 2: *coisas casadas* (como parênteses) nascem juntas na mesma regra: `S → ( S )` põe as duas pontas de uma vez.", Mood.HAPPY),
            BrainPower("Como gerar id , id , id (uma lista separada por vírgulas)?", "L → id | id , L. Ou, para um parser top-down: L → id L' e L' → , id L' | ε."),
            WatchIt("Passar só nos testes visíveis não basta: a fábrica tem *testes ocultos* que comparam todas as frases curtas. Pense na linguagem inteira, não nos exemplos."),
            KeyPoints(
                listOf(
                    "Um ou mais: A → x A | x. Zero ou mais: A → x A | ε.",
                    "Pares casados: coloque as duas pontas na mesma regra.",
                    "Divida o trabalho entre não-terminais, como máquinas numa linha.",
                ),
            ),
        ),
    )

    val w2 = Lesson(
        "FIRST: quem chega primeiro?",
        listOf(
            Talk("Para escolher a regra certa olhando só a *próxima palavra*, eu preciso saber: com o que cada não-terminal pode *começar*? Isso é o *FIRST*.", Mood.THINK),
            Talk(
                "Pense numa *fila*. O primeiro da fila entra no FIRST. Mas se o primeiro pode *sumir* (virar ε), o próximo também tem chance!",
                Mood.HAPPY, Visual.FirstQueue("S", listOf("A", "B", "c"), setOf(0, 1)),
            ),
            BrainPower("S → A b e A → a | ε. Quem está em FIRST(S)?", "{ a, b }. Se A sumir, o b fica na frente da fila."),
            WatchIt("ε só entra em FIRST(S) se *todos* da fila podem sumir. Em S → A b o b nunca some, então ε ∉ FIRST(S)."),
            NoDumbQuestions(
                listOf(
                    "Por que calcular várias vezes?" to "Porque um FIRST depende do outro. Você repete as regras até nada mudar: é um ponto fixo.",
                    "E o FIRST de um terminal?" to "É ele mesmo: FIRST(a) = { a }.",
                ),
            ),
            KeyPoints(
                listOf(
                    "A → a…  ⇒  a ∈ FIRST(A).",
                    "A → B…  ⇒  FIRST(B) − ε entra em FIRST(A).",
                    "Se B some, olhe o próximo da fila.",
                    "Todos somem ⇒ ε ∈ FIRST(A).",
                ),
            ),
        ),
    )

    val w3 = Lesson(
        "FOLLOW: quem vem depois?",
        listOf(
            Talk("FIRST olha o *começo*. FOLLOW olha *quem vem depois*: que token pode aparecer logo depois de um não-terminal?", Mood.THINK),
            Talk(
                "O segredo: procure o não-terminal nos *lados direitos*. O vizinho da direita entra no FOLLOW.",
                Mood.HAPPY, Visual.FollowSpot("S", listOf("a", "A", "b"), 1),
            ),
            Talk(
                "E se ele estiver no *fim* da regra? Então quem segue o lado esquerdo também segue ele. É uma *herança*: FOLLOW(S) ⊆ FOLLOW(A).",
                Mood.SURPRISED, Visual.FollowSpot("S", listOf("c", "A"), 1),
            ),
            Talk("E o `$`? É o *fim da frase*. Ele sempre segue o símbolo inicial.", Mood.HAPPY),
            WatchIt("FOLLOW *nunca* tem ε. Se você marcou ε num FOLLOW, apague!"),
            BrainPower("S → A B e B → b | ε. Quem está em FOLLOW(A)?", "{ b, $ }: b vem de FIRST(B); como B pode sumir, A herda FOLLOW(S) = { $ }."),
            KeyPoints(
                listOf(
                    "$ ∈ FOLLOW(símbolo inicial).",
                    "X → … A β  ⇒  FIRST(β) − ε entra em FOLLOW(A).",
                    "A no fim (ou β some)  ⇒  FOLLOW(X) entra em FOLLOW(A).",
                    "Procure A à DIREITA das setas.",
                ),
            ),
        ),
    )

    val w4 = Lesson(
        "A cola do parser",
        listOf(
            Talk(
                "A tabela LL(1) é a minha *cola*: linha = não-terminal no topo da pilha, coluna = próximo token. A célula diz qual regra usar.",
                Mood.HAPPY, Visual.TableLookup(Grammars.EXPR_LL),
            ),
            Talk("Regra de ouro 1: `A → α` vai nas colunas de *FIRST(α)*.", Mood.THINK),
            Talk("Regra de ouro 2: se `α` pode sumir, `A → α` vai também nas colunas de *FOLLOW(A)*. Leia assim: \"se o próximo token pode vir depois de A, faça A sumir\".", Mood.THINK),
            Talk("Duas regras na mesma célula = *conflito*. Eu fico em dúvida… e a gramática *não é LL(1)*.", Mood.SURPRISED),
            BrainPower("S → a A | a B. Onde está o conflito?", "Em M[S, a]: as duas alternativas começam com a."),
            KeyPoints(
                listOf(
                    "Linha = topo da pilha; coluna = próximo token.",
                    "A → α nas colunas de FIRST(α).",
                    "Se α ⇒* ε, também nas colunas de FOLLOW(A).",
                    "Célula com duas regras = não é LL(1).",
                ),
            ),
        ),
    )

    val w5 = Lesson(
        "Pilha na mão",
        listOf(
            Talk(
                "Agora eu leio a frase de verdade. Tenho uma *pilha* com o que ainda falta reconhecer.",
                Mood.HAPPY, Visual.StackOps(listOf("+$", "+S", "-", "+b", "+S", "+a")),
            ),
            Talk(
                "Topo é não-terminal? Consulto a tabela e troco. Topo é terminal? Tem que ser igual ao token: *casa* e avança. Veja:",
                Mood.THINK, Visual.LLRun(Grammars.ANBN, "a b"),
            ),
            WatchIt("Empilhe o lado direito *ao contrário*: o primeiro símbolo precisa ficar no topo."),
            Talk(
                "Truque de mestre: entrada já lida + pilha = a forma sentencial da derivação à esquerda. A árvore cresce *de cima para baixo*.",
                Mood.HAPPY, Visual.Derivation(Grammars.EXPR_LL, listOf("E", "T E'", "F T' E'", "id T' E'", "id E'", "id")),
            ),
            NoDumbQuestions(
                listOf(
                    "E o parser recursivo que o professor mostrou?" to "É o mesmo algoritmo! A pilha vira a pilha de chamadas de função. Você vai programar um no Robô Descendente.",
                ),
            ),
            KeyPoints(
                listOf(
                    "Começa com S $ na pilha.",
                    "Não-terminal no topo: troca pela regra de M[topo, token].",
                    "Terminal no topo: casa com o token ou dá erro.",
                    "$ e $: aceita!",
                ),
            ),
        ),
    )

    val wr = Lesson(
        "Programando o robô",
        listOf(
            Talk("Chega de tabela: vamos *programar* um robô parser! Cada não-terminal vira uma *função*.", Mood.HAPPY),
            Talk(
                "Cada alternativa vira um `if`. A pergunta do `if` é: o token atual está no FIRST dessa alternativa?",
                Mood.THINK,
                Visual.Code(listOf("def E():", "    if token in { (, id }:", "        T(); E'()", "    else: erro()", "", "def E'():", "    if token in { + }:", "        casa('+'); T(); E'()", "    elif token in { ), $ }:", "        return   # ε", "    else: erro()")),
            ),
            Talk("E a regra ε? Vira um `return`, escolhido pelos tokens de *FOLLOW*. Ou seja: as condições dos ifs *são a tabela LL(1)*.", Mood.SURPRISED),
            WatchIt("Um `else: return` também funciona nas frases certas, mas o robô descobre os erros mais tarde. Use FOLLOW para errar cedo."),
            KeyPoints(
                listOf(
                    "Não-terminal = função; alternativa = if.",
                    "Condição = FIRST do lado direito.",
                    "Ramo ε = return quando o token está em FOLLOW.",
                    "Terminal no corpo = casa(token) ou erro.",
                ),
            ),
        ),
    )

    val w6 = Lesson(
        "Cirurgia de gramáticas",
        listOf(
            Talk(
                "Olha o que acontece comigo com `E → E + T`: eu expando E… que vira E + T… que vira E + T + T… *para sempre*, sem ler nada!",
                Mood.SAD, Visual.LeftRecursion,
            ),
            Talk(
                "A cura: fazer a repetição crescer *para a direita*. `A → A α | β` vira `A → β A'` e `A' → α A' | ε`.",
                Mood.HAPPY, Visual.Derivation("A -> b A'\nA' -> a A' | ε", listOf("A", "b A'", "b a A'", "b a a A'", "b a a")),
            ),
            Talk("Prefixo comum? Em `A → a b | a c` eu não sei qual escolher olhando só o `a`. *Fatore*: `A → a A'` e `A' → b | c`.", Mood.THINK),
            BrainPower("L → L , id | id. Como fica sem recursão à esquerda?", "L → id L'  e  L' → , id L' | ε."),
            KeyPoints(
                listOf(
                    "Recursão à esquerda trava o top-down.",
                    "A → A α | β  ⇒  A → β A',  A' → α A' | ε.",
                    "A → α β1 | α β2  ⇒  A → α A',  A' → β1 | β2.",
                    "A linguagem não muda — só a forma da gramática.",
                ),
            ),
        ),
    )

    val w7 = Lesson(
        "Tetris de símbolos",
        listOf(
            Talk("Agora ao contrário: construir a árvore *de baixo para cima*, juntando peças como num *Tetris de símbolos*.", Mood.HAPPY),
            Talk(
                "*Shift* puxa o próximo token para a pilha. *Reduce* troca o lado direito que está no topo (o *handle*) pelo lado esquerdo. Olha:",
                Mood.THINK, Visual.SRRun(Grammars.ASB_C, "a c b"),
            ),
            Talk("Quando reduzir? No SLR: só se o próximo token pode vir *depois* do não-terminal que vai nascer (FOLLOW).", Mood.THINK),
            Fireside(
                "LL", "LR",
                listOf(
                    true to "Eu decido a regra logo no começo, olhando um token só.",
                    false to "Eu espero ver a regra inteira na pilha antes de decidir. Sou paciente.",
                    true to "Mas eu sou fácil de escrever à mão!",
                    false to "E eu aceito mais gramáticas, até com recursão à esquerda. Por isso o yacc e o bison usam o meu jeito.",
                    true to "Pelo menos eu aviso os erros cedo.",
                    false to "Eu também! Nós dois paramos no primeiro token impossível.",
                ),
            ),
            WatchIt("Reduzir cedo demais enterra o handle. Com E + T na pilha e * chegando, reduzir E → E + T é armadilha: * ∉ FOLLOW(E)."),
            KeyPoints(
                listOf(
                    "Shift: empilha o token.",
                    "Reduce A → β: β no topo vira A.",
                    "SLR: reduza só se o próximo token ∈ FOLLOW(A).",
                    "Pilha + resto da entrada = derivação à direita, ao contrário.",
                ),
            ),
        ),
    )

    val w8 = Lesson(
        "Itens: o ponto que anda",
        listOf(
            Talk(
                "Como o parser LR sabe que o topo é um handle? Com *itens*: regras com um *ponto* marcando o progresso.",
                Mood.THINK, Visual.DotWalk("E", listOf("E", "+", "T")),
            ),
            Talk(
                "Se o ponto está antes de um não-terminal, eu posso estar no começo de *qualquer* regra dele. Isso é o *fechamento*:",
                Mood.HAPPY, Visual.Closure(Grammars.LIST_LR0),
            ),
            Talk("Andar com o ponto por cima de um símbolo X é o *goto*. Cada conjunto de itens vira um *estado* de um autômato.", Mood.HAPPY),
            WatchIt("No fechamento, *repita*: um item novo pode puxar outros. Se L → • S apareceu, as regras de S também entram."),
            KeyPoints(
                listOf(
                    "Item = regra com ponto: A → α • β.",
                    "Fechamento: • antes de B ⇒ adicione B → • γ.",
                    "goto(I, X): avance o ponto sobre X e feche.",
                    "Transição com terminal = shift; item completo = reduce.",
                ),
            ),
        ),
    )

    val w9 = Lesson(
        "Bonecas russas",
        listOf(
            Talk(
                "As famílias de parsers são como *bonecas russas*: LR(0) ⊂ SLR ⊂ LALR ⊂ LR(1). E LL(1) também cabe dentro do LR(1).",
                Mood.HAPPY, Visual.Hierarchy,
            ),
            Talk("Para classificar, pense no que resolve cada conflito: o FOLLOW (SLR), o lookahead por item (LALR/LR(1)) ou nada (ambígua).", Mood.THINK),
            NoDumbQuestions(
                listOf(
                    "Qual é o mais usado na prática?" to "LALR (yacc, bison) e o descendente recursivo escrito à mão, usado em muitos compiladores modernos.",
                    "Ambígua pode ser LR?" to "Nunca. Ambígua não é LL(k) nem LR(k) para nenhum k.",
                ),
            ),
            KeyPoints(
                listOf(
                    "Recursão à esquerda ou prefixo comum ⇒ não é LL(1).",
                    "Conflito some com FOLLOW ⇒ SLR.",
                    "Conflito some com lookahead por item ⇒ LALR ou LR(1).",
                    "Nada resolve ⇒ provavelmente ambígua.",
                ),
            ),
        ),
    )
}
