<?php
declare(strict_types=1);

function ketel_u16(string $data, int $offset): int {
    if ($offset < 0 || $offset + 2 > strlen($data)) throw new RuntimeException('Afgebroken DNS-antwoord');
    return unpack('n', substr($data, $offset, 2))[1];
}
function ketel_dns_name(string $data, int &$cursor): string {
    $labels = []; $seen = []; $offset = $cursor; $end = null;
    while (true) {
        if ($offset >= strlen($data) || isset($seen[$offset])) throw new RuntimeException('Ongeldige DNS-naam');
        $seen[$offset] = true; $length = ord($data[$offset]);
        if (($length & 192) === 192) {
            $end ??= $offset + 2; $offset = ketel_u16($data, $offset) & 0x3fff;
        } else {
            if (($length & 192) !== 0) throw new RuntimeException('Ongeldig DNS-label');
            $offset++;
            if ($length === 0) { $cursor = $end ?? $offset; return strtolower(implode('.', $labels)); }
            if ($offset + $length > strlen($data)) throw new RuntimeException('Afgebroken DNS-label');
            $labels[] = substr($data, $offset, $length); $offset += $length;
        }
    }
}
function ketel_dns_parse(string $data, int $id, string $host, int $type): array {
    if (strlen($data) < 12 || ketel_u16($data, 0) !== $id || (ketel_u16($data, 2) & 0xf800) !== 0x8000 || ketel_u16($data, 4) !== 1) throw new RuntimeException('Onverwacht DNS-antwoord');
    $flags = ketel_u16($data, 2); $cursor = 12;
    if (ketel_dns_name($data, $cursor) !== $host || ketel_u16($data, $cursor) !== $type || ketel_u16($data, $cursor + 2) !== 1) throw new RuntimeException('Onverwachte DNS-vraag');
    if (($flags & 0x200) !== 0) return ['status' => 'TRUNCATED', 'records' => []];
    $cursor += 4; $records = [];
    for ($i = 0; $i < ketel_u16($data, 6); $i++) {
        $name = ketel_dns_name($data, $cursor);
        $recordType = ketel_u16($data, $cursor); $class = ketel_u16($data, $cursor + 2);
        $ttl = (ketel_u16($data, $cursor + 4) << 16) | ketel_u16($data, $cursor + 6);
        $size = ketel_u16($data, $cursor + 8); $cursor += 10;
        if ($cursor + $size > strlen($data)) throw new RuntimeException('Afgebroken DNS-record');
        $value = null;
        if ($class === 1 && (($recordType === 1 && $size === 4) || ($recordType === 28 && $size === 16))) $value = inet_ntop(substr($data, $cursor, $size));
        if ($class === 1 && $recordType === 5) { $position = $cursor; $value = ketel_dns_name($data, $position); }
        if ($value !== null && $value !== false) $records[] = ['name' => $name, 'type' => $recordType, 'value' => $value, 'ttl' => $ttl];
        $cursor += $size;
    }
    $status = [0 => 'NOERROR', 1 => 'FORMERR', 2 => 'SERVFAIL', 3 => 'NXDOMAIN', 5 => 'REFUSED'][$flags & 15] ?? ('RCODE_' . ($flags & 15));
    if ($status === 'NOERROR' && !$records) $status = 'NODATA';
    return ['status' => $status, 'records' => $records];
}
function ketel_read_exact($socket, int $length): string {
    $result = '';
    while (strlen($result) < $length) {
        $part = fread($socket, $length - strlen($result));
        if ($part === false || $part === '') throw new RuntimeException('TCP DNS-antwoord onvolledig of time-out');
        $result .= $part;
    }
    return $result;
}
function ketel_dns_query(string $label, string $server, int $type): array {
    $host = 'www.deheksenketel.com'; $began = microtime(true);
    $result = ['resolver' => $label, 'server' => $server, 'type' => $type === 1 ? 'A' : 'AAAA', 'source' => 'hosting_server', 'records' => []];
    try {
        $id = random_int(0, 65535); $question = pack('nnnnnn', $id, 0x0100, 1, 0, 0, 0);
        foreach (explode('.', $host) as $part) $question .= chr(strlen($part)) . $part;
        $question .= "\0" . pack('nn', $type, 1);
        $socket = @stream_socket_client('udp://' . $server . ':53', $errno, $error, 1.0);
        if ($socket === false) throw new RuntimeException('DNS-verbinding niet mogelijk');
        try {
            stream_set_timeout($socket, 1);
            if (fwrite($socket, $question) !== strlen($question)) throw new RuntimeException('DNS-verzoek niet verstuurd');
            $data = fread($socket, 65535);
            if ($data === false || $data === '') {
                $result['status'] = 'TIMEOUT'; $result['error'] = 'Geen DNS-antwoord vanaf hosting';
                return $result + ['duration_ms' => (int)round((microtime(true) - $began) * 1000)];
            }
            $reply = ketel_dns_parse($data, $id, $host, $type);
        } finally { fclose($socket); }
        $result['transport'] = 'UDP';
        if ($reply['status'] === 'TRUNCATED') {
            $socket = @stream_socket_client('tcp://' . $server . ':53', $errno, $error, 1.0);
            if ($socket === false) throw new RuntimeException('TCP DNS-verbinding niet mogelijk');
            try {
                stream_set_timeout($socket, 1);
                $packet = pack('n', strlen($question)) . $question;
                if (fwrite($socket, $packet) !== strlen($packet)) throw new RuntimeException('TCP DNS-verzoek onvolledig verstuurd');
                $length = unpack('n', ketel_read_exact($socket, 2))[1];
                $reply = ketel_dns_parse(ketel_read_exact($socket, $length), $id, $host, $type);
            } finally { fclose($socket); }
            $result['transport'] = 'TCP';
        }
        $result = array_merge($result, $reply);
    } catch (Throwable $error) { $result['status'] = 'ERROR'; $result['error'] = $error->getMessage(); }
    $result['duration_ms'] = (int)round((microtime(true) - $began) * 1000);
    return $result;
}
