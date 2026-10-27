# BadarasOS

O sistema da TV TCL 50P755 (Google TV, Android 12) da casa: uma tela inicial própria, Filmes e Séries com
player próprio, a Central de Jogos ligada ao PC e o PC como fonte de vídeos.

- Pasta: `D:\BadarasOS` (o caminho antigo `C:\tvh` é uma junção para cá; tudo que usa `C:\tvh` continua valendo)
- GitHub (privado): https://github.com/fvlazco/BadarasOS, ramo `main`
- Base: o projeto público de launcher `Gathin23/cleanyourtv` (ramo `upstream-main`, remoto `origin`)

## Endereços

| O quê | Valor |
|---|---|
| TV | `192.168.15.10`, ADB na porta 5555, chave em `tools\adbkey\adbkey` (fica fora do Git) |
| PC | `192.168.15.9` (cabo, rede "Rede 2"); se o IP mudar, a fonte Meu PC e a aba Jogos precisam ser atualizadas |
| PC na TV | entrada HDMI 1 (`com.tcl.tvinput/.TvPassThroughService/HW15`) |
| Monitor do PC | Odyssey G40B (`SAM7279`); TV no Windows: `XXX8000` |

## O que tem

### Tela inicial (app `com.gathin.tvhome`, pasta `launcher`)
- Página em HTML (`launcher/app/src/main/assets/home.html`), com o menu lateral: Início, TV, Filmes e Séries, Jogos, Apps, Fontes e Configurações.
- `WebHomeActivity.java` faz a ponte entre a página e o Android. Também libera memória com a página escondida, já que a TV tem 2 GB.

### Filmes e Séries (`assets/movies.js`)
- Catálogo do Cinemeta; fontes dos add-ons em `files/addons.json` e legendas em `files/subtitles.json` na TV. Cópias dos dois ficam em `addons.json` e `subtitles.json`, na raiz deste projeto.
- **Atualizar add-ons:** a lista não se atualiza sozinha. Para incluir ou tirar um add-on, edite o arquivo e envie para a TV.
- **Escolha a fonte:**
  - Recomendações no topo: No seu PC, Assistir agora, Melhor dublada e Legenda sincronizada.
  - Os links diretos são testados antes de recomendar.
  - Um add-on que falha 3 vezes seguidas fica de fora por 6 h.
- **Episódios:**
  - Miniaturas vêm do Cinemeta. Quando faltam, o app busca no TVmaze e no Kitsu.
  - Episódio assistido ganha marca de assistido e barra de progresso.
  - Segurar OK marca ou desmarca como assistido.
- **Continuar:** mostra um cartão por título; quando o episódio termina, oferece o "Próximo episódio".
- **Anime:**
  - Os tempos de abertura e créditos vêm do AniSkip.
  - A temporada certa no MyAnimeList é escolhida pelo Kitsu, porque o ARM mistura especiais com a série.
  - Sem dados no AniSkip, a abertura e os créditos são estimados pelas pausas da legenda.

### Player próprio (`PlayerActivity.java` + `assets/player.html`)
- Toca os links diretos. O torrent vai para o Stremio.
- **Legendas:** sem legenda em português, ele traduz a inglesa pelo Google. A tradução fica em cache de até 30 MB.
- **Botões:** pular abertura e próximo episódio, com contagem de 10 s a partir dos créditos.
- **Avançar:** cada toque avança 10 s e os toques seguidos somam.

### Central de Jogos (`assets/games.js` + `pcagent`)
- **Abrir um jogo:**
  - A aba Jogos lista Steam e Hydra, com o último jogo jogado em destaque.
  - "Jogar na TV" acende só a TV no PC em 1080p60, abre o jogo e mostra a tela de carregamento com a arte do jogo.
  - A TV só troca para o HDMI 1 quando a janela do jogo já está em tela cheia e em foco.
- **Trocas de tela escondidas:**
  - `GameCover.java` desenha a tela da Central por cima do HDMI. Ela cobre o "procurando sinal" e a área de trabalho do Windows.
  - Precisa da permissão `appops SYSTEM_ALERT_WINDOW`, que já vem na instalação.
- **Durante o jogo:**
  - O Cyberpunk abre direto, sem o launcher da CD Projekt (`launch_direct` no `config.json`).
  - Perguntas da Steam: o programa aperta Enter; se ela continuar parada, a TV mostra o PC.
- **Fechar e voltar:**
  - "Fechar jogo" e "Devolver tela ao PC" funcionam pelo controle remoto.
  - Se a TV desliga no meio do jogo, a imagem volta ao monitor.

### Programa do PC (`pcagent`)
- `agent.py` roda escondido (pythonw) e abre junto com o Windows. O atalho fica em Inicializar e se chama "Central de Jogos (TV)".
- **Como a TV conversa com o PC:**
  - Os pedidos da TV chegam pelo logcat (`Log.i("GameReq", ...)`).
  - O PC responde gravando arquivos em `files/` na TV, pelo ADB.
- **Configuração:** fica em `pcagent\config.json`; os valores padrão estão no começo de `agent.py`.
- **Registro:** `pcagent\agent.log`.
- **Meu PC (`mediaserver.py`):**
  - A pasta `D:\Series e Filmes` vira um add-on do Stremio na porta 8765, que só a TV acessa.
  - Séries ficam em `Series\<nome>\` e precisam de temporada e episódio no nome do arquivo. Filmes ficam em `Filmes\`.
  - Uma `.srt` ao lado do vídeo vira legenda em português.
  - Pasta nova é percebida sozinha.
- `otimizar_lego_batman.py`: perfil gráfico do LEGO Batman. Use `--desfazer` para voltar.

## Como atualizar

```powershell
# app da TV (compila e instala; o aviso "deprecated API" do Java é normal)
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
cd D:\BadarasOS\launcher; .\gradlew.bat assembleRelease --no-daemon -q 2>$null
& D:\BadarasOS\launcher\build-and-install.ps1 -TvHost 192.168.15.10 -InputKeyService

# programa do PC (depois de mexer em agent.py / mediaserver.py)
Get-Process pythonw | Stop-Process -Force
Start-Process C:\tvh\pcagent\venv\Scripts\pythonw.exe -ArgumentList '"C:\tvh\pcagent\agent.py"' -WorkingDirectory C:\tvh\pcagent

# salvar e mandar para o GitHub
cd D:\BadarasOS; git add -A; git commit -m "..."; git push
```

Atalho de teste na TV: `am start -n com.gathin.tvhome/.WebHomeActivity --es open "<cmd>"`. Exemplos de `<cmd>`: `games`, `tab:series`, `detail:series:tt123`, `ep:tt123:1:5:BestCine` e `sources:movie:tt123`.

### Minha lista
- **Marcar:** botão "Minha lista" nos detalhes de série ou filme; com o título já marcado, ele vira "Na minha lista". Aperte de novo para tirar.
- **Ver:** aba "Minha lista" em Filmes e Séries, com o último marcado primeiro. "Mostrar" filtra por Tudo, Filmes, Séries ou Animes.
- **Onde fica guardada:** nas preferências do app no Android (`mylist`), não na página, então limpar o cache da página não apaga a lista. Ela se perde só se o app for desinstalado.

## Ideias pendentes
- **Otimizar outros jogos:** sob pedido, um por vez.
