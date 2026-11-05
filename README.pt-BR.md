<p align="center">
  <a href="https://cleanyour.tv"><img src="https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/banner.png" alt="CleanYourTV. Sua TV, sem anúncios. Remova os aplicativos desnecessários de qualquer Android TV ou Google TV pelo Wi-Fi e substitua a tela inicial cheia de anúncios por um launcher limpo, com visual semelhante ao da Apple TV. Sem root, sem cabo, sem desinstalar nada e com restauração em uma única linha." width="800"></a>
</p>

<p align="center">
  Leia em: <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · <a href="README.it.md">Italiano</a> · Português · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a> · <a href="README.uk.md">Українська</a> · <a href="README.tr.md">Türkçe</a>
</p>

<p align="center">
  <a href="https://github.com/Gathin23/cleanyourtv/stargazers"><img src="https://img.shields.io/github/stars/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Estrelas"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/pulls?q=is%3Amerged"><img src="https://img.shields.io/github/issues-search?query=repo%3AGathin23%2Fcleanyourtv%20is%3Apr%20is%3Amerged&style=flat&cacheSeconds=1800&label=merged%20PRs&color=cfffcf&labelColor=15181e" alt="PRs mesclados"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/network/members"><img src="https://img.shields.io/github/forks/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Forks"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://img.shields.io/github/contributors/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Contribuidores"></a>
  <a href="https://buymeacoffee.com/gathint"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="Apoie o projeto"></a>
  <a href="https://www.producthunt.com/products/cleanyourtv"><img src="https://img.shields.io/badge/Product%20Hunt-upvote%20%E2%96%B2-da552f?style=flat&logo=producthunt&logoColor=white&labelColor=15181e" alt="CleanYourTV no Product Hunt"></a>
</p>

<p align="center">
  <a href="https://trendshift.io/repositories/310225"><img src="docs/trending-badge.png" alt="Repositório em alta nº 1 da semana, de 19 a 25 de outubro de 2026, via trendshift.io" width="360"></a>
</p>

# CleanYourTV: Guia em Português Brasileiro

Conecte-se a uma Android TV ou Google TV pela rede Wi-Fi da sua casa, desative aplicativos pré-instalados, anúncios e serviços que coletam dados e que você não utiliza. Depois, instale uma tela inicial (launcher) mais limpa nas TVs compatíveis.

Não é necessário root, reinstalar o firmware ou utilizar um cabo USB. Os aplicativos do sistema são apenas **desativados**, nunca removidos, e sempre há um script disponível para restaurá-los.

Esta página apresenta os principais recursos e como utilizá-los. Cada modelo de TV, região e versão de firmware possui sua própria lista de aplicativos e limitações. Por isso, verifique primeiro as informações específicas da sua TV.

Em caso de divergência, prevalece o [README em inglês](README.md). As observações específicas de cada modelo estão disponíveis na [lista de TVs compatíveis](examples/README.md).

> [!WARNING]
> **Sua TV é Samsung, LG, Roku ou de outra marca que não utiliza Android?**
> <br>O CleanYourTV não funciona nesses dispositivos, mas você ainda pode desativar o rastreamento passo a passo, utilizando apenas o controle remoto: [cleanyour.tv/not-android](https://cleanyour.tv/not-android).

![Comparação entre a tela inicial original do Google TV e o launcher do CleanYourTV](docs/stock-vs-new-side.jpg)

## Comece por aqui

| Você tem | Acesse |
|---|---|
| **Uma Android TV ou Google TV** | [Início rápido](#início-rápido): um assistente de programação faz o trabalho em cerca de quinze minutos. |
| **Uma TV Samsung, LG, Roku, Vizio ou Hisense VIDAA** | [cleanyour.tv/not-android](https://cleanyour.tv/not-android): o CleanYourTV não funciona nessas TVs, mas você pode desativar o rastreamento usando o controle remoto. |
| **Um celular ou notebook, sem agente de programação** | Um dos [aplicativos da comunidade](https://cleanyour.tv/app) para celulares Android, Mac e Windows, desenvolvidos por pessoas independentes. |
| **Quer ver como fica antes** | [O que você recebe](#o-que-você-recebe): capturas de tela da nova tela inicial. |

**Links oficiais:** [cleanyour.tv](https://cleanyour.tv) e este repositório. Outros domínios que usam esse nome, como
cleanyourtv.com, não são mantidos por este projeto ([#1004](https://github.com/Gathin23/cleanyourtv/issues/1004)).
O CleanYourTV não publica aplicativos em nenhuma loja de aplicativos, e clean-your-tv.net também não é nosso.

[Como funciona](#como-funciona) · [Início rápido](#início-rápido) · [O que você recebe](#o-que-você-recebe) ·
[TVs compatíveis](#tvs-compatíveis) · [Fazendo manualmente](#fazendo-manualmente) · [O launcher](#o-launcher) ·
[Restauração](#restaurando-sua-tv) · [Atualizações de firmware](#depois-de-uma-atualização-de-firmware) ·
[Dúvidas frequentes](#perguntas-frequentes) · [Como contribuir](#como-contribuir)

## Como funciona

As As Android TVs têm a mesma ponte de depuração (ADB) dos celulares Android. Ative a depuração USB nas
opções do desenvolvedor da TV, autorize seu computador uma única vez e o restante é feito com ferramentas
padrão do Android:

| Etapa | O que acontece |
|---|---|
| **Fazer backup** | Salva a lista de pacotes instalados e as configurações. |
| **Desativar** | Desativa a telemetria do fabricante, os serviços de anúncios, as fileiras de conteúdo e os aplicativos pré-instalados desnecessários, usando `pm disable-user`. Nada é desinstalado, então um script de restauração pode desfazer cada etapa. |
| **Instalar** | Instala um launcher nativo e o define como tela inicial padrão. |

**Sem root, sem cabo, sem desinstalar nada e com restauração em uma única linha caso você mude de ideia.**

O excesso de aplicativos varia de acordo com a marca. Por isso, o prompt faz com que seu assistente de programação
identifique o que existe na sua TV e pergunte antes de alterar qualquer coisa que você possa usar.

## Início rápido

| Você tem | O que fazer |
|---|---|
| **Um computador** | Use o prompt com um assistente de programação: siga as três etapas abaixo. |
| **Um computador, mas sem assistente de programação** | Execute as três etapas por conta própria: os comandos estão em [Fazendo manualmente](#fazendo-manualmente). |
| **Um celular ou notebook, sem assistente de programação** | Escolha um dos [aplicativos da comunidade](https://cleanyour.tv/app): aplicativos gratuitos e de código aberto, desenvolvidos por pessoas independentes com base nas listas de TVs deste projeto. |

As três etapas abaixo foram escritas para serem executadas com um assistente de programação: o computador
precisa estar na mesma rede Wi-Fi da TV e ter um terminal disponível, além de Python 3, um JDK entre as versões
17 e 22 e Android SDK platform 34 com build-tools 34 (o assistente compila o launcher nesse ambiente).
Sem um assistente, [Fazendo manualmente](#fazendo-manualmente) é o mesmo processo, executado por você,
com os comandos de instalação incluídos.

Você pode usar qualquer um destes: Claude Code (no [0G Private Computer](https://pc.0g.ai/?utm_source=cleanyourtv&utm_medium=github&utm_campaign=readme)
ou em um [plano da Anthropic](https://claude.com/pricing)), [Codex](https://github.com/openai/codex),
[Gemini CLI](https://github.com/google-gemini/gemini-cli) ou [Antigravity CLI](https://antigravity.google/product/antigravity-cli).

| Onde | O que fazer |
|---|---|
| **1. Na TV** | Configurações → Preferências do dispositivo → Sobre → pressione OK sete vezes em **Build** → volte → Opções do desenvolvedor → ative **Depuração USB**. Se houver uma opção de Depuração de rede ou ADB, ative-a também. Mantenha a TV ligada. |
| **2. No computador** | Clone o repositório e abra essa pasta no seu assistente (`git clone https://github.com/Gathin23/cleanyourtv`). Em seguida, cole o [`PROMPT.md`](PROMPT.md) e informe o modelo da sua TV. |
| **3. Acompanhe o processo** | Aceite a solicitação "Permitir depuração USB?" na TV e responda ao checklist: quais aplicativos você usa, o tamanho do dock e a ordem dos aplicativos, se deseja manter o assistente de voz, o Chromecast e as atualizações. |

Clonar o repositório é importante: assim, o assistente reutiliza os scripts `tools/disable.sh` e
`launcher/build-and-install.sh` deste projeto, em vez de criar os próprios. É nesses scripts que ficam o backup,
a proteção da tela inicial e o registro necessário para restaurar tudo.

O assistente faz o backup, remove os aplicativos desnecessários, compila e instala o launcher e verifica cada
etapa com capturas de tela obtidas diretamente da TV. Com este repositório como contexto, o processo leva
cerca de quinze minutos.

## O que você recebe

Uma tela inicial com visual semelhante ao da Apple TV, que não fica no caminho:

| Parte | Como é |
|---|---|
| **Uma tela inicial totalmente preta** | Um dock com seus quatro aplicativos favoritos em uma faixa arredondada, como na Apple TV. |
| **Uma área de destaque no topo** | Mostra o nome do aplicativo em foco em letras grandes, com um brilho ambiente em uma cor extraída da imagem do aplicativo. |
| **Os demais aplicativos em uma grade** | Deslizam para cima quando você pressiona a seta para baixo. |
| **Um único destaque flutuante com efeito de vidro** | Se move suavemente de um bloco para outro. O bloco em foco se eleva e exibe seu nome. |
| **Três botões com efeito de vidro fosco** | Entradas (portas HDMI com os nomes que você definiu nas configurações da TV, AV e TV ao vivo), Wi-Fi ou Ethernet com o nome da rede e Configurações. |
| **Uma saudação, um relógio discreto e a data** | Exibidos na fonte [Outfit](https://fonts.google.com/specimen/Outfit), com uma proteção de tela de relógio no mesmo estilo. Defina `show_stats` na configuração ou em um tema para exibir uma linha abaixo do relógio com CPU, memória livre e armazenamento disponível. Ela fica desativada até você habilitá-la e só é atualizada enquanto a tela inicial está aberta. |
| **Temas e cartões de módulos** | Os temas alteram as cores, o papel de parede, o dock e as colunas sem precisar recompilar. Os cartões de módulos permitem que outros aplicativos adicionem um bloco, somente com sua autorização. |

E uma TV que deixa de atrapalhar: sem fileiras patrocinadas, sem avisos insistentes de "Descobrir",
sem análises enviadas para servidores externos e sem aplicativos que você nunca pediu.

| | |
|---|---|
| ![A área de destaque mostra o aplicativo em foco sobre um brilho ambiente na cor correspondente](docs/focus-2-up-netflix.png) | ![O menu Entradas: portas HDMI com o que está conectado, AV e TV ao vivo](docs/inputs-menu.jpg) |
| *Área de destaque e brilho ambiente* | *Entradas: HDMI com status da conexão, AV e TV ao vivo* |
| ![A grade com o destaque de vidro sobre um bloco e o nome logo abaixo](docs/focus-1-fancode.png) | ![A proteção de tela Relógio: um relógio discreto flutuando sobre o brilho](docs/clock-screensaver.png) |
| *A grade, um destaque de vidro e o nome do bloco* | *Proteção de tela Relógio, com movimento para evitar marcas permanentes na tela* |
| ![O tema aurora: papel de parede e botões coloridos, aplicados sem recompilar](themes/aurora/screenshot.jpg) | ![Seis colunas e um dock com seis aplicativos definidos em config.json do tema](themes/six-columns/screenshot.jpg) |
| *Tema Aurora, aplicado com `tools/theme.sh`* | *Seis colunas e um dock com seis aplicativos, no mesmo launcher* |
| ![Movendo um bloco: mantenha OK pressionado, escolha Mover, o bloco é selecionado e os outros ficam esmaecidos](docs/tile-move.jpg) | ![A biblioteca de aplicativos: aplicativos instalados, um toque adiciona um aplicativo de volta à tela inicial](docs/app-library.jpg) |
| *Mantenha OK pressionado em um bloco e escolha Mover: use as setas para posicioná-lo e Voltar para soltá-lo* | *Biblioteca de aplicativos: um toque coloca de volta na tela inicial um aplicativo removido* |

## TVs compatíveis

Qualquer TV que execute **Android TV** ou **Google TV**.

| Plataforma | O que funciona |
|---|---|
| **Android TV, Google TV** | A remoção dos aplicativos desnecessários e o launcher. |
| **Fire TV** | Somente a parte de remoção dos aplicativos, pois o Fire OS não permite definir um launcher de terceiros como tela inicial. E, na Fire TV Box de 2015 (Fire OS 5), nem isso funciona: todas as operações `pm` são recusadas. |
| **Alguns modelos TCL vendidos na China** | Não permitem instalar nenhum launcher; as listas desses modelos informam isso no cabeçalho. |
| **Samsung (Tizen), LG (webOS), Roku, Vizio, Hisense VIDAA** | Não são compatíveis (não oferecem ADB). Em vez disso, desative o rastreamento: [cleanyour.tv/not-android](https://cleanyour.tv/not-android). Consulte [SHOWCASE.md](SHOWCASE.md#other-platforms-fire-tv-and-lg-webos) para ver quatro ports para webOS e [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) para saber o que uma TV Samsung de 2021 responde pelo Wi-Fi (sem ADB e, portanto, sem `pm disable-user`) caso esteja considerando um port para Tizen. |

Listas testadas e enviadas por colaboradores, com uma pasta por modelo em [`examples/`](examples/). As marcas
com mais listas, cada uma com um link para seus modelos no site:

**Encontre sua TV:** pesquise em [cleanyour.tv/tvs](https://cleanyour.tv/tvs) ou consulte a tabela completa em
[examples/README.md](examples/README.md).

Sua TV não está na lista? O prompt identifica os aplicativos desnecessários por conta própria. Depois, adicione
sua lista para que a próxima pessoa com o mesmo modelo não precise repetir esse trabalho:
[CONTRIBUTING.md](CONTRIBUTING.md).

## Fazendo manualmente

Tudo o que o assistente faz, você também pode executar por conta própria. Antes, é preciso ter duas coisas
instaladas no computador:

| Você precisa de | Para quê |
|---|---|
| **Python 3** (qualquer versão 3.x) | Para as ferramentas. O comando de criação do ambiente virtual abaixo instala as dependências utilizadas por elas. |
| **JDK 17 a 22** (um JDK completo, incluindo `jlink`) e **Android SDK platform 34** com **build-tools 34.0.0 ou qualquer versão 34.x mais recente** | Para compilar o launcher. O JDK 23 e versões mais recentes impedem o funcionamento do Gradle 8.9. A plataforma 34 é a utilizada pelo processo de build; uma plataforma mais nova não a substitui. No macOS, se nenhum desses componentes estiver instalado: `brew install openjdk@17 android-commandlinetools` e, em seguida, `sdkmanager "platforms;android-34" "build-tools;34.0.0"`. No Linux, o script de instalação procura o SDK em `~/Android/Sdk` e o executável `java` no seu PATH. |

`tools/tv.py` se comunica com a TV por meio do ADB de rede na porta 5555. Se essa porta continuar fechada
e a TV oferecer Depuração sem fio com código de pareamento (como acontece com o Chromecast com Google TV e
o Google TV Streamer), faça o pareamento uma vez usando o `adb` padrão. Depois, você pode executar
`adb tcpip 5555` para abrir a porta — o que normalmente dura apenas até a TV reiniciar — ou mantê-la fechada:
configure `TV_ADB_BINARY` para apontar para esse mesmo `adb` e `TV_HOST` para a porta exibida na tela de
Depuração sem fio. Assim, as ferramentas acessam a TV pela conexão pareada, sem que nada fique escutando
na porta 5555 ([tools/native-adb.md](tools/native-adb.md)). O guia de [solução de problemas](docs/TROUBLESHOOTING.md#connecting)
explica as duas opções e as etapas de pareamento.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # endereço IP da sua TV

tools/venv/bin/python -I tools/tv.py --probe                 # modelo, versão do Android e firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # executa um comando de shell na TV
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # aplica uma lista de remoção de aplicativos
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # desfaz as alterações e restaura o launcher original
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # verifica o que uma atualização de firmware desfez
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # compila o launcher e o define como tela inicial
tools/theme.sh themes/aurora                                 # aplica um tema; --reset o remove
```

| Ferramenta | O que faz |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Cliente ADB implementado inteiramente em Python: shell, execução em lote, envio e recebimento de arquivos, reinicialização e capturas de tela, com a opção de pressionar teclas do controle remoto antes. Funciona mesmo quando o binário `adb` padrão está bloqueado. Oferece também um [backend Google ADB com pareamento](tools/native-adb.md), opcional, para Depuração sem fio criptografada. |
| [`tools/docker-build.sh`](tools/docker-build.sh) | Compila o launcher ou um complemento dentro do Docker (JDK 17, Android SDK 34), sem exigir outras ferramentas neste computador além do Docker. Instale o resultado usando `SKIP_BUILD=1`. [Guia](tools/docker-build.md). |
| [`tools/b64push.py`](tools/b64push.py) | Envia arquivos grandes, como APKs, divididos em blocos codificados em base64 e verifica o checksum. |
| [`tools/relay.py`](tools/relay.py) | Encaminha uma porta local para a porta ADB da TV, para assistentes cujo ambiente isolado não consegue acessar sua rede. Fica escutando somente neste computador. [Guia](tools/relay.md). |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Aplicam ou desfazem uma lista de remoção de aplicativos. A restauração usa o estado registrado antes das alterações. |
| [`tools/recovery.py`](tools/recovery.py) | Gera o registro necessário para que `disable.sh` consiga desfazer as próprias alterações e exibe os comandos de restauração em lote. |
| [`tools/check.py`](tools/check.py) | Compara a TV com a lista aplicada para identificar o que uma atualização de firmware desfez; `--fix` restaura as alterações. |
| [`tools/adb-closed.py`](tools/adb-closed.py) | Verifica se a porta ADB de rede da TV está fechada depois que a depuração é desativada: retorna 0 quando a TV recusa a conexão, 1 enquanto a porta continua aberta e 2 quando não há resposta ou a própria verificação não pode ser executada. Não precisa do `nc`. |
| [`tools/philips-api.py`](tools/philips-api.py) | Somente para Philips: verifica se a API de controle JointSpace, utilizada pelo Home Assistant, continua respondendo antes e depois da remoção de aplicativos (`tools/venv/bin/python -I tools/philips-api.py <tv ip>`). Tenta primeiro a porta 1926 por HTTPS e depois a 1925; lê somente o que responder, sem pareamento. |
| [`tools/privacy.py`](tools/privacy.py) | Faz uma auditoria na TV em busca de possíveis aplicativos e pacotes relacionados a voz, ACR e telemetria e apresenta as evidências de cada um. Com `--write`, transforma os resultados em uma lista que `disable.sh` e `restore.sh` já entendem. |
| [`tools/theme.sh`](tools/theme.sh), [`theme_restart.py`](tools/theme_restart.py) | Enviam uma pasta de [`themes/`](themes/) para o launcher e reiniciam a tela inicial. |
| [`tools/manage.py`](tools/manage.py) | Lista todos os complementos, temas e opções, com suas capturas de tela; mostra o que está na TV e permite instalar, atualizar ou remover cada item executando o próprio script de quem o criou. |
| [`tools/bench.py`](tools/bench.py) | Mede o desempenho do launcher que responde ao botão HOME: latência das teclas, distância percorrida pelo foco, quadros com travamentos, inicialização a frio, retorno à tela inicial e consumo de CPU e PSS em repouso. Não altera nada na TV e grava os números brutos de cada tecla em JSON; não exige instalação adicional. |
| [`tools/trace.py`](tools/trace.py), [`trace_report.py`](tools/trace_report.py) | Registram traces do Perfetto do launcher (retorno à tela inicial, inicialização a frio e navegação pelo D-pad) e mostram onde o tempo é gasto, por execução e comparando antes e depois. Opcional: requer [`requirements-perf.txt`](tools/requirements-perf.txt). [Guia](tools/trace.md). |
| [`tools/flamechart.py`](tools/flamechart.py) | Gera um flame chart comparando os traces de antes e depois, útil em um pull request: uma faixa por thread em uma escala de tempo compartilhada, com o trabalho que a alteração busca modificar destacado em magenta e seu tempo total. Opcional, assim como as ferramentas de trace. [Guia](tools/flamechart.md). |
| [`tools/screendiff.py`](tools/screendiff.py) | Compara uma captura de tela anterior e outra posterior, pixel por pixel, ignorando o relógio ou qualquer área que você queira mascarar. Também salva o par de imagens com o nome da sua rede Wi-Fi desfocado. Opcional, assim como as ferramentas de trace. [Guia](tools/screendiff.md). |
| [`tools/race_calendar.py`](tools/race_calendar.py) | Adiciona o calendário da temporada atual de F1 a um tema para o modo de fim de semana de corrida, usando a API gratuita Jolpica. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Compila uma versão otimizada para distribuição (cerca de 572 KB), instala, define como tela inicial e reverte a alteração se o launcher não assumir essa função. |

Os scripts nunca deixam a TV sem um aplicativo de tela inicial: eles só desativam um launcher original que
reconhecem e ignoram esse aplicativo na lista até que outro launcher responda ao botão HOME. Suas próprias
listas podem ficar fora do repositório: os caminhos são relativos ao diretório de onde você executa o comando,
e caminhos absolutos também funcionam.

<details>
<summary><b>Windows (PowerShell)</b></summary>

Cada script tem uma versão equivalente em PowerShell ao lado do original. Instale Python 3 e um JDK entre
as versões 17 e 22 e, no SDK Manager, adicione a plataforma 34 e o build-tools 34 (34.0.0 ou qualquer versão
34.x mais recente). O script usa `JAVA_HOME` quando ele aponta para um JDK dentro desse intervalo; caso
contrário, usa o JDK incluído no Android Studio, desde que também esteja dentro desse intervalo:

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

As duas últimas linhas fazem a restauração e aplicam um tema, equivalentes aos comandos `tools/restore.sh`
e `tools/theme.sh` apresentados acima. No Google TV, a restauração também recupera o launcher original:
`-Stock com.google.android.apps.tv.launcherx`.

</details>

<details>
<summary><b>Como os scripts protegem a tela inicial</b></summary>

Os scripts de instalação perguntam à TV qual launcher controla a tela inicial e só desativam um que reconheçam.
Os quatro launchers originais definidos em `KNOWN_STOCK_LAUNCHERS` são:
`com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx`,
`com.google.android.leanbacklauncher` e o `com.mitv.tvhome` da Xiaomi (PatchWall). Use
`STOCK_LAUNCHER=<package>` (`-StockLauncher`) para substituir essa definição ou `none` para não desativar
nenhum launcher.

`tools/disable.sh` (`disable.ps1`) aplica a mesma regra antes de desativar qualquer aplicativo:

- Se a lista contiver o aplicativo que controla a tela inicial e nenhum outro aplicativo da TV responder ao
  botão HOME, ele ignora essa entrada, informa o motivo e continua com o restante da lista. Assim, uma lista
  aplicada antes da troca de launcher não deixa a TV sem nada para iniciar.
- O assistente de configuração, os aplicativos de configurações, o `FallbackHome` do fabricante e os
  aplicativos de teste de fábrica (como `com.duokan.factorytest`, da Xiaomi) também respondem ao botão HOME
  e não são considerados.
- No Google TV, o script também mantém o launcher original ativo enquanto `setupwraith` responder ao botão
  HOME e continuar habilitado (por não estar na lista ou por ter sido mantido para preservar a rede Wi-Fi).
  O `setupwraith` responde antes de um launcher instalado que ainda não seja a tela inicial e, por isso,
  assumiria essa função.

Execute a mesma lista novamente depois que `launcher/build-and-install.sh` definir o novo launcher como tela
inicial e a entrada correspondente for aplicada. `FORCE_HOME=1` (`$env:FORCE_HOME`) força a desativação
mesmo assim.

</details>

## O microfone e o que é possível afirmar sobre ele

A pergunta "Quais desses pacotes estão escutando?" não tem uma resposta honesta em uma única linha:
uma verificação de permissões mostra cinquenta pacotes do sistema com `RECORD_AUDIO` que não gravam nada,
enquanto clientes de ACR conseguem analisar a tela sem sequer ter permissão de áudio. O script
[`tools/privacy.py`](tools/privacy.py) mostra o que a TV realmente informa, com as evidências para cada
possível recurso de voz, ACR e telemetria:
`TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py`. A opção `--write` transforma os resultados
em uma lista normal de remoção de aplicativos. O que essa auditoria não consegue fazer é provar a intenção
por trás de cada recurso: [docs/privacy.md](docs/privacy.md) explica o motivo.

## O launcher

Uma tela inicial nativa e leve para Android TV: **cerca de 572 KB**, feita em Java puro e projetada para TVs
com 1 GB de RAM. Sem blur, sem vídeo, sem permissão de rede e sem serviços em segundo plano. A **TV** precisa
ter Android 8.0 (API 26) ou superior, conforme o `minSdk` em
[`launcher/app/build.gradle`](launcher/app/build.gradle). O que ainda funciona em modelos mais antigos está
explicado em [Solução de problemas](docs/TROUBLESHOOTING.md).

| Recurso | Como funciona |
|---|---|
| **Configuração** | Ordem do dock, aplicativos ocultos, aplicativo de TV ao vivo, de 4 a 7 blocos por linha e animações reduzidas. Tudo em [`config.xml`](launcher/app/src/main/res/values/config.xml). |
| **Configurações na TV** | Mantenha OK pressionado em Configurações para abrir as configurações do launcher. Opcionalmente, `launcher_settings_button` faz com que um clique normal as abra diretamente; as configurações da TV continuam disponíveis na página de configurações e no bloco do aplicativo correspondente. |
| **Temas** | Plano de fundo, papel de parede e layout definidos em um `config.json` enviado pelo Wi-Fi, sem recompilação. 21 temas estão incluídos em [`themes/`](themes/), incluindo o `default/` integrado. |
| **31 idiomas** | Segue o idioma da TV, com layout totalmente espelhado da direita para a esquerda em hebraico. |
| **Biblioteca de aplicativos** | Opcional: permite navegar por todos os aplicativos instalados e adicionar ou remover atalhos da tela inicial. Consulte [o guia da biblioteca de aplicativos](docs/app-library.md). |
| **Menu dos blocos** | Ativado por padrão (`tile_menu`): mantenha OK pressionado em um bloco para acessar Mover, Remover da tela inicial e Informações do app. Se estiver desativado, manter OK pressionado abre Informações do app. |
| **Cartões de módulos** | Qualquer aplicativo de TV pode oferecer seu próprio bloco; você aprova cada um apenas uma vez. Consulte [`docs/modules.md`](docs/modules.md). |
| **Complementos** | Aplicativos opcionais que se integram como cartões de módulos, armazenados em [`addons/`](addons/) e nunca incorporados ao launcher. |
| **Proteção de tela com relógio** | Um relógio em movimento sobre o brilho ambiente, para evitar marcas permanentes na tela. |
| **Teclas do controle remoto** | Mantém funcionando os botões de Entradas, YouTube e engrenagem mesmo depois que o launcher original é removido. |

Temas, opções e complementos, todos com capturas de tela: [cleanyour.tv/addons](https://cleanyour.tv/addons).

**Saiba mais:** [docs/launcher.md](docs/launcher.md) explica cada linha da página de configurações do launcher,
como alterar a tela inicial padrão, os idiomas, as teclas do controle remoto, a proteção de tela com relógio,
a navegação e como compilar o projeto por conta própria.

## Restaurando sua TV

Guarde a lista que você aplicou. Uma única linha devolve a TV ao estado anterior:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

| O que | Como funciona |
|---|---|
| **O registro vem primeiro** | Antes de `tools/disable.sh` alterar qualquer coisa, ele registra como a TV estava em `~/.cleanyourtv/`. |
| **A restauração recupera esse estado** | Um pacote que você já havia desativado por conta própria continua desativado, e a tela inicial volta ao componente que a controlava antes. |
| **O launcher continua instalado** | Se quiser removê-lo, faça isso em Configurações → Apps na própria TV. |
| **Está sem computador?** | Se não conseguir acessar a TV, a própria tela Configurações → Apps permite reativar os pacotes. |

**Saiba mais:** [docs/restore.md](docs/restore.md) explica o que o registro armazena, quando o argumento
do launcher original é necessário e o que acontece quando não existe um registro.

## Depois de uma atualização de firmware

Uma atualização é instalada sobre o sistema já ajustado: os pacotes listados voltam, a tela inicial passa
a ser controlada pelo launcher original e novos aplicativos pré-instalados podem aparecer sem constar em
nenhuma lista. Ative o ADB novamente, como na etapa 1 do [Início rápido](#início-rápido), e compare a TV
com a lista aplicada. A opção `--fix` restaura as alterações:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

**Saiba mais:** [docs/firmware-updates.md](docs/firmware-updates.md) explica os códigos de saída, uma
verificação semanal, o que `--fix` altera, quais TVs mantêm o launcher original como tela inicial e quais
listas desativam o atualizador.

## Quando algo dá errado

A lista completa de problemas que encontramos está em [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).
Estes são os três pontos mais importantes:

- 🍎 **O macOS bloqueia `adb connect`** ("No route to host") por causa das permissões de privacidade da
  rede local. `tools/tv.py` contorna o problema sem usar o binário.
- ⚠️ **Desative o launcher original somente depois que o novo estiver instalado e funcionando**, caso
  contrário a tela inicial pode abrir um aplicativo de teste de fábrica. O script de instalação já organiza
  as etapas nessa ordem.
- 🔁 **No Google TV, o assistente de configuração** (`com.google.android.tungsten.setupwraith`) pode assumir
  a tela inicial e fazer o sistema entrar em um ciclo de falhas depois que o launcher original é desativado.
  Desative-o também e inclua-o no seu `debloat.txt` para que a restauração o reative.
  - Mas primeiro verifique `dumpsys wifi | grep -o 'cname=[^ ]*'`: se a rede Wi-Fi foi salva durante a
    configuração, ela pertence ao `setupwraith`, e desativá-lo apagará essa rede. Conecte-se novamente em
    Configurações antes; `tools/disable.sh` ignora o `setupwraith` até que você faça isso.
  - Nos Chromecasts do Google e no Google TV Streamer, isso também impede a transmissão de conteúdo pelo
    celular. Consulte [Solução de problemas](docs/TROUBLESHOOTING.md).
  - Já ficou preso nessa situação? Execute
    `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.

## Perguntas frequentes

<details>
<summary><b>Não sei programar e não tenho notebook. Ainda posso fazer isso?</b></summary>

Sim. Desenvolvedores independentes criaram aplicativos gratuitos e de código aberto com base nas listas
de TVs do CleanYourTV. Eles fazem a limpeza pelo Wi-Fi, sem precisar de um agente de programação. Cada
aplicativo mostra primeiro um plano, apenas desativa aplicativos e permite restaurar tudo:

| Aplicativo | Para | Desenvolvido por |
|---|---|---|
| [Clean Your TV app](https://github.com/udhaykumarbala/cleanyourtv-app) | Celular Android | [@udhaykumarbala](https://github.com/udhaykumarbala) |
| [Tellyscope](https://github.com/man15h/tellyscope) | Celular Android | [@man15h](https://github.com/man15h) |
| [TidyTV](https://github.com/jolan94/tidytv) | Celular Android, Google Play | [@jolan94](https://github.com/jolan94) |
| [CleanMyTV](https://github.com/wingedpanther/cleanmytv) | Mac e Windows | [@wingedpanther](https://github.com/wingedpanther) |

Esses aplicativos são projetos independentes, sem vínculo ou endosso do CleanYourTV ou de seu mantenedor.
Cada um é fornecido no estado em que se encontra, sob sua própria licença. Relate problemas nos respectivos
repositórios. Saiba mais em [cleanyour.tv/app](https://cleanyour.tv/app).

</details>

<details>
<summary><b>É seguro? Posso inutilizar minha TV?</b></summary>

Nada é gravado no firmware, não há acesso root nem nada é excluído; os pacotes são desativados
no perfil do usuário. O risco está em desativar algo de que a TV depende. Por isso, as listas são aplicadas
em pequenos grupos e o script de restauração deve estar pronto. A própria tela Configurações → Apps da TV
permite reativar pacotes, mesmo sem as ferramentas. Se a TV não iniciar o suficiente para permitir isso,
normalmente é possível recuperá-la com uma restauração de fábrica pelo menu de recuperação.

Mais informações: [docs/faq.md](docs/faq.md#is-this-safe-can-it-brick-the-tv)

</details>

<details>
<summary><b>E quanto às atualizações de firmware?</b></summary>

O atualizador permanece ativado na maioria das listas, então as atualizações de firmware continuam chegando
normalmente. Algumas listas desativam o próprio atualizador da TV; nesses casos, uma atualização só será
instalada depois que você o reativar. Quando uma atualização é instalada, ela substitui partes do sistema
já ajustado, fazendo com que alguns pacotes voltem. Nesse caso, basta executar o script novamente: consulte
[Depois de uma atualização de firmware](#depois-de-uma-atualização-de-firmware).

</details>

<details>
<summary><b>Os aplicativos vão parar de funcionar?</b></summary>

Os aplicativos de streaming continuam recebendo atualizações pela Play Store, que não é alterada.
O launcher apenas lista o que está instalado e abre os aplicativos usando intents que permanecem estáveis
há uma década.

</details>

<details>
<summary><b>Como reorganizo meus aplicativos?</b></summary>

Mantenha OK pressionado em **Configurações** (no canto superior direito) e escolha **Reordenar aplicativos**.
Pressione OK para selecionar um aplicativo, use as setas para movê-lo, pressione Voltar para posicioná-lo
e Voltar novamente para finalizar. Manter OK pressionado em um bloco também oferece a opção **Mover**
(`tile_menu`, ativada por padrão), que seleciona o aplicativo imediatamente. Mais informações:
[docs/launcher.md](docs/launcher.md#launcher-settings-and-the-default-home)

</details>

<details>
<summary><b>Posso fazer isso na casa de outra pessoa?</b></summary>

Sim, usando uma conexão privada pelo [Tailscale](https://tailscale.com), em vez de estar na mesma rede Wi-Fi.
Você pode instalar o Tailscale na TV da outra pessoa e compartilhar o acesso com ela, ou usar um computador
ou Apple TV na casa dela para encaminhar a conexão. Nunca faça isso abrindo a porta ADB no roteador dela.
Veja o passo a passo em [docs/REMOTE.md](docs/REMOTE.md).

</details>

<details>
<summary><b>O acesso ADB não é uma brecha de segurança?</b></summary>

É necessário tocar uma vez em "Permitir que este computador acesse", na TV, assim como acontece ao
desenvolver um aplicativo Android. O ADB de rede transmite dados sem criptografia pela porta 5555 e,
em alguns firmwares, qualquer dispositivo na rede local consegue abrir um shell. Por isso, feche essa
porta quando terminar:

1. Na TV: Opções do desenvolvedor → desative **Depuração USB**, **Depuração de rede/ADB** e
   **Depuração sem fio**, conforme as opções disponíveis no seu modelo, e revogue as autorizações de
   depuração USB.
2. Reinicie a TV e, no computador, confirme que a porta foi fechada:
   `python3 tools/adb-closed.py <tv ip>` deve informar que a porta está fechada.

O launcher continua funcionando sem a depuração ativada. Mais informações, incluindo como verificar a
própria porta da Depuração sem fio: [docs/faq.md](docs/faq.md#isnt-adb-access-a-security-hole)

</details>

<details>
<summary><b>Ele também bloqueia anúncios dentro dos aplicativos?</b></summary>

Parcialmente, usando o DNS privado do Android (Android 9 ou superior). Configure um resolvedor que bloqueie
anúncios, como o [AdGuard DNS](https://adguard-dns.io), e os domínios de anúncios e rastreadores dos
aplicativos deixarão de ser resolvidos em todo o sistema, inclusive após reinicializações. A desvantagem:
esse resolvedor passa a receber todas as consultas DNS feitas pela TV, e isso não remove os anúncios do
YouTube.

Mais informações, incluindo os dois comandos necessários e como voltar à configuração anterior:
[docs/faq.md](docs/faq.md#can-it-block-ads-inside-apps-too)

</details>

<details>
<summary><b>Há opções nas próprias Configurações da TV que também vale a pena desativar?</b></summary>

Sim, algumas, e nenhuma exige ADB: ID de publicidade, Localização, Uso e diagnóstico e Verificação sempre
disponível, além do modo Somente aplicativos, quando o launcher original é mantido. Desative Uso e
diagnóstico antes de remover os aplicativos; em algumas TVs, a própria página dessa opção é removida
durante o processo.

Mais informações, com os caminhos para Google TV, Android TV e Fire OS:
[docs/faq.md](docs/faq.md#are-there-switches-in-the-tvs-own-settings-worth-flipping-too)

</details>

<details>
<summary><b>AirPlay?</b></summary>

Não por meio deste repositório. O Chromecast integrado continua funcionando para celulares Android.
Para iPhones, [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) funciona;
há um script de instalação para ele na [PR #84](https://github.com/Gathin23/cleanyourtv/pull/84).

</details>

<details>
<summary><b>Posso simplesmente baixar o APK?</b></summary>

Compile-o com o script de uma linha em [Fazendo manualmente](#fazendo-manualmente). Isso leva cerca de
um minuto e permite que você controle o que será instalado na sua TV. Para isso, são necessários o JDK
e o Android SDK mencionados naquela seção. Não há binários pré-compilados disponíveis.

</details>

## A história

Em 17 de outubro de 2026, publiquei [uma comparação de antes e depois](https://x.com/gathint/status/2111379569030939102)
da TV dos meus pais, que havia sido limpa pelo Claude Code via Wi-Fi. A publicação alcançou 8,6 milhões
de visualizações, outras pessoas começaram a fazer o mesmo em suas próprias TVs e as respostas deram origem
às listas deste repositório: mais de 170 modelos de TV, enviados por mais de 200 colaboradores, nos primeiros
quatro dias. Até o dia 19, o repositório se chamava tv-vibehack; os links antigos continuam funcionando.

Duas semanas depois, o projeto era o repositório em alta nº 1 no GitHub na semana de 19 a 25 de outubro,
com mais de 46 mil estrelas e 5,7 mil forks, além de 1,4 milhão de visualizações e 260 mil clones em 14 dias.
As listas agora abrangem 442 modelos de TV de 117 marcas. A repercussão: 412 artigos em 54 países e
229 vídeos no YouTube e no Bilibili, todos relacionados em [docs/PRESS.md](docs/PRESS.md)
(dados de 30 de outubro de 2026).

## Como contribuir

A contribuição mais útil é a lista de remoção de aplicativos da sua TV. Assim, a próxima pessoa com o mesmo
modelo não precisará descobrir tudo novamente. [CONTRIBUTING.md](CONTRIBUTING.md) explica o formato e os
testes solicitados. Se você for contribuir usando um agente de programação, [AGENTS.md](AGENTS.md) apresenta
as mesmas regras para ele.

| Como ajudar | O que significa |
|---|---|
| **Lista de remoção da sua TV** | É a contribuição mais útil: a próxima pessoa com esse modelo não precisará fazer a etapa de identificação. |
| **Launcher e prompt** | Melhorias no launcher e ajustes para deixar o prompt mais claro também são bem-vindos. |
| **Criou algo com base no projeto?** | Um launcher, port ou tema: adicione-o ao [SHOWCASE.md](SHOWCASE.md), que já reúne cento e noventa e três projetos organizados em telas iniciais, forks, complementos, ferramentas e outras plataformas, além de um índice por marca de TV. |

As pessoas cujo trabalho faz parte deste repositório:

<a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://contrib.rocks/image?repo=Gathin23/cleanyourtv&columns=16&max=400" alt="Contribuidores"></a>

<details>
<summary><strong>Histórico de estrelas</strong></summary>

<a href="https://star-history.com/#Gathin23/cleanyourtv&Date"><img src="https://api.star-history.com/svg?repos=Gathin23/cleanyourtv&type=Date" alt="Gráfico do histórico de estrelas" width="640"></a>

</details>

## Contato

| Assunto | Onde |
|---|---|
| **Dúvidas sobre sua TV** | [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions) |
| **Imprensa e outras consultas** | [hello@itsgathin.com](mailto:hello@itsgathin.com) |
| **Problemas de segurança** | Faça uma denúncia privada pela aba [Security](https://github.com/Gathin23/cleanyourtv/security). Consulte também [`SECURITY.md`](SECURITY.md). |

## Informações legais

- O CleanYourTV é um projeto independente de código aberto. Não é afiliado, endossado nem patrocinado por
  nenhum fabricante de TV ou plataforma. Os nomes de marcas e modelos são usados apenas para indicar em
  quais TVs uma lista foi testada e continuam sendo marcas registradas de seus respectivos proprietários.
- O projeto funciona somente por meio de configurações que a própria TV disponibiliza ao proprietário:
  Opções do desenvolvedor, depuração ADB e o comando `pm disable-user` do Android, que é equivalente à opção
  Desativar em Configurações > Apps. Ele não obtém acesso root à TV, não grava firmware e não contorna
  bloqueios, DRM ou proteção contra cópia. O repositório não contém software, firmware ou APKs de fabricantes.
- Nada é desinstalado, e `tools/restore.sh` restaura tudo. Você executa o processo em uma TV de sua propriedade,
  por sua conta e risco. Alterar o software de uma TV pode afetar a garantia ou o suporte técnico.
- Todo o conteúdo é fornecido "no estado em que se encontra", sem qualquer tipo de garantia, sob a licença
  MIT apresentada abaixo.

## Licença

MIT. Consulte [`LICENSE`](LICENSE). A fonte Outfit está licenciada sob a SIL Open Font License; consulte
`launcher/FONT-LICENSE-OFL.txt`.
