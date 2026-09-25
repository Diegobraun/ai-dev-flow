const INTERVALO = 4000;
const CHAVE_DO_GRUPO = 'devflow.grupo';

const ETAPAS = [
  { id: 'refinamento', nome: 'Refinamento', principal: 'refinar' },
  { id: 'aprovacao', nome: 'Aprovação', principal: 'aprovar-refinamento' },
  { id: 'desenvolvimento', nome: 'Desenvolvimento', principal: 'desenvolver' },
  { id: 'review', nome: 'Code review', principal: 'revisar' },
  { id: 'teste', nome: 'Teste integrado', principal: 'testar' },
  { id: 'pr', nome: 'Pull request', principal: 'abrir-pr' },
];

const COLUNAS = [
  { id: 'refinamento', nome: 'Refinamento' },
  { id: 'aprovacao', nome: 'Aprovação' },
  { id: 'desenvolvimento', nome: 'Desenvolvimento' },
  { id: 'review', nome: 'Review' },
  { id: 'teste', nome: 'Teste' },
  { id: 'pr', nome: 'PR' },
  { id: 'encerrada', nome: 'Concluída / Cancelada' },
];

const NOMES_CURTOS = {
  'preparar-workspace': 'Workspace',
  refinar: 'Refinamento',
  'aprovar-refinamento': 'Aprovação',
  'aprovar-entre-areas': 'Áreas',
  desenvolver: 'Desenvolvimento',
  revisar: 'Review',
  'decidir-review': 'Decisão do review',
  testar: 'Testes',
  'decidir-testes': 'Decisão dos testes',
  'aprovar-pr': 'Aprovação do PR',
  'abrir-pr': 'Abrir PR',
};

const ABA_DA_PENDENCIA = {
  'aprovar-refinamento': 'refinamento',
  'aprovar-entre-areas': 'refinamento',
  'decidir-review': 'review',
  'decidir-testes': 'testes',
  'aprovar-pr': 'pr',
};

const estado = {
  rota: null,
  geracao: 0,
  timer: null,
  detalhe: null,
  aba: null,
  abaEscolhida: false,
  documentoExibido: null,
  assinaturaDasAcoes: null,
  pendencias: new Map(),
};

const $ = (seletor, raiz = document) => raiz.querySelector(seletor);
const $$ = (seletor, raiz = document) => [...raiz.querySelectorAll(seletor)];

function esc(valor) {
  return String(valor ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function dinheiro(valor) {
  if (valor == null) return '—';
  return 'US$ ' + Number(valor).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function dataHora(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

function relativo(iso) {
  if (!iso) return '';
  const segundos = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (segundos < 60) return 'agora';
  const minutos = Math.floor(segundos / 60);
  if (minutos < 60) return `há ${minutos} min`;
  const horas = Math.floor(minutos / 60);
  if (horas < 24) return `há ${horas} h`;
  const dias = Math.floor(horas / 24);
  return dias === 1 ? 'ontem' : `há ${dias} dias`;
}

function duracao(inicio, fim) {
  if (!inicio) return '';
  const ms = (fim ? new Date(fim) : new Date()) - new Date(inicio);
  const segundos = Math.max(0, Math.round(ms / 1000));
  if (segundos < 60) return `${segundos} s`;
  const minutos = Math.floor(segundos / 60);
  if (minutos < 60) return `${minutos} min ${String(segundos % 60).padStart(2, '0')} s`;
  const horas = Math.floor(minutos / 60);
  if (horas < 48) return `${horas} h ${String(minutos % 60).padStart(2, '0')} min`;
  return `${Math.floor(horas / 24)} dias`;
}

function markdown(texto) {
  if (!texto) return '';
  let conteudo = String(texto).trim();
  if (conteudo.startsWith('---')) {
    const fim = conteudo.indexOf('\n---', 3);
    if (fim > 0) conteudo = conteudo.slice(fim + 4).trim();
  }
  return DOMPurify.sanitize(marked.parse(conteudo, { gfm: true }));
}

function repositorioCurto(repositorio) {
  if (!repositorio) return '';
  const partes = repositorio.replace(/\.git$/, '').split(/[/:]/).filter(Boolean);
  return partes.slice(-2).join('/');
}

function lista(valor) {
  if (Array.isArray(valor)) return valor;
  if (valor == null || valor === '') return [];
  return [valor];
}

function textoDe(valor) {
  if (valor == null) return '';
  if (typeof valor === 'object') return JSON.stringify(valor);
  return String(valor);
}

async function api(caminho, opcoes = {}) {
  const resposta = await fetch(caminho, {
    ...opcoes,
    headers: { Accept: 'application/json', ...(opcoes.body ? { 'Content-Type': 'application/json' } : {}) },
  });
  if (!resposta.ok) {
    let mensagem = `${resposta.status} ${resposta.statusText}`;
    try {
      const corpo = await resposta.json();
      mensagem = corpo.erro || corpo.detail || corpo.message || corpo.error || mensagem;
    } catch (e) {
      mensagem = mensagem.trim();
    }
    const erro = new Error(mensagem);
    erro.status = resposta.status;
    throw erro;
  }
  if (resposta.status === 204) return null;
  const texto = await resposta.text();
  return texto ? JSON.parse(texto) : null;
}

function avisar(mensagem, erro = false) {
  const aviso = $('#aviso');
  aviso.textContent = mensagem;
  aviso.classList.toggle('erro', erro);
  aviso.hidden = false;
  clearTimeout(avisar.timer);
  avisar.timer = setTimeout(() => { aviso.hidden = true; }, erro ? 7000 : 4000);
}

function marcarAtualizacao(erro) {
  const indicador = $('#ao-vivo');
  const alerta = $('#alerta');
  indicador.classList.toggle('erro', !!erro);
  indicador.classList.toggle('pausado', !erro && document.hidden);
  if (erro) {
    alerta.textContent = `Não consegui falar com o Camunda: ${erro.message}`;
    alerta.hidden = false;
    $('#atualizado').textContent = 'tentando de novo…';
  } else {
    alerta.hidden = true;
    $('#atualizado').textContent = 'atualizado às ' + new Date().toLocaleTimeString('pt-BR');
  }
}

function rotaAtual() {
  const partes = location.hash.replace(/^#\/?/, '').split('/').filter(Boolean).map(decodeURIComponent);
  if (partes[0] === 'tarefa' && partes[1]) return { nome: 'tarefa', chave: partes[1] };
  if (partes[0] === 'pendencias') return { nome: 'pendencias' };
  if (partes[0] === 'nova') return { nome: 'nova' };
  return { nome: 'quadro' };
}

function navegar() {
  estado.rota = rotaAtual();
  estado.geracao++;
  estado.detalhe = null;
  estado.aba = null;
  estado.abaEscolhida = false;
  estado.documentoExibido = null;
  estado.assinaturaDasAcoes = null;
  estado.pendencias.clear();
  $$('.nav a').forEach(a => a.classList.toggle('atual', a.dataset.rota === (estado.rota.nome === 'tarefa' ? 'quadro' : estado.rota.nome)));
  VIEWS[estado.rota.nome].montar();
  window.scrollTo({ top: 0 });
  atualizar();
}

async function atualizar() {
  clearTimeout(estado.timer);
  const geracao = estado.geracao;
  const view = VIEWS[estado.rota.nome];
  if (!document.hidden) {
    try {
      await Promise.all([view.carregar(geracao), estado.rota.nome === 'pendencias' ? null : contarPendencias()]);
      if (geracao === estado.geracao) marcarAtualizacao(null);
    } catch (erro) {
      if (geracao === estado.geracao) marcarAtualizacao(erro);
    }
  } else {
    marcarAtualizacao(null);
  }
  if (geracao === estado.geracao && view.acompanhar) {
    estado.timer = setTimeout(atualizar, INTERVALO);
  }
}

async function contarPendencias() {
  const pendencias = await api('/api/pendencias');
  atualizarContador(pendencias.length);
}

function atualizarContador(total) {
  $('#contador-pendencias').textContent = total > 0 ? total : '';
  document.title = total > 0 ? `(${total}) ai-dev-flow` : 'ai-dev-flow';
}

function carregando() {
  return '<div class="carregando"><i></i>Carregando…</div>';
}

const VIEWS = {
  quadro: {
    acompanhar: true,
    montar() {
      $('#conteudo').innerHTML = `
        <div class="cabecalho">
          <div>
            <h1>Quadro</h1>
            <p class="muted">Cada tarefa anda pelas etapas enquanto os agentes trabalham. As que esperam por você ficam destacadas.</p>
          </div>
          <div id="kpis" class="kpis"></div>
        </div>
        <div id="quadro" class="quadro">${carregando()}</div>`;
    },
    async carregar(geracao) {
      const tarefas = await api('/api/tarefas');
      if (geracao !== estado.geracao) return;
      desenharQuadro(tarefas);
    },
  },
  tarefa: {
    acompanhar: true,
    montar() {
      $('#conteudo').innerHTML = `
        <section id="topo" class="painel glass">${carregando()}</section>
        <section id="proximo" class="proximo glass" hidden></section>
        <div class="detalhe" style="margin-top: 14px">
          <div class="principal">
            <div id="acoes"></div>
            <section class="documentos glass">
              <nav id="abas" class="abas"></nav>
              <div id="documento" class="documento"></div>
            </section>
          </div>
          <aside class="lateral">
            <div id="areas"></div>
            <div id="incidentes"></div>
            <section id="resumos" class="painel glass resumos" hidden></section>
          </aside>
        </div>`;
    },
    async carregar(geracao) {
      const detalhe = await api(`/api/tarefas/${encodeURIComponent(estado.rota.chave)}`);
      if (geracao !== estado.geracao) return;
      estado.detalhe = detalhe;
      desenharTarefa(detalhe);
      await desenharAcoes(detalhe, geracao);
    },
  },
  pendencias: {
    acompanhar: true,
    montar() {
      $('#conteudo').innerHTML = `
        <div class="cabecalho">
          <div>
            <h1>Minhas pendências</h1>
            <p id="subtitulo-pendencias" class="muted">Decisões humanas que seguram o fluxo.</p>
          </div>
          <label class="filtro">Grupo
            <select id="grupo"><option value="">Todos os grupos</option></select>
          </label>
        </div>
        <div id="pendencias" class="pendencias">${carregando()}</div>`;
      $('#grupo').addEventListener('change', evento => {
        try { localStorage.setItem(CHAVE_DO_GRUPO, evento.target.value); } catch (e) { evento.target.dataset.semStorage = '1'; }
        atualizar();
      });
    },
    async carregar(geracao) {
      const grupo = grupoEscolhido();
      const [pendencias, grupos, todas] = await Promise.all([
        api('/api/pendencias' + (grupo ? `?grupo=${encodeURIComponent(grupo)}` : '')),
        api('/api/grupos'),
        grupo ? api('/api/pendencias') : null,
      ]);
      if (geracao !== estado.geracao) return;
      atualizarContador((todas ?? pendencias).length);
      desenharGrupos(grupos, grupo);
      desenharPendencias(pendencias, grupo);
    },
  },
  nova: {
    acompanhar: false,
    montar() {
      $('#conteudo').innerHTML = `
        <div class="cabecalho">
          <div>
            <h1>Nova tarefa</h1>
            <p class="muted">O devflow clona o repositório, cria a branch <code>devflow/&lt;tarefa&gt;</code> e passa a tarefa por refinamento, desenvolvimento, code review e teste integrado. Você aprova o refinamento e o pull request.</p>
          </div>
        </div>
        <section class="painel glass formulario">
          <p class="orientacao">Ao iniciar, os agentes começam a rodar com o Claude Code e cada etapa tem custo. O refinador pergunta o que faltar na descrição.</p>
          <form id="nova-tarefa" novalidate>
            <label class="inteiro">Descrição
              <textarea name="descricao" required placeholder="O pedido como viria no ticket"></textarea>
              <small>O refinador transforma isso em arquivos afetados, contratos, critérios de aceite e perguntas.</small>
            </label>
            <label class="inteiro">Repositório
              <input name="repositorio" required placeholder="https://github.com/org/servico.git ou /caminho/local">
              <small>URL git ou caminho local na máquina do worker.</small>
            </label>
            <label>Identificador
              <input name="tarefa" pattern="[a-z0-9][a-z0-9\\-]{1,60}" placeholder="gerado a partir da descrição">
              <small>Minúsculas, números e hífen. Vira o nome da branch e da pasta .devflow/.</small>
            </label>
            <label>Branch base
              <input name="branchBase" value="main" required>
            </label>
            <label>Rodadas de review antes de chamar um humano
              <input name="limiteDeRevisoes" type="number" min="1" max="10" value="3" required>
            </label>
            <div class="inteiro botoes">
              <span id="erro-nova" class="erro-campo"></span>
              <a class="muted" href="#/">Cancelar</a>
              <button type="submit">Iniciar tarefa</button>
            </div>
          </form>
        </section>`;
      $('#nova-tarefa').addEventListener('submit', iniciarTarefa);
    },
    async carregar() {
      return null;
    },
  },
};

function grupoEscolhido() {
  try {
    return localStorage.getItem(CHAVE_DO_GRUPO) || '';
  } catch (e) {
    return $('#grupo')?.value || '';
  }
}

function colunaDe(tarefa) {
  if (tarefa.estado === 'concluida' || tarefa.estado === 'cancelada') return 'encerrada';
  return COLUNAS.some(c => c.id === tarefa.etapa) ? tarefa.etapa : 'outra';
}

function desenharQuadro(tarefas) {
  const ativas = tarefas.filter(t => t.estado === 'ativa' || t.estado === 'incidente');
  const humanas = tarefas.filter(t => t.pendencias.length > 0).length;
  const incidentes = tarefas.filter(t => t.estado === 'incidente').length;
  const custo = tarefas.reduce((soma, t) => soma + (t.custoUsd || 0), 0);
  $('#kpis').innerHTML = `
    <div class="kpi"><b>${ativas.length}</b><small>em andamento</small></div>
    <div class="kpi ${humanas ? 'alerta-kpi' : ''}"><b>${humanas}</b><small>esperando você</small></div>
    <div class="kpi ${incidentes ? 'erro-kpi' : ''}"><b>${incidentes}</b><small>com incidente</small></div>
    <div class="kpi"><b>${esc(dinheiro(custo))}</b><small>custo dos agentes</small></div>`;

  const colunas = [...COLUNAS];
  if (tarefas.some(t => colunaDe(t) === 'outra')) {
    colunas.splice(colunas.length - 1, 0, { id: 'outra', nome: 'Outras etapas' });
  }
  if (!tarefas.length) {
    $('#quadro').innerHTML = `
      <div class="vazio-grande glass painel" style="grid-column: 1 / -1">
        <h2>Nenhuma tarefa ainda</h2>
        <p>Comece por uma <a href="#/nova">nova tarefa</a>. Ela aparece aqui e anda pelas colunas enquanto os agentes trabalham.</p>
      </div>`;
    return;
  }
  $('#quadro').innerHTML = colunas.map(coluna => {
    const daColuna = tarefas.filter(t => colunaDe(t) === coluna.id);
    return `
      <section class="coluna etapa-${coluna.id}">
        <header>${esc(coluna.nome)}<span>${daColuna.length}</span></header>
        <div class="cartoes">
          ${daColuna.length ? daColuna.map(cartao).join('') : '<div class="vazio">Nada aqui</div>'}
        </div>
      </section>`;
  }).join('');
}

function cartao(tarefa) {
  const coluna = colunaDe(tarefa);
  const classeDaEtapa = coluna === 'encerrada' ? `etapa-${tarefa.estado}` : `etapa-${coluna}`;
  const badges = [];
  if (tarefa.estado === 'incidente') badges.push('<span class="badge erro">incidente</span>');
  if (tarefa.pendencias.length) {
    const texto = tarefa.pendencias.length > 1 ? `${tarefa.pendencias.length} aprovações` : 'aguardando você';
    badges.push(`<span class="badge alerta pulsa">${texto}</span>`);
  } else if (tarefa.estado === 'ativa') {
    badges.push('<span class="badge info pulsa">agente trabalhando</span>');
  }
  if (tarefa.estado === 'concluida') badges.push('<span class="badge ok">concluída</span>');
  if (tarefa.estado === 'cancelada') badges.push('<span class="badge erro">cancelada</span>');
  const rodada = rodadaDoCartao(tarefa, coluna);
  if (rodada) badges.push(`<span class="badge neutro">${esc(rodada)}</span>`);
  if (tarefa.custoUsd != null) badges.push(`<span class="badge neutro">${esc(dinheiro(tarefa.custoUsd))}</span>`);
  if (tarefa.prUrl) badges.push('<span class="badge ok">PR aberto</span>');
  const onde = tarefa.estado === 'concluida' || tarefa.estado === 'cancelada'
    ? relativo(tarefa.fim)
    : tarefa.elementosAtivos.join(', ');
  return `
    <a class="cartao ${classeDaEtapa} ${tarefa.pendencias.length ? 'humano' : ''} ${tarefa.estado === 'incidente' ? 'falha' : ''}"
       href="#/tarefa/${encodeURIComponent(tarefa.processInstanceKey)}">
      <strong>${esc(tarefa.tarefa || tarefa.processInstanceKey)}</strong>
      <div class="sub">${esc(repositorioCurto(tarefa.repositorio))}${onde ? ' · ' + esc(onde) : ''}</div>
      <div class="meta">${badges.join('')}</div>
    </a>`;
}

function rodadaDoCartao(tarefa, coluna) {
  if ((coluna === 'refinamento' || coluna === 'aprovacao') && tarefa.rodadaDeRefinamento) {
    return `rodada ${tarefa.rodadaDeRefinamento}`;
  }
  if (coluna === 'desenvolvimento' && tarefa.rodadaDeDesenvolvimento) {
    return `rodada ${tarefa.rodadaDeDesenvolvimento}`;
  }
  if ((coluna === 'review' || coluna === 'teste' || coluna === 'pr') && tarefa.rodadaDeRevisao) {
    return `review ${tarefa.rodadaDeRevisao}${tarefa.limiteDeRevisoes ? '/' + tarefa.limiteDeRevisoes : ''}`;
  }
  return null;
}

function etapaDoElemento(detalhe, elementId, elementInstanceKey) {
  const passo = detalhe.historico.find(p => p.elementInstanceKey === elementInstanceKey)
    || detalhe.historico.find(p => p.elementId === elementId);
  return passo ? passo.etapa : null;
}

function passosDaLinhaDoTempo(detalhe) {
  const { historico, resumo, pendencias, incidentes } = detalhe;
  const encerrada = resumo.estado === 'concluida' || resumo.estado === 'cancelada';
  const passos = ETAPAS.map(etapa => {
    const itens = historico.filter(p => p.etapa === etapa.id);
    const ativo = itens.some(p => p.estado === 'ACTIVE');
    const falhou = incidentes.some(i => etapaDoElemento(detalhe, i.elementId) === etapa.id);
    const humano = pendencias.some(p => etapaDoElemento(detalhe, p.elementId, p.elementInstanceKey) === etapa.id);
    const rodadas = historico.filter(p => p.elementId === etapa.principal).length;
    let situacao = 'pendente';
    let detalheDoPasso = encerrada ? 'não executada' : 'a seguir';
    if (falhou) {
      situacao = 'falhou';
      detalheDoPasso = 'incidente';
    } else if (humano) {
      situacao = 'atual';
      detalheDoPasso = 'aguardando você';
    } else if (ativo) {
      situacao = 'atual';
      const desde = itens.filter(p => p.estado === 'ACTIVE').map(p => p.inicio).sort()[0];
      detalheDoPasso = `em andamento · ${duracao(desde)}`;
    } else if (itens.length) {
      situacao = 'feito';
      const tempo = itens.reduce((soma, p) => soma + (p.inicio && p.fim ? new Date(p.fim) - new Date(p.inicio) : 0), 0);
      detalheDoPasso = rodadas > 1 ? `${rodadas} rodadas` : (tempo > 1000 ? duracao(new Date(Date.now() - tempo).toISOString()) : 'concluída');
    }
    return { ...etapa, situacao, detalhe: detalheDoPasso, rodadas };
  });
  const cancelada = resumo.estado === 'cancelada';
  passos.push({
    id: cancelada ? 'cancelada' : 'concluida',
    nome: cancelada ? 'Cancelada' : 'Concluída',
    situacao: encerrada ? 'feito' : 'pendente',
    detalhe: encerrada ? dataHora(resumo.fim) : 'fim',
    rodadas: 0,
  });
  return passos;
}

function desenharLinhaDoTempo(detalhe) {
  return `<div class="linha-do-tempo">${passosDaLinhaDoTempo(detalhe).map((passo, indice) => {
    const simbolo = passo.situacao === 'feito' ? (passo.id === 'cancelada' ? '✕' : '✓') : passo.situacao === 'falhou' ? '!' : indice + 1;
    return `
      <div class="passo etapa-${passo.id} ${passo.situacao}">
        <span class="no">${simbolo}${passo.rodadas > 1 ? `<span class="rodadas">×${passo.rodadas}</span>` : ''}</span>
        <span class="nome">${esc(passo.nome)}</span>
        <small>${esc(passo.detalhe)}</small>
      </div>`;
  }).join('')}</div>`;
}

function desenharTrilha(detalhe) {
  const relevantes = detalhe.historico.filter(p => p.tipo === 'SERVICE_TASK' || p.tipo === 'USER_TASK');
  if (!relevantes.length) return '';
  const contagem = {};
  const blocos = [];
  relevantes.forEach(p => {
    const anterior = blocos[blocos.length - 1];
    if (anterior && anterior.elementId === p.elementId && p.elementId === 'aprovar-entre-areas') {
      anterior.quantidade++;
      anterior.ativo = anterior.ativo || p.estado === 'ACTIVE';
      return;
    }
    contagem[p.elementId] = (contagem[p.elementId] || 0) + 1;
    blocos.push({ ...p, numero: contagem[p.elementId], quantidade: 1, ativo: p.estado === 'ACTIVE' });
  });
  return `<div class="trilha">${blocos.map((b, i) => {
    const nome = NOMES_CURTOS[b.elementId] || b.nome;
    const numero = contagem[b.elementId] > 1 ? ` ${b.numero}` : '';
    const quantidade = b.quantidade > 1 ? ` ×${b.quantidade}` : '';
    const classe = b.incidente ? 'erro' : '';
    return `${i ? '<span class="seta">→</span>' : ''}<span class="badge etapa-${b.etapa} ${classe} ${b.ativo ? 'pulsa' : ''}" title="${esc(dataHora(b.inicio))}${b.fim ? ' – ' + esc(dataHora(b.fim)) : ''}">${esc(nome + numero + quantidade)}</span>`;
  }).join('')}</div>`;
}

function badgeDoEstado(resumo) {
  switch (resumo.estado) {
    case 'concluida': return '<span class="badge ok">concluída</span>';
    case 'cancelada': return '<span class="badge erro">cancelada</span>';
    case 'incidente': return '<span class="badge erro pulsa">incidente</span>';
    default: return resumo.pendencias.length
      ? '<span class="badge alerta pulsa">aguardando você</span>'
      : '<span class="badge info pulsa">agente trabalhando</span>';
  }
}

function desenharTarefa(detalhe) {
  const { resumo, variaveis } = detalhe;
  document.title = `${resumo.tarefa || resumo.processInstanceKey} · ai-dev-flow`;
  const rodadas = [
    resumo.rodadaDeRefinamento ? `${resumo.rodadaDeRefinamento} de refinamento` : null,
    resumo.rodadaDeDesenvolvimento ? `${resumo.rodadaDeDesenvolvimento} de desenvolvimento` : null,
    resumo.rodadaDeRevisao ? `${resumo.rodadaDeRevisao}${resumo.limiteDeRevisoes ? '/' + resumo.limiteDeRevisoes : ''} de review` : null,
  ].filter(Boolean);
  $('#topo').innerHTML = `
    <div class="cabecalho" style="margin-bottom: 0">
      <div>
        <div class="titulo"><h1>${esc(resumo.tarefa || resumo.processInstanceKey)}</h1>${badgeDoEstado(resumo)}</div>
        <div class="linha-meta">
          ${resumo.repositorio ? `<span>repositório <b>${esc(repositorioCurto(resumo.repositorio))}</b></span>` : ''}
          ${resumo.branch ? `<span>branch <b class="mono">${esc(resumo.branch)}</b></span>` : ''}
          ${resumo.inicio ? `<span>iniciada <b title="${esc(dataHora(resumo.inicio))}">${esc(relativo(resumo.inicio))}</b></span>` : ''}
          <span>duração <b>${esc(duracao(resumo.inicio, resumo.fim))}</b></span>
          <span class="mono">#${esc(resumo.processInstanceKey)}</span>
        </div>
      </div>
      <div class="kpis">
        <div class="kpi"><b>${esc(dinheiro(resumo.custoUsd))}</b><small>custo dos agentes</small></div>
        <div class="kpi"><b>${esc(resumo.rodadaDeRevisao ?? 0)}</b><small>rodadas de review</small></div>
        ${variaveis.commits != null ? `<div class="kpi"><b>${esc(variaveis.commits)}</b><small>commits</small></div>` : ''}
      </div>
    </div>
    ${desenharLinhaDoTempo(detalhe)}
    ${desenharTrilha(detalhe)}
    ${rodadas.length ? `<div class="linha-meta">rodadas: ${esc(rodadas.join(' · '))}</div>` : ''}`;
  desenharProximo(detalhe);
  desenharAbas(detalhe);
  desenharAreas(detalhe);
  desenharIncidentes(detalhe);
  desenharResumos(detalhe);
}

function abasDisponiveis(detalhe) {
  const v = detalhe.variaveis;
  const abas = [{ id: 'pedido', nome: 'Pedido', conteudo: v.descricao }];
  if (v.refinamentoDocumento) abas.push({ id: 'refinamento', nome: 'Refinamento', extra: v.rodadaDeRefinamento ? `rodada ${v.rodadaDeRefinamento}` : '', conteudo: v.refinamentoDocumento });
  if (v.reviewDocumento) abas.push({ id: 'review', nome: 'Code review', extra: v.rodadaDeRevisao ? `rodada ${v.rodadaDeRevisao}` : '', conteudo: v.reviewDocumento });
  if (v.testesDocumento) abas.push({ id: 'testes', nome: 'Testes', conteudo: v.testesDocumento });
  if (detalhe.prDocumento) abas.push({ id: 'pr', nome: 'Pull request', conteudo: detalhe.prDocumento });
  abas.push({ id: 'historico', nome: 'Histórico', extra: String(detalhe.historico.length) });
  abas.push({ id: 'variaveis', nome: 'Variáveis', extra: String(Object.keys(v).length) });
  return abas;
}

function abaPadrao(detalhe, abas) {
  const ids = abas.map(a => a.id);
  for (const pendencia of detalhe.pendencias) {
    const preferida = ABA_DA_PENDENCIA[pendencia.elementId];
    if (preferida === 'pr' && !ids.includes('pr') && ids.includes('testes')) return 'testes';
    if (preferida && ids.includes(preferida)) return preferida;
  }
  return ['pr', 'testes', 'review', 'refinamento'].find(id => ids.includes(id)) || 'pedido';
}

function desenharAbas(detalhe) {
  const abas = abasDisponiveis(detalhe);
  if (!estado.abaEscolhida || !abas.some(a => a.id === estado.aba)) {
    estado.aba = abaPadrao(detalhe, abas);
  }
  $('#abas').innerHTML = abas.map(a => `
    <button type="button" data-aba="${a.id}" class="${a.id === estado.aba ? 'ativa' : ''}">${esc(a.nome)}${a.extra ? `<small>${esc(a.extra)}</small>` : ''}</button>`).join('');
  $$('#abas button').forEach(botao => botao.addEventListener('click', () => {
    estado.aba = botao.dataset.aba;
    estado.abaEscolhida = true;
    desenharAbas(estado.detalhe);
  }));
  const aba = abas.find(a => a.id === estado.aba);
  let html;
  if (aba.id === 'historico') {
    html = desenharHistorico(detalhe);
  } else if (aba.id === 'variaveis') {
    html = desenharVariaveis(detalhe.variaveis);
  } else {
    html = aba.conteudo ? `<article class="markdown">${markdown(aba.conteudo)}</article>` : '<p class="muted">Sem conteúdo.</p>';
  }
  const assinatura = aba.id + '\u0000' + html;
  if (estado.documentoExibido !== assinatura) {
    const abertos = new Set($$('#documento details[open]').map(d => d.dataset.nome));
    $('#documento').innerHTML = html;
    $$('#documento details').forEach(d => { if (abertos.has(d.dataset.nome)) d.open = true; });
    estado.documentoExibido = assinatura;
  }
}

function desenharHistorico(detalhe) {
  const passos = detalhe.historico.filter(p => p.tipo !== 'SEQUENCE_FLOW');
  if (!passos.length) return '<p class="muted">Nenhum elemento visitado ainda.</p>';
  return `<div style="overflow-x:auto"><table class="historico">
    <thead><tr><th>Elemento</th><th>Tipo</th><th>Início</th><th>Duração</th><th>Estado</th></tr></thead>
    <tbody>${passos.map(p => `
      <tr class="etapa-${p.etapa}">
        <td><span class="ponto"></span>${esc(p.nome)}</td>
        <td class="muted">${esc(tipoLegivel(p.tipo))}</td>
        <td title="${esc(p.inicio || '')}">${esc(dataHora(p.inicio))}</td>
        <td>${esc(duracao(p.inicio, p.fim))}</td>
        <td>${p.incidente ? '<span class="badge erro">incidente</span>' : p.estado === 'ACTIVE' ? '<span class="badge info pulsa">ativo</span>' : p.estado === 'TERMINATED' ? '<span class="badge neutro">interrompido</span>' : '<span class="badge ok">concluído</span>'}</td>
      </tr>`).join('')}</tbody></table></div>`;
}

function tipoLegivel(tipo) {
  return {
    START_EVENT: 'início', END_EVENT: 'fim', SERVICE_TASK: 'agente', USER_TASK: 'humano',
    EXCLUSIVE_GATEWAY: 'decisão', PARALLEL_GATEWAY: 'paralelo', MULTI_INSTANCE_BODY: 'multi-instância',
  }[tipo] || (tipo || '').toLowerCase();
}

function desenharVariaveis(variaveis) {
  const nomes = Object.keys(variaveis).sort((a, b) => a.localeCompare(b));
  if (!nomes.length) return '<p class="muted">Sem variáveis.</p>';
  return `<div class="variaveis">${nomes.map(nome => {
    const valor = variaveis[nome];
    const texto = typeof valor === 'string' ? valor : JSON.stringify(valor, null, 2);
    const previa = textoDe(valor).slice(0, 140);
    return `<details data-nome="${esc(nome)}"><summary><code>${esc(nome)}</code><span>${esc(previa)}</span></summary><pre>${esc(texto)}</pre></details>`;
  }).join('')}</div>`;
}

function desenharAreas(detalhe) {
  const areas = detalhe.areas || [];
  if (!areas.length) {
    $('#areas').innerHTML = '';
    return;
  }
  const aprovadas = areas.filter(a => a.status === 'aprovada').length;
  const atuais = areas.filter(a => a.status !== 'retirada').length;
  const rotulos = { aprovada: 'aprovada', recusada: 'recusada', pendente: 'aguardando decisão', respondida: 'respondida', aguardando: 'ainda não chamada', retirada: 'fora do refinamento atual' };
  const classes = { aprovada: 'ok', recusada: 'erro', pendente: 'alerta pulsa', respondida: 'info', aguardando: 'neutro', retirada: 'neutro' };
  $('#areas').innerHTML = `
    <section class="painel glass">
      <h3>Áreas afetadas · ${aprovadas}/${atuais} aprovadas</h3>
      <ul class="lista">${areas.map(a => `
        <li class="area ${esc(a.status)}">
          <div class="row"><strong>${esc(a.area)}</strong><span class="badge ${classes[a.status] || 'neutro'}">${esc(rotulos[a.status] || a.status)}</span></div>
          ${lista(a.times).length ? `<div class="sub">times: ${esc(lista(a.times).map(textoDe).join(', '))}</div>` : ''}
          ${lista(a.servicos).length ? `<div class="sub">serviços: ${esc(lista(a.servicos).map(textoDe).join(', '))}</div>` : ''}
          ${lista(a.contratos).length ? `<div class="sub">contratos: ${esc(lista(a.contratos).map(textoDe).join(', '))}</div>` : ''}
          ${a.motivo ? `<div class="sub">${a.status === 'retirada' ? 'recusou numa rodada anterior' : 'motivo'}: ${esc(a.motivo)}</div>` : ''}
        </li>`).join('')}
      </ul>
    </section>`;
}

function desenharIncidentes(detalhe) {
  if (!detalhe.incidentes.length) {
    $('#incidentes').innerHTML = '';
    return;
  }
  $('#incidentes').innerHTML = detalhe.incidentes.map(i => {
    const passo = detalhe.historico.find(p => p.elementId === i.elementId);
    return `
      <section class="painel glass incidente">
        <h2>Incidente em ${esc(passo ? passo.nome : i.elementId)}</h2>
        <div class="row"><span class="badge erro">${esc(i.tipo)}</span> <span class="muted">${esc(relativo(i.criadoEm))}</span></div>
        <pre class="mono">${esc(i.mensagem)}</pre>
        <p class="muted" style="margin:0;font-size:12.5px">O log da chamada fica em <code>.devflow/&lt;tarefa&gt;/logs/</code> no workspace. Depois de corrigir a causa, dê retry no Operate.</p>
      </section>`;
  }).join('');
}

const TRABALHO_DOS_AGENTES = {
  'preparar-workspace': ['Preparando o workspace', 'Clonando o repositório e criando a branch da tarefa.'],
  refinar: ['O refinador está trabalhando', 'Lendo o código, consultando o grafo de contratos e escrevendo o refinamento.'],
  desenvolver: ['O desenvolvedor está trabalhando', 'Implementando o refinamento aprovado, rodando o build e fazendo commits.'],
  revisar: ['O revisor está trabalhando', 'Comparando o diff com o refinamento, rodando o build e conferindo os contratos.'],
  testar: ['O testador está trabalhando', 'Escrevendo e rodando os testes integrados de cada critério de aceite.'],
  'abrir-pr': ['Abrindo o pull request', 'Montando o corpo do PR com o refinamento, o review e os testes.'],
};

const O_QUE_DECIDIR = {
  'aprovar-refinamento': v => v.refinamentoStatus === 'com-perguntas'
    ? `O refinador tem ${lista(v.refinamentoPerguntas).length} pergunta(s) e não segue para o desenvolvimento sem as respostas. Responda abaixo; as que têm sugestão já vêm preenchidas.`
    : 'Leia o refinamento e aprove, ou peça um ajuste.',
  'aprovar-entre-areas': v => `A mudança afeta a área ${esc(v.aprovacao?.area || '')}. O time dela aprova ou recusa.`,
  'decidir-review': v => `O review bloqueou ${esc(v.rodadaDeRevisao ?? '')} vez(es). Decida se corrige de novo, volta ao refinamento, segue ou cancela.`,
  'decidir-testes': () => 'Os testes integrados falharam. Decida se volta para o desenvolvimento ou cancela.',
  'aprovar-pr': () => 'Tudo passou. Confira o resumo e abra o pull request.',
};

function desenharProximo(detalhe) {
  const { resumo, variaveis, pendencias, incidentes, historico } = detalhe;
  const alvo = $('#proximo');
  let tipo;
  let titulo;
  let texto;
  if (incidentes.length) {
    const i = incidentes[0];
    const passo = historico.find(p => p.elementId === i.elementId);
    tipo = 'erro';
    titulo = `Parou com erro em ${passo ? passo.nome : i.elementId}`;
    texto = `${esc(i.mensagem || '')}<br><span class="muted">Depois de corrigir a causa, tente de novo pelo Operate (Incidents, Retry).</span>`;
  } else if (pendencias.length) {
    const nomes = pendencias.map(p => p.nome || NOMES_CURTOS[p.elementId] || p.elementId);
    const explicar = O_QUE_DECIDIR[pendencias[0].elementId];
    tipo = 'voce';
    titulo = pendencias.length > 1 ? `Esperando ${pendencias.length} decisões: ${nomes.join(', ')}` : `Esperando você: ${nomes[0]}`;
    texto = explicar ? explicar({ ...variaveis, ...(estado.pendencias.get(pendencias[0].userTaskKey)?.variaveis || {}) }) : 'Decida no painel abaixo.';
  } else if (resumo.estado === 'concluida') {
    tipo = 'ok';
    titulo = 'Concluída';
    texto = variaveis.prUrl ? `Pull request aberto: <a href="${esc(variaveis.prUrl)}" target="_blank" rel="noopener">${esc(variaveis.prUrl)}</a>` : 'O corpo do PR está pronto na aba Pull request e no workspace.';
  } else if (resumo.estado === 'cancelada') {
    tipo = 'neutro';
    titulo = 'Cancelada';
    texto = 'O fluxo foi encerrado sem pull request.';
  } else {
    const ativo = [...historico].reverse().find(p => p.estado === 'ACTIVE' && TRABALHO_DOS_AGENTES[p.elementId]);
    const [nome, descricao] = ativo ? TRABALHO_DOS_AGENTES[ativo.elementId] : ['O fluxo está andando', 'Passando para a próxima etapa.'];
    tipo = 'agente';
    titulo = nome;
    const minutos = ativo?.inicio ? (Date.now() - new Date(ativo.inicio).getTime()) / 60000 : 0;
    texto = `${esc(descricao)}${ativo?.inicio ? ` <span class="muted">Começou ${esc(relativo(ativo.inicio))}. Costuma levar de 1 a 3 minutos.</span>` : ''}`;
    if (minutos > 8) {
      tipo = 'lento';
      texto += `<br><b>Está demorando mais que o normal.</b> <span class="muted">Um build ou teste pode estar esperando algo que não está no ar (Kafka, banco). O log do agente fica em <code>workspaces/${esc(resumo.tarefa)}/.devflow/${esc(resumo.tarefa)}/logs/</code>. O limite da etapa é 45 minutos.</span>`;
    }
  }
  alvo.hidden = false;
  alvo.className = `proximo glass ${tipo}`;
  alvo.innerHTML = `<div class="icone"></div><div><h2>${esc(titulo)}</h2><p>${texto}</p></div>`;
}

const ROTULOS_DE_STATUS = {
  pronto: 'pronto',
  'com-perguntas': 'com perguntas',
  ok: 'passou',
  falhou: 'falhou',
  'nao-executado': 'não rodou',
  aprovado: 'aprovado',
  bloqueado: 'bloqueado',
  implementado: 'implementado',
  impedido: 'impedido',
};

function rotuloDeStatus(valor) {
  return ROTULOS_DE_STATUS[valor] || valor;
}

function sugestaoDa(pergunta) {
  const achada = /\(sugest[aã]o:\s*([^)]*)\)/i.exec(pergunta);
  return achada ? achada[1].trim() : '';
}

function semSugestao(pergunta) {
  return pergunta.replace(/\s*\(sugest[aã]o:[^)]*\)\s*/i, ' ').replace(/^\(bloqueante\)\s*/i, '').trim();
}

function desenharResumos(detalhe) {
  const v = detalhe.variaveis;
  const linhas = [];
  if (v.refinamentoStatus) linhas.push(['Refinamento', `<span class="badge ${v.refinamentoStatus === 'pronto' ? 'ok' : 'alerta'}">${esc(rotuloDeStatus(v.refinamentoStatus))}</span>`]);
  if (v.desenvolvimentoStatus === 'impedido') linhas.push(['Desenvolvimento', '<span class="badge alerta">impedido</span>']);
  if (v.desenvolvimentoBuild) linhas.push(['Build', `<span class="badge ${v.desenvolvimentoBuild === 'ok' ? 'ok' : v.desenvolvimentoBuild === 'falhou' ? 'erro' : 'neutro'}">${esc(rotuloDeStatus(v.desenvolvimentoBuild))}</span>`]);
  if (v.veredito) linhas.push(['Review', `<span class="badge ${v.veredito === 'aprovado' ? 'ok' : 'erro'}">${esc(rotuloDeStatus(v.veredito))}</span>${v.bloqueantes != null ? ` <span class="muted">${esc(v.bloqueantes)} bloqueante(s)</span>` : ''}`]);
  if (v.testesAprovados != null) linhas.push(['Testes', `<span class="badge ${v.testesAprovados ? 'ok' : 'erro'}">${v.testesAprovados ? 'passaram' : 'falharam'}</span>`]);
  if (v.prUrl) linhas.push(['PR', `<a href="${esc(v.prUrl)}" target="_blank" rel="noopener">${esc(v.prUrl.replace(/^https?:\/\//, ''))}</a>`]);
  if (v.branchBase) linhas.push(['Base', `<span class="mono">${esc(v.branchBase)}</span>`]);
  if (v.workspace) linhas.push(['Workspace', `<span class="mono">${esc(v.workspace)}</span>`]);
  const textos = [
    ['Refinamento', v.refinamentoResumo],
    ['Desenvolvimento', v.desenvolvimentoResumo],
    ['Review', v.reviewResumo],
    ['Testes', v.testesResumo],
  ].filter(([, texto]) => texto);
  const secao = $('#resumos');
  if (!linhas.length && !textos.length) {
    secao.hidden = true;
    return;
  }
  secao.hidden = false;
  secao.innerHTML = `
    <h3>Resultado das etapas</h3>
    ${linhas.length ? `<dl>${linhas.map(([rotulo, valor]) => `<dt>${esc(rotulo)}</dt><dd>${valor}</dd>`).join('')}</dl>` : ''}
    ${textos.map(([rotulo, texto]) => `<div style="margin-top:12px"><h3 style="margin-bottom:4px">${esc(rotulo)}</h3><div class="markdown" style="font-size:13px">${markdown(texto)}</div></div>`).join('')}`;
}

async function desenharAcoes(detalhe, geracao) {
  const assinatura = detalhe.pendencias.map(p => p.userTaskKey).join(',');
  if (assinatura === estado.assinaturaDasAcoes) return;
  const pendencias = await Promise.all(detalhe.pendencias.map(async p => {
    if (!estado.pendencias.has(p.userTaskKey)) {
      estado.pendencias.set(p.userTaskKey, await api(`/api/pendencias/${encodeURIComponent(p.userTaskKey)}`));
    }
    return estado.pendencias.get(p.userTaskKey);
  }));
  if (geracao !== estado.geracao) return;
  estado.assinaturaDasAcoes = assinatura;
  const alvo = $('#acoes');
  alvo.innerHTML = '';
  pendencias.forEach(p => alvo.appendChild(painelDeAcao(p)));
}

function painelDeAcao(pendencia) {
  const construtor = PAINEIS[pendencia.elementId] || painelGenerico;
  const v = { ...(estado.detalhe?.variaveis || {}), ...(pendencia.variaveis || {}) };
  const { titulo, corpo, campos, botoes, coletar } = construtor(pendencia, v);
  const secao = document.createElement('section');
  secao.className = 'painel glass acao';
  secao.innerHTML = `
    <h2>${esc(titulo)}</h2>
    <div class="row muted" style="font-size:12px">
      <span>aberta ${esc(relativo(pendencia.criadaEm))}</span>
      ${lista(pendencia.candidateGroups).map(g => `<span class="badge neutro">${esc(g)}</span>`).join(' ')}
    </div>
    <div class="resumo">${corpo}</div>
    <form novalidate>
      ${campos}
      <div class="botoes">${botoes}</div>
      <span class="erro-campo" style="color:var(--error);font-size:12.5px"></span>
    </form>`;
  const form = $('form', secao);
  $$('[data-so-com]', form).forEach(campo => {
    const radios = $$('input[type=radio]', form);
    const aplicar = () => { campo.hidden = !radios.some(r => r.checked && r.value === campo.dataset.soCom); };
    radios.forEach(radio => radio.addEventListener('change', aplicar));
    aplicar();
  });
  $$('input[data-alterna]', form).forEach(alternador => {
    const alvo = $(alternador.dataset.alterna, form);
    const aplicar = () => { alvo.hidden = alternador.checked; };
    alternador.addEventListener('change', aplicar);
    aplicar();
  });
  form.addEventListener('submit', async evento => {
    evento.preventDefault();
    const erro = $('.erro-campo', form);
    erro.textContent = '';
    let variaveis;
    try {
      variaveis = coletar(form, evento.submitter?.value);
    } catch (falha) {
      erro.textContent = falha.message;
      return;
    }
    await concluir(pendencia, variaveis, form);
  });
  return secao;
}

async function concluir(pendencia, variaveis, form) {
  const botoes = $$('button', form);
  botoes.forEach(b => { b.disabled = true; });
  try {
    await api(`/api/pendencias/${encodeURIComponent(pendencia.userTaskKey)}/conclusao`, {
      method: 'POST',
      body: JSON.stringify(variaveis),
    });
    avisar(`${pendencia.nome}: decisão enviada. O fluxo segue.`);
    form.closest('.acao').remove();
    estado.assinaturaDasAcoes = null;
    estado.pendencias.delete(pendencia.userTaskKey);
    setTimeout(atualizar, 600);
  } catch (falha) {
    botoes.forEach(b => { b.disabled = false; });
    $('.erro-campo', form).textContent = falha.message;
    avisar(`Não foi possível concluir: ${falha.message}`, true);
  }
}

function textoObrigatorio(form, nome, mensagem) {
  const valor = (form.elements[nome]?.value || '').trim();
  if (!valor) {
    form.elements[nome]?.focus();
    throw new Error(mensagem);
  }
  return valor;
}

function escolhaObrigatoria(form, nome) {
  const marcada = $(`input[name="${nome}"]:checked`, form);
  if (!marcada) throw new Error('Escolha uma opção');
  return marcada.value;
}

function opcoes(nome, itens) {
  return `<div class="opcoes">${itens.map(([valor, rotulo, descricao]) => `
    <label class="opcao"><input type="radio" name="${nome}" value="${valor}">
      <span><b>${esc(rotulo)}</b>${descricao ? `<small>${esc(descricao)}</small>` : ''}</span>
    </label>`).join('')}</div>`;
}

const PAINEIS = {
  'aprovar-refinamento'(p, v) {
    const perguntas = lista(v.refinamentoPerguntas).map(textoDe);
    const comPerguntas = v.refinamentoStatus === 'com-perguntas' && perguntas.length;
    const cabecalho = `
      <div class="row" style="margin-bottom:8px">
        ${v.refinamentoStatus ? `<span class="badge ${v.refinamentoStatus === 'pronto' ? 'ok' : 'alerta'}">${esc(rotuloDeStatus(v.refinamentoStatus))}</span>` : ''}
        ${v.rodadaDeRefinamento ? `<span class="badge neutro">rodada ${esc(v.rodadaDeRefinamento)}</span>` : ''}
        <span class="badge neutro">custo até aqui ${esc(dinheiro(v.custoUsd))}</span>
      </div>
      ${v.refinamentoResumo ? `<div class="markdown">${markdown(v.refinamentoResumo)}</div>` : ''}
      <p class="muted" style="margin:8px 0 0">O documento completo está na aba Refinamento, logo abaixo.</p>`;
    if (comPerguntas) {
      return {
        titulo: 'O refinador precisa de respostas',
        corpo: cabecalho,
        campos: `
          <div class="perguntas">${perguntas.map((q, i) => `
            <label class="pergunta ${/^\(bloqueante\)/i.test(q) ? 'bloqueante' : ''}">
              <span class="enunciado"><b>${i + 1}.</b> ${esc(semSugestao(q))}${/^\(bloqueante\)/i.test(q) ? ' <span class="badge erro">bloqueia</span>' : ''}</span>
              <textarea name="resposta-${i}" rows="2" placeholder="Sua resposta">${esc(sugestaoDa(q))}</textarea>
              ${sugestaoDa(q) ? '<small class="muted">Preenchido com a sugestão do refinador. Edite se quiser outra coisa.</small>' : ''}
            </label>`).join('')}
          </div>
          <label class="campo">Mais algum ajuste (opcional)
            <textarea name="observacoesDoRefinamento" rows="2" placeholder="Ex.: manter a resposta antiga e criar GET /v2/loans"></textarea>
          </label>`,
        botoes: `
          ${perguntas.every(sugestaoDa) ? '<button type="submit" class="ghost" value="sugestoes">Aceitar as sugestões e seguir</button>' : ''}
          <button type="submit" class="sucesso" value="responder">Enviar respostas ao refinador</button>`,
        coletar(form, decisao) {
          if (decisao === 'sugestoes') return { refinamentoAprovado: true };
          const respostas = perguntas.map((q, i) => {
            const resposta = (form.elements[`resposta-${i}`].value || '').trim();
            return resposta ? `${i + 1}. ${semSugestao(q)}\nResposta: ${resposta}` : null;
          });
          if (respostas.some((resposta, i) => !resposta && (/^\(bloqueante\)/i.test(perguntas[i]) || !sugestaoDa(perguntas[i])))) {
            throw new Error('Responda as perguntas que bloqueiam e as que vieram sem sugestão');
          }
          const extra = (form.elements.observacoesDoRefinamento.value || '').trim();
          const texto = [...respostas.filter(Boolean), extra ? `Ajuste: ${extra}` : null].filter(Boolean).join('\n\n');
          if (!texto) throw new Error('Responda pelo menos uma pergunta');
          return { refinamentoAprovado: false, observacoesDoRefinamento: texto };
        },
      };
    }
    return {
      titulo: 'Aprovar refinamento',
      corpo: cabecalho,
      campos: `
        <label class="campo">Ajuste para o refinador (só se for pedir ajuste)
          <textarea name="observacoesDoRefinamento" rows="2" placeholder="Ex.: o limite é por faixa de risco, fuso America/Sao_Paulo"></textarea>
        </label>`,
      botoes: `
        <button type="submit" class="ghost" value="ajustar">Pedir ajuste</button>
        <button type="submit" class="sucesso" value="aprovar">Aprovar e seguir</button>`,
      coletar(form, decisao) {
        if (decisao === 'aprovar') return { refinamentoAprovado: true };
        return {
          refinamentoAprovado: false,
          observacoesDoRefinamento: textoObrigatorio(form, 'observacoesDoRefinamento', 'Escreva o que o refinador deve ajustar'),
        };
      },
    };
  },

  'decidir-review'(p, v) {
    return {
      titulo: 'Code review bloqueado',
      corpo: `
        <p style="margin:0 0 6px">${esc(v.rodadaDeRevisao ?? '?')} rodada(s) de review, <b>${esc(v.bloqueantes ?? '?')} bloqueante(s)</b> na última. Custo até aqui ${esc(dinheiro(v.custoUsd))}.</p>
        ${v.reviewResumo ? `<div class="markdown">${markdown(v.reviewResumo)}</div>` : ''}`,
      campos: `${opcoes('decisaoDoReview', [
        ['corrigir', 'Corrigir mais uma vez', 'O desenvolvedor recebe o último review e tenta de novo.'],
        ['refinar', 'Voltar ao refinamento', 'O problema está no plano, não no código. O refinador recebe o que você escrever e o review recomeça do zero.'],
        ['seguir', 'Seguir para os testes mesmo assim', 'Os bloqueantes ficam registrados no PR.'],
        ['cancelar', 'Cancelar a tarefa', 'Encerra o fluxo sem PR.'],
      ])}
        <label class="campo" data-so-com="refinar" hidden>O que o refinador precisa decidir ou corrigir
          <textarea name="observacoesDoRefinamento" rows="3" placeholder="Ex.: manter GET /loans como está e criar GET /v2/loans paginado"></textarea>
        </label>`,
      botoes: '<button type="submit">Enviar decisão</button>',
      coletar(form) {
        const decisao = escolhaObrigatoria(form, 'decisaoDoReview');
        if (decisao !== 'refinar') return { decisaoDoReview: decisao };
        return {
          decisaoDoReview: decisao,
          observacoesDoRefinamento: textoObrigatorio(form, 'observacoesDoRefinamento', 'Escreva o que o refinador deve mudar'),
        };
      },
    };
  },

  'decidir-testes'(p, v) {
    return {
      titulo: 'Testes integrados falharam',
      corpo: v.testesResumo ? `<div class="markdown">${markdown(v.testesResumo)}</div>` : '',
      campos: opcoes('decisaoDosTestes', [
        ['corrigir', 'Voltar para o desenvolvimento', 'O desenvolvedor recebe o testes.md com as falhas.'],
        ['cancelar', 'Cancelar a tarefa', 'Encerra o fluxo sem PR.'],
      ]),
      botoes: '<button type="submit">Enviar decisão</button>',
      coletar: form => ({ decisaoDosTestes: escolhaObrigatoria(form, 'decisaoDosTestes') }),
    };
  },

  'aprovar-pr'(p, v) {
    const resumos = [['Desenvolvimento', v.desenvolvimentoResumo], ['Review', v.reviewResumo], ['Testes', v.testesResumo]]
      .filter(([, texto]) => texto);
    return {
      titulo: 'Pronto para o pull request',
      corpo: `
        <p style="margin:0 0 6px">Branch <code>${esc(v.branch || '?')}</code> · ${esc(v.rodadaDeRevisao ?? 0)} rodada(s) de review · custo total ${esc(dinheiro(v.custoUsd))}</p>
        ${resumos.map(([rotulo, texto]) => `<p style="margin:6px 0"><b>${esc(rotulo)}:</b> ${esc(texto)}</p>`).join('')}`,
      campos: '',
      botoes: `
        <button type="submit" class="ghost" value="recusar">Não abrir</button>
        <button type="submit" class="sucesso" value="aprovar">Abrir o pull request</button>`,
      coletar: (form, decisao) => ({ prAprovado: decisao === 'aprovar' }),
    };
  },

  'aprovar-entre-areas'(p, v) {
    const aprovacao = v.aprovacao && typeof v.aprovacao === 'object' ? v.aprovacao : {};
    const area = aprovacao.area || p.area || lista(p.candidateGroups)[0] || 'área';
    const grupo = (rotulo, itens) => lista(itens).length
      ? `<div class="grupo-info"><small>${esc(rotulo)}</small><div class="chips">${lista(itens).map(i => `<span class="badge info">${esc(textoDe(i))}</span>`).join('')}</div></div>`
      : '';
    return {
      titulo: `Aprovação da área ${area}`,
      corpo: `
        <p style="margin:0 0 10px">A mudança de <b>${esc(v.tarefa || '')}</b> afeta a área <b>${esc(area)}</b>. Confira os times, serviços e contratos envolvidos antes de decidir.</p>
        ${grupo('Times', aprovacao.times)}
        ${grupo('Serviços', aprovacao.servicos)}
        ${grupo('Contratos', aprovacao.contratos)}`,
      campos: `
        <label class="campo">Motivo (obrigatório para recusar)
          <textarea name="motivo" placeholder="Ex.: o contrato GET /accounts muda sem versão e quebra o app"></textarea>
        </label>`,
      botoes: `
        <button type="submit" class="perigo" value="recusar">Recusar</button>
        <button type="submit" class="sucesso" value="aprovar">Aprovar</button>`,
      coletar(form, decisao) {
        if (decisao === 'recusar') {
          return { aprovado: false, motivo: textoObrigatorio(form, 'motivo', 'Explique o motivo da recusa') };
        }
        return { aprovado: true, motivo: (form.elements.motivo.value || '').trim() };
      },
    };
  },
};

function painelGenerico(p, v) {
  const locais = p.variaveis || {};
  const simples = Object.entries(locais)
    .filter(([, valor]) => valor == null || typeof valor !== 'object' || Array.isArray(valor))
    .filter(([, valor]) => textoDe(valor).length < 400)
    .slice(0, 14);
  return {
    titulo: p.nome || p.elementId,
    corpo: `
      <p class="muted" style="margin:0 0 8px">Tarefa humana sem painel próprio (<code>${esc(p.elementId)}</code>).</p>
      ${simples.length ? `<div class="resumos"><dl>${simples.map(([nome, valor]) => `<dt class="mono">${esc(nome)}</dt><dd>${esc(textoDe(valor))}</dd>`).join('')}</dl></div>` : ''}`,
    campos: `
      <label class="campo">Comentário
        <textarea name="motivo" placeholder="Opcional ao aprovar, obrigatório ao rejeitar"></textarea>
      </label>`,
    botoes: `
      <button type="submit" class="perigo" value="rejeitar">Rejeitar</button>
      <button type="submit" class="sucesso" value="aprovar">Aprovar</button>`,
    coletar(form, decisao) {
      if (decisao === 'rejeitar') {
        return { aprovado: false, motivo: textoObrigatorio(form, 'motivo', 'Explique o motivo da rejeição') };
      }
      return { aprovado: true, motivo: (form.elements.motivo.value || '').trim() };
    },
  };
}

function desenharGrupos(grupos, escolhido) {
  const select = $('#grupo');
  const opcoesDeGrupo = [...new Set([...grupos, ...(escolhido ? [escolhido] : [])])].sort();
  const html = '<option value="">Todos os grupos</option>' + opcoesDeGrupo.map(g => `<option value="${esc(g)}">${esc(g)}</option>`).join('');
  if (select.dataset.html !== html) {
    select.innerHTML = html;
    select.dataset.html = html;
  }
  select.value = escolhido;
}

function desenharPendencias(pendencias, grupo) {
  $('#subtitulo-pendencias').textContent = pendencias.length
    ? `${pendencias.length} decisão(ões) humana(s) segurando o fluxo${grupo ? ` no grupo ${grupo}` : ''}.`
    : 'Decisões humanas que seguram o fluxo.';
  if (!pendencias.length) {
    $('#pendencias').innerHTML = `
      <div class="vazio-grande glass painel" style="grid-column: 1 / -1">
        <h2>Nada esperando por você</h2>
        <p>${grupo ? `Nenhuma pendência para o grupo <b>${esc(grupo)}</b>.` : 'Os agentes estão trabalhando ou não há tarefas abertas.'} Esta lista se atualiza sozinha.</p>
      </div>`;
    return;
  }
  $('#pendencias').innerHTML = pendencias.map(p => {
    const etapa = { 'aprovar-refinamento': 'aprovacao', 'aprovar-entre-areas': 'aprovacao', 'decidir-review': 'review', 'decidir-testes': 'teste', 'aprovar-pr': 'pr' }[p.elementId] || 'outra';
    return `
      <a class="cartao painel etapa-${etapa}" href="#/tarefa/${encodeURIComponent(p.processInstanceKey)}">
        <div class="pendencia">
          <div class="topo"><span class="badge etapa-${etapa}" style="--c: var(--${etapa === 'outra' ? 'accent' : etapa})">${esc(p.nome)}</span><span class="muted" style="font-size:12px">${esc(relativo(p.criadaEm))}</span></div>
          <strong style="font-size:15px">${esc(p.tarefa || p.processInstanceKey)}</strong>
          ${p.area ? `<div class="sub">área <b style="color:var(--text)">${esc(p.area)}</b></div>` : ''}
          <div class="rodape">
            <span class="chips">${lista(p.candidateGroups).map(g => `<span class="badge neutro">${esc(g)}</span>`).join('') || '<span class="muted">sem grupo</span>'}</span>
            <span style="color:var(--accent);font-weight:600">Decidir →</span>
          </div>
        </div>
      </a>`;
  }).join('');
}

async function iniciarTarefa(evento) {
  evento.preventDefault();
  const form = evento.target;
  const erro = $('#erro-nova');
  erro.textContent = '';
  const dados = Object.fromEntries(new FormData(form).entries());
  const pedido = {
    descricao: (dados.descricao || '').trim(),
    repositorio: (dados.repositorio || '').trim(),
    branchBase: (dados.branchBase || '').trim() || 'main',
    limiteDeRevisoes: Number(dados.limiteDeRevisoes || 3),
  };
  if ((dados.tarefa || '').trim()) pedido.tarefa = dados.tarefa.trim();
  if (!pedido.descricao) { erro.textContent = 'Descreva a tarefa'; form.elements.descricao.focus(); return; }
  if (!pedido.repositorio) { erro.textContent = 'Informe o repositório'; form.elements.repositorio.focus(); return; }
  if (pedido.tarefa && !/^[a-z0-9][a-z0-9-]{1,60}$/.test(pedido.tarefa)) { erro.textContent = 'Identificador inválido: use minúsculas, números e hífen'; form.elements.tarefa.focus(); return; }
  if (!(pedido.limiteDeRevisoes >= 1 && pedido.limiteDeRevisoes <= 10)) { erro.textContent = 'O limite de rodadas vai de 1 a 10'; return; }
  const botao = $('button[type="submit"]', form);
  botao.disabled = true;
  try {
    const criada = await api('/tarefas', { method: 'POST', body: JSON.stringify(pedido) });
    avisar(`Tarefa ${criada.tarefa} iniciada.`);
    location.hash = `#/tarefa/${encodeURIComponent(criada.processInstanceKey)}`;
  } catch (falha) {
    botao.disabled = false;
    erro.textContent = falha.status === 400 ? 'Pedido inválido: confira os campos' : falha.message;
  }
}

document.addEventListener('visibilitychange', () => {
  if (!document.hidden && VIEWS[estado.rota.nome].acompanhar) atualizar();
  $('#ao-vivo').classList.toggle('pausado', document.hidden);
});
window.addEventListener('hashchange', navegar);
navegar();
