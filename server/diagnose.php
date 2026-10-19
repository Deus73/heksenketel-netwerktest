<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
function ketel_response(int $status, array $body): never {
    http_response_code($status); echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit;
}
set_exception_handler(function (Throwable $error): never {
    error_log('Heksenketel server diagnostics: ' . $error);
    ketel_response(503, ['error' => 'Serverdiagnose niet beschikbaar; controleer PHP-errorlog']);
});
if ($_SERVER['REQUEST_METHOD'] !== 'POST') ketel_response(405, ['error' => 'POST required']);
// Doel en DNS-servers liggen vast. Er worden geen willekeurige adressen van de client gebruikt.
$directory = sys_get_temp_dir() . '/heksenketel-probes-' . substr(hash('sha256', __DIR__), 0, 16);
if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) ketel_response(503, ['error' => 'Serveropslag niet beschikbaar']);
$file = @fopen($directory . '/limits.json', 'c+');
if ($file === false || !flock($file, LOCK_EX)) ketel_response(503, ['error' => 'Server tijdelijk bezet']);
$state = json_decode(stream_get_contents($file), true) ?: []; $now = time();
$state = array_filter($state, fn($entry) => $entry['start'] > $now - 3600);
$client = hash('sha256', $_SERVER['REMOTE_ADDR'] ?? 'unknown');
$entry = $state[$client] ?? ['start' => $now, 'last' => 0, 'count' => 0];
if ($entry['count'] >= 30 || $entry['last'] > $now - 10) {
    flock($file, LOCK_UN); fclose($file); header('Retry-After: 10'); ketel_response(429, ['error' => 'Wacht even voordat je opnieuw test']);
}
$state[$client] = ['start' => $entry['start'], 'last' => $now, 'count' => $entry['count'] + 1];
rewind($file); ftruncate($file, 0); fwrite($file, json_encode($state)); fflush($file); flock($file, LOCK_UN); fclose($file);
require_once __DIR__ . '/dns.php';
$resolvers = ['Proximus 1' => '195.238.2.21', 'Proximus 2' => '195.238.2.22',
    'Telenet 1' => '195.130.131.4', 'Telenet 2' => '195.130.130.4',
    'Cloudflare (hosting)' => '1.1.1.1', 'Google (hosting)' => '8.8.8.8', 'Quad9 (hosting)' => '9.9.9.9'];
$records = [];
foreach ($resolvers as $name => $ip) foreach ([1, 28] as $type) $records[] = ketel_dns_query($name, $ip, $type);
ketel_response(200, ['target' => 'www.deheksenketel.com', 'source' => 'hosting_server', 'timestamp' => gmdate('c'), 'dns' => $records]);
