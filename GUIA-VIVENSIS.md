# Guia de Customização para Receptores Vivensis VX Smart (Google TV / Android TV)

Este guia e o script automatizado [`pipeline-vivensis.sh`](pipeline-vivensis.sh) permitem a qualquer usuário do receptor **Vivensis VX Smart (`HYR2B-Vivensis`)** instalar o launcher limpo do projeto [CleanYourTV](README.pt-BR.md) e otimizar o aparelho pelo Wi-Fi, mantendo **100% do funcionamento dos canais via satélite da Vivensis**.

---

## Por Que o Vivensis VX Smart Precisa Deste Pipeline Específico?

Diferente de TVs comuns, o receptor **Vivensis VX Smart** possui três particularidades críticas que este pipeline resolve automaticamente:
1. **Proteção do Sintonizador de Satélite (`com.vivensis.launcher`):**
   No Vivensis, o aplicativo de canais via satélite (`com.skyworthdigital.tv.TvActivity`) e o script que inicializa o sinal no boot (`TunerInputController$IntentReceiver`) moram dentro do pacote `com.vivensis.launcher`. Por isso, ele **jamais pode ser desativado** (`STOCK_LAUNCHER=none`).
2. **Persistência do Botão Home Após Reiniciar (`stock_home_redirect` + `HomeKeyService`):**
   Quando o receptor reinicia, o script de boot da Vivensis garante os sinais de TV e força a abertura da interface antiga (`LauncherActivity`). O pipeline configura o `HomeKeyService` para redirecionar apenas a `LauncherActivity` de volta para a nova tela inicial (**"Início"**), deixando a tela de canais ao vivo (`TvActivity`) e a sintonia (`com.vivensis.dtvsetup`) livres.
3. **Correção Automática para Quem Usa Pendrive como Memória Interna (`/mnt/expand`):**
   Se houver um pendrive USB formatado como armazenamento do sistema, o Android instala novos apps nele por padrão. Como o pendrive demora ~13 segundos a mais para montar durante o boot, o Android apaga a preferência da tela inicial se o launcher estiver no pendrive. O pipeline detecta isso e move o launcher (`572 KB`) automaticamente para a **memória flash interna (`/data/app`)** do receptor.

---

## Passo 1: Preparar o Receptor Vivensis

No controle remoto do seu Vivensis:
1. Abra **Configurações** → **Preferências do dispositivo** → **Sobre**;
2. Desça até **Número da versão (Build)** e aperte **OK 7 vezes** até aparecer *"Você agora é um desenvolvedor"*;
3. Volte uma tela, entre em **Opções do desenvolvedor** e ative a **Depuração USB**;
4. Anote o endereço IP do receptor em **Configurações** → **Rede e Internet** (ex.: `192.168.1.50`).

---

## Passo 2: Executar o Pipeline Automatizado no Computador

Com o computador na mesma rede que o receptor (e tendo `python3`, `JDK 17` ou `21` e `Android SDK 34` instalados), basta rodar no terminal:

```bash
./pipeline-vivensis.sh
```

O assistente interativo no terminal fará tudo passo a passo:
1. Conectará ao seu Vivensis (lembre-se de marcar **"Sempre permitir"** na tela da TV no primeiro aviso);
2. Criará um backup completo em `backup/<IP_DO_RECEPTOR>/`;
3. Perguntará se você quer o **Debloat seguro** (mantendo as funções do Google TV como protetor de tela, recomendações e busca por voz) e se quer o **AdBlock DNS** desligado ou ligado;
4. Permitirá escolher os 6 aplicativos fixos da barra inferior (ou manter o padrão apertando `Enter`);
5. Compilará, instalará na memória interna (`/data/app`) e ativará o redirecionamento de boot.

---

## Como Restaurar o Receptor ao Original

Se em qualquer momento você quiser reativar todos os pacotes e voltar para a tela inicial original da Vivensis, basta rodar o script gerado no seu backup:

```bash
./backup/<IP_DO_RECEPTOR>/restore.sh
```
