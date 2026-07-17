# Nightwire UI — Cyberpunk Design System

Sistema visual techno-noir do Server Dashboard. Amarelo-voltagem e ciano neon sobre superfícies
quase-pretas de CRT, formas angulares com `clip-path` (notch) no lugar de cantos arredondados, e uma
espinha tipográfica monospace/terminal. Estética "high tech, low life" — um centro de comando de
operador, não um app amigável.

Os tokens vivem em [`static/home/styles/style.css`](static/home/styles/style.css) (`:root`) e são
mapeados para as variáveis `--bs-*` do Bootstrap 5.3, que é re-tematizado (não substituído). O layout
usa uma **sidebar lateral** compartilhada via fragment Thymeleaf
([`templates/home/fragments/layout.html`](templates/home/fragments/layout.html)).

## 1. Cor

Fundo quase-preto carregando três acentos neon, usados com parcimônia (a maior parte de qualquer tela
é preto/cinza; o neon é reservado para acento, foco e status).

### Base
- **Voltage** `#FCEE0A` — primário / CTA (amarelo Night City). `--cp-voltage`
- **Jet** `#08080A` — fundo do app. `--cp-jet`
- **Panel** `#0D0D10` (→ `#0A0A0D`) — superfície de card/painel (gradiente sutil). `--cp-panel`
- **Cyan** `#00F0FF` — secundário / links / foco. `--cp-cyan`
- **Magenta** `#FF2E88` — terciário / raro. `--cp-magenta`

### Semânticas (com sabor de gênero)
- **Blood ICE** `#FF2A3C` — erro / destrutivo. `--cp-danger` (texto legível: `--cp-danger-fg #FF6B78`)
- **Amber CRT** `#FFB000` — aviso. `--cp-amber`
- **Terminal green** `#7CFF6B` — sucesso / online. `--cp-ok`

### Texto (bone → steel)
- `#F4F4EC` headings (`--text-heading`) · `#E9E9E1` primeiro plano · `#A9A9A0` corpo (`--text-body`)
- `#C9C9C0` corpo forte · `#8B8B80` labels (`--text-label`) · `#6f6f68` mais fraco (`--text-faint`)

### Bordas (hairline bone translúcido)
- `rgba(233,233,225,0.12)` divisórias · `0.16` inputs · `0.3` outline forte. Foco troca para acento
  sólido + `box-shadow` de 1px na mesma cor.

## 2. Tipografia

Três famílias, cada uma com um papel. Carregadas via Google Fonts no topo do `style.css`.

- **Share Tech Mono** (`--font-display`) — headings, botões, labels de tab. **SEMPRE CAIXA ALTA**,
  tracking apertado.
- **Archivo** (`--font-body`, 400–800) — parágrafos e texto corrido. Única face proporcional; único
  lugar onde múltiplos pesos aparecem. Caixa normal.
- **JetBrains Mono** (`--font-mono`) — labels, microcopy, dados, saída de terminal, valores de métrica.

Eyebrows/labels são uppercase, pequenas e com `letter-spacing`. Corpo (Archivo) é o único ponto onde o
sistema relaxa para caixa normal.

## 3. Forma — o traço-assinatura

`border-radius` é efetivamente **0** em todo lugar. Cards, painéis, sidebar, botões e badges são
recortados com polígonos `clip-path` (um notch cortado num canto). É o traço mais identificável do
sistema — prefira um notch a um raio. Variáveis: `--clip-notch-card`, `--clip-notch-button`,
`--clip-notch-badge`, `--clip-notch-hud`, `--clip-notch-mark`.

Única exceção de raio: `--radius-pill 999px` para o raro pill verdadeiro (trilho de switch).

## 4. Espaço, sombra e textura

- **Escala densa** de 4px (4/8/12/14/16/18/20/24/26/30/40/54) — HUD/terminal, controles próximos.
- **Sem sombras de elevação** (sistema flat/matte). A única "sombra" é **glow**: `box-shadow` colorido
  como emissão de luz (botões no hover, dots de status, inputs em foco), sempre na cor de acento do
  próprio elemento. `--shadow-glow-accent/-cyan/-danger`.
- **Sem backdrop-blur/glassmorphism.** Transparência é usada com precisão (fundos alpha tingidos
  `rgba(accent, 0.05–0.15)` para alertas, badges, tracks ativos).
- **Texturas CRT (sutis)** — camadas `.bg-fx` fixas atrás de tudo: `.bg-glow` (radial cyan/magenta
  ~6%), `.bg-scan` (scanlines leves ~0.15) e `.bg-grain` (granulado ~3.5%). **Sem** glitch/flicker
  animado no conteúdo — a prioridade é a legibilidade de um dashboard lido o dia todo.

## 5. Componentes

- **Sidebar** (260px, sticky no desktop; offcanvas `<lg` no mobile): marca em notch amarelo, links
  Share Tech Mono uppercase com atalho (F1–F9) alinhado à direita, linha ativa com **fill amarelo** +
  borda esquerda. Rodapé com dot verde (OPERADOR / UPLINK). Ver `.app-sidebar`, `.sidebar-link`.
- **Cards** (`.card-metric`): `--surface-card-gradient`, borda hairline, `--clip-notch-card`, sem
  sombra. Valores grandes em Share Tech Mono (`.metric-value`).
- **Botões**: primário = fundo voltage + texto jet + notch + hover `brightness(1.12)` e glow amarelo.
  Outline (secundário) = borda ciano, fill tênue no hover. Sufixo `▸` no texto marca "executar".
- **Badges/Tags** (`.badge-signal`, `.badge-service-*`): mono, notch, fill alpha do acento.
- **Formulários**: input/select fundo panel, borda hairline, foco ciano com ring 1px. Labels são
  eyebrows `// nome` (comment-style).
- **Tabs** (`.nav-tabs`): sublinhado amarelo no ativo, sem caixa.
- **Modais**: painel com notch, sem sombra difusa forte.

## 6. Voz e conteúdo

- **Idioma:** português para a chrome de UI, com termos de gênero em inglês quando cabe (terminal-flavor).
- **Voz:** terse, imperativa, sabor de linha de comando. Botões leem como comandos (`Entrar ▸`).
  Status lê como saída de sistema. Erro é direto e diegético ("chave inválida").
- **Labels** prefixadas com `//` (comment-style): `// usuário`, `// access_key`, `// visão_geral`.
- **Pontuação como UI:** `▸` executar/ir, `//` label/eyebrow, `▾/▴` download/upload. Sem emoji —
  dingbats unicode fazem o papel de ícones quando um glifo basta. Bootstrap Icons segue em uso para
  os ícones de UI existentes.

## 7. Do / Don't

**Do:** usar Jet como fundo; reservar neon para acento/foco/status; notch em vez de raio; uppercase +
Share Tech Mono em headings/botões; glow (não sombra cinza) para profundidade; manter o dashboard
legível (FX sutis).

**Don't:** superfícies claras; border-radius em cards; sombras difusas de elevação; glass/backdrop-blur;
neon em grandes superfícies; glitch/flicker animado sobre dados; fontes serifadas/decorativas.

---

*Adaptado do design system "Nightwire UI — Cyberpunk" do projeto Claude Design "Atualizar design para
cyberpunk". Os mockups de referência de cada tela (`Overview.dc.html`, `Login.dc.html`, etc.) vivem
nesse projeto de design.*
