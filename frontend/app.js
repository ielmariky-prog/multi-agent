const video         = document.getElementById('video');
const canvas        = document.getElementById('canvas');
const startBtn      = document.getElementById('start-camera');
const captureBtn    = document.getElementById('capture');
const fileInput     = document.getElementById('file-input');
const paymentSelect = document.getElementById('payment-mode');
const preview       = document.getElementById('preview');
const cameraStatus  = document.getElementById('camera-status');
const uploadStatus  = document.getElementById('upload-status');
const apiUrlInput   = document.getElementById('api-url');

const chatInput     = document.getElementById('chat-input');
const chatSend      = document.getElementById('chat-send');
const chatMessages  = document.getElementById('chat-messages');
const chatStatus    = document.getElementById('chat-status');

const reportLoad     = document.getElementById('report-load');
const reportStatus   = document.getElementById('report-status');
const reportTable    = document.getElementById('report-table');
const reportTbody    = document.getElementById('report-tbody');
const reportSummary  = document.getElementById('report-summary');
const filterStart    = document.getElementById('filter-start');
const filterEnd      = document.getElementById('filter-end');
const filterType     = document.getElementById('filter-type');
const filterCurrency = document.getElementById('filter-currency');

const DEFAULT_BASE = 'http://localhost:8081';
apiUrlInput.value = localStorage.getItem('apiBase') || DEFAULT_BASE;
apiUrlInput.addEventListener('change', () => {
  const val = apiUrlInput.value.trim();
  if (val) localStorage.setItem('apiBase', val);
});

function baseUrl() {
  return (apiUrlInput.value.trim() || DEFAULT_BASE).replace(/\/$/, '');
}

// ─────────────────────────────────────────
// Chat / Pipeline
// ─────────────────────────────────────────

function appendMessage(role, text) {
  const div = document.createElement('div');
  div.className = 'chat-msg chat-msg--' + role;
  div.textContent = text;
  chatMessages.appendChild(div);
  chatMessages.scrollTop = chatMessages.scrollHeight;
}

async function sendToPipeline() {
  const text = chatInput.value.trim();
  if (!text) return;

  chatInput.value = '';
  appendMessage('user', text);
  chatStatus.textContent = 'Traitement en cours…';
  chatStatus.className = 'status';
  chatSend.disabled = true;

  try {
    const res = await fetch(`${baseUrl()}/pipeline/process`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, timeoutMs: 30000 }),
    });

    const json = await res.json().catch(() => ({}));

    if (!res.ok) throw new Error(json.message || `HTTP ${res.status}`);

    let reply = `Statut : ${json.status || 'OK'}`;
    if (json.decision) reply += ` — ${json.decision}`;
    if (json.message)  reply += `\n${json.message}`;

    appendMessage('agent', reply);
    chatStatus.textContent = '';

  } catch (e) {
    chatStatus.textContent = 'Erreur : ' + e.message;
    chatStatus.className = 'status err';
    appendMessage('agent', 'Une erreur est survenue : ' + e.message);
  } finally {
    chatSend.disabled = false;
    chatInput.focus();
  }
}

chatSend.addEventListener('click', sendToPipeline);
chatInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendToPipeline();
  }
});

// ─────────────────────────────────────────
// Rapport de dépenses
// ─────────────────────────────────────────

async function loadReport() {
  reportStatus.textContent = 'Chargement…';
  reportStatus.className = 'status';
  reportTable.classList.add('hidden');
  reportSummary.classList.add('hidden');
  reportLoad.disabled = true;

  const params = new URLSearchParams();
  if (filterStart.value)    params.set('start',    filterStart.value);
  if (filterEnd.value)      params.set('end',      filterEnd.value);
  if (filterType.value)     params.set('type',     filterType.value.trim());
  if (filterCurrency.value) params.set('currency', filterCurrency.value.trim());

  try {
    const res = await fetch(`${baseUrl()}/expenses/report?${params}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();

    const items = data.items || data.expenses || [];
    reportTbody.innerHTML = '';

    if (items.length === 0) {
      reportStatus.textContent = 'Aucune dépense trouvée pour ces filtres.';
      reportStatus.className = 'status';
      reportLoad.disabled = false;
      return;
    }

    items.forEach(item => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${item.date || '—'}</td>
        <td>${item.type || '—'}</td>
        <td class="amount">${item.amount != null ? Number(item.amount).toFixed(2) : '—'}</td>
        <td>${item.currency || '—'}</td>
        <td>${item.description || '—'}</td>
      `;
      reportTbody.appendChild(tr);
    });

    const total = items.reduce((s, i) => s + (i.amount || 0), 0);
    reportSummary.textContent =
      `${items.length} dépense(s) — Total : ${total.toFixed(2)} ${items[0]?.currency || ''}`;
    reportSummary.classList.remove('hidden');
    reportTable.classList.remove('hidden');
    reportStatus.textContent = '';

  } catch (e) {
    reportStatus.textContent = 'Erreur chargement : ' + e.message;
    reportStatus.className = 'status err';
  } finally {
    reportLoad.disabled = false;
  }
}

reportLoad.addEventListener('click', loadReport);

// ─────────────────────────────────────────
// Upload justificatif
// ─────────────────────────────────────────

async function uploadFile(file) {
  const formData = new FormData();
  formData.append('file', file);
  if (paymentSelect && paymentSelect.value) {
    formData.append('paymentMode', paymentSelect.value);
  }
  uploadStatus.textContent = 'Upload en cours…';
  uploadStatus.className = 'status';

  try {
    const res = await fetch(`${baseUrl()}/receipts/upload`, { method: 'POST', body: formData });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json().catch(() => ({}));
    uploadStatus.textContent = 'Upload réussi' + (json.id ? ` (id: ${json.id})` : '');
    uploadStatus.className = 'status ok';
  } catch (e) {
    uploadStatus.textContent = 'Erreur upload : ' + e.message;
    uploadStatus.className = 'status err';
  }
}

function showPreview(src, isPdf = false) {
  preview.innerHTML = '';
  if (isPdf) {
    const embed = document.createElement('embed');
    embed.src = src;
    embed.type = 'application/pdf';
    preview.appendChild(embed);
  } else {
    const img = document.createElement('img');
    img.src = src;
    preview.appendChild(img);
  }
}

fileInput.addEventListener('change', async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  const isPdf = file.type === 'application/pdf';
  if (!isPdf) {
    const reader = new FileReader();
    reader.onload = () => showPreview(reader.result);
    reader.readAsDataURL(file);
  } else {
    showPreview(URL.createObjectURL(file), true);
  }
  await uploadFile(file);
});

// ─────────────────────────────────────────
// Caméra
// ─────────────────────────────────────────

let stream;

async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
    video.srcObject = stream;
    captureBtn.disabled = false;
    cameraStatus.textContent = 'Caméra active';
    cameraStatus.className = 'status ok';
  } catch (e) {
    cameraStatus.textContent = "Impossible d'accéder à la caméra : " + e.message;
    cameraStatus.className = 'status err';
  }
}

function stopCamera() {
  if (stream) {
    stream.getTracks().forEach(t => t.stop());
    stream = null;
  }
  captureBtn.disabled = true;
}

function dataUrlToFile(dataUrl, filename) {
  const arr  = dataUrl.split(',');
  const mime = arr[0].match(/:(.*?);/)[1];
  const bstr = atob(arr[1]);
  let n = bstr.length;
  const u8arr = new Uint8Array(n);
  while (n--) u8arr[n] = bstr.charCodeAt(n);
  return new File([u8arr], filename, { type: mime });
}

async function capturePhoto() {
  if (!stream) return;
  const ctx = canvas.getContext('2d');
  canvas.width  = video.videoWidth;
  canvas.height = video.videoHeight;
  ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
  const dataUrl = canvas.toDataURL('image/jpeg', 0.9);
  showPreview(dataUrl);
  await uploadFile(dataUrlToFile(dataUrl, 'receipt.jpg'));
}

startBtn.addEventListener('click', () => {
  if (stream) { stopCamera(); startCamera(); } else { startCamera(); }
});
captureBtn.addEventListener('click', capturePhoto);
