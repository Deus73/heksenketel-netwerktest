# Vergelijkende DNS- en HTTPS-test

Voor Windows, macOS en Linux met **Python 3.10 of nieuwer**. Geen extra Python-pakketten nodig. De test verandert geen netwerkinstellingen.

## Starten

Open een terminal in deze map en voer uit:

```sh
python3 test_dns.py
```

Op Windows is het commando doorgaans `py test_dns.py`. Vul het **officiële webadres** in wanneer daarom wordt gevraagd. Het programma zoekt dit adres niet zelf op, om verwarring met gelijknamige zaken te vermijden.

Je kunt het adres en de gebruikte aansluiting ook direct meegeven:

```sh
python3 test_dns.py https://JOUW-OFFICIELE-DOMEIN.be --connection "Telenet thuis"
```

Gebruik het echte domein in plaats van het voorbeeld. Bij internationale tekens in het URL-pad: gebruik de %-gecodeerde URL uit de browser. Standaard wordt HTTPS gebruikt. HTTP zonder TLS wordt niet getest.

## Wat wordt vastgelegd?

- IPv4- en IPv6-DNS-antwoorden (A/AAAA), CNAME-ketens, TTL, foutmeldingen en responstijden.
- UDP-DNS met TCP-herhaling bij afgekapt antwoord.
- Een HTTPS-verzoek naar ieder uniek gevonden IP-adres en via de systeemresolver.
- Certificaatvalidatie met de originele hostnaam en SNI, HTTP-status en redirectbestemming.
- `report.md` (leesbaar rapport), `report.json` (volledige gegevens) en `dns.csv` (spreadsheet).

Redirects worden **niet gevolgd**, zodat een eerste doorverwijzing zichtbaar blijft. De test vergelijkt geen volledige pagina-inhoud en onderzoekt geen zoekmachine-antwoorden. HTTP 403/404/500 toont wel een antwoord van een HTTPS-server, maar betekent niet dat de website normaal werkt.

## Providers en bronnen

`resolvers.json` bevat een startlijst voor Proximus en Telenet plus Cloudflare, Google Public DNS en Quad9 als referentie. Bronnen staan per provideradres in dat bestand. Sommige adressen komen uit communitydocumentatie en kunnen verouderd zijn. Dit is **geen volledige lijst van alle Belgische providers**. Voor Orange, VOO, DIGI, edpnet en andere aansluitingen: vraag de actuele recursieve DNS-adressen bij de provider op of lees ze uit de router; vul geen gegokte adressen in. Resellers kunnen DNS van een ander netwerk gebruiken.

Voeg een bevestigd adres toe met:

```sh
python3 test_dns.py JOUW-OFFICIELE-DOMEIN.be --resolver "Eigen provider=192.0.2.53"
```

`192.0.2.53` is uitsluitend een documentatievoorbeeld: vervang het door het werkelijke adres. IPv6-adressen zijn ook toegestaan. Je kunt eveneens `resolvers.json` aanpassen of een eigen JSON-lijst via `--resolvers bestand.json` meegeven. Gebruik recursieve resolvers, niet zomaar de autoritatieve nameservers van een providerwebsite.

## Echte vergelijking tussen verbindingen

1. Start de test via de vaste aansluiting met een herkenbaar `--connection`-label.
2. Verbind hetzelfde toestel met een mobiele hotspot van een **andere** provider.
3. Herhaal kort daarna met hetzelfde webadres en een nieuw label.
4. Vergelijk de rapporten. Voeg zo mogelijk een test vanaf een aansluiting van de bevraagde provider toe.
5. Noteer of VPN, beveiligingsfilter of ouderlijk toezicht actief was. Op sommige toestellen gebruikt de browser beveiligde DNS buiten de systeemresolver om; het browsersurfgedrag kan daardoor verschillen van deze test.

Op Linux kun je de ingestelde resolver vaak bekijken met `resolvectl status`, op Windows met `ipconfig /all`, en op macOS met `scutil --dns`. Een lokaal routeradres of systeemstub is ook bruikbaar als extra resolver; het is niet noodzakelijk het adres van de upstream-provider-DNS.

## Interpretatie

- **REFUSED/time-out**: de resolver kan alleen eigen klanten toelaten, of poort 53 is onbereikbaar. Geen bewijs van een websiteblokkade.
- **NXDOMAIN bij één resolver**: onderzoekssignaal; controleer herhaaldelijk en vanaf de eigen aansluiting van die provider. Ook cache- of configuratiefouten zijn mogelijk.
- **Verschillende IP-adressen**: vaak normaal bij CDN's, load balancing en geografische spreiding.
- **HTTPS-fout op één verbinding**: kan ook liggen aan routing, IPv6, firewall of serverfilters.
- **Redirect**: kan afkomstig zijn van de website zelf. Een valide HTTPS-verbinding maakt ongemerkte inhoudswijziging onderweg veel moeilijker, maar bewijst niet wie alle achterliggende systemen beheert.

De DNS-verzoeken worden verstuurd via je **huidige verbinding**. Dit geeft je geen internetaansluiting bij een andere provider. Onversleutelde DNS kan onderweg worden onderschept; dit script valideert geen DNSSEC en gebruikt geen DoH/DoT. Het rapport trekt daarom geen automatische conclusie over blokkade, schuld of opzet.

Er wordt niets naar een centrale rapportageserver geüpload. De geraadpleegde DNS-diensten ontvangen het domein; de website ontvangt HTTPS-verzoeken. Rapporten bevatten je ingevoerde URL en aansluitingslabel: controleer die voordat je ze deelt, vooral URL-queryparameters.

## Technische controle

```sh
python3 -m unittest discover -s tests -v
```
