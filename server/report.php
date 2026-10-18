<?php
declare(strict_types=1);

// PHP 8.1+, op HTTPS-webhosting met werkende WordPress-mail of mail() plaatsen.
// Ontvanger, afzender en onderwerp komen nooit uit het clientverzoek.
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
set_exception_handler(function (Throwable $error): never {
    error_log('Heksenketel DNS receiver: ' . $error);
    respond(503, ['accepted' => false, 'error' => 'Server processing failed; check PHP error log']);
});
function respond(int $status, array $body): never {
    http_response_code($status);
    echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
if ($_SERVER['REQUEST_METHOD'] !== 'POST') respond(405, ['accepted' => false, 'error' => 'POST required']);
$type = strtolower(explode(';', $_SERVER['CONTENT_TYPE'] ?? '')[0]);
if (trim($type) !== 'application/json') respond(415, ['accepted' => false, 'error' => 'JSON required']);
$raw = file_get_contents('php://input', false, null, 0, 131073);
if ($raw === false || strlen($raw) > 131072) respond(413, ['accepted' => false, 'error' => 'Report too large']);
try { $report = json_decode($raw, true, 32, JSON_THROW_ON_ERROR); }
catch (JsonException $e) { respond(400, ['accepted' => false, 'error' => 'Invalid JSON']); }
if (!is_array($report) || ($report['target'] ?? '') !== 'www.deheksenketel.com'
    || !is_string($report['id'] ?? null) || !preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/D', $report['id'])
    || ($report['schema'] ?? null) !== 1 || !is_array($report['dns'] ?? null)
    || !is_array($report['network'] ?? null) || !is_array($report['https'] ?? null)) {
    respond(400, ['accepted' => false, 'error' => 'Invalid report schema']);
}

// Tijdelijke administratie buiten de webroot; bevat alleen tellers en UUID-hashes.
$stateDir = sys_get_temp_dir() . '/heksenketel-dns-' . substr(hash('sha256', __DIR__), 0, 16);
if (!is_dir($stateDir) && !@mkdir($stateDir, 0700, true) && !is_dir($stateDir)) respond(503, ['accepted' => false]);
$lock = @fopen($stateDir . '/state.json', 'c+');
if ($lock === false || !flock($lock, LOCK_EX)) respond(503, ['accepted' => false]);
$state = json_decode(stream_get_contents($lock), true) ?: ['sent' => [], 'clients' => []];
$now = time();
$state['sent'] = array_filter($state['sent'], fn($at) => $at > $now - 604800);
$state['clients'] = array_filter($state['clients'], fn($entry) => $entry['start'] > $now - 3600);
$id = hash('sha256', $report['id']);
if (isset($state['sent'][$id])) { flock($lock, LOCK_UN); fclose($lock); respond(200, ['accepted' => true, 'duplicate' => true]); }
// Geen vertrouwen in vrij invulbare X-Forwarded-For-headers.
$client = hash('sha256', $_SERVER['REMOTE_ADDR'] ?? 'unknown');
$entry = $state['clients'][$client] ?? ['start' => $now, 'count' => 0];
if ($entry['count'] >= 5) { flock($lock, LOCK_UN); fclose($lock); respond(429, ['accepted' => false, 'error' => 'Hourly limit reached']); }
$entry['count']++;
$state['clients'][$client] = $entry;
$body = "Netwerkdiagnose voor www.deheksenketel.com\n"
    . "Clientmetingen zijn niet onafhankelijk geverifieerd en bewijzen geen blokkade of opzet.\n"
    . "Ontvangen (UTC): " . gmdate('c') . "\n\n"
    . json_encode($report, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
$headers = ['From: info@deheksenketel.com', 'MIME-Version: 1.0', 'Content-Type: text/plain; charset=UTF-8'];
$accepted = false;
try {
    // Op beheerde WordPress-hosting kan losse PHP-mail ontbreken. Gebruik dan de
    // mailconfiguratie van WordPress, inclusief eventueel ingestelde SMTP-plugin.
    $wordpress = dirname(__DIR__) . '/wp-load.php';
    if (!function_exists('wp_mail') && is_file($wordpress)) require_once $wordpress;
    if (function_exists('wp_mail')) {
        $accepted = wp_mail('info@deheksenketel.com', 'Netwerkdiagnose De Heksenketel', $body, $headers);
    } elseif (function_exists('mail')) {
        $accepted = mail('info@deheksenketel.com', 'Netwerkdiagnose De Heksenketel', $body, implode("\r\n", $headers));
    } else {
        error_log('Heksenketel DNS receiver: neither wp_mail nor mail is available');
    }
} catch (Throwable $error) {
    error_log('Heksenketel DNS receiver mail transport: ' . $error);
}
if ($accepted) $state['sent'][$id] = $now;
rewind($lock); ftruncate($lock, 0); fwrite($lock, json_encode($state)); fflush($lock); flock($lock, LOCK_UN); fclose($lock);
if (!$accepted) respond(503, ['accepted' => false, 'error' => 'Mail transport unavailable']);
respond(200, ['accepted' => true]);
