<?php
declare(strict_types=1);
require __DIR__ . '/dns.php';

function expect(bool $condition, string $label): void {
    if (!$condition) throw new RuntimeException('Test mislukt: ' . $label);
}
function packet(int $flags, ?string $address): string {
    $question = "\x03www\x0ddeheksenketel\x03com\0" . pack('nn', 1, 1);
    $header = pack('nnnnnn', 1234, $flags, 1, $address === null ? 0 : 1, 0, 0);
    return $header . $question . ($address === null ? '' : "\xc0\x0c" . pack('nnNn', 1, 1, 60, strlen($address)) . $address);
}
function rejects(callable $call, string $label): void {
    try { $call(); } catch (Throwable $error) { echo "OK: $label\n"; return; }
    throw new RuntimeException('Geen fout voor: ' . $label);
}
$response = packet(0x8180, inet_pton('192.0.2.1'));
$reply = ketel_dns_parse($response, 1234, 'www.deheksenketel.com', 1);
expect($reply['records'][0]['value'] === '192.0.2.1', 'IPv4-adres');
expect($reply['records'][0]['name'] === 'www.deheksenketel.com', 'Gecomprimeerde naam');
echo "OK: gecomprimeerd IPv4-antwoord\n";
expect(ketel_dns_parse(packet(0x8183, null), 1234, 'www.deheksenketel.com', 1)['status'] === 'NXDOMAIN', 'NXDOMAIN');
echo "OK: NXDOMAIN\n";
expect(ketel_dns_parse(packet(0x8380, null), 1234, 'www.deheksenketel.com', 1)['status'] === 'TRUNCATED', 'Truncatie');
echo "OK: truncatie\n";
rejects(fn() => ketel_dns_parse($response, 999, 'www.deheksenketel.com', 1), 'verkeerde transactie');
rejects(fn() => ketel_dns_parse($response, 1234, 'other.example', 1), 'verkeerde vraag');
rejects(fn() => ketel_dns_parse(substr($response, 0, -1), 1234, 'www.deheksenketel.com', 1), 'afgebroken antwoord');
rejects(function () { $position = 0; ketel_dns_name("\xc0\x00", $position); }, 'compressielus');
