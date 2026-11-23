const byId = (id) => document.getElementById(id);
const form = byId('form');
const letter = byId('letter');
const langCodes = { PT: 'pt-BR', EN: 'en', ES: 'es' };
const languages = { PT: 'Português (Brasil)', EN: 'English', ES: 'Español' };
let settings;
let example;
let displayed;
let importing = 0;

function status(message, error = false, id = 'status') {
  byId(id).textContent = message;
  byId(id).dataset.error = String(error);
}

async function api(path, options) {
  const response = await fetch('/api' + path, options);
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || 'A solicitação falhou. Tente novamente.');
  }
  return response.json();
}

function post(path, data, access = '') {
  const headers = { 'Content-Type': 'application/json' };
  if (access) headers['X-Orbe-Access'] = access;
  return api(path, { method: 'POST', headers, body: JSON.stringify(data) });
}

function payload() {
  const inputs = new FormData(form);
  return Object.fromEntries(['name', 'resume', 'vacancy', 'language', 'tone', 'emphasis']
    .map((field) => [field, String(inputs.get(field) || '').trim()]));
}

function updateMetadata() {
  if (!displayed) return;
  const words = letter.value.match(/[\p{L}\p{N}]+(?:[’'-][\p{L}\p{N}]+)*/gu)?.length || 0;
  const labels = [languages[displayed.language], words + ' palavras'];
  if (displayed.example) labels.push('Exemplo fictício');
  if (words < 250 || words > 380) labels.push('Fora da faixa de 250 a 380');
  byId('metadata').textContent = labels.join(' · ');
}

function render(result, language) {
  displayed = { language, example: result.example };
  letter.value = result.text;
  letter.lang = langCodes[language];
  byId('empty').hidden = true;
  byId('output').hidden = false;
  byId('evidence').open = false;
  byId('quotes').replaceChildren(...result.evidence.map((quote) => {
    const item = document.createElement('li');
    item.textContent = quote;
    return item;
  }));
  updateMetadata();
  status('', false, 'output-status');
  byId('preview-title').focus();
}

function isExample(input) {
  return example && input.name === example.name && input.resume === example.resume
    && input.vacancy === example.vacancy && (input.tone === 'PROFESSIONAL' || !settings.customGeneration)
    && !input.emphasis;
}

async function loadSettings() {
  if (!settings) settings = await api('/settings');
  const notice = byId('mode');
  notice.hidden = settings.customGeneration;
  notice.textContent = 'Demonstração pública com dados fictícios. Para usar seu CV, rode o Carta Orbe com um modelo configurado.';
  byId('access').hidden = !settings.customGeneration || !settings.requiresCode;
  byId('remote').hidden = !settings.customGeneration || !settings.remoteModel;
  if (!settings.customGeneration) {
    byId('tone').disabled = true;
    byId('emphasis').disabled = true;
  }
}

byId('sample').addEventListener('click', async () => {
  try {
    byId('sample').disabled = true;
    example = await api('/examples');
    Object.entries(example).forEach(([key, value]) => { byId(key).value = value; });
    byId('url').value = '';
    byId('tone').value = 'PROFESSIONAL';
    byId('emphasis').value = '';
    status('Exemplo carregado. Confirme o idioma e gere a carta.');
    byId('language').focus();
  } catch (error) {
    status(error.message, true);
  } finally {
    byId('sample').disabled = false;
  }
});

byId('choose-file').addEventListener('click', () => byId('file').click());
byId('file').addEventListener('change', async () => {
  const picker = byId('file');
  const file = picker.files[0];
  picker.value = '';
  if (!file) return;
  const data = new FormData();
  data.set('file', file);
  const version = ++importing;
  try {
    const initialText = byId('resume').value;
    const response = await api('/resume', { method: 'POST', body: data });
    if (version !== importing || initialText !== byId('resume').value) return;
    byId('resume').value = response.text;
    status('Currículo importado. Revise o conteúdo antes de gerar.');
  } catch (error) {
    if (version === importing) status(error.message, true);
  }
});

byId('import').addEventListener('click', async () => {
  const button = byId('import');
  const field = byId('url');
  if (!field.reportValidity() || !field.value.trim()) {
    field.focus();
    status('Informe o link da vaga ou cole a descrição.', true);
    return;
  }
  try {
    button.disabled = true;
    status('Importando a descrição da vaga…');
    const initialText = byId('vacancy').value;
    const url = field.value.trim();
    const response = await post('/vacancy', { url });
    if (initialText !== byId('vacancy').value || url !== field.value.trim()) {
      status('A descrição foi alterada durante a importação e foi preservada.');
      return;
    }
    byId('vacancy').value = response.description;
    status(response.truncated ? 'Importação parcial. Complete os requisitos relevantes.'
      : 'Vaga importada. Revise a descrição antes de gerar.');
  } catch (error) {
    status(error.message, true);
  } finally {
    button.disabled = false;
  }
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  byId('generate').disabled = true;
  try {
    await loadSettings();
    const input = payload();
    const usingExample = isExample(input);
    if (!usingExample && !settings.customGeneration) {
      throw new Error('Use o exemplo nesta demo. Para um currículo próprio, configure o modelo em uma instância privada.');
    }
    if (!usingExample && settings.remoteModel && !byId('consent').checked) {
      byId('consent').focus();
      throw new Error('Confirme o envio ao serviço de IA para gerar a carta.');
    }
    status(usingExample ? 'Carregando carta de exemplo…' : 'Gerando a carta. Isso pode levar alguns instantes…');
    const result = usingExample ? await api('/examples/' + input.language)
      : await post('/letters', { ...input, remoteConsent: byId('consent').checked }, byId('code').value);
    render(result, input.language);
    status('Carta pronta para revisão.');
  } catch (error) {
    status(error.message, true);
  } finally {
    byId('generate').disabled = false;
  }
});

letter.addEventListener('input', () => {
  letter.value = letter.value.replace(/[–—―]|-{2,}/g, ',');
  updateMetadata();
});
byId('copy').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(letter.value);
    status('Texto copiado.', false, 'output-status');
  } catch {
    letter.select();
    status('Texto selecionado. Use o comando de copiar do navegador.', false, 'output-status');
  }
});
byId('download').addEventListener('click', () => {
  const file = new Blob([letter.value + '\n'], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(file);
  const link = document.createElement('a');
  link.download = 'carta-orbe-' + langCodes[displayed.language] + '.txt';
  link.href = url;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
});
byId('print').addEventListener('click', () => window.print());
window.addEventListener('beforeprint', () => {
  byId('print-letter').textContent = letter.value;
  byId('print-letter').lang = letter.lang;
});
loadSettings().catch((error) => status(error.message, true));
