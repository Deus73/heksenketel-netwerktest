const TARGET = "www.deheksenketel.com";
const ENDPOINT = new URL("../report.php", import.meta.url);
const DIAGNOSE = new URL("../diagnose.php", import.meta.url);
const HOME = new URL("../../", import.meta.url);
const QUEUE_KEY = "heksenketel.pending.v1";
const LAST_KEY = "heksenketel.latest.v1";
const $ = id => document.getElementById(id);
let busy = false;
let latest = null;
let storageAvailable = true;

function status(message, error = false) {
  $("status").textContent = message;
  $("status").dataset.error = String(error);
}
function pending() {
  const value = JSON.parse(localStorage.getItem(QUEUE_KEY) || "[]");
  if (!Array.isArray(value)) throw new Error("De lokale rapportwachtrij is beschadigd. Download je laatste rapport en wis daarna de websitegegevens.");
  return value;
}
function controls() {
  $("start").disabled = busy || !storageAvailable;
  $("download").disabled = !latest;
  try {
    const count = pending().length;
    $("retry").disabled = busy || !count || !storageAvailable;
    $("queue").textContent = count ? `${count} rapport(en) wachten op verzending.` : "Geen wachtende rapporten.";
  } catch (error) { $("retry").disabled = true; $("queue").textContent = error.message; }
}
async function request(url, options = {}, timeout = 12000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    return await fetch(url, {cache: "no-store", credentials: "omit", ...options, signal: controller.signal});
  } finally { clearTimeout(timer); }
}
async function jsonRequest(url, options = {}, timeout = 12000) {
  // Body ook binnen de timeout lezen: sommige servers sturen wel headers maar geen body.
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const response = await fetch(url, {cache: "no-store", credentials: "omit", ...options, signal: controller.signal});
    if (!response.ok) throw Object.assign(new Error(`Server antwoordt HTTP ${response.status}`), {httpStatus: response.status});
    return await response.json();
  } finally { clearTimeout(timer); }
}
function errorText(error) {
  return error.name === "AbortError" ? "Time-out" : String(error.message || error);
}
async function browserDNS(name, base, type) {
  const began = performance.now();
  const row = {resolver: name, type, source: "browser_doh", records: []};
  try {
    const url = new URL(base);
    url.searchParams.set("name", TARGET); url.searchParams.set("type", type);
    const reply = await jsonRequest(url, {headers: {Accept: "application/dns-json"}});
    if (!Number.isInteger(reply.Status)) throw new Error("Onverwacht DNS-antwoord");
    const names = {0: "NOERROR", 2: "SERVFAIL", 3: "NXDOMAIN", 5: "REFUSED"};
    row.status = names[reply.Status] || `RCODE_${reply.Status}`;
    row.records = (reply.Answer || []).filter(record => [1, 5, 28].includes(record.type)).map(record => ({name: record.name, type: record.type, value: record.data, ttl: record.TTL}));
    if (reply.Status === 0 && !row.records.length) row.status = "NODATA";
  } catch (error) { row.status = "ERROR"; row.error = errorText(error); }
  row.duration_ms = Math.round(performance.now() - began);
  return row;
}
async function reachability() {
  const began = performance.now();
  try {
    const response = await request(HOME, {redirect: "follow"}, 12000);
    return {source: "browser", http_status: response.status, redirected: response.redirected, final_url: response.url,
      tls_handled_by_browser: HOME.protocol === "https:", duration_ms: Math.round(performance.now() - began)};
  } catch (error) {
    return {source: "browser", error: errorText(error), duration_ms: Math.round(performance.now() - began),
      note: "Netwerk-, TLS- en browser/CORS-fouten zijn hier niet altijd van elkaar te onderscheiden."};
  }
}
async function serverDNS() {
  try {
    const data = await jsonRequest(DIAGNOSE, {method: "POST", headers: {"Content-Type": "application/json"}, body: "{}", redirect: "error"}, 35000);
    if (data.target !== TARGET || data.source !== "hosting_server" || !Array.isArray(data.dns)) throw new Error("Onverwacht serverrapport");
    return data;
  } catch (error) { return {source: "hosting_server", error: errorText(error), dns: []}; }
}
function renderRows(id, rows) {
  const body = $(id); body.replaceChildren();
  for (const row of rows) {
    const tr = document.createElement("tr");
    const values = [row.resolver, row.type, row.status, row.error || row.records?.map(record => record.value).join(", ") || "—"];
    for (const value of values) { const td = document.createElement("td"); td.textContent = String(value || "—"); tr.append(td); }
    body.append(tr);
  }
}
function render(report) {
  $("results").hidden = false;
  $("reachability").textContent = report.https.error ? `Websitecontrole vanaf je browser: ${report.https.error}.` :
    `Websitecontrole vanaf je browser: HTTP ${report.https.http_status}${report.https.redirected ? " (doorverwijzing gevolgd)" : ""}.`;
  renderRows("browser-dns", report.dns.filter(row => row.source === "browser_doh"));
  renderRows("server-dns", report.dns.filter(row => row.source === "hosting_server"));
  $("server-status").textContent = report.server_diagnostics?.error || "Servermetingen apart geregistreerd.";
}
async function uploadQueued() {
  const reports = pending(); let accepted = 0;
  for (const report of reports) {
    let uploaded = false; let failure;
    for (let attempt = 0; attempt < 3; attempt++) {
      status(`Rapport aanbieden voor e-mail… poging ${attempt + 1}.`);
      try {
        const result = await jsonRequest(ENDPOINT, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(report), redirect: "error"}, 15000);
        if (result.accepted !== true) throw new Error("Geen geldige verzendbevestiging");
        uploaded = true; break;
      } catch (error) {
        failure = error;
        if (error.httpStatus && error.httpStatus < 500) break;
        if (attempt < 2) await new Promise(resolve => setTimeout(resolve, (attempt + 1) * 1500));
      }
    }
    if (!uploaded) throw new Error(`Rapport lokaal bewaard. Versturen mislukt: ${errorText(failure)}.`);
    localStorage.setItem(QUEUE_KEY, JSON.stringify(pending().filter(item => item.id !== report.id)));
    accepted++;
  }
  return accepted;
}
async function run(retryOnly = false) {
  if (busy) return;
  busy = true; controls();
  // Voorkomt dat twee geopende tabbladen gelijktijdig de wachtrij wijzigen waar ondersteund.
  const work = async () => {
    try {
      if (!retryOnly) {
        if (pending().length >= 20) throw new Error("Er wachten al 20 rapporten. Verstuur die eerst of download je laatste rapport en wis de websitegegevens.");
        status("Website en DNS onderzoeken… Dit duurt meestal minder dan een minuut.");
        const started = new Date().toISOString();
        const [dns, https, server] = await Promise.all([
          Promise.all([
            browserDNS("Cloudflare", "https://cloudflare-dns.com/dns-query", "A"),
            browserDNS("Cloudflare", "https://cloudflare-dns.com/dns-query", "AAAA"),
            browserDNS("Google", "https://dns.google/resolve", "A"),
            browserDNS("Google", "https://dns.google/resolve", "AAAA")]),
          reachability(), serverDNS()
        ]);
        const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
        const report = {schema: 1, id: crypto.randomUUID(), target: TARGET, timestamp: started, client: "webapp-1.3",
          network: {source: "browser", online_hint: navigator.onLine, effective_type_hint: connection?.effectiveType || null,
            provider: "Niet beschikbaar in browser", system_dns: "Niet beschikbaar in browser"},
          dns: [...dns, ...server.dns.map(row => ({...row, source: "hosting_server"}))], https,
          server_diagnostics: {source: "hosting_server", timestamp: server.timestamp || null, error: server.error || null},
          limitations: ["Providerresolvers zijn vanaf de hosting bevraagd, niet vanaf dit toestel.",
            "Browser-DNS-metingen gebruiken onafhankelijke DoH-diensten en tonen niet de systeem-DNS.",
            "Geen directe provider-, VPN- of systeem-DNS-detectie in de browser.",
            "Time-outs, browserfouten en afwijkende IP-adressen bewijzen geen blokkade of opzet."]};
        // Eerst opslaan; bij het verlaten van de pagina blijft het voltooide rapport bewaard.
        localStorage.setItem(QUEUE_KEY, JSON.stringify([...pending(), report]));
        latest = report;
        localStorage.setItem(LAST_KEY, JSON.stringify(report));
        render(report); controls();
      }
      const count = await uploadQueued();
      status(count ? `${count} rapport(en) aangenomen voor verzending naar info@deheksenketel.com. Dit bevestigt nog niet de aflevering in de mailbox.` : "Geen wachtende rapporten.");
    } catch (error) { status(errorText(error), true); }
  };
  try {
    if (navigator.locks) await navigator.locks.request("heksenketel-reports", work);
    else await work();
  } finally { busy = false; controls(); }
}
$("start").addEventListener("click", () => run());
$("retry").addEventListener("click", () => run(true));
$("download").addEventListener("click", () => {
  if (!latest) return;
  const blob = new Blob([JSON.stringify(latest, null, 2)], {type: "application/json"});
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a"); link.href = url; link.download = `heksenketel-${latest.id}.json`;
  document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 30000);
});
try {
  localStorage.setItem("heksenketel.storage-check", "1"); localStorage.removeItem("heksenketel.storage-check");
  latest = JSON.parse(localStorage.getItem(LAST_KEY) || "null");
  if (latest?.target === TARGET && Array.isArray(latest.dns) && latest.https) render(latest);
  else latest = null;
} catch { storageAvailable = false; status("Lokale opslag is niet beschikbaar. Sta websiteopslag toe om rapporten veilig voor verzending te bewaren.", true); }
controls();
if ("serviceWorker" in navigator) navigator.serviceWorker.register("./sw.js").catch(() => {});
